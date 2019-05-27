package org.apache.catalina.mbeans;

import java.util.Set;

import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Group;
import org.apache.catalina.Loader;
import org.apache.catalina.Role;
import org.apache.catalina.Server;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.util.ContextName;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;


/**
 * 服务器端MBeans实现类的公用方法
 */
public class MBeanUtils {

    // ------------------------------------------------------- Static Variables

    /**
     * <code>createManagedBean()</code>使用的常规规则的异常集合.
     * 每对的第一个元素是类名，第二个元素是托管bean名.
     */
    private static final String exceptions[][] = {
        { "org.apache.catalina.users.MemoryGroup",
          "Group" },
        { "org.apache.catalina.users.MemoryRole",
          "Role" },
        { "org.apache.catalina.users.MemoryUser",
          "User" },
    };


    /**
     * 管理bean的配置信息注册表
     */
    private static Registry registry = createRegistry();


    /**
     * The <code>MBeanServer</code> for this application.
     */
    private static MBeanServer mserver = createServer();


    // --------------------------------------------------------- Static Methods

    /**
     * 创建并返回这个Catalina组件相应的<code>ManagedBean</code>名称.
     *
     * @param component 用于创建名称的组件
     */
    static String createManagedName(Object component) {

        // 处理标准规则中的异常
        String className = component.getClass().getName();
        for (int i = 0; i < exceptions.length; i++) {
            if (className.equals(exceptions[i][0])) {
                return (exceptions[i][1]);
            }
        }

        // 执行标准转换
        int period = className.lastIndexOf('.');
        if (period >= 0)
            className = className.substring(period + 1);
        return (className);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Connector</code>对象.
     *
     * @param environment 要管理的ContextEnvironment
     * 
     * @exception Exception 如果无法创建或注册MBean
     */
    public static DynamicMBean createMBean(ContextEnvironment environment)
        throws Exception {

        String mname = createManagedName(environment);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(environment);
        ObjectName oname = createObjectName(domain, environment);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>ContextResource</code>对象.
     *
     * @param resource The ContextResource to be managed
     * 
     * @exception Exception 如果无法创建或注册MBean
     */
    public static DynamicMBean createMBean(ContextResource resource)
        throws Exception {

        String mname = createManagedName(resource);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(resource);
        ObjectName oname = createObjectName(domain, resource);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>ContextResourceLink</code>对象.
     *
     * @param resourceLink The ContextResourceLink to be managed
     * 
     * @exception Exception 如果无法创建或注册MBean
     */
    public static DynamicMBean createMBean(ContextResourceLink resourceLink)
        throws Exception {

        String mname = createManagedName(resourceLink);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(resourceLink);
        ObjectName oname = createObjectName(domain, resourceLink);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Group</code>对象.
     *
     * @param group The Group to be managed
     * 
     * @exception Exception 如果无法创建或注册MBean
     */
    static DynamicMBean createMBean(Group group)
        throws Exception {

        String mname = createManagedName(group);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(group);
        ObjectName oname = createObjectName(domain, group);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Role</code>对象.
     *
     * @param role The Role to be managed
     * 
     * @exception Exception 如果无法创建或注册MBean
     */
    static DynamicMBean createMBean(Role role)
        throws Exception {

        String mname = createManagedName(role);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(role);
        ObjectName oname = createObjectName(domain, role);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>User</code>对象.
     *
     * @param user The User to be managed
     * 
     * @exception Exception 如果无法创建或注册MBean
     */
    static DynamicMBean createMBean(User user)
        throws Exception {

        String mname = createManagedName(user);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(user);
        ObjectName oname = createObjectName(domain, user);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>UserDatabase</code>对象.
     *
     * @param userDatabase The UserDatabase to be managed
     * 
     * @exception Exception 如果无法创建或注册MBean
     */
    static DynamicMBean createMBean(UserDatabase userDatabase)
        throws Exception {

        String mname = createManagedName(userDatabase);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(userDatabase);
        ObjectName oname = createObjectName(domain, userDatabase);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 为这个<code>Service</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param environment The ContextEnvironment to be named
     * 
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    public static ObjectName createObjectName(String domain,
                                              ContextEnvironment environment)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Object container =
                environment.getNamingResources().getContainer();
        if (container instanceof Server) {
            name = new ObjectName(domain + ":type=Environment" +
                        ",resourcetype=Global,name=" + environment.getName());
        } else if (container instanceof Context) {
            Context context = ((Context)container);
            ContextName cn = new ContextName(context.getName(), false);
            Container host = context.getParent();
            name = new ObjectName(domain + ":type=Environment" +
                        ",resourcetype=Context,host=" + host.getName() +
                        ",context=" + cn.getDisplayName() +
                        ",name=" + environment.getName());
        }
        return (name);
    }


    /**
     * 为这个<code>ContextResource</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param resource The ContextResource to be named
     * 
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    public static ObjectName createObjectName(String domain,
                                              ContextResource resource)
        throws MalformedObjectNameException {

        ObjectName name = null;
        String quotedResourceName = ObjectName.quote(resource.getName());
        Object container =
                resource.getNamingResources().getContainer();
        if (container instanceof Server) {
            name = new ObjectName(domain + ":type=Resource" +
                    ",resourcetype=Global,class=" + resource.getType() +
                    ",name=" + quotedResourceName);
        } else if (container instanceof Context) {
            Context context = ((Context)container);
            ContextName cn = new ContextName(context.getName(), false);
            Container host = context.getParent();
            name = new ObjectName(domain + ":type=Resource" +
                    ",resourcetype=Context,host=" + host.getName() +
                    ",context=" + cn.getDisplayName() +
                    ",class=" + resource.getType() +
                    ",name=" + quotedResourceName);
        }

        return (name);

    }


     /**
     * 为这个<code>ContextResourceLink</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param resourceLink The ContextResourceLink to be named
     * 
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    public static ObjectName createObjectName(String domain,
                                              ContextResourceLink resourceLink)
        throws MalformedObjectNameException {

        ObjectName name = null;
        String quotedResourceLinkName
                = ObjectName.quote(resourceLink.getName());
        Object container =
                resourceLink.getNamingResources().getContainer();
        if (container instanceof Server) {
            name = new ObjectName(domain + ":type=ResourceLink" +
                    ",resourcetype=Global" +
                    ",name=" + quotedResourceLinkName);
        } else if (container instanceof Context) {
            Context context = ((Context)container);
            ContextName cn = new ContextName(context.getName(), false);
            Container host = context.getParent();
            name = new ObjectName(domain + ":type=ResourceLink" +
                    ",resourcetype=Context,host=" + host.getName() +
                    ",context=" + cn.getDisplayName() +
                    ",name=" + quotedResourceLinkName);
        }

        return (name);

    }


    /**
     * 为这个<code>Group</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param group The Group to be named
     * 
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Group group)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=Group,groupname=" +
                              ObjectName.quote(group.getGroupname()) +
                              ",database=" + group.getUserDatabase().getId());
        return (name);
    }


    /**
     * 为这个<code>Loader</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param loader The Loader to be named
     * 
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain, Loader loader)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Context context = loader.getContext();

        ContextName cn = new ContextName(context.getName(), false);
        Container host = context.getParent();
        name = new ObjectName(domain + ":type=Loader,host=" + host.getName() +
                ",context=" + cn.getDisplayName());

        return name;
    }


    /**
     * 为这个<code>Role</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param role The Role to be named
     * 
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain, Role role)
            throws MalformedObjectNameException {

         ObjectName name = new ObjectName(domain + ":type=Role,rolename=" +
                 ObjectName.quote(role.getRolename()) +
                 ",database=" + role.getUserDatabase().getId());
        return name;
    }


    /**
     * 为这个<code>User</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param user The User to be named
     * 
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain, User user)
            throws MalformedObjectNameException {

        ObjectName name = new ObjectName(domain + ":type=User,username=" +
                ObjectName.quote(user.getUsername()) +
                ",database=" + user.getUserDatabase().getId());
        return name;
    }


    /**
     * 为这个<code>UserDatabase</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param userDatabase The UserDatabase to be named
     * @return a new object name
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              UserDatabase userDatabase)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=UserDatabase,database=" +
                              userDatabase.getId());
        return (name);

    }

    /**
     * 创建, 配置（如果有必要）并返回管理对象描述的注册表.
     */
    public static synchronized Registry createRegistry() {

        if (registry == null) {
            registry = Registry.getRegistry(null, null);
            ClassLoader cl = MBeanUtils.class.getClassLoader();

            registry.loadDescriptors("org.apache.catalina.mbeans",  cl);
            registry.loadDescriptors("org.apache.catalina.authenticator", cl);
            registry.loadDescriptors("org.apache.catalina.core", cl);
            registry.loadDescriptors("org.apache.catalina", cl);
            registry.loadDescriptors("org.apache.catalina.deploy", cl);
            registry.loadDescriptors("org.apache.catalina.loader", cl);
            registry.loadDescriptors("org.apache.catalina.realm", cl);
            registry.loadDescriptors("org.apache.catalina.session", cl);
            registry.loadDescriptors("org.apache.catalina.startup", cl);
            registry.loadDescriptors("org.apache.catalina.users", cl);
            registry.loadDescriptors("org.apache.catalina.ha", cl);
            registry.loadDescriptors("org.apache.catalina.connector", cl);
            registry.loadDescriptors("org.apache.catalina.valves",  cl);
            registry.loadDescriptors("org.apache.catalina.storeconfig",  cl);
            registry.loadDescriptors("org.apache.tomcat.util.descriptor.web",  cl);
        }
        return (registry);
    }


    /**
     * 创建, 配置（如果需要）并返回<code>MBeanServer</code>，我们将注册<code>DynamicMBean</code>实现类到该MBeanServer中.
     */
    public static synchronized MBeanServer createServer() {

        if (mserver == null) {
            mserver = Registry.getRegistry(null, null).getMBeanServer();
        }
        return (mserver);
    }


    /**
     * 为这个<code>ContextEnvironment</code>对象注销MBean.
     *
     * @param environment The ContextEnvironment to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    public static void destroyMBean(ContextEnvironment environment)
        throws Exception {

        String mname = createManagedName(environment);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, environment);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * 为这个<code>ContextResource</code>对象注销MBean.
     *
     * @param resource The ContextResource to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    public static void destroyMBean(ContextResource resource)
        throws Exception {

        // 如果用户数据库资源需要销毁 groups, roles, users, UserDatabase mbean
        if ("org.apache.catalina.UserDatabase".equals(resource.getType())) {
            destroyMBeanUserDatabase(resource.getName());
        }

        String mname = createManagedName(resource);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, resource);
        if( mserver.isRegistered(oname ))
            mserver.unregisterMBean(oname);

    }


    /**
     * 为这个<code>ContextResourceLink</code>对象注销MBean.
     *
     * @param resourceLink The ContextResourceLink to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    public static void destroyMBean(ContextResourceLink resourceLink)
        throws Exception {

        String mname = createManagedName(resourceLink);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, resourceLink);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }

    /**
     * 为这个<code>Group</code>对象注销MBean.
     *
     * @param group The Group to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Group group)
        throws Exception {

        String mname = createManagedName(group);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, group);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * 为这个<code>Role</code>对象注销MBean.
     *
     * @param role The Role to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Role role)
        throws Exception {

        String mname = createManagedName(role);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, role);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * 为这个<code>User</code>对象注销MBean.
     *
     * @param user The User to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(User user)
        throws Exception {

        String mname = createManagedName(user);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, user);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * 注销<code>UserDatabase</code>对象指定名称的MBean.
     *
     * @param userDatabase The UserDatabase to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBeanUserDatabase(String userDatabase)
        throws Exception {

        ObjectName query = null;
        Set<ObjectName> results = null;

        // Groups
        query = new ObjectName(
                "Users:type=Group,database=" + userDatabase + ",*");
        results = mserver.queryNames(query, null);
        for(ObjectName result : results) {
            mserver.unregisterMBean(result);
        }

        // Roles
        query = new ObjectName(
                "Users:type=Role,database=" + userDatabase + ",*");
        results = mserver.queryNames(query, null);
        for(ObjectName result : results) {
            mserver.unregisterMBean(result);
        }

        // Users
        query = new ObjectName(
                "Users:type=User,database=" + userDatabase + ",*");
        results = mserver.queryNames(query, null);
        for(ObjectName result : results) {
            mserver.unregisterMBean(result);
        }

        // The database itself
        ObjectName db = new ObjectName(
                "Users:type=UserDatabase,database=" + userDatabase);
        if( mserver.isRegistered(db) ) {
            mserver.unregisterMBean(db);
        }
    }
}
