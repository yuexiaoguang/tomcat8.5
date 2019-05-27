package org.apache.catalina.mbeans;

import java.io.File;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.DataSourceRealm;
import org.apache.catalina.realm.JDBCRealm;
import org.apache.catalina.realm.JNDIRealm;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.UserDatabaseRealm;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.HostConfig;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class MBeanFactory {

    private static final Log log = LogFactory.getLog(MBeanFactory.class);

    protected static final StringManager sm = StringManager.getManager(MBeanFactory.class);

    /**
     * 应用的<code>MBeanServer</code>.
     */
    private static final MBeanServer mserver = MBeanUtils.createServer();


    // ------------------------------------------------------------- Attributes

    /**
     * 创建这个工厂的容器(Server/Service).
     */
    private Object container;


    // ------------------------------------------------------------- Operations

    /**
     * 设置创建这个工厂的容器.
     * 
     * @param container 关联的容器
     */
    public void setContainer(Object container) {
        this.container = container;
    }


    /**
     * 检索路径串时删除冗余代码的一种简便方法
     *
     * @param t 路径
     * @return empty string if t==null || t.equals("/")
     */
    private final String getPathStr(String t) {
        if (t == null || t.equals("/")) {
            return "";
        }
        return t;
    }

   /**
     * 获取指定父级的ObjectName对应的父级 ContainerBase
     */
    private Container getParentContainerFromParent(ObjectName pname)
        throws Exception {

        String type = pname.getKeyProperty("type");
        String j2eeType = pname.getKeyProperty("j2eeType");
        Service service = getService(pname);
        StandardEngine engine = (StandardEngine) service.getContainer();
        if ((j2eeType!=null) && (j2eeType.equals("WebModule"))) {
            String name = pname.getKeyProperty("name");
            name = name.substring(2);
            int i = name.indexOf('/');
            String hostName = name.substring(0,i);
            String path = name.substring(i);
            Container host = engine.findChild(hostName);
            String pathStr = getPathStr(path);
            Container context = host.findChild(pathStr);
            return context;
        } else if (type != null) {
            if (type.equals("Engine")) {
                return engine;
            } else if (type.equals("Host")) {
                String hostName = pname.getKeyProperty("host");
                Container host = engine.findChild(hostName);
                return host;
            }
        }
        return null;

    }


    /**
     * 获取指定子级的ObjectName对应的父级 ContainerBase
     */
    private Container getParentContainerFromChild(ObjectName oname)
        throws Exception {

        String hostName = oname.getKeyProperty("host");
        String path = oname.getKeyProperty("path");
        Service service = getService(oname);
        Container engine = service.getContainer();
        if (hostName == null) {
            // child's container is Engine
            return engine;
        } else if (path == null) {
            // child's container is Host
            Container host = engine.findChild(hostName);
            return host;
        } else {
            // child's container is Context
            Container host = engine.findChild(hostName);
            path = getPathStr(path);
            Container context = host.findChild(path);
            return context;
        }
    }


    private Service getService(ObjectName oname) throws Exception {

        if (container instanceof Service) {
            // Don't bother checking the domain - this is the only option
            return (Service) container;
        }

        StandardService service = null;
        String domain = oname.getDomain();
        if (container instanceof Server) {
            Service[] services = ((Server)container).findServices();
            for (int i = 0; i < services.length; i++) {
                service = (StandardService) services[i];
                if (domain.equals(service.getObjectName().getDomain())) {
                    break;
                }
            }
        }
        if (service == null ||
                !service.getObjectName().getDomain().equals(domain)) {
            throw new Exception("Service with the domain is not found");
        }
        return service;

    }


    /**
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     * 
     * @return 创建的连接器的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createAjpConnector(String parent, String address, int port)
        throws Exception {

        return createConnector(parent, address, port, true, false);
    }

    /**
     * Create a new DataSource Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param dataSourceName 数据源名称
     * @param roleNameCol 角色名称的列名
     * @param userCredCol 用户凭据的列名
     * @param userNameCol 用户名的列名
     * @param userRoleTable 角色表的表名
     * @param userTable 用户的表名
     * 
     * @return 创建的realm的对象名称
     * @exception Exception 如果不能创建或注册MBean
     */
    public String createDataSourceRealm(String parent, String dataSourceName,
        String roleNameCol, String userCredCol, String userNameCol,
        String userRoleTable, String userTable) throws Exception {

        // Create a new DataSourceRealm instance
        DataSourceRealm realm = new DataSourceRealm();
        realm.setDataSourceName(dataSourceName);
        realm.setRoleNameCol(roleNameCol);
        realm.setUserCredCol(userCredCol);
        realm.setUserNameCol(userNameCol);
        realm.setUserRoleTable(userRoleTable);
        realm.setUserTable(userTable);

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        // Add the new instance to its parent component
        container.setRealm(realm);
        // 返回对应的 MBean 名称
        ObjectName oname = realm.getObjectName();
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }
    }

    /**
     * Create a new HttpConnector
     *
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     * 
     * @return 创建的连接器的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createHttpConnector(String parent, String address, int port)
            throws Exception {
        return createConnector(parent, address, port, false, false);
    }

    /**
     * Create a new Connector
     *
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     * @param isAjp 创建一个AJP/1.3连接器
     * @param isSSL 创建安全连接器
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    private String createConnector(String parent, String address, int port, boolean isAjp, boolean isSSL)
        throws Exception {
        // Set the protocol
        String protocol = isAjp ? "AJP/1.3" : "HTTP/1.1";
        Connector retobj = new Connector(protocol);
        if ((address!=null) && (address.length()>0)) {
            retobj.setProperty("address", address);
        }
        // Set port number
        retobj.setPort(port);
        // Set SSL
        retobj.setSecure(isSSL);
        retobj.setScheme(isSSL ? "https" : "http");
        // Add the new instance to its parent component
        // FIX ME - addConnector will fail
        ObjectName pname = new ObjectName(parent);
        Service service = getService(pname);
        service.addConnector(retobj);

        // Return the corresponding MBean name
        ObjectName coname = retobj.getObjectName();

        return (coname.toString());
    }


    /**
     * Create a new HttpsConnector
     *
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     * 
     * @return 创建的连接器的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createHttpsConnector(String parent, String address, int port)
        throws Exception {
        return createConnector(parent, address, port, false, true);
    }

    /**
     * Create a new JDBC Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param driverName JDBC驱动器名称
     * @param connectionName 连接的用户名
     * @param connectionPassword 连接的密码
     * @param connectionURL 数据库的连接URL
     * 
     * @return 创建的realm的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createJDBCRealm(String parent, String driverName,
        String connectionName, String connectionPassword, String connectionURL)
        throws Exception {

        // Create a new JDBCRealm instance
        JDBCRealm realm = new JDBCRealm();
        realm.setDriverName(driverName);
        realm.setConnectionName(connectionName);
        realm.setConnectionPassword(connectionPassword);
        realm.setConnectionURL(connectionURL);

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        // Add the new instance to its parent component
        container.setRealm(realm);
        // Return the corresponding MBean name
        ObjectName oname = realm.getObjectName();

        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }
    }


    /**
     * Create a new JNDI Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     * 
     * @return 创建的realm的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createJNDIRealm(String parent)
        throws Exception {

         // Create a new JNDIRealm instance
        JNDIRealm realm = new JNDIRealm();

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        // Add the new instance to its parent component
        container.setRealm(realm);
        // Return the corresponding MBean name
        ObjectName oname = realm.getObjectName();

        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }
    }


    /**
     * Create a new Memory Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     * 
     * @return 创建的realm的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createMemoryRealm(String parent)
        throws Exception {

         // Create a new MemoryRealm instance
        MemoryRealm realm = new MemoryRealm();

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        // Add the new instance to its parent component
        container.setRealm(realm);
        // Return the corresponding MBean name
        ObjectName oname = realm.getObjectName();
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }
    }


   /**
     * Create a new StandardContext.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param path 这个Context的上下文路径
     * @param docBase 这个Context的文档基础目录 (or WAR)
     * 
     * @return 创建的上下文的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardContext(String parent,
                                        String path,
                                        String docBase)
        throws Exception {

        return createStandardContext(parent, path, docBase, false, false);
    }


    /**
     * Create a new StandardContext.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param path 此上下文的上下文路径
     * @param docBase 这个上下文的文档基目录(or WAR)
     * @param xmlValidation 是否验证XML描述符
     * @param xmlNamespaceAware 是否识别XML处理器命名空间
     * 
     * @return 创建的上下文的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardContext(String parent,
                                        String path,
                                        String docBase,
                                        boolean xmlValidation,
                                        boolean xmlNamespaceAware)
        throws Exception {

        // Create a new StandardContext instance
        StandardContext context = new StandardContext();
        path = getPathStr(path);
        context.setPath(path);
        context.setDocBase(docBase);
        context.setXmlValidation(xmlValidation);
        context.setXmlNamespaceAware(xmlNamespaceAware);

        ContextConfig contextConfig = new ContextConfig();
        context.addLifecycleListener(contextConfig);

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        ObjectName deployer = new ObjectName(pname.getDomain()+
                                             ":type=Deployer,host="+
                                             pname.getKeyProperty("host"));
        if(mserver.isRegistered(deployer)) {
            String contextName = context.getName();
            mserver.invoke(deployer, "addServiced",
                           new Object [] {contextName},
                           new String [] {"java.lang.String"});
            String configPath = (String)mserver.getAttribute(deployer,
                                                             "configBaseName");
            String baseName = context.getBaseName();
            File configFile = new File(new File(configPath), baseName+".xml");
            if (configFile.isFile()) {
                context.setConfigFile(configFile.toURI().toURL());
            }
            mserver.invoke(deployer, "manageApp",
                           new Object[] {context},
                           new String[] {"org.apache.catalina.Context"});
            mserver.invoke(deployer, "removeServiced",
                           new Object [] {contextName},
                           new String [] {"java.lang.String"});
        } else {
            log.warn("Deployer not found for "+pname.getKeyProperty("host"));
            Service service = getService(pname);
            Engine engine = service.getContainer();
            Host host = (Host) engine.findChild(pname.getKeyProperty("host"));
            host.addChild(context);
        }

        // Return the corresponding MBean name
        return context.getObjectName().toString();
    }


    /**
     * Create a new StandardHost.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param name 此主机的唯一名称
     * @param appBase 应用程序基目录名
     * @param autoDeploy 是否自动部署?
     * @param deployOnStartup 是否在服务器启动时部署?
     * @param deployXML 是否部署上下文XML配置文件属性?
     * @param unpackWARs 自动部署的时候，是否解压 WAR?
     * 
     * @return 创建的主机的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardHost(String parent, String name,
                                     String appBase,
                                     boolean autoDeploy,
                                     boolean deployOnStartup,
                                     boolean deployXML,
                                     boolean unpackWARs)
        throws Exception {

        // Create a new StandardHost instance
        StandardHost host = new StandardHost();
        host.setName(name);
        host.setAppBase(appBase);
        host.setAutoDeploy(autoDeploy);
        host.setDeployOnStartup(deployOnStartup);
        host.setDeployXML(deployXML);
        host.setUnpackWARs(unpackWARs);

        // add HostConfig for active reloading
        HostConfig hostConfig = new HostConfig();
        host.addLifecycleListener(hostConfig);

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Service service = getService(pname);
        Engine engine = service.getContainer();
        engine.addChild(host);

        // Return the corresponding MBean name
        return (host.getObjectName().toString());
    }


    /**
     * Creates a new StandardService and StandardEngine.
     *
     * @param domain       容器实例的域名
     * @param defaultHost  在Engine中使用的默认主机的名称
     * @param baseDir      Engine的基础目录
     * 
     * @return 创建的服务的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardServiceEngine(String domain,
            String defaultHost, String baseDir) throws Exception{

        if (!(container instanceof Server)) {
            throw new Exception("Container not Server");
        }

        StandardEngine engine = new StandardEngine();
        engine.setDomain(domain);
        engine.setName(domain);
        engine.setDefaultHost(defaultHost);

        Service service = new StandardService();
        service.setContainer(engine);
        service.setName(domain);

        ((Server) container).addService(service);

        return engine.getObjectName().toString();
    }


    /**
     * Create a new StandardManager.
     *
     * @param parent 关联的父级组件的MBean名称
     * 
     * @return 创建的管理器的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardManager(String parent)
        throws Exception {

        // Create a new StandardManager instance
        StandardManager manager = new StandardManager();

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        if (container instanceof Context) {
            ((Context) container).setManager(manager);
        } else {
            throw new Exception(sm.getString("mBeanFactory.managerContext"));
        }
        ObjectName oname = manager.getObjectName();
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }
    }


    /**
     * Create a new  UserDatabaseRealm.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param resourceName UserDatabase关联的全局 JNDI资源名称
     *  
     * @return 创建的realm的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createUserDatabaseRealm(String parent, String resourceName)
        throws Exception {

         // Create a new UserDatabaseRealm instance
        UserDatabaseRealm realm = new UserDatabaseRealm();
        realm.setResourceName(resourceName);

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        // Add the new instance to its parent component
        container.setRealm(realm);
        // Return the corresponding MBean name
        ObjectName oname = realm.getObjectName();
        // FIXME getObjectName() returns null
        //ObjectName oname =
        //    MBeanUtils.createObjectName(pname.getDomain(), realm);
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }

    }


    /**
     * 创建一个 Valve并将其关联到 {@link Container}.
     *
     * @param className 创建的{@link Valve}完全限定类名
     * @param parent    关联的父级{@link Container}的MBean名称
     *
     * @return  创建的{@link Valve}的MBean名称, 或<code>null</code>如果{@link Valve}没有实现{@link JmxEnabled}.
     * 
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createValve(String className, String parent)
            throws Exception {

        // Look for the parent
        ObjectName parentName = new ObjectName(parent);
        Container container = getParentContainerFromParent(parentName);

        if (container == null) {
            // TODO
            throw new IllegalArgumentException();
        }

        Valve valve = (Valve) Class.forName(className).getConstructor().newInstance();

        container.getPipeline().addValve(valve);

        if (valve instanceof JmxEnabled) {
            return ((JmxEnabled) valve).getObjectName().toString();
        } else {
            return null;
        }
    }


    /**
     * Create a new Web Application Loader.
     *
     * @param parent 关联的父级组件的MBean名称
     * 
     * @return 创建的加载器的对象名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createWebappLoader(String parent)
        throws Exception {

        // Create a new WebappLoader instance
        WebappLoader loader = new WebappLoader();

        // Add the new instance to its parent component
        ObjectName pname = new ObjectName(parent);
        Container container = getParentContainerFromParent(pname);
        if (container instanceof Context) {
            ((Context) container).setLoader(loader);
        }
        // FIXME add Loader.getObjectName
        //ObjectName oname = loader.getObjectName();
        ObjectName oname =
            MBeanUtils.createObjectName(pname.getDomain(), loader);
        return (oname.toString());

    }


    /**
     * 删除现有的Connector.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeConnector(String name) throws Exception {

        // 获取要移除的组件的引用
        ObjectName oname = new ObjectName(name);
        Service service = getService(oname);
        String port = oname.getKeyProperty("port");
        //String address = oname.getKeyProperty("address");

        Connector conns[] = service.findConnectors();

        for (int i = 0; i < conns.length; i++) {
            String connAddress = String.valueOf(conns[i].getProperty("address"));
            String connPort = ""+conns[i].getPort();

            // if (((address.equals("null")) &&
            if ((connAddress==null) && port.equals(connPort)) {
                service.removeConnector(conns[i]);
                conns[i].destroy();
                break;
            }
            // } else if (address.equals(connAddress))
            if (port.equals(connPort)) {
                // Remove this component from its parent component
                service.removeConnector(conns[i]);
                conns[i].destroy();
                break;
            }
        }
    }


    /**
     * 删除现有的 Context.
     *
     * @param contextName 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeContext(String contextName) throws Exception {

        // 获取要移除的组件的引用
        ObjectName oname = new ObjectName(contextName);
        String domain = oname.getDomain();
        StandardService service = (StandardService) getService(oname);

        Engine engine = service.getContainer();
        String name = oname.getKeyProperty("name");
        name = name.substring(2);
        int i = name.indexOf('/');
        String hostName = name.substring(0,i);
        String path = name.substring(i);
        ObjectName deployer = new ObjectName(domain+":type=Deployer,host="+
                                             hostName);
        String pathStr = getPathStr(path);
        if(mserver.isRegistered(deployer)) {
            mserver.invoke(deployer,"addServiced",
                           new Object[]{pathStr},
                           new String[] {"java.lang.String"});
            mserver.invoke(deployer,"unmanageApp",
                           new Object[] {pathStr},
                           new String[] {"java.lang.String"});
            mserver.invoke(deployer,"removeServiced",
                           new Object[] {pathStr},
                           new String[] {"java.lang.String"});
        } else {
            log.warn("Deployer not found for "+hostName);
            Host host = (Host) engine.findChild(hostName);
            Context context = (Context) host.findChild(pathStr);
            // Remove this component from its parent component
            host.removeChild(context);
            if(context instanceof StandardContext)
            try {
                ((StandardContext)context).destroy();
            } catch (Exception e) {
                log.warn("Error during context [" + context.getName() + "] destroy ", e);
           }

        }
    }


    /**
     * 删除现有的 Host.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeHost(String name) throws Exception {

        // 获取要移除的组件的引用
        ObjectName oname = new ObjectName(name);
        String hostName = oname.getKeyProperty("host");
        Service service = getService(oname);
        Engine engine = service.getContainer();
        Host host = (Host) engine.findChild(hostName);

        // Remove this component from its parent component
        if(host!=null) {
            engine.removeChild(host);
        }
    }


    /**
     * 删除现有的 Loader.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeLoader(String name) throws Exception {

        ObjectName oname = new ObjectName(name);
        // 获取要移除的组件的引用
        Container container = getParentContainerFromChild(oname);
        if (container instanceof Context) {
            ((Context) container).setLoader(null);
        }
    }


    /**
     * 删除现有的 Manager.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeManager(String name) throws Exception {

        ObjectName oname = new ObjectName(name);
        // Acquire a reference to the component to be removed
        Container container = getParentContainerFromChild(oname);
        if (container instanceof Context) {
            ((Context) container).setManager(null);
        }
    }


    /**
     * 删除现有的 Realm.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeRealm(String name) throws Exception {

        ObjectName oname = new ObjectName(name);
        // Acquire a reference to the component to be removed
        Container container = getParentContainerFromChild(oname);
        container.setRealm(null);
    }


    /**
     * 删除现有的 Service.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeService(String name) throws Exception {

        if (!(container instanceof Server)) {
            throw new Exception();
        }

        // Acquire a reference to the component to be removed
        ObjectName oname = new ObjectName(name);
        Service service = getService(oname);
        ((Server) container).removeService(service);
    }


    /**
     * 删除现有的 Valve.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果组件不能被移除
     */
    public void removeValve(String name) throws Exception {

        // Acquire a reference to the component to be removed
        ObjectName oname = new ObjectName(name);
        Container container = getParentContainerFromChild(oname);
        Valve[] valves = container.getPipeline().getValves();
        for (int i = 0; i < valves.length; i++) {
            ObjectName voname = ((JmxEnabled) valves[i]).getObjectName();
            if (voname.equals(oname)) {
                container.getPipeline().removeValve(valves[i]);
            }
        }
    }
}
