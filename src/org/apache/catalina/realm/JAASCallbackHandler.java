package org.apache.catalina.realm;


import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.tomcat.util.res.StringManager;

/**
 * <p>JAAS <strong>CallbackHandler</code>接口的实现类,
 * 用于协商指定给构造器的用户名和凭证的传递. 不需要与用户交互(或可能).</p>
 *
 * <p><code>CallbackHandler</code>将预加密提供的密码, 如果<code>server.xml</code>中的<code>&lt;Realm&gt;</code>节点需要的话.</p>
 * <p>目前, <code>JAASCallbackHandler</code>知道怎样处理<code>javax.security.auth.callback.NameCallback</code>和
 * <code>javax.security.auth.callback.PasswordCallback</code>类型的回调.</p>
 */
public class JAASCallbackHandler implements CallbackHandler {

    // ------------------------------------------------------------ Constructor


    /**
     * @param realm 关联的JAASRealm实例
     * @param username 要验证的Username
     * @param password 要验证的Password
     */
    public JAASCallbackHandler(JAASRealm realm, String username,
                               String password) {

        this(realm, username, password, null, null, null, null, null, null, null);
    }


    /**
     * @param realm         关联的JAASRealm 实例
     * @param username      要验证的Username
     * @param password      要验证的Password
     * @param nonce         Server generated nonce
     * @param nc            Nonce count
     * @param cnonce        Client generated nonce
     * @param qop           应用于消息的保护质量
     * @param realmName     Realm名称
     * @param md5a2         用于计算摘要的第二个 MD5摘要 MD5(Method + ":" + uri)
     * @param authMethod    使用中的认证方法
     */
    public JAASCallbackHandler(JAASRealm realm, String username,
                               String password, String nonce, String nc,
                               String cnonce, String qop, String realmName,
                               String md5a2, String authMethod) {
        this.realm = realm;
        this.username = username;

        if (realm.hasMessageDigest()) {
            this.password = realm.getCredentialHandler().mutate(password);
        }
        else {
            this.password = password;
        }
        this.nonce = nonce;
        this.nc = nc;
        this.cnonce = cnonce;
        this.qop = qop;
        this.realmName = realmName;
        this.md5a2 = md5a2;
        this.authMethod = authMethod;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(JAASCallbackHandler.class);

    /**
     * 要验证的Password.
     */
    protected final String password;


    /**
     * 关联的<code>JAASRealm</code>实例.
     */
    protected final JAASRealm realm;

    /**
     * 要验证的用户名.
     */
    protected final String username;

    /**
     * Server generated nonce.
     */
    protected final String nonce;

    /**
     * Nonce count.
     */
    protected final String nc;

    /**
     * Client generated nonce.
     */
    protected final String cnonce;

    /**
     * 应用于消息的保护质量.
     */
    protected final String qop;

    /**
     * Realm名称.
     */
    protected final String realmName;

    /**
     * Second MD5 digest.
     */
    protected final String md5a2;

    /**
     * 使用的认证方法. 如果是null, 假设 BASIC/FORM.
     */
    protected final String authMethod;

    // --------------------------------------------------------- Public Methods


    /**
     * 检索提供的<code>Callbacks</code>请求的信息.
     * 这个实现类仅识别 {@link NameCallback}, {@link PasswordCallback}, {@link TextInputCallback}.
     * {@link TextInputCallback} 用于传递DIGEST认证所需的各种附加参数.
     *
     * @param callbacks 待处理的<code>Callback</code>
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception UnsupportedCallbackException 如果登录方法请求不支持的回调类型
     */
    @Override
    public void handle(Callback callbacks[])
        throws IOException, UnsupportedCallbackException {

        for (int i = 0; i < callbacks.length; i++) {

            if (callbacks[i] instanceof NameCallback) {
                if (realm.getContainer().getLogger().isTraceEnabled())
                    realm.getContainer().getLogger().trace(sm.getString("jaasCallback.username", username));
                ((NameCallback) callbacks[i]).setName(username);
            } else if (callbacks[i] instanceof PasswordCallback) {
                final char[] passwordcontents;
                if (password != null) {
                    passwordcontents = password.toCharArray();
                } else {
                    passwordcontents = new char[0];
                }
                ((PasswordCallback) callbacks[i]).setPassword
                    (passwordcontents);
            } else if (callbacks[i] instanceof TextInputCallback) {
                TextInputCallback cb = ((TextInputCallback) callbacks[i]);
                if (cb.getPrompt().equals("nonce")) {
                    cb.setText(nonce);
                } else if (cb.getPrompt().equals("nc")) {
                    cb.setText(nc);
                } else if (cb.getPrompt().equals("cnonce")) {
                    cb.setText(cnonce);
                } else if (cb.getPrompt().equals("qop")) {
                    cb.setText(qop);
                } else if (cb.getPrompt().equals("realmName")) {
                    cb.setText(realmName);
                } else if (cb.getPrompt().equals("md5a2")) {
                    cb.setText(md5a2);
                } else if (cb.getPrompt().equals("authMethod")) {
                    cb.setText(authMethod);
                } else if (cb.getPrompt().equals("catalinaBase")) {
                    cb.setText(realm.getContainer().getCatalinaBase().getAbsolutePath());
                } else {
                    throw new UnsupportedCallbackException(callbacks[i]);
                }
            } else {
                throw new UnsupportedCallbackException(callbacks[i]);
            }
        }
    }
}
