package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;

import org.apache.tomcat.util.buf.UDecoder;

/**
 * 表示Web应用程序的登录配置元素, 作为部署描述符中<code>&lt;login-config&gt;</code>元素的表示.
 */
public class LoginConfig implements Serializable {


    private static final long serialVersionUID = 1L;


    // ----------------------------------------------------------- Constructors


    public LoginConfig() {
        super();
    }


    /**
     * @param authMethod 身份验证方法
     * @param realmName realm 名
     * @param loginPage 登录页面URI
     * @param errorPage 错误页面URI
     */
    public LoginConfig(String authMethod, String realmName,
                       String loginPage, String errorPage) {

        super();
        setAuthMethod(authMethod);
        setRealmName(realmName);
        setLoginPage(loginPage);
        setErrorPage(errorPage);
    }


    // ------------------------------------------------------------- Properties


    /**
     * 用于应用程序登录的身份验证方法. 必须是 BASIC, DIGEST, FORM, CLIENT-CERT.
     */
    private String authMethod = null;

    public String getAuthMethod() {
        return (this.authMethod);
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }


    /**
     * 表单登录的错误页面的上下文相对URI.
     */
    private String errorPage = null;

    public String getErrorPage() {
        return (this.errorPage);
    }

    public void setErrorPage(String errorPage) {
        //        if ((errorPage == null) || !errorPage.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Error Page resource path must start with a '/'");
        this.errorPage = UDecoder.URLDecode(errorPage);
    }


    /**
     * 表单登录的登录页面的上下文相对URI.
     */
    private String loginPage = null;

    public String getLoginPage() {
        return (this.loginPage);
    }

    public void setLoginPage(String loginPage) {
        //        if ((loginPage == null) || !loginPage.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Login Page resource path must start with a '/'");
        this.loginPage = UDecoder.URLDecode(loginPage);
    }


    /**
     * 在向用户提出身份验证凭据时使用的 Realm 名称.
     */
    private String realmName = null;

    public String getRealmName() {
        return (this.realmName);
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("LoginConfig[");
        sb.append("authMethod=");
        sb.append(authMethod);
        if (realmName != null) {
            sb.append(", realmName=");
            sb.append(realmName);
        }
        if (loginPage != null) {
            sb.append(", loginPage=");
            sb.append(loginPage);
        }
        if (errorPage != null) {
            sb.append(", errorPage=");
            sb.append(errorPage);
        }
        sb.append("]");
        return (sb.toString());

    }


    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((authMethod == null) ? 0 : authMethod.hashCode());
        result = prime * result
                + ((errorPage == null) ? 0 : errorPage.hashCode());
        result = prime * result
                + ((loginPage == null) ? 0 : loginPage.hashCode());
        result = prime * result
                + ((realmName == null) ? 0 : realmName.hashCode());
        return result;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof LoginConfig))
            return false;
        LoginConfig other = (LoginConfig) obj;
        if (authMethod == null) {
            if (other.authMethod != null)
                return false;
        } else if (!authMethod.equals(other.authMethod))
            return false;
        if (errorPage == null) {
            if (other.errorPage != null)
                return false;
        } else if (!errorPage.equals(other.errorPage))
            return false;
        if (loginPage == null) {
            if (other.loginPage != null)
                return false;
        } else if (!loginPage.equals(other.loginPage))
            return false;
        if (realmName == null) {
            if (other.realmName != null)
                return false;
        } else if (!realmName.equals(other.realmName))
            return false;
        return true;
    }
}
