package org.apache.catalina.realm;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Map.Entry;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.CredentialHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Digester;

/**
 * <p>JAAS <strong>LoginModule</strong>接口的实现类, 主要用于测试<code>JAASRealm</code>.
 * 它使用与<code>org.apache.catalina.realm.MemoryRealm</code>支持的用户名/密码/角色信息的XML格式数据文件.</p>
 *
 * <p>该类识别以下字符串值选项, 其在配置文件中指定并传递到<code>options</code>参数中的{@link
 * #initialize(Subject, CallbackHandler, Map, Map)}:</p>
 * <ul>
 * <li><strong>pathname</strong> - 包含用户信息的XML文件的相对 (相对于"catalina.base"系统属性指定的路径名)或绝对的路径名, 以{@link MemoryRealm}支持的格式.
 * 							默认值匹配默认的 MemoryRealm.</li>
 * <li><strong>credentialHandlerClassName</strong> - 要使用的CredentialHandler的完全限定名. 如果未指定, 将使用{@link MessageDigestCredentialHandler}.</li>
 * <li>将被用于识别任何其他选项, 并调用{@link CredentialHandler}上的setter.
 * 		例如, <code>algorithm=SHA256</code>将调用 {@link MessageDigestCredentialHandler#setAlgorithm(String)}, 并使用参数<code>"SHA256"</code></li>
 * </ul>
 *
 * <p><strong>IMPLEMENTATION NOTE</strong> - 这个类只实现了<code>Realm</code>以满足<code>GenericPrincipal</code>构造参数的调用要求.
 * 		它实际上没有执行<code>Realm</code>实现类所需的功能.</p>
 */
public class JAASMemoryLoginModule extends MemoryRealm implements LoginModule {
    // We need to extend MemoryRealm to avoid class cast

