package org.apache.catalina.core;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletSecurityElement;
import javax.servlet.UnavailableException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.ServletSecurity;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.security.SecurityUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.PeriodicEventListener;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.modeler.Util;

/**
 * <b>Wrapper</b>接口的标准实现类表示单个servlet定义. 
 * 不允许有子级Containers, 父级Container必须是一个Context.
 */
@SuppressWarnings("deprecation") // SingleThreadModel
public class StandardWrapper extends ContainerBase
    implements ServletConfig, Wrapper, NotificationEmitter {

    private static final Log log = LogFactory.getLog(StandardWrapper.class);

    protected static final String[] DEFAULT_SERVLET_METHODS = new String[] {
                                                    "GET", "HEAD", "POST" };

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardWrapper component with the default basic Valve.
     */
    public StandardWrapper() {
        super();
        swValve=new StandardWrapperValve();
        pipeline.setBasic(swValve);
        broadcaster = new NotificationBroadcasterSupport();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此servlet将可用的日期和时间 (毫秒), 如果servlet可用，则为零.
     * 如果这个值等于Long.MAX_VALUE, 这个servlet的不可用性被认为是永久性的.
     */
    protected long available = 0L;

    /**
     * 用于发送 j2ee 通知. 
     */
    protected final NotificationBroadcasterSupport broadcaster;

    /**
     * 当前活动的分配数(即使它们是相同的实例，在非STM servlet上也是如此).
     */
    protected final AtomicInteger countAllocated = new AtomicInteger(0);


    /**
     * 关联的外观模式.
     */
    protected final StandardWrapperFacade facade = new StandardWrapperFacade(this);


    /**
     * 这个servlet的实例.
     */
    protected volatile Servlet instance = null;


    /**
     * 这个实例是否已初始化
     */
    protected volatile boolean instanceInitialized = false;


    /**
     * load-on-startup加载顺序值(负值表示第一个调用).
     */
    protected int loadOnStartup = -1;


    /**
     * 映射.
     */
    protected final ArrayList<String> mappings = new ArrayList<>();


    /**
     * 这个servlet的初始化参数, 使用参数名作为key.
     */
    protected HashMap<String, String> parameters = new HashMap<>();


    /**
     * 此servlet的安全角色引用, 使用角色名作为key.
     * 相应的值是Web应用程序本身的角色名.
     */
    protected HashMap<String, String> references = new HashMap<>();


    /**
     * run-as标识符
     */
    protected String runAs = null;

    /**
     * 通知序列号.
     */
    protected long sequenceNumber = 0;

    /**
     * 完全限定的servlet类名.
     */
    protected String servletClass = null;


    /**
     * 这个servlet是否实现了SingleThreadModel接口?
     */
    protected volatile boolean singleThreadModel = false;


    /**
     * 正在卸载servlet实例吗?
     */
    protected volatile boolean unloading = false;


    /**
     * STM实例的最大数目
     */
    protected int maxInstances = 20;


    /**
     * 一个STM servlet当前加载的实例数.
     */
    protected int nInstances = 0;


    /**
     * 包含STM实例的堆栈.
     */
    protected Stack<Servlet> instancePool = null;


    /**
     * servlet卸载的等待时间, 毫秒.
     */
    protected long unloadDelay = 2000;


    /**
     * True, 如果是 JspServlet
     */
    protected boolean isJspServlet;


    /**
     * JSP 监测 mbean的ObjectName
     */
    protected ObjectName jspMonitorON;


    /**
     * 是否忽略 System.out
     */
    protected boolean swallowOutput = false;

    // To support jmx attributes
    protected StandardWrapperValve swValve;
    protected long loadTime=0;
    protected int classLoadTime=0;

    /**
     * Multipart config
     */
    protected MultipartConfigElement multipartConfigElement = null;

    /**
     * Async support
     */
    protected boolean asyncSupported = false;

    /**
     * Enabled
     */
    protected boolean enabled = true;

    protected volatile boolean servletSecurityAnnotationScanRequired = false;

    private boolean overridable = false;

    /**
     * 当启用SecurityManager并调用<code>Servlet.init</code>的时候调用.
     */
    protected static Class<?>[] classType = new Class[]{ServletConfig.class};

    private final ReentrantReadWriteLock parametersLock =
            new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock mappingsLock =
            new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock referencesLock =
            new ReentrantReadWriteLock();


    // ------------------------------------------------------------- Properties

    @Override
    public boolean isOverridable() {
        return overridable;
    }

    @Override
    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }

    /**
     * 返回可用的 date/time, 毫秒. 
     * 如果date/time是Long.MAX_VALUE, 意味着永久不可用, 任何对这个servlet的请求将返回SC_NOT_FOUND错误.
     * 如果日期/时间在将来, 任何这个servlet的请求将返回一个SC_SERVICE_UNAVAILABLE错误.
     * 如果是零,servlet当前可用.
     */
    @Override
    public long getAvailable() {
        return (this.available);
    }


    /**
     * 设置可用的 date/time, 毫秒. 
     * 如果date/time是Long.MAX_VALUE, 意味着永久不可用, 任何对这个servlet的请求将返回SC_NOT_FOUND错误.
     * 如果日期/时间在将来, 任何这个servlet的请求将返回一个SC_SERVICE_UNAVAILABLE错误.
     * 如果是零,servlet当前可用.
     *
     * @param available 可用的date/time
     */
    @Override
    public void setAvailable(long available) {

        long oldAvailable = this.available;
        if (available > System.currentTimeMillis())
            this.available = available;
        else
            this.available = 0L;
        support.firePropertyChange("available", Long.valueOf(oldAvailable),
                                   Long.valueOf(this.available));

    }


    /**
     * @return 此servlet的活动分配数, 即使它们都是同一个实例(将真正的servlet没有实现<code>SingleThreadModel</code>.
     */
    public int getCountAllocated() {
        return this.countAllocated.get();
    }


    /**
     * @return load-on-startup属性值(负值表示第一个调用).
     */
    @Override
    public int getLoadOnStartup() {

        if (isJspServlet && loadOnStartup < 0) {
            /*
             * JspServlet 必须总是预加载, 因为它的实例在注册JMX的时候会使用 (注册JSP监控MBean时)
             */
             return Integer.MAX_VALUE;
        } else {
            return (this.loadOnStartup);
        }
    }


    /**
     * 设置load-on-startup属性值(负值表示第一个调用).
     *
     * @param value New load-on-startup value
     */
    @Override
    public void setLoadOnStartup(int value) {

        int oldLoadOnStartup = this.loadOnStartup;
        this.loadOnStartup = value;
        support.firePropertyChange("loadOnStartup",
                                   Integer.valueOf(oldLoadOnStartup),
                                   Integer.valueOf(this.loadOnStartup));

    }



    /**
     * 设置load-on-startup属性值.
     * 为规范, 任何缺少或非数值的值都被转换为零, 这样servlet在启动时仍然会被加载, 但以任意顺序.
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartupString(String value) {

        try {
            setLoadOnStartup(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            setLoadOnStartup(0);
        }
    }

    /**
     * @return 解析的load-on-startup值
     */
    public String getLoadOnStartupString() {
        return Integer.toString( getLoadOnStartup());
    }


    /**
     * @return 当使用单个线程模型servlet时, 将分配的实例的最大数量.
     */
    public int getMaxInstances() {
        return (this.maxInstances);
    }


    /**
     * 设置当使用单个线程模型servlet时, 将分配的实例的最大数量.
     *
     * @param maxInstances New value of maxInstances
     */
    public void setMaxInstances(int maxInstances) {

        int oldMaxInstances = this.maxInstances;
        this.maxInstances = maxInstances;
        support.firePropertyChange("maxInstances", oldMaxInstances,
                                   this.maxInstances);

    }


    /**
     * 设置父级Container, 但只有当它是Context.
     *
     * @param container Proposed parent Container
     */
    @Override
    public void setParent(Container container) {

        if ((container != null) &&
            !(container instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardWrapper.notContext"));
        if (container instanceof StandardContext) {
            swallowOutput = ((StandardContext)container).getSwallowOutput();
            unloadDelay = ((StandardContext)container).getUnloadDelay();
        }
        super.setParent(container);
    }


    /**
     * @return run-as标识符.
     */
    @Override
    public String getRunAs() {
        return (this.runAs);
    }


    /**
     * 设置run-as标识符.
     *
     * @param runAs New run-as identity value
     */
    @Override
    public void setRunAs(String runAs) {
        String oldRunAs = this.runAs;
        this.runAs = runAs;
        support.firePropertyChange("runAs", oldRunAs, this.runAs);
    }


    /**
     * @return 完全限定的servlet类名.
     */
    @Override
    public String getServletClass() {
        return (this.servletClass);
    }


    /**
     * 设置完全限定的servlet类名.
     *
     * @param servletClass Servlet class name
     */
    @Override
    public void setServletClass(String servletClass) {
        String oldServletClass = this.servletClass;
        this.servletClass = servletClass;
        support.firePropertyChange("servletClass", oldServletClass,
                                   this.servletClass);
        if (Constants.JSP_SERVLET_CLASS.equals(servletClass)) {
            isJspServlet = true;
        }
    }



    /**
     * 设置这个servlet的名称.
     * 这个是一个<code>Container.setName()</code>方法的别名, 以及<code>ServletConfig</code>接口要求的<code>getServletName()</code>方法.
     *
     * @param name The new name of this servlet
     */
    public void setServletName(String name) {
        setName(name);
    }


    /**
     * servlet类是否实现<code>SingleThreadModel</code>接口?
     * 这只能在类加载后确定. 调用此方法不会触发加载类, 因为这可能导致应用程序出乎意料地行为.
     *
     * @return {@code null}如果未加载类, 否则{@code true}如果servlet实现了{@code SingleThreadModel},
     *         否则{@code false}.
     */
    public Boolean isSingleThreadModel() {
        // 如果servlet已加载
        if (singleThreadModel || instance != null) {
            return Boolean.valueOf(singleThreadModel);
        }
        return null;
    }


    /**
     * @return <code>true</code>如果Servlet已被标记为不可用.
     */
    @Override
    public boolean isUnavailable() {

        if (!isEnabled())
            return true;
        else if (available == 0L)
            return false;
        else if (available <= System.currentTimeMillis()) {
            available = 0L;
            return false;
        } else
            return true;

    }


    @Override
    public String[] getServletMethods() throws ServletException {

        instance = loadServlet();

        Class<? extends Servlet> servletClazz = instance.getClass();
        if (!javax.servlet.http.HttpServlet.class.isAssignableFrom(
                                                        servletClazz)) {
            return DEFAULT_SERVLET_METHODS;
        }

        HashSet<String> allow = new HashSet<>();
        allow.add("TRACE");
        allow.add("OPTIONS");

        Method[] methods = getAllDeclaredMethods(servletClazz);
        for (int i=0; methods != null && i<methods.length; i++) {
            Method m = methods[i];

            if (m.getName().equals("doGet")) {
                allow.add("GET");
                allow.add("HEAD");
            } else if (m.getName().equals("doPost")) {
                allow.add("POST");
            } else if (m.getName().equals("doPut")) {
                allow.add("PUT");
            } else if (m.getName().equals("doDelete")) {
                allow.add("DELETE");
            }
        }

        String[] methodNames = new String[allow.size()];
        return allow.toArray(methodNames);
    }


    /**
     * @return 关联的servlet 实例.
     */
    @Override
    public Servlet getServlet() {
        return instance;
    }


    /**
     * 设置关联的servlet 实例.
     */
    @Override
    public void setServlet(Servlet servlet) {
        instance = servlet;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setServletSecurityAnnotationScanRequired(boolean b) {
        this.servletSecurityAnnotationScanRequired = b;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 执行周期性任务，如重新加载等.
     * 此方法将在容器的类加载上下文中调用. 将捕获和记录异常.
     */
    @Override
    public void backgroundProcess() {
        super.backgroundProcess();

        if (!getState().isAvailable())
            return;

        if (getServlet() instanceof PeriodicEventListener) {
            ((PeriodicEventListener) getServlet()).periodicEvent();
        }
    }


    /**
     * 从servlet异常中提取根异常.
     *
     * @param e servlet异常
     * 
     * @return the root cause of the Servlet exception
     */
    public static Throwable getRootCause(ServletException e) {
        Throwable rootCause = e;
        Throwable rootCauseCheck = null;
        // Extra aggressive rootCause finding
        int loops = 0;
        do {
            loops++;
            rootCauseCheck = rootCause.getCause();
            if (rootCauseCheck != null)
                rootCause = rootCauseCheck;
        } while (rootCauseCheck != null && (loops < 20));
        return rootCause;
    }


    /**
     * 拒绝再添加子级Container,因为Wrapper是Container体系结构中的最低层级.
     *
     * @param child Child container to be added
     */
    @Override
    public void addChild(Container child) {
        throw new IllegalStateException(sm.getString("standardWrapper.notChild"));
    }


    /**
     * 添加一个新的servlet初始化参数.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    @Override
    public void addInitParameter(String name, String value) {

        parametersLock.writeLock().lock();
        try {
            parameters.put(name, value);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("addInitParameter", name);
    }


    /**
     * 添加关联的映射.
     *
     * @param mapping The new wrapper mapping
     */
    @Override
    public void addMapping(String mapping) {

        mappingsLock.writeLock().lock();
        try {
            mappings.add(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        if(parent.getState().equals(LifecycleState.STARTED))
            fireContainerEvent(ADD_MAPPING_EVENT, mapping);

    }


    /**
     * 向记录集添加一个新的安全角色引用记录.
     *
     * @param name 此servlet中使用的角色名称
     * @param link Web应用程序中使用的角色名
     */
    @Override
    public void addSecurityReference(String name, String link) {

        referencesLock.writeLock().lock();
        try {
            references.put(name, link);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("addSecurityReference", name);
    }


    /**
     * 分配该servlet的初始化实例，该servlet准备就绪调用它的<code>service()</code>方法.
     * 如果servlet类没有实现<code>SingleThreadModel</code>, 可以立即返回初始化实例.
     * 如果servlet类实现了<code>SingleThreadModel</code>, Wrapper实现类必须确保这个实例不会被再次分配，
     * 直到它被<code>deallocate()</code>释放.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if a loading error occurs
     */
    @Override
    public Servlet allocate() throws ServletException {

        // If we are currently unloading this servlet, throw an exception
        if (unloading) {
            throw new ServletException(sm.getString("standardWrapper.unloading", getName()));
        }

        boolean newInstance = false;

        // 如果不是SingleThreadedModel, 每次返回相同的实例
        if (!singleThreadModel) {
            // 加载并初始化实例
            if (instance == null || !instanceInitialized) {
                synchronized (this) {
                    if (instance == null) {
                        try {
                            if (log.isDebugEnabled()) {
                                log.debug("Allocating non-STM instance");
                            }

                            // Note: 直到加载它的时候才知道Servlet是否实现了SingleThreadModel.
                            instance = loadServlet();
                            newInstance = true;
                            if (!singleThreadModel) {
                                // 对于non-STM, 此处增加以防止卸载的竞争条件. Bug 43683, test case #3
                                countAllocated.incrementAndGet();
                            }
                        } catch (ServletException e) {
                            throw e;
                        } catch (Throwable e) {
                            ExceptionUtils.handleThrowable(e);
                            throw new ServletException(sm.getString("standardWrapper.allocate"), e);
                        }
                    }
                    if (!instanceInitialized) {
                        initServlet(instance);
                    }
                }
            }

            if (singleThreadModel) {
                if (newInstance) {
                    // 必须在上述同步之外执行此操作，以防止可能的死锁
                    synchronized (instancePool) {
                        instancePool.push(instance);
                        nInstances++;
                    }
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("  Returning non-STM instance");
                }
                // 对于新实例, 在创建时计数将被递增
                if (!newInstance) {
                    countAllocated.incrementAndGet();
                }
                return instance;
            }
        }

        synchronized (instancePool) {
            while (countAllocated.get() >= nInstances) {
                // 分配一个新的实例，或者等待
                if (nInstances < maxInstances) {
                    try {
                        instancePool.push(loadServlet());
                        nInstances++;
                    } catch (ServletException e) {
                        throw e;
                    } catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        throw new ServletException(sm.getString("standardWrapper.allocate"), e);
                    }
                } else {
                    try {
                        instancePool.wait();
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }
            if (log.isTraceEnabled()) {
                log.trace("  Returning allocated STM instance");
            }
            countAllocated.incrementAndGet();
            return instancePool.pop();
        }
    }


    /**
     * 将先前分配的servlet返回到可用实例池中.
     * 如果这个servlet类没有实现SingleThreadModel,实际上不需要任何动作.
     *
     * @param servlet The servlet to be returned
     *
     * @exception ServletException 如果发生了分配错误
     */
    @Override
    public void deallocate(Servlet servlet) throws ServletException {

        // If not SingleThreadModel, no action is required
        if (!singleThreadModel) {
            countAllocated.decrementAndGet();
            return;
        }

        // Unlock and free this instance
        synchronized (instancePool) {
            countAllocated.decrementAndGet();
            instancePool.push(servlet);
            instancePool.notify();
        }
    }


    /**
     * 返回指定的初始化参数名称的值; 或者<code>null</code>.
     *
     * @param name 请求的初始化参数的名称
     */
    @Override
    public String findInitParameter(String name) {

        parametersLock.readLock().lock();
        try {
            return parameters.get(name);
        } finally {
            parametersLock.readLock().unlock();
        }
    }


    /**
     * 返回所有定义的初始化参数的名称.
     */
    @Override
    public String[] findInitParameters() {

        parametersLock.readLock().lock();
        try {
            String results[] = new String[parameters.size()];
            return parameters.keySet().toArray(results);
        } finally {
            parametersLock.readLock().unlock();
        }
    }


    /**
     * 返回关联的映射.
     */
    @Override
    public String[] findMappings() {

        mappingsLock.readLock().lock();
        try {
            return mappings.toArray(new String[mappings.size()]);
        } finally {
            mappingsLock.readLock().unlock();
        }

    }


    /**
     * 为指定的安全角色引用名称返回安全角色链接; 或者<code>null</code>.
     *
     * @param name 在servlet中使用的安全角色引用
     */
    @Override
    public String findSecurityReference(String name) {

        referencesLock.readLock().lock();
        try {
            return references.get(name);
        } finally {
            referencesLock.readLock().unlock();
        }

    }


    /**
     * 返回安全角色引用名称的集合; 否则返回一个零长度数组.
     */
    @Override
    public String[] findSecurityReferences() {

        referencesLock.readLock().lock();
        try {
            String results[] = new String[references.size()];
            return references.keySet().toArray(results);
        } finally {
            referencesLock.readLock().unlock();
        }

    }


    /**
     * 加载并初始化此servlet的实例, 如果没有一个初始化实例.
     * 这可以使用，例如，加载servlet被标记在部署描述符是在服务器启动时加载.
     * <p>
     * <b>实现注意</b>: servlet的类名称以<code>org.apache.catalina.</code>开始 (so-called "container" servlets)
     * 由加载这个类的同一个类加载器加载, 而不是当前Web应用程序的类加载器.
     * 这使此类访问Catalina, 防止为Web应用程序加载的类.
     *
     * @exception ServletException 如果servlet init()方法抛出异常
     * @exception ServletException 如果出现其他加载问题
     */
    @Override
    public synchronized void load() throws ServletException {
        instance = loadServlet();

        if (!instanceInitialized) {
            initServlet(instance);
        }

        if (isJspServlet) {
            StringBuilder oname = new StringBuilder(getDomain());

            oname.append(":type=JspMonitor");

            oname.append(getWebModuleKeyProperties());

            oname.append(",name=");
            oname.append(getName());

            oname.append(getJ2EEKeyProperties());

            try {
                jspMonitorON = new ObjectName(oname.toString());
                Registry.getRegistry(null, null)
                    .registerComponent(instance, jspMonitorON, null);
            } catch( Exception ex ) {
                log.info("Error registering JSP monitoring with jmx " +
                         instance);
            }
        }
    }


    /**
     * 加载并初始化此servlet的实例, 如果没有一个初始化实例.
     * 这可以使用，例如，加载servlet被标记在部署描述符是在服务器启动时加载.
     * 
     * @return 加载的Servlet实例
     * @throws ServletException Servlet加载错误
     */
    public synchronized Servlet loadServlet() throws ServletException {

        // 如果已经有一个实例或实例池
        if (!singleThreadModel && (instance != null))
            return instance;

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        Servlet servlet;
        try {
            long t1=System.currentTimeMillis();
            // 如果没有指定servlet类
            if (servletClass == null) {
                unavailable(null);
                throw new ServletException
                    (sm.getString("standardWrapper.notClass", getName()));
            }

            InstanceManager instanceManager = ((StandardContext)getParent()).getInstanceManager();
            try {
                servlet = (Servlet) instanceManager.newInstance(servletClass);
            } catch (ClassCastException e) {
                unavailable(null);
                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.notServlet", servletClass), e);
            } catch (Throwable e) {
                e = ExceptionUtils.unwrapInvocationTargetException(e);
                ExceptionUtils.handleThrowable(e);
                unavailable(null);

                // Added extra log statement for Bugzilla 36630:
                // http://bz.apache.org/bugzilla/show_bug.cgi?id=36630
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("standardWrapper.instantiate", servletClass), e);
                }

                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.instantiate", servletClass), e);
            }

            if (multipartConfigElement == null) {
                MultipartConfig annotation =
                        servlet.getClass().getAnnotation(MultipartConfig.class);
                if (annotation != null) {
                    multipartConfigElement =
                            new MultipartConfigElement(annotation);
                }
            }

            processServletSecurityAnnotation(servlet.getClass());

            // 特殊处理ContainerServlet实例
            // Note: InstanceManager检查是否允许应用程序加载 ContainerServlets
            if (servlet instanceof ContainerServlet) {
                ((ContainerServlet) servlet).setWrapper(this);
            }

            classLoadTime=(int) (System.currentTimeMillis() -t1);

            initServlet(servlet);

            fireContainerEvent("load", this);

            loadTime=System.currentTimeMillis() -t1;
        } finally {
            if (swallowOutput) {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    if (getServletContext() != null) {
                        getServletContext().log(log);
                    } else {
                        out.println(log);
                    }
                }
            }
        }
        return servlet;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void servletSecurityAnnotationScan() throws ServletException {
        if (getServlet() == null) {
            Class<?> clazz = null;
            try {
                clazz = ((Context) getParent()).getLoader().getClassLoader().loadClass(
                        getServletClass());
                processServletSecurityAnnotation(clazz);
            } catch (ClassNotFoundException e) {
                // Safe to ignore. No class means no annotations to process
            }
        } else {
            if (servletSecurityAnnotationScanRequired) {
                processServletSecurityAnnotation(getServlet().getClass());
            }
        }
    }

    private void processServletSecurityAnnotation(Class<?> clazz) {
        // Calling this twice isn't harmful so no syncs
        servletSecurityAnnotationScanRequired = false;

        Context ctxt = (Context) getParent();

        if (ctxt.getIgnoreAnnotations()) {
            return;
        }

        ServletSecurity secAnnotation =
            clazz.getAnnotation(ServletSecurity.class);
        if (secAnnotation != null) {
            ctxt.addServletSecurity(
                    new ApplicationServletRegistration(this, ctxt),
                    new ServletSecurityElement(secAnnotation));
        }
    }

    private synchronized void initServlet(Servlet servlet)
            throws ServletException {

        if (instanceInitialized && !singleThreadModel) return;

        // Call the initialization method of this servlet
        try {
            if( Globals.IS_SECURITY_ENABLED) {
                boolean success = false;
                try {
                    Object[] args = new Object[] { facade };
                    SecurityUtil.doAsPrivilege("init",
                                               servlet,
                                               classType,
                                               args);
                    success = true;
                } finally {
                    if (!success) {
                        // destroy() will not be called, thus clear the reference now
                        SecurityUtil.remove(servlet);
                    }
                }
            } else {
                servlet.init(facade);
            }

            instanceInitialized = true;
        } catch (UnavailableException f) {
            unavailable(f);
            throw f;
        } catch (ServletException f) {
            // If the servlet wanted to be unavailable it would have
            // said so, so do not call unavailable(null).
            throw f;
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
            getServletContext().log("StandardWrapper.Throwable", f );
            // If the servlet wanted to be unavailable it would have
            // said so, so do not call unavailable(null).
            throw new ServletException
                (sm.getString("standardWrapper.initException", getName()), f);
        }
    }

    /**
     * 删除指定的初始化参数.
     *
     * @param name 要删除的初始化参数名称
     */
    @Override
    public void removeInitParameter(String name) {

        parametersLock.writeLock().lock();
        try {
            parameters.remove(name);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("removeInitParameter", name);

    }


    /**
     * 删除关联的映射.
     *
     * @param mapping 要删除的模式
     */
    @Override
    public void removeMapping(String mapping) {

        mappingsLock.writeLock().lock();
        try {
            mappings.remove(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        if(parent.getState().equals(LifecycleState.STARTED))
            fireContainerEvent(REMOVE_MAPPING_EVENT, mapping);
    }


    /**
     * 删除指定角色名称的任何安全角色引用.
     *
     * @param name 要删除此servlet中使用的安全角色
     */
    @Override
    public void removeSecurityReference(String name) {

        referencesLock.writeLock().lock();
        try {
            references.remove(name);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("removeSecurityReference", name);
    }


    /**
     * 处理一个UnavailableException, 标记此servlet在指定的时间内不可用.
     *
     * @param unavailable 发生的异常, 或<code>null</code>将此servlet标记为永久不可用
     */
    @Override
    public void unavailable(UnavailableException unavailable) {
        getServletContext().log(sm.getString("standardWrapper.unavailable", getName()));
        if (unavailable == null)
            setAvailable(Long.MAX_VALUE);
        else if (unavailable.isPermanent())
            setAvailable(Long.MAX_VALUE);
        else {
            int unavailableSeconds = unavailable.getUnavailableSeconds();
            if (unavailableSeconds <= 0)
                unavailableSeconds = 60;        // Arbitrary default
            setAvailable(System.currentTimeMillis() +
                         (unavailableSeconds * 1000L));
        }

    }


    /**
     * 卸载此servlet的所有初始化实例, 调用<code>destroy()</code>方法之后.
     * 例如，可以在关闭整个servlet引擎之前使用它, 或者在加载与Loader的存储库相关联的加载器的所有类之前.
     *
     * @exception ServletException 如果destroy()方法抛出异常
     */
    @Override
    public synchronized void unload() throws ServletException {

        // Nothing to do if we have never loaded the instance
        if (!singleThreadModel && (instance == null))
            return;
        unloading = true;

        // 如果当前实例被分配，就花一段时间
        // (possibly more than once if non-STM)
        if (countAllocated.get() > 0) {
            int nRetries = 0;
            long delay = unloadDelay / 20;
            while ((nRetries < 21) && (countAllocated.get() > 0)) {
                if ((nRetries % 10) == 0) {
                    log.info(sm.getString("standardWrapper.waiting",
                                          countAllocated.toString(),
                                          getName()));
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // Ignore
                }
                nRetries++;
            }
        }

        if (instanceInitialized) {
            PrintStream out = System.out;
            if (swallowOutput) {
                SystemLogHandler.startCapture();
            }

            // Call the servlet destroy() method
            try {
                if( Globals.IS_SECURITY_ENABLED) {
                    try {
                        SecurityUtil.doAsPrivilege("destroy", instance);
                    } finally {
                        SecurityUtil.remove(instance);
                    }
                } else {
                    instance.destroy();
                }

            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
                instance = null;
                instancePool = null;
                nInstances = 0;
                fireContainerEvent("unload", this);
                unloading = false;
                throw new ServletException
                    (sm.getString("standardWrapper.destroyException", getName()),
                     t);
            } finally {
                // Annotation processing
                if (!((Context) getParent()).getIgnoreAnnotations()) {
                    try {
                        ((Context)getParent()).getInstanceManager().destroyInstance(instance);
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        log.error(sm.getString("standardWrapper.destroyInstance", getName()), t);
                    }
                }
                // Write captured output
                if (swallowOutput) {
                    String log = SystemLogHandler.stopCapture();
                    if (log != null && log.length() > 0) {
                        if (getServletContext() != null) {
                            getServletContext().log(log);
                        } else {
                            out.println(log);
                        }
                    }
                }
            }
        }

        // Deregister the destroyed instance
        instance = null;
        instanceInitialized = false;

        if (isJspServlet && jspMonitorON != null ) {
            Registry.getRegistry(null, null).unregisterComponent(jspMonitorON);
        }

        if (singleThreadModel && (instancePool != null)) {
            try {
                while (!instancePool.isEmpty()) {
                    Servlet s = instancePool.pop();
                    if (Globals.IS_SECURITY_ENABLED) {
                        try {
                            SecurityUtil.doAsPrivilege("destroy", s);
                        } finally {
                            SecurityUtil.remove(s);
                        }
                    } else {
                        s.destroy();
                    }
                    // Annotation processing
                    if (!((Context) getParent()).getIgnoreAnnotations()) {
                       ((StandardContext)getParent()).getInstanceManager().destroyInstance(s);
                    }
                }
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
                instancePool = null;
                nInstances = 0;
                unloading = false;
                fireContainerEvent("unload", this);
                throw new ServletException
                    (sm.getString("standardWrapper.destroyException",
                                  getName()), t);
            }
            instancePool = null;
            nInstances = 0;
        }

        singleThreadModel = false;

        unloading = false;
        fireContainerEvent("unload", this);

    }


    // -------------------------------------------------- ServletConfig Methods


    /**
     * @return 指定名称的初始化参数值; 或者<code>null</code>.
     *
     * @param name 要检索的初始化参数的名称
     */
    @Override
    public String getInitParameter(String name) {
        return (findInitParameter(name));
    }


    /**
     * @return 定义的初始化参数名称集合. 如果没有, 返回空枚举.
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        parametersLock.readLock().lock();
        try {
            return Collections.enumeration(parameters.keySet());
        } finally {
            parametersLock.readLock().unlock();
        }
    }


    /**
     * @return 关联的servlet上下文.
     */
    @Override
    public ServletContext getServletContext() {

        if (parent == null)
            return (null);
        else if (!(parent instanceof Context))
            return (null);
        else
            return (((Context) parent).getServletContext());

    }


    /**
     * @return 这个servlet的名称.
     */
    @Override
    public String getServletName() {
        return (getName());
    }

    public long getProcessingTime() {
        return swValve.getProcessingTime();
    }

    public long getMaxTime() {
        return swValve.getMaxTime();
    }

    public long getMinTime() {
        return swValve.getMinTime();
    }

    public int getRequestCount() {
        return swValve.getRequestCount();
    }

    public int getErrorCount() {
        return swValve.getErrorCount();
    }

    /**
     * 增加用于监视的错误计数.
     */
    @Override
    public void incrementErrorCount(){
        swValve.incrementErrorCount();
    }

    public long getLoadTime() {
        return loadTime;
    }

    public int getClassLoadTime() {
        return classLoadTime;
    }

    @Override
    public MultipartConfigElement getMultipartConfigElement() {
        return multipartConfigElement;
    }

    @Override
    public void setMultipartConfigElement(
            MultipartConfigElement multipartConfigElement) {
        this.multipartConfigElement = multipartConfigElement;
    }

    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    @Override
    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // -------------------------------------------------------- Package Methods


    // -------------------------------------------------------- protected Methods


    /**
     * @return <code>true</code>如果指定的类名表示容器提供应该由服务器类装入器装入的servlet类.
     *
     * @param classname 要检查的类的名称
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    protected boolean isContainerProvidedServlet(String classname) {

        if (classname.startsWith("org.apache.catalina.")) {
            return true;
        }
        try {
            Class<?> clazz =
                this.getClass().getClassLoader().loadClass(classname);
            return (ContainerServlet.class.isAssignableFrom(clazz));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return false;
        }

    }


    protected Method[] getAllDeclaredMethods(Class<?> c) {

        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());

        Method[] thisMethods = c.getDeclaredMethods();
        if (thisMethods.length == 0) {
            return parentMethods;
        }

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods =
                new Method[parentMethods.length + thisMethods.length];
            System.arraycopy(parentMethods, 0, allMethods, 0,
                             parentMethods.length);
            System.arraycopy(thisMethods, 0, allMethods, parentMethods.length,
                             thisMethods.length);

            thisMethods = allMethods;
        }

        return thisMethods;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Send j2ee.state.starting notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting",
                                                        this.getObjectName(),
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Start up this component
        super.startInternal();

        setAvailable(0L);

        // Send j2ee.state.running notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.running", this.getObjectName(),
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setAvailable(Long.MAX_VALUE);

        // Send j2ee.state.stopping notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.stopping", this.getObjectName(),
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // 关闭servlet实例 (如果已经初始化)
        try {
            unload();
        } catch (ServletException e) {
            getServletContext().log(sm.getString
                      ("standardWrapper.unloadException", getName()), e);
        }

        // Shut down this component
        super.stopInternal();

        // Send j2ee.state.stopped notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.stopped", this.getObjectName(),
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Send j2ee.object.deleted notification
        Notification notification =
            new Notification("j2ee.object.deleted", this.getObjectName(),
                            sequenceNumber++);
        broadcaster.sendNotification(notification);

    }


    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties =
            new StringBuilder("j2eeType=Servlet");

        keyProperties.append(getWebModuleKeyProperties());

        keyProperties.append(",name=");

        String name = getName();
        if (Util.objectNameValueNeedsQuote(name)) {
            name = ObjectName.quote(name);
        }
        keyProperties.append(name);

        keyProperties.append(getJ2EEKeyProperties());

        return keyProperties.toString();
    }


    private String getWebModuleKeyProperties() {

        StringBuilder keyProperties = new StringBuilder(",WebModule=//");
        String hostName = getParent().getParent().getName();
        if (hostName == null) {
            keyProperties.append("DEFAULT");
        } else {
            keyProperties.append(hostName);
        }

        String contextName = ((Context) getParent()).getName();
        if (!contextName.startsWith("/")) {
            keyProperties.append('/');
        }
        keyProperties.append(contextName);

        return keyProperties.toString();
    }

    private String getJ2EEKeyProperties() {

        StringBuilder keyProperties = new StringBuilder(",J2EEApplication=");

        StandardContext ctx = null;
        if (parent instanceof StandardContext) {
            ctx = (StandardContext) getParent();
        }

        if (ctx == null) {
            keyProperties.append("none");
        } else {
            keyProperties.append(ctx.getJ2EEApplication());
        }
        keyProperties.append(",J2EEServer=");
        if (ctx == null) {
            keyProperties.append("none");
        } else {
            keyProperties.append(ctx.getJ2EEServer());
        }

        return keyProperties.toString();
    }


    /**
     * Remove a JMX notificationListener
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    @Override
    public void removeNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object object) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener,filter,object);
    }

    protected MBeanNotificationInfo[] notificationInfo;

    /**
     * Get JMX Broadcaster Info
     * FIXME: This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {

        if(notificationInfo == null) {
            notificationInfo = new MBeanNotificationInfo[]{
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.created"},
                    Notification.class.getName(),
                    "servlet is created"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.starting"},
                    Notification.class.getName(),
                    "servlet is starting"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.running"},
                    Notification.class.getName(),
                    "servlet is running"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.stopped"},
                    Notification.class.getName(),
                    "servlet start to stopped"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.stopped"},
                    Notification.class.getName(),
                    "servlet is stopped"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.deleted"},
                    Notification.class.getName(),
                    "servlet is deleted"
                    )
            };
        }

        return notificationInfo;
    }


    /**
     * Add a JMX-NotificationListener
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
}
