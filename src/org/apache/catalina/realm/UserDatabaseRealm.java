package org.apache.catalina.realm;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.naming.Context;

import org.apache.catalina.Group;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.Wrapper;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * <p>{@link Realm}实现类，基于一个{@link UserDatabase}实现类变得可用，通过这个Catalina实例的全局JNDI资源配置.
 * 设置<code>resourceName</code>参数为全局JNDI资源名称，为配置的<code>UserDatabase</code>实例.</p>
 */
public class UserDatabaseRealm extends RealmBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于验证用户和相关角色的<code>UserDatabase</code>.
     */
    protected UserDatabase database = null;


    /**
     * 描述信息.
     * @deprecated This will be removed in Tomcat 9 onwards.
     */
    @Deprecated
    protected static final String name = "UserDatabaseRealm";


    /**
     * 将使用的<code>UserDatabase</code>资源的全局JNDI名称.
     */
    protected String resourceName = "UserDatabase";


    // ------------------------------------------------------------- Properties

    /**
     * @return 将使用的<code>UserDatabase</code>资源的全局JNDI名称.
     */
    public String getResourceName() {
        return resourceName;
    }


    /**
     * 设置将使用的<code>UserDatabase</code>资源的全局JNDI名称.
     *
     * @param resourceName 全局 JNDI 名称
     */
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回<code>true</code>如果指定的Principal具有指定的安全角色, 在这个 Realm上下文中; 否则返回<code>false</code>.
     * 这个实现返回<code>true</code>, 如果<code>User</code>有这个角色, 或者用户所在的<code>Group</code>有这个角色. 
     *
     * @param principal 要验证角色的Principal
     * @param role 要验证的安全角色
     */
    @Override
    public boolean hasRole(Wrapper wrapper, Principal principal, String role) {
        // Check for a role alias defined in a <security-role-ref> element
        if (wrapper != null) {
            String realRole = wrapper.findSecurityReference(role);
            if (realRole != null)
                role = realRole;
        }
        if( principal instanceof GenericPrincipal) {
            GenericPrincipal gp = (GenericPrincipal)principal;
            if(gp.getUserPrincipal() instanceof User) {
                principal = gp.getUserPrincipal();
            }
        }
        if(! (principal instanceof User) ) {
            //Play nice with SSO and mixed Realms
            return super.hasRole(null, principal, role);
        }
        if("*".equals(role)) {
            return true;
        } else if(role == null) {
            return false;
        }
        User user = (User)principal;
        Role dbrole = database.findRole(role);
        if(dbrole == null) {
            return false;
        }
        if(user.isInRole(dbrole)) {
            return true;
        }
        Iterator<Group> groups = user.getGroups();
        while(groups.hasNext()) {
            Group group = groups.next();
            if(group.isInRole(dbrole)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------ Protected Methods


    @Override
    @Deprecated
    protected String getName() {
        return name;
    }


    /**
     * 返回指定用户名的密码.
     */
    @Override
    protected String getPassword(String username) {

        User user = database.findUser(username);

        if (user == null) {
            return null;
        }

        return (user.getPassword());
    }


    /**
     * 返回指定用户名的Principal.
     */
    @Override
    protected Principal getPrincipal(String username) {

        User user = database.findUser(username);
        if(user == null) {
            return null;
        }

        List<String> roles = new ArrayList<>();
        Iterator<Role> uroles = user.getRoles();
        while(uroles.hasNext()) {
            Role role = uroles.next();
            roles.add(role.getName());
        }
        Iterator<Group> groups = user.getGroups();
        while(groups.hasNext()) {
            Group group = groups.next();
            uroles = group.getRoles();
            while(uroles.hasNext()) {
                Role role = uroles.next();
                roles.add(role.getName());
            }
        }
        return new GenericPrincipal(username, user.getPassword(), roles, user);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * @exception LifecycleException 如果此组件检测到阻止其启动的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {

        try {
            Context context = getServer().getGlobalNamingContext();
            database = (UserDatabase) context.lookup(resourceName);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            containerLog.error(sm.getString("userDatabaseRealm.lookup",
                                            resourceName),
                               e);
            database = null;
        }
        if (database == null) {
            throw new LifecycleException
                (sm.getString("userDatabaseRealm.noDatabase", resourceName));
        }

        super.startInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        // Perform normal superclass finalization
        super.stopInternal();

        // 释放对数据库的引用
        database = null;
    }
}
