package org.apache.catalina.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.CredentialHandler;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.ThreadBindingListener;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.CharsetMapper;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.ExtensionValidator;
import org.apache.catalina.util.URLEncoder;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.ContextBindings;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.InstanceManagerBindings;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.descriptor.XmlIdentifiers;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.Injectable;
import org.apache.tomcat.util.descriptor.web.InjectionTarget;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.MessageDestination;
import org.apache.tomcat.util.descriptor.web.MessageDestinationRef;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.http.CookieProcessor;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.apache.tomcat.util.security.PrivilegedGetTccl;
import org.apache.tomcat.util.security.PrivilegedSetTccl;

/**
 * <b>Context</b>接口的标准实现类. 
 * 每个子容器必须是一个Wrapper实现类，用于处理指向特定servlet的请求.
 */
public class StandardContext extends ContainerBase
        implements Context, NotificationEmitter {

    private static final Log log = LogFactory.getLog(StandardContext.class);


    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardContext component with the default basic Valve.
     */
    public StandardContext() {

        super();
        pipeline.setBasic(new StandardContextValve());
        broadcaster = new NotificationBroadcasterSupport();
        // Set defaults
        if (!Globals.STRICT_SERVLET_COMPLIANCE) {
            // Strict servlet compliance requires all extension mapped servlets
            // to be checked against welcome files
            resourceOnlyServlets.add("jsp");
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 允许multipart/form-data请求被解析，即使目标servlet不指定 @MultipartConfig 或者有一个&lt;multipart-config&gt; 元素.
     */
    protected boolean allowCasualMultipartParsing = false;

    /**
     * 控制是否读取剩余请求数据, 即使请求违反数据大小约束.
     */
    private boolean swallowAbortedUploads = true;

    /**
     * 备用部署描述符名称.
     */
    private String altDDName = null;


    /**
     * Lifecycle provider.
     */
    private InstanceManager instanceManager = null;


    private boolean antiResourceLocking = false;


    /**
     * 为此应用程序配置的应用程序监听器类名称集, 按照它们在web.xml文件中的顺序.
     */
    private String applicationListeners[] = new String[0];

    private final Object applicationListenersLock = new Object();

    /**
     * 需要对ServletContext方法进行限制访问的应用程序监听器集合. See Servlet 3.1 section 4.4.
     */
    private final Set<Object> noPluggabilityListeners = new HashSet<>();

    /**
     * 实例化的应用程序事件监听器对象列表.
     * 请注意，SCI和其他代码可以使用可插入性API来在应用程序启动之前将监听器实例直接添加到此列表中.
     */
    private List<Object> applicationEventListenersList = new CopyOnWriteArrayList<>();


    /**
     * 实例化的应用程序生命周期监听器对象集合.
     * 请注意，SCI和其他代码可以使用可插入性API来在应用程序启动之前将监听器实例直接添加到此列表中.
     */
    private Object applicationLifecycleListenersObjects[] =
        new Object[0];


    /**
     * 这个Web应用的ServletContainerInitializer的有序集合.
     */
    private Map<ServletContainerInitializer,Set<Class<?>>> initializers =
        new LinkedHashMap<>();


    /**
     * 定义的应用程序参数集合.
     */
    private ApplicationParameter applicationParameters[] =
        new ApplicationParameter[0];

    private final Object applicationParametersLock = new Object();


    /**
     * 发送j2ee 通知的broadcaster. 
     */
    private NotificationBroadcasterSupport broadcaster = null;

    /**
     * 字符集映射器的区域设置.
     */
    private CharsetMapper charsetMapper = null;


    /**
     * 被创建的CharsetMapper类的类名.
     */
    private String charsetMapperClass =
      "org.apache.catalina.util.CharsetMapper";


    /**
     * 这个上下文XML描述符的 URL.
     */
    private URL configFile = null;


    /**
     * 这个Context的正确配置标志.
     */
    private boolean configured = false;


    /**
     * 此Web应用程序的安全约束.
     */
    private volatile SecurityConstraint constraints[] =
            new SecurityConstraint[0];

    private final Object constraintsLock = new Object();


    /**
     * ServletContext实现类.
     */
    protected ApplicationContext context = null;

    /**
     * 被提供给监听器的关联ServletContext的打包版本，它要求对ServletContext方法限制访问. See Servlet 3.1 section 4.4.
     */
    private NoPluggabilityServletContext noPluggabilityServletContext = null;


    /**
     * 应该尝试使用cookie进行会话ID通信吗?
     */
    private boolean cookies = true;


    /**
     * 是否允许<code>ServletContext.getContext()</code>方法访问服务器中其他Web应用程序的上下文?
     */
    private boolean crossContext = false;


    /**
     * 编码的路径.
     */
    private String encodedPath = null;


    /**
     * 此Web应用程序的未编码路径.
     */
    private String path = null;


    /**
     * 用于配置ClassLoader的"遵循标准委托模型"标志.
     */
    private boolean delegate = false;


    private boolean denyUncoveredHttpMethods;


    /**
     * 此Web应用程序的显示名称.
     */
    private String displayName = null;


    /**
     * 重写默认上下文XML位置.
     */
    private String defaultContextXml;


    /**
     * 重写默认Web XML位置.
     */
    private String defaultWebXml;


    /**
     * 此Web应用程序的可分发标志.
     */
    private boolean distributable = false;


    /**
     * 此Web应用程序的文档根目录.
     */
    private String docBase = null;


    /**
     * 此Web应用程序的异常页, Java异常的完全限定类名作为key.
     */
    private HashMap<String, ErrorPage> exceptionPages = new HashMap<>();


    /**
     * 初始化的过滤器配置（以及相关的过滤器实例）集合, 过滤器名作为key.
     */
    private HashMap<String, ApplicationFilterConfig> filterConfigs =
            new HashMap<>();


    /**
     * 此应用程序的过滤器集合, 过滤器名作为key.
     */
    private HashMap<String, FilterDef> filterDefs = new HashMap<>();


    /**
     * 此应用程序的过滤器映射集合, 按照它们在部署描述符中定义的顺序排序.
     * 通过{@link ServletContext}添加的映射可能在部署描述符中定义的顺序前面，也可能在后面.
     */
    private final ContextFilterMaps filterMaps = new ContextFilterMaps();

    /**
     * 是否忽略注解.
     */
    private boolean ignoreAnnotations = false;


    /**
     * 这个Container关联的Loader实现类.
     */
    private Loader loader = null;
    private final ReadWriteLock loaderLock = new ReentrantReadWriteLock();


    /**
     * 此Web应用程序的登录配置描述符.
     */
    private LoginConfig loginConfig = null;


    /**
     * 这个Container关联的Manager实现类.
     */
    protected Manager manager = null;
    private final ReadWriteLock managerLock = new ReentrantReadWriteLock();


    /**
     * Web应用程序的命名上下文监听器.
     */
    private NamingContextListener namingContextListener = null;


    /**
     * Web应用程序的命名资源.
     */
    private NamingResourcesImpl namingResources = null;

    /**
     * 此Web应用程序的消息目的地.
     */
    private HashMap<String, MessageDestination> messageDestinations =
        new HashMap<>();


    /**
     * Web应用的 MIME映射, 扩展名作为key.
     */
    private HashMap<String, String> mimeMappings = new HashMap<>();


    /**
     * 此Web应用程序的上下文初始化参数, 名称作为key.
     */
    private final ConcurrentMap<String, String> parameters = new ConcurrentHashMap<>();


    /**
     * 请求处理暂停标志(重载期间)
     */
    private volatile boolean paused = false;


    /**
     * Web应用程序部署描述符版本的DTD的公共标识符. 
     * 这是用来支持轻松验证规则在处理2.2版的web.xml文件.
     */
    private String publicId = null;


    /**
     * 应用的重新加载标志.
     */
    private boolean reloadable = false;


    /**
     * 是否解压 WAR.
     */
    private boolean unpackWAR = true;


    /**
     * 默认的{@link StandardHost#isCopyXML()}的Context等级覆盖标志.
     */
    private boolean copyXML = false;


    /**
     * 此Web应用程序的默认上下文覆盖标志.
     */
    private boolean override = false;


    /**
     * 此Web应用程序的原始文档根目录.
     */
    private String originalDocBase = null;


    /**
     * 此Web应用程序的特权标志.
     */
    private boolean privileged = false;


    /**
     * 下一个调用<code>addWelcomeFile()</code>方法导致任何已经存在的欢迎文件的替换? 
     * 这将在处理Web应用程序的部署描述符之前设置, 以便应用程序指定选择<strong>replace</strong>,而不是附加到全局描述符中定义的那些.
     */
    private boolean replaceWelcomeFiles = false;


    /**
     * 此应用程序的安全角色映射, 使用角色名称作为key(在应用程序中使用).
     */
    private HashMap<String, String> roleMappings = new HashMap<>();


    /**
     * 此应用程序的安全角色, 使用角色名称作为key.
     */
    private String securityRoles[] = new String[0];

    private final Object securityRolesLock = new Object();


    /**
     * 此Web应用程序的servlet映射, 使用匹配表达式作为key
     */
    private HashMap<String, String> servletMappings = new HashMap<>();

    private final Object servletMappingsLock = new Object();


    /**
     * 会话超时时间(in minutes).
     */
    private int sessionTimeout = 30;

    /**
     * 通知序列号.
     */
    private AtomicLong sequenceNumber = new AtomicLong(0);

    /**
     * 此Web应用程序的状态代码错误页, 使用HTTP状态码作为key(Integer类型).
     * 注意，状态码零用于默认错误页.
     */
    private HashMap<Integer, ErrorPage> statusPages = new HashMap<>();


    /**
     * 设置为true, system.out 和 system.err在执行servlet时重定向到记录器.
     */
    private boolean swallowOutput = false;


    /**
     * 容器将等待servlet卸载的毫秒数.
     */
    private long unloadDelay = 2000;


    /**
     * 此应用程序的监视资源.
     */
    private String watchedResources[] = new String[0];

    private final Object watchedResourcesLock = new Object();


    /**
     * 此应用程序的欢迎文件.
     */
    private String welcomeFiles[] = new String[0];

    private final Object welcomeFilesLock = new Object();


    /**
     * LifecycleListener类名集合, 将被添加到每个<code>createWrapper()</code>新创建的Wrapper.
     */
    private String wrapperLifecycles[] = new String[0];

    private final Object wrapperLifecyclesLock = new Object();

    /**
     * ContainerListener类名集合, 将被添加到每个<code>createWrapper()</code>新创建的Wrapper.
     */
    private String wrapperListeners[] = new String[0];

    private final Object wrapperListenersLock = new Object();

    /**
     * 此上下文的工作目录的路径名(相对于服务器的home，如果不是绝对的).
     */
    private String workDir = null;


    /**
     * 使用的Wrapper类实现类的Java类名.
     */
    private String wrapperClassName = StandardWrapper.class.getName();
    private Class<?> wrapperClass = null;


    /**
     * JNDI使用标志.
     */
    private boolean useNaming = true;


    /**
     * 关联的命名上下文的名称.
     */
    private String namingContextName = null;


    private WebResourceRoot resources;
    private final ReadWriteLock resourcesLock = new ReentrantReadWriteLock();

    private long startupTime;
    private long startTime;
    private long tldScanTime;

    /**
     * 引擎名称. 如果是null, 使用域名.
     */
    private String j2EEApplication="none";
    private String j2EEServer="none";


    /**
     * 用于打开/关闭XML验证web.xml和web-fragment.xml文件的属性值.
     */
    private boolean webXmlValidation = Globals.STRICT_SERVLET_COMPLIANCE;


    /**
     * 用于打开/关闭XML命名空间验证的属性值
     */
    private boolean webXmlNamespaceAware = Globals.STRICT_SERVLET_COMPLIANCE;


    /**
     * 用于打开/关闭使用外部实体的属性.
     */
    private boolean xmlBlockExternal = true;


    /**
     * 用于打开/关闭XML验证的属性值
     */
    private boolean tldValidation = Globals.STRICT_SERVLET_COMPLIANCE;


    /**
     * 用于会话cookie的名称. <code>null</code>指示名称由应用程序控制.
     */
    private String sessionCookieName;


    /**
     * 会话cookie是否应该使用 HttpOnly
     */
    private boolean useHttpOnly = true;


    /**
     * 用于会话cookie的域名. <code>null</code>指示域名由应用程序控制.
     */
    private String sessionCookieDomain;


    /**
     * 用于会话cookie的路径. <code>null</code>指示路径由应用程序控制.
     */
    private String sessionCookiePath;


    /**
     * 是否添加到会话cookie路径的末尾以确保浏览器, 特别是 IE, 不要让发送会话cookie 的上下文 /foo请求变成上下文 /foobar.
     */
    private boolean sessionCookiePathUsesTrailingSlash = false;


    /**
     * 用于搜索包含配置信息的Jar的Jar扫描器, 例如TLD 或 web-fragment.xml文件.
     */
    private JarScanner jarScanner = null;

    /**
     * 启用RMI目标内存泄漏检测控制.
     * 这是必要的因为检测只能工作在Java 9, 如果一些模块化检查被禁用.
     */
    private boolean clearReferencesRmiTargets = true;

    /**
     * 如果Tomcat试图终止由Web应用程序启动的线程?
     * 停止线程是通过弃用<code>Thread.stop()</code>方法的方式来执行的, 很可能导致不稳定.
     * 像这样的, 在开发环境中启用此选项应视为最后的选择，不建议在生产环境中使用. 如果未指定, 将使用默认值<code>false</code>.
     */
    private boolean clearReferencesStopThreads = false;

    /**
     * Tomcat是否应该尝试终止任何由Web应用程序启动的{@link java.util.TimerThread}?
     * 如果未指定, 将使用默认值<code>false</code>.
     */
    private boolean clearReferencesStopTimerThreads = false;

    /**
     * 如果一个 HttpClient keep-alive定时线程已经由Web应用启动，并且仍然在运行,
     * Tomcat是否应该将上下文类加载器从当前{@link ClassLoader}修改为{@link ClassLoader#getParent()}来防止内存泄漏?
     * 注意，keep-alive定时线程将自动停止，一旦keep-alives都到期, 在一个繁忙的系统中，一段时间内可能不会发生.
     */
    private boolean clearReferencesHttpClientKeepAliveThread = true;

    /**
     * 当应用程序停止时，Tomcat是否会更新线程池的线程，以避免由于未清理的ThreadLocal变量而导致内存泄漏.
     * 也需要StandardThreadExecutor 或 ThreadPoolExecutor的 threadRenewalDelay属性被设置为正值.
     */
    private boolean renewThreadsWhenStoppingContext = true;

    /**
     * 当上下文启动的时候，有效的web.xml是否应该被记录?
     */
    private boolean logEffectiveWebXml = false;

    private int effectiveMajorVersion = 3;

    private int effectiveMinorVersion = 0;

    private JspConfigDescriptor jspConfigDescriptor = null;

    private Set<String> resourceOnlyServlets = new HashSet<>();

    private String webappVersion = "";

    private boolean addWebinfClassesResources = false;

    private boolean fireRequestListenersOnForwards = false;

    /**
     * 为了跟踪目的，通过{@link ApplicationContext#createServlet(Class)}创建的Servlet.
     */
    private Set<Servlet> createdServlets = new HashSet<>();

    private boolean preemptiveAuthentication = false;

    private boolean sendRedirectBody = false;

    private boolean jndiExceptionOnFailedWrite = true;

    private Map<String, String> postConstructMethods = new HashMap<>();
    private Map<String, String> preDestroyMethods = new HashMap<>();

    private String containerSciFilter;

    private Boolean failCtxIfServletStartFails;

    protected static final ThreadBindingListener DEFAULT_NAMING_LISTENER = (new ThreadBindingListener() {
        @Override
        public void bind() {}
        @Override
        public void unbind() {}
    });
    protected ThreadBindingListener threadBindingListener = DEFAULT_NAMING_LISTENER;

    private final Object namingToken = new Object();

    private CookieProcessor cookieProcessor;

    private boolean validateClientProvidedNewSessionId = true;

    private boolean mapperContextRootRedirectEnabled = true;

    private boolean mapperDirectoryRedirectEnabled = false;

    private boolean useRelativeRedirects = !Globals.STRICT_SERVLET_COMPLIANCE;

    private boolean dispatchersUseEncodedPaths = true;

    private String requestEncoding = null;

    private String responseEncoding = null;

    // ----------------------------------------------------- Context Properties

    @Override
    public String getRequestCharacterEncoding() {
        return requestEncoding;
    }


    @Override
    public void setRequestCharacterEncoding(String requestEncoding) {
        this.requestEncoding = requestEncoding;
    }


    @Override
    public String getResponseCharacterEncoding() {
        return responseEncoding;
    }


    @Override
    public void setResponseCharacterEncoding(String responseEncoding) {
        this.responseEncoding = responseEncoding;
    }


    @Override
    public void setDispatchersUseEncodedPaths(boolean dispatchersUseEncodedPaths) {
        this.dispatchersUseEncodedPaths = dispatchersUseEncodedPaths;
    }


    /**
     * {@inheritDoc}
     * <p>
     * 这个实现默认值是{@code true}.
     */
    @Override
    public boolean getDispatchersUseEncodedPaths() {
        return dispatchersUseEncodedPaths;
    }


    @Override
    public void setUseRelativeRedirects(boolean useRelativeRedirects) {
        this.useRelativeRedirects = useRelativeRedirects;
    }


    /**
     * {@inheritDoc}
     * <p>
     * 这个实现默认值是{@code true}.
     */
    @Override
    public boolean getUseRelativeRedirects() {
        return useRelativeRedirects;
    }


    @Override
    public void setMapperContextRootRedirectEnabled(boolean mapperContextRootRedirectEnabled) {
        this.mapperContextRootRedirectEnabled = mapperContextRootRedirectEnabled;
    }


    /**
     * {@inheritDoc}
     * <p>
     * 这个实现默认值是{@code false}.
     */
    @Override
    public boolean getMapperContextRootRedirectEnabled() {
        return mapperContextRootRedirectEnabled;
    }


    @Override
    public void setMapperDirectoryRedirectEnabled(boolean mapperDirectoryRedirectEnabled) {
        this.mapperDirectoryRedirectEnabled = mapperDirectoryRedirectEnabled;
    }


    /**
     * {@inheritDoc}
     * <p>
     * 这个实现默认值是{@code false}.
     */
    @Override
    public boolean getMapperDirectoryRedirectEnabled() {
        return mapperDirectoryRedirectEnabled;
    }


    @Override
    public void setValidateClientProvidedNewSessionId(boolean validateClientProvidedNewSessionId) {
        this.validateClientProvidedNewSessionId = validateClientProvidedNewSessionId;
    }


    /**
     * {@inheritDoc}
     * <p>
     * 这个实现默认值是{@code true}.
     */
    @Override
    public boolean getValidateClientProvidedNewSessionId() {
        return validateClientProvidedNewSessionId;
    }


    @Override
    public void setCookieProcessor(CookieProcessor cookieProcessor) {
        if (cookieProcessor == null) {
            throw new IllegalArgumentException(
                    sm.getString("standardContext.cookieProcessor.null"));
        }
        this.cookieProcessor = cookieProcessor;
    }


    @Override
    public CookieProcessor getCookieProcessor() {
        return cookieProcessor;
    }


    @Override
    public Object getNamingToken() {
        return namingToken;
    }


    @Override
    public void setContainerSciFilter(String containerSciFilter) {
        this.containerSciFilter = containerSciFilter;
    }


    @Override
    public String getContainerSciFilter() {
        return containerSciFilter;
    }


    @Override
    public boolean getSendRedirectBody() {
        return sendRedirectBody;
    }


    @Override
    public void setSendRedirectBody(boolean sendRedirectBody) {
        this.sendRedirectBody = sendRedirectBody;
    }


    @Override
    public boolean getPreemptiveAuthentication() {
        return preemptiveAuthentication;
    }


    @Override
    public void setPreemptiveAuthentication(boolean preemptiveAuthentication) {
        this.preemptiveAuthentication = preemptiveAuthentication;
    }


    @Override
    public void setFireRequestListenersOnForwards(boolean enable) {
        fireRequestListenersOnForwards = enable;
    }


    @Override
    public boolean getFireRequestListenersOnForwards() {
        return fireRequestListenersOnForwards;
    }


    @Override
    public void setAddWebinfClassesResources(
            boolean addWebinfClassesResources) {
        this.addWebinfClassesResources = addWebinfClassesResources;
    }


    @Override
    public boolean getAddWebinfClassesResources() {
        return addWebinfClassesResources;
    }


    @Override
    public void setWebappVersion(String webappVersion) {
        if (null == webappVersion) {
            this.webappVersion = "";
        } else {
            this.webappVersion = webappVersion;
        }
    }


    @Override
    public String getWebappVersion() {
        return webappVersion;
    }


    @Override
    public String getBaseName() {
        return new ContextName(path, webappVersion).getBaseName();
    }


    @Override
    public String getResourceOnlyServlets() {
        return StringUtils.join(resourceOnlyServlets);
    }


    @Override
    public void setResourceOnlyServlets(String resourceOnlyServlets) {
        this.resourceOnlyServlets.clear();
        if (resourceOnlyServlets == null) {
            return;
        }
        for (String servletName : resourceOnlyServlets.split(",")) {
            servletName = servletName.trim();
            if (servletName.length()>0) {
                this.resourceOnlyServlets.add(servletName);
            }
        }
    }


    @Override
    public boolean isResourceOnlyServlet(String servletName) {
        return resourceOnlyServlets.contains(servletName);
    }


    @Override
    public int getEffectiveMajorVersion() {
        return effectiveMajorVersion;
    }

    @Override
    public void setEffectiveMajorVersion(int effectiveMajorVersion) {
        this.effectiveMajorVersion = effectiveMajorVersion;
    }

    @Override
    public int getEffectiveMinorVersion() {
        return effectiveMinorVersion;
    }

    @Override
    public void setEffectiveMinorVersion(int effectiveMinorVersion) {
        this.effectiveMinorVersion = effectiveMinorVersion;
    }

    @Override
    public void setLogEffectiveWebXml(boolean logEffectiveWebXml) {
        this.logEffectiveWebXml = logEffectiveWebXml;
    }

    @Override
    public boolean getLogEffectiveWebXml() {
        return logEffectiveWebXml;
    }

    @Override
    public Authenticator getAuthenticator() {
        Pipeline pipeline = getPipeline();
        if (pipeline != null) {
            Valve basic = pipeline.getBasic();
            if (basic instanceof Authenticator)
                return (Authenticator) basic;
            for (Valve valve : pipeline.getValves()) {
                if (valve instanceof Authenticator) {
                    return (Authenticator) valve;
                }
            }
        }
        return null;
    }

    @Override
    public JarScanner getJarScanner() {
        if (jarScanner == null) {
            jarScanner = new StandardJarScanner();
        }
        return jarScanner;
    }


    @Override
    public void setJarScanner(JarScanner jarScanner) {
        this.jarScanner = jarScanner;
    }


    @Override
    public InstanceManager getInstanceManager() {
       return instanceManager;
    }


    @Override
    public void setInstanceManager(InstanceManager instanceManager) {
       this.instanceManager = instanceManager;
    }


    @Override
    public String getEncodedPath() {
        return encodedPath;
    }


    /**
     * 设置为<code>true</code>, 允许请求映射到servlet,
     * 不需要直接声明 @MultipartConfig 或在web.xml中指定  &lt;multipart-config&gt; , 来解析multipart/form-data 请求.
     *
     * @param allowCasualMultipartParsing <code>true</code>允许这种随意的句法分析, 否则<code>false</code>.
     */
    @Override
    public void setAllowCasualMultipartParsing(boolean allowCasualMultipartParsing) {
        this.allowCasualMultipartParsing = allowCasualMultipartParsing;
    }

    /**
     * 返回<code>true</code>, 如果请求映射到servlet, 不需要"multipart config"来解析multipart/form-data请求.
     */
    @Override
    public boolean getAllowCasualMultipartParsing() {
        return this.allowCasualMultipartParsing;
    }

    /**
     * 设置为<code>false</code>, 禁用请求数据吞咽, 当上传由于大小限制而中止的时候.
     *
     * @param swallowAbortedUploads <code>false</code>禁用吞咽, 否则<code>true</code>(默认).
     */
    @Override
    public void setSwallowAbortedUploads(boolean swallowAbortedUploads) {
        this.swallowAbortedUploads = swallowAbortedUploads;
    }

    /**
     * 返回<code>true</code>如果剩余的请求数据将被读取，即使请求违反数据大小约束.
     *
     * @return <code>true</code>如果数据将被忽略(默认), 否则<code>false</code>.
     */
    @Override
    public boolean getSwallowAbortedUploads() {
        return this.swallowAbortedUploads;
    }

    /**
     * 添加一个ServletContainerInitializer实例到这个Web应用.
     *
     * @param sci       要添加的实例
     * @param classes   初始化器感兴趣的类
     */
    @Override
    public void addServletContainerInitializer(ServletContainerInitializer sci, Set<Class<?>> classes) {
        initializers.put(sci, classes);
    }


    /**
     * 返回用于配置ClassLoader的"遵循标准委托模型"标志.
     *
     * @return <code>true</code> 如果类加载第一次委托给父类加载器
     */
    public boolean getDelegate() {
        return (this.delegate);
    }


    /**
     * 设置用于配置ClassLoader的"遵循标准委托模型"标志.
     *
     * @param delegate The new flag
     */
    public void setDelegate(boolean delegate) {
        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", oldDelegate, this.delegate);
    }


    /**
     * @return true 如果使用内部命名支持.
     */
    public boolean isUseNaming() {
        return (useNaming);
    }


    /**
     * 启用或禁用命名.
     *
     * @param useNaming <code>true</code>启用命名环境
     */
    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }


    @Override
    public Object[] getApplicationEventListeners() {
        return applicationEventListenersList.toArray();
    }


    /**
     * {@inheritDoc}
     *
     * 请注意，此实现不是线程安全的. 如果两个线程同时调用此方法, 结果可能是一组监听器或两者的结合.
     */
    @Override
    public void setApplicationEventListeners(Object listeners[]) {
        applicationEventListenersList.clear();
        if (listeners != null && listeners.length > 0) {
            applicationEventListenersList.addAll(Arrays.asList(listeners));
        }
    }


    /**
     * 将监听器添加到初始化的应用程序事件监听器列表的末尾.
     *
     * @param listener 要添加的监听器
     */
    public void addApplicationEventListener(Object listener) {
        applicationEventListenersList.add(listener);
    }


    @Override
    public Object[] getApplicationLifecycleListeners() {
        return (applicationLifecycleListenersObjects);
    }


    /**
     * 存储初始化的应用程序生命周期监听器对象集合, 按Web应用程序部署描述符中指定的顺序.
     *
     * @param listeners 实例化监听器对象的集合.
     */
    @Override
    public void setApplicationLifecycleListeners(Object listeners[]) {
        applicationLifecycleListenersObjects = listeners;
    }


    /**
     * 将监听器添加到初始化的应用程序生命周期监听器列表的末尾.
     *
     * @param listener 要添加的监听器
     */
    public void addApplicationLifecycleListener(Object listener) {
        int len = applicationLifecycleListenersObjects.length;
        Object[] newListeners = Arrays.copyOf(
                applicationLifecycleListenersObjects, len + 1);
        newListeners[len] = listener;
        applicationLifecycleListenersObjects = newListeners;
    }


    public boolean getAntiResourceLocking() {
        return (this.antiResourceLocking);
    }


    public void setAntiResourceLocking(boolean antiResourceLocking) {
        boolean oldAntiResourceLocking = this.antiResourceLocking;
        this.antiResourceLocking = antiResourceLocking;
        support.firePropertyChange("antiResourceLocking",
                                   oldAntiResourceLocking,
                                   this.antiResourceLocking);
    }


    /**
     * @return 此上下文的字符集映射器的区域设置.
     */
    public CharsetMapper getCharsetMapper() {

        // 在第一次请求时创建映射器
        if (this.charsetMapper == null) {
            try {
                Class<?> clazz = Class.forName(charsetMapperClass);
                this.charsetMapper = (CharsetMapper) clazz.getConstructor().newInstance();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                this.charsetMapper = new CharsetMapper();
            }
        }
        return (this.charsetMapper);
    }


    /**
     * Set the Locale to character set mapper for this Context.
     *
     * @param mapper The new mapper
     */
    public void setCharsetMapper(CharsetMapper mapper) {
        CharsetMapper oldCharsetMapper = this.charsetMapper;
        this.charsetMapper = mapper;
        if( mapper != null )
            this.charsetMapperClass= mapper.getClass().getName();
        support.firePropertyChange("charsetMapper", oldCharsetMapper, this.charsetMapper);
    }


    @Override
    public String getCharset(Locale locale) {
        return getCharsetMapper().getCharset(locale);
    }


    @Override
    public URL getConfigFile() {
        return this.configFile;
    }


    @Override
    public void setConfigFile(URL configFile) {
        this.configFile = configFile;
    }


    @Override
    public boolean getConfigured() {
        return this.configured;
    }


    /**
     * 此上下文的“正确配置”标志. 这可以通过启动监听器设置为false，该监听器检测致命的配置错误以避免应用程序可用.
     *
     * @param configured 正确配置的标志
     */
    @Override
    public void setConfigured(boolean configured) {

        boolean oldConfigured = this.configured;
        this.configured = configured;
        support.firePropertyChange("configured",
                                   oldConfigured,
                                   this.configured);

    }


    @Override
    public boolean getCookies() {
        return this.cookies;
    }


    /**
     * 设置"为会话ID使用cookie"标志.
     *
     * @param cookies The new flag
     */
    @Override
    public void setCookies(boolean cookies) {

        boolean oldCookies = this.cookies;
        this.cookies = cookies;
        support.firePropertyChange("cookies",
                                   oldCookies,
                                   this.cookies);

    }


    /**
     * 获取用于会话cookie的名称. 重写可由应用程序指定的任何设置.
     */
    @Override
    public String getSessionCookieName() {
        return sessionCookieName;
    }


    /**
     * 设置用于会话cookie的名称. 重写可由应用程序指定的任何设置.
     *
     * @param sessionCookieName   要使用的名称
     */
    @Override
    public void setSessionCookieName(String sessionCookieName) {
        String oldSessionCookieName = this.sessionCookieName;
        this.sessionCookieName = sessionCookieName;
        support.firePropertyChange("sessionCookieName",
                oldSessionCookieName, sessionCookieName);
    }


    /**
     * 是否为会话cookie使用 HttpOnly cookie.
     *
     * @return <code>true</code>如果应该设置会话cookie上的HttpOnly标志
     */
    @Override
    public boolean getUseHttpOnly() {
        return useHttpOnly;
    }


    /**
     * 是否为会话cookie使用 HttpOnly cookie.
     *
     * @param useHttpOnly   设置为<code>true</code>来为会话cookie使用HttpOnly cookie
     */
    @Override
    public void setUseHttpOnly(boolean useHttpOnly) {
        boolean oldUseHttpOnly = this.useHttpOnly;
        this.useHttpOnly = useHttpOnly;
        support.firePropertyChange("useHttpOnly",
                oldUseHttpOnly,
                this.useHttpOnly);
    }


    /**
     * 获取用于会话cookie的域名. 重写可由应用程序指定的任何设置.
     */
    @Override
    public String getSessionCookieDomain() {
        return sessionCookieDomain;
    }


    /**
     * 设置用于会话cookie的域名. 重写可由应用程序指定的任何设置.
     *
     * @param sessionCookieDomain   要使用的域名
     */
    @Override
    public void setSessionCookieDomain(String sessionCookieDomain) {
        String oldSessionCookieDomain = this.sessionCookieDomain;
        this.sessionCookieDomain = sessionCookieDomain;
        support.firePropertyChange("sessionCookieDomain",
                oldSessionCookieDomain, sessionCookieDomain);
    }


    /**
     * 获取用于会话cookie的路径. 重写可由应用程序指定的任何设置.
     */
    @Override
    public String getSessionCookiePath() {
        return sessionCookiePath;
    }


    /**
     * 设置用于会话cookie的路径. 重写可由应用程序指定的任何设置.
     *
     * @param sessionCookiePath   要使用的路径
     */
    @Override
    public void setSessionCookiePath(String sessionCookiePath) {
        String oldSessionCookiePath = this.sessionCookiePath;
        this.sessionCookiePath = sessionCookiePath;
        support.firePropertyChange("sessionCookiePath",
                oldSessionCookiePath, sessionCookiePath);
    }


    @Override
    public boolean getSessionCookiePathUsesTrailingSlash() {
        return sessionCookiePathUsesTrailingSlash;
    }


    @Override
    public void setSessionCookiePathUsesTrailingSlash(
            boolean sessionCookiePathUsesTrailingSlash) {
        this.sessionCookiePathUsesTrailingSlash =
            sessionCookiePathUsesTrailingSlash;
    }


    @Override
    public boolean getCrossContext() {
        return this.crossContext;
    }


    /**
     * 设置"允许交叉servlet上下文"标志.
     *
     * @param crossContext The new cross contexts flag
     */
    @Override
    public void setCrossContext(boolean crossContext) {

        boolean oldCrossContext = this.crossContext;
        this.crossContext = crossContext;
        support.firePropertyChange("crossContext",
                                   oldCrossContext,
                                   this.crossContext);

    }

    public String getDefaultContextXml() {
        return defaultContextXml;
    }

    /**
     * 设置将使用的默认上下文XML的位置.
     * 如果不是绝对的, 这将是相对于引擎的基础目录(默认是catalina.base 系统属性).
     *
     * @param defaultContextXml The default web xml
     */
    public void setDefaultContextXml(String defaultContextXml) {
        this.defaultContextXml = defaultContextXml;
    }

    public String getDefaultWebXml() {
        return defaultWebXml;
    }

    /**
     * 设置使用的默认Web XML的位置.
     * 如果不是绝对的, 这将是相对于引擎的基础目录(默认是catalina.base 系统属性).
     *
     * @param defaultWebXml The default web xml
     */
    public void setDefaultWebXml(String defaultWebXml) {
        this.defaultWebXml = defaultWebXml;
    }

    /**
     * 获取启动上下文的时间（以毫秒为单位）.
     */
    public long getStartupTime() {
        return startupTime;
    }

    public void setStartupTime(long startupTime) {
        this.startupTime = startupTime;
    }

    public long getTldScanTime() {
        return tldScanTime;
    }

    public void setTldScanTime(long tldScanTime) {
        this.tldScanTime = tldScanTime;
    }


    @Override
    public boolean getDenyUncoveredHttpMethods() {
        return denyUncoveredHttpMethods;
    }


    @Override
    public void setDenyUncoveredHttpMethods(boolean denyUncoveredHttpMethods) {
        this.denyUncoveredHttpMethods = denyUncoveredHttpMethods;
    }


    /**
     * @return 此Web应用程序的显示名称.
     */
    @Override
    public String getDisplayName() {
        return (this.displayName);
    }


    /**
     * @return 备用部署描述符名称.
     */
    @Override
    public String getAltDDName(){
        return altDDName;
    }


    /**
     * 设置备用部署描述符名称.
     *
     * @param altDDName The new name
     */
    @Override
    public void setAltDDName(String altDDName) {
        this.altDDName = altDDName;
        if (context != null) {
            context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
    }


    /**
     * 设置此Web应用程序的显示名称.
     *
     * @param displayName 显示名称
     */
    @Override
    public void setDisplayName(String displayName) {
        String oldDisplayName = this.displayName;
        this.displayName = displayName;
        support.firePropertyChange("displayName", oldDisplayName,
                                   this.displayName);
    }


    /**
     * @return 此Web应用程序的可分发标志.
     */
    @Override
    public boolean getDistributable() {
        return (this.distributable);
    }

    /**
     * 设置此Web应用程序的可分发标志.
     *
     * @param distributable 可分发标志
     */
    @Override
    public void setDistributable(boolean distributable) {
        boolean oldDistributable = this.distributable;
        this.distributable = distributable;
        support.firePropertyChange("distributable",
                                   oldDistributable,
                                   this.distributable);
    }


    /**
     * @return 此上下文的文档根目录.  这是一个绝对路径名, 相对路径, 或一个 URL.
     */
    @Override
    public String getDocBase() {
        return (this.docBase);
    }


    /**
     * 设置此上下文的文档根目录. 这是一个绝对路径名, 相对路径, 或一个 URL.
     *
     * @param docBase 文档根目录
     */
    @Override
    public void setDocBase(String docBase) {
        this.docBase = docBase;
    }

    public String getJ2EEApplication() {
        return j2EEApplication;
    }

    public void setJ2EEApplication(String j2EEApplication) {
        this.j2EEApplication = j2EEApplication;
    }

    public String getJ2EEServer() {
        return j2EEServer;
    }

    public void setJ2EEServer(String j2EEServer) {
        this.j2EEServer = j2EEServer;
    }


    @Override
    public Loader getLoader() {
        Lock readLock = loaderLock.readLock();
        readLock.lock();
        try {
            return loader;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void setLoader(Loader loader) {

        Lock writeLock = loaderLock.writeLock();
        writeLock.lock();
        Loader oldLoader = null;
        try {
            // Change components if necessary
            oldLoader = this.loader;
            if (oldLoader == loader)
                return;
            this.loader = loader;

            // Stop the old component if necessary
            if (getState().isAvailable() && (oldLoader != null) &&
                (oldLoader instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldLoader).stop();
                } catch (LifecycleException e) {
                    log.error("StandardContext.setLoader: stop: ", e);
                }
            }

            // Start the new component if necessary
            if (loader != null)
                loader.setContext(this);
            if (getState().isAvailable() && (loader != null) &&
                (loader instanceof Lifecycle)) {
                try {
                    ((Lifecycle) loader).start();
                } catch (LifecycleException e) {
                    log.error("StandardContext.setLoader: start: ", e);
                }
            }
        } finally {
            writeLock.unlock();
        }

        // Report this property change to interested listeners
        support.firePropertyChange("loader", oldLoader, loader);
    }


    @Override
    public Manager getManager() {
        Lock readLock = managerLock.readLock();
        readLock.lock();
        try {
            return manager;
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public void setManager(Manager manager) {

        Lock writeLock = managerLock.writeLock();
        writeLock.lock();
        Manager oldManager = null;
        try {
            // Change components if necessary
            oldManager = this.manager;
            if (oldManager == manager)
                return;
            this.manager = manager;

            // Stop the old component if necessary
            if (oldManager instanceof Lifecycle) {
                try {
                    ((Lifecycle) oldManager).stop();
                    ((Lifecycle) oldManager).destroy();
                } catch (LifecycleException e) {
                    log.error("StandardContext.setManager: stop-destroy: ", e);
                }
            }

            // Start the new component if necessary
            if (manager != null) {
                manager.setContext(this);
            }
            if (getState().isAvailable() && manager instanceof Lifecycle) {
                try {
                    ((Lifecycle) manager).start();
                } catch (LifecycleException e) {
                    log.error("StandardContext.setManager: start: ", e);
                }
            }
        } finally {
            writeLock.unlock();
        }

        // Report this property change to interested listeners
        support.firePropertyChange("manager", oldManager, manager);
    }


    /**
     * @return 是否忽略注解.
     */
    @Override
    public boolean getIgnoreAnnotations() {
        return this.ignoreAnnotations;
    }


    /**
     * 是否忽略注解.
     *
     * @param ignoreAnnotations The boolean on the annotations parsing
     */
    @Override
    public void setIgnoreAnnotations(boolean ignoreAnnotations) {
        boolean oldIgnoreAnnotations = this.ignoreAnnotations;
        this.ignoreAnnotations = ignoreAnnotations;
        support.firePropertyChange("ignoreAnnotations", oldIgnoreAnnotations,
                this.ignoreAnnotations);
    }


    /**
     * @return 此Web应用程序的登录配置描述符.
     */
    @Override
    public LoginConfig getLoginConfig() {
        return (this.loginConfig);
    }


    /**
     * 设置此Web应用程序的登录配置描述符.
     *
     * @param config 登录配置
     */
    @Override
    public void setLoginConfig(LoginConfig config) {

        // 验证传入的属性值
        if (config == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.loginConfig.required"));
        String loginPage = config.getLoginPage();
        if ((loginPage != null) && !loginPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.loginWarning",
                                 loginPage));
                config.setLoginPage("/" + loginPage);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.loginConfig.loginPage",
                                  loginPage));
            }
        }
        String errorPage = config.getErrorPage();
        if ((errorPage != null) && !errorPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.errorWarning",
                                 errorPage));
                config.setErrorPage("/" + errorPage);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.loginConfig.errorPage",
                                  errorPage));
            }
        }

        // 处理属性设置更改
        LoginConfig oldLoginConfig = this.loginConfig;
        this.loginConfig = config;
        support.firePropertyChange("loginConfig",
                                   oldLoginConfig, this.loginConfig);

    }


    /**
     * @return 关联的命名资源.
     */
    @Override
    public NamingResourcesImpl getNamingResources() {

        if (namingResources == null) {
            setNamingResources(new NamingResourcesImpl());
        }
        return (namingResources);
    }


    /**
     * 设置Web应用的命名资源.
     *
     * @param namingResources 命名资源
     */
    @Override
    public void setNamingResources(NamingResourcesImpl namingResources) {

        // Process the property setting change
        NamingResourcesImpl oldNamingResources = this.namingResources;
        this.namingResources = namingResources;
        if (namingResources != null) {
            namingResources.setContainer(this);
        }
        support.firePropertyChange("namingResources",
                                   oldNamingResources, this.namingResources);

        if (getState() == LifecycleState.NEW ||
                getState() == LifecycleState.INITIALIZING ||
                getState() == LifecycleState.INITIALIZED) {
            // NEW 将会发生，如果Context定义在 server.xml
            // 在这里 getObjectKeyPropertiesNameOnly() 将触发一个NPE.
            // INITIALIZED 将会发生，如果Context定义在 context.xml
            // 如果现在启动, 当上下文开始时，将尝试第二个开始

            // 在这两种情况下, 返回并让上下文在启动时初始化命名资源
            return;
        }

        if (oldNamingResources != null) {
            try {
                oldNamingResources.stop();
                oldNamingResources.destroy();
            } catch (LifecycleException e) {
                log.warn("standardContext.namingResource.destroy.fail", e);
            }
        }
        if (namingResources != null) {
            try {
                namingResources.init();
                namingResources.start();
            } catch (LifecycleException e) {
                log.warn("standardContext.namingResource.init.fail", e);
            }
        }
    }


    /**
     * @return 此上下文的上下文路径.
     */
    @Override
    public String getPath() {
        return (path);
    }


    /**
     * 设置此上下文的上下文路径.
     *
     * @param path 上下文路径
     */
    @Override
    public void setPath(String path) {
        boolean invalid = false;
        if (path == null || path.equals("/")) {
            invalid = true;
            this.path = "";
        } else if ("".equals(path) || path.startsWith("/")) {
            this.path = path;
        } else {
            invalid = true;
            this.path = "/" + path;
        }
        if (this.path.endsWith("/")) {
            invalid = true;
            this.path = this.path.substring(0, this.path.length() - 1);
        }
        if (invalid) {
            log.warn(sm.getString(
                    "standardContext.pathInvalid", path, this.path));
        }
        encodedPath = URLEncoder.DEFAULT.encode(this.path, StandardCharsets.UTF_8);
        if (getName() == null) {
            setName(this.path);
        }
    }


    /**
     * @return 当前正在解析的部署描述符DTD的公共标识符.
     */
    @Override
    public String getPublicId() {
        return (this.publicId);
    }


    /**
     * 设置当前正在解析的部署描述符DTD的公共标识符.
     *
     * @param publicId The public identifier
     */
    @Override
    public void setPublicId(String publicId) {

        if (log.isDebugEnabled())
            log.debug("Setting deployment descriptor public ID to '" +
                publicId + "'");

        String oldPublicId = this.publicId;
        this.publicId = publicId;
        support.firePropertyChange("publicId", oldPublicId, publicId);

    }


    /**
     * @return 是否重新加载.
     */
    @Override
    public boolean getReloadable() {
        return (this.reloadable);
    }


    /**
     * @return 这个Web应用的默认上下文覆盖标志.
     */
    @Override
    public boolean getOverride() {
        return (this.override);
    }


    /**
     * @return 这个Context的原始文档根目录. 这可以是绝对路径名, 相对路径名, 或一个URL.
     * Is only set as deployment has change docRoot!
     */
    public String getOriginalDocBase() {
        return (this.originalDocBase);
    }

    /**
     * 设置这个Context的原始文档根目录. 这可以是绝对路径名, 相对路径名, 或一个URL.
     *
     * @param docBase 原始文档根目录
     */
    public void setOriginalDocBase(String docBase) {
        this.originalDocBase = docBase;
    }


    /**
     * @return 父类加载器.
     * 只有在配置了Loader之后，这个调用才有意义.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (getPrivileged()) {
            return this.getClass().getClassLoader();
        } else if (parent != null) {
            return (parent.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }


    /**
     * @return 特权标志.
     */
    @Override
    public boolean getPrivileged() {
        return (this.privileged);
    }


    /**
     * 特权标志.
     *
     * @param privileged The new privileged flag
     */
    @Override
    public void setPrivileged(boolean privileged) {

        boolean oldPrivileged = this.privileged;
        this.privileged = privileged;
        support.firePropertyChange("privileged",
                                   oldPrivileged,
                                   this.privileged);
    }


    /**
     * 设置此应用的重新加载标志.
     *
     * @param reloadable The new reloadable flag
     */
    @Override
    public void setReloadable(boolean reloadable) {

        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   oldReloadable,
                                   this.reloadable);
    }


    /**
     * 设置此Web应用程序的默认上下文覆盖标志.
     *
     * @param override The new override flag
     */
    @Override
    public void setOverride(boolean override) {

        boolean oldOverride = this.override;
        this.override = override;
        support.firePropertyChange("override",
                                   oldOverride,
                                   this.override);
    }


    /**
     * 设置"替换欢迎文件"属性.
     *
     * @param replaceWelcomeFiles The new property value
     */
    public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {
        boolean oldReplaceWelcomeFiles = this.replaceWelcomeFiles;
        this.replaceWelcomeFiles = replaceWelcomeFiles;
        support.firePropertyChange("replaceWelcomeFiles",
                                   oldReplaceWelcomeFiles,
                                   this.replaceWelcomeFiles);
    }


    /**
     * @return 返回原始的servlet上下文.
     */
    @Override
    public ServletContext getServletContext() {
        if (context == null) {
            context = new ApplicationContext(this);
            if (altDDName != null)
                context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
        return (context.getFacade());
    }


    /**
     * @return 默认会话超时时间(in minutes)
     */
    @Override
    public int getSessionTimeout() {
        return (this.sessionTimeout);
    }


    /**
     * 设置默认会话超时时间(in minutes)
     *
     * @param timeout 默认会话超时时间
     */
    @Override
    public void setSessionTimeout(int timeout) {

        int oldSessionTimeout = this.sessionTimeout;
        /*
         * SRV.13.4 ("Deployment Descriptor"):
         * 如果超时时间是 0 或负值, 容器确保会话的默认行为永远不会超时.
         */
        this.sessionTimeout = (timeout == 0) ? -1 : timeout;
        support.firePropertyChange("sessionTimeout",
                                   oldSessionTimeout,
                                   this.sessionTimeout);
    }


    @Override
    public boolean getSwallowOutput() {
        return (this.swallowOutput);
    }


    /**
     * 如果设置为 true, 将导致system.out 和system.err 在执行servlet时要重定向到logger.
     *
     * @param swallowOutput The new value
     */
    @Override
    public void setSwallowOutput(boolean swallowOutput) {

        boolean oldSwallowOutput = this.swallowOutput;
        this.swallowOutput = swallowOutput;
        support.firePropertyChange("swallowOutput",
                                   oldSwallowOutput,
                                   this.swallowOutput);

    }


    public long getUnloadDelay() {
        return (this.unloadDelay);
    }


    /**
     * 表示卸载servlet时容器将等待的毫秒数.
     * 将此设置为一个小值可能会导致在停止Web应用程序时无法完成更多的请求.
     *
     * @param unloadDelay The new value
     */
    public void setUnloadDelay(long unloadDelay) {
        long oldUnloadDelay = this.unloadDelay;
        this.unloadDelay = unloadDelay;
        support.firePropertyChange("unloadDelay",
                                   Long.valueOf(oldUnloadDelay),
                                   Long.valueOf(this.unloadDelay));
    }


    /**
     * @return 是否解压 WAR.
     */
    public boolean getUnpackWAR() {
        return (unpackWAR);
    }


    /**
     * 是否解压 WAR.
     *
     * @param unpackWAR <code>true</code>表示在部署的时候解压WAR
     */
    public void setUnpackWAR(boolean unpackWAR) {
        this.unpackWAR = unpackWAR;
    }


    /**
     * context.xml文件是否应该被复制到配置的目录. 默认不会.
     *
     * @return <code>true</code>表示如果WAR中包含<code>META-INF/context.xml</code>文件， 将在部署时复制到主机配置基础文件夹
     */
    public boolean getCopyXML() {
        return copyXML;
    }


    /**
     * context.xml文件是否应该被复制到配置的目录. 默认不会.
     *
     * @param copyXML the new flag value
     */
    public void setCopyXML(boolean copyXML) {
        this.copyXML = copyXML;
    }


    /**
     * @return Wrapper实现类的Java类名，用于将Servlet注册进这个Context.
     */
    @Override
    public String getWrapperClass() {
        return (this.wrapperClassName);
    }


    /**
     * 设置Wrapper实现类的Java类名，用于将Servlet注册进这个Context..
     *
     * @param wrapperClassName The new wrapper class name
     *
     * @throws IllegalArgumentException 如果找不到指定的wrapper类，或者不是StandardWrapper的子类
     */
    @Override
    public void setWrapperClass(String wrapperClassName) {
        this.wrapperClassName = wrapperClassName;

        try {
            wrapperClass = Class.forName(wrapperClassName);
            if (!StandardWrapper.class.isAssignableFrom(wrapperClass)) {
                throw new IllegalArgumentException(
                    sm.getString("standardContext.invalidWrapperClass",
                                 wrapperClassName));
            }
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException(cnfe.getMessage());
        }
    }


    @Override
    public WebResourceRoot getResources() {
        Lock readLock = resourcesLock.readLock();
        readLock.lock();
        try {
            return resources;
        } finally {
            readLock.unlock();
        }
    }


    @Override
    public void setResources(WebResourceRoot resources) {

        Lock writeLock = resourcesLock.writeLock();
        writeLock.lock();
        WebResourceRoot oldResources = null;
        try {
            if (getState().isAvailable()) {
                throw new IllegalStateException
                    (sm.getString("standardContext.resourcesStart"));
            }

            oldResources = this.resources;
            if (oldResources == resources)
                return;

            this.resources = resources;
            if (oldResources != null) {
                oldResources.setContext(null);
            }
            if (resources != null) {
                resources.setContext(this);
            }

            support.firePropertyChange("resources", oldResources,
                    resources);
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return jspConfigDescriptor;
    }

    @Override
    public void setJspConfigDescriptor(JspConfigDescriptor descriptor) {
        this.jspConfigDescriptor = descriptor;
    }

    @Override
    public ThreadBindingListener getThreadBindingListener() {
        return threadBindingListener;
    }

    @Override
    public void setThreadBindingListener(ThreadBindingListener threadBindingListener) {
        this.threadBindingListener = threadBindingListener;
    }


    // ------------------------------------------------------ Public Properties

    /**
     * @return 修改JNDI上下文是否会触发异常或是否忽略请求.
     */
    public boolean getJndiExceptionOnFailedWrite() {
        return jndiExceptionOnFailedWrite;
    }


    /**
     * 修改JNDI上下文是否会触发异常或是否忽略请求.
     *
     * @param jndiExceptionOnFailedWrite <code>false</code>避免异常
     */
    public void setJndiExceptionOnFailedWrite(boolean jndiExceptionOnFailedWrite) {
        this.jndiExceptionOnFailedWrite = jndiExceptionOnFailedWrite;
    }


    /**
     * @return 字符集映射器的区域设置.
     */
    public String getCharsetMapperClass() {
        return (this.charsetMapperClass);
    }


    /**
     * 设置字符集映射器的区域设置.
     *
     * @param mapper The new mapper class
     */
    public void setCharsetMapperClass(String mapper) {

        String oldCharsetMapperClass = this.charsetMapperClass;
        this.charsetMapperClass = mapper;
        support.firePropertyChange("charsetMapperClass",
                                   oldCharsetMapperClass,
                                   this.charsetMapperClass);

    }


    /** 
     * 获取工作目录的绝对路径. 避免重复.
     *
     * @return 工作路径
     */
    public String getWorkPath() {
        if (getWorkDir() == null) {
            return null;
        }
        File workDir = new File(getWorkDir());
        if (!workDir.isAbsolute()) {
            try {
                workDir = new File(getCatalinaBase().getCanonicalFile(),
                        getWorkDir());
            } catch (IOException e) {
                log.warn(sm.getString("standardContext.workPath", getName()),
                        e);
            }
        }
        return workDir.getAbsolutePath();
    }

    /**
     * @return 这个Context的工作目录.
     */
    public String getWorkDir() {
        return (this.workDir);
    }


    /**
     * 设置这个Context的工作目录.
     *
     * @param workDir The new work directory
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;

        if (getState().isAvailable()) {
            postWorkDirectory();
        }
    }


    public boolean getClearReferencesRmiTargets() {
        return this.clearReferencesRmiTargets;
    }


    public void setClearReferencesRmiTargets(boolean clearReferencesRmiTargets) {
        boolean oldClearReferencesRmiTargets = this.clearReferencesRmiTargets;
        this.clearReferencesRmiTargets = clearReferencesRmiTargets;
        support.firePropertyChange("clearReferencesRmiTargets",
                oldClearReferencesRmiTargets, this.clearReferencesRmiTargets);
    }


    public boolean getClearReferencesStopThreads() {
        return (this.clearReferencesStopThreads);
    }


    public void setClearReferencesStopThreads(
            boolean clearReferencesStopThreads) {

        boolean oldClearReferencesStopThreads = this.clearReferencesStopThreads;
        this.clearReferencesStopThreads = clearReferencesStopThreads;
        support.firePropertyChange("clearReferencesStopThreads",
                                   oldClearReferencesStopThreads,
                                   this.clearReferencesStopThreads);
    }


    public boolean getClearReferencesStopTimerThreads() {
        return (this.clearReferencesStopTimerThreads);
    }


    public void setClearReferencesStopTimerThreads(
            boolean clearReferencesStopTimerThreads) {

        boolean oldClearReferencesStopTimerThreads =
            this.clearReferencesStopTimerThreads;
        this.clearReferencesStopTimerThreads = clearReferencesStopTimerThreads;
        support.firePropertyChange("clearReferencesStopTimerThreads",
                                   oldClearReferencesStopTimerThreads,
                                   this.clearReferencesStopTimerThreads);
    }


    public boolean getClearReferencesHttpClientKeepAliveThread() {
        return (this.clearReferencesHttpClientKeepAliveThread);
    }


    public void setClearReferencesHttpClientKeepAliveThread(
            boolean clearReferencesHttpClientKeepAliveThread) {
        this.clearReferencesHttpClientKeepAliveThread =
            clearReferencesHttpClientKeepAliveThread;
    }


    public boolean getRenewThreadsWhenStoppingContext() {
        return this.renewThreadsWhenStoppingContext;
    }

    public void setRenewThreadsWhenStoppingContext(
            boolean renewThreadsWhenStoppingContext) {
        boolean oldRenewThreadsWhenStoppingContext =
                this.renewThreadsWhenStoppingContext;
        this.renewThreadsWhenStoppingContext = renewThreadsWhenStoppingContext;
        support.firePropertyChange("renewThreadsWhenStoppingContext",
                oldRenewThreadsWhenStoppingContext,
                this.renewThreadsWhenStoppingContext);
    }

    public Boolean getFailCtxIfServletStartFails() {
        return failCtxIfServletStartFails;
    }

    public void setFailCtxIfServletStartFails(
            Boolean failCtxIfServletStartFails) {
        Boolean oldFailCtxIfServletStartFails = this.failCtxIfServletStartFails;
        this.failCtxIfServletStartFails = failCtxIfServletStartFails;
        support.firePropertyChange("failCtxIfServletStartFails",
                oldFailCtxIfServletStartFails,
                failCtxIfServletStartFails);
    }

    protected boolean getComputedFailCtxIfServletStartFails() {
        if(failCtxIfServletStartFails != null) {
            return failCtxIfServletStartFails.booleanValue();
        }
        //else look at Host config
        if(getParent() instanceof StandardHost) {
            return ((StandardHost)getParent()).isFailCtxIfServletStartFails();
        }
        //else
        return false;
    }

    // -------------------------------------------------------- Context Methods

    /**
     * 添加一个新的监听器类名到配置的监听器集合.
     *
     * @param listener 监听器类的Java类名
     */
    @Override
    public void addApplicationListener(String listener) {

        synchronized (applicationListenersLock) {
            String results[] = new String[applicationListeners.length + 1];
            for (int i = 0; i < applicationListeners.length; i++) {
                if (listener.equals(applicationListeners[i])) {
                    log.info(sm.getString("standardContext.duplicateListener",listener));
                    return;
                }
                results[i] = applicationListeners[i];
            }
            results[applicationListeners.length] = listener;
            applicationListeners = results;
        }
        fireContainerEvent("addApplicationListener", listener);

        // FIXME - add instance if already started?
    }


    /**
     * 添加一个新的应用参数.
     *
     * @param parameter 应用参数
     */
    @Override
    public void addApplicationParameter(ApplicationParameter parameter) {

        synchronized (applicationParametersLock) {
            String newName = parameter.getName();
            for (ApplicationParameter p : applicationParameters) {
                if (newName.equals(p.getName()) && !p.getOverride())
                    return;
            }
            ApplicationParameter results[] = Arrays.copyOf(
                    applicationParameters, applicationParameters.length + 1);
            results[applicationParameters.length] = parameter;
            applicationParameters = results;
        }
        fireContainerEvent("addApplicationParameter", parameter);

    }


    /**
     * 添加一个子级Container, 只有当其是Wrapper的实现类时.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException 如果容器不是Wrapper的实现类
     */
    @Override
    public void addChild(Container child) {

        // Global JspServlet
        Wrapper oldJspServlet = null;

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        boolean isJspServlet = "jsp".equals(child.getName());

        // Allow webapp to override JspServlet inherited from global web.xml.
        if (isJspServlet) {
            oldJspServlet = (Wrapper) findChild("jsp");
            if (oldJspServlet != null) {
                removeChild(oldJspServlet);
            }
        }

        super.addChild(child);

        if (isJspServlet && oldJspServlet != null) {
            /*
             * The webapp-specific JspServlet inherits all the mappings
             * specified in the global web.xml, and may add additional ones.
             */
            String[] jspMappings = oldJspServlet.findMappings();
            for (int i=0; jspMappings!=null && i<jspMappings.length; i++) {
                addServletMappingDecoded(jspMappings[i], child.getName());
            }
        }
    }


    /**
     * 为该Web应用程序添加一个安全约束.
     *
     * @param constraint 安全约束
     */
    @Override
    public void addConstraint(SecurityConstraint constraint) {

        // Validate the proposed constraint
        SecurityCollection collections[] = constraint.findCollections();
        for (int i = 0; i < collections.length; i++) {
            String patterns[] = collections[i].findPatterns();
            for (int j = 0; j < patterns.length; j++) {
                patterns[j] = adjustURLPattern(patterns[j]);
                if (!validateURLPattern(patterns[j]))
                    throw new IllegalArgumentException
                        (sm.getString
                         ("standardContext.securityConstraint.pattern",
                          patterns[j]));
            }
            if (collections[i].findMethods().length > 0 &&
                    collections[i].findOmittedMethods().length > 0) {
                throw new IllegalArgumentException(sm.getString(
                        "standardContext.securityConstraint.mixHttpMethod"));
            }
        }

        // 将此约束添加到Web应用程序的集合中
        synchronized (constraintsLock) {
            SecurityConstraint results[] =
                new SecurityConstraint[constraints.length + 1];
            for (int i = 0; i < constraints.length; i++)
                results[i] = constraints[i];
            results[constraints.length] = constraint;
            constraints = results;
        }

    }



    /**
     * 添加一个指定错误或Java异常对应的错误页面.
     *
     * @param errorPage The error page definition to be added
     */
    @Override
    public void addErrorPage(ErrorPage errorPage) {
        // Validate the input parameters
        if (errorPage == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.errorPage.required"));
        String location = errorPage.getLocation();
        if ((location != null) && !location.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.errorPage.warning",
                                 location));
                errorPage.setLocation("/" + location);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.errorPage.error",
                                  location));
            }
        }

        // Add the specified error page to our internal collections
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            synchronized (exceptionPages) {
                exceptionPages.put(exceptionType, errorPage);
            }
        } else {
            synchronized (statusPages) {
                statusPages.put(Integer.valueOf(errorPage.getErrorCode()),
                                errorPage);
            }
        }
        fireContainerEvent("addErrorPage", errorPage);

    }


    /**
     * 添加一个过滤器定义.
     *
     * @param filterDef The filter definition to be added
     */
    @Override
    public void addFilterDef(FilterDef filterDef) {

        synchronized (filterDefs) {
            filterDefs.put(filterDef.getFilterName(), filterDef);
        }
        fireContainerEvent("addFilterDef", filterDef);

    }


    /**
     * 添加一个过滤器映射.
     *
     * @param filterMap The filter mapping to be added
     *
     * @exception IllegalArgumentException 如果指定的过滤器名不匹配已经存在的过滤器定义，或者过滤器映射是错误的
     */
    @Override
    public void addFilterMap(FilterMap filterMap) {
        validateFilterMap(filterMap);
        // 将此筛选器映射添加到注册集
        filterMaps.add(filterMap);
        fireContainerEvent("addFilterMap", filterMap);
    }


    /**
     * 添加一个过滤器映射, 在部署描述符定义的映射之前，但是在其它通过这个方法添加的映射之后.
     *
     * @param filterMap The filter mapping to be added
     *
     * @exception IllegalArgumentException 如果指定的过滤器名不匹配已经存在的过滤器定义，或者过滤器映射是错误的
     */
    @Override
    public void addFilterMapBefore(FilterMap filterMap) {
        validateFilterMap(filterMap);
        // Add this filter mapping to our registered set
        filterMaps.addBefore(filterMap);
        fireContainerEvent("addFilterMap", filterMap);
    }


    /**
     * 验证指定的FilterMap.
     *
     * @param filterMap the filter mapping
     */
    private void validateFilterMap(FilterMap filterMap) {
        // 验证所提出的过滤器映射
        String filterName = filterMap.getFilterName();
        String[] servletNames = filterMap.getServletNames();
        String[] urlPatterns = filterMap.getURLPatterns();
        if (findFilterDef(filterName) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.name", filterName));

        if (!filterMap.getMatchAllServletNames() &&
            !filterMap.getMatchAllUrlPatterns() &&
            (servletNames.length == 0) && (urlPatterns.length == 0))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        // FIXME: Older spec revisions may still check this
        /*
        if ((servletNames.length != 0) && (urlPatterns.length != 0))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        */
        for (int i = 0; i < urlPatterns.length; i++) {
            if (!validateURLPattern(urlPatterns[i])) {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.filterMap.pattern",
                            urlPatterns[i]));
            }
        }
    }


    /**
     * 添加到本地编码映射 (see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale 区域设置以映射编码
     * @param encoding 用于给定区域设置的编码
     */
    @Override
    public void addLocaleEncodingMappingParameter(String locale, String encoding){
        getCharsetMapper().addCharsetMappingFromDeploymentDescriptor(locale, encoding);
    }


    /**
     * 为这个Web应用程序添加一个消息目的地.
     *
     * @param md New message destination
     */
    public void addMessageDestination(MessageDestination md) {

        synchronized (messageDestinations) {
            messageDestinations.put(md.getName(), md);
        }
        fireContainerEvent("addMessageDestination", md.getName());

    }


    /**
     * 为这个Web应用程序添加一个消息目的地引用.
     *
     * @param mdr New message destination reference
     */
    public void addMessageDestinationRef(MessageDestinationRef mdr) {

        namingResources.addMessageDestinationRef(mdr);
        fireContainerEvent("addMessageDestinationRef", mdr.getName());

    }


    /**
     * 添加一个新的MIME映射, 将指定的扩展名替换为现有映射.
     *
     * @param extension 映射的文件扩展名
     * @param mimeType 相应的MIME类型
     */
    @Override
    public void addMimeMapping(String extension, String mimeType) {

        synchronized (mimeMappings) {
            mimeMappings.put(extension.toLowerCase(Locale.ENGLISH), mimeType);
        }
        fireContainerEvent("addMimeMapping", extension);

    }


    /**
     * 添加一个新的上下文初始化参数.
     *
     * @param name Name of the new parameter
     * @param value Value of the new  parameter
     *
     * @exception IllegalArgumentException 如果缺少名称或值，或者此上下文初始化参数已注册
     */
    @Override
    public void addParameter(String name, String value) {
        // Validate the proposed context initialization parameter
        if ((name == null) || (value == null)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.parameter.required"));
        }

        // Add this parameter to our defined set if not already present
        String oldValue = parameters.putIfAbsent(name, value);

        if (oldValue != null) {
            throw new IllegalArgumentException(
                    sm.getString("standardContext.parameter.duplicate", name));
        }

        fireContainerEvent("addParameter", name);
    }


    /**
     * 添加安全角色引用.
     *
     * @param role 应用程序中使用的安全角色
     * @param link 要检查的实际安全角色
     */
    @Override
    public void addRoleMapping(String role, String link) {

        synchronized (roleMappings) {
            roleMappings.put(role, link);
        }
        fireContainerEvent("addRoleMapping", role);

    }


    /**
     * 添加一个新的安全角色.
     *
     * @param role New security role
     */
    @Override
    public void addSecurityRole(String role) {

        synchronized (securityRolesLock) {
            String results[] =new String[securityRoles.length + 1];
            for (int i = 0; i < securityRoles.length; i++)
                results[i] = securityRoles[i];
            results[securityRoles.length] = role;
            securityRoles = results;
        }
        fireContainerEvent("addSecurityRole", role);

    }


    @Override
    @Deprecated
    public void addServletMapping(String pattern, String name) {
        addServletMappingDecoded(UDecoder.URLDecode(pattern, "UTF-8"), name);
    }


    @Override
    @Deprecated
    public void addServletMapping(String pattern, String name, boolean jspWildCard) {
        addServletMappingDecoded(UDecoder.URLDecode(pattern, "UTF-8"), name, false);
    }


    @Override
    public void addServletMappingDecoded(String pattern, String name) {
        addServletMappingDecoded(pattern, name, false);
    }


    @Override
    public void addServletMappingDecoded(String pattern, String name,
                                  boolean jspWildCard) {
        // Validate the proposed mapping
        if (findChild(name) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.name", name));
        String adjustedPattern = adjustURLPattern(pattern);
        if (!validateURLPattern(adjustedPattern))
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.pattern", adjustedPattern));

        // Add this mapping to our registered set
        synchronized (servletMappingsLock) {
            String name2 = servletMappings.get(adjustedPattern);
            if (name2 != null) {
                // 不要允许一个以上的servlet在同一个模式上
                Wrapper wrapper = (Wrapper) findChild(name2);
                wrapper.removeMapping(adjustedPattern);
            }
            servletMappings.put(adjustedPattern, name);
        }
        Wrapper wrapper = (Wrapper) findChild(name);
        wrapper.addMapping(adjustedPattern);

        fireContainerEvent("addServletMapping", adjustedPattern);
    }


    /**
     * 向该上下文识别的集合添加新的监视资源.
     *
     * @param name 监视资源文件名称
     */
    @Override
    public void addWatchedResource(String name) {

        synchronized (watchedResourcesLock) {
            String results[] = new String[watchedResources.length + 1];
            for (int i = 0; i < watchedResources.length; i++)
                results[i] = watchedResources[i];
            results[watchedResources.length] = name;
            watchedResources = results;
        }
        fireContainerEvent("addWatchedResource", name);
    }


    /**
     * 添加一个新的欢迎文件.
     *
     * @param name 欢迎文件名
     */
    @Override
    public void addWelcomeFile(String name) {

        synchronized (welcomeFilesLock) {
            // 来自应用程序部署描述符的欢迎文件将完全替换来自conf/web.xml文件的欢迎文件
            if (replaceWelcomeFiles) {
                fireContainerEvent(CLEAR_WELCOME_FILES_EVENT, null);
                welcomeFiles = new String[0];
                setReplaceWelcomeFiles(false);
            }
            String results[] =new String[welcomeFiles.length + 1];
            for (int i = 0; i < welcomeFiles.length; i++)
                results[i] = welcomeFiles[i];
            results[welcomeFiles.length] = name;
            welcomeFiles = results;
        }
        if(this.getState().equals(LifecycleState.STARTED))
            fireContainerEvent(ADD_WELCOME_FILE_EVENT, name);
    }


    /**
     * 添加一个LifecycleListener类名，被添加到每个Wrapper.
     *
     * @param listener LifecycleListener类的Java类名
     */
    @Override
    public void addWrapperLifecycle(String listener) {

        synchronized (wrapperLifecyclesLock) {
            String results[] =new String[wrapperLifecycles.length + 1];
            for (int i = 0; i < wrapperLifecycles.length; i++)
                results[i] = wrapperLifecycles[i];
            results[wrapperLifecycles.length] = listener;
            wrapperLifecycles = results;
        }
        fireContainerEvent("addWrapperLifecycle", listener);
    }


    /**
     * 添加一个ContainerListener类名，被添加到每个Wrapper.
     *
     * @param listener ContainerListener类的Java类名
     */
    @Override
    public void addWrapperListener(String listener) {

        synchronized (wrapperListenersLock) {
            String results[] =new String[wrapperListeners.length + 1];
            for (int i = 0; i < wrapperListeners.length; i++)
                results[i] = wrapperListeners[i];
            results[wrapperListeners.length] = listener;
            wrapperListeners = results;
        }
        fireContainerEvent("addWrapperListener", listener);

    }


    /**
     * 工厂方法创建并返回一个Wrapper实例.
     * Wrapper的构造方法将被调用, 但没有设置属性.
     */
    @Override
    public Wrapper createWrapper() {

        Wrapper wrapper = null;
        if (wrapperClass != null) {
            try {
                wrapper = (Wrapper) wrapperClass.getConstructor().newInstance();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error("createWrapper", t);
                return null;
            }
        } else {
            wrapper = new StandardWrapper();
        }

        synchronized (wrapperLifecyclesLock) {
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                try {
                    Class<?> clazz = Class.forName(wrapperLifecycles[i]);
                    LifecycleListener listener =
                        (LifecycleListener) clazz.getConstructor().newInstance();
                    wrapper.addLifecycleListener(listener);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error("createWrapper", t);
                    return null;
                }
            }
        }

        synchronized (wrapperListenersLock) {
            for (int i = 0; i < wrapperListeners.length; i++) {
                try {
                    Class<?> clazz = Class.forName(wrapperListeners[i]);
                    ContainerListener listener =
                            (ContainerListener) clazz.getConstructor().newInstance();
                    wrapper.addContainerListener(listener);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error("createWrapper", t);
                    return null;
                }
            }
        }

        return wrapper;
    }


    /**
     * 返回配置的应用监听器类名集合.
     */
    @Override
    public String[] findApplicationListeners() {
        return applicationListeners;
    }


    /**
     * 返回应用参数集合.
     */
    @Override
    public ApplicationParameter[] findApplicationParameters() {

        synchronized (applicationParametersLock) {
            return (applicationParameters);
        }

    }


    /**
     * 返回此Web应用程序的安全约束.
     * 如果没有，返回一个零长度的数组.
     */
    @Override
    public SecurityConstraint[] findConstraints() {
        return (constraints);
    }


    /**
     * 返回指定的HTTP错误代码对应的错误页面, 或者返回<code>null</code>.
     *
     * @param errorCode 错误码
     */
    @Override
    public ErrorPage findErrorPage(int errorCode) {
        return statusPages.get(Integer.valueOf(errorCode));
    }


    /**
     * 返回指定异常类型对应的错误页面; 或者返回<code>null</code>.
     *
     * @param exceptionType 异常类型
     */
    @Override
    public ErrorPage findErrorPage(String exceptionType) {

        synchronized (exceptionPages) {
            return (exceptionPages.get(exceptionType));
        }

    }


    /**
     * 返回定义的所有错误页面集合，包括指定错误码和异常类型的.
     */
    @Override
    public ErrorPage[] findErrorPages() {

        synchronized(exceptionPages) {
            synchronized(statusPages) {
                ErrorPage results1[] = new ErrorPage[exceptionPages.size()];
                results1 = exceptionPages.values().toArray(results1);
                ErrorPage results2[] = new ErrorPage[statusPages.size()];
                results2 = statusPages.values().toArray(results2);
                ErrorPage results[] =
                    new ErrorPage[results1.length + results2.length];
                for (int i = 0; i < results1.length; i++)
                    results[i] = results1[i];
                for (int i = results1.length; i < results.length; i++)
                    results[i] = results2[i - results1.length];
                return (results);
            }
        }

    }


    /**
     * 返回指定名称对应的过滤器定义; 或者<code>null</code>.
     *
     * @param filterName 过滤器名
     */
    @Override
    public FilterDef findFilterDef(String filterName) {

        synchronized (filterDefs) {
            return (filterDefs.get(filterName));
        }

    }


    /**
     * @return 定义的过滤器集合.
     */
    @Override
    public FilterDef[] findFilterDefs() {

        synchronized (filterDefs) {
            FilterDef results[] = new FilterDef[filterDefs.size()];
            return (filterDefs.values().toArray(results));
        }

    }


    /**
     * @return 过滤器映射集合.
     */
    @Override
    public FilterMap[] findFilterMaps() {
        return filterMaps.asArray();
    }


    /**
     * @return 指定名称的消息目标; 或者<code>null</code>.
     *
     * @param name 消息目标的名称
     */
    public MessageDestination findMessageDestination(String name) {

        synchronized (messageDestinations) {
            return (messageDestinations.get(name));
        }

    }


    /**
     * @return 这个应用的所有消息目标. 如果没有，返回零长度数组.
     */
    public MessageDestination[] findMessageDestinations() {

        synchronized (messageDestinations) {
            MessageDestination results[] =
                new MessageDestination[messageDestinations.size()];
            return (messageDestinations.values().toArray(results));
        }

    }


    /**
     * @return 指定名称的消息目标引用; 或者<code>null</code>.
     *
     * @param name 消息目标引用的名称
     */
    public MessageDestinationRef findMessageDestinationRef(String name) {
        return namingResources.findMessageDestinationRef(name);
    }


    /**
     * @return 这个应用的所有消息目标引用. 如果没有，返回零长度数组.
     */
    public MessageDestinationRef[] findMessageDestinationRefs() {
        return namingResources.findMessageDestinationRefs();
    }


    /**
     * @return 指定的扩展名映射到的MIME类型;或者返回<code>null</code>.
     *
     * @param extension 映射到MIME 类型的扩展名
     */
    @Override
    public String findMimeMapping(String extension) {
        return (mimeMappings.get(extension.toLowerCase(Locale.ENGLISH)));
    }


    /**
     * @return 定义MIME映射的扩展名. 如果没有，则返回零长度数组.
     */
    @Override
    public String[] findMimeMappings() {

        synchronized (mimeMappings) {
            String results[] = new String[mimeMappings.size()];
            return
                (mimeMappings.keySet().toArray(results));
        }

    }


    /**
     * @return 指定的上下文初始化参数名称的值; 否则返回<code>null</code>.
     *
     * @param name 要返回的参数的名称
     */
    @Override
    public String findParameter(String name) {
        return parameters.get(name);
    }


    /**
     * @return 所有定义的上下文初始化参数的名称. 如果没有定义参数，则返回零长度数组.
     */
    @Override
    public String[] findParameters() {
        List<String> parameterNames = new ArrayList<>(parameters.size());
        parameterNames.addAll(parameters.keySet());
        return parameterNames.toArray(new String[parameterNames.size()]);
    }


    /**
     * 为给定的安全角色 (应用程序所使用的), 返回相应的角色名 (Realm定义的). 
     * 否则，返回指定的角色不变.
     *
     * @param 映射的安全角色
     * 
     * @return 角色名
     */
    @Override
    public String findRoleMapping(String role) {

        String realRole = null;
        synchronized (roleMappings) {
            realRole = roleMappings.get(role);
        }
        if (realRole != null)
            return (realRole);
        else
            return (role);
    }


    /**
     * @return <code>true</code>如果定义了指定的安全角色; 或者返回<code>false</code>.
     *
     * @param role 要验证的安全角色
     */
    @Override
    public boolean findSecurityRole(String role) {

        synchronized (securityRolesLock) {
            for (int i = 0; i < securityRoles.length; i++) {
                if (role.equals(securityRoles[i]))
                    return true;
            }
        }
        return false;
    }


    /**
     * @return 为该应用程序定义的安全角色. 如果没有，返回零长度数组.
     */
    @Override
    public String[] findSecurityRoles() {
        synchronized (securityRolesLock) {
            return (securityRoles);
        }
    }


    /**
     * @return 指定模式映射的servlet名称;
     * 或者返回<code>null</code>.
     *
     * @param pattern 请求映射的模式
     */
    @Override
    public String findServletMapping(String pattern) {

        synchronized (servletMappingsLock) {
            return (servletMappings.get(pattern));
        }

    }


    /**
     * @return 所有定义的servlet映射的模式. 如果没有，返回零长度数组.
     */
    @Override
    public String[] findServletMappings() {

        synchronized (servletMappingsLock) {
            String results[] = new String[servletMappings.size()];
            return
               (servletMappings.keySet().toArray(results));
        }

    }


    /**
     * @return 指定的HTTP状态码对应的错误页面的上下文相对URI; 或者返回<code>null</code>.
     *
     * @param status HTTP status code to look up
     */
    @Override
    public String findStatusPage(int status) {

        ErrorPage errorPage = statusPages.get(Integer.valueOf(status));
        if (errorPage!=null) {
            return errorPage.getLocation();
        }
        return null;

    }


    /**
     * @return 指定错误页面的HTTP状态代码集合.
     * 如果没有，返回零长度数组.
     */
    @Override
    public int[] findStatusPages() {

        synchronized (statusPages) {
            int results[] = new int[statusPages.size()];
            Iterator<Integer> elements = statusPages.keySet().iterator();
            int i = 0;
            while (elements.hasNext())
                results[i++] = elements.next().intValue();
            return (results);
        }

    }


    /**
     * @return <code>true</code>如果定义了指定的欢迎文件; 或者<code>false</code>.
     *
     * @param name 欢迎文件
     */
    @Override
    public boolean findWelcomeFile(String name) {

        synchronized (welcomeFilesLock) {
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (name.equals(welcomeFiles[i]))
                    return true;
            }
        }
        return false;

    }


    /**
     * @return 此上下文的监视资源集. 如果没有，返回零长度数组.
     */
    @Override
    public String[] findWatchedResources() {
        synchronized (watchedResourcesLock) {
            return watchedResources;
        }
    }


    /**
     * @return 定义的欢迎文件集合. 如果没有，返回零长度数组.
     */
    @Override
    public String[] findWelcomeFiles() {

        synchronized (welcomeFilesLock) {
            return (welcomeFiles);
        }

    }


    /**
     * @return LifecycleListener类名集合，将被添加到新创建的Wrapper.
     */
    @Override
    public String[] findWrapperLifecycles() {

        synchronized (wrapperLifecyclesLock) {
            return (wrapperLifecycles);
        }

    }


    /**
     * @return ContainerListener类名集合，将被添加到新创建的Wrapper.
     */
    @Override
    public String[] findWrapperListeners() {

        synchronized (wrapperListenersLock) {
            return (wrapperListeners);
        }

    }


    /**
     * 如果支持重新加载，则重新加载此Web应用程序.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  该方法被设计为处理重新加载, 在类加载器的底层类库中的类修改后或web.xml文件修改后.
     * 它不处理任何context.xml文件的修改. 如果修改了context.xml, 应该停止这个Context并创建(以及启动)一个 Context 实例.
     * 注意还有附加代码，在<code>CoyoteAdapter#postParseRequest()</code>中处理映射到暂停的Context的请求.
     *
     * @exception IllegalStateException 如果<code>reloadable</code>属性被设置为<code>false</code>.
     */
    @Override
    public synchronized void reload() {

        // Validate our current component state
        if (!getState().isAvailable())
            throw new IllegalStateException
                (sm.getString("standardContext.notStarted", getName()));

        if(log.isInfoEnabled())
            log.info(sm.getString("standardContext.reloadingStarted",
                    getName()));

        // 暂时停止接受请求.
        setPaused(true);

        try {
            stop();
        } catch (LifecycleException e) {
            log.error(
                sm.getString("standardContext.stoppingContext", getName()), e);
        }

        try {
            start();
        } catch (LifecycleException e) {
            log.error(
                sm.getString("standardContext.startingContext", getName()), e);
        }

        setPaused(false);

        if(log.isInfoEnabled())
            log.info(sm.getString("standardContext.reloadingCompleted",
                    getName()));

    }


    /**
     * 从监听器集合中删除指定的应用程序监听器.
     *
     * @param listener 要删除的监听器的Java类名
     */
    @Override
    public void removeApplicationListener(String listener) {

        synchronized (applicationListenersLock) {

            // Make sure this listener is currently present
            int n = -1;
            for (int i = 0; i < applicationListeners.length; i++) {
                if (applicationListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified listener
            int j = 0;
            String results[] = new String[applicationListeners.length - 1];
            for (int i = 0; i < applicationListeners.length; i++) {
                if (i != n)
                    results[j++] = applicationListeners[i];
            }
            applicationListeners = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeApplicationListener", listener);

        // FIXME - behavior if already started?
    }


    /**
     * 从集合中移除指定名称的应用程序参数.
     *
     * @param name 要移除的应用程序参数的名称
     */
    @Override
    public void removeApplicationParameter(String name) {

        synchronized (applicationParametersLock) {

            // Make sure this parameter is currently present
            int n = -1;
            for (int i = 0; i < applicationParameters.length; i++) {
                if (name.equals(applicationParameters[i].getName())) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified parameter
            int j = 0;
            ApplicationParameter results[] =
                new ApplicationParameter[applicationParameters.length - 1];
            for (int i = 0; i < applicationParameters.length; i++) {
                if (i != n)
                    results[j++] = applicationParameters[i];
            }
            applicationParameters = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeApplicationParameter", name);

    }


    /**
     * 删除一个子容器, 只有当子容器是Wrapper实现类的时候.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException 如果要添加的子容器不是Wrapper实现类
     */
    @Override
    public void removeChild(Container child) {

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        super.removeChild(child);

    }


    /**
     * 从这个Web应用程序中移除指定的安全约束.
     *
     * @param constraint 要删除的约束
     */
    @Override
    public void removeConstraint(SecurityConstraint constraint) {

        synchronized (constraintsLock) {

            // Make sure this constraint is currently present
            int n = -1;
            for (int i = 0; i < constraints.length; i++) {
                if (constraints[i].equals(constraint)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            SecurityConstraint results[] =
                new SecurityConstraint[constraints.length - 1];
            for (int i = 0; i < constraints.length; i++) {
                if (i != n)
                    results[j++] = constraints[i];
            }
            constraints = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeConstraint", constraint);

    }


    /**
     * 移除指定错误码或Java异常对应的错误页面; 否则，什么都不做
     *
     * @param errorPage 要移除的错误页面
     */
    @Override
    public void removeErrorPage(ErrorPage errorPage) {

        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            synchronized (exceptionPages) {
                exceptionPages.remove(exceptionType);
            }
        } else {
            synchronized (statusPages) {
                statusPages.remove(Integer.valueOf(errorPage.getErrorCode()));
            }
        }
        fireContainerEvent("removeErrorPage", errorPage);

    }


    /**
     * 移除指定的过滤器定义; 否则，什么都不做.
     *
     * @param filterDef Filter definition to be removed
     */
    @Override
    public void removeFilterDef(FilterDef filterDef) {

        synchronized (filterDefs) {
            filterDefs.remove(filterDef.getFilterName());
        }
        fireContainerEvent("removeFilterDef", filterDef);
    }


    /**
     * 移除过滤器映射.
     *
     * @param filterMap The filter mapping to be removed
     */
    @Override
    public void removeFilterMap(FilterMap filterMap) {
        filterMaps.remove(filterMap);
        // Inform interested listeners
        fireContainerEvent("removeFilterMap", filterMap);
    }


    /**
     * 删除指定名称的消息目标.
     *
     * @param name Name of the message destination to remove
     */
    public void removeMessageDestination(String name) {

        synchronized (messageDestinations) {
            messageDestinations.remove(name);
        }
        fireContainerEvent("removeMessageDestination", name);

    }


    /**
     * 删除指定名称的消息目标引用.
     *
     * @param name Name of the message destination ref to remove
     */
    public void removeMessageDestinationRef(String name) {

        namingResources.removeMessageDestinationRef(name);
        fireContainerEvent("removeMessageDestinationRef", name);

    }


    /**
     * 移除指定扩展名对应的MIME映射; 否则，什么都不做.
     *
     * @param extension Extension to remove the mapping for
     */
    @Override
    public void removeMimeMapping(String extension) {

        synchronized (mimeMappings) {
            mimeMappings.remove(extension);
        }
        fireContainerEvent("removeMimeMapping", extension);

    }


    /**
     * 移除指定名称对应的上下文初始化参数; 否则，什么都不做.
     *
     * @param name Name of the parameter to remove
     */
    @Override
    public void removeParameter(String name) {
        parameters.remove(name);
        fireContainerEvent("removeParameter", name);
    }


    /**
     * 移除指定名称对应的所有安全角色
     *
     * @param role Security role (as used in the application) to remove
     */
    @Override
    public void removeRoleMapping(String role) {

        synchronized (roleMappings) {
            roleMappings.remove(role);
        }
        fireContainerEvent("removeRoleMapping", role);

    }


    /**
     * 移除指定名称对应的所有安全角色.
     *
     * @param role Security role to remove
     */
    @Override
    public void removeSecurityRole(String role) {

        synchronized (securityRolesLock) {

            // Make sure this security role is currently present
            int n = -1;
            for (int i = 0; i < securityRoles.length; i++) {
                if (role.equals(securityRoles[i])) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified security role
            int j = 0;
            String results[] = new String[securityRoles.length - 1];
            for (int i = 0; i < securityRoles.length; i++) {
                if (i != n)
                    results[j++] = securityRoles[i];
            }
            securityRoles = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeSecurityRole", role);

    }


    /**
     * 移除指定模式对应的所有servlet映射; 否则，什么都不做.
     *
     * @param pattern URL pattern of the mapping to remove
     */
    @Override
    public void removeServletMapping(String pattern) {

        String name = null;
        synchronized (servletMappingsLock) {
            name = servletMappings.remove(pattern);
        }
        Wrapper wrapper = (Wrapper) findChild(name);
        if( wrapper != null ) {
            wrapper.removeMapping(pattern);
        }
        fireContainerEvent("removeServletMapping", pattern);
    }


    /**
     * 从与此上下文关联的列表中删除指定的受监视资源名称.
     *
     * @param name 要删除的被监视资源的名称
     */
    @Override
    public void removeWatchedResource(String name) {

        synchronized (watchedResourcesLock) {

            // Make sure this watched resource is currently present
            int n = -1;
            for (int i = 0; i < watchedResources.length; i++) {
                if (watchedResources[i].equals(name)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified watched resource
            int j = 0;
            String results[] = new String[watchedResources.length - 1];
            for (int i = 0; i < watchedResources.length; i++) {
                if (i != n)
                    results[j++] = watchedResources[i];
            }
            watchedResources = results;

        }

        fireContainerEvent("removeWatchedResource", name);

    }


    /**
     * 从指定的列表中删除指定的欢迎文件名.
     *
     * @param name 要移除的欢迎文件名
     */
    @Override
    public void removeWelcomeFile(String name) {

        synchronized (welcomeFilesLock) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (welcomeFiles[i].equals(name)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified welcome file
            int j = 0;
            String results[] = new String[welcomeFiles.length - 1];
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (i != n)
                    results[j++] = welcomeFiles[i];
            }
            welcomeFiles = results;

        }

        // Inform interested listeners
        if(this.getState().equals(LifecycleState.STARTED))
            fireContainerEvent(REMOVE_WELCOME_FILE_EVENT, name);

    }


    /**
     * 从LifecycleListener类集合中移除指定的类名，将被添加到新创建的Wrapper.
     *
     * @param listener Class name of a LifecycleListener class to be removed
     */
    @Override
    public void removeWrapperLifecycle(String listener) {


        synchronized (wrapperLifecyclesLock) {

            // Make sure this lifecycle listener is currently present
            int n = -1;
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                if (wrapperLifecycles[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified lifecycle listener
            int j = 0;
            String results[] = new String[wrapperLifecycles.length - 1];
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                if (i != n)
                    results[j++] = wrapperLifecycles[i];
            }
            wrapperLifecycles = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeWrapperLifecycle", listener);

    }


    /**
     * 从ContainerListener类集合中移除指定的类名，将被添加到新创建的Wrapper.
     *
     * @param listener Class name of a ContainerListener class to be removed
     */
    @Override
    public void removeWrapperListener(String listener) {


        synchronized (wrapperListenersLock) {

            // Make sure this listener is currently present
            int n = -1;
            for (int i = 0; i < wrapperListeners.length; i++) {
                if (wrapperListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified listener
            int j = 0;
            String results[] = new String[wrapperListeners.length - 1];
            for (int i = 0; i < wrapperListeners.length; i++) {
                if (i != n)
                    results[j++] = wrapperListeners[i];
            }
            wrapperListeners = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeWrapperListener", listener);

    }


    /**
     * 获取StandardContext中所有servlet的累计处理时间.
     *
     * @return Cumulative 所有servlet的累计处理时间
     */
    public long getProcessingTime() {

        long result = 0;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                result += ((StandardWrapper)children[i]).getProcessingTime();
            }
        }

        return result;
    }

    /**
     * 获取所有servlet的最大处理时间.
     */
    public long getMaxTime() {

        long result = 0;
        long time;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                time = ((StandardWrapper)children[i]).getMaxTime();
                if (time > result)
                    result = time;
            }
        }

        return result;
    }

    /**
     * 获取所有servlet的最小处理时间.
     */
    public long getMinTime() {

        long result = -1;
        long time;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                time = ((StandardWrapper)children[i]).getMinTime();
                if (result < 0 || time < result)
                    result = time;
            }
        }

        return result;
    }

    /**
     * 获取所有servlet的累计请求计数.
     */
    public int getRequestCount() {

        int result = 0;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                result += ((StandardWrapper)children[i]).getRequestCount();
            }
        }

        return result;
    }

    /**
     * 获取所有servlet的累积错误计数.
     */
    public int getErrorCount() {

        int result = 0;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                result += ((StandardWrapper)children[i]).getErrorCount();
            }
        }

        return result;
    }


    /**
     * 返回给定路径的实际路径; 或者<code>null</code>.
     *
     * @param path 所需资源的路径
     */
    @Override
    public String getRealPath(String path) {
        // WebResources API 要求所有路径以 / 开头. 这是与早期Tomcat版本一致的特殊情况.
        if ("".equals(path)) {
            path = "/";
        }
        if (resources != null) {
            try {
                WebResource resource = resources.getResource(path);
                String canonicalPath = resource.getCanonicalPath();
                if (canonicalPath == null) {
                    return null;
                } else if ((resource.isDirectory() && !canonicalPath.endsWith(File.separator) ||
                        !resource.exists()) && path.endsWith("/")) {
                    return canonicalPath + File.separatorChar;
                } else {
                    return canonicalPath;
                }
            } catch (IllegalArgumentException iae) {
                // ServletContext.getRealPath() does not allow this to be thrown
            }
        }
        return null;
    }

    /**
     * 钩子来注册需要扫描的安全注解.
     * 
     * @param wrapper   添加的Servlet的wrapper
     * 
     * @return the associated registration
     */
    public ServletRegistration.Dynamic dynamicServletAdded(Wrapper wrapper) {
        Servlet s = wrapper.getServlet();
        if (s != null && createdServlets.contains(s)) {
            // 标记包装器以指示需要扫描的注解
            wrapper.setServletSecurityAnnotationScanRequired(true);
        }
        return new ApplicationServletRegistration(wrapper, this);
    }

    /**
     * 钩子跟踪哪些注册需要注解扫描
     * 
     * @param servlet 要添加的Servlet
     */
    public void dynamicServletCreated(Servlet servlet) {
        createdServlets.add(servlet);
    }


    /**
     * 在上下文中管理过滤器映射的帮助类.
     */
    private static final class ContextFilterMaps {
        private final Object lock = new Object();

        /**
         * 此应用程序的过滤器映射集合, 通过{@link ServletContext}添加的额外映射可能在部署描述符中定义的顺序之前或之后.
         */
        private FilterMap[] array = new FilterMap[0];

        /**
         * 通过{@link ServletContext}添加的过滤器映射必须位于部署描述符中定义的顺序之前, 但是必须在{@link ServletContext}方法调用之后.
         * 这不是部署描述符之后添加的映射的问题 - 它们只是增加到最后 - 但正确地添加映射, 在部署描述符映射需要知道最后一个'before'映射添加到哪里之前.
         */
        private int insertPoint = 0;

        /**
         * @return 过滤器映射集合
         */
        public FilterMap[] asArray() {
            synchronized (lock) {
                return array;
            }
        }

        /**
         * 在当前过滤器映射集合的末尾添加过滤器映射.
         *
         * @param filterMap 要添加的过滤器映射
         */
        public void add(FilterMap filterMap) {
            synchronized (lock) {
                FilterMap results[] = Arrays.copyOf(array, array.length + 1);
                results[array.length] = filterMap;
                array = results;
            }
        }

        /**
         * 添加过滤器映射, 在部署描述符定义的映射之前, 但在其他通过这个方法添加的映射之后.
         *
         * @param filterMap 要添加的过滤器映射
         */
        public void addBefore(FilterMap filterMap) {
            synchronized (lock) {
                FilterMap results[] = new FilterMap[array.length + 1];
                System.arraycopy(array, 0, results, 0, insertPoint);
                System.arraycopy(array, insertPoint, results, insertPoint + 1,
                        array.length - insertPoint);
                results[insertPoint] = filterMap;
                array = results;
                insertPoint++;
            }
        }

        /**
         * 移除过滤器映射.
         *
         * @param filterMap 要删除的过滤器映射
         */
        public void remove(FilterMap filterMap) {
            synchronized (lock) {
                // Make sure this filter mapping is currently present
                int n = -1;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == filterMap) {
                        n = i;
                        break;
                    }
                }
                if (n < 0)
                    return;

                // Remove the specified filter mapping
                FilterMap results[] = new FilterMap[array.length - 1];
                System.arraycopy(array, 0, results, 0, n);
                System.arraycopy(array, n + 1, results, n, (array.length - 1)
                        - n);
                array = results;
                if (n < insertPoint) {
                    insertPoint--;
                }
            }
        }
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 配置和初始化一组过滤器.
     * 返回<code>true</code> 如果所有过滤器初始化成功完成; 否则返回<code>false</code>.
     */
    public boolean filterStart() {

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Starting filters");
        }
        // 为每个定义的过滤器实例化并记录FilterConfig
        boolean ok = true;
        synchronized (filterConfigs) {
            filterConfigs.clear();
            for (Entry<String,FilterDef> entry : filterDefs.entrySet()) {
                String name = entry.getKey();
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug(" Starting filter '" + name + "'");
                }
                try {
                    ApplicationFilterConfig filterConfig =
                            new ApplicationFilterConfig(this, entry.getValue());
                    filterConfigs.put(name, filterConfig);
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    getLogger().error(sm.getString(
                            "standardContext.filterStart", name), t);
                    ok = false;
                }
            }
        }

        return ok;
    }


    /**
     * 结束并释放一组过滤器
     * 返回<code>true</code> 如果所有过滤器初始化成功完成; 否则返回<code>false</code>.
     */
    public boolean filterStop() {

        if (getLogger().isDebugEnabled())
            getLogger().debug("Stopping filters");

        // Release all Filter and FilterConfig instances
        synchronized (filterConfigs) {
            for (Entry<String, ApplicationFilterConfig> entry : filterConfigs.entrySet()) {
                if (getLogger().isDebugEnabled())
                    getLogger().debug(" Stopping filter '" + entry.getKey() + "'");
                ApplicationFilterConfig filterConfig = entry.getValue();
                filterConfig.release();
            }
            filterConfigs.clear();
        }
        return true;

    }


    /**
     * 查找并返回指定名称的初始化的<code>FilterConfig</code>; 或者返回<code>null</code>.
     *
     * @param name 所需过滤器的名称
     * 
     * @return the filter config object
     */
    public FilterConfig findFilterConfig(String name) {
        return (filterConfigs.get(name));
    }


    /**
     * 配置一组应用事件监听器.
     * 
     * 返回<code>true</code>如果所有监听器初始化成功,否则返回<code>false</code>.
     */
    public boolean listenerStart() {

        if (log.isDebugEnabled())
            log.debug("Configuring application event listeners");

        // Instantiate the required listeners
        String listeners[] = findApplicationListeners();
        Object results[] = new Object[listeners.length];
        boolean ok = true;
        for (int i = 0; i < results.length; i++) {
            if (getLogger().isDebugEnabled())
                getLogger().debug(" Configuring event listener class '" +
                    listeners[i] + "'");
            try {
                String listener = listeners[i];
                results[i] = getInstanceManager().newInstance(listener);
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
                getLogger().error(sm.getString(
                        "standardContext.applicationListener", listeners[i]), t);
                ok = false;
            }
        }
        if (!ok) {
            getLogger().error(sm.getString("standardContext.applicationSkipped"));
            return false;
        }

        // Sort listeners in two arrays
        ArrayList<Object> eventListeners = new ArrayList<>();
        ArrayList<Object> lifecycleListeners = new ArrayList<>();
        for (int i = 0; i < results.length; i++) {
            if ((results[i] instanceof ServletContextAttributeListener)
                || (results[i] instanceof ServletRequestAttributeListener)
                || (results[i] instanceof ServletRequestListener)
                || (results[i] instanceof HttpSessionIdListener)
                || (results[i] instanceof HttpSessionAttributeListener)) {
                eventListeners.add(results[i]);
            }
            if ((results[i] instanceof ServletContextListener)
                || (results[i] instanceof HttpSessionListener)) {
                lifecycleListeners.add(results[i]);
            }
        }

        // Listener实例可能已经被直接添加到这个, 通过ServletContextInitializer和通过可插拔的API.
        // 将这些监听器放入web.xml中定义的监听器或注解之后， 然后重写实例列表.
        for (Object eventListener: getApplicationEventListeners()) {
            eventListeners.add(eventListener);
        }
        setApplicationEventListeners(eventListeners.toArray());
        for (Object lifecycleListener: getApplicationLifecycleListeners()) {
            lifecycleListeners.add(lifecycleListener);
            if (lifecycleListener instanceof ServletContextListener) {
                noPluggabilityListeners.add(lifecycleListener);
            }
        }
        setApplicationLifecycleListeners(lifecycleListeners.toArray());

        // Send application start events

        if (getLogger().isDebugEnabled())
            getLogger().debug("Sending application start events");

        // Ensure context is not null
        getServletContext();
        context.setNewServletContextListenerAllowed(false);

        Object instances[] = getApplicationLifecycleListeners();
        if (instances == null || instances.length == 0) {
            return ok;
        }

        ServletContextEvent event = new ServletContextEvent(getServletContext());
        ServletContextEvent tldEvent = null;
        if (noPluggabilityListeners.size() > 0) {
            noPluggabilityServletContext = new NoPluggabilityServletContext(getServletContext());
            tldEvent = new ServletContextEvent(noPluggabilityServletContext);
        }
        for (int i = 0; i < instances.length; i++) {
            if (!(instances[i] instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) instances[i];
            try {
                fireContainerEvent("beforeContextInitialized", listener);
                if (noPluggabilityListeners.contains(listener)) {
                    listener.contextInitialized(tldEvent);
                } else {
                    listener.contextInitialized(event);
                }
                fireContainerEvent("afterContextInitialized", listener);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                fireContainerEvent("afterContextInitialized", listener);
                getLogger().error
                    (sm.getString("standardContext.listenerStart",
                                  instances[i].getClass().getName()), t);
                ok = false;
            }
        }
        return (ok);

    }


    /**
     * 发送一个应用停止事件给所有内部的监听器.
     * 返回<code>true</code>，如果所有事件发送成功, 否则返回<code>false</code>.
     */
    public boolean listenerStop() {

        if (log.isDebugEnabled())
            log.debug("Sending application stop events");

        boolean ok = true;
        Object listeners[] = getApplicationLifecycleListeners();
        if (listeners != null && listeners.length > 0) {
            ServletContextEvent event = new ServletContextEvent(getServletContext());
            ServletContextEvent tldEvent = null;
            if (noPluggabilityServletContext != null) {
                tldEvent = new ServletContextEvent(noPluggabilityServletContext);
            }
            for (int i = 0; i < listeners.length; i++) {
                int j = (listeners.length - 1) - i;
                if (listeners[j] == null)
                    continue;
                if (listeners[j] instanceof ServletContextListener) {
                    ServletContextListener listener =
                        (ServletContextListener) listeners[j];
                    try {
                        fireContainerEvent("beforeContextDestroyed", listener);
                        if (noPluggabilityListeners.contains(listener)) {
                            listener.contextDestroyed(tldEvent);
                        } else {
                            listener.contextDestroyed(event);
                        }
                        fireContainerEvent("afterContextDestroyed", listener);
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        fireContainerEvent("afterContextDestroyed", listener);
                        getLogger().error
                            (sm.getString("standardContext.listenerStop",
                                listeners[j].getClass().getName()), t);
                        ok = false;
                    }
                }
                try {
                    if (getInstanceManager() != null) {
                        getInstanceManager().destroyInstance(listeners[j]);
                    }
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    getLogger().error
                       (sm.getString("standardContext.listenerStop",
                            listeners[j].getClass().getName()), t);
                    ok = false;
                }
            }
        }

        // Annotation processing
        listeners = getApplicationEventListeners();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                int j = (listeners.length - 1) - i;
                if (listeners[j] == null)
                    continue;
                try {
                    if (getInstanceManager() != null) {
                        getInstanceManager().destroyInstance(listeners[j]);
                    }
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    getLogger().error
                        (sm.getString("standardContext.listenerStop",
                            listeners[j].getClass().getName()), t);
                    ok = false;
                }
            }
        }

        setApplicationEventListeners(null);
        setApplicationLifecycleListeners(null);

        noPluggabilityServletContext = null;
        noPluggabilityListeners.clear();

        return ok;
    }


    /**
     * 分配资源，包括代理.
     * 
     * @throws LifecycleException 如果启动错误
     */
    public void resourcesStart() throws LifecycleException {

        // 在已启动的资源的情况下检查当前状态
        if (!resources.getState().isAvailable()) {
            resources.start();
        }

        if (effectiveMajorVersion >=3 && addWebinfClassesResources) {
            WebResource webinfClassesResource = resources.getResource(
                    "/WEB-INF/classes/META-INF/resources");
            if (webinfClassesResource.isDirectory()) {
                getResources().createWebResourceSet(
                        WebResourceRoot.ResourceSetType.RESOURCE_JAR, "/",
                        webinfClassesResource.getURL(), "/");
            }
        }
    }


    /**
     * 释放资源并销毁代理.
     * 
     * @return <code>true</code>如果没有错误
     */
    public boolean resourcesStop() {

        boolean ok = true;

        Lock writeLock = resourcesLock.writeLock();
        writeLock.lock();
        try {
            if (resources != null) {
                resources.stop();
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardContext.resourcesStop"), t);
            ok = false;
        } finally {
            writeLock.unlock();
        }

        return ok;
    }


    /**
     * 加载并初始化所有servlet,在定义的时候标有"load on startup".
     *
     * @param children 所有当前定义的servlet包装器数组(包括未声明load on startup)
     * 
     * @return <code>true</code>如果load on startup被认为是成功的
     */
    public boolean loadOnStartup(Container children[]) {

        // 收集需要初始化的 "load on startup" servlet
        TreeMap<Integer, ArrayList<Wrapper>> map = new TreeMap<>();
        for (int i = 0; i < children.length; i++) {
            Wrapper wrapper = (Wrapper) children[i];
            int loadOnStartup = wrapper.getLoadOnStartup();
            if (loadOnStartup < 0)
                continue;
            Integer key = Integer.valueOf(loadOnStartup);
            ArrayList<Wrapper> list = map.get(key);
            if (list == null) {
                list = new ArrayList<>();
                map.put(key, list);
            }
            list.add(wrapper);
        }

        // 加载收集的"load on startup" servlets
        for (ArrayList<Wrapper> list : map.values()) {
            for (Wrapper wrapper : list) {
                try {
                    wrapper.load();
                } catch (ServletException e) {
                    getLogger().error(sm.getString("standardContext.loadOnStartup.loadException",
                          getName(), wrapper.getName()), StandardWrapper.getRootCause(e));
                    // NOTE: 加载错误(包括一个从init()方法抛出UnavailableException的servlet)不会对应用程序启动造成致命影响
                    // 除非指定了 failCtxIfServletStartFails="true"
                    if(getComputedFailCtxIfServletStartFails()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        if(log.isDebugEnabled())
            log.debug("Starting " + getBaseName());

        // Send j2ee.state.starting notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting",
                    this.getObjectName(), sequenceNumber.getAndIncrement());
            broadcaster.sendNotification(notification);
        }

        setConfigured(false);
        boolean ok = true;

        // 目前，这实际上是一个NO-OP，但需要被调用以确保NamingResource遵循正确的生命周期
        if (namingResources != null) {
            namingResources.start();
        }

        // Post work directory
        postWorkDirectory();

        // Add missing components as necessary
        if (getResources() == null) {   // (1) Required by Loader
            if (log.isDebugEnabled())
                log.debug("Configuring default Resources");

            try {
                setResources(new StandardRoot(this));
            } catch (IllegalArgumentException e) {
                log.error(sm.getString("standardContext.resourcesInit"), e);
                ok = false;
            }
        }
        if (ok) {
            resourcesStart();
        }

        if (getLoader() == null) {
            WebappLoader webappLoader = new WebappLoader(getParentClassLoader());
            webappLoader.setDelegate(getDelegate());
            setLoader(webappLoader);
        }

        // 未指定显式的Cookie处理器; 使用默认的
        if (cookieProcessor == null) {
            cookieProcessor = new Rfc6265CookieProcessor();
        }

        // 初始化字符集映射器
        getCharsetMapper();

        // 验证所需扩展
        boolean dependencyCheck = true;
        try {
            dependencyCheck = ExtensionValidator.validateApplication
                (getResources(), this);
        } catch (IOException ioe) {
            log.error(sm.getString("standardContext.extensionValidationError"), ioe);
            dependencyCheck = false;
        }

        if (!dependencyCheck) {
            // 如果相关性检查失败，则不允许应用程序可用
            ok = false;
        }

        // 读取"catalina.useNaming"环境变量
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }

        if (ok && isUseNaming()) {
            if (getNamingContextListener() == null) {
                NamingContextListener ncl = new NamingContextListener();
                ncl.setName(getNamingContextName());
                ncl.setExceptionOnFailedWrite(getJndiExceptionOnFailedWrite());
                addLifecycleListener(ncl);
                setNamingContextListener(ncl);
            }
        }

        // Standard container startup
        if (log.isDebugEnabled())
            log.debug("Processing standard container startup");


        // Binding thread
        ClassLoader oldCCL = bindThread();

        try {
            if (ok) {
                // Start our subordinate components, if any
                Loader loader = getLoader();
                if (loader instanceof Lifecycle) {
                    ((Lifecycle) loader).start();
                }

                // since the loader just started, the webapp classloader is now
                // created.
                setClassLoaderProperty("clearReferencesRmiTargets",
                        getClearReferencesRmiTargets());
                setClassLoaderProperty("clearReferencesStopThreads",
                        getClearReferencesStopThreads());
                setClassLoaderProperty("clearReferencesStopTimerThreads",
                        getClearReferencesStopTimerThreads());
                setClassLoaderProperty("clearReferencesHttpClientKeepAliveThread",
                        getClearReferencesHttpClientKeepAliveThread());

                // 通过调用 unbindThread 和 bindThread, 将当前线程CCL设置为WebApp类加载器
                unbindThread(oldCCL);
                oldCCL = bindThread();

                // 重新初始化记录器. 其他组件可能使用得太早，因此应该重置.
                logger = null;
                getLogger();

                Realm realm = getRealmInternal();
                if(null != realm) {
                    if (realm instanceof Lifecycle) {
                        ((Lifecycle) realm).start();
                    }

                    // 将 CredentialHandler 放入 ServletContext, 因此应用程序可以访问它.
                    // 包装它进一个"safe"处理器, 所以应用程序不能修改它.
                    CredentialHandler safeHandler = new CredentialHandler() {
                        @Override
                        public boolean matches(String inputCredentials, String storedCredentials) {
                            return getRealmInternal().getCredentialHandler().matches(inputCredentials, storedCredentials);
                        }

                        @Override
                        public String mutate(String inputCredentials) {
                            return getRealmInternal().getCredentialHandler().mutate(inputCredentials);
                        }
                    };
                    context.setAttribute(Globals.CREDENTIAL_HANDLER, safeHandler);
                }

                // Notify our interested LifecycleListeners
                fireLifecycleEvent(Lifecycle.CONFIGURE_START_EVENT, null);

                // Start our child containers, if not already started
                for (Container child : findChildren()) {
                    if (!child.getState().isAvailable()) {
                        child.start();
                    }
                }

                // Start the Valves in our pipeline (including the basic),
                if (pipeline instanceof Lifecycle) {
                    ((Lifecycle) pipeline).start();
                }

                // Acquire clustered manager
                Manager contextManager = null;
                Manager manager = getManager();
                if (manager == null) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("standardContext.cluster.noManager",
                                Boolean.valueOf((getCluster() != null)),
                                Boolean.valueOf(distributable)));
                    }
                    if ( (getCluster() != null) && distributable) {
                        try {
                            contextManager = getCluster().createManager(getName());
                        } catch (Exception ex) {
                            log.error("standardContext.clusterFail", ex);
                            ok = false;
                        }
                    } else {
                        contextManager = new StandardManager();
                    }
                }

                // 如果没有指定默认配置管理器
                if (contextManager != null) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("standardContext.manager",
                                contextManager.getClass().getName()));
                    }
                    setManager(contextManager);
                }

                if (manager!=null && (getCluster() != null) && distributable) {
                    // 让集群知道有一个可分发的上下文，并且它有自己的管理器
                    getCluster().registerManager(manager);
                }
            }

            if (!getConfigured()) {
                log.error(sm.getString("standardContext.configurationFail"));
                ok = false;
            }

            // We put the resources into the servlet context
            if (ok)
                getServletContext().setAttribute
                    (Globals.RESOURCES_ATTR, getResources());

            if (ok ) {
                if (getInstanceManager() == null) {
                    javax.naming.Context context = null;
                    if (isUseNaming() && getNamingContextListener() != null) {
                        context = getNamingContextListener().getEnvContext();
                    }
                    Map<String, Map<String, String>> injectionMap = buildInjectionMap(
                            getIgnoreAnnotations() ? new NamingResourcesImpl(): getNamingResources());
                    setInstanceManager(new DefaultInstanceManager(context,
                            injectionMap, this, this.getClass().getClassLoader()));
                }
                getServletContext().setAttribute(
                        InstanceManager.class.getName(), getInstanceManager());
                InstanceManagerBindings.bind(getLoader().getClassLoader(), getInstanceManager());
            }

            // Create context attributes that will be required
            if (ok) {
                getServletContext().setAttribute(
                        JarScanner.class.getName(), getJarScanner());
            }

            // Set up the context init params
            mergeParameters();

            // Call ServletContainerInitializers
            for (Map.Entry<ServletContainerInitializer, Set<Class<?>>> entry :
                initializers.entrySet()) {
                try {
                    entry.getKey().onStartup(entry.getValue(),
                            getServletContext());
                } catch (ServletException e) {
                    log.error(sm.getString("standardContext.sciFail"), e);
                    ok = false;
                    break;
                }
            }

            // Configure and call application event listeners
            if (ok) {
                if (!listenerStart()) {
                    log.error(sm.getString("standardContext.listenerFail"));
                    ok = false;
                }
            }

            // 检查未覆盖HTTP方法的约束
            // 需要在SCI和监听器之后，因为他们可以编程地改变约束
            if (ok) {
                checkConstraintsForUncoveredMethods(findConstraints());
            }

            try {
                // Start manager
                Manager manager = getManager();
                if (manager instanceof Lifecycle) {
                    ((Lifecycle) manager).start();
                }
            } catch(Exception e) {
                log.error(sm.getString("standardContext.managerFail"), e);
                ok = false;
            }

            // Configure and call application filters
            if (ok) {
                if (!filterStart()) {
                    log.error(sm.getString("standardContext.filterFail"));
                    ok = false;
                }
            }

            // Load and initialize all "load on startup" servlets
            if (ok) {
                if (!loadOnStartup(findChildren())){
                    log.error(sm.getString("standardContext.servletFail"));
                    ok = false;
                }
            }

            // Start ContainerBackgroundProcessor thread
            super.threadStart();
        } finally {
            // Unbinding thread
            unbindThread(oldCCL);
        }

        // 根据启动成功设置可用状态
        if (ok) {
            if (log.isDebugEnabled())
                log.debug("Starting completed");
        } else {
            log.error(sm.getString("standardContext.startFailed", getName()));
        }

        startTime=System.currentTimeMillis();

        // Send j2ee.state.running notification
        if (ok && (this.getObjectName() != null)) {
            Notification notification =
                new Notification("j2ee.state.running", this.getObjectName(),
                                 sequenceNumber.getAndIncrement());
            broadcaster.sendNotification(notification);
        }

        // WebResources实现类缓存到JAR文件的引用. 在某些平台上，这些引用可能会锁定JAR文件.
        // 因为Web应用程序启动可能会从大量的JAR中读取, 触发清理工作.
        getResources().gc();

        // 如果出错，重新初始化
        if (!ok) {
            setState(LifecycleState.FAILED);
        } else {
            setState(LifecycleState.STARTING);
        }
    }


    private void checkConstraintsForUncoveredMethods(
            SecurityConstraint[] constraints) {
        SecurityConstraint[] newConstraints =
                SecurityConstraint.findUncoveredHttpMethods(constraints,
                        getDenyUncoveredHttpMethods(), getLogger());
        for (SecurityConstraint constraint : newConstraints) {
            addConstraint(constraint);
        }
    }


    private void setClassLoaderProperty(String name, boolean value) {
        ClassLoader cl = getLoader().getClassLoader();
        if (!IntrospectionUtils.setProperty(cl, name, Boolean.toString(value))) {
            // Failed to set
            log.info(sm.getString(
                    "standardContext.webappClassLoader.missingProperty",
                    name, Boolean.toString(value)));
        }
    }

    private Map<String, Map<String, String>> buildInjectionMap(NamingResourcesImpl namingResources) {
        Map<String, Map<String, String>> injectionMap = new HashMap<>();
        for (Injectable resource: namingResources.findLocalEjbs()) {
            addInjectionTarget(resource, injectionMap);
        }
        for (Injectable resource: namingResources.findEjbs()) {
            addInjectionTarget(resource, injectionMap);
        }
        for (Injectable resource: namingResources.findEnvironments()) {
            addInjectionTarget(resource, injectionMap);
        }
        for (Injectable resource: namingResources.findMessageDestinationRefs()) {
            addInjectionTarget(resource, injectionMap);
        }
        for (Injectable resource: namingResources.findResourceEnvRefs()) {
            addInjectionTarget(resource, injectionMap);
        }
        for (Injectable resource: namingResources.findResources()) {
            addInjectionTarget(resource, injectionMap);
        }
        for (Injectable resource: namingResources.findServices()) {
            addInjectionTarget(resource, injectionMap);
        }
        return injectionMap;
    }

    private void addInjectionTarget(Injectable resource, Map<String, Map<String, String>> injectionMap) {
        List<InjectionTarget> injectionTargets = resource.getInjectionTargets();
        if (injectionTargets != null && injectionTargets.size() > 0) {
            String jndiName = resource.getName();
            for (InjectionTarget injectionTarget: injectionTargets) {
                String clazz = injectionTarget.getTargetClass();
                Map<String, String> injections = injectionMap.get(clazz);
                if (injections == null) {
                    injections = new HashMap<>();
                    injectionMap.put(clazz, injections);
                }
                injections.put(injectionTarget.getTargetName(), jndiName);
            }
        }
    }



    /**
     * 将应用程序部署描述符中指定的上下文初始化参数与服务器配置中描述的应用程序参数进行合并, 尤其注意应用参数的<code>override</code>属性.
     */
    private void mergeParameters() {
        Map<String,String> mergedParams = new HashMap<>();

        String names[] = findParameters();
        for (int i = 0; i < names.length; i++) {
            mergedParams.put(names[i], findParameter(names[i]));
        }

        ApplicationParameter params[] = findApplicationParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].getOverride()) {
                if (mergedParams.get(params[i].getName()) == null) {
                    mergedParams.put(params[i].getName(),
                            params[i].getValue());
                }
            } else {
                mergedParams.put(params[i].getName(), params[i].getValue());
            }
        }

        ServletContext sc = getServletContext();
        for (Map.Entry<String,String> entry : mergedParams.entrySet()) {
            sc.setInitParameter(entry.getKey(), entry.getValue());
        }

    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        // Send j2ee.state.stopping notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.stopping", this.getObjectName(),
                                 sequenceNumber.getAndIncrement());
            broadcaster.sendNotification(notification);
        }

        setState(LifecycleState.STOPPING);

        // Binding thread
        ClassLoader oldCCL = bindThread();

        try {
            // Stop our child containers, if any
            final Container[] children = findChildren();

            // Stop ContainerBackgroundProcessor thread
            threadStop();

            for (int i = 0; i < children.length; i++) {
                children[i].stop();
            }

            // Stop our filters
            filterStop();

            Manager manager = getManager();
            if (manager instanceof Lifecycle && ((Lifecycle) manager).getState().isAvailable()) {
                ((Lifecycle) manager).stop();
            }

            // Stop our application listeners
            listenerStop();

            // 结束字符集映射器
            setCharsetMapper(null);

            // 正常容器关闭处理
            if (log.isDebugEnabled())
                log.debug("Processing standard container shutdown");

            // JNDI资源被绑定到 CONFIGURE_STOP_EVENT, 因此在它们被绑定之前停止命名资源,
            // 因为NamingResources查找 JNDI来检索. 这需要在应用程序完成资源后完成
            if (namingResources != null) {
                namingResources.stop();
            }

            fireLifecycleEvent(Lifecycle.CONFIGURE_STOP_EVENT, null);

            // Stop the Valves in our pipeline (including the basic)
            if (pipeline instanceof Lifecycle &&
                    ((Lifecycle) pipeline).getState().isAvailable()) {
                ((Lifecycle) pipeline).stop();
            }

            // Clear all application-originated servlet context attributes
            if (context != null)
                context.clearAttributes();

            Realm realm = getRealmInternal();
            if (realm instanceof Lifecycle) {
                ((Lifecycle) realm).stop();
            }
            Loader loader = getLoader();
            if (loader instanceof Lifecycle) {
                ClassLoader classLoader = loader.getClassLoader();
                ((Lifecycle) loader).stop();
                if (classLoader != null) {
                    InstanceManagerBindings.unbind(classLoader);
                }
            }

            // Stop resources
            resourcesStop();
        } finally {

            // Unbinding thread
            unbindThread(oldCCL);
        }

        // Send j2ee.state.stopped notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.stopped", this.getObjectName(),
                                sequenceNumber.getAndIncrement());
            broadcaster.sendNotification(notification);
        }

        // Reset application context
        context = null;

        // 此对象将不再可见或使用.
        try {
            resetContext();
        } catch( Exception ex ) {
            log.error( "Error resetting context " + this + " " + ex, ex );
        }

        //reset the instance manager
        setInstanceManager(null);

        if (log.isDebugEnabled())
            log.debug("Stopping complete");

    }

    /** 
     * 销毁需要彻底清理上下文.
     * 
     * XXX Should this be done in stop() ?
     */
    @Override
    protected void destroyInternal() throws LifecycleException {

        // 如果状态是NEW, 当调用 destroy的时候, 从未设置对象名称，因此无法创建通知
        if (getObjectName() != null) {
            // Send j2ee.object.deleted notification
            Notification notification =
                new Notification("j2ee.object.deleted", this.getObjectName(),
                                 sequenceNumber.getAndIncrement());
            broadcaster.sendNotification(notification);
        }

        if (namingResources != null) {
            namingResources.destroy();
        }

        Loader loader = getLoader();
        if (loader instanceof Lifecycle) {
            ((Lifecycle) loader).destroy();
        }

        Manager manager = getManager();
        if (manager instanceof Lifecycle) {
            ((Lifecycle) manager).destroy();
        }

        if (resources != null) {
            resources.destroy();
        }

        super.destroyInternal();
    }


    @Override
    public void backgroundProcess() {

        if (!getState().isAvailable())
            return;

        Loader loader = getLoader();
        if (loader != null) {
            try {
                loader.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString(
                        "standardContext.backgroundProcess.loader", loader), e);
            }
        }
        Manager manager = getManager();
        if (manager != null) {
            try {
                manager.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString(
                        "standardContext.backgroundProcess.manager", manager),
                        e);
            }
        }
        WebResourceRoot resources = getResources();
        if (resources != null) {
            try {
                resources.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString(
                        "standardContext.backgroundProcess.resources",
                        resources), e);
            }
        }
        InstanceManager instanceManager = getInstanceManager();
        if (instanceManager instanceof DefaultInstanceManager) {
            try {
                ((DefaultInstanceManager)instanceManager).backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString(
                        "standardContext.backgroundProcess.instanceManager",
                        resources), e);
            }
        }
        super.backgroundProcess();
    }


    private void resetContext() throws Exception {
    	// 还原原始状态(启动时预读取 web.xml)
        // 如果你继承这个 - 覆盖此方法并确保清理

        // 不要重置从<Context.../>读取的任何东西, 因为<Context .../>元素是在初始化的时候读取的, 以后不会再读取
        for (Container child : findChildren()) {
            removeChild(child);
        }
        startupTime = 0;
        startTime = 0;
        tldScanTime = 0;

        // Bugzilla 32867
        distributable = false;

        applicationListeners = new String[0];
        applicationEventListenersList.clear();
        applicationLifecycleListenersObjects = new Object[0];
        jspConfigDescriptor = null;

        initializers.clear();

        createdServlets.clear();

        postConstructMethods.clear();
        preDestroyMethods.clear();

        if(log.isDebugEnabled())
            log.debug("resetContext " + getObjectName());
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 调整URL模式以一个斜杠开始, 如果合适的话 (即我们正在运行servlet 2.2应用程序). 
     * 否则，返回指定的URL模式不变.
     *
     * @param urlPattern 要调整和返回的URL模式
     * 
     * @return 包含斜杠的 URL模式
     */
    protected String adjustURLPattern(String urlPattern) {

        if (urlPattern == null)
            return (urlPattern);
        if (urlPattern.startsWith("/") || urlPattern.startsWith("*."))
            return (urlPattern);
        if (!isServlet22())
            return (urlPattern);
        if(log.isDebugEnabled())
            log.debug(sm.getString("standardContext.urlPattern.patternWarning",
                         urlPattern));
        return ("/" + urlPattern);

    }


    /**
     * 正在处理一个版本2.2的部署描述符吗?
     *
     * @return <code>true</code>如果运行遗留的servlet 2.2应用程序
     */
    @Override
    public boolean isServlet22() {
        return XmlIdentifiers.WEB_22_PUBLIC.equals(publicId);
    }


    @Override
    public Set<String> addServletSecurity(ServletRegistration.Dynamic registration,
            ServletSecurityElement servletSecurityElement) {

        Set<String> conflicts = new HashSet<>();

        Collection<String> urlPatterns = registration.getMappings();
        for (String urlPattern : urlPatterns) {
            boolean foundConflict = false;

            SecurityConstraint[] securityConstraints =
                findConstraints();
            for (SecurityConstraint securityConstraint : securityConstraints) {

                SecurityCollection[] collections =
                    securityConstraint.findCollections();
                for (SecurityCollection collection : collections) {
                    if (collection.findPattern(urlPattern)) {
                        // 找到的第一个模式将指示是否存在冲突，因为对于任何给定的模式，所有匹配约束都将来自描述符或非描述符. 不允许混合
                        if (collection.isFromDescriptor()) {
                            // Skip this pattern
                            foundConflict = true;
                            conflicts.add(urlPattern);
                            break;
                        } else {
                            // 需要覆盖此模式的约束
                            collection.removePattern(urlPattern);
                            // If the collection is now empty, remove it
                            if (collection.findPatterns().length == 0) {
                                securityConstraint.removeCollection(collection);
                            }
                        }
                    }
                }

                // If the constraint now has no collections - remove it
                if (securityConstraint.findCollections().length == 0) {
                    removeConstraint(securityConstraint);
                }

                // 一旦发现冲突，就不需要检查当前模式的其他约束条件
                if (foundConflict) {
                    break;
                }
            }

            // Note: 对于编程添加的servlet，这可能不是完整的安全约束集，因为在应用程序调用setSecurity之后，可以添加附加的URL模式.
            //       对于所有以编程方式添加的servlet, #dynamicServletAdded()方法设置一个标志，确保在首次使用servlet之前重新评估约束

            // 如果模式没有冲突, 添加新的 constraint(s).
            if (!foundConflict) {
                SecurityConstraint[] newSecurityConstraints =
                        SecurityConstraint.createConstraints(
                                servletSecurityElement,
                                urlPattern);
                for (SecurityConstraint securityConstraint :
                        newSecurityConstraints) {
                    addConstraint(securityConstraint);
                }

                checkConstraintsForUncoveredMethods(newSecurityConstraints);
            }
        }
        return conflicts;
    }


    /**
     * 绑定当前线程, 对CL目的和JNDI ENC的支持 : 上下文的startup, shutdown, realoading.
     *
     * @return 前一个上下文类加载器
     */
    protected ClassLoader bindThread() {

        ClassLoader oldContextClassLoader = bind(false, null);

        if (isUseNaming()) {
            try {
                ContextBindings.bindThread(this, getNamingToken());
            } catch (NamingException e) {
                // Silent catch, 这是早期启动阶段的正常情况
            }
        }

        return oldContextClassLoader;
    }


    /**
     * 取消绑定线程并恢复指定的上下文类加载器.
     *
     * @param oldContextClassLoader 以前的类加载器
     */
    protected void unbindThread(ClassLoader oldContextClassLoader) {

        if (isUseNaming()) {
            ContextBindings.unbindThread(this, getNamingToken());
        }

        unbind(false, oldContextClassLoader);
    }


    @Override
    public ClassLoader bind(boolean usePrivilegedAction, ClassLoader originalClassLoader) {
        Loader loader = getLoader();
        ClassLoader webApplicationClassLoader = null;
        if (loader != null) {
            webApplicationClassLoader = loader.getClassLoader();
        }

        if (originalClassLoader == null) {
            if (usePrivilegedAction) {
                PrivilegedAction<ClassLoader> pa = new PrivilegedGetTccl();
                originalClassLoader = AccessController.doPrivileged(pa);
            } else {
                originalClassLoader = Thread.currentThread().getContextClassLoader();
            }
        }

        if (webApplicationClassLoader == null ||
                webApplicationClassLoader == originalClassLoader) {
            // 不可能或不需要切换类加载器. 返回null.
            return null;
        }

        ThreadBindingListener threadBindingListener = getThreadBindingListener();

        if (usePrivilegedAction) {
            PrivilegedAction<Void> pa = new PrivilegedSetTccl(webApplicationClassLoader);
            AccessController.doPrivileged(pa);
        } else {
            Thread.currentThread().setContextClassLoader(webApplicationClassLoader);
        }
        if (threadBindingListener != null) {
            try {
                threadBindingListener.bind();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString(
                        "standardContext.threadBindingListenerError", getName()), t);
            }
        }

        return originalClassLoader;
    }


    @Override
    public void unbind(boolean usePrivilegedAction, ClassLoader originalClassLoader) {
        if (originalClassLoader == null) {
            return;
        }

        if (threadBindingListener != null) {
            try {
                threadBindingListener.unbind();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString(
                        "standardContext.threadBindingListenerError", getName()), t);
            }
        }

        if (usePrivilegedAction) {
            PrivilegedAction<Void> pa = new PrivilegedSetTccl(originalClassLoader);
            AccessController.doPrivileged(pa);
        } else {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }


    /**
     * 获取命名上下文全名.
     *
     * @return the context name
     */
    private String getNamingContextName() {
        if (namingContextName == null) {
            Container parent = getParent();
            if (parent == null) {
            namingContextName = getName();
            } else {
            Stack<String> stk = new Stack<>();
            StringBuilder buff = new StringBuilder();
            while (parent != null) {
                stk.push(parent.getName());
                parent = parent.getParent();
            }
            while (!stk.empty()) {
                buff.append("/" + stk.pop());
            }
            buff.append(getName());
            namingContextName = buff.toString();
            }
        }
        return namingContextName;
    }


    /**
     * 命名上下文监听器.
     */
    public NamingContextListener getNamingContextListener() {
        return namingContextListener;
    }


    /**
     * 命名上下文监听器.
     *
     * @param namingContextListener the new naming context listener
     */
    public void setNamingContextListener(NamingContextListener namingContextListener) {
        this.namingContextListener = namingContextListener;
    }


    /**
     * @return 请求处理暂停标志.
     */
    @Override
    public boolean getPaused() {
        return (this.paused);
    }


    @Override
    public boolean fireRequestInitEvent(ServletRequest request) {

        Object instances[] = getApplicationEventListeners();

        if ((instances != null) && (instances.length > 0)) {

            ServletRequestEvent event =
                    new ServletRequestEvent(getServletContext(), request);

            for (int i = 0; i < instances.length; i++) {
                if (instances[i] == null)
                    continue;
                if (!(instances[i] instanceof ServletRequestListener))
                    continue;
                ServletRequestListener listener =
                    (ServletRequestListener) instances[i];

                try {
                    listener.requestInitialized(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLogger().error(sm.getString(
                            "standardContext.requestListener.requestInit",
                            instances[i].getClass().getName()), t);
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
                    return false;
                }
            }
        }
        return true;
    }


    @Override
    public boolean fireRequestDestroyEvent(ServletRequest request) {
        Object instances[] = getApplicationEventListeners();

        if ((instances != null) && (instances.length > 0)) {

            ServletRequestEvent event =
                new ServletRequestEvent(getServletContext(), request);

            for (int i = 0; i < instances.length; i++) {
                int j = (instances.length -1) -i;
                if (instances[j] == null)
                    continue;
                if (!(instances[j] instanceof ServletRequestListener))
                    continue;
                ServletRequestListener listener =
                    (ServletRequestListener) instances[j];

                try {
                    listener.requestDestroyed(event);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLogger().error(sm.getString(
                            "standardContext.requestListener.requestInit",
                            instances[j].getClass().getName()), t);
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
                    return false;
                }
            }
        }
        return true;
    }


    @Override
    public void addPostConstructMethod(String clazz, String method) {
        if (clazz == null || method == null)
            throw new IllegalArgumentException(
                    sm.getString("standardContext.postconstruct.required"));
        if (postConstructMethods.get(clazz) != null)
            throw new IllegalArgumentException(sm.getString(
                    "standardContext.postconstruct.duplicate", clazz));

        postConstructMethods.put(clazz, method);
        fireContainerEvent("addPostConstructMethod", clazz);
    }


    @Override
    public void removePostConstructMethod(String clazz) {
        postConstructMethods.remove(clazz);
        fireContainerEvent("removePostConstructMethod", clazz);
    }


    @Override
    public void addPreDestroyMethod(String clazz, String method) {
        if (clazz == null || method == null)
            throw new IllegalArgumentException(
                    sm.getString("standardContext.predestroy.required"));
        if (preDestroyMethods.get(clazz) != null)
            throw new IllegalArgumentException(sm.getString(
                    "standardContext.predestroy.duplicate", clazz));

        preDestroyMethods.put(clazz, method);
        fireContainerEvent("addPreDestroyMethod", clazz);
    }


    @Override
    public void removePreDestroyMethod(String clazz) {
        preDestroyMethods.remove(clazz);
        fireContainerEvent("removePreDestroyMethod", clazz);
    }


    @Override
    public String findPostConstructMethod(String clazz) {
        return postConstructMethods.get(clazz);
    }


    @Override
    public String findPreDestroyMethod(String clazz) {
        return preDestroyMethods.get(clazz);
    }


    @Override
    public Map<String, String> findPostConstructMethods() {
        return postConstructMethods;
    }


    @Override
    public Map<String, String> findPreDestroyMethods() {
        return preDestroyMethods;
    }


    /**
     * 为工作目录设置适当的上下文属性.
     */
    private void postWorkDirectory() {

        // 获取（或计算）工作目录路径
        String workDir = getWorkDir();
        if (workDir == null || workDir.length() == 0) {

            // 检索父级(通常是一个主机)名称
            String hostName = null;
            String engineName = null;
            String hostWorkDir = null;
            Container parentHost = getParent();
            if (parentHost != null) {
                hostName = parentHost.getName();
                if (parentHost instanceof StandardHost) {
                    hostWorkDir = ((StandardHost)parentHost).getWorkDir();
                }
                Container parentEngine = parentHost.getParent();
                if (parentEngine != null) {
                   engineName = parentEngine.getName();
                }
            }
            if ((hostName == null) || (hostName.length() < 1))
                hostName = "_";
            if ((engineName == null) || (engineName.length() < 1))
                engineName = "_";

            String temp = getBaseName();
            if (temp.startsWith("/"))
                temp = temp.substring(1);
            temp = temp.replace('/', '_');
            temp = temp.replace('\\', '_');
            if (temp.length() < 1)
                temp = ContextName.ROOT_NAME;
            if (hostWorkDir != null ) {
                workDir = hostWorkDir + File.separator + temp;
            } else {
                workDir = "work" + File.separator + engineName +
                    File.separator + hostName + File.separator + temp;
            }
            setWorkDir(workDir);
        }

        // 创建目录
        File dir = new File(workDir);
        if (!dir.isAbsolute()) {
            String catalinaHomePath = null;
            try {
                catalinaHomePath = getCatalinaBase().getCanonicalPath();
                dir = new File(catalinaHomePath, workDir);
            } catch (IOException e) {
                log.warn(sm.getString("standardContext.workCreateException",
                        workDir, catalinaHomePath, getName()), e);
            }
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            log.warn(sm.getString("standardContext.workCreateFail", dir,
                    getName()));
        }

        // 设置适当的servlet上下文属性
        if (context == null) {
            getServletContext();
        }
        context.setAttribute(ServletContext.TEMPDIR, dir);
        context.setAttributeReadOnly(ServletContext.TEMPDIR);
    }


    /**
     * 设置请求处理暂停标志.
     *
     * @param paused 请求处理暂停标志
     */
    private void setPaused(boolean paused) {
        this.paused = paused;
    }


    /**
     * 验证提议的语法 <code>&lt;url-pattern&gt;</code>是否符合规格要求.
     *
     * @param urlPattern 要验证的URL模式
     * 
     * @return <code>true</code> 如果URL模式是符合的
     */
    private boolean validateURLPattern(String urlPattern) {

        if (urlPattern == null)
            return false;
        if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
            return false;
        }
        if (urlPattern.equals("")) {
            return true;
        }
        if (urlPattern.startsWith("*.")) {
            if (urlPattern.indexOf('/') < 0) {
                checkUnusualURLPattern(urlPattern);
                return true;
            } else
                return false;
        }
        if ( (urlPattern.startsWith("/")) &&
                (urlPattern.indexOf("*.") < 0)) {
            checkUnusualURLPattern(urlPattern);
            return true;
        } else
            return false;

    }


    /**
     * 检查异常情况，除了有效的<code>&lt;url-pattern&gt;</code>.
     * See Bugzilla 34805, 43079 & 43080
     */
    private void checkUnusualURLPattern(String urlPattern) {
        if (log.isInfoEnabled()) {
            // First group checks for '*' or '/foo*' style patterns
            // Second group checks for *.foo.bar style patterns
            if((urlPattern.endsWith("*") && (urlPattern.length() < 2 ||
                        urlPattern.charAt(urlPattern.length()-2) != '/')) ||
                    urlPattern.startsWith("*.") && urlPattern.length() > 2 &&
                        urlPattern.lastIndexOf('.') > 1) {
                log.info("Suspicious url pattern: \"" + urlPattern + "\"" +
                        " in context [" + getName() + "] - see" +
                        " sections 12.1 and 12.2 of the Servlet specification");
            }
        }
    }


    // ------------------------------------------------------------- Operations

    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties =
            new StringBuilder("j2eeType=WebModule,");
        keyProperties.append(getObjectKeyPropertiesNameOnly());
        keyProperties.append(",J2EEApplication=");
        keyProperties.append(getJ2EEApplication());
        keyProperties.append(",J2EEServer=");
        keyProperties.append(getJ2EEServer());

        return keyProperties.toString();
    }

    private String getObjectKeyPropertiesNameOnly() {
        StringBuilder result = new StringBuilder("name=//");
        String hostname = getParent().getName();
        if (hostname == null) {
            result.append("DEFAULT");
        } else {
            result.append(hostname);
        }

        String contextName = getName();
        if (!contextName.startsWith("/")) {
            result.append('/');
        }
        result.append(contextName);

        return result.toString();
    }

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        // Register the naming resources
        if (namingResources != null) {
            namingResources.init();
        }

        // Send j2ee.object.created notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.object.created",
                    this.getObjectName(), sequenceNumber.getAndIncrement());
            broadcaster.sendNotification(notification);
        }
    }


    /* Remove a JMX notificationListener
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    @Override
    public void removeNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object object) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener,filter,object);
    }

    private MBeanNotificationInfo[] notificationInfo;

    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        // FIXME: i18n
        if(notificationInfo == null) {
            notificationInfo = new MBeanNotificationInfo[]{
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.created"},
                    Notification.class.getName(),
                    "web application is created"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.starting"},
                    Notification.class.getName(),
                    "change web application is starting"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.running"},
                    Notification.class.getName(),
                    "web application is running"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.stopping"},
                    Notification.class.getName(),
                    "web application start to stopped"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.stopped"},
                    Notification.class.getName(),
                    "web application is stopped"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.deleted"},
                    Notification.class.getName(),
                    "web application is deleted"
                    )
            };

        }

        return notificationInfo;
    }


    /**
     * Add a JMX NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    @Override
    public void addNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object object) throws IllegalArgumentException {
        broadcaster.addNotificationListener(listener,filter,object);
    }


    /**
     * Remove a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    @Override
    public void removeNotificationListener(NotificationListener listener)
    throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }


    // ------------------------------------------------------------- Attributes

    /**
     * @return the naming resources associated with this web application.
     */
    public String[] getWelcomeFiles() {

        return findWelcomeFiles();

    }


    @Override
    public boolean getXmlNamespaceAware() {
        return webXmlNamespaceAware;
    }


    @Override
    public void setXmlNamespaceAware(boolean webXmlNamespaceAware) {
        this.webXmlNamespaceAware = webXmlNamespaceAware;
    }


    @Override
    public void setXmlValidation(boolean webXmlValidation) {
        this.webXmlValidation = webXmlValidation;
    }


    @Override
    public boolean getXmlValidation() {
        return webXmlValidation;
    }


    @Override
    public void setXmlBlockExternal(boolean xmlBlockExternal) {
        this.xmlBlockExternal = xmlBlockExternal;
    }


    @Override
    public boolean getXmlBlockExternal() {
        return xmlBlockExternal;
    }


    @Override
    public void setTldValidation(boolean tldValidation) {
        this.tldValidation = tldValidation;
    }


    @Override
    public boolean getTldValidation() {
        return tldValidation;
    }


    /**
     * 该模块部署在的J2EE Server ObjectName.
     */
    private String server = null;

    /**
     * 该模块运行的Java虚拟机.
     */
    private String[] javaVMs = null;

    public String getServer() {
        return server;
    }

    public String setServer(String server) {
        return this.server=server;
    }

    public String[] getJavaVMs() {
        return javaVMs;
    }

    public String[] setJavaVMs(String[] javaVMs) {
        return this.javaVMs = javaVMs;
    }

    /**
     * 获取此上下文启动的时间.
     *
     * @return Time (in milliseconds since January 1, 1970, 00:00:00)当这个上下文启动时
     */
    public long getStartTime() {
        return startTime;
    }


    @SuppressWarnings("deprecation")
    private static class NoPluggabilityServletContext
            implements javax.servlet.ServletContext {

        private final ServletContext sc;

        public NoPluggabilityServletContext(ServletContext sc) {
            this.sc = sc;
        }

        @Override
        public String getContextPath() {
            return sc.getContextPath();
        }

        @Override
        public ServletContext getContext(String uripath) {
            return sc.getContext(uripath);
        }

        @Override
        public int getMajorVersion() {
           return sc.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return sc.getMinorVersion();
        }

        @Override
        public int getEffectiveMajorVersion() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public int getEffectiveMinorVersion() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public String getMimeType(String file) {
            return sc.getMimeType(file);
        }

        @Override
        public Set<String> getResourcePaths(String path) {
            return sc.getResourcePaths(path);
        }

        @Override
        public URL getResource(String path) throws MalformedURLException {
            return sc.getResource(path);
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            return sc.getResourceAsStream(path);
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return sc.getRequestDispatcher(path);
        }

        @Override
        public RequestDispatcher getNamedDispatcher(String name) {
            return sc.getNamedDispatcher(name);
        }

        @Override
        public void log(String msg) {
            sc.log(msg);
        }

        @Override
        public void log(String message, Throwable throwable) {
            sc.log(message, throwable);
        }

        @Override
        public String getRealPath(String path) {
            return sc.getRealPath(path);
        }

        @Override
        public String getServerInfo() {
            return sc.getServerInfo();
        }

        @Override
        public String getInitParameter(String name) {
            return sc.getInitParameter(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return sc.getInitParameterNames();
        }

        @Override
        public boolean setInitParameter(String name, String value) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public Object getAttribute(String name) {
            return sc.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return sc.getAttributeNames();
        }

        @Override
        public void setAttribute(String name, Object object) {
            sc.setAttribute(name, object);
        }

        @Override
        public void removeAttribute(String name) {
            sc.removeAttribute(name);
        }

        @Override
        public String getServletContextName() {
            return sc.getServletContextName();
        }

        @Override
        public Dynamic addServlet(String servletName, String className) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public Dynamic addServlet(String servletName, Servlet servlet) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public Dynamic addServlet(String servletName,
                Class<? extends Servlet> servletClass) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public <T extends Servlet> T createServlet(Class<T> c)
                throws ServletException {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public ServletRegistration getServletRegistration(String servletName) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public Map<String,? extends ServletRegistration> getServletRegistrations() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public javax.servlet.FilterRegistration.Dynamic addFilter(
                String filterName, String className) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public javax.servlet.FilterRegistration.Dynamic addFilter(
                String filterName, Filter filter) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public javax.servlet.FilterRegistration.Dynamic addFilter(
                String filterName, Class<? extends Filter> filterClass) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public <T extends Filter> T createFilter(Class<T> c)
                throws ServletException {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public FilterRegistration getFilterRegistration(String filterName) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public Map<String,? extends FilterRegistration> getFilterRegistrations() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public void setSessionTrackingModes(
                Set<SessionTrackingMode> sessionTrackingModes) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public void addListener(String className) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public <T extends EventListener> void addListener(T t) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public void addListener(Class<? extends EventListener> listenerClass) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public <T extends EventListener> T createListener(Class<T> c)
                throws ServletException {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public ClassLoader getClassLoader() {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public void declareRoles(String... roleNames) {
            throw new UnsupportedOperationException(
                    sm.getString("noPluggabilityServletContext.notAllowed"));
        }

        @Override
        public String getVirtualServerName() {
            return sc.getVirtualServerName();
        }
    }
}
