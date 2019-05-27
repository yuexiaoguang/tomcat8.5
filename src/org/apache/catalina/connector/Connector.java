package org.apache.catalina.connector;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import javax.management.ObjectName;

import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;
import org.apache.tomcat.util.res.StringManager;


/**
 * Coyote连接器实现.
 */
public class Connector extends LifecycleMBeanBase  {

    private static final Log log = LogFactory.getLog(Connector.class);


    /**
     * 启用外观回收.
     */
    public static final boolean RECYCLE_FACADES =
        Boolean.parseBoolean(System.getProperty("org.apache.catalina.connector.RECYCLE_FACADES", "false"));


    // ------------------------------------------------------------ Constructor

    public Connector() {
        this(null);
    }

    public Connector(String protocol) {
        setProtocol(protocol);
        // 实例化协议处理程序
        ProtocolHandler p = null;
        try {
            Class<?> clazz = Class.forName(protocolHandlerClassName);
            p = (ProtocolHandler) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            log.error(sm.getString(
                    "coyoteConnector.protocolHandlerInstantiationFailed"), e);
        } finally {
            this.protocolHandler = p;
        }

        if (Globals.STRICT_SERVLET_COMPLIANCE) {
            uriCharset = StandardCharsets.ISO_8859_1;
        } else {
            uriCharset = StandardCharsets.UTF_8;
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 关联的<code>Service</code>.
     */
    protected Service service = null;


    /**
     * 是否允许TRACE?
     */
    protected boolean allowTrace = false;


    /**
     * 异步请求的默认超时(ms).
     */
    protected long asyncTimeout = 30000;


    /**
     * 是否启用DNS查找.
     */
    protected boolean enableLookups = false;


    /*
     * 是否启用生成 X-Powered-By 响应 header?
     */
    protected boolean xpoweredBy = false;


    /**
     * 监听请求的端口号.
     */
    protected int port = -1;


    /**
     * 代理服务器名称.
     * 当操作代理服务器后面的Tomcat时，这非常有用, 因此将得到正确的创建.
     * 如果未指定, 使用<code>Host</code> header包含的服务器名称.
     */
    protected String proxyName = null;


    /**
     * 代理服务器端口. 当操作代理服务器后面的Tomcat时，这非常有用, 因此将得到正确的创建.
     * 如果未指定, 使用<code>port</code>属性指定的端口号.
     */
    protected int proxyPort = 0;


    /**
     * non-SSL 转发到 SSL 的端口号.
     */
    protected int redirectPort = 443;


    /**
     * 将通过此连接器接收的所有请求设置的请求方案.
     */
    protected String scheme = "http";


    /**
     * 将通过该连接器接收的所有请求设置的安全连接标志.
     */
    protected boolean secure = false;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Connector.class);


    /**
     * 请求允许的最大cookies数. 使用不小于零的值. 默认为 200.
     */
    private int maxCookieCount = 200;

    /**
     * 参数最大数量(GET 加 POST)容器将自动解析. 默认为10000. 小于0的值意味着没有限制.
     */
    protected int maxParameterCount = 10000;

    /**
     * 将由容器自动解析的POST的最大大小. 默认2MB.
     */
    protected int maxPostSize = 2 * 1024 * 1024;


    /**
     * 在验证期间容器将保存的POST最大大小. 默认4kB
     */
    protected int maxSavePostSize = 4 * 1024;

    /**
     * 将解析的HTTP方法的逗号分隔列表，根据POST-style规则解析 application/x-www-form-urlencoded 请求主体.
     */
    protected String parseBodyMethods = "POST";

    /**
     * {@link #parseBodyMethods}定义的方法集合.
     */
    protected HashSet<String> parseBodyMethodsSet;


    /**
     * 使用基于IP的虚拟主机.
     */
    protected boolean useIPVHosts = false;


    /**
     * Coyote协议处理程序类名.
     * 默认为 Coyote HTTP/1.1 protocolHandler.
     */
    protected String protocolHandlerClassName =
        "org.apache.coyote.http11.Http11NioProtocol";


    /**
     * Coyote协议处理程序.
     */
    protected final ProtocolHandler protocolHandler;


    /**
     * Coyote adapter.
     */
    protected Adapter adapter = null;


    /**
     * URI编码.
     *
     * @deprecated This will be removed in 9.0.x onwards
     */
    @Deprecated
    protected String URIEncoding = null;


    /**
     * @deprecated This will be removed in 9.0.x onwards
     */
    @Deprecated
    protected String URIEncodingLower = null;


    private Charset uriCharset = StandardCharsets.UTF_8;


    /**
     * URI编码作为主体.
     */
    protected boolean useBodyEncodingForURI = false;


    protected static final HashMap<String,String> replacements = new HashMap<>();
    static {
        replacements.put("acceptCount", "backlog");
        replacements.put("connectionLinger", "soLinger");
        replacements.put("connectionTimeout", "soTimeout");
        replacements.put("rootFile", "rootfile");
    }


    // ------------------------------------------------------------- Properties

    /**
     * 从协议处理程序返回属性.
     *
     * @param name 属性名
     * @return 属性值
     */
    public Object getProperty(String name) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = replacements.get(name);
        }
        return IntrospectionUtils.getProperty(protocolHandler, repl);
    }


    /**
     * 设置配置的属性.
     *
     * @param name 属性名
     * @param value 属性值
     * @return <code>true</code>如果属性已成功设置
     */
    public boolean setProperty(String name, String value) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = replacements.get(name);
        }
        return IntrospectionUtils.setProperty(protocolHandler, repl, value);
    }


    /**
     * 从协议处理程序返回属性.
     *
     * @param name 属性名
     * @return 属性值
     */
    public Object getAttribute(String name) {
        return getProperty(name);
    }


    /**
     * 在协议处理程序上设置属性.
     *
     * @param name 属性名
     * @param value 属性值
     */
    public void setAttribute(String name, Object value) {
        setProperty(name, String.valueOf(value));
    }


    /**
     * @return 关联的<code>Service</code>
     */
    public Service getService() {
        return this.service;
    }


    /**
     * 设置关联的<code>Service</code>
     *
     * @param service 属于这个Engine的service
     */
    public void setService(Service service) {
        this.service = service;
    }


    /**
     * @return <code>true</code>如果允许跟踪方法. 默认为<code>false</code>.
     */
    public boolean getAllowTrace() {
        return this.allowTrace;
    }


    /**
     * 禁用或启用跟踪HTTP方法.
     *
     * @param allowTrace The new allowTrace flag
     */
    public void setAllowTrace(boolean allowTrace) {
        this.allowTrace = allowTrace;
        setProperty("allowTrace", String.valueOf(allowTrace));
    }


    /**
     * @return 异步请求的缺省超时(ms).
     */
    public long getAsyncTimeout() {
        return asyncTimeout;
    }


    /**
     * 设置异步请求的默认超时时间.
     *
     * @param asyncTimeout 超时时间 ms.
     */
    public void setAsyncTimeout(long asyncTimeout) {
        this.asyncTimeout= asyncTimeout;
        setProperty("asyncTimeout", String.valueOf(asyncTimeout));
    }


    /**
     * @return 启用DNS查找.
     */
    public boolean getEnableLookups() {
        return this.enableLookups;
    }


    /**
     * 启用DNS查找.
     *
     * @param enableLookups 启用DNS查找
     */
    public void setEnableLookups(boolean enableLookups) {
        this.enableLookups = enableLookups;
        setProperty("enableLookups", String.valueOf(enableLookups));
    }


    public int getMaxCookieCount() {
        return maxCookieCount;
    }


    public void setMaxCookieCount(int maxCookieCount) {
        this.maxCookieCount = maxCookieCount;
    }


    /**
     * @return 参数最大数量(GET 加 POST)容器将自动解析. 小于0的值意味着没有限制.
     */
    public int getMaxParameterCount() {
        return maxParameterCount;
    }


    /**
     * 设置参数最大数量(GET 加 POST)容器将自动解析. 小于0的值意味着没有限制.
     *
     * @param maxParameterCount The new setting
     */
    public void setMaxParameterCount(int maxParameterCount) {
        this.maxParameterCount = maxParameterCount;
        setProperty("maxParameterCount", String.valueOf(maxParameterCount));
    }


    /**
     * @return 将由容器自动解析的POST的最大大小. 默认2MB.
     */
    public int getMaxPostSize() {
        return maxPostSize;
    }


    /**
     * 设置将由容器自动解析的POST的最大大小. 默认2MB.
     *
     * @param maxPostSize 将由容器自动解析的POST的最大大小
     */
    public void setMaxPostSize(int maxPostSize) {
        this.maxPostSize = maxPostSize;
        setProperty("maxPostSize", String.valueOf(maxPostSize));
    }


    /**
     * @return 在验证期间容器将保存的POST最大大小. 默认4kB
     */
    public int getMaxSavePostSize() {
        return maxSavePostSize;
    }


    /**
     * 设置在验证期间容器将保存的POST最大大小. 默认4kB.
     *
     * @param maxSavePostSize 将保存的POST最大大小
     */
    public void setMaxSavePostSize(int maxSavePostSize) {
        this.maxSavePostSize = maxSavePostSize;
        setProperty("maxSavePostSize", String.valueOf(maxSavePostSize));
    }


    /**
     * @return 支持正文参数分析的HTTP方法
     */
    public String getParseBodyMethods() {
        return this.parseBodyMethods;
    }


    /**
     * 设置支持正文参数分析的HTTP方法. 默认为<code>POST</code>.
     *
     * @param methods http方法名称的逗号分隔列表
     */
    public void setParseBodyMethods(String methods) {

        HashSet<String> methodSet = new HashSet<>();

        if (null != methods) {
            methodSet.addAll(Arrays.asList(methods.split("\\s*,\\s*")));
        }

        if (methodSet.contains("TRACE")) {
            throw new IllegalArgumentException(sm.getString("coyoteConnector.parseBodyMethodNoTrace"));
        }

        this.parseBodyMethods = methods;
        this.parseBodyMethodsSet = methodSet;
        setProperty("parseBodyMethods", methods);
    }


    protected boolean isParseBodyMethod(String method) {
        return parseBodyMethodsSet.contains(method);
    }


    /**
     * @return 此连接器被配置监听请求的端口号. 0意味着在套接字绑定时选择一个随机空闲端口.
     */
    public int getPort() {
        return this.port;
    }


    /**
     * 设置监听请求的端口号.
     *
     * @param port 端口号
     */
    public void setPort(int port) {
        this.port = port;
        setProperty("port", String.valueOf(port));
    }


    /**
     * @return 此连接器正在监听请求的端口号.
     * 如果{@link #getPort}返回零，那么这个方法将报告实际绑定的端口.
     */
    public int getLocalPort() {
        return ((Integer) getProperty("localPort")).intValue();
    }


    /**
     * @return 使用的Coyote协议处理器.
     */
    public String getProtocol() {
        if (("org.apache.coyote.http11.Http11NioProtocol".equals(getProtocolHandlerClassName()) &&
                    (!AprLifecycleListener.isAprAvailable() || !AprLifecycleListener.getUseAprConnector())) ||
                "org.apache.coyote.http11.Http11AprProtocol".equals(getProtocolHandlerClassName()) &&
                    AprLifecycleListener.getUseAprConnector()) {
            return "HTTP/1.1";
        } else if (("org.apache.coyote.ajp.AjpNioProtocol".equals(getProtocolHandlerClassName()) &&
                    (!AprLifecycleListener.isAprAvailable() || !AprLifecycleListener.getUseAprConnector())) ||
                "org.apache.coyote.ajp.AjpAprProtocol".equals(getProtocolHandlerClassName()) &&
                    AprLifecycleListener.getUseAprConnector()) {
            return "AJP/1.3";
        }
        return getProtocolHandlerClassName();
    }


    /**
     * 设置连接器使用的Coyote协议.
     *
     * @param protocol Coyote协议名
     *
     * @deprecated Will be removed in Tomcat 9. Protocol must be configured via
     *             the constructor
     */
    @Deprecated
    public void setProtocol(String protocol) {

        boolean aprConnector = AprLifecycleListener.isAprAvailable() &&
                AprLifecycleListener.getUseAprConnector();

        if ("HTTP/1.1".equals(protocol) || protocol == null) {
            if (aprConnector) {
                setProtocolHandlerClassName("org.apache.coyote.http11.Http11AprProtocol");
            } else {
                setProtocolHandlerClassName("org.apache.coyote.http11.Http11NioProtocol");
            }
        } else if ("AJP/1.3".equals(protocol)) {
            if (aprConnector) {
                setProtocolHandlerClassName("org.apache.coyote.ajp.AjpAprProtocol");
            } else {
                setProtocolHandlerClassName("org.apache.coyote.ajp.AjpNioProtocol");
            }
        } else {
            setProtocolHandlerClassName(protocol);
        }
    }


    /**
     * @return 使用的Coyote协议处理器的类名.
     */
    public String getProtocolHandlerClassName() {
        return this.protocolHandlerClassName;
    }


    /**
     * 设置连接器使用的Coyote协议处理器类名.
     *
     * @param protocolHandlerClassName 类名
     *
     * @deprecated Will be removed in Tomcat 9. Protocol must be configured via
     *             the constructor
     */
    @Deprecated
    public void setProtocolHandlerClassName(String protocolHandlerClassName) {
        this.protocolHandlerClassName = protocolHandlerClassName;
    }


    /**
     * @return 与连接器关联的协议处理程序.
     */
    public ProtocolHandler getProtocolHandler() {
        return this.protocolHandler;
    }


    /**
     * @return 这个Connector的代理服务器名称.
     */
    public String getProxyName() {
        return this.proxyName;
    }


    /**
     * 设置这个Connector的代理服务器名称.
     *
     * @param proxyName 代理服务器名
     */
    public void setProxyName(String proxyName) {

        if(proxyName != null && proxyName.length() > 0) {
            this.proxyName = proxyName;
        } else {
            this.proxyName = null;
        }
        setProperty("proxyName", this.proxyName);
    }


    /**
     * @return 这个Connector的代理服务器端口.
     */
    public int getProxyPort() {
        return this.proxyPort;
    }


    /**
     * 设置这个Connector的代理服务器端口.
     *
     * @param proxyPort 代理服务器端口
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        setProperty("proxyPort", String.valueOf(proxyPort));
    }


    /**
     * @return 请求重定向的端口号，如果它出现在非SSL端口上，则受需要SSL的安全约束的传输保证.
     */
    public int getRedirectPort() {
        return this.redirectPort;
    }


    /**
     * 设置重定向端口号.
     *
     * @param redirectPort 重定向端口号 (non-SSL 到 SSL)
     */
    public void setRedirectPort(int redirectPort) {
        this.redirectPort = redirectPort;
        setProperty("redirectPort", String.valueOf(redirectPort));
    }


    /**
     * @return 将分配给通过该连接器接收的请求的方案. 默认值是"http".
     */
    public String getScheme() {
        return this.scheme;
    }


    /**
     * 设置将分配给通过该连接器接收的请求的方案.
     *
     * @param scheme The new scheme
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }


    /**
     * @return 将被分配给通过该连接器接收的请求的安全连接标志. 默认为 "false".
     */
    public boolean getSecure() {
        return this.secure;
    }


    /**
     * 设置将被分配给通过该连接器接收的请求的安全连接标志.
     *
     * @param secure 安全连接标志
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
        setProperty("secure", Boolean.toString(secure));
    }


    /**
     * @return 用于URI的字符编码的名称.
     */
    public String getURIEncoding() {
        return uriCharset.name();
    }


    /**
     * @return 使用小写的URI使用的字符编码.
     *
     * @deprecated This will be removed in 9.0.x onwards
     */
    @Deprecated
    public String getURIEncodingLower() {
        return uriCharset.name().toLowerCase(Locale.ENGLISH);
    }


    /**
     * @return 用于将原始URI字节（在%nn解码之后）转换为字符的 Charset. 永远不会是 null
     */
    public Charset getURICharset() {
        return uriCharset;
    }

    /**
     * 设置用于URI的URI编码.
     *
     * @param URIEncoding URI字符编码.
     */
    public void setURIEncoding(String URIEncoding) {
        try {
            uriCharset = B2CConverter.getCharset(URIEncoding);
        } catch (UnsupportedEncodingException e) {
            log.warn(sm.getString("coyoteConnector.invalidEncoding",
                    URIEncoding, uriCharset.name()), e);
        }
        setProperty("uRIEncoding", URIEncoding);
    }


    /**
     * @return true 如果实体主体编码应该用于URI.
     */
    public boolean getUseBodyEncodingForURI() {
        return this.useBodyEncodingForURI;
    }


    /**
     * 实体主体编码是否应该用于URI.
     *
     * @param useBodyEncodingForURI The new value for the flag.
     */
    public void setUseBodyEncodingForURI(boolean useBodyEncodingForURI) {
        this.useBodyEncodingForURI = useBodyEncodingForURI;
        setProperty("useBodyEncodingForURI", String.valueOf(useBodyEncodingForURI));
    }

    /**
     * 启用或禁用X-Powered-By 响应 header的生成.
     *
     * @return <code>true</code>如果启用X-Powered-By 响应 header的生成, 否则false
     */
    public boolean getXpoweredBy() {
        return xpoweredBy;
    }


    /**
     * 启用或禁用X-Powered-By 响应 header的生成.
     *
     * @param xpoweredBy true如果启用X-Powered-By 响应 header的生成, 否则false
     */
    public void setXpoweredBy(boolean xpoweredBy) {
        this.xpoweredBy = xpoweredBy;
        setProperty("xpoweredBy", String.valueOf(xpoweredBy));
    }


    /**
     * 启用基于IP的虚拟主机的使用.
     *
     * @param useIPVHosts <code>true</code>如果主机通过IP标识,
     *                    <code>false</code>如果主机通过名称标识.
     */
    public void setUseIPVHosts(boolean useIPVHosts) {
        this.useIPVHosts = useIPVHosts;
        setProperty("useIPVHosts", String.valueOf(useIPVHosts));
    }


    /**
     * 是否启用基于IP的虚拟主机.
     */
    public boolean getUseIPVHosts() {
        return useIPVHosts;
    }


    public String getExecutorName() {
        Object obj = protocolHandler.getExecutor();
        if (obj instanceof org.apache.catalina.Executor) {
            return ((org.apache.catalina.Executor) obj).getName();
        }
        return "Internal";
    }


    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        protocolHandler.addSslHostConfig(sslHostConfig);
    }


    public SSLHostConfig[] findSslHostConfigs() {
        return protocolHandler.findSslHostConfigs();
    }


    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        protocolHandler.addUpgradeProtocol(upgradeProtocol);
    }


    public UpgradeProtocol[] findUpgradeProtocols() {
        return protocolHandler.findUpgradeProtocols();
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 创建（或分配）并返回一个请求对象，用于将请求的内容指定给负责的容器.
     */
    public Request createRequest() {

        Request request = new Request();
        request.setConnector(this);
        return (request);

    }


    /**
     * 创建（或分配）并返回一个响应对象，用于接收来自负责的容器的响应内容.
     */
    public Response createResponse() {

        Response response = new Response();
        response.setConnector(this);
        return (response);

    }


    protected String createObjectNameKeyProperties(String type) {

        Object addressObj = getProperty("address");

        StringBuilder sb = new StringBuilder("type=");
        sb.append(type);
        sb.append(",port=");
        int port = getPort();
        if (port > 0) {
            sb.append(port);
        } else {
            sb.append("auto-");
            sb.append(getProperty("nameIndex"));
        }
        String address = "";
        if (addressObj instanceof InetAddress) {
            address = ((InetAddress) addressObj).getHostAddress();
        } else if (addressObj != null) {
            address = addressObj.toString();
        }
        if (address.length() > 0) {
            sb.append(",address=");
            sb.append(ObjectName.quote(address));
        }
        return sb.toString();
    }


    /**
     * 暂停连接器.
     */
    public void pause() {
        try {
            protocolHandler.pause();
        } catch (Exception e) {
            log.error(sm.getString("coyoteConnector.protocolHandlerPauseFailed"), e);
        }
    }


    /**
     * 恢复连接器.
     */
    public void resume() {
        try {
            protocolHandler.resume();
        } catch (Exception e) {
            log.error(sm.getString("coyoteConnector.protocolHandlerResumeFailed"), e);
        }
    }


    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        // Initialize adapter
        adapter = new CoyoteAdapter(this);
        protocolHandler.setAdapter(adapter);

        // 确保 parseBodyMethodsSet 有一个默认的
        if (null == parseBodyMethodsSet) {
            setParseBodyMethods(getParseBodyMethods());
        }

        if (protocolHandler.isAprRequired() && !AprLifecycleListener.isAprAvailable()) {
            throw new LifecycleException(sm.getString("coyoteConnector.protocolHandlerNoApr",
                    getProtocolHandlerClassName()));
        }
        if (AprLifecycleListener.isAprAvailable() && AprLifecycleListener.getUseOpenSSL() &&
                protocolHandler instanceof AbstractHttp11JsseProtocol) {
            AbstractHttp11JsseProtocol<?> jsseProtocolHandler =
                    (AbstractHttp11JsseProtocol<?>) protocolHandler;
            if (jsseProtocolHandler.isSSLEnabled() &&
                    jsseProtocolHandler.getSslImplementationName() == null) {
                // OpenSSL与JSSE配置兼容, 所以如果APR可用的话使用它
                jsseProtocolHandler.setSslImplementationName(OpenSSLImplementation.class.getName());
            }
        }

        try {
            protocolHandler.init();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerInitializationFailed"), e);
        }
    }


    /**
     * 开始处理请求.
     *
     * @exception LifecycleException 如果发生致命启动错误
     */
    @Override
    protected void startInternal() throws LifecycleException {

        // 启动之前验证设置
        if (getPort() < 0) {
            throw new LifecycleException(sm.getString(
                    "coyoteConnector.invalidPort", Integer.valueOf(getPort())));
        }

        setState(LifecycleState.STARTING);

        try {
            protocolHandler.start();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerStartFailed"), e);
        }
    }


    /**
     * 终止处理请求.
     *
     * @exception LifecycleException 如果发生致命关闭错误
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);

        try {
            protocolHandler.stop();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerStopFailed"), e);
        }
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        try {
            protocolHandler.destroy();
        } catch (Exception e) {
            throw new LifecycleException(
                    sm.getString("coyoteConnector.protocolHandlerDestroyFailed"), e);
        }

        if (getService() != null) {
            getService().removeConnector(this);
        }

        super.destroyInternal();
    }


    @Override
    public String toString() {
        // Not worth caching this right now
        StringBuilder sb = new StringBuilder("Connector[");
        sb.append(getProtocol());
        sb.append('-');
        int port = getPort();
        if (port > 0) {
            sb.append(port);
        } else {
            sb.append("auto-");
            sb.append(getProperty("nameIndex"));
        }
        sb.append(']');
        return sb.toString();
    }


    // -------------------- JMX registration  --------------------

    @Override
    protected String getDomainInternal() {
        Service s = getService();
        if (s == null) {
            return null;
        } else {
            return service.getDomain();
        }
    }

    @Override
    protected String getObjectNameKeyProperties() {
        return createObjectNameKeyProperties("Connector");
    }

}
