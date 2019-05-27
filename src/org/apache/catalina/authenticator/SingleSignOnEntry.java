package org.apache.catalina.authenticator;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.Session;

/**
 * 已验证用户的缓存中的条目.
 * 这是必要的，使其可获得<code>AuthenticatorBase</code>子类, 需要使用它进行重新验证, 当使用 SingleSignOn 的时候.
 */
public class SingleSignOnEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------  Instance Fields

    private String authType = null;

    private String password = null;

    // 标记为瞬态，因此特殊处理可应用于序列化
    private transient Principal principal = null;

    private final ConcurrentMap<SingleSignOnSessionKey,SingleSignOnSessionKey> sessionKeys =
            new ConcurrentHashMap<>();

    private String username = null;

    private boolean canReauthenticate = false;

    // ---------------------------------------------------------  Constructors

    /**
     * @param principal 最后调用的<code>Realm.authenticate</code>返回的<code>Principal</code>.
     * @param authType  用于认证的类型(BASIC, CLIENT-CERT, DIGEST or FORM)
     * @param username  用于身份验证的用户名
     * @param password  用于身份验证的密码
     */
    public SingleSignOnEntry(Principal principal, String authType,
                             String username, String password) {

        updateCredentials(principal, authType, username, password);
    }

    // ------------------------------------------------------- Package Methods

    /**
     * 添加一个<code>Session</code>.
     *
     * @param sso       管理SSO会话的<code>SingleSignOn</code> valve
     * @param ssoId     SSO会话的 ID.
     * @param session   SSO关联的<code>Session</code>
     */
    public void addSession(SingleSignOn sso, String ssoId, Session session) {
        SingleSignOnSessionKey key = new SingleSignOnSessionKey(session);
        SingleSignOnSessionKey currentKey = sessionKeys.putIfAbsent(key, key);
        if (currentKey == null) {
            // 以前没有添加会话
            session.addSessionListener(sso.getSessionListener(ssoId));
        }
    }

    /**
     * 删除<code>Session</code>.
     *
     * @param session  the <code>Session</code> to remove.
     */
    public void removeSession(Session session) {
        SingleSignOnSessionKey key = new SingleSignOnSessionKey(session);
        sessionKeys.remove(key);
    }

    /**
     * 返回这个SSO关联的 HTTP Session标识符.
     */
    public Set<SingleSignOnSessionKey> findSessions() {
        return sessionKeys.keySet();
    }

    /**
     * 获取最初用于验证与SSO关联的用户的身份验证类型的名称.
     *
     * @return "BASIC", "CLIENT_CERT", "DIGEST", "FORM" or "NONE"
     */
    public String getAuthType() {
        return this.authType;
    }

    /**
     * 获取与原始身份验证关联的身份验证类型是否支持重新认证.
     *
     * @return  <code>true</code>如果<code>getAuthType</code>返回"BASIC" 或 "FORM", 否则<code>false</code>.
     */
    public boolean getCanReauthenticate() {
        return this.canReauthenticate;
    }

    /**
     * 获取密码.
     *
     * @return  如果原始身份验证类型不涉及密码则为<code>null</code>.
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * 获取已经通过SSO认证的<code>Principal</code>.
     */
    public Principal getPrincipal() {
        return this.principal;
    }

    /**
     * 获取用户提供的用户名，作为认证过程的一部分.
     */
    public String getUsername() {
        return this.username;
    }


    /**
     * 更新SingleSignOnEntry 以反映与调用方关联的最新安全信息.
     *
     * @param principal 最后调用的<code>Realm.authenticate</code>返回的<code>Principal</code>
     * @param authType  用于认证的类型(BASIC, CLIENT-CERT, DIGEST or FORM)
     * @param username  用于身份验证的用户名
     * @param password  用于身份验证的密码
     */
    public synchronized void updateCredentials(Principal principal, String authType,
                                  String username, String password) {
        this.principal = principal;
        this.authType = authType;
        this.username = username;
        this.password = password;
        this.canReauthenticate = (HttpServletRequest.BASIC_AUTH.equals(authType) ||
                HttpServletRequest.FORM_AUTH.equals(authType));
    }


    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if (principal instanceof Serializable) {
            out.writeBoolean(true);
            out.writeObject(principal);
        } else {
            out.writeBoolean(false);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        in.defaultReadObject();
        boolean hasPrincipal = in.readBoolean();
        if (hasPrincipal) {
            principal = (Principal) in.readObject();
        }
    }
}
