package org.apache.catalina.startup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.NonLoginAuthenticator;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.NamingContextListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.descriptor.web.LoginConfig;

// TODO: 临时文件夹的懒加载 - 只有当JSP被编译或获取临时文件夹时，需要创建它. 避免需要baseDir

// TODO: 允许没有base dir的上下文 - 即只有程序. 将禁用默认的 servlet.

/**
 * 用于嵌入/单元测试的最小Tomcat启动程序.
 *
 * <p>
 * Tomcat支持多种形式的配置和启动 - 最常见和最稳定是基于 server.xml, 在 org.apache.catalina.startup.Bootstrap 中实现.
 *
 * <p>
 * 这个类用于嵌入Tomcat的应用.
 *
 * <p>
 * 要求:
 * <ul>
 *   <li>所有的 tomcat类和可能的servlet在 classpath中. (例如所有的都在一个大的 jar中, 或在一个eclipse CP中, 或在其它任何组合中)</li>
 *
 *   <li>需要一个临时目录用于工作文件</li>
 *
 *   <li>不需要配置文件. 这个类提供使用的方法，如果应用程序有一个 web.xml 文件, 但它是可选的 - 可以使用自己的servlet.</li>
 * </ul>
 *
 * <p>
 * 'add'方法中有一个变量配置servlet和应用. 这些方法, 默认情况下, 创建一个简单的在内存中的安全 realm 并应用它.
 * 如果需要更加复杂的安全处理, 可以定义这个类的子类.
 *
 * <p>
 * 这个类提供一组方便的方法来配置应用上下文, 都要调用<code>addWebapp</code>. 这些方法创建一个应用上下文, 配置它, 稍后将其添加到{@link Host}.
 * 它们不使用全局默认的 web.xml; 但是, 它们添加一个生命周期监听器监听标准的 DefaultServlet, JSP 处理, 和欢迎文件.
 *
 * <p>
 * 在复杂的情况下, 可能更倾向于使用普通的 Tomcat API 来创建Web应用上下文; 例如, 你可能需要安装自定义的 Loader,
 * 在调用 {@link Host#addChild(Container)}之前.
 * 为了重写<code>addWebapp</code>方法的基础行为, 你可能想调用这个类的两个方法: {@link #noDefaultWebXmlPath()}和{@link #getDefaultWebXmlListener()}.
 *
 * <p>
 * {@link #getDefaultWebXmlListener()} 返回一个添加标准DefaultServlet, JSP 处理, 欢迎文件的 {@link LifecycleListener}.
 * 如果你添加了这个监听器, 必须阻止Tomcat 应用任何标准的全局web.xml ...
 *
 * <p>
 * {@link #noDefaultWebXmlPath()} 返回虚拟路径名来配置，阻止{@link ContextConfig}应用全局的 web.xml 文件.
 */
public class Tomcat {
    // 一些日志实现类使用logger弱引用， 因此日志配置可能丢失，如果 GC 在Logger配置完成但还没有使用时运行.
    // 这个Map的目的是保留对显式配置的logger的强引用，因此配置不会丢失.
    private final Map<String, Logger> pinnedLoggers = new HashMap<>();

    protected Server server;

    protected int port = 8080;
    protected String hostname = "localhost";
    protected String basedir;
    protected boolean defaultConnectorCreated = false;

    private final Map<String, String> userPass = new HashMap<>();
    private final Map<String, List<String>> userRoles = new HashMap<>();
    private final Map<String, Principal> userPrincipals = new HashMap<>();

    public Tomcat() {
        ExceptionUtils.preload();
    }

    /**
     * Tomcat需要临时文件的目录. 这个应该第一个调用.
     *
     * <p>
     * 默认情况下, 如果这个方法没有调用, 我们使用:
     * <ul>
     *  <li>system properties - catalina.base, catalina.home</li>
     *  <li>[user.dir]/tomcat.$PORT</li>
     * </ul>
     * (/tmp 是不安全的).
     *
     * <p>
     * TODO: 禁用work dir, 如果不需要( no jsp, etc ).
     *
     * @param basedir Tomcat基础目录
     */
    public void setBaseDir(String basedir) {
        this.basedir = basedir;
    }

    /**
     * 设置默认连接器的端口. 必须在start()之前调用.
     * 
     * @param port 端口号
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 默认主机的主机名, 默认是'localhost'.
     * 
     * @param s 默认主机名
     */
    public void setHostname(String s) {
        hostname = s;
    }

