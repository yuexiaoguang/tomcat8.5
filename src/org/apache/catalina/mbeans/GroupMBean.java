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
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.Group</code> component.</p>
 */
public class GroupMBean extends BaseModelMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public GroupMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 管理bean的配置信息注册表.
     */
    protected final Registry registry = MBeanUtils.createRegistry();


    /**
     * 描述这个MBean的<code>ManagedBean</code>.
     */
    protected final ManagedBean managed = registry.findManagedBean("Group");


    // ------------------------------------------------------------- Attributes


    /**
     * @return 这个组的所有授权角色的MBean的名字.
     */
    public String[] getRoles() {

        Group group = (Group) this.resource;
        ArrayList<String> results = new ArrayList<>();
        Iterator<Role> roles = group.getRoles();
        while (roles.hasNext()) {
            Role role = null;
            try {
                role = roles.next();
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), role);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for role " + role);
                iae.initCause(e);
                throw iae;
            }
        }
        return results.toArray(new String[results.size()]);

    }


    /**
     * @return 这个组所有成员用户的MBean名称.
     */
    public String[] getUsers() {

        Group group = (Group) this.resource;
        ArrayList<String> results = new ArrayList<>();
        Iterator<User> users = group.getUsers();
        while (users.hasNext()) {
            User user = null;
            try {
                user = users.next();
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), user);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for user " + user);
                iae.initCause(e);
                throw iae;
            }
        }
        return results.toArray(new String[results.size()]);
    }


    // ------------------------------------------------------------- Operations


    /**
     * 添加一个新的{@link Role}到组中.
     *
     * @param rolename Role name of the new role
     */
    public void addRole(String rolename) {

        Group group = (Group) this.resource;
        if (group == null) {
            return;
        }
        Role role = group.getUserDatabase().findRole(rolename);
        if (role == null) {
            throw new IllegalArgumentException
                ("Invalid role name '" + rolename + "'");
        }
        group.addRole(role);

    }


    /**
     * 从这个组中移除一个{@link Role}
     *
     * @param rolename Role name of the old role
     */
    public void removeRole(String rolename) {

        Group group = (Group) this.resource;
        if (group == null) {
            return;
        }
        Role role = group.getUserDatabase().findRole(rolename);
        if (role == null) {
            throw new IllegalArgumentException
                ("Invalid role name '" + rolename + "'");
        }
        group.removeRole(role);
    }
}
