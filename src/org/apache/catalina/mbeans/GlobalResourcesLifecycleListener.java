package org.apache.catalina.mbeans;


import java.util.Iterator;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;

import org.apache.catalina.Group;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;


/**
 * <code>LifecycleListener</code>实现类，实例化管理的全局JNDI资源相关的MBeans集合.
 */
public class GlobalResourcesLifecycleListener implements LifecycleListener {
	
    private static final Log log = LogFactory.getLog(GlobalResourcesLifecycleListener.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 附属的Catalina组件.
     */
    protected Lifecycle component = null;


    /**
     * 管理bean的配置信息注册表.
     */
    protected static final Registry registry = MBeanUtils.createRegistry();


    // ---------------------------------------------- LifecycleListener Methods


    /**
     * 启动和关闭事件的主要入口点.
     *
     * @param event The event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.START_EVENT.equals(event.getType())) {
            component = event.getLifecycle();
            createMBeans();
        } else if (Lifecycle.STOP_EVENT.equals(event.getType())) {
            destroyMBeans();
            component = null;
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 为相关的全局JNDI资源创建MBeans.
     */
    protected void createMBeans() {

        // 查找全局命名上下文
        Context context = null;
        try {
            context = (Context) (new InitialContext()).lookup("java:/");
        } catch (NamingException e) {
            log.error("No global naming context defined for server");
            return;
        }

        // 遍历定义的全局JNDI资源上下文
        try {
            createMBeans("", context);
        } catch (NamingException e) {
            log.error("Exception processing Global JNDI Resources", e);
        }

    }


    /**
     * 为指定命名上下文相关的全局JNDI资源创建MBeans.
     *
     * @param prefix 完整对象名称路径的前缀
     * @param context 要扫描的上下文
     *
     * @exception NamingException 如果发生JNDI异常
     */
    protected void createMBeans(String prefix, Context context)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug("Creating MBeans for Global JNDI Resources in Context '" +
                prefix + "'");
        }

        try {
            NamingEnumeration<Binding> bindings = context.listBindings("");
            while (bindings.hasMore()) {
                Binding binding = bindings.next();
                String name = prefix + binding.getName();
                Object value = context.lookup(binding.getName());
                if (log.isDebugEnabled()) {
                    log.debug("Checking resource " + name);
                }
                if (value instanceof Context) {
                    createMBeans(name + "/", (Context) value);
                } else if (value instanceof UserDatabase) {
                    try {
                        createMBeans(name, (UserDatabase) value);
                    } catch (Exception e) {
                        log.error("Exception creating UserDatabase MBeans for " + name,
                                e);
                    }
                }
            }
        } catch( RuntimeException ex) {
            log.error("RuntimeException " + ex);
        } catch( OperationNotSupportedException ex) {
            log.error("Operation not supported " + ex);
        }

    }


    /**
     * 为指定的UserDatabase和它的内容创建MBeans.
     *
     * @param name 这个 UserDatabase完整的资源名称
     * @param database 要处理的 UserDatabase
     *
     * @exception Exception 如果创建MBeans时发生异常
     */
    protected void createMBeans(String name, UserDatabase database)
        throws Exception {

        // Create the MBean for the UserDatabase itself
        if (log.isDebugEnabled()) {
            log.debug("Creating UserDatabase MBeans for resource " + name);
            log.debug("Database=" + database);
        }
        try {
            MBeanUtils.createMBean(database);
        } catch(Exception e) {
            throw new IllegalArgumentException(
                    "Cannot create UserDatabase MBean for resource " + name, e);
        }

        // Create the MBeans for each defined Role
        Iterator<Role> roles = database.getRoles();
        while (roles.hasNext()) {
            Role role = roles.next();
            if (log.isDebugEnabled()) {
                log.debug("  Creating Role MBean for role " + role);
            }
            try {
                MBeanUtils.createMBean(role);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot create Role MBean for role " + role, e);
            }
        }

        // Create the MBeans for each defined Group
        Iterator<Group> groups = database.getGroups();
        while (groups.hasNext()) {
            Group group = groups.next();
            if (log.isDebugEnabled()) {
                log.debug("  Creating Group MBean for group " + group);
            }
            try {
                MBeanUtils.createMBean(group);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot create Group MBean for group " + group, e);
            }
        }

        // Create the MBeans for each defined User
        Iterator<User> users = database.getUsers();
        while (users.hasNext()) {
            User user = users.next();
            if (log.isDebugEnabled()) {
                log.debug("  Creating User MBean for user " + user);
            }
            try {
                MBeanUtils.createMBean(user);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot create User MBean for user " + user, e);
            }
        }
    }


    /**
     * 为相关的全局JNDI资源销毁MBeans.
     */
    protected void destroyMBeans() {
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBeans for Global JNDI Resources");
        }
    }
}