    /**
     * 这相当于将Web应用程序添加到Tomcat的webapps目录中.
     * 等效的默认web.xml 将被添加到Web应用, 而且应用中任何打包的WEB-INF/web.xml 和 META-INF/context.xml 将正常处理.
     * 正常的网页片段和{@link javax.servlet.ServletContainerInitializer}处理将被应用.
     *
     * @param contextPath 使用的上下文, ""为根上下文.
     * @param docBase 上下文的基础目录, 静态文件. 必须存在, 相对于服务器 home
     * 
     * @return 部署的上下文
     * @throws ServletException 如果发生部署错误
     */
    public Context addWebapp(String contextPath, String docBase) throws ServletException {
        return addWebapp(getHost(), contextPath, docBase);
    }


    /**
     * 添加一个上下文 - 程序模式, 没有默认的 web.xml 使用.
     * 意味着不支持JSP (没有 JSP servlet), 没有默认的servlet, 而且不支持web socket, 除非通过程序接口显式的启用.
     * 同样也没有{@link javax.servlet.ServletContainerInitializer} 处理和注解处理.
     * 如果一个程序添加了一个{@link javax.servlet.ServletContainerInitializer}, 不会扫描匹配
     * {@link javax.servlet.annotation.HandlesTypes}.
     *
     * <p>
     * API调用等效于 web.xml:
     *
     * <pre>{@code
     *  // context-param
     *  ctx.addParameter("name", "value");
     *
     *
     *  // error-page
     *  ErrorPage ep = new ErrorPage();
     *  ep.setErrorCode(500);
     *  ep.setLocation("/error.html");
     *  ctx.addErrorPage(ep);
     *
     *  ctx.addMimeMapping("ext", "type");
     * }</pre>
     *
     *
     * <p>
     * Note: 如果重新加载Context, 所有的配置将丢失. 如果需要支持重新加载, 可以使用一个LifecycleListener 来提供配置.
     *
     * <p>
     * TODO: add the rest
     *
     * @param contextPath 要使用的上下文映射, ""作为根上下文.
     * @param docBase 上下文的基础目录, 对于静态文件. 必须存在, 相对于服务器 home
     * @return 不熟的上下文
     */
    public Context addContext(String contextPath, String docBase) {
        return addContext(getHost(), contextPath, docBase);
    }

    /**
     * 等效于 &lt;servlet&gt;&lt;servlet-name&gt;&lt;servlet-class&gt;.
     *
     * <p>
     * 通常它比使用Servlet作为参数更好更快 - 可以使用这个，如果 servlet不常用, 并且希望避免加载所有deps.
     * ( for example: jsp servlet )
     *
     * 可以自定义返回的servlet, ex:
     *  <pre>
     *    wrapper.addInitParameter("name", "value");
     *  </pre>
     *
     * @param contextPath   要添加Servlet的Context
     * @param servletName   Servlet名称(用于映射)
     * @param servletClass  Servlet要使用的类
     * 
     * @return servlet的包装器
     */
    public Wrapper addServlet(String contextPath,
            String servletName,
            String servletClass) {
        Container ctx = getHost().findChild(contextPath);
        return addServlet((Context) ctx, servletName, servletClass);
    }

    /**
     * {@link #addServlet(String, String, String)}的静态版本
     * 
     * @param ctx           要添加Servlet的Context
     * @param servletName   Servlet名称(用于映射)
     * @param servletClass  Servlet要使用的类
     * 
     * @return The wrapper for the servlet
     */
    public static Wrapper addServlet(Context ctx,
                                      String servletName,
                                      String servletClass) {
        // will do class for name and set init params
        Wrapper sw = ctx.createWrapper();
        sw.setServletClass(servletClass);
        sw.setName(servletName);
        ctx.addChild(sw);

        return sw;
    }

    /**
     * 添加一个现有的Servlet到上下文， 不使用 class.forName或初始化.
     * @param contextPath   要添加Servlet的Context
     * @param servletName   Servlet名称(用于映射)
     * @param servlet       要添加的Servlet
     * @return The wrapper for the servlet
     */
    public Wrapper addServlet(String contextPath,
            String servletName,
            Servlet servlet) {
        Container ctx = getHost().findChild(contextPath);
        return addServlet((Context) ctx, servletName, servlet);
    }

    /**
     * {@link #addServlet(String, String, Servlet)}的静态版本.
     * 
     * @param ctx           要添加Servlet的Context
     * @param servletName   Servlet名称(用于映射)
     * @param servlet       要添加的Servlet
     * @return The wrapper for the servlet
     */
    public static Wrapper addServlet(Context ctx,
                                      String servletName,
                                      Servlet servlet) {
        // will do class for name and set init params
        Wrapper sw = new ExistingStandardWrapper(servlet);
        sw.setName(servletName);
        ctx.addChild(sw);

        return sw;
    }


