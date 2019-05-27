package org.apache.catalina.mbeans;


import java.util.ArrayList;
import java.util.Iterator;

import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.users.MemoryUserDatabase</code> component.</p>
 */
public class MemoryUserDatabaseMBean extends BaseModelMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public MemoryUserDatabaseMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 管理bean的配置信息注册表.
     */
    protected final Registry registry = MBeanUtils.createRegistry();


    /**
     * 描述这个MBean的<code>ManagedBean</code>信息
     */
    protected final ManagedBean managed =
        registry.findManagedBean("MemoryUserDatabase");


    /**
     * 描述这个Group MBean的<code>ManagedBean</code>信息
     */
    protected final ManagedBean managedGroup =
        registry.findManagedBean("Group");


    /**
     * 描述这个Role MBean的<code>ManagedBean</code>信息
     */
    protected final ManagedBean managedRole =
        registry.findManagedBean("Role");


    /**
     * 描述这个User MBean的<code>ManagedBean</code>信息
     */
    protected final ManagedBean managedUser =
        registry.findManagedBean("User");


    // ------------------------------------------------------------- Attributes


    /**
     * @return 数据库中定义的所有组的MBean的名字.
     */
    public String[] getGroups() {

        UserDatabase database = (UserDatabase) this.resource;
        ArrayList<String> results = new ArrayList<>();
        Iterator<Group> groups = database.getGroups();
        while (groups.hasNext()) {
            Group group = groups.next();
            results.add(findGroup(group.getGroupname()));
        }
        return results.toArray(new String[results.size()]);

    }


    /**
     * @return 数据库中定义的所有角色的MBean的名字.
     */
    public String[] getRoles() {

        UserDatabase database = (UserDatabase) this.resource;
        ArrayList<String> results = new ArrayList<>();
        Iterator<Role> roles = database.getRoles();
        while (roles.hasNext()) {
            Role role = roles.next();
            results.add(findRole(role.getRolename()));
        }
        return results.toArray(new String[results.size()]);
    }


    /**
     * @return 数据库中定义的所有用户的MBean的名字.
     */
    public String[] getUsers() {

        UserDatabase database = (UserDatabase) this.resource;
        ArrayList<String> results = new ArrayList<>();
        Iterator<User> users = database.getUsers();
        while (users.hasNext()) {
            User user = users.next();
            results.add(findUser(user.getUsername()));
        }
        return results.toArray(new String[results.size()]);
    }


    // ------------------------------------------------------------- Operations


    /**
     * 创建一个新组，并返回相应的MBean的名字
     *
     * @param groupname 新组的组名
     * @param description 新组的描述
     */
    public String createGroup(String groupname, String description) {

        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.createGroup(groupname, description);
        try {
            MBeanUtils.createMBean(group);
        } catch (Exception e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Exception creating group [" + groupname + "] MBean");
            iae.initCause(e);
            throw iae;
        }
        return (findGroup(groupname));

    }


    /**
     * 创建一个新角色，并返回相应的MBean的名字
     *
     * @param rolename 新角色的角色名
     * @param description 新角色的描述
     */
    public String createRole(String rolename, String description) {

        UserDatabase database = (UserDatabase) this.resource;
        Role role = database.createRole(rolename, description);
        try {
            MBeanUtils.createMBean(role);
        } catch (Exception e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Exception creating role [" + rolename + "] MBean");
            iae.initCause(e);
            throw iae;
        }
        return (findRole(rolename));
    }


    /**
     * 创建一个新用户，并返回相应的MBean的名字
     *
     * @param username 新用户的用户名
     * @param password 新用户的密码
     * @param fullName 新用户的全名
     */
    public String createUser(String username, String password,
                             String fullName) {

        UserDatabase database = (UserDatabase) this.resource;
        User user = database.createUser(username, password, fullName);
        try {
            MBeanUtils.createMBean(user);
        } catch (Exception e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Exception creating user [" + username + "] MBean");
            iae.initCause(e);
            throw iae;
        }
        return (findUser(username));

    }


    /**
     * 返回指定的组名的MBean的名字;或者<code>null</code>.
     *
     * @param groupname 组名
     */
    public String findGroup(String groupname) {

        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.findGroup(groupname);
        if (group == null) {
            return (null);
        }
        try {
            ObjectName oname =
                MBeanUtils.createObjectName(managedGroup.getDomain(), group);
            return (oname.toString());
        } catch (MalformedObjectNameException e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Cannot create object name for group [" + groupname + "]");
            iae.initCause(e);
            throw iae;
        }

    }


    /**
     * 返回指定角色名的MBean的名字;或者<code>null</code>.
     *
     * @param rolename Role name to look up
     */
    public String findRole(String rolename) {

        UserDatabase database = (UserDatabase) this.resource;
        Role role = database.findRole(rolename);
        if (role == null) {
            return (null);
        }
        try {
            ObjectName oname =
                MBeanUtils.createObjectName(managedRole.getDomain(), role);
            return (oname.toString());
        } catch (MalformedObjectNameException e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Cannot create object name for role [" + rolename + "]");
            iae.initCause(e);
            throw iae;
        }

    }


    /**
     * 返回指定用户名的MBean的名字;或者<code>null</code>.
     *
     * @param username User name to look up
     */
    public String findUser(String username) {

        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        if (user == null) {
            return (null);
        }
        try {
            ObjectName oname =
                MBeanUtils.createObjectName(managedUser.getDomain(), user);
            return (oname.toString());
        } catch (MalformedObjectNameException e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Cannot create object name for user [" + username + "]");
            iae.initCause(e);
            throw iae;
        }

    }


    /**
     * 删除现有的组和销毁相应的MBean
     *
     * @param groupname Group name to remove
     */
    public void removeGroup(String groupname) {

        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.findGroup(groupname);
        if (group == null) {
            return;
        }
        try {
            MBeanUtils.destroyMBean(group);
            database.removeGroup(group);
        } catch (Exception e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Exception destroying group [" + groupname + "] MBean");
            iae.initCause(e);
            throw iae;
        }

    }


    /**
     * 删除现有的角色和销毁相应的MBean
     *
     * @param rolename Role name to remove
     */
    public void removeRole(String rolename) {

        UserDatabase database = (UserDatabase) this.resource;
        Role role = database.findRole(rolename);
        if (role == null) {
            return;
        }
        try {
            MBeanUtils.destroyMBean(role);
            database.removeRole(role);
        } catch (Exception e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Exception destroying role [" + rolename + "] MBean");
            iae.initCause(e);
            throw iae;
        }

    }


    /**
     * 删除现有的用户和销毁相应的MBean
     *
     * @param username User name to remove
     */
    public void removeUser(String username) {

        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        if (user == null) {
            return;
        }
        try {
            MBeanUtils.destroyMBean(user);
            database.removeUser(user);
        } catch (Exception e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Exception destroying user [" + username + "] MBean");
            iae.initCause(e);
            throw iae;
        }
    }
}
