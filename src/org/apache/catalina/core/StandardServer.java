package org.apache.catalina.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.util.Random;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.mbeans.MBeanFactory;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.util.ExtensionValidator;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringCache;
import org.apache.tomcat.util.res.StringManager;


/**
 * <b>Server</b>接口的标准实现类, 在部署和启动Catalina时，可用(不是必须的).
 */
public final class StandardServer extends LifecycleMBeanBase implements Server {

    private static final Log log = LogFactory.getLog(StandardServer.class);


    // ------------------------------------------------------------ Constructor


    public StandardServer() {
        super();

        globalNamingResources = new NamingResourcesImpl();
        globalNamingResources.setContainer(this);

        if (isUseNaming()) {
            namingContextListener = new NamingContextListener();
            addLifecycleListener(namingContextListener);
        } else {
            namingContextListener = null;
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 全局命名资源上下文.
     */
    private javax.naming.Context globalNamingContext = null;


    /**
     * 全局命名资源.
     */
    private NamingResourcesImpl globalNamingResources = null;


    /**
     * 命名上下文监听器.
     */
    private final NamingContextListener namingContextListener;


    /**
     * 等待关机命令的端口号.
     */
    private int port = 8005;

    /**
     * 等待关闭命令的地址.
     */
    private String address = "localhost";


    /**
     * 随机数发生器,只有在关闭命令字符串长于1024个字符时，才会使用.
     */
    private Random random = null;


    /**
     * 这个Server关联的Services.
     */
    private Service services[] = new Service[0];
    private final Object servicesLock = new Object();


    /**
     * 正在等待的关闭命令字符串.
     */
    private String shutdown = "SHUTDOWN";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 属性修改支持.
     */
    final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private volatile boolean stopAwait = false;

    private Catalina catalina = null;

    private ClassLoader parentClassLoader = null;

    /**
     * 当前在await()方法中的线程.
     */
    private volatile Thread awaitThread = null;

    /**
     * 用于等待关闭命令的服务器套接字.
     */
    private volatile ServerSocket awaitSocket = null;

    private File catalinaHome = null;

    private File catalinaBase = null;

    private final Object namingToken = new Object();


    // ------------------------------------------------------------- Properties

    @Override
    public Object getNamingToken() {
        return namingToken;
    }


    /**
     * 返回全局命名资源上下文.
     */
    @Override
    public javax.naming.Context getGlobalNamingContext() {
        return (this.globalNamingContext);
    }


    /**
     * 设置全局命名资源上下文.
     *
     * @param globalNamingContext 全局命名资源上下文
     */
    public void setGlobalNamingContext(javax.naming.Context globalNamingContext) {
        this.globalNamingContext = globalNamingContext;
    }


    /**
     * 返回全局命名资源.
     */
    @Override
    public NamingResourcesImpl getGlobalNamingResources() {
        return (this.globalNamingResources);
    }


    /**
     * 设置全局命名资源.
     *
     * @param globalNamingResources 全局命名资源
     */
    @Override
    public void setGlobalNamingResources
        (NamingResourcesImpl globalNamingResources) {

        NamingResourcesImpl oldGlobalNamingResources =
            this.globalNamingResources;
        this.globalNamingResources = globalNamingResources;
        this.globalNamingResources.setContainer(this);
        support.firePropertyChange("globalNamingResources",
                                   oldGlobalNamingResources,
                                   this.globalNamingResources);

    }


    /**
     * 当前Tomcat服务器发布号
     */
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }


    /**
     * 返回当前服务器生成的时间戳
     */
    public String getServerBuilt() {
        return ServerInfo.getServerBuilt();
    }


    /**
     * 返回当前服务器的版本号.
     */
    public String getServerNumber() {
        return ServerInfo.getServerNumber();
    }


    /**
     * 返回等待关机命令的端口号.
     */
    @Override
    public int getPort() {
        return (this.port);
    }


    /**
     * 设置等待关机命令的端口号.
     *
     * @param port 端口号
     */
    @Override
    public void setPort(int port) {
        this.port = port;
    }


    /**
     * 返回等待关闭命令的地址.
     */
    @Override
    public String getAddress() {
        return (this.address);
    }


    /**
     * 设置等待关闭命令的地址.
     *
     * @param address The new address
     */
    @Override
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * 返回正在等待的关闭命令字符串.
     */
    @Override
    public String getShutdown() {
        return (this.shutdown);
    }


    /**
     * 设置正在等待的关闭命令字符串.
     *
     * @param shutdown 关闭命令
     */
    @Override
    public void setShutdown(String shutdown) {
        this.shutdown = shutdown;
    }


    /**
     * 返回外部Catalina 启动/关闭组件.
     */
    @Override
    public Catalina getCatalina() {
        return catalina;
    }


    /**
     * 设置外部Catalina 启动/关闭组件.
     */
    @Override
    public void setCatalina(Catalina catalina) {
        this.catalina = catalina;
    }

    // --------------------------------------------------------- Server Methods


    /**
     * 添加一个新的Service到定义的Service集合.
     *
     * @param service The Service to be added
     */
    @Override
    public void addService(Service service) {

        service.setServer(this);

        synchronized (servicesLock) {
            Service results[] = new Service[services.length + 1];
            System.arraycopy(services, 0, results, 0, services.length);
            results[services.length] = service;
            services = results;

            if (getState().isAvailable()) {
                try {
                    service.start();
                } catch (LifecycleException e) {
                    // Ignore
                }
            }

            // Report this property change to interested listeners
            support.firePropertyChange("service", null, service);
        }

    }

    public void stopAwait() {
        stopAwait=true;
        Thread t = awaitThread;
        if (t != null) {
            ServerSocket s = awaitSocket;
            if (s != null) {
                awaitSocket = null;
                try {
                    s.close();
                } catch (IOException e) {
                    // Ignored
                }
            }
            t.interrupt();
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
    }

    /**
     * 等待接收到正确的关机命令，然后返回.
     * 这使主线程保持活跃 - 监听HTTP连接的线程池是守护线程.
     */
    @Override
    public void await() {
        // 负值 - 不要等待端口 - Tomcat是嵌入的
        if( port == -2 ) {
            // undocumented yet - 为了嵌入周围的应用程序.
            return;
        }
        if( port==-1 ) {
            try {
                awaitThread = Thread.currentThread();
                while(!stopAwait) {
                    try {
                        Thread.sleep( 10000 );
                    } catch( InterruptedException ex ) {
                        // continue and check the flag
                    }
                }
            } finally {
                awaitThread = null;
            }
            return;
        }

        // 设置等待的服务器套接字
        try {
            awaitSocket = new ServerSocket(port, 1,
                    InetAddress.getByName(address));
        } catch (IOException e) {
            log.error("StandardServer.await: create[" + address
                               + ":" + port
                               + "]: ", e);
            return;
        }

        try {
            awaitThread = Thread.currentThread();

            // 等待连接和有效命令的循环
            while (!stopAwait) {
                ServerSocket serverSocket = awaitSocket;
                if (serverSocket == null) {
                    break;
                }

                // 等待下一个连接
                Socket socket = null;
                StringBuilder command = new StringBuilder();
                try {
                    InputStream stream;
                    long acceptStartTime = System.currentTimeMillis();
                    try {
                        socket = serverSocket.accept();
                        socket.setSoTimeout(10 * 1000);  // Ten seconds
                        stream = socket.getInputStream();
                    } catch (SocketTimeoutException ste) {
                        // This should never happen but bug 56684 suggests that
                        // it does.
                        log.warn(sm.getString("standardServer.accept.timeout",
                                Long.valueOf(System.currentTimeMillis() - acceptStartTime)), ste);
                        continue;
                    } catch (AccessControlException ace) {
                        log.warn("StandardServer.accept security exception: "
                                + ace.getMessage(), ace);
                        continue;
                    } catch (IOException e) {
                        if (stopAwait) {
                            // Wait was aborted with socket.close()
                            break;
                        }
                        log.error("StandardServer.await: accept: ", e);
                        break;
                    }

                    // 从套接字中读取一组字符
                    int expected = 1024; // 断开以避免DoS攻击
                    while (expected < shutdown.length()) {
                        if (random == null)
                            random = new Random();
                        expected += (random.nextInt() % 1024);
                    }
                    while (expected > 0) {
                        int ch = -1;
                        try {
                            ch = stream.read();
                        } catch (IOException e) {
                            log.warn("StandardServer.await: read: ", e);
                            ch = -1;
                        }
                        // 控制字符或EOF (-1) 终止循环
                        if (ch < 32 || ch == 127) {
                            break;
                        }
                        command.append((char) ch);
                        expected--;
                    }
                } finally {
                    // 关闭套接字，因为已经完成了
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // 与命令字符串匹配
                boolean match = command.toString().equals(shutdown);
                if (match) {
                    log.info(sm.getString("standardServer.shutdownViaPort"));
                    break;
                } else
                    log.warn("StandardServer.await: Invalid command '"
                            + command.toString() + "' received");
            }
        } finally {
            ServerSocket serverSocket = awaitSocket;
            awaitThread = null;
            awaitSocket = null;

            // 关闭服务器套接字并返回
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }


    /**
     * @return 指定的Service; 或者<code>null</code>.
     *
     * @param name 返回的Service的名称
     */
    @Override
    public Service findService(String name) {

        if (name == null) {
            return (null);
        }
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                if (name.equals(services[i].getName())) {
                    return (services[i]);
                }
            }
        }
        return (null);
    }


    /**
     * @return 这个Server中定义的所有Service.
     */
    @Override
    public Service[] findServices() {
        return services;
    }

    /**
     * @return JMX 服务名称.
     */
    public ObjectName[] getServiceNames() {
        ObjectName onames[]=new ObjectName[ services.length ];
        for( int i=0; i<services.length; i++ ) {
            onames[i]=((StandardService)services[i]).getObjectName();
        }
        return onames;
    }


    /**
     * 移除指定的Service.
     *
     * @param service The Service to be removed
     */
    @Override
    public void removeService(Service service) {

        synchronized (servicesLock) {
            int j = -1;
            for (int i = 0; i < services.length; i++) {
                if (service == services[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            try {
                services[j].stop();
            } catch (LifecycleException e) {
                // Ignore
            }
            int k = 0;
            Service results[] = new Service[services.length - 1];
            for (int i = 0; i < services.length; i++) {
                if (i != j)
                    results[k++] = services[i];
            }
            services = results;

            // Report this property change to interested listeners
            support.firePropertyChange("service", service, null);
        }

    }


    @Override
    public File getCatalinaBase() {
        if (catalinaBase != null) {
            return catalinaBase;
        }

        catalinaBase = getCatalinaHome();
        return catalinaBase;
    }


    @Override
    public void setCatalinaBase(File catalinaBase) {
        this.catalinaBase = catalinaBase;
    }


    @Override
    public File getCatalinaHome() {
        return catalinaHome;
    }


    @Override
    public void setCatalinaHome(File catalinaHome) {
        this.catalinaHome = catalinaHome;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 添加属性更改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 移除属性更改监听器.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StandardServer[");
        sb.append(getPort());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * 将这个<code>Server</code>的配置信息写入server.xml配置文件.
     *
     * @exception javax.management.InstanceNotFoundException 如果找不到托管资源对象
     * @exception javax.management.MBeanException 如果对象的初始化器抛出异常，则不支持持久性
     * @exception javax.management.RuntimeOperationsException 如果持久性机制报告异常
     */
    public synchronized void storeConfig() throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "storeConfig", null, null);
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(t);
        }
    }


    /**
     * 将这个<code>Context</code>的配置信息写入指定的配置文件.
     *
     * @param context 应保存其配置的上下文
     * 
     * @exception javax.management.InstanceNotFoundException 如果找不到托管资源对象
     * @exception javax.management.MBeanException 如果对象的初始化器抛出异常，则不支持持久性
     * @exception javax.management.RuntimeOperationsException 如果持久性机制报告异常
     */
    public synchronized void storeContext(Context context) throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "store",
                    new Object[] {context},
                    new String [] { "java.lang.String"});
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(t);
        }
    }


    /**
     * @return <code>true</code>如果使用命名.
     */
    private boolean isUseNaming() {
        boolean useNaming = true;
        // Reading the "catalina.useNaming" environment variable
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }
        return useNaming;
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {

        fireLifecycleEvent(CONFIGURE_START_EVENT, null);
        setState(LifecycleState.STARTING);

        globalNamingResources.start();

        // 启动定义的Service
        synchronized (servicesLock) {
            for (int i = 0; i < services.length; i++) {
                services[i].start();
            }
        }
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);

        // Stop our defined Services
        for (int i = 0; i < services.length; i++) {
            services[i].stop();
        }

        globalNamingResources.stop();

        stopAwait();
    }