    /**
     * 初始化服务器.
     *
     * @throws LifecycleException Init error
     */
    public void init() throws LifecycleException {
        getServer();
        getConnector();
        server.init();
    }


    /**
     * 启动服务器.
     *
     * @throws LifecycleException Start error
     */
    public void start() throws LifecycleException {
        getServer();
        getConnector();
        server.start();
    }

    /**
     * 停止服务器.
     *
     * @throws LifecycleException Stop error
     */
    public void stop() throws LifecycleException {
        getServer();
        server.stop();
    }


    /**
     * 销毁服务器. 一旦这个方法被调用，这个对象不能再被使用.
     *
     * @throws LifecycleException Destroy error
     */
    public void destroy() throws LifecycleException {
        getServer();
        server.destroy();
        // Could null out objects here
    }

    /**
     * 添加一个用户到内存中的 realm. 所有创建的应用默认使用这个, 可以使用 setRealm()替换.
     * 
     * @param user The user name
     * @param pass The password
     */
    public void addUser(String user, String pass) {
        userPass.put(user, pass);
    }

    /**
     * 给用户添加一个角色.
     * 
     * @param user The user name
     * @param role The role name
     */
    public void addRole(String user, String role) {
        List<String> roles = userRoles.get(user);
        if (roles == null) {
            roles = new ArrayList<>();
            userRoles.put(user, roles);
        }
        roles.add(role);
    }

    // ------- Extra customization -------
    // You can tune individual tomcat objects, using internal APIs

    /**
     * 获取默认的 http 连接器. 可以设置更多参数 - 端口已经初始化.
     *
     * <p>
     * 可以代替的是, 可以构造一个 Connector 并设置参数, 然后调用 addConnector(Connector)
     *
     * @return 可定制的连接器对象
     */
    public Connector getConnector() {
        Service service = getService();
        if (service.findConnectors().length > 0) {
            return service.findConnectors()[0];
        }

        if (defaultConnectorCreated) {
            return null;
        }
        // 和标准的Tomcat 配置一样.
        // 它创建一个APR HTTP 连接器，如果 AprLifecycleListener已经配置(created), 而且 Tomcat Native library可用.
        // 否则它创建一个NIO HTTP 连接器.
        Connector connector = new Connector("HTTP/1.1");
        connector.setPort(port);
        service.addConnector(connector);
        defaultConnectorCreated = true;
        return connector;
    }

    public void setConnector(Connector connector) {
        defaultConnectorCreated = true;
        Service service = getService();
        boolean found = false;
        for (Connector serviceConnector : service.findConnectors()) {
            if (connector == serviceConnector) {
                found = true;
            }
        }
        if (!found) {
            service.addConnector(connector);
        }
    }

    /**
     * 获取service 对象. 可以用于添加更多的连接器和少量的其它设置.
     * @return The service
     */
    public Service getService() {
        return getServer().findServices()[0];
    }

    /**
     * 设置当前主机 - 所有未来的应用将被添加到这个主机. 当tomcat 启动时, 主机将会是默认的主机.
     *
     * @param host The current host
     */
    public void setHost(Host host) {
        Engine engine = getEngine();
        boolean found = false;
        for (Container engineHost : engine.findChildren()) {
            if (engineHost == host) {
                found = true;
            }
        }
        if (!found) {
            engine.addChild(host);
        }
    }

    public Host getHost() {
        Engine engine = getEngine();
        if (engine.findChildren().length > 0) {
            return (Host) engine.findChildren()[0];
        }

        Host host = new StandardHost();
        host.setName(hostname);
        getEngine().addChild(host);
        return host;
    }

    /**
     * 访问 engine, 用于更进一步的自定义.
     * @return The engine
     */
    public Engine getEngine() {
        Service service = getServer().findServices()[0];
        if (service.getContainer() != null) {
            return service.getContainer();
        }
        Engine engine = new StandardEngine();
        engine.setName( "Tomcat" );
        engine.setDefaultHost(hostname);
        engine.setRealm(createDefaultRealm());
        service.setContainer(engine);
        return engine;
    }

    /**
     * 获取server 对象. 可以添加监听器和少量的其它配置. 默认禁用JNDI.
     * @return The Server
     */
    public Server getServer() {

        if (server != null) {
            return server;
        }

        System.setProperty("catalina.useNaming", "false");

        server = new StandardServer();

        initBaseDir();

        server.setPort( -1 );

        Service service = new StandardService();
        service.setName("Tomcat");
        server.addService(service);
        return server;
    }

