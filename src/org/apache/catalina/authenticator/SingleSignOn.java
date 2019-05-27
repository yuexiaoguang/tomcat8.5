package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.SessionListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * <strong>Valve</strong> 支持“单点登录”用户体验,
 * 通过一个Web应用程序身份验证的用户的安全标识同样适用于同一个安全域中的其他应用.
 * 为了成功地使用，必须满足以下要求:
 * <ul>
 * <li>Valve 必须在表示虚拟主机的容器上进行配置(通常是<code>Host</code>实现类).</li>
 * <li><code>Realm</code>包含共享用户和角色信息，必须在同一个Container上配置(或更高一级), 在Web应用程序级别上未被重写.</li>
 * <li>Web应用程序本身必须使用一个标准的认证，在
 *     <code>org.apache.catalina.authenticator</code>包中找到的.</li>
 * </ul>
 */
public class SingleSignOn extends ValveBase {

    private static final StringManager sm = StringManager.getManager(SingleSignOn.class);

    /* 在容器层级顶层的引擎，其中该SSO阀已被放置. 它用于从SingleSignOnSessionKey返回会话对象，并在阀门启动和停止时更新.
     */
    private Engine engine;

    //------------------------------------------------------ Constructor

    public SingleSignOn() {
        super(true);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * SingleSignOnEntry实例的缓存，为已认证的Principal, 使用cookie值作为key.
     */
    protected Map<String,SingleSignOnEntry> cache = new ConcurrentHashMap<>();

    /**
     * 这个valve 是否需要下游的 Authenticator 重新验证每个请求, 或者它本身可以绑定一个 UserPrincipal和AuthType对象到请求中.
     */
    private boolean requireReauthentication = false;

    /**
     * 可选的SSO cookie域.
     */
    private String cookieDomain;


    // ------------------------------------------------------------- Properties

    /**
     * 返回可选的SSO cookie域.
     * 可能返回 null.
     */
    public String getCookieDomain() {
        return cookieDomain;
    }


    /**
     * 设置sso cookie使用的域.
     *
     * @param cookieDomain cookie域名
     */
    public void setCookieDomain(String cookieDomain) {
        if (cookieDomain != null && cookieDomain.trim().length() == 0) {
            this.cookieDomain = null;
        } else {
            this.cookieDomain = cookieDomain;
        }
    }


    /**
     * 获取是否每个请求需要重新验证安全<code>Realm</code> (通过pipeline中下游的Authenticator);
     * 或者如果这个Valve 本身可以绑定安全信息到请求中，基于一个有效的SSO条目的存在, 不需要重新验证<code>Realm</code>.
     *
     * @return  <code>true</code>如果它需要Authenticator重新验证每个请求, 在调用
     *          <code>HttpServletRequest.setUserPrincipal()</code>和<code>HttpServletRequest.setAuthType()</code>之前;
     *          <code>false</code> 如果<code>Valve</code>本身让这些调用依赖请求相关的有效的SingleSignOn的存在
     */
    public boolean getRequireReauthentication() {
        return requireReauthentication;
    }


    /**
     * 设置是否每个请求需要重新验证安全<code>Realm</code> (通过pipeline中下游的Authenticator);
     * 或者如果这个Valve 本身可以绑定安全信息到请求中，基于一个有效的SSO条目的存在, 不需要重新验证<code>Realm</code>.
     * <p>
     * 如果这个属性是<code>false</code> (默认), 这个<code>Valve</code> 将绑定一个 UserPrincipal 和 AuthType到请求中,
     * 如果这个请求关联了一个有效的 SSO 条目.  它不会通知传入请求的安全<code>Realm</code>.
     * <p>
     * 这个属性应该设置为<code>true</code>, 如果整个服务器配置需要<code>Realm</code>重新验证每个请求.
     * 这种配置的一个例子是, <code>Realm</code>实现类为Web层和相关的EJB层提供了安全性, 并且需要在每个请求线程上设置安全凭据，以支持EJB访问.
     * <p>
     * 如果这个属性应该设置<code>true</code>, 这个Valve 将设置请求中的标志, 提醒下游的Authenticator 请求被关联到一个 SSO 会话. 
     * 然后Authenticator 将调用它的{@link AuthenticatorBase#reauthenticateFromSSO reauthenticateFromSSO}
     * 方法尝试重新验证请求到<code>Realm</code>, 使用这个Valve缓存的任何凭据.
     * <p>
     * 这个属性默认是<code>false</code>, 为了保持与以前版本的Tomcat的向后兼容性.
     *
     * @param required  <code>true</code>如果它需要Authenticator重新验证每个请求, 在调用
     *          <code>HttpServletRequest.setUserPrincipal()</code>和<code>HttpServletRequest.setAuthType()</code>之前;
     *          <code>false</code> 如果<code>Valve</code>本身让这些调用依赖请求相关的有效的SingleSignOn的存在
     */
    public void setRequireReauthentication(boolean required) {
        this.requireReauthentication = required;
    }


    // ---------------------------------------------------------- Valve Methods

    /**
     * 执行单点登录支持处理.
     *
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        request.removeNote(Constants.REQ_SSOID_NOTE);

        // 有效用户是否已被验证?
        if (containerLog.isDebugEnabled()) {
            containerLog.debug(sm.getString("singleSignOn.debug.invoke", request.getRequestURI()));
        }
        if (request.getUserPrincipal() != null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.hasPrincipal",
                        request.getUserPrincipal().getName()));
            }
            getNext().invoke(request, response);
            return;
        }

        // 检查单点登录cookie
        if (containerLog.isDebugEnabled()) {
            containerLog.debug(sm.getString("singleSignOn.debug.cookieCheck"));
        }
        Cookie cookie = null;
        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                if (Constants.SINGLE_SIGN_ON_COOKIE.equals(cookies[i].getName())) {
                    cookie = cookies[i];
                    break;
                }
            }
        }
        if (cookie == null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.cookieNotFound"));
            }
            getNext().invoke(request, response);
            return;
        }

        // 查找与此cookie值关联的缓存主体
        if (containerLog.isDebugEnabled()) {
            containerLog.debug(sm.getString("singleSignOn.debug.principalCheck",
                    cookie.getValue()));
        }
        SingleSignOnEntry entry = cache.get(cookie.getValue());
        if (entry != null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.principalFound",
                        entry.getPrincipal() != null ? entry.getPrincipal().getName() : "",
                        entry.getAuthType()));
            }
            request.setNote(Constants.REQ_SSOID_NOTE, cookie.getValue());
            // 只有设置安全元素, 如果重新认证是不需要的
            if (!getRequireReauthentication()) {
                request.setAuthType(entry.getAuthType());
                request.setUserPrincipal(entry.getPrincipal());
            }
        } else {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.principalNotFound",
                        cookie.getValue()));
            }
            // 无需返回有效的SSO会话ID
            cookie.setValue("REMOVE");
            // 设置为0，等同于删除
            cookie.setMaxAge(0);
            // 域和路径必须匹配原始cookie，以“替换”原始cookie
            cookie.setPath("/");
            String domain = getCookieDomain();
            if (domain != null) {
                cookie.setDomain(domain);
            }
            // 这将触发一个Set-Cookie header. 虽然值不是安全敏感的, 确保满足安全性和httpOnly
            cookie.setSecure(request.isSecure());
            if (request.getServletContext().getSessionCookieConfig().isHttpOnly() ||
                    request.getContext().getUseHttpOnly()) {
                cookie.setHttpOnly(true);
            }

            response.addCookie(cookie);
        }

        // Invoke the next Valve in our pipeline
        getNext().invoke(request, response);
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 通过从缓存中删除对该会话的引用来处理会话销毁事件 - 如果会话销毁是注销的结果 - 销毁关联的SSO 会话.
     *
     * @param ssoId   SSO会话ID
     * @param session 要销毁的会话
     */
    public void sessionDestroyed(String ssoId, Session session) {

        if (!getState().isAvailable()) {
            return;
        }

        // 会话是否由于超时或上下文停止而被销毁?
        // 如果是这样, 将从SSO中删除过期的会话. 如果会话已退出, 将注销与SSO相关联的所有会话.
        if (((session.getMaxInactiveInterval() > 0)
            && (session.getIdleTimeInternal() >= session.getMaxInactiveInterval() * 1000))
            || (!session.getManager().getContext().getState().isAvailable())) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.sessionTimeout",
                        ssoId, session));
            }
            removeSession(ssoId, session);
        } else {
            // 会话已注销.
            // 取消单个会话ID, 使关联会话无效
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.sessionLogout",
                        ssoId, session));
            }
            // 首先删除我们知道已过期/已注销的会话，因为它已经从其管理器中删除, 如果不先删除它, deregister()将记录无法找到的警告
            removeSession(ssoId, session);
            // 如果SSO会话只与一个Web应用程序关联，上面的调用将从缓存中删除SSO会话
            if (cache.containsKey(ssoId)) {
                deregister(ssoId);
            }
        }
    }


    /**
     * 将指定的单点登录标识符与指定的会话关联.
     *
     * @param ssoId 单点登录标识符
     * @param session 关联的会话
     *
     * @return <code>true</code>如果会话与给定的SSO会话关联, 否则<code>false</code>
     */
    protected boolean associate(String ssoId, Session session) {
        SingleSignOnEntry sso = cache.get(ssoId);
        if (sso == null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.associateFail",
                        ssoId, session));
            }
            return false;
        } else {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.associate",
                        ssoId, session));
            }
            sso.addSession(this, ssoId, session);
            return true;
        }
    }


    /**
     * 注销指定的会话.
     * 如果这是最后一个会话, 然后去掉单点登录标识符.
     *
     * @param ssoId 要注销的单点登录标识符
     */
    protected void deregister(String ssoId) {

        // 查找并移除指定的SingleSignOnEntry
        SingleSignOnEntry sso = cache.remove(ssoId);

        if (sso == null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.deregisterFail", ssoId));
            }
            return;
        }

        // 到期关联的会话
        Set<SingleSignOnSessionKey> ssoKeys = sso.findSessions();
        if (ssoKeys.size() == 0) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.deregisterNone", ssoId));
            }
        }
        for (SingleSignOnSessionKey ssoKey : ssoKeys) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.deregister", ssoKey, ssoId));
            }
            // 使此会话无效
            expire(ssoKey);
        }

        // NOTE: 客户端仍然可以拥有旧的单点登录cookie, 但是它将在下一个请求中被移除，因为它不再在缓存中
    }


    private void expire(SingleSignOnSessionKey key) {
        if (engine == null) {
            containerLog.warn(sm.getString("singleSignOn.sessionExpire.engineNull", key));
            return;
        }
        Container host = engine.findChild(key.getHostName());
        if (host == null) {
            containerLog.warn(sm.getString("singleSignOn.sessionExpire.hostNotFound", key));
            return;
        }
        Context context = (Context) host.findChild(key.getContextName());
        if (context == null) {
            containerLog.warn(sm.getString("singleSignOn.sessionExpire.contextNotFound", key));
            return;
        }
        Manager manager = context.getManager();
        if (manager == null) {
            containerLog.warn(sm.getString("singleSignOn.sessionExpire.managerNotFound", key));
            return;
        }
        Session session = null;
        try {
            session = manager.findSession(key.getSessionId());
        } catch (IOException e) {
            containerLog.warn(sm.getString("singleSignOn.sessionExpire.managerError", key), e);
            return;
        }
        if (session == null) {
            containerLog.warn(sm.getString("singleSignOn.sessionExpire.sessionNotFound", key));
            return;
        }
        session.expire();
    }


    /**
     * 重新验证给定的<code>Realm</code>, 使用单点登录会话标识符关联的凭据, 通过参数<code>ssoId</code>.
     * <p>
     * 如果重新验证成功, SSO会话关联的<code>Principal</code>和验证类型将被绑定到给定的<code>Request</code>对象, 通过调用
     * {@link Request#setAuthType Request.setAuthType()} 和 {@link Request#setUserPrincipal Request.setUserPrincipal()}
     * </p>
     *
     * @param ssoId     SingleSignOn会话标识符
     * @param realm     要验证的Realm实现类
     * @param request   需要验证的请求
     *
     * @return  <code>true</code>如果重新验证成功,否则<code>false</code>.
     */
    protected boolean reauthenticate(String ssoId, Realm realm,
                                     Request request) {

        if (ssoId == null || realm == null) {
            return false;
        }

        boolean reauthenticated = false;

        SingleSignOnEntry entry = cache.get(ssoId);
        if (entry != null && entry.getCanReauthenticate()) {

            String username = entry.getUsername();
            if (username != null) {
                Principal reauthPrincipal =
                        realm.authenticate(username, entry.getPassword());
                if (reauthPrincipal != null) {
                    reauthenticated = true;
                    // Bind the authorization credentials to the request
                    request.setAuthType(entry.getAuthType());
                    request.setUserPrincipal(reauthPrincipal);
                }
            }
        }

        return reauthenticated;
    }


    /**
     * 将指定的Principal注册，与单个登录标识符的指定值相关联.
     *
     * @param ssoId 要注册的单点登录标识符
     * @param principal 已识别的关联用户主体
     * @param authType 用于验证此用户主体的身份验证类型
     * @param username 用于对该用户进行身份验证的用户名
     * @param password 用于对该用户进行身份验证的密码
     */
    protected void register(String ssoId, Principal principal, String authType,
                  String username, String password) {

        if (containerLog.isDebugEnabled()) {
            containerLog.debug(sm.getString("singleSignOn.debug.register", ssoId,
                    principal != null ? principal.getName() : "", authType));
        }

        cache.put(ssoId, new SingleSignOnEntry(principal, authType, username, password));
    }


    /**
     * 更新根据<code>ssoId</code>找到的所有<code>SingleSignOnEntry</code>以及指定的验证数据.
     * <p>
     * 此方法的目的是允许在没有用户名/密码组合的情况下建立SSO条目(即使用 DIGEST 或 CLIENT-CERT认证)更新用户名和密码,
     * 如果一个可用的通过BASIC 或 FORM 认证. SSO 将可用于认证.
     * <p>
     * <b>NOTE:</b> 只更新 SSO 条目，如果调用<code>SingleSignOnEntry.getCanReauthenticate()</code>返回
     * <code>false</code>; 否则, 假设SSO 已经有足够的信息来认证, 而且不需要更新.
     *
     * @param ssoId     要更新的单点登录的标识符
     * @param principal 最后调用的<code>Realm.authenticate</code>返回的<code>Principal</code>.
     * @param authType  用于认证的类型(BASIC, CLIENT-CERT, DIGEST or FORM)
     * @param username  用于身份验证的用户名
     * @param password  用于身份验证的密码
     *
     * @return <code>true</code>如果凭证已更新, 否则<code>false</code>
     */
    protected boolean update(String ssoId, Principal principal, String authType,
                          String username, String password) {

        SingleSignOnEntry sso = cache.get(ssoId);
        if (sso != null && !sso.getCanReauthenticate()) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("singleSignOn.debug.update", ssoId, authType));
            }

            sso.updateCredentials(principal, authType, username, password);
            return true;
        }
        return false;
    }


    /**
     * 从一个SingleSignOn删除一个会话.
     * 当会话超时并不再活动时调用.
     *
     * @param ssoId 会话的单点登录标识符.
     * @param session 要删除的会话.
     */
    protected void removeSession(String ssoId, Session session) {

        if (containerLog.isDebugEnabled()) {
            containerLog.debug(sm.getString("singleSignOn.debug.removeSession", session, ssoId));
        }

        // Get a reference to the SingleSignOn
        SingleSignOnEntry entry = cache.get(ssoId);
        if (entry == null) {
            return;
        }

        // 从SingleSignOnEntry删除非活动会话
        entry.removeSession(session);

        // 如果SingleSignOnEntry中没有会话, 注销它.
        if (entry.findSessions().size() == 0) {
            deregister(ssoId);
        }
    }


    protected SessionListener getSessionListener(String ssoId) {
        return new SingleSignOnListener(ssoId);
    }


    @Override
    protected synchronized void startInternal() throws LifecycleException {
        Container c = getContainer();
        while (c != null && !(c instanceof Engine)) {
            c = c.getParent();
        }
        if (c instanceof Engine) {
            engine = (Engine) c;
        }
        super.startInternal();
    }


    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        engine = null;
    }
}