    /**
     * 调用预启动初始化. 这用于允许连接器在UNIX操作环境下绑定到受限端口.
     */
    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        // 注册全局String 缓存
        // 注意，虽然缓存是全局的, 如果JVM中存在多个Server(嵌入时可能发生) 然后，相同的缓存将以多个名称注册
        onameStringCache = register(new StringCache(), "type=StringCache");

        // 注册 MBeanFactory
        MBeanFactory factory = new MBeanFactory();
        factory.setContainer(this);
        onameMBeanFactory = register(factory, "type=MBeanFactory");

        // 注册命名资源
        globalNamingResources.init();

        // 使用通用和共享类加载器中的JAR填充扩展验证器
        if (getCatalina() != null) {
            ClassLoader cl = getCatalina().getParentClassLoader();
            // 遍历类加载器层次结构. 在系统类加载器上停止.
            // 这将添加共享和公共的类加载器
            while (cl != null && cl != ClassLoader.getSystemClassLoader()) {
                if (cl instanceof URLClassLoader) {
                    URL[] urls = ((URLClassLoader) cl).getURLs();
                    for (URL url : urls) {
                        if (url.getProtocol().equals("file")) {
                            try {
                                File f = new File (url.toURI());
                                if (f.isFile() &&
                                        f.getName().endsWith(".jar")) {
                                    ExtensionValidator.addSystemResource(f);
                                }
                            } catch (URISyntaxException e) {
                                // Ignore
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                }
                cl = cl.getParent();
            }
        }
        // 初始化定义的Services
        for (int i = 0; i < services.length; i++) {
            services[i].init();
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // 销毁定义的Services
        for (int i = 0; i < services.length; i++) {
            services[i].destroy();
        }

        globalNamingResources.destroy();

        unregister(onameMBeanFactory);

        unregister(onameStringCache);

        super.destroyInternal();
    }

    /**
     * 返回父类加载器.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (catalina != null) {
            return (catalina.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }

    /**
     * 设置父类加载器.
     *
     * @param parent 父类加载器
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);
    }


    private ObjectName onameStringCache;
    private ObjectName onameMBeanFactory;

    /**
     * 获取此服务器的MBean域名. 使用以下搜索顺序获得域名:
     * <ol>
     * <li>第一个{@link org.apache.catalina.Engine}的名称.</li>
     * <li>第一个{@link Service}的名称.</li>
     * </ol>
     */
    @Override
    protected String getDomainInternal() {

        String domain = null;

        Service[] services = findServices();
        if (services.length > 0) {
            Service service = services[0];
            if (service != null) {
                domain = service.getDomain();
            }
        }
        return domain;
    }


    @Override
    protected final String getObjectNameKeyProperties() {
        return "type=Server";
    }

}