    /**
     * @param host 将部署上下文的主机
     * @param contextPath 要使用的上下文, "" 作为根上下文.
     * @param dir 上下文的基础目录, 作为静态文件. 必须存在, 相对于服务器 home
     * @return the deployed context
     */
    public Context addContext(Host host, String contextPath, String dir) {
        return addContext(host, contextPath, contextPath, dir);
    }

    /**
     * @param host 将部署上下文的主机
     * @param contextPath 要使用的上下文, "" 作为根上下文.
     * @param contextName 上下文名称
     * @param dir 上下文的基础目录, 作为静态文件. 必须存在, 相对于服务器 home
     * @return the deployed context
     */
    public Context addContext(Host host, String contextPath, String contextName,
            String dir) {
        silence(host, contextName);
        Context ctx = createContext(host, contextPath);
        ctx.setName(contextName);
        ctx.setPath(contextPath);
        ctx.setDocBase(dir);
        ctx.addLifecycleListener(new FixContextListener());

        if (host == null) {
            getHost().addChild(ctx);
        } else {
            host.addChild(ctx);
        }
        return ctx;
    }

    /**
     * @param host 将部署上下文的主机
     * @param contextPath 要使用的上下文, "" 作为根上下文.
     * @param docBase 上下文的基础目录, 作为静态文件. 必须存在, 相对于服务器 home
     * @return the deployed context
     */
    public Context addWebapp(Host host, String contextPath, String docBase) {
        LifecycleListener listener = null;
        try {
            Class<?> clazz = Class.forName(getHost().getConfigClass());
            listener = (LifecycleListener) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            // Wrap in IAE since we can't easily change the method signature to
            // to throw the specific checked exceptions
            throw new IllegalArgumentException(e);
        }

        return addWebapp(host,  contextPath, docBase, listener);
    }

    /**
     * @param host 将部署上下文的主机
     * @param contextPath 要使用的上下文, "" 作为根上下文.
     * @param docBase 上下文的基础目录, 作为静态文件. 必须存在, 相对于服务器 home
     * @param config 自定义上下文配置帮助类
     * @return the deployed context
     *
     * @deprecated Use {@link
     *             #addWebapp(Host, String, String, LifecycleListener)} instead
     */
    @Deprecated
    public Context addWebapp(Host host, String contextPath, String docBase, ContextConfig config) {
        return addWebapp(host, contextPath, docBase, (LifecycleListener) config);
    }


    /**
     * @param host 将部署上下文的主机
     * @param contextPath 要使用的上下文, "" 作为根上下文.
     * @param docBase 上下文的基础目录, 作为静态文件. 必须存在, 相对于服务器 home
     * @param config 自定义上下文配置帮助类
     * @return the deployed context
     */
    public Context addWebapp(Host host, String contextPath, String docBase,
            LifecycleListener config) {

        silence(host, contextPath);

        Context ctx = createContext(host, contextPath);
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);
        ctx.addLifecycleListener(getDefaultWebXmlListener());
        ctx.setConfigFile(getWebappConfigFile(docBase, contextPath));

        ctx.addLifecycleListener(config);

        if (config instanceof ContextConfig) {
            // prevent it from looking ( if it finds one - it'll have dup error )
            ((ContextConfig) config).setDefaultWebXml(noDefaultWebXmlPath());
        }

        if (host == null) {
            getHost().addChild(ctx);
        } else {
            host.addChild(ctx);
        }

