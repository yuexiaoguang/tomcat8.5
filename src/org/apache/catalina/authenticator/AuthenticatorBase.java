package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ClientAuthConfig;
import javax.security.auth.message.config.RegistrationListener;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.TomcatPrincipal;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.jaspic.CallbackHandlerImpl;
import org.apache.catalina.authenticator.jaspic.MessageInfoImpl;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.util.SessionIdGeneratorBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.res.StringManager;

/**
 * <b>Valve</b>接口的基础实现类，强制执行Web应用程序部署描述符中的<code>&lt;security-constraint&gt;</code>元素. 
 * 此功能是作为Valve实现的,因此可忽略的环境中不需要这些功能.  每个支持的身份验证方法的单独实现可以根据需要对这个基类进行子类划分.
 * <p>
 * <b>使用约束</b>:  当使用这个类时, 附加的Context(或层次结构中的父级Container) 必须有一个关联的Realm，
 * 可用于验证用户以及已分配给它们的角色
 * <p>
 * <b>使用约束</b>: 这个Valve只用于处理HTTP请求.  其他类型的请求都将被直接通过.
 */
public abstract class AuthenticatorBase extends ValveBase
        implements Authenticator, RegistrationListener {

    private static final Log log = LogFactory.getLog(AuthenticatorBase.class);

    /**
     * "Expires" header 总是设置为 Date(1), 因此只生成一次
     */
    private static final String DATE_ONE =
            (new SimpleDateFormat(FastHttpDateFormat.RFC1123_DATE, Locale.US)).format(new Date(1));

    private static final AuthConfigProvider NO_PROVIDER_AVAILABLE = new NoOpAuthConfigProvider();

    protected static final StringManager sm = StringManager.getManager(AuthenticatorBase.class);

    /**
     * Authentication header
     */
    protected static final String AUTH_HEADER_NAME = "WWW-Authenticate";

    /**
     * 默认验证域名称.
     */
    protected static final String REALM_NAME = "Authentication required";

    protected static String getRealmName(Context context) {
        if (context == null) {
            // Very unlikely
            return REALM_NAME;
        }

        LoginConfig config = context.getLoginConfig();
        if (config == null) {
            return REALM_NAME;
        }

        String result = config.getRealmName();
        if (result == null) {
            return REALM_NAME;
        }

        return result;
    }

    // ------------------------------------------------------ Constructor

    public AuthenticatorBase() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * 在用户被认证后，是否始终使用会话?
     * 这可能会提供一些性能上的好处，因为会话可以用来缓存已验证的Principal, 因此，消除了在每个请求上通过Realm认证用户的需要.
     * 这可能对组合有帮助, 例如BASIC 验证和JNDIRealm 或DataSourceRealm一起使用. 然而，也会有创建和GC会话的性能成本.
     * 默认情况下, 不会创建会话.
     */
    protected boolean alwaysUseSession = false;

    /**
     * 如果请求是HTTP会话的一部分，是否应该缓存经过身份验证的Principal？
     */
    protected boolean cache = true;

    /**
     * 在成功的身份验证中是否应该更改会话ID以防止会话固定攻击?
     */
    protected boolean changeSessionIdOnAuthentication = true;

    /**
     * 这个Valve关联的Context.
     */
    protected Context context = null;

    /**
     * 确定是否禁用代理缓存的标志, 或把问题上升到应用的开发者.
     */
    protected boolean disableProxyCaching = true;

    /**
     * 确定是否禁用与IE不兼容的标头的代理缓存.
     */
    protected boolean securePagesWithPragma = false;

    /**
     * 生成SSO会话标识符时，要使用的安全随机数生成器类的Java类名.
     * 随机数生成器类必须是self-seeding的，并且具有零参数构造函数. 如果未指定, 将生成一个{@link java.security.SecureRandom}实例.
     */
    protected String secureRandomClass = null;

    /**
     * 用于创建{@link java.security.SecureRandom}实例的算法的名称，该实例用于生成SSO会话ID.
     * 如果未指定算法, 将使用SHA1PRNG. 
     */
    protected String secureRandomAlgorithm = "SHA1PRNG";

    /**
     * 用于创建{@link java.security.SecureRandom}实例的提供者的名称，该实例用于生成SSO会话ID.
     * 默认使用SHA1PRNG.
     */
    protected String secureRandomProvider = null;

    /**
     * JASPIC回调处理类的名称.
     * 默认使用{@link org.apache.catalina.authenticator.jaspic.CallbackHandlerImpl}.
     */
    protected String jaspicCallbackHandlerClass = null;

    protected SessionIdGeneratorBase sessionIdGenerator = null;

    /**
     * 请求处理链中的SingleSignOn 实现类.
     */
    protected SingleSignOn sso = null;

    private volatile String jaspicAppContextID = null;
    private volatile AuthConfigProvider jaspicProvider = null;


    // ------------------------------------------------------------- Properties

    public boolean getAlwaysUseSession() {
        return alwaysUseSession;
    }

    public void setAlwaysUseSession(boolean alwaysUseSession) {
        this.alwaysUseSession = alwaysUseSession;
    }

    /**
     * 返回是否缓存已验证的Principal.
     *
     * @return <code>true</code> 如果已验证的Principal将被缓存,
     *         否则<code>false</code>
     */
    public boolean getCache() {
        return this.cache;
    }

    /**
     * 设置是否缓存已验证的Principal.
     *
     * @param cache The new cache flag
     */
    public void setCache(boolean cache) {
        this.cache = cache;
    }

    /**
     * 返回这个Valve关联的Container.
     */
    @Override
    public Container getContainer() {
        return this.context;
    }

    /**
     * 设置这个Valve关联的Container.
     *
     * @param container 关联的容器
     */
    @Override
    public void setContainer(Container container) {

        if (container != null && !(container instanceof Context)) {
            throw new IllegalArgumentException(sm.getString("authenticator.notContext"));
        }

        super.setContainer(container);
        this.context = (Context) container;

    }

    /**
     * 是否添加标头来禁用代理缓存.
     *
     * @return <code>true</code>如果添加header, 否则<code>false</code>
     */
    public boolean getDisableProxyCaching() {
        return disableProxyCaching;
    }

    /**
     * 是否添加标头来禁用代理缓存.
     *
     * @param nocache
     *            <code>true</code>如果添加标头以禁用代理缓存,
     *            否则<code>false</code>.
     */
    public void setDisableProxyCaching(boolean nocache) {
        disableProxyCaching = nocache;
    }

    /**
     * 返回是否禁用与IE不兼容的标头的代理缓存的标志.
     *
     * @return <code>true</code>如果应该使用Pragma header, 否则<code>false</code>
     */
    public boolean getSecurePagesWithPragma() {
        return securePagesWithPragma;
    }

    /**
     * 设置是否禁用与IE不兼容的标头的代理缓存的标志.
     *
     * @param securePagesWithPragma
     *            <code>true</code>如果添加了与SSL下的IE Office文档不兼容的标头，但它解决了Mozilla中的缓存问题.
     */
    public void setSecurePagesWithPragma(boolean securePagesWithPragma) {
        this.securePagesWithPragma = securePagesWithPragma;
    }

    /**
     * 在成功的身份验证中是否应该更改会话ID以防止会话固定攻击?
     *
     * @return <code>true</code>认证成功之后修改会话ID, <code>false</code>不修改.
     */
    public boolean getChangeSessionIdOnAuthentication() {
        return changeSessionIdOnAuthentication;
    }

    /**
     * 在成功的身份验证中是否应该更改会话ID以防止会话固定攻击?
     *
     * @param changeSessionIdOnAuthentication <code>true</code>认证成功之后修改会话ID, <code>false</code>不修改.
     */
    public void setChangeSessionIdOnAuthentication(boolean changeSessionIdOnAuthentication) {
        this.changeSessionIdOnAuthentication = changeSessionIdOnAuthentication;
    }

    /**
     * 返回安全随机数生成器类名.
     *
     * @return SecureRandom实现类的完全限定名
     */
    public String getSecureRandomClass() {
        return this.secureRandomClass;
    }

    /**
     * 设置安全随机数生成器类名.
     *
     * @param secureRandomClass 类名
     */
    public void setSecureRandomClass(String secureRandomClass) {
        this.secureRandomClass = secureRandomClass;
    }

    /**
     * 返回安全随机数生成器算法名.
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }

    /**
     * 设置安全随机数生成器算法名.
     *
     * @param secureRandomAlgorithm 安全随机数生成器算法名
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }

    /**
     * 返回安全随机数生成器提供者名称.
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }

    /**
     * 设置安全随机数生成器提供者名称.
     *
     * @param secureRandomProvider 安全随机数生成器提供者名称
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }

    /**
     * 返回JASPIC回调处理类名
     */
    public String getJaspicCallbackHandlerClass() {
        return jaspicCallbackHandlerClass;
    }

    /**
     * 设置JASPIC回调处理类名
     *
     * @param jaspicCallbackHandlerClass JASPIC回调处理类名
     */
    public void setJaspicCallbackHandlerClass(String jaspicCallbackHandlerClass) {
        this.jaspicCallbackHandlerClass = jaspicCallbackHandlerClass;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 在关联上下文的Web应用程序部署描述符中强制执行安全限制.
     *
     * @param request Request to be processed
     * @param response Response to be processed
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果处理的元素抛出此异常
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        if (log.isDebugEnabled()) {
            log.debug("Security checking request " + request.getMethod() + " " +
                    request.getRequestURI());
        }

        // 有一个缓存的经过身份验证的Principal来记录吗?
        if (cache) {
            Principal principal = request.getUserPrincipal();
            if (principal == null) {
                Session session = request.getSessionInternal(false);
                if (session != null) {
                    principal = session.getPrincipal();
                    if (principal != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("We have cached auth type " + session.getAuthType() +
                                    " for principal " + principal);
                        }
                        request.setAuthType(session.getAuthType());
                        request.setUserPrincipal(principal);
                    }
                }
            }
        }

        boolean authRequired = isContinuationRequired(request);

        // servlet可以通过注解指定安全约束.
        // 确保在约束检查之前已经对它们进行了处理
        Wrapper wrapper = request.getWrapper();
        if (wrapper != null) {
            wrapper.servletSecurityAnnotationScan();
        }

        Realm realm = this.context.getRealm();
        // 这个请求URI受到安全约束吗?
        SecurityConstraint[] constraints = realm.findSecurityConstraints(request, this.context);

        AuthConfigProvider jaspicProvider = getJaspicProvider();
        if (jaspicProvider != null) {
            authRequired = true;
        }

        if (constraints == null && !context.getPreemptiveAuthentication() && !authRequired) {
            if (log.isDebugEnabled()) {
                log.debug(" Not subject to any constraint");
            }
            getNext().invoke(request, response);
            return;
        }

        // 确保受限制的资源不被Web代理或浏览器缓存，因为缓存可能提供安全漏洞
        if (constraints != null && disableProxyCaching &&
                !"POST".equalsIgnoreCase(request.getMethod())) {
            if (securePagesWithPragma) {
                // Note: 这些会导致用IE下载文件的问题
                response.setHeader("Pragma", "No-cache");
                response.setHeader("Cache-Control", "no-cache");
            } else {
                response.setHeader("Cache-Control", "private");
            }
            response.setHeader("Expires", DATE_ONE);
        }

        if (constraints != null) {
            // 为该安全约束强制执行任何用户数据约束
            if (log.isDebugEnabled()) {
                log.debug(" Calling hasUserDataPermission()");
            }
            if (!realm.hasUserDataPermission(request, response, constraints)) {
                if (log.isDebugEnabled()) {
                    log.debug(" Failed hasUserDataPermission() test");
                }
                /*
                 * ASSERT: 验证器已经设置了适当的HTTP状态码, 所以不需要做任何特别的事情
                 */
                return;
            }
        }

        // 由于认证修改了对失败的响应, 必须首先验证allow-from-all.
        boolean hasAuthConstraint = false;
        if (constraints != null) {
            hasAuthConstraint = true;
            for (int i = 0; i < constraints.length && hasAuthConstraint; i++) {
                if (!constraints[i].getAuthConstraint()) {
                    hasAuthConstraint = false;
                } else if (!constraints[i].getAllRoles() &&
                        !constraints[i].getAuthenticatedUsers()) {
                    String[] roles = constraints[i].findAuthRoles();
                    if (roles == null || roles.length == 0) {
                        hasAuthConstraint = false;
                    }
                }
            }
        }

        if (!authRequired && hasAuthConstraint) {
            authRequired = true;
        }

        if (!authRequired && context.getPreemptiveAuthentication()) {
            authRequired =
                    request.getCoyoteRequest().getMimeHeaders().getValue("authorization") != null;
        }

        if (!authRequired && context.getPreemptiveAuthentication()
                && HttpServletRequest.CLIENT_CERT_AUTH.equals(getAuthMethod())) {
            X509Certificate[] certs = getRequestCertificates(request);
            authRequired = certs != null && certs.length > 0;
        }

        JaspicState jaspicState = null;

        if (authRequired) {
            if (log.isDebugEnabled()) {
                log.debug(" Calling authenticate()");
            }

            if (jaspicProvider != null) {
                jaspicState = getJaspicState(jaspicProvider, request, response, hasAuthConstraint);
                if (jaspicState == null) {
                    return;
                }
            }

            if (jaspicProvider == null && !doAuthenticate(request, response) ||
                    jaspicProvider != null &&
                            !authenticateJaspic(request, response, jaspicState, false)) {
                if (log.isDebugEnabled()) {
                    log.debug(" Failed authenticate() test");
                }
                /*
                 * ASSERT: 验证器已经设置了适当的HTTP状态码, 所以不需要做任何特别的事情
                 */
                return;
            }

        }

        if (constraints != null) {
            if (log.isDebugEnabled()) {
                log.debug(" Calling accessControl()");
            }
            if (!realm.hasResourcePermission(request, response, constraints, this.context)) {
                if (log.isDebugEnabled()) {
                    log.debug(" Failed accessControl() test");
                }
                /*
                 * ASSERT: AccessControl 方法已经设置了适当的HTTP状态码, 所以不需要做任何特别的事情
                 */
                return;
            }
        }

        // 满足所有指定的约束条件
        if (log.isDebugEnabled()) {
            log.debug(" Successfully passed all security constraints");
        }
        getNext().invoke(request, response);

        if (jaspicProvider != null) {
            secureResponseJspic(request, response, jaspicState);
        }
    }


    @Override
    public boolean authenticate(Request request, HttpServletResponse httpResponse)
            throws IOException {

        AuthConfigProvider jaspicProvider = getJaspicProvider();

        if (jaspicProvider == null) {
            return doAuthenticate(request, httpResponse);
        } else {
            Response response = request.getResponse();
            JaspicState jaspicState = getJaspicState(jaspicProvider, request, response, true);
            if (jaspicState == null) {
                return false;
            }

            boolean result = authenticateJaspic(request, response, jaspicState, true);

            secureResponseJspic(request, response, jaspicState);

            return result;
        }
    }


    private void secureResponseJspic(Request request, Response response, JaspicState state) {
        try {
            state.serverAuthContext.secureResponse(state.messageInfo, null);
            request.setRequest((HttpServletRequest) state.messageInfo.getRequestMessage());
            response.setResponse((HttpServletResponse) state.messageInfo.getResponseMessage());
        } catch (AuthException e) {
            log.warn(sm.getString("authenticator.jaspicSecureResponseFail"), e);
        }
    }


    private JaspicState getJaspicState(AuthConfigProvider jaspicProvider, Request request,
            Response response, boolean authMandatory) throws IOException {
        JaspicState jaspicState = new JaspicState();

        jaspicState.messageInfo =
                new MessageInfoImpl(request.getRequest(), response.getResponse(), authMandatory);

        try {
            CallbackHandler callbackHandler = createCallbackHandler();
            ServerAuthConfig serverAuthConfig = jaspicProvider.getServerAuthConfig(
                    "HttpServlet", jaspicAppContextID, callbackHandler);
            String authContextID = serverAuthConfig.getAuthContextID(jaspicState.messageInfo);
            jaspicState.serverAuthContext = serverAuthConfig.getAuthContext(authContextID, null, null);
        } catch (AuthException e) {
            log.warn(sm.getString("authenticator.jaspicServerAuthContextFail"), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return null;
        }

        return jaspicState;
    }

    private CallbackHandler createCallbackHandler() {
        CallbackHandler callbackHandler = null;
        if (jaspicCallbackHandlerClass == null) {
            callbackHandler = CallbackHandlerImpl.getInstance();
        } else {
            Class<?> clazz = null;
            try {
                clazz = Class.forName(jaspicCallbackHandlerClass, true,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                // Proceed with the retry below
            }

            try {
                if (clazz == null) {
                    clazz = Class.forName(jaspicCallbackHandlerClass);
                }
                callbackHandler = (CallbackHandler)clazz.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new SecurityException(e);
            }
        }

        return callbackHandler;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 提供子类来实现其特定的认证机制.
     *
     * @param request 触发认证的请求
     * @param response 与请求相关联的响应
     *
     * @return {@code true} 如果用户已被认证, 否则{@code false}, 在这种情况下，将向响应写入认证失败
     *
     * @throws IOException 如果在认证过程中发生I/O问题
     */
    protected abstract boolean doAuthenticate(Request request, HttpServletResponse response)
            throws IOException;


    /**
     * 验证器是否需要调用{@link #authenticate(Request, HttpServletResponse)}以继续在先前请求中启动的身份验证过程?
     *
     * @param request 当前正在处理的请求
     *
     * @return {@code true} 如果必须调用authenticate(), 否则{@code false}
     */
    protected boolean isContinuationRequired(Request request) {
        return false;
    }


    /**
     * 在<code>javax.servlet.request.X509Certificate</code>秘钥下查找请求中的X509证书链.
     * 如果未找到, 从CyOOT请求中触发提取证书链.
     *
     * @param request 要处理的请求
     *
     * @return X509证书链, 否则<code>null</code>.
     */
    protected X509Certificate[] getRequestCertificates(final Request request)
            throws IllegalStateException {

        X509Certificate certs[] =
                (X509Certificate[]) request.getAttribute(Globals.CERTIFICATES_ATTR);

        if ((certs == null) || (certs.length < 1)) {
            try {
                request.getCoyoteRequest().action(ActionCode.REQ_SSL_CERTIFICATE, null);
                certs = (X509Certificate[]) request.getAttribute(Globals.CERTIFICATES_ATTR);
            } catch (IllegalStateException ise) {
                // 请求正文太大，无法保存进缓冲区
                // 返回NULL将触发AUTH失效
            }
        }

        return certs;
    }

    /**
     * 将指定的单点登录标识符与指定会话关联.
     *
     * @param ssoId 单点登录标识符
     * @param session 关联的会话
     */
    protected void associate(String ssoId, Session session) {

        if (sso == null) {
            return;
        }
        sso.associate(ssoId, session);

    }


    private boolean authenticateJaspic(Request request, Response response, JaspicState state,
            boolean requirePrincipal) {

        boolean cachedAuth = checkForCachedAuthentication(request, response, false);
        Subject client = new Subject();
        AuthStatus authStatus;
        try {
            authStatus = state.serverAuthContext.validateRequest(state.messageInfo, client, null);
        } catch (AuthException e) {
            log.debug(sm.getString("authenticator.loginFail"), e);
            return false;
        }

        request.setRequest((HttpServletRequest) state.messageInfo.getRequestMessage());
        response.setResponse((HttpServletResponse) state.messageInfo.getResponseMessage());

        if (authStatus == AuthStatus.SUCCESS) {
            GenericPrincipal principal = getPrincipal(client);
            if (log.isDebugEnabled()) {
                log.debug("Authenticated user: " + principal);
            }
            if (principal == null) {
                request.setUserPrincipal(null);
                request.setAuthType(null);
                if (requirePrincipal) {
                    return false;
                }
            } else if (cachedAuth == false ||
                    !principal.getUserPrincipal().equals(request.getUserPrincipal())) {
                // 如果身份验证凭据被缓存且Principal没有更改，则跳过注册.
                request.setNote(Constants.REQ_JASPIC_SUBJECT_NOTE, client);
                @SuppressWarnings("rawtypes")// JASPIC API uses raw types
                Map map = state.messageInfo.getMap();
                if (map != null && map.containsKey("javax.servlet.http.registerSession")) {
                    register(request, response, principal, "JASPIC", null, null, true, true);
                } else {
                    register(request, response, principal, "JASPIC", null, null);
                }
            }
            return true;
        }
        return false;
    }


    private GenericPrincipal getPrincipal(Subject subject) {
        if (subject == null) {
            return null;
        }

        Set<GenericPrincipal> principals = subject.getPrivateCredentials(GenericPrincipal.class);
        if (principals.isEmpty()) {
            return null;
        }

        return principals.iterator().next();
    }


    /**
     * 检查用户是否已经在处理链中被早期验证过，或者是否有足够的信息来验证用户，而不需要进一步的用户交互.
     *
     * @param request 当前的请求
     * @param response 当前的响应
     * @param useSSO 应该使用SSO中的信息来尝试验证当前用户吗?
     *
     * @return <code>true</code>如果用户通过缓存进行了身份验证,
     *         否则<code>false</code>
     */
    protected boolean checkForCachedAuthentication(Request request, HttpServletResponse response, boolean useSSO) {

        // 用户是否已被认证?
        Principal principal = request.getUserPrincipal();
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("authenticator.check.found", principal.getName()));
            }
            // 将会话与任何现有的SSO会话相关联. 即使useSSO 是 false, 这将确保退出时协调的会话失效.
            if (ssoId != null) {
                associate(ssoId, request.getSessionInternal(true));
            }
            return true;
        }

        // 是否有一个SSO会话，可以尝试重新认证?
        if (useSSO && ssoId != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("authenticator.check.sso", ssoId));
            }
            /*
             * 尝试使用SSO缓存的数据重新进行身份验证.
             * 如果失败, 无论原始SSO登录是DIGEST 或 SSL (因为没有缓存的用户名和密码，所以无法重新认证自己),
             * 或者因为某种原因否定了用户的重新认证. 在这两种情况下，都必须提示用户登录
             */
            if (reauthenticateFromSSO(ssoId, request)) {
                return true;
            }
        }

        // Connector是否提供了一个需要认证的预认证的Principal?
        if (request.getCoyoteRequest().getRemoteUserNeedsAuthorization()) {
            String username = request.getCoyoteRequest().getRemoteUser().toString();
            if (username != null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("authenticator.check.authorize", username));
                }
                Principal authorized = context.getRealm().authenticate(username);
                if (authorized == null) {
                    // Realm 不认可用户. 从身份验证用户名创建没有角色的用户
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("authenticator.check.authorizeFail", username));
                    }
                    authorized = new GenericPrincipal(username, null, null);
                }
                String authType = request.getAuthType();
                if (authType == null || authType.length() == 0) {
                    authType = getAuthMethod();
                }
                register(request, response, authorized, authType, username, null);
                return true;
            }
        }
        return false;
    }

    /**
     * 尝试重新进行身份验证的 <code>Realm</code>, 使用<code>entry</code>参数中包含的凭据.
     *
     * @param ssoId 与调用者关联的SingleSignOn 会话标识符
     * @param request 需要进行身份验证的请求
     * 
     * @return <code>true</code> 如果来自SSL的重新认证发生
     */
    protected boolean reauthenticateFromSSO(String ssoId, Request request) {

        if (sso == null || ssoId == null) {
            return false;
        }

        boolean reauthenticated = false;

        Container parent = getContainer();
        if (parent != null) {
            Realm realm = parent.getRealm();
            if (realm != null) {
                reauthenticated = sso.reauthenticate(ssoId, realm, request);
            }
        }

        if (reauthenticated) {
            associate(ssoId, request.getSessionInternal(true));

            if (log.isDebugEnabled()) {
                log.debug(" Reauthenticated cached principal '" +
                        request.getUserPrincipal().getName() +
                        "' with auth type '" + request.getAuthType() + "'");
            }
        }

        return reauthenticated;
    }

    /**
     * 注册一个经过验证的Principal和身份验证类型, 在当前session中, 使用SingleSignOn valve. 设置要返回的cookie.
     *
     * @param request 处理的servlet请求
     * @param response 生成的servlet响应
     * @param principal 已注册的身份验证主体
     * @param authType 要注册的身份验证类型
     * @param username 用于验证的用户名
     * @param password 用于验证的密码
     */
    public void register(Request request, HttpServletResponse response, Principal principal,
            String authType, String username, String password) {
        register(request, response, principal, authType, username, password, alwaysUseSession, cache);
    }


    private void register(Request request, HttpServletResponse response, Principal principal,
            String authType, String username, String password, boolean alwaysUseSession,
            boolean cache) {

        if (log.isDebugEnabled()) {
            String name = (principal == null) ? "none" : principal.getName();
            log.debug("Authenticated '" + name + "' with type '" + authType + "'");
        }

        // 在请求中缓存身份验证信息
        request.setAuthType(authType);
        request.setUserPrincipal(principal);

        Session session = request.getSessionInternal(false);

        if (session != null) {
            // 如果主体是null， 这是一个注销. 不需要修改会话ID. See BZ 59043.
            if (changeSessionIdOnAuthentication && principal != null) {
                String oldId = null;
                if (log.isDebugEnabled()) {
                    oldId = session.getId();
                }
                Manager manager = request.getContext().getManager();
                manager.changeSessionId(session);
                request.changeSessionId(session.getId());
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("authenticator.changeSessionId",
                            oldId, session.getId()));
                }
            }
        } else if (alwaysUseSession) {
            session = request.getSessionInternal(true);
        }

        // 缓存会话中的身份验证信息
        if (cache) {
            if (session != null) {
                session.setAuthType(authType);
                session.setPrincipal(principal);
                if (username != null) {
                    session.setNote(Constants.SESS_USERNAME_NOTE, username);
                } else {
                    session.removeNote(Constants.SESS_USERNAME_NOTE);
                }
                if (password != null) {
                    session.setNote(Constants.SESS_PASSWORD_NOTE, password);
                } else {
                    session.removeNote(Constants.SESS_PASSWORD_NOTE);
                }
            }
        }

        // 构造返回客户端的cookie
        if (sso == null) {
            return;
        }

        // 仅在SSO尚未为现有条目设置注释时才创建新的SSO条目(这与后续的DIGEST和SSL认证上下文请求有关)
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (ssoId == null) {
            // 构造返回客户端的cookie
            ssoId = sessionIdGenerator.generateSessionId();
            Cookie cookie = new Cookie(Constants.SINGLE_SIGN_ON_COOKIE, ssoId);
            cookie.setMaxAge(-1);
            cookie.setPath("/");

            // Bugzilla 41217
            cookie.setSecure(request.isSecure());

            // Bugzilla 34724
            String ssoDomain = sso.getCookieDomain();
            if (ssoDomain != null) {
                cookie.setDomain(ssoDomain);
            }

            // 使用和会话cookie相同的规则配置SSO cookie上的 httpOnly
            if (request.getServletContext().getSessionCookieConfig().isHttpOnly()
                    || request.getContext().getUseHttpOnly()) {
                cookie.setHttpOnly(true);
            }

            response.addCookie(cookie);

            // 使用SSO阀门注册这个主体
            sso.register(ssoId, principal, authType, username, password);
            request.setNote(Constants.REQ_SSOID_NOTE, ssoId);

        } else {
            if (principal == null) {
                // 注册程序注销
                sso.deregister(ssoId);
                request.removeNote(Constants.REQ_SSOID_NOTE);
                return;
            } else {
                // 使用最新认证数据更新SSO会话
                sso.update(ssoId, principal, authType, username, password);
            }
        }

        // Fix for Bug 10040
        // 总是将会话与新的SSO请求关联起来.
        // 只有在关联会话被销毁时SSO条目才从SSO注册表映射中删除;
        // 如果为这个请求创建了一个新的SSO条目，并且用户从不重读上下文, 如果我们不关联会话，SSO条目永远不会被清除
        if (session == null) {
            session = request.getSessionInternal(true);
        }
        sso.associate(ssoId, session);

    }

    @Override
    public void login(String username, String password, Request request) throws ServletException {
        Principal principal = doLogin(request, username, password);
        register(request, request.getResponse(), principal, getAuthMethod(), username, password);
    }

    protected abstract String getAuthMethod();

    /**
     * 处理登录请求.
     *
     * @param request 关联的请求
     * @param username 用户
     * @param password 密码
     * @return 已验证的Principal
     * @throws ServletException 没有用指定的凭据验证主体
     */
    protected Principal doLogin(Request request, String username, String password)
            throws ServletException {
        Principal p = context.getRealm().authenticate(username, password);
        if (p == null) {
            throw new ServletException(sm.getString("authenticator.loginFail"));
        }
        return p;
    }

    @Override
    public void logout(Request request) {
        AuthConfigProvider provider = getJaspicProvider();
        if (provider != null) {
            MessageInfo messageInfo = new MessageInfoImpl(request, request.getResponse(), true);
            Subject client = (Subject) request.getNote(Constants.REQ_JASPIC_SUBJECT_NOTE);
            if (client == null) {
                return;
            }

            ServerAuthContext serverAuthContext;
            try {
                ServerAuthConfig serverAuthConfig = provider.getServerAuthConfig("HttpServlet",
                        jaspicAppContextID, CallbackHandlerImpl.getInstance());
                String authContextID = serverAuthConfig.getAuthContextID(messageInfo);
                serverAuthContext = serverAuthConfig.getAuthContext(authContextID, null, null);
                serverAuthContext.cleanSubject(messageInfo, client);
            } catch (AuthException e) {
                log.debug(sm.getString("authenticator.jaspicCleanSubjectFail"), e);
            }
        }

        Principal p = request.getPrincipal();
        if (p instanceof TomcatPrincipal) {
            try {
                ((TomcatPrincipal) p).logout();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.debug(sm.getString("authenticator.tomcatPrincipalLogoutFail"), t);
            }
        }

        register(request, request.getResponse(), null, null, null, null);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        ServletContext servletContext = context.getServletContext();
        jaspicAppContextID = servletContext.getVirtualServerName() + " " +
                servletContext.getContextPath();

        // 在请求处理路径中查找SingleSignOn 实现类
        Container parent = context.getParent();
        while ((sso == null) && (parent != null)) {
            Valve valves[] = parent.getPipeline().getValves();
            for (int i = 0; i < valves.length; i++) {
                if (valves[i] instanceof SingleSignOn) {
                    sso = (SingleSignOn) valves[i];
                    break;
                }
            }
            if (sso == null) {
                parent = parent.getParent();
            }
        }
        if (log.isDebugEnabled()) {
            if (sso != null) {
                log.debug("Found SingleSignOn Valve at " + sso);
            } else {
                log.debug("No SingleSignOn Valve is present");
            }
        }

        sessionIdGenerator = new StandardSessionIdGenerator();
        sessionIdGenerator.setSecureRandomAlgorithm(getSecureRandomAlgorithm());
        sessionIdGenerator.setSecureRandomClass(getSecureRandomClass());
        sessionIdGenerator.setSecureRandomProvider(getSecureRandomProvider());

        super.startInternal();
    }

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();

        sso = null;
    }


    private AuthConfigProvider getJaspicProvider() {
        AuthConfigProvider provider = jaspicProvider;
        if (provider == null) {
            provider = findJaspicProvider();
        }
        if (provider == NO_PROVIDER_AVAILABLE) {
            return null;
        }
        return provider;
    }


    private AuthConfigProvider findJaspicProvider() {
        AuthConfigFactory factory = AuthConfigFactory.getFactory();
        AuthConfigProvider provider = null;
        if (factory != null) {
            provider = factory.getConfigProvider("HttpServlet", jaspicAppContextID, this);
        }
        if (provider == null) {
            provider = NO_PROVIDER_AVAILABLE;
        }
        jaspicProvider = provider;
        return provider;
    }


    @Override
    public void notify(String layer, String appContext) {
        findJaspicProvider();
    }


    private static class JaspicState {
        public MessageInfo messageInfo = null;
        public ServerAuthContext serverAuthContext = null;
    }


    private static class NoOpAuthConfigProvider implements AuthConfigProvider {

        @Override
        public ClientAuthConfig getClientAuthConfig(String layer, String appContext, CallbackHandler handler)
                throws AuthException {
            return null;
        }

        @Override
        public ServerAuthConfig getServerAuthConfig(String layer, String appContext, CallbackHandler handler)
                throws AuthException {
            return null;
        }

        @Override
        public void refresh() {
        }
    }
}