    private static final Log log = LogFactory.getLog(JAASMemoryLoginModule.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 负责响应请求的回调处理程序..
     */
    protected CallbackHandler callbackHandler = null;


    /**
     * <code>commit()</code>是否成功返回?
     */
    protected boolean committed = false;


    /**
     * <code>LoginModule</code>的配置信息.
     */
    protected Map<String,?> options = null;


    /**
     * XML配置文件的绝对或相对路径.
     */
    protected String pathname = "conf/tomcat-users.xml";


    /**
     * 通过确认的<code>Principal</code>, 或者<code>null</code>，如果验证失败.
     */
    protected Principal principal = null;


    /**
     * 和其他配置的<code>LoginModule</code>实例共享的状态信息.
     */
    protected Map<String,?> sharedState = null;


    /**
     * 正在进行身份验证的主题.
     */
    protected Subject subject = null;


    // --------------------------------------------------------- Public Methods

    public JAASMemoryLoginModule() {
        if (log.isDebugEnabled()) {
            log.debug("MEMORY LOGIN MODULE");
        }
    }

    /**
     * <code>Subject</code>身份验证的第2阶段,当第一阶段失败.
     * 如果<code>LoginContext</code>在整个认证链中的某处失败，将调用这个方法.
     *
     * @return <code>true</code>如果这个方法成功, 或者<code>false</code>如果这个<code>LoginModule</code>应该忽略
     *
     * @exception LoginException 如果中止失败
     */
    @Override
    public boolean abort() throws LoginException {

        // 如果认证不成功，只返回false
        if (principal == null) {
            return false;
        }

        // 如果整体身份验证失败，清除
        if (committed) {
            logout();
        } else {
            committed = false;
            principal = null;
        }
        if (log.isDebugEnabled()) {
            log.debug("Abort");
        }
        return true;
    }


    /**
     * <code>Subject</code>验证的第二阶段，当第一阶段验证成功.
     * 如果<code>LoginContext</code>在整个认证链中成功，将调用这个方法.
     *
     * @return <code>true</code>如果这个方法成功, 或者<code>false</code>如果这个<code>LoginModule</code>应该忽略
     *
     * @exception LoginException 如果提交失败
     */
    @Override
    public boolean commit() throws LoginException {
        if (log.isDebugEnabled()) {
            log.debug("commit " + principal);
        }

        // 如果认证不成功，只返回false
        if (principal == null) {
            return false;
        }

        // 添加Principal到 Subject
        if (!subject.getPrincipals().contains(principal)) {
            subject.getPrincipals().add(principal);
            // Add the roles as additional subjects as per the contract with the
            // JAASRealm
            if (principal instanceof GenericPrincipal) {
                String roles[] = ((GenericPrincipal) principal).getRoles();
                for (int i = 0; i < roles.length; i++) {
                    subject.getPrincipals().add(new GenericPrincipal(roles[i], null, null));
                }

            }
        }

        committed = true;
        return true;
    }


    /**
     * 使用指定的配置信息初始化这个<code>LoginModule</code>.
     *
     * @param subject 要验证的<code>Subject</code>
     * @param callbackHandler <code>CallbackHandler</code>，在必要时与最终用户通信
     * @param sharedState 和其他<code>LoginModule</code>实例共享的配置信息
     * @param options 指定的<code>LoginModule</code>实例的配置信息
     */
    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                           Map<String,?> sharedState, Map<String,?> options) {
        if (log.isDebugEnabled()) {
            log.debug("Init");
        }

        // 保存配置值
        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.sharedState = sharedState;
        this.options = options;

        // 执行特定实例的初始化
        Object option = options.get("pathname");
        if (option instanceof String) {
            this.pathname = (String) option;
        }

        CredentialHandler credentialHandler = null;
        option = options.get("credentialHandlerClassName");
        if (option instanceof String) {
            try {
                Class<?> clazz = Class.forName((String) option);
                credentialHandler = (CredentialHandler) clazz.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (credentialHandler == null) {
            credentialHandler = new MessageDigestCredentialHandler();
        }

        for (Entry<String,?> entry : options.entrySet()) {
            if ("pathname".equals(entry.getKey())) {
                continue;
            }
            if ("credentialHandlerClassName".equals(entry.getKey())) {
                continue;
            }
            // Skip any non-String values since any value we are interested in
            // will be a String.
            if (entry.getValue() instanceof String) {
                IntrospectionUtils.setProperty(credentialHandler, entry.getKey(),
                        (String) entry.getValue());
            }
        }
        setCredentialHandler(credentialHandler);

        // 加载定义的Principals
        load();
    }


    /**
     * 验证<code>Subject</code>的第一阶段.
     *
     * @return <code>true</code>如果这个方法成功, 或者<code>false</code>如果这个<code>LoginModule</code>应该忽略
     *
     * @exception LoginException 如果身份验证失败
     */
    @Override
    public boolean login() throws LoginException {
        // Set up our CallbackHandler requests
        if (callbackHandler == null)
            throw new LoginException("No CallbackHandler specified");
        Callback callbacks[] = new Callback[9];
        callbacks[0] = new NameCallback("Username: ");
        callbacks[1] = new PasswordCallback("Password: ", false);
        callbacks[2] = new TextInputCallback("nonce");
        callbacks[3] = new TextInputCallback("nc");
        callbacks[4] = new TextInputCallback("cnonce");
        callbacks[5] = new TextInputCallback("qop");
        callbacks[6] = new TextInputCallback("realmName");
        callbacks[7] = new TextInputCallback("md5a2");
        callbacks[8] = new TextInputCallback("authMethod");

        // 与用户交互以检索用户名和密码
        String username = null;
        String password = null;
        String nonce = null;
        String nc = null;
        String cnonce = null;
        String qop = null;
        String realmName = null;
        String md5a2 = null;
        String authMethod = null;

        try {
            callbackHandler.handle(callbacks);
            username = ((NameCallback) callbacks[0]).getName();
            password =
                new String(((PasswordCallback) callbacks[1]).getPassword());
            nonce = ((TextInputCallback) callbacks[2]).getText();
            nc = ((TextInputCallback) callbacks[3]).getText();
            cnonce = ((TextInputCallback) callbacks[4]).getText();
            qop = ((TextInputCallback) callbacks[5]).getText();
            realmName = ((TextInputCallback) callbacks[6]).getText();
            md5a2 = ((TextInputCallback) callbacks[7]).getText();
            authMethod = ((TextInputCallback) callbacks[8]).getText();
        } catch (IOException | UnsupportedCallbackException e) {
            throw new LoginException(e.toString());
        }

        // 验证收到的用户名和密码
        if (authMethod == null) {
            // BASIC or FORM
            principal = super.authenticate(username, password);
        } else if (authMethod.equals(HttpServletRequest.DIGEST_AUTH)) {
            principal = super.authenticate(username, password, nonce, nc,
                    cnonce, qop, realmName, md5a2);
        } else if (authMethod.equals(HttpServletRequest.CLIENT_CERT_AUTH)) {
            principal = super.getPrincipal(username);
        } else {
            throw new LoginException("Unknown authentication method");
        }

        if (log.isDebugEnabled()) {
            log.debug("login " + username + " " + principal);
        }

        // 根据成功或失败报告结果
        if (principal != null) {
            return true;
        } else {
            throw new FailedLoginException("Username or password is incorrect");
        }
    }


    /**
     * 用户退出登录.
     *
     * @return 所有情况都返回<code>true</code>，因为<code>LoginModule</code>不应该被忽略
     *
     * @exception LoginException 如果注销失败
     */
    @Override
    public boolean logout() throws LoginException {
        subject.getPrincipals().remove(principal);
        committed = false;
        principal = null;
        return true;
    }

    /**
     * 加载配置文件的内容.
     */
    protected void load() {
        // 验证配置文件是否存在
        File file = new File(pathname);
        if (!file.isAbsolute()) {
            String catalinaBase = getCatalinaBase();
            if (catalinaBase == null) {
                log.warn("Unable to determine Catalina base to load file " + pathname);
                return;
            } else {
                file = new File(catalinaBase, pathname);
            }
        }
        if (!file.canRead()) {
            log.warn("Cannot load configuration file " + file.getAbsolutePath());
            return;
        }

        // 加载配置文件的内容
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.addRuleSet(new MemoryRuleSet());
        try {
            digester.push(this);
            digester.parse(file);
        } catch (Exception e) {
            log.warn("Error processing configuration file " + file.getAbsolutePath(), e);
            return;
        } finally {
            digester.reset();
        }
    }

    private String getCatalinaBase() {
        // 必须通过回调获得这一点，因为这是我们回到定义的Realm的唯一链接. 不能使用系统属性，因为在嵌入式场景中可能不设置/正确
        if (callbackHandler == null) {
            return null;
        }

        Callback callbacks[] = new Callback[1];
        callbacks[0] = new TextInputCallback("catalinaBase");

        String result = null;

        try {
            callbackHandler.handle(callbacks);
            result = ((TextInputCallback) callbacks[0]).getText();
        } catch (IOException | UnsupportedCallbackException e) {
            return null;
        }
        return result;
    }
}