        return ctx;
    }

    /**
     * 返回一个监听器，用于提供所需JSP处理的配置项.
     * 来自标准的Tomcat 全局 web.xml. 传递这个到{@link Context#addLifecycleListener(LifecycleListener)}, 
     * 然后传递{@link #noDefaultWebXmlPath()}的结果到{@link ContextConfig#setDefaultWebXml(String)}.
     * 
     * @return 配置默认JSP处理的监听器对象.
     */
    public LifecycleListener getDefaultWebXmlListener() {
        return new DefaultWebXmlListener();
    }

    /**
     * @return 当使用{@link #getDefaultWebXmlListener()}时，传递到{@link ContextConfig#setDefaultWebXml(String)}的路径名.
     */
    public String noDefaultWebXmlPath() {
        return Constants.NoDefaultWebXml;
    }

    // ---------- Helper methods and classes -------------------

    /**
     * 创建一个内存中的realm.
     * 可以使用一个真正的realm替换掉它. 这里创建的Realm默认将被添加到 Engine, 并且可能在Engine级上替换, 或在Host、Context级上覆盖(每个正常的Tomcat行为).
     * @return a realm instance
     */
    protected Realm createDefaultRealm() {
        return new RealmBase() {
            @Override
            @Deprecated
            protected String getName() {
                return "Simple";
            }

            @Override
            protected String getPassword(String username) {
                return userPass.get(username);
            }

            @Override
            protected Principal getPrincipal(String username) {
                Principal p = userPrincipals.get(username);
                if (p == null) {
                    String pass = userPass.get(username);
                    if (pass != null) {
                        p = new GenericPrincipal(username, pass,
                                userRoles.get(username));
                        userPrincipals.put(username, p);
                    }
                }
                return p;
            }

        };
    }

    protected void initBaseDir() {
        String catalinaHome = System.getProperty(Globals.CATALINA_HOME_PROP);
        if (basedir == null) {
            basedir = System.getProperty(Globals.CATALINA_BASE_PROP);
        }
        if (basedir == null) {
            basedir = catalinaHome;
        }
        if (basedir == null) {
            // Create a temp dir.
            basedir = System.getProperty("user.dir") +
                "/tomcat." + port;
        }

        File baseFile = new File(basedir);
        baseFile.mkdirs();
        try {
            baseFile = baseFile.getCanonicalFile();
        } catch (IOException e) {
            baseFile = baseFile.getAbsoluteFile();
        }
        server.setCatalinaBase(baseFile);
        System.setProperty(Globals.CATALINA_BASE_PROP, baseFile.getPath());
        basedir = baseFile.getPath();

        if (catalinaHome == null) {
            server.setCatalinaHome(baseFile);
        } else {
            File homeFile = new File(catalinaHome);
            homeFile.mkdirs();
            try {
                homeFile = homeFile.getCanonicalFile();
            } catch (IOException e) {
                homeFile = homeFile.getAbsoluteFile();
            }
            server.setCatalinaHome(homeFile);
        }
        System.setProperty(Globals.CATALINA_HOME_PROP,
                server.getCatalinaHome().getPath());
    }

    static final String[] silences = new String[] {
        "org.apache.coyote.http11.Http11NioProtocol",
        "org.apache.catalina.core.StandardService",
        "org.apache.catalina.core.StandardEngine",
        "org.apache.catalina.startup.ContextConfig",
        "org.apache.catalina.core.ApplicationContext",
        "org.apache.catalina.core.AprLifecycleListener"
    };

    private boolean silent = false;

    /**
     * @param silent    <code>true</code>设置日志级别为 WARN. 阻止一般的启动信息被记录.
     *                  <code>false</code>设置日志级别为默认的 INFO.
     */
    public void setSilent(boolean silent) {
        this.silent = silent;
        for (String s : silences) {
            Logger logger = Logger.getLogger(s);
            pinnedLoggers.put(s, logger);
            if (silent) {
                logger.setLevel(Level.WARNING);
            } else {
                logger.setLevel(Level.INFO);
            }
        }
    }

    private void silence(Host host, String contextPath) {
        String loggerName = getLoggerName(host, contextPath);
        Logger logger = Logger.getLogger(loggerName);
        pinnedLoggers.put(loggerName, logger);
        if (silent) {
            logger.setLevel(Level.WARNING);
        } else {
            logger.setLevel(Level.INFO);
        }
    }


    /*
     * 本质上和{@link ContainerBase#logName()}使用相同的逻辑.
     */
    private String getLoggerName(Host host, String contextName) {
        if (host == null) {
            host = getHost();
        }
        StringBuilder loggerName = new StringBuilder();
        loggerName.append(ContainerBase.class.getName());
        loggerName.append(".[");
        // Engine name
        loggerName.append(host.getParent().getName());
        loggerName.append("].[");
        // Host name
        loggerName.append(host.getName());
        loggerName.append("].[");
        // Context name
        if (contextName == null || contextName.equals("")) {
            loggerName.append("/");
        } else if (contextName.startsWith("##")) {
            loggerName.append("/");
            loggerName.append(contextName);
        }
        loggerName.append(']');

        return loggerName.toString();
    }

    /**
     * 为给定的Host创建配置的 {@link Context}.
     * 使用{@link StandardHost#setContextClass(String)}配置的默认构造器将会使用.
     *
     * @param host 要创建{@link Context}的Host, 或<code>null</code>使用默认的host
     * @param url 要获取{@link Context}的Web应用的路径
     * @return 新创建的{@link Context}
     */
    private Context createContext(Host host, String url) {
        String contextClass = StandardContext.class.getName();
        if (host == null) {
            host = this.getHost();
        }
        if (host instanceof StandardHost) {
            contextClass = ((StandardHost) host).getContextClass();
        }
        try {
            return (Context) Class.forName(contextClass).getConstructor()
                    .newInstance();
        } catch (InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException
                | ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Can't instantiate context-class " + contextClass
                            + " for host " + host + " and url "
                            + url, e);
        }
    }

    /**
     * 启用JNDI 命名，默认是禁用的.
     * Server必须实现{@link Lifecycle}，为了使用 {@link NamingContextListener}.
     */
    public void enableNaming() {
        // 确保getServer()已经被调用，因为那里是命名禁用的地方
        getServer();
        server.addLifecycleListener(new NamingContextListener());

        System.setProperty("catalina.useNaming", "true");

        String value = "org.apache.naming";
        String oldValue =
            System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
        if (oldValue != null) {
            if (oldValue.contains(value)) {
                value = oldValue;
            } else {
                value = value + ":" + oldValue;
            }
        }
        System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);

        value = System.getProperty
            (javax.naming.Context.INITIAL_CONTEXT_FACTORY);
        if (value == null) {
            System.setProperty
                (javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                 "org.apache.naming.java.javaURLContextFactory");
        }
    }

    /**
     * 提供context的默认配置. 等效于默认的 web.xml.
     *
     *  TODO: 在 一般的 Tomcat中, 如果默认的web.xml未找到, 使用这个方法
     *
     * @param contextPath  要设置默认配置的context
     */
    public void initWebappDefaults(String contextPath) {
        Container ctx = getHost().findChild(contextPath);
        initWebappDefaults((Context) ctx);
    }

    /**
     * {@link #initWebappDefaults(String)}的静态版本
     * 
     * @param ctx   要设置默认配置的context
     */
    public static void initWebappDefaults(Context ctx) {
        // Default servlet
        Wrapper servlet = addServlet(
                ctx, "default", "org.apache.catalina.servlets.DefaultServlet");
        servlet.setLoadOnStartup(1);
        servlet.setOverridable(true);

        // JSP servlet (by class name - to avoid loading all deps)
        servlet = addServlet(
                ctx, "jsp", "org.apache.jasper.servlet.JspServlet");
        servlet.addInitParameter("fork", "false");
        servlet.setLoadOnStartup(3);
        servlet.setOverridable(true);

        // Servlet mappings
        ctx.addServletMappingDecoded("/", "default");
        ctx.addServletMappingDecoded("*.jsp", "jsp");
        ctx.addServletMappingDecoded("*.jspx", "jsp");

        // Sessions
        ctx.setSessionTimeout(30);

        // MIME mappings
        for (int i = 0; i < DEFAULT_MIME_MAPPINGS.length;) {
            ctx.addMimeMapping(DEFAULT_MIME_MAPPINGS[i++],
                    DEFAULT_MIME_MAPPINGS[i++]);
        }

        // Welcome files
        ctx.addWelcomeFile("index.html");
        ctx.addWelcomeFile("index.htm");
        ctx.addWelcomeFile("index.jsp");
    }


    /**
     * 修复启动序列 - 不使用web.xml时需要.
     *
     * <p>
     * context中的start()方法将设置 'configured' 为 false - 而且希望监听器将其设置为 true.
     */
    public static class FixContextListener implements LifecycleListener {

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            try {
                Context context = (Context) event.getLifecycle();
                if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
                    context.setConfigured(true);
                }
                // 需要LoginConfig 处理 @ServletSecurity 注解
                // annotations
                if (context.getLoginConfig() == null) {
                    context.setLoginConfig(
                            new LoginConfig("NONE", null, null, null));
                    context.getPipeline().addValve(new NonLoginAuthenticator());
                }
            } catch (ClassCastException e) {
                return;
            }
        }

    }


    /**
     * 修复重新加载 - 使用程序配置的时候需要.
     * 当重新加载一个context时, 任何程序的配置将丢失. 当context启动时，这个监听器设置等效于 conf/web.xml.
     */
    public static class DefaultWebXmlListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (Lifecycle.BEFORE_START_EVENT.equals(event.getType())) {
                initWebappDefaults((Context) event.getLifecycle());
            }
        }
    }


    /**
     * 包装现有的servlet的帮助类.
     * 这里禁用 servlet生命周期和正常的重新加载, 还可以减少开销, 并对servlet提供更直接的控制.
     */
    public static class ExistingStandardWrapper extends StandardWrapper {
        private final Servlet existing;

        @SuppressWarnings("deprecation")
        public ExistingStandardWrapper( Servlet existing ) {
            this.existing = existing;
            this.asyncSupported = hasAsync(existing);
        }

        private static boolean hasAsync(Servlet existing) {
            boolean result = false;
            Class<?> clazz = existing.getClass();
            WebServlet ws = clazz.getAnnotation(WebServlet.class);
            if (ws != null) {
                result = ws.asyncSupported();
            }
            return result;
        }

        @Override
        public synchronized Servlet loadServlet() throws ServletException {
            if (singleThreadModel) {
                Servlet instance;
                try {
                    instance = existing.getClass().getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new ServletException(e);
                }
                instance.init(facade);
                return instance;
            } else {
                if (!instanceInitialized) {
                    existing.init(facade);
                    instanceInitialized = true;
                }
                return existing;
            }
        }
        @Override
        public long getAvailable() {
            return 0;
        }
        @Override
        public boolean isUnavailable() {
            return false;
        }
        @Override
        public Servlet getServlet() {
            return existing;
        }
        @Override
        public String getServletClass() {
            return existing.getClass().getName();
        }
    }

    /**
     * TODO: properties资源是否更好一些? 或者只解析 /etc/mime.types ?
     * 需要这个，是因为不使用默认的 web.xml, 这个被编码的地方.
     */
    private static final String[] DEFAULT_MIME_MAPPINGS = {
        "abs", "audio/x-mpeg",
        "ai", "application/postscript",
        "aif", "audio/x-aiff",
        "aifc", "audio/x-aiff",
        "aiff", "audio/x-aiff",
        "aim", "application/x-aim",
        "art", "image/x-jg",
        "asf", "video/x-ms-asf",
        "asx", "video/x-ms-asf",
        "au", "audio/basic",
        "avi", "video/x-msvideo",
        "avx", "video/x-rad-screenplay",
        "bcpio", "application/x-bcpio",
        "bin", "application/octet-stream",
        "bmp", "image/bmp",
        "body", "text/html",
        "cdf", "application/x-cdf",
        "cer", "application/pkix-cert",
        "class", "application/java",
        "cpio", "application/x-cpio",
        "csh", "application/x-csh",
        "css", "text/css",
        "dib", "image/bmp",
        "doc", "application/msword",
        "dtd", "application/xml-dtd",
        "dv", "video/x-dv",
        "dvi", "application/x-dvi",
        "eps", "application/postscript",
        "etx", "text/x-setext",
        "exe", "application/octet-stream",
        "gif", "image/gif",
        "gtar", "application/x-gtar",
        "gz", "application/x-gzip",
        "hdf", "application/x-hdf",
        "hqx", "application/mac-binhex40",
        "htc", "text/x-component",
        "htm", "text/html",
        "html", "text/html",
        "ief", "image/ief",
        "jad", "text/vnd.sun.j2me.app-descriptor",
        "jar", "application/java-archive",
        "java", "text/x-java-source",
        "jnlp", "application/x-java-jnlp-file",
        "jpe", "image/jpeg",
        "jpeg", "image/jpeg",
        "jpg", "image/jpeg",
        "js", "application/javascript",
        "jsf", "text/plain",
        "jspf", "text/plain",
        "kar", "audio/midi",
        "latex", "application/x-latex",
        "m3u", "audio/x-mpegurl",
        "mac", "image/x-macpaint",
        "man", "text/troff",
        "mathml", "application/mathml+xml",
        "me", "text/troff",
        "mid", "audio/midi",
        "midi", "audio/midi",
        "mif", "application/x-mif",
        "mov", "video/quicktime",
        "movie", "video/x-sgi-movie",
        "mp1", "audio/mpeg",
        "mp2", "audio/mpeg",
        "mp3", "audio/mpeg",
        "mp4", "video/mp4",
        "mpa", "audio/mpeg",
        "mpe", "video/mpeg",
        "mpeg", "video/mpeg",
        "mpega", "audio/x-mpeg",
        "mpg", "video/mpeg",
        "mpv2", "video/mpeg2",
        "nc", "application/x-netcdf",
        "oda", "application/oda",
        "odb", "application/vnd.oasis.opendocument.database",
        "odc", "application/vnd.oasis.opendocument.chart",
        "odf", "application/vnd.oasis.opendocument.formula",
        "odg", "application/vnd.oasis.opendocument.graphics",
        "odi", "application/vnd.oasis.opendocument.image",
        "odm", "application/vnd.oasis.opendocument.text-master",
        "odp", "application/vnd.oasis.opendocument.presentation",
        "ods", "application/vnd.oasis.opendocument.spreadsheet",
        "odt", "application/vnd.oasis.opendocument.text",
        "otg", "application/vnd.oasis.opendocument.graphics-template",
        "oth", "application/vnd.oasis.opendocument.text-web",
        "otp", "application/vnd.oasis.opendocument.presentation-template",
        "ots", "application/vnd.oasis.opendocument.spreadsheet-template ",
        "ott", "application/vnd.oasis.opendocument.text-template",
        "ogx", "application/ogg",
        "ogv", "video/ogg",
        "oga", "audio/ogg",
        "ogg", "audio/ogg",
        "spx", "audio/ogg",
        "flac", "audio/flac",
        "anx", "application/annodex",
        "axa", "audio/annodex",
        "axv", "video/annodex",
        "xspf", "application/xspf+xml",
        "pbm", "image/x-portable-bitmap",
        "pct", "image/pict",
        "pdf", "application/pdf",
        "pgm", "image/x-portable-graymap",
        "pic", "image/pict",
        "pict", "image/pict",
        "pls", "audio/x-scpls",
        "png", "image/png",
        "pnm", "image/x-portable-anymap",
        "pnt", "image/x-macpaint",
        "ppm", "image/x-portable-pixmap",
        "ppt", "application/vnd.ms-powerpoint",
        "pps", "application/vnd.ms-powerpoint",
        "ps", "application/postscript",
        "psd", "image/vnd.adobe.photoshop",
        "qt", "video/quicktime",
        "qti", "image/x-quicktime",
        "qtif", "image/x-quicktime",
        "ras", "image/x-cmu-raster",
        "rdf", "application/rdf+xml",
        "rgb", "image/x-rgb",
        "rm", "application/vnd.rn-realmedia",
        "roff", "text/troff",
        "rtf", "application/rtf",
        "rtx", "text/richtext",
        "sh", "application/x-sh",
        "shar", "application/x-shar",
        /*"shtml", "text/x-server-parsed-html",*/
        "sit", "application/x-stuffit",
        "snd", "audio/basic",
        "src", "application/x-wais-source",
        "sv4cpio", "application/x-sv4cpio",
        "sv4crc", "application/x-sv4crc",
        "svg", "image/svg+xml",
        "svgz", "image/svg+xml",
        "swf", "application/x-shockwave-flash",
        "t", "text/troff",
        "tar", "application/x-tar",
        "tcl", "application/x-tcl",
        "tex", "application/x-tex",
        "texi", "application/x-texinfo",
        "texinfo", "application/x-texinfo",
        "tif", "image/tiff",
        "tiff", "image/tiff",
        "tr", "text/troff",
        "tsv", "text/tab-separated-values",
        "txt", "text/plain",
        "ulw", "audio/basic",
        "ustar", "application/x-ustar",
        "vxml", "application/voicexml+xml",
        "xbm", "image/x-xbitmap",
        "xht", "application/xhtml+xml",
        "xhtml", "application/xhtml+xml",
        "xls", "application/vnd.ms-excel",
        "xml", "application/xml",
        "xpm", "image/x-xpixmap",
        "xsl", "application/xml",
        "xslt", "application/xslt+xml",
        "xul", "application/vnd.mozilla.xul+xml",
        "xwd", "image/x-xwindowdump",
        "vsd", "application/vnd.visio",
        "wav", "audio/x-wav",
        "wbmp", "image/vnd.wap.wbmp",
        "wml", "text/vnd.wap.wml",
        "wmlc", "application/vnd.wap.wmlc",
        "wmls", "text/vnd.wap.wmlsc",
        "wmlscriptc", "application/vnd.wap.wmlscriptc",
        "wmv", "video/x-ms-wmv",
        "wrl", "model/vrml",
        "wspolicy", "application/wspolicy+xml",
        "Z", "application/x-compress",
        "z", "application/x-compress",
        "zip", "application/zip"
    };

    protected URL getWebappConfigFile(String path, String contextName) {
        File docBase = new File(path);
        if (docBase.isDirectory()) {
            return getWebappConfigFileFromDirectory(docBase, contextName);
        } else {
            return getWebappConfigFileFromJar(docBase, contextName);
        }
    }

    private URL getWebappConfigFileFromDirectory(File docBase, String contextName) {
        URL result = null;
        File webAppContextXml = new File(docBase, Constants.ApplicationContextXml);
        if (webAppContextXml.exists()) {
            try {
                result = webAppContextXml.toURI().toURL();
            } catch (MalformedURLException e) {
                Logger.getLogger(getLoggerName(getHost(), contextName)).log(Level.WARNING,
                        "Unable to determine web application context.xml " + docBase, e);
            }
        }
        return result;
    }

    private URL getWebappConfigFileFromJar(File docBase, String contextName) {
        URL result = null;
        try (JarFile jar = new JarFile(docBase)) {
            JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
            if (entry != null) {
                result = UriUtil.buildJarUrl(docBase, Constants.ApplicationContextXml);
            }
        } catch (IOException e) {
            Logger.getLogger(getLoggerName(getHost(), contextName)).log(Level.WARNING,
                    "Unable to determine web application context.xml " + docBase, e);
        }
        return result;
    }
}
