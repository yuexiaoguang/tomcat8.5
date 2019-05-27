package org.apache.catalina.realm;

import java.io.Serializable;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import javax.security.auth.login.LoginContext;

import org.apache.catalina.TomcatPrincipal;
import org.ietf.jgss.GSSCredential;

/**
 * <strong>java.security.Principal</strong>通用实现类，对于<code>Realm</code>实现类是有用的.
 */
public class GenericPrincipal implements TomcatPrincipal, Serializable {

    private static final long serialVersionUID = 1L;


    // ----------------------------------------------------------- Constructors

    /**
     * @param name 由该Principal表示的用户的用户名
     * @param password 用于验证此用户的密码
     * @param roles 用户的角色列表
     */
    public GenericPrincipal(String name, String password, List<String> roles) {
        this(name, password, roles, null);
    }

    /**
     * @param name 由该Principal表示的用户的用户名
     * @param password 用于验证此用户的密码
     * @param roles 用户的角色列表
     * @param userPrincipal - 调用getUserPrincipal返回的主体; 如果是null, 直接返回
     */
    public GenericPrincipal(String name, String password, List<String> roles,
            Principal userPrincipal) {
        this(name, password, roles, userPrincipal, null);
    }

    /**
     * @param name 由该Principal表示的用户的用户名
     * @param password 用于验证此用户的密码
     * @param roles 用户的角色列表
     * @param userPrincipal - 调用getUserPrincipal返回的主体; 如果是null, 直接返回
     * @param loginContext  - 如果提供, 这将用于在适当的时间注销用户
     */
    public GenericPrincipal(String name, String password, List<String> roles,
            Principal userPrincipal, LoginContext loginContext) {
        this(name, password, roles, userPrincipal, loginContext, null);
    }

    /**
     * @param name 由该Principal表示的用户的用户名
     * @param password 用于验证此用户的密码
     * @param roles 用户的角色列表
     * @param userPrincipal - 调用getUserPrincipal返回的主体; 如果是null, 直接返回
     * @param loginContext  - 如果提供, 这将用于在适当的时间注销用户
     * @param gssCredential - 如果提供, 用户委托的凭证
     */
    public GenericPrincipal(String name, String password, List<String> roles,
            Principal userPrincipal, LoginContext loginContext,
            GSSCredential gssCredential) {
        super();
        this.name = name;
        this.password = password;
        this.userPrincipal = userPrincipal;
        if (roles == null) {
            this.roles = new String[0];
        } else {
            this.roles = roles.toArray(new String[roles.size()]);
            if (this.roles.length > 1) {
                Arrays.sort(this.roles);
            }
        }
        this.loginContext = loginContext;
        this.gssCredential = gssCredential;
    }


    // -------------------------------------------------------------- Properties

    /**
     * 这个Principal代表的用户名.
     */
    protected final String name;

    @Override
    public String getName() {
        return this.name;
    }


    /**
     * 用户的身份验证凭据.
     */
    protected final String password;

    public String getPassword() {
        return this.password;
    }


    /**
     * 角色列表.
     */
    protected final String roles[];

    public String[] getRoles() {
        return this.roles;
    }


    /**
     * 暴露给应用程序的已验证的Principal.
     */
    protected final Principal userPrincipal;

    @Override
    public Principal getUserPrincipal() {
        if (userPrincipal != null) {
            return userPrincipal;
        } else {
            return this;
        }
    }


    /**
     * JAAS LoginContext, 用于验证这个Principal.
     * Kept so we can call logout().
     */
    protected final transient LoginContext loginContext;


    /**
     * 用户的委托的凭据.
     */
    protected transient GSSCredential gssCredential = null;

    @Override
    public GSSCredential getGssCredential() {
        return this.gssCredential;
    }
    protected void setGssCredential(GSSCredential gssCredential) {
        this.gssCredential = gssCredential;
    }


    // ---------------------------------------------------------- Public Methods

    /**
     * 这个Principal代表的用户是否具有指定的角色?
     *
     * @param role 要测试的角色
     *
     * @return <code>true</code>如果这个Principal 具有指定的角色, 否则<code>false</code>
     */
    public boolean hasRole(String role) {
        if ("*".equals(role)) {// Special 2.4 role meaning everyone
            return true;
        }
        if (role == null) {
            return false;
        }
        return Arrays.binarySearch(roles, role) >= 0;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GenericPrincipal[");
        sb.append(this.name);
        sb.append("(");
        for (int i = 0; i < roles.length; i++ ) {
            sb.append( roles[i]).append(",");
        }
        sb.append(")]");
        return sb.toString();
    }


    /**
     * 在关联的JAASLoginContext上调用 logout. 将来可能扩展以覆盖其他注销需求.
     *
     * @throws Exception 如果注销出了问题. 使用Exception允许将来扩展此方法以覆盖可能抛出不同异常到LoginContext的其他退出机制
     */
    @Override
    public void logout() throws Exception {
        if (loginContext != null) {
            loginContext.logout();
        }
        if (gssCredential != null) {
            gssCredential.dispose();
        }
    }


    // ----------------------------------------------------------- Serialization

    private Object writeReplace() {
        return new SerializablePrincipal(name, password, roles, userPrincipal);
    }

    private static class SerializablePrincipal implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final String password;
        private final String[] roles;
        private final Principal principal;

        public SerializablePrincipal(String name, String password, String[] roles,
                Principal principal) {
            this.name = name;
            this.password = password;
            this.roles = roles;
            if (principal instanceof Serializable) {
                this.principal = principal;
            } else {
                this.principal = null;
            }
        }

        private Object readResolve() {
            return new GenericPrincipal(name, password, Arrays.asList(roles), principal);
        }
    }
}
