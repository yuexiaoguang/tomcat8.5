package org.apache.tomcat.websocket.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoMethodMapping;

/**
 * 提供ServerContainer的每个类加载器（即每个Web应用程序）实例.
 * 可以通过将下列servlet上下文初始化参数设置为所需的值, 来配置Web应用程序范围的默认值.
 * <ul>
 * <li>{@link Constants#BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM}</li>
 * <li>{@link Constants#TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM}</li>
 * </ul>
 */
public class WsServerContainer extends WsWebSocketContainer
        implements ServerContainer {

    private static final StringManager sm = StringManager.getManager(WsServerContainer.class);

    private static final CloseReason AUTHENTICATED_HTTP_SESSION_CLOSED =
            new CloseReason(CloseCodes.VIOLATED_POLICY,
                    "This connection was established under an authenticated " +
                    "HTTP session that has ended.");

    private final WsWriteTimeout wsWriteTimeout = new WsWriteTimeout();

    private final ServletContext servletContext;
    private final Map<String,ServerEndpointConfig> configExactMatchMap =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer,SortedSet<TemplatePathMatch>> configTemplateMatchMap =
            new ConcurrentHashMap<>();
    private volatile boolean enforceNoAddAfterHandshake =
            org.apache.tomcat.websocket.Constants.STRICT_SPEC_COMPLIANCE;
    private volatile boolean addAllowed = true;
    private final ConcurrentMap<String,Set<WsSession>> authenticatedSessions =
            new ConcurrentHashMap<>();
    private volatile boolean endpointsRegistered = false;

    WsServerContainer(ServletContext servletContext) {

        this.servletContext = servletContext;
        setInstanceManager((InstanceManager) servletContext.getAttribute(InstanceManager.class.getName()));

        // 配置servlet上下文范围默认值
        String value = servletContext.getInitParameter(
                Constants.BINARY_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM);
        if (value != null) {
            setDefaultMaxBinaryMessageBufferSize(Integer.parseInt(value));
        }

        value = servletContext.getInitParameter(
                Constants.TEXT_BUFFER_SIZE_SERVLET_CONTEXT_INIT_PARAM);
        if (value != null) {
            setDefaultMaxTextMessageBufferSize(Integer.parseInt(value));
        }

        value = servletContext.getInitParameter(
                Constants.ENFORCE_NO_ADD_AFTER_HANDSHAKE_CONTEXT_INIT_PARAM);
        if (value != null) {
            setEnforceNoAddAfterHandshake(Boolean.parseBoolean(value));
        }

        FilterRegistration.Dynamic fr = servletContext.addFilter(
                "Tomcat WebSocket (JSR356) Filter", new WsFilter());
        fr.setAsyncSupported(true);

        EnumSet<DispatcherType> types = EnumSet.of(DispatcherType.REQUEST,
                DispatcherType.FORWARD);

        fr.addMappingForUrlPatterns(types, true, "/*");
    }


    /**
     * 在指定路径中以指定的配置发布所提供的端点实现.
     * 调用这个方法之前, 必须调用{@link #WsServerContainer(ServletContext)}.
     *
     * @param sec   The configuration to use when creating endpoint instances
     * @throws DeploymentException if the endpoint cannot be published as
     *         requested
     */
    @Override
    public void addEndpoint(ServerEndpointConfig sec)
            throws DeploymentException {

        if (enforceNoAddAfterHandshake && !addAllowed) {
            throw new DeploymentException(
                    sm.getString("serverContainer.addNotAllowed"));
        }

        if (servletContext == null) {
            throw new DeploymentException(
                    sm.getString("serverContainer.servletContextMissing"));
        }
        String path = sec.getPath();

        // 将方法映射添加到用户属性
        PojoMethodMapping methodMapping = new PojoMethodMapping(sec.getEndpointClass(),
                sec.getDecoders(), path);
        if (methodMapping.getOnClose() != null || methodMapping.getOnOpen() != null
                || methodMapping.getOnError() != null || methodMapping.hasMessageHandlers()) {
            sec.getUserProperties().put(org.apache.tomcat.websocket.pojo.Constants.POJO_METHOD_MAPPING_KEY,
                    methodMapping);
        }

        UriTemplate uriTemplate = new UriTemplate(path);
        if (uriTemplate.hasParameters()) {
            Integer key = Integer.valueOf(uriTemplate.getSegmentCount());
            SortedSet<TemplatePathMatch> templateMatches =
                    configTemplateMatchMap.get(key);
            if (templateMatches == null) {
                // 确保如果并发线程执行此块，则它们最终都使用相同的TreeSet 实例
                templateMatches = new TreeSet<>(
                        TemplatePathMatchComparator.getInstance());
                configTemplateMatchMap.putIfAbsent(key, templateMatches);
                templateMatches = configTemplateMatchMap.get(key);
            }
            if (!templateMatches.add(new TemplatePathMatch(sec, uriTemplate))) {
                // 重复的 uriTemplate;
                throw new DeploymentException(
                        sm.getString("serverContainer.duplicatePaths", path,
                                     sec.getEndpointClass(),
                                     sec.getEndpointClass()));
            }
        } else {
            // 精确匹配
            ServerEndpointConfig old = configExactMatchMap.put(path, sec);
            if (old != null) {
                // 重复的路径映射
                throw new DeploymentException(
                        sm.getString("serverContainer.duplicatePaths", path,
                                     old.getEndpointClass(),
                                     sec.getEndpointClass()));
            }
        }

        endpointsRegistered = true;
    }


    /**
     * 提供等效的 {@link #addEndpoint(ServerEndpointConfig)}, 用于发布已被注解为为WebSocket端点的普通旧Java对象 (POJOs).
     *
     * @param pojo   注解的 POJO
     */
    @Override
    public void addEndpoint(Class<?> pojo) throws DeploymentException {

        ServerEndpoint annotation = pojo.getAnnotation(ServerEndpoint.class);
        if (annotation == null) {
            throw new DeploymentException(
                    sm.getString("serverContainer.missingAnnotation",
                            pojo.getName()));
        }
        String path = annotation.value();

        // 验证编码器
        validateEncoders(annotation.encoders());

        // ServerEndpointConfig
        ServerEndpointConfig sec;
        Class<? extends Configurator> configuratorClazz =
                annotation.configurator();
        Configurator configurator = null;
        if (!configuratorClazz.equals(Configurator.class)) {
            try {
                configurator = annotation.configurator().getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new DeploymentException(sm.getString(
                        "serverContainer.configuratorFail",
                        annotation.configurator().getName(),
                        pojo.getClass().getName()), e);
            }
        }
        sec = ServerEndpointConfig.Builder.create(pojo, path).
                decoders(Arrays.asList(annotation.decoders())).
                encoders(Arrays.asList(annotation.encoders())).
                subprotocols(Arrays.asList(annotation.subprotocols())).
                configurator(configurator).
                build();

        addEndpoint(sec);
    }


    boolean areEndpointsRegistered() {
        return endpointsRegistered;
    }


    /**
     * 直到WebSocket规范提供了这样的机制, 提供此Tomcat专有方法使应用程序能够以编程方式确定是否将单个请求升级到WebSocket.
     * <p>
     * Note: 此方法不被Tomcat使用，而是直接由第三方代码使用，不能删除.
     *
     * @param request 要升级的请求对象
     * @param response 要用升级结果填充的响应对象
     * @param sec 用于处理升级请求的服务器端点
     * @param pathParams 与升级请求相关联的路径参数
     *
     * @throws ServletException 如果配置错误阻止升级发生
     * @throws IOException 如果在升级过程中发生I/O错误
     */
    public void doUpgrade(HttpServletRequest request,
            HttpServletResponse response, ServerEndpointConfig sec,
            Map<String,String> pathParams)
            throws ServletException, IOException {
        UpgradeUtil.doUpgrade(this, request, response, sec, pathParams);
    }


    public WsMappingResult findMapping(String path) {

        // 防止在第一次尝试使用一个端点时, 注册一个附加端点
        if (addAllowed) {
            addAllowed = false;
        }

        // 检查精确匹配. 没有模板的简单案例.
        ServerEndpointConfig sec = configExactMatchMap.get(path);
        if (sec != null) {
            return new WsMappingResult(sec, Collections.<String, String>emptyMap());
        }

        // 没有精确匹配. 需要查找模板匹配.
        UriTemplate pathUriTemplate = null;
        try {
            pathUriTemplate = new UriTemplate(path);
        } catch (DeploymentException e) {
            // 路径无效，因此无法匹配WebSocketEndpoint
            return null;
        }

        // 必须匹配的段数
        Integer key = Integer.valueOf(pathUriTemplate.getSegmentCount());
        SortedSet<TemplatePathMatch> templateMatches =
                configTemplateMatchMap.get(key);

        if (templateMatches == null) {
            // 没有相同数量的段的模板，因此将没有匹配
            return null;
        }

        // 列表按标准化模板的字母顺序排列.
        // 正确匹配是第一个匹配的.
        Map<String,String> pathParams = null;
        for (TemplatePathMatch templateMatch : templateMatches) {
            pathParams = templateMatch.getUriTemplate().match(pathUriTemplate);
            if (pathParams != null) {
                sec = templateMatch.getConfig();
                break;
            }
        }

        if (sec == null) {
            // No match
            return null;
        }

        return new WsMappingResult(sec, pathParams);
    }



    public boolean isEnforceNoAddAfterHandshake() {
        return enforceNoAddAfterHandshake;
    }


    public void setEnforceNoAddAfterHandshake(
            boolean enforceNoAddAfterHandshake) {
        this.enforceNoAddAfterHandshake = enforceNoAddAfterHandshake;
    }


    protected WsWriteTimeout getTimeout() {
        return wsWriteTimeout;
    }


    /**
     * {@inheritDoc}
     *
     * 重写使该包中其他类可见.
     */
    @Override
    protected void registerSession(Endpoint endpoint, WsSession wsSession) {
        super.registerSession(endpoint, wsSession);
        if (wsSession.isOpen() &&
                wsSession.getUserPrincipal() != null &&
                wsSession.getHttpSessionId() != null) {
            registerAuthenticatedSession(wsSession,
                    wsSession.getHttpSessionId());
        }
    }


    /**
     * {@inheritDoc}
     *
     * 重写使该包中其他类可见.
     */
    @Override
    protected void unregisterSession(Endpoint endpoint, WsSession wsSession) {
        if (wsSession.getUserPrincipal() != null &&
                wsSession.getHttpSessionId() != null) {
            unregisterAuthenticatedSession(wsSession,
                    wsSession.getHttpSessionId());
        }
        super.unregisterSession(endpoint, wsSession);
    }


    private void registerAuthenticatedSession(WsSession wsSession,
            String httpSessionId) {
        Set<WsSession> wsSessions = authenticatedSessions.get(httpSessionId);
        if (wsSessions == null) {
            wsSessions = Collections.newSetFromMap(
                     new ConcurrentHashMap<WsSession,Boolean>());
             authenticatedSessions.putIfAbsent(httpSessionId, wsSessions);
             wsSessions = authenticatedSessions.get(httpSessionId);
        }
        wsSessions.add(wsSession);
    }


    private void unregisterAuthenticatedSession(WsSession wsSession,
            String httpSessionId) {
        Set<WsSession> wsSessions = authenticatedSessions.get(httpSessionId);
        // wsSessions 将会是 null, 如果HTTP会话已结束
        if (wsSessions != null) {
            wsSessions.remove(wsSession);
        }
    }


    public void closeAuthenticatedSession(String httpSessionId) {
        Set<WsSession> wsSessions = authenticatedSessions.remove(httpSessionId);

        if (wsSessions != null && !wsSessions.isEmpty()) {
            for (WsSession wsSession : wsSessions) {
                try {
                    wsSession.close(AUTHENTICATED_HTTP_SESSION_CLOSED);
                } catch (IOException e) {
                    // 关闭期间的任何IOException都将被捕获，并且onError 方法将被调用.
                }
            }
        }
    }


    private static void validateEncoders(Class<? extends Encoder>[] encoders)
            throws DeploymentException {

        for (Class<? extends Encoder> encoder : encoders) {
            // 需要实例化解码器以确保它是有效的，并且如果没有部署，则可能失败
            @SuppressWarnings("unused")
            Encoder instance;
            try {
                encoder.getConstructor().newInstance();
            } catch(ReflectiveOperationException e) {
                throw new DeploymentException(sm.getString(
                        "serverContainer.encoderFail", encoder.getName()), e);
            }
        }
    }


    private static class TemplatePathMatch {
        private final ServerEndpointConfig config;
        private final UriTemplate uriTemplate;

        public TemplatePathMatch(ServerEndpointConfig config,
                UriTemplate uriTemplate) {
            this.config = config;
            this.uriTemplate = uriTemplate;
        }


        public ServerEndpointConfig getConfig() {
            return config;
        }


        public UriTemplate getUriTemplate() {
            return uriTemplate;
        }
    }


    /**
     * 此Comparator 实现是线程安全的，因此只创建一个实例.
     */
    private static class TemplatePathMatchComparator
            implements Comparator<TemplatePathMatch> {

        private static final TemplatePathMatchComparator INSTANCE =
                new TemplatePathMatchComparator();

        public static TemplatePathMatchComparator getInstance() {
            return INSTANCE;
        }

        private TemplatePathMatchComparator() {
            // Hide default constructor
        }

        @Override
        public int compare(TemplatePathMatch tpm1, TemplatePathMatch tpm2) {
            return tpm1.getUriTemplate().getNormalizedPath().compareTo(
                    tpm2.getUriTemplate().getNormalizedPath());
        }
    }
}
