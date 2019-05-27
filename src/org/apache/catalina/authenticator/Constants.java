package org.apache.catalina.authenticator;


public class Constants {
    // 登录配置的认证方法
    // HttpServletRequest中的Servlet规范方案
    // 供应商特定方案
    public static final String SPNEGO_METHOD = "SPNEGO";

    // 基于表单的认证常量
    public static final String FORM_ACTION = "/j_security_check";
    public static final String FORM_PASSWORD = "j_password";
    public static final String FORM_USERNAME = "j_username";

    // SPNEGO 认证常数
    public static final String KRB5_CONF_PROPERTY = "java.security.krb5.conf";
    public static final String DEFAULT_KRB5_CONF = "conf/krb5.ini";
    public static final String JAAS_CONF_PROPERTY = "java.security.auth.login.config";
    public static final String DEFAULT_JAAS_CONF = "conf/jaas.conf";
    public static final String DEFAULT_LOGIN_MODULE_NAME = "com.sun.security.jgss.krb5.accept";
    /**
     * @deprecated Unused. Will be removed in Tomcat 9.
     */
    @Deprecated
    public static final String USE_SUBJECT_CREDS_ONLY_PROPERTY =
            "javax.security.auth.useSubjectCredsOnly";

    // 单点登录支持的Cookie名称
    public static final String SINGLE_SIGN_ON_COOKIE =
        System.getProperty("org.apache.catalina.authenticator.Constants.SSO_SESSION_COOKIE_NAME", "JSESSIONIDSSO");


    // --------------------------------------------------------- Request Notes

    /**
     * 跟踪此请求关联的单点登录的ID的key
     */
    public static final String REQ_SSOID_NOTE = "org.apache.catalina.request.SSOID";


    public static final String REQ_JASPIC_SUBJECT_NOTE = "org.apache.catalina.authenticator.jaspic.SUBJECT";


    // ---------------------------------------------------------- Session Notes


    /**
     * 如果身份认证的属性<code>cache</code>被设置, 当前请求是会话的一部分, 验证信息将被缓存，以避免重复调用
     * <code>Realm.authenticate()</code>, 下面是key:
     */


    /**
     * 用于验证此用户的密码的key
     */
    public static final String SESS_PASSWORD_NOTE = "org.apache.catalina.session.PASSWORD";


    /**
     * 用于验证此用户的用户名的key.
     */
    public static final String SESS_USERNAME_NOTE = "org.apache.catalina.session.USERNAME";


    /**
     * 下面的key用于在表单登录处理过程中缓存所需的信息，在完成身份验证之前.
     */


    /**
     * 先前认证的principal (如果缓存被禁用).
     */
    public static final String FORM_PRINCIPAL_NOTE = "org.apache.catalina.authenticator.PRINCIPAL";


    /**
     * 原始请求数据, 如果验证成功，用户将被重定向
     */
    public static final String FORM_REQUEST_NOTE = "org.apache.catalina.authenticator.REQUEST";


}
