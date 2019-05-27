package org.apache.catalina.loader;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

import org.apache.catalina.Container;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.apache.juli.WebappProperties;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstrumentableClassLoader;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.PermissionCheck;

/**
 * 指定的Web应用程序类装入器.
 * <p>
 * 这类装载器是一个JDK的<code>URLClassLoader</code>的完整实现.
 * 它设计用来充分和标准的<code>URLClassLoader</code>兼容, 虽然它的内部行为可能完全不同.
 * <p>
 * <strong>实现注意</strong> - 这个类加载器忠实地遵循规范中推荐的委托模型.
 * 系统类装入器将首先查询, 然后是本地存储库, 只有到父类装入器的委托才会出现.
 * 这使得Web应用程序重写任何共享类, 除了J2SE的类.
 * 特殊处理, 是从使用XML解析器接口、JNDI接口、 以及来自servlet API的类提供, 这些从来没有从web应用程序库加载.
 * <code>delegate</code>属性允许应用修改此行为以将父类加载程序移到本地存储库之前.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - 由于Jasper编译技术的局限性, 任何包含servlet类的存储库都将被类装入器忽略.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - 类装入器生成资源URL，其中包含一个类从JAR文件加载时包含的完整JAR URL,
 * 允许在类级别设置安全权限, 甚至当一个类包含在一个JAR中时.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - 本地存储库的搜索顺序，是按照通过初始构造函数添加的顺序.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - 不进行密封违规或安全检查，除非安全管理器存在.
 * <p>
 * <strong>IMPLEMENTATION NOTE</strong> - 在 8.0中, 这个类加载器实现了{@link InstrumentableClassLoader},
 * 允许Web应用程序类在同一Web应用程序中检测其他类. 不允许系统类或容器类或其它应用中的类.
 */
public abstract class WebappClassLoaderBase extends URLClassLoader
        implements Lifecycle, InstrumentableClassLoader, WebappProperties, PermissionCheck {

    private static final Log log = LogFactory.getLog(WebappClassLoaderBase.class);

    /**
     * 要忽略的ThreadGroup名称列表，当扫描Web应用程序需要关闭的线程时.
     */
    private static final List<String> JVM_THREAD_GROUP_NAMES = new ArrayList<>();

    private static final String JVM_THREAD_GROUP_SYSTEM = "system";

    private static final String CLASS_FILE_SUFFIX = ".class";

    static {
        ClassLoader.registerAsParallelCapable();
        JVM_THREAD_GROUP_NAMES.add(JVM_THREAD_GROUP_SYSTEM);
        JVM_THREAD_GROUP_NAMES.add("RMI Runtime");
    }

    protected class PrivilegedFindClassByName
        implements PrivilegedAction<Class<?>> {

        protected final String name;

        PrivilegedFindClassByName(String name) {
            this.name = name;
        }

        @Override
        public Class<?> run() {
            return findClassInternal(name);
        }
    }


    protected static final class PrivilegedGetClassLoader
        implements PrivilegedAction<ClassLoader> {

        public final Class<?> clazz;

        public PrivilegedGetClassLoader(Class<?> clazz){
            this.clazz = clazz;
        }

        @Override
        public ClassLoader run() {
            return clazz.getClassLoader();
        }
    }


    // ------------------------------------------------------- Static Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------------- Constructors

    protected WebappClassLoaderBase() {

        super(new URL[0]);

        ClassLoader p = getParent();
        if (p == null) {
            p = getSystemClassLoader();
        }
        this.parent = p;

        ClassLoader j = String.class.getClassLoader();
        if (j == null) {
            j = getSystemClassLoader();
            while (j.getParent() != null) {
                j = j.getParent();
            }
        }
        this.javaseClassLoader = j;

        securityManager = System.getSecurityManager();
        if (securityManager != null) {
            refreshPolicy();
        }
    }


    /**
     * @param parent 父类加载器
     */
    protected WebappClassLoaderBase(ClassLoader parent) {

        super(new URL[0], parent);

        ClassLoader p = getParent();
        if (p == null) {
            p = getSystemClassLoader();
        }
        this.parent = p;

        ClassLoader j = String.class.getClassLoader();
        if (j == null) {
            j = getSystemClassLoader();
            while (j.getParent() != null) {
                j = j.getParent();
            }
        }
        this.javaseClassLoader = j;

        securityManager = System.getSecurityManager();
        if (securityManager != null) {
            refreshPolicy();
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 这个Web应用关联的web资源.
     */
    protected WebResourceRoot resources = null;


    /**
     * 加载的类和资源的ResourceEntry缓存, 资源路径作为key, 而不是二进制名称.
     * 路径作为key，因为可以用二进制名称（类）或路径 (其他资源，如属性文件)请求资源， 从二进制名称到路径的映射是明确的，但反向映射是不明确的.
     */
    protected final Map<String, ResourceEntry> resourceEntries =
            new ConcurrentHashMap<>();


    /**
     * 这个类是否应该将委托装载到父类装入器，在搜索自己的库(即通常的Java2的委托模型)之前?
     * 如果设置为<code>false</code>, 这个类装入器将首先搜索自己的存储库, 只有在本地找不到类或资源时，才委托给父级.
     * 默认为<code>false</code>, 是servlet规范调用的行为.
     */
    protected boolean delegate = false;


    private final HashMap<String,Long> jarModificationTimes = new HashMap<>();


    /**
     * 如果这个加载程序是Web应用程序上下文的，需要读取文件权限的集合.
     */
    protected final ArrayList<Permission> permissionList = new ArrayList<>();


    /**
     * 每个CodeSource的PermissionCollection.
     */
    protected final HashMap<String, PermissionCollection> loaderPC = new HashMap<>();


    /**
     * 安装的SecurityManager实例.
     */
    protected final SecurityManager securityManager;


    /**
     * 父类加载器.
     */
    protected final ClassLoader parent;


    /**
     * Bootstrap类加载器, 用于加载JavaSE 类.
     * 在一些实现中，这个类加载器总是<code>null</null>，
     * 在这种情况下，将在系统类加载器上递归地调用{@link ClassLoader#getParent()}.
     */
    private ClassLoader javaseClassLoader;


    /**
     * 所有的权限.
     * @deprecated Unused. This will be removed in Tomcat 9.
     */
    @Deprecated
    protected final Permission allPermission = new java.security.AllPermission();


    /**
     * 使RMI目标内存泄漏检测得以控制.
     * 这是必要的因为检测只能工作在java 9, 如果一些模块化检查被禁用.
     */
    private boolean clearReferencesRmiTargets = true;

    /**
     * 如果Tomcat试图终止由Web应用程序启动的线程?
     * 停止线程, 通过弃用 <code>Thread.stop()</code>方法很可能导致不稳定.
     * 像这样的, 在开发环境中启用此选项应视为最后的选择，不建议在生产环境中使用. 如果未指定, 默认为<code>false</code>.
     */
    private boolean clearReferencesStopThreads = false;

    /**
     * 如果Tomcat试图终止由Web应用程序启动的{@link java.util.TimerThread}?
     * 如果未指定, 默认为<code>false</code>.
     */
    private boolean clearReferencesStopTimerThreads = false;

    /**
     * 停止类加载器时Tomcat是否调用{@link org.apache.juli.logging.LogFactory#release()}?
     * 如果未指定, 默认为<code>true</code>. 更改默认设置可能导致内存泄漏和其他问题.
     */
    private boolean clearReferencesLogFactoryRelease = true;

    /**
     * 如果一个 HttpClient keep-alive计时器线程已经由这个Web应用程序启动，并且仍在运行,
     * Tomcat是否应该修改{@link ClassLoader}为{@link ClassLoader#getParent()}防止内存泄漏?
     * 注意，keep-alive计时器线程都将自行停止, 一旦keep-alives都已经过期, 在一个繁忙的系统中，一段时间内可能不会发生.
     */
    private boolean clearReferencesHttpClientKeepAliveThread = true;

    /**
     * 保存类文件转换器装饰这个类加载器.
     * CopyOnWriteArrayList是线程安全的. 写入的代价是昂贵的, 但那些应该是罕见的. 读取很快, 因为实际上没有使用同步.
     * 重要的, ClassLoader在加载类时，不会阻塞对转换器的迭代.
     */
    private final List<ClassFileTransformer> transformers = new CopyOnWriteArrayList<>();


    /**
     * 是否已经调用{@link #addURL(URL)}, 在搜索资源时创建检查超级类的要求.
     */
    private boolean hasExternalRepositories = false;


    /**
     * 由这个类管理的存储库而不是超级类.
     */
    private List<URL> localRepositories = new ArrayList<>();


    private volatile LifecycleState state = LifecycleState.NEW;


    // ------------------------------------------------------------- Properties

    public WebResourceRoot getResources() {
        return this.resources;
    }


    /**
     * @param resources 类加载器将载入类的资源
     */
    public void setResources(WebResourceRoot resources) {
        this.resources = resources;
    }


    /**
     * @return 此类加载器的上下文名称.
     */
    public String getContextName() {
        if (resources == null) {
            return "Unknown";
        } else {
            return resources.getContext().getBaseName();
        }
    }


    /**
     * 返回这个类加载器的"delegate first"标志.
     * 
     * @return <code>true</code>如果类查找将委托给父级. Tomcat中默认的是<code>false</code>.
     */
    public boolean getDelegate() {
        return (this.delegate);
    }


    /**
     * 设置这个类加载器的"delegate first"标志.
     * 如果是true, 这个类加载器委托给父类加载器, 在搜索它自己的库之前.
     * 如果是false (默认), 这个类加载器将首先搜索它自己的库, 只有在本地没有找到类或资源时才会委托给父级, 按照servlet规范.
     *
     * @param delegate The new "delegate first" flag
     */
    public void setDelegate(boolean delegate) {
        this.delegate = delegate;
    }


    /**
     * 如果有一个Java SecurityManager创建了一个读文件权限, 对于给定URL的目标.
     *
     * @param url 本地系统上的文件或目录的URL
     */
    void addPermission(URL url) {
        if (url == null) {
            return;
        }
        if (securityManager != null) {
            String protocol = url.getProtocol();
            if ("file".equalsIgnoreCase(protocol)) {
                URI uri;
                File f;
                String path;
                try {
                    uri = url.toURI();
                    f = new File(uri);
                    path = f.getCanonicalPath();
                } catch (IOException | URISyntaxException e) {
                    log.warn(sm.getString(
                            "webappClassLoader.addPermisionNoCanonicalFile",
                            url.toExternalForm()));
                    return;
                }
                if (f.isFile()) {
                    // 允许读取文件
                    addPermission(new FilePermission(path, "read"));
                } else if (f.isDirectory()) {
                    addPermission(new FilePermission(path, "read"));
                    addPermission(new FilePermission(
                            path + File.separator + "-", "read"));
                } else {
                    // 文件不存在 - 忽略 (shouldn't happen)
                }
            } else {
                // 不支持的URL协议
                log.warn(sm.getString(
                        "webappClassLoader.addPermisionNoProtocol",
                        protocol, url.toExternalForm()));
            }
        }
    }


    /**
     * 如果Java SecurityManager创建了一个 Permission.
     *
     * @param permission The permission
     */
    void addPermission(Permission permission) {
        if ((securityManager != null) && (permission != null)) {
            permissionList.add(permission);
        }
    }


    public boolean getClearReferencesRmiTargets() {
        return this.clearReferencesRmiTargets;
    }


    public void setClearReferencesRmiTargets(boolean clearReferencesRmiTargets) {
        this.clearReferencesRmiTargets = clearReferencesRmiTargets;
    }


    public boolean getClearReferencesStopThreads() {
        return (this.clearReferencesStopThreads);
    }


    public void setClearReferencesStopThreads(boolean clearReferencesStopThreads) {
        this.clearReferencesStopThreads = clearReferencesStopThreads;
    }


    public boolean getClearReferencesStopTimerThreads() {
        return (this.clearReferencesStopTimerThreads);
    }


    public void setClearReferencesStopTimerThreads(boolean clearReferencesStopTimerThreads) {
        this.clearReferencesStopTimerThreads = clearReferencesStopTimerThreads;
    }


    public boolean getClearReferencesLogFactoryRelease() {
        return (this.clearReferencesLogFactoryRelease);
    }


    public void setClearReferencesLogFactoryRelease(boolean clearReferencesLogFactoryRelease) {
        this.clearReferencesLogFactoryRelease = clearReferencesLogFactoryRelease;
    }


    public boolean getClearReferencesHttpClientKeepAliveThread() {
        return (this.clearReferencesHttpClientKeepAliveThread);
    }


    public void setClearReferencesHttpClientKeepAliveThread(
            boolean clearReferencesHttpClientKeepAliveThread) {
        this.clearReferencesHttpClientKeepAliveThread =
            clearReferencesHttpClientKeepAliveThread;
    }


    // ------------------------------------------------------- Reloader Methods

    /**
     * 将指定的类文件转换器添加到此类加载器中.
     * 然后，在调用该方法之后，转换器将能够修改由此类加载程序加载的任何类的字节码.
     *
     * @param transformer 要添加到类加载器的转换器
     */
    @Override
    public void addTransformer(ClassFileTransformer transformer) {

        if (transformer == null) {
            throw new IllegalArgumentException(sm.getString(
                    "webappClassLoader.addTransformer.illegalArgument", getContextName()));
        }

        if (this.transformers.contains(transformer)) {
            // 如果已经添加了这个转换器的相同实例, bail out
            log.warn(sm.getString("webappClassLoader.addTransformer.duplicate",
                    transformer, getContextName()));
            return;
        }
        this.transformers.add(transformer);

        log.info(sm.getString("webappClassLoader.addTransformer", transformer, getContextName()));
    }

    /**
     * 从这个类加载器中移除指定的类文件转换器.
     * 在调用该方法之后，它将不再能够修改由类加载器加载的任何类的字节码.
     * 但是, 任何已经由这个转换器修改的类将保持不变.
     *
     * @param transformer 要删除的转换器
     */
    @Override
    public void removeTransformer(ClassFileTransformer transformer) {

        if (transformer == null) {
            return;
        }

        if (this.transformers.remove(transformer)) {
            log.info(sm.getString("webappClassLoader.removeTransformer",
                    transformer, getContextName()));
            return;
        }

    }

    protected void copyStateWithoutTransformers(WebappClassLoaderBase base) {
        base.resources = this.resources;
        base.delegate = this.delegate;
        base.state = LifecycleState.NEW;
        base.clearReferencesStopThreads = this.clearReferencesStopThreads;
        base.clearReferencesStopTimerThreads = this.clearReferencesStopTimerThreads;
        base.clearReferencesLogFactoryRelease = this.clearReferencesLogFactoryRelease;
        base.clearReferencesHttpClientKeepAliveThread = this.clearReferencesHttpClientKeepAliveThread;
        base.jarModificationTimes.putAll(this.jarModificationTimes);
        base.permissionList.addAll(this.permissionList);
        base.loaderPC.putAll(this.loaderPC);
    }

    /**
     * 修改了一个或多个类或资源，以便重新加载?
     * 
     * @return <code>true</code>如果有修改
     */
    public boolean modified() {

        if (log.isDebugEnabled())
            log.debug("modified()");

        for (Entry<String,ResourceEntry> entry : resourceEntries.entrySet()) {
            long cachedLastModified = entry.getValue().lastModified;
            long lastModified = resources.getClassLoaderResource(
                    entry.getKey()).getLastModified();
            if (lastModified != cachedLastModified) {
                if( log.isDebugEnabled() )
                    log.debug(sm.getString("webappClassLoader.resourceModified",
                            entry.getKey(),
                            new Date(cachedLastModified),
                            new Date(lastModified)));
                return true;
            }
        }

        // Check if JARs have been added or removed
        WebResource[] jars = resources.listResources("/WEB-INF/lib");
        // Filter out non-JAR resources

        int jarCount = 0;
        for (WebResource jar : jars) {
            if (jar.getName().endsWith(".jar") && jar.isFile() && jar.canRead()) {
                jarCount++;
                Long recordedLastModified = jarModificationTimes.get(jar.getName());
                if (recordedLastModified == null) {
                    // Jar has been added
                    log.info(sm.getString("webappClassLoader.jarsAdded",
                            resources.getContext().getName()));
                    return true;
                }
                if (recordedLastModified.longValue() != jar.getLastModified()) {
                    // Jar has been changed
                    log.info(sm.getString("webappClassLoader.jarsModified",
                            resources.getContext().getName()));
                    return true;
                }
            }
        }

        if (jarCount < jarModificationTimes.size()){
            log.info(sm.getString("webappClassLoader.jarsRemoved",
                    resources.getContext().getName()));
            return true;
        }

        // No classes have been modified
        return false;
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());
        sb.append("\r\n  context: ");
        sb.append(getContextName());
        sb.append("\r\n  delegate: ");
        sb.append(delegate);
        sb.append("\r\n");
        if (this.parent != null) {
            sb.append("----------> Parent Classloader:\r\n");
            sb.append(this.parent.toString());
            sb.append("\r\n");
        }
        if (this.transformers.size() > 0) {
            sb.append("----------> Class file transformers:\r\n");
            for (ClassFileTransformer transformer : this.transformers) {
                sb.append(transformer).append("\r\n");
            }
        }
        return (sb.toString());
    }


    // ---------------------------------------------------- ClassLoader Methods


    // Note: exposed for use by tests
    protected final Class<?> doDefineClass(String name, byte[] b, int off, int len,
            ProtectionDomain protectionDomain) {
        return super.defineClass(name, b, off, len, protectionDomain);
    }

    /**
     * 在本地库中查找指定的类. 
     * 如果没找到, 抛出<code>ClassNotFoundException</code>
     *
     * @param name 要加载的类的二进制名称
     *
     * @exception ClassNotFoundException 如果未找到该类
     */
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {

        if (log.isDebugEnabled())
            log.debug("    findClass(" + name + ")");

        checkStateForClassLoading(name);

        // (1) 使用SecurityManager时定义此类的权限
        if (securityManager != null) {
            int i = name.lastIndexOf('.');
            if (i >= 0) {
                try {
                    if (log.isTraceEnabled())
                        log.trace("      securityManager.checkPackageDefinition");
                    securityManager.checkPackageDefinition(name.substring(0,i));
                } catch (Exception se) {
                    if (log.isTraceEnabled())
                        log.trace("      -->Exception-->ClassNotFoundException", se);
                    throw new ClassNotFoundException(name, se);
                }
            }
        }

        // 从父类找到这个类 (如果未找到，抛出ClassNotFoundException)
        Class<?> clazz = null;
        try {
            if (log.isTraceEnabled())
                log.trace("      findClassInternal(" + name + ")");
            try {
                if (securityManager != null) {
                    PrivilegedAction<Class<?>> dp =
                        new PrivilegedFindClassByName(name);
                    clazz = AccessController.doPrivileged(dp);
                } else {
                    clazz = findClassInternal(name);
                }
            } catch(AccessControlException ace) {
                log.warn("WebappClassLoader.findClassInternal(" + name
                        + ") security exception: " + ace.getMessage(), ace);
                throw new ClassNotFoundException(name, ace);
            } catch (RuntimeException e) {
                if (log.isTraceEnabled())
                    log.trace("      -->RuntimeException Rethrown", e);
                throw e;
            }
            if ((clazz == null) && hasExternalRepositories) {
                try {
                    clazz = super.findClass(name);
                } catch(AccessControlException ace) {
                    log.warn("WebappClassLoader.findClassInternal(" + name
                            + ") security exception: " + ace.getMessage(), ace);
                    throw new ClassNotFoundException(name, ace);
                } catch (RuntimeException e) {
                    if (log.isTraceEnabled())
                        log.trace("      -->RuntimeException Rethrown", e);
                    throw e;
                }
            }
            if (clazz == null) {
                if (log.isDebugEnabled())
                    log.debug("    --> Returning ClassNotFoundException");
                throw new ClassNotFoundException(name);
            }
        } catch (ClassNotFoundException e) {
            if (log.isTraceEnabled())
                log.trace("    --> Passing on ClassNotFoundException");
            throw e;
        }

        // Return the class we have located
        if (log.isTraceEnabled())
            log.debug("      Returning class " + clazz);

        if (log.isTraceEnabled()) {
            ClassLoader cl;
            if (Globals.IS_SECURITY_ENABLED){
                cl = AccessController.doPrivileged(
                    new PrivilegedGetClassLoader(clazz));
            } else {
                cl = clazz.getClassLoader();
            }
            log.debug("      Loaded by " + cl.toString());
        }
        return (clazz);
    }


    /**
     * 在本地存储库中找到指定的资源, 并返回一个<code>URL</code>在这, 或者<code>null</code>，如果资源未找到.
     *
     * @param name 要查找的资源的名称
     */
    @Override
    public URL findResource(final String name) {

        if (log.isDebugEnabled())
            log.debug("    findResource(" + name + ")");

        checkStateForResourceLoading(name);

        URL url = null;

        String path = nameToPath(name);

        WebResource resource = resources.getClassLoaderResource(path);
        if (resource.exists()) {
            url = resource.getURL();
            trackLastModified(path, resource);
        }

        if ((url == null) && hasExternalRepositories) {
            url = super.findResource(name);
        }

        if (log.isDebugEnabled()) {
            if (url != null)
                log.debug("    --> Returning '" + url.toString() + "'");
            else
                log.debug("    --> Resource not found, returning null");
        }
        return url;
    }


    private void trackLastModified(String path, WebResource resource) {
        if (resourceEntries.containsKey(path)) {
            return;
        }
        ResourceEntry entry = new ResourceEntry();
        entry.lastModified = resource.getLastModified();
        synchronized(resourceEntries) {
            if (!resourceEntries.containsKey(path)) {
                resourceEntries.put(path, entry);
            }
        }
    }


    /**
     * 返回枚举<code>URLs</code>用指定名称表示所有资源.
     * 如果没找到, 返回空枚举.
     *
     * @param name 要查找的资源的名称
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {

        if (log.isDebugEnabled())
            log.debug("    findResources(" + name + ")");

        checkStateForResourceLoading(name);

        LinkedHashSet<URL> result = new LinkedHashSet<>();

        String path = nameToPath(name);

        WebResource[] webResources = resources.getClassLoaderResources(path);
        for (WebResource webResource : webResources) {
            if (webResource.exists()) {
                result.add(webResource.getURL());
            }
        }

        // 将调用的结果添加到超类
        if (hasExternalRepositories) {
            Enumeration<URL> otherResourcePaths = super.findResources(name);
            while (otherResourcePaths.hasMoreElements()) {
                result.add(otherResourcePaths.nextElement());
            }
        }

        return Collections.enumeration(result);
    }


    /**
     * 查找给定名称的资源.
     * 资源是一些数据(images, audio, text, etc.)可以通过与代码位置无关的方式来访问类代码. 
     * 资源的名称是一个 "/"-分隔标识资源的路径名.
     * 如果找不到资源, 返回<code>null</code>.
     * <p>
     * 此方法根据以下算法进行搜索, 找到合适的URL后返回. 如果找不到资源, 返回<code>null</code>.
     * <ul>
     * <li>如果<code>delegate</code>属性被设置为<code>true</code>,
     *     调用父类加载器的<code>getResource()</code>方法.</li>
     * <li>调用<code>findResource()</code>在本地定义的库中查找此资源.</li>
     * <li>调用父类加载器的<code>getResource()</code>方法.</li>
     * </ul>
     *
     * @param name 返回URL的资源的名称
     */
    @Override
    public URL getResource(String name) {

        if (log.isDebugEnabled())
            log.debug("getResource(" + name + ")");

        checkStateForResourceLoading(name);

        URL url = null;

        boolean delegateFirst = delegate || filter(name, false);

        // (1) 委托给父级
        if (delegateFirst) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader " + parent);
            url = parent.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning '" + url.toString() + "'");
                return (url);
            }
        }

        // (2) 搜索本地库
        url = findResource(name);
        if (url != null) {
            if (log.isDebugEnabled())
                log.debug("  --> Returning '" + url.toString() + "'");
            return (url);
        }

        // (3) 如果没有尝试，无条件地委托给父服务器
        if (!delegateFirst) {
            url = parent.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning '" + url.toString() + "'");
                return (url);
            }
        }

        // (4) 找不到资源
        if (log.isDebugEnabled())
            log.debug("  --> Resource not found, returning null");
        return (null);
    }


    /**
     * 查找给定名称的资源, 并返回一个可以用来读取它的输入流.
     * 搜索顺序如 <code>getResource()</code>所述, 在检查资源数据是否已被缓存之前.
     * 如果找不到资源, 返回<code>null</code>.
     *
     * @param name 返回输入流的资源的名称
     */
    @Override
    public InputStream getResourceAsStream(String name) {

        if (log.isDebugEnabled())
            log.debug("getResourceAsStream(" + name + ")");

        checkStateForResourceLoading(name);

        InputStream stream = null;

        boolean delegateFirst = delegate || filter(name, false);

        // (1) 委托给父级
        if (delegateFirst) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader " + parent);
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning stream from parent");
                return stream;
            }
        }

        // (2) 搜索本地库
        if (log.isDebugEnabled())
            log.debug("  Searching local repositories");
        String path = nameToPath(name);
        WebResource resource = resources.getClassLoaderResource(path);
        if (resource.exists()) {
            stream = resource.getInputStream();
            trackLastModified(path, resource);
        }
        try {
            if (hasExternalRepositories && stream == null) {
                URL url = super.findResource(name);
                if (url != null) {
                    stream = url.openStream();
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        if (stream != null) {
            if (log.isDebugEnabled())
                log.debug("  --> Returning stream from local");
            return stream;
        }

        // (3) 无条件委托给父级
        if (!delegateFirst) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader unconditionally " + parent);
            stream = parent.getResourceAsStream(name);
            if (stream != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning stream from parent");
                return stream;
            }
        }

        // (4) 找不到资源
        if (log.isDebugEnabled())
            log.debug("  --> Resource not found, returning null");
        return null;
    }


    /**
     * 用指定的名称加载类.
     *
     * @param name 要加载的类的二进制名称
     *
     * @exception ClassNotFoundException 如果未找到该类
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return (loadClass(name, false));
    }


    /**
     * 用指定的名称加载类, 使用以下算法搜索，直到找到并返回类为止.
     * 如果找不到类, 返回<code>ClassNotFoundException</code>.
     * <ul>
     * <li>调用<code>findLoadedClass(String)</code>检查类是否已加载.
     * 	如果已存在, 返回相同的<code>Class</code>对象.</li>
     * <li>如果<code>delegate</code>属性被设置为<code>true</code>,
     *     调用父类加载器的<code>loadClass()</code>方法.</li>
     * <li>调用<code>findClass()</code>在本地定义的存储库中找到这个类.</li>
     * <li>调用父类加载器的<code>loadClass()</code>方法.</li>
     * </ul>
     * 如果使用上述步骤找到类, 以及<code>resolve</code>标记是<code>true</code>, 
     * 这个方法随后将在结果Class对象调用<code>resolveClass(Class)</code>.
     *
     * @param name 要加载的类的二进制名称
     * @param resolve 如果是<code>true</code>，然后解决这个类
     *
     * @exception ClassNotFoundException 如果未找到该类
     */
    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        synchronized (getClassLoadingLock(name)) {
            if (log.isDebugEnabled())
                log.debug("loadClass(" + name + ", " + resolve + ")");
            Class<?> clazz = null;

            // 如果类装入器停止，则不加载类
            checkStateForClassLoading(name);

            // (0) 检查以前加载的本地类缓存
            clazz = findLoadedClass0(name);
            if (clazz != null) {
                if (log.isDebugEnabled())
                    log.debug("  Returning class from cache");
                if (resolve)
                    resolveClass(clazz);
                return (clazz);
            }

            // (0.1) 检查以前加载的类缓存
            clazz = findLoadedClass(name);
            if (clazz != null) {
                if (log.isDebugEnabled())
                    log.debug("  Returning class from cache");
                if (resolve)
                    resolveClass(clazz);
                return (clazz);
            }

            // (0.2) 尝试用系统类装入器加载类, 为了防止程序重写J2SE类. 实现了 SRV.10.7.2
            String resourceName = binaryNameToPath(name, false);

            ClassLoader javaseLoader = getJavaseClassLoader();
            boolean tryLoadingFromJavaseLoader;
            try {
                // 使用getResource, 因为它不会触发昂贵的 ClassNotFoundException, 如果从Java SE类装载器加载的资源不可用，从Java SE类装载器.
                // 但是 (see https://bz.apache.org/bugzilla/show_bug.cgi?id=58125 for details), 在很少的情况下, 在安全管理器下运行时,
                // 这个调用将触发 ClassCircularityError.
                // See https://bz.apache.org/bugzilla/show_bug.cgi?id=61424 for details, 这个怎么触发StackOverflowError
                // 鉴于这些报告错误, 获取Throwable 确保任何其他边缘情况也被捕获
                tryLoadingFromJavaseLoader = (javaseLoader.getResource(resourceName) != null);
            } catch (Throwable t) {
                // 吞下所有的异常，除了那些必须重新抛出的
                ExceptionUtils.handleThrowable(t);
                // getResource() 窍门不适合这个类. 必须直接加载它并接受可能的 ClassNotFoundException.
                tryLoadingFromJavaseLoader = true;
            }

            if (tryLoadingFromJavaseLoader) {
                try {
                    clazz = javaseLoader.loadClass(name);
                    if (clazz != null) {
                        if (resolve)
                            resolveClass(clazz);
                        return (clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            // (0.5) 访问这个类的权限，当使用一个SecurityManager的时候
            if (securityManager != null) {
                int i = name.lastIndexOf('.');
                if (i >= 0) {
                    try {
                        securityManager.checkPackageAccess(name.substring(0,i));
                    } catch (SecurityException se) {
                        String error = "Security Violation, attempt to use " +
                            "Restricted Class: " + name;
                        log.info(error, se);
                        throw new ClassNotFoundException(error, se);
                    }
                }
            }

            boolean delegateLoad = delegate || filter(name, true);

            // (1) 委托给父级
            if (delegateLoad) {
                if (log.isDebugEnabled())
                    log.debug("  Delegating to parent classloader1 " + parent);
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (log.isDebugEnabled())
                            log.debug("  Loading class from parent");
                        if (resolve)
                            resolveClass(clazz);
                        return (clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }

            // (2) 搜索本地库
            if (log.isDebugEnabled())
                log.debug("  Searching local repositories");
            try {
                clazz = findClass(name);
                if (clazz != null) {
                    if (log.isDebugEnabled())
                        log.debug("  Loading class from local repository");
                    if (resolve)
                        resolveClass(clazz);
                    return (clazz);
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }

            // (3) 无条件委托给父级
            if (!delegateLoad) {
                if (log.isDebugEnabled())
                    log.debug("  Delegating to parent classloader at end: " + parent);
                try {
                    clazz = Class.forName(name, false, parent);
                    if (clazz != null) {
                        if (log.isDebugEnabled())
                            log.debug("  Loading class from parent");
                        if (resolve)
                            resolveClass(clazz);
                        return (clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // Ignore
                }
            }
        }

        throw new ClassNotFoundException(name);
    }


    protected void checkStateForClassLoading(String className) throws ClassNotFoundException {
        // 一旦Web应用程序停止，则不允许加载新类.
        try {
            checkStateForResourceLoading(className);
        } catch (IllegalStateException ise) {
            throw new ClassNotFoundException(ise.getMessage(), ise);
        }
    }


    protected void checkStateForResourceLoading(String resource) throws IllegalStateException {
        // 一旦Web应用程序停止，就不允许加载资源.
        if (!state.isAvailable()) {
            String msg = sm.getString("webappClassLoader.stopped", resource);
            IllegalStateException ise = new IllegalStateException(msg);
            log.info(msg, ise);
            throw ise;
        }
    }

    /**
     * 得到一个CodeSource权限. 
     * 如果这个WebappClassLoader实例是web应用上下文, 添加对应资源的读取FilePermission权限.
     *
     * @param codeSource 代码是从哪里加载的
     * @return PermissionCollection for CodeSource
     */
    @Override
    protected PermissionCollection getPermissions(CodeSource codeSource) {

        String codeUrl = codeSource.getLocation().toString();
        PermissionCollection pc;
        if ((pc = loaderPC.get(codeUrl)) == null) {
            pc = super.getPermissions(codeSource);
            if (pc != null) {
                Iterator<Permission> perms = permissionList.iterator();
                while (perms.hasNext()) {
                    Permission p = perms.next();
                    pc.add(p);
                }
                loaderPC.put(codeUrl,pc);
            }
        }
        return (pc);
    }


    @Override
    public boolean check(Permission permission) {
        if (!Globals.IS_SECURITY_ENABLED) {
            return true;
        }
        Policy currentPolicy = Policy.getPolicy();
        if (currentPolicy != null) {
            URL contextRootUrl = resources.getResource("/").getCodeBase();
            CodeSource cs = new CodeSource(contextRootUrl, (Certificate[]) null);
            PermissionCollection pc = currentPolicy.getPermissions(cs);
            if (pc.implies(permission)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 注意，此方法返回的URL列表可能不完整.
     * Web应用程序类加载器访问类加载器资源， 通过支持任意文件的任意映射的{@link WebResourceRoot},
     * WEB-INF/classes下面的目录和JAR文件内容. 任何这样的资源都不会包含在返回的URL中.
     */
    @Override
    public URL[] getURLs() {
        ArrayList<URL> result = new ArrayList<>();
        result.addAll(localRepositories);
        result.addAll(Arrays.asList(super.getURLs()));
        return result.toArray(new URL[result.size()]);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        // NOOP
    }


    /**
     * 获取所有的生命周期监听器.
     * 如果没有, 返回零长度数组.
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return new LifecycleListener[0];
    }


    /**
     * 移除生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        // NOOP
    }


    /**
     * 获取源组件的当前状态.
     */
    @Override
    public LifecycleState getState() {
        return state;
    }


    @Override
    public String getStateName() {
        return getState().toString();
    }


    @Override
    public void init() {
        state = LifecycleState.INITIALIZED;
    }


    /**
     * @exception LifecycleException 如果发生生命周期错误
     */
    @Override
    public void start() throws LifecycleException {

        state = LifecycleState.STARTING_PREP;

        WebResource classes = resources.getResource("/WEB-INF/classes");
        if (classes.isDirectory() && classes.canRead()) {
            localRepositories.add(classes.getURL());
        }
        WebResource[] jars = resources.listResources("/WEB-INF/lib");
        for (WebResource jar : jars) {
            if (jar.getName().endsWith(".jar") && jar.isFile() && jar.canRead()) {
                localRepositories.add(jar.getURL());
                jarModificationTimes.put(
                        jar.getName(), Long.valueOf(jar.getLastModified()));
            }
        }

        state = LifecycleState.STARTED;
    }


    /**
     * @exception LifecycleException 如果发生生命周期错误
     */
    @Override
    public void stop() throws LifecycleException {

        state = LifecycleState.STOPPING_PREP;

        // 清除引用，在started 设置为 false之前, 由于可能的副作用
        clearReferences();

        state = LifecycleState.STOPPING;

        resourceEntries.clear();
        jarModificationTimes.clear();
        resources = null;

        permissionList.clear();
        loaderPC.clear();

        state = LifecycleState.STOPPED;
    }


    @Override
    public void destroy() {
        state = LifecycleState.DESTROYING;

        try {
            super.close();
        } catch (IOException ioe) {
            log.warn(sm.getString("webappClassLoader.superCloseFail"), ioe);
        }
        state = LifecycleState.DESTROYED;
    }


    // ------------------------------------------------------ Protected Methods

    protected ClassLoader getJavaseClassLoader() {
        return javaseClassLoader;
    }

    protected void setJavaseClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException(
                    sm.getString("webappClassLoader.javaseClassLoaderNull"));
        }
        javaseClassLoader = classLoader;
    }

    /**
     * 清除引用.
     */
    protected void clearReferences() {

        // 注销剩余的JDBC 驱动程序
        clearReferencesJdbc();

        // 停止Web应用程序启动的任何线程
        clearReferencesThreads();

        // 检查由这个类加载器加载的 ThreadLocal所引发的漏洞
        checkThreadLocalsForLeaks();

        // 清除由这个类加载器加载的 RMI 目标
        if (clearReferencesRmiTargets) {
            clearReferencesRmiTargets();
        }

         // 清除 IntrospectionUtils 缓存.
        IntrospectionUtils.clear();

        // 清除common-logging中的 classloader引用
        if (clearReferencesLogFactoryRelease) {
            org.apache.juli.logging.LogFactory.release(this);
        }

        // 清除VM的 bean introspector的 classloader引用
        java.beans.Introspector.flushCaches();

        // 清除自定义 URLStreamHandlers
        TomcatURLStreamHandlerFactory.release(this);
    }


    /**
     * 注销Web应用注册的 JDBC 驱动程序.
     * 这是不必要的，因为
     * a) DriverManager 检查调用类的类加载器(如果检查上下文类加载器就容易多了)
     * b) 在DriverManager实现类上使用反射创建引用.
     *
     * 不能只创建一个JdbcLeakPrevention的实例，因为它将被公共类加载器加载 (由于它的 .class 文件在$CATALINA_HOME/lib 目录中).
     * 检查调用类的类加载器上的 DriverManager失败. 因此, 通过父类加载程序加载字节，但是用这个类加载器定义类.
     */
    private final void clearReferencesJdbc() {
        // 这个类大概有 (~ 1K), 因此允许 2k 作为起点
        byte[] classBytes = new byte[2048];
        int offset = 0;
        try (InputStream is = getResourceAsStream(
                "org/apache/catalina/loader/JdbcLeakPrevention.class")) {
            int read = is.read(classBytes, offset, classBytes.length-offset);
            while (read > -1) {
                offset += read;
                if (offset == classBytes.length) {
                    // Buffer full - double size
                    byte[] tmp = new byte[classBytes.length * 2];
                    System.arraycopy(classBytes, 0, tmp, 0, classBytes.length);
                    classBytes = tmp;
                }
                read = is.read(classBytes, offset, classBytes.length-offset);
            }
            Class<?> lpClass =
                defineClass("org.apache.catalina.loader.JdbcLeakPrevention",
                    classBytes, 0, offset, this.getClass().getProtectionDomain());
            Object obj = lpClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            List<String> driverNames = (List<String>) obj.getClass().getMethod(
                    "clearJdbcDriverRegistrations").invoke(obj);
            for (String name : driverNames) {
                log.warn(sm.getString("webappClassLoader.clearJdbc",
                        getContextName(), name));
            }
        } catch (Exception e) {
            // 上面有很多事情出错...
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString(
                    "webappClassLoader.jdbcRemoveFailed", getContextName()), t);
        }
    }


    @SuppressWarnings("deprecation") // thread.stop()
    private void clearReferencesThreads() {
        Thread[] threads = getThreads();
        List<Thread> executorThreadsToStop = new ArrayList<>();

        // Iterate over the set of threads
        for (Thread thread : threads) {
            if (thread != null) {
                ClassLoader ccl = thread.getContextClassLoader();
                if (ccl == this) {
                    // Don't warn about this thread
                    if (thread == Thread.currentThread()) {
                        continue;
                    }

                    final String threadName = thread.getName();

                    // JVM controlled threads
                    ThreadGroup tg = thread.getThreadGroup();
                    if (tg != null && JVM_THREAD_GROUP_NAMES.contains(tg.getName())) {
                        // HttpClient keep-alive threads
                        if (clearReferencesHttpClientKeepAliveThread &&
                                threadName.equals("Keep-Alive-Timer")) {
                            thread.setContextClassLoader(parent);
                            log.debug(sm.getString("webappClassLoader.checkThreadsHttpClient"));
                        }

                        // 不要警告剩余的JVM控制线程
                        continue;
                    }

                    // 跳过已经死亡的线程
                    if (!thread.isAlive()) {
                        continue;
                    }

                    // TimerThread 可以安全地停止, 所以分别对待
                    // "java.util.TimerThread" in Sun/Oracle JDK
                    // "java.util.Timer$TimerImpl" in Apache Harmony and in IBM JDK
                    if (thread.getClass().getName().startsWith("java.util.Timer") &&
                            clearReferencesStopTimerThreads) {
                        clearReferencesStopTimerThread(thread);
                        continue;
                    }

                    if (isRequestThread(thread)) {
                        log.warn(sm.getString("webappClassLoader.stackTraceRequestThread",
                                getContextName(), threadName, getStackTrace(thread)));
                    } else {
                        log.warn(sm.getString("webappClassLoader.stackTrace",
                                getContextName(), threadName, getStackTrace(thread)));
                    }

                    // 除非明确配置，否则不要尝试停止线程
                    if (!clearReferencesStopThreads) {
                        continue;
                    }

                    // 如果线程已通过executor启动, 尝试关闭executor
                    boolean usingExecutor = false;
                    try {

                        // Runnable wrapped by Thread
                        // "target" in Sun/Oracle JDK
                        // "runnable" in IBM JDK
                        // "action" in Apache Harmony
                        Object target = null;
                        for (String fieldName : new String[] { "target", "runnable", "action" }) {
                            try {
                                Field targetField = thread.getClass().getDeclaredField(fieldName);
                                targetField.setAccessible(true);
                                target = targetField.get(thread);
                                break;
                            } catch (NoSuchFieldException nfe) {
                                continue;
                            }
                        }

                        // "java.util.concurrent"代码是 public 的, 所以所有的实现都是相似的
                        if (target != null && target.getClass().getCanonicalName() != null &&
                                target.getClass().getCanonicalName().equals(
                                "java.util.concurrent.ThreadPoolExecutor.Worker")) {
                            Field executorField = target.getClass().getDeclaredField("this$0");
                            executorField.setAccessible(true);
                            Object executor = executorField.get(target);
                            if (executor instanceof ThreadPoolExecutor) {
                                ((ThreadPoolExecutor) executor).shutdownNow();
                                usingExecutor = true;
                            }
                        }
                    } catch (SecurityException | NoSuchFieldException | IllegalArgumentException |
                            IllegalAccessException e) {
                        log.warn(sm.getString("webappClassLoader.stopThreadFail",
                                thread.getName(), getContextName()), e);
                    }

                    if (usingExecutor) {
                        // Executor 可能需要很短的时间来停止所有线程. 记下应该停止的线程，并在方法的末尾检查它们.
                        executorThreadsToStop.add(thread);
                    } else {
                        // 这种方法是不合理的，有充分的理由. 这是非常危险的代码，但在这一点上是唯一的选择.
                        // 最好让应用程序自己清理.
                        thread.stop();
                    }
                }
            }
        }

        // 如果启用线程停止, 当执行器关闭时executor 线程应该停止，但这取决于线程正确地处理中断.
        // 给所有executor线程几秒钟关闭，如果它们正在运行
        // 给线程2秒关闭
        int count = 0;
        for (Thread t : executorThreadsToStop) {
            while (t.isAlive() && count < 100) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    break;
                }
                count++;
            }
            if (t.isAlive()) {
            	// 这种方法是不合理的，有充分的理由. 这是非常危险的代码，但在这一点上是唯一的选择.
                // 最好让应用程序自己清理.
                t.stop();
            }
        }
    }


    /*
     * 查看线程堆栈跟踪，以查看它是否是请求线程. 它不是最好的, 但对大多数情况来说应该足够好.
     */
    private boolean isRequestThread(Thread thread) {

        StackTraceElement[] elements = thread.getStackTrace();

        if (elements == null || elements.length == 0) {
            // 一定已经停止了. 假设不是请求处理线程.
            return false;
        }

        // 以相反顺序查找调用CoyoteAdapter 方法. 除非Tomcat进行了大量修改，否则所有请求线程都将拥有此线程 - 在这种情况下没有多少
        // can do.
        for (int i = 0; i < elements.length; i++) {
            StackTraceElement element = elements[elements.length - (i+1)];
            if ("org.apache.catalina.connector.CoyoteAdapter".equals(
                    element.getClassName())) {
                return true;
            }
        }
        return false;
    }


    private void clearReferencesStopTimerThread(Thread thread) {

        // 需要获取引用:
        // in Sun/Oracle JDK:
        // - newTasksMayBeScheduled 字段 (in java.util.TimerThread)
        // - queue field
        // - queue.clear()
        // in IBM JDK, Apache Harmony:
        // - cancel() method (in java.util.Timer$TimerImpl)

        try {

            try {
                Field newTasksMayBeScheduledField =
                    thread.getClass().getDeclaredField("newTasksMayBeScheduled");
                newTasksMayBeScheduledField.setAccessible(true);
                Field queueField = thread.getClass().getDeclaredField("queue");
                queueField.setAccessible(true);

                Object queue = queueField.get(thread);

                Method clearMethod = queue.getClass().getDeclaredMethod("clear");
                clearMethod.setAccessible(true);

                synchronized(queue) {
                    newTasksMayBeScheduledField.setBoolean(thread, false);
                    clearMethod.invoke(queue);
                    queue.notify();  // In case queue was already empty.
                }

            }catch (NoSuchFieldException nfe){
                Method cancelMethod = thread.getClass().getDeclaredMethod("cancel");
                synchronized(thread) {
                    cancelMethod.setAccessible(true);
                    cancelMethod.invoke(thread);
                }
            }

            log.warn(sm.getString("webappClassLoader.warnTimerThread",
                    getContextName(), thread.getName()));

        } catch (Exception e) {
            // 上面有很多事情出错...
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString(
                    "webappClassLoader.stopTimerThreadFail",
                    thread.getName(), getContextName()), t);
        }
    }

    private void checkThreadLocalsForLeaks() {
        Thread[] threads = getThreads();

        try {
            // 获取Thread 类中保存的ThreadLocal字段
            Field threadLocalsField =
                Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);
            Field inheritableThreadLocalsField =
                Thread.class.getDeclaredField("inheritableThreadLocals");
            inheritableThreadLocalsField.setAccessible(true);
            // Make the underlying array of ThreadLoad.ThreadLocalMap.Entry objects
            // accessible
            Class<?> tlmClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            Field tableField = tlmClass.getDeclaredField("table");
            tableField.setAccessible(true);
            Method expungeStaleEntriesMethod = tlmClass.getDeclaredMethod("expungeStaleEntries");
            expungeStaleEntriesMethod.setAccessible(true);

            for (int i = 0; i < threads.length; i++) {
                Object threadLocalMap;
                if (threads[i] != null) {

                    // 清除第一个 map
                    threadLocalMap = threadLocalsField.get(threads[i]);
                    if (null != threadLocalMap){
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }

                    // 清除第二个 map
                    threadLocalMap =inheritableThreadLocalsField.get(threads[i]);
                    if (null != threadLocalMap){
                        expungeStaleEntriesMethod.invoke(threadLocalMap);
                        checkThreadLocalMapForLeaks(threadLocalMap, tableField);
                    }
                }
            }
        } catch (Throwable t) {
            JreCompat jreCompat = JreCompat.getInstance();
            if (jreCompat.isInstanceOfInaccessibleObjectException(t)) {
                // 必须运行在java 9，没有必要的命令行选项.
                log.warn(sm.getString("webappClassLoader.addExportsThreadLocal"));
            } else {
                ExceptionUtils.handleThrowable(t);
                log.warn(sm.getString(
                        "webappClassLoader.checkThreadLocalsForLeaksFail",
                        getContextName()), t);
            }
        }
    }


    /**
     * 分析给定ThreadLocal map对象. 也可以在指向内部表的字段中保存，以在对该方法的每次调用中重新计算它.
     */
    private void checkThreadLocalMapForLeaks(Object map,
            Field internalTableField) throws IllegalAccessException,
            NoSuchFieldException {
        if (map != null) {
            Object[] table = (Object[]) internalTableField.get(map);
            if (table != null) {
                for (int j =0; j < table.length; j++) {
                    Object obj = table[j];
                    if (obj != null) {
                        boolean keyLoadedByWebapp = false;
                        boolean valueLoadedByWebapp = false;
                        // Check the key
                        Object key = ((Reference<?>) obj).get();
                        if (this.equals(key) || loadedByThisOrChild(key)) {
                            keyLoadedByWebapp = true;
                        }
                        // Check the value
                        Field valueField =
                                obj.getClass().getDeclaredField("value");
                        valueField.setAccessible(true);
                        Object value = valueField.get(obj);
                        if (this.equals(value) || loadedByThisOrChild(value)) {
                            valueLoadedByWebapp = true;
                        }
                        if (keyLoadedByWebapp || valueLoadedByWebapp) {
                            Object[] args = new Object[5];
                            args[0] = getContextName();
                            if (key != null) {
                                args[1] = getPrettyClassName(key.getClass());
                                try {
                                    args[2] = key.toString();
                                } catch (Exception e) {
                                    log.warn(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaks.badKey",
                                            args[1]), e);
                                    args[2] = sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaks.unknown");
                                }
                            }
                            if (value != null) {
                                args[3] = getPrettyClassName(value.getClass());
                                try {
                                    args[4] = value.toString();
                                } catch (Exception e) {
                                    log.warn(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaks.badValue",
                                            args[3]), e);
                                    args[4] = sm.getString(
                                    "webappClassLoader.checkThreadLocalsForLeaks.unknown");
                                }
                            }
                            if (valueLoadedByWebapp) {
                                log.error(sm.getString(
                                        "webappClassLoader.checkThreadLocalsForLeaks",
                                        args));
                            } else if (value == null) {
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaksNull",
                                            args));
                                }
                            } else {
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString(
                                            "webappClassLoader.checkThreadLocalsForLeaksNone",
                                            args));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private String getPrettyClassName(Class<?> clazz) {
        String name = clazz.getCanonicalName();
        if (name==null){
            name = clazz.getName();
        }
        return name;
    }

    private String getStackTrace(Thread thread) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement ste : thread.getStackTrace()) {
            builder.append("\n ").append(ste);
        }
        return builder.toString();
    }

    /**
     * @param o 要测试的对象, 可能是 null
     * 
     * @return <code>true</code>如果是通过当前类加载器或者它的子类加载的
     */
    private boolean loadedByThisOrChild(Object o) {
        if (o == null) {
            return false;
        }

        Class<?> clazz;
        if (o instanceof Class) {
            clazz = (Class<?>) o;
        } else {
            clazz = o.getClass();
        }

        ClassLoader cl = clazz.getClassLoader();
        while (cl != null) {
            if (cl == this) {
                return true;
            }
            cl = cl.getParent();
        }

        if (o instanceof Collection<?>) {
            Iterator<?> iter = ((Collection<?>) o).iterator();
            try {
                while (iter.hasNext()) {
                    Object entry = iter.next();
                    if (loadedByThisOrChild(entry)) {
                        return true;
                    }
                }
            } catch (ConcurrentModificationException e) {
                log.warn(sm.getString(
                        "webappClassLoader.loadedByThisOrChildFail", clazz.getName(), getContextName()),
                        e);
            }
        }
        return false;
    }

    /*
     * 将当前线程集合转换为数组.
     */
    private Thread[] getThreads() {
        // 获取当前线程组
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        // 查找根线程组
        try {
            while (tg.getParent() != null) {
                tg = tg.getParent();
            }
        } catch (SecurityException se) {
            String msg = sm.getString(
                    "webappClassLoader.getThreadGroupError", tg.getName());
            if (log.isDebugEnabled()) {
                log.debug(msg, se);
            } else {
                log.warn(msg);
            }
        }

        int threadCountGuess = tg.activeCount() + 50;
        Thread[] threads = new Thread[threadCountGuess];
        int threadCountActual = tg.enumerate(threads);
        // 确保不会错过任何线程
        while (threadCountActual == threadCountGuess) {
            threadCountGuess *=2;
            threads = new Thread[threadCountGuess];
            // Note tg.enumerate(Thread[]) 默默地忽略任何无法加入数组的线程
            threadCountActual = tg.enumerate(threads);
        }

        return threads;
    }


    /**
     * 这取决于Sun JVM的内部结构，所以它通过反射来完成一切.
     */
    private void clearReferencesRmiTargets() {
        try {
            // 需要访问sun.rmi.transport.Target的 ccl 字段查找漏洞
            Class<?> objectTargetClass =
                Class.forName("sun.rmi.transport.Target");
            Field cclField = objectTargetClass.getDeclaredField("ccl");
            cclField.setAccessible(true);
            // 需要访问存根字段来报告泄漏
            Field stubField = objectTargetClass.getDeclaredField("stub");
            stubField.setAccessible(true);

            // Clear the objTable map
            Class<?> objectTableClass =
                Class.forName("sun.rmi.transport.ObjectTable");
            Field objTableField = objectTableClass.getDeclaredField("objTable");
            objTableField.setAccessible(true);
            Object objTable = objTableField.get(null);
            if (objTable == null) {
                return;
            }

            synchronized (objTable) {
                // 对表中的值进行迭代
                if (objTable instanceof Map<?,?>) {
                    Iterator<?> iter = ((Map<?,?>) objTable).values().iterator();
                    while (iter.hasNext()) {
                        Object obj = iter.next();
                        Object cclObject = cclField.get(obj);
                        if (this == cclObject) {
                            iter.remove();
                            Object stubObject = stubField.get(obj);
                            log.error(sm.getString("webappClassLoader.clearRmi",
                                    stubObject.getClass().getName(), stubObject));
                        }
                    }
                }

                // Clear the implTable map
                Field implTableField = objectTableClass.getDeclaredField("implTable");
                implTableField.setAccessible(true);
                Object implTable = implTableField.get(null);
                if (implTable == null) {
                    return;
                }

                // Iterate over the values in the table
                if (implTable instanceof Map<?,?>) {
                    Iterator<?> iter = ((Map<?,?>) implTable).values().iterator();
                    while (iter.hasNext()) {
                        Object obj = iter.next();
                        Object cclObject = cclField.get(obj);
                        if (this == cclObject) {
                            iter.remove();
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log.info(sm.getString("webappClassLoader.clearRmiInfo",
                    getContextName()), e);
        } catch (SecurityException | NoSuchFieldException | IllegalArgumentException |
                IllegalAccessException e) {
            log.warn(sm.getString("webappClassLoader.clearRmiFail",
                    getContextName()), e);
        } catch (Exception e) {
            JreCompat jreCompat = JreCompat.getInstance();
            if (jreCompat.isInstanceOfInaccessibleObjectException(e)) {
                // 必须运行在java 9，没有必要的命令行选项.
                log.warn(sm.getString("webappClassLoader.addExportsRmi"));
            } else {
                // Re-throw all other exceptions
                throw e;
            }
        }
    }


    /**
     * 在本地存储库中找到指定的类.
     *
     * @param name 要加载的类的二进制名
     *
     * @return 加载的类, 或 null
     */
    protected Class<?> findClassInternal(String name) {

        checkStateForResourceLoading(name);

        if (name == null) {
            return null;
        }
        String path = binaryNameToPath(name, true);

        ResourceEntry entry = resourceEntries.get(path);
        WebResource resource = null;

        if (entry == null) {
            resource = resources.getClassLoaderResource(path);

            if (!resource.exists()) {
                return null;
            }

            entry = new ResourceEntry();
            entry.lastModified = resource.getLastModified();

            // 在本地资源库中添加条目
            synchronized (resourceEntries) {
                // 确保所有可能在竞争中的线程加载特定的类，最终以相同的ResourceEntry实例结束
                ResourceEntry entry2 = resourceEntries.get(path);
                if (entry2 == null) {
                    resourceEntries.put(path, entry);
                } else {
                    entry = entry2;
                }
            }
        }

        Class<?> clazz = entry.loadedClass;
        if (clazz != null)
            return clazz;

        synchronized (getClassLoadingLock(name)) {
            clazz = entry.loadedClass;
            if (clazz != null)
                return clazz;

            if (resource == null) {
                resource = resources.getClassLoaderResource(path);
            }

            if (!resource.exists()) {
                return null;
            }

            byte[] binaryContent = resource.getContent();
            Manifest manifest = resource.getManifest();
            URL codeBase = resource.getCodeBase();
            Certificate[] certificates = resource.getCertificates();

            if (transformers.size() > 0) {
                // 如果资源是正在加载的类, 用任何附加的转换器装饰它
                String className = name.endsWith(CLASS_FILE_SUFFIX) ?
                        name.substring(0, name.length() - CLASS_FILE_SUFFIX.length()) : name;
                String internalName = className.replace(".", "/");

                for (ClassFileTransformer transformer : this.transformers) {
                    try {
                        byte[] transformed = transformer.transform(
                                this, internalName, null, null, binaryContent);
                        if (transformed != null) {
                            binaryContent = transformed;
                        }
                    } catch (IllegalClassFormatException e) {
                        log.error(sm.getString("webappClassLoader.transformError", name), e);
                        return null;
                    }
                }
            }

            // Looking up the package
            String packageName = null;
            int pos = name.lastIndexOf('.');
            if (pos != -1)
                packageName = name.substring(0, pos);

            Package pkg = null;

            if (packageName != null) {
                pkg = getPackage(packageName);
                // Define the package (if null)
                if (pkg == null) {
                    try {
                        if (manifest == null) {
                            definePackage(packageName, null, null, null, null, null, null, null);
                        } else {
                            definePackage(packageName, manifest, codeBase);
                        }
                    } catch (IllegalArgumentException e) {
                        // Ignore: 正常的错误，由于包装的双重定义
                    }
                    pkg = getPackage(packageName);
                }
            }

            if (securityManager != null) {

                // Checking sealing
                if (pkg != null) {
                    boolean sealCheck = true;
                    if (pkg.isSealed()) {
                        sealCheck = pkg.isSealed(codeBase);
                    } else {
                        sealCheck = (manifest == null) || !isPackageSealed(packageName, manifest);
                    }
                    if (!sealCheck)
                        throw new SecurityException
                            ("Sealing violation loading " + name + " : Package "
                             + packageName + " is sealed.");
                }

            }

            try {
                clazz = defineClass(name, binaryContent, 0,
                        binaryContent.length, new CodeSource(codeBase, certificates));
            } catch (UnsupportedClassVersionError ucve) {
                throw new UnsupportedClassVersionError(
                        ucve.getLocalizedMessage() + " " +
                        sm.getString("webappClassLoader.wrongVersion",
                                name));
            }
            entry.loadedClass = clazz;
        }

        return clazz;
    }


    private String binaryNameToPath(String binaryName, boolean withLeadingSlash) {
        // 1 for leading '/', 6 for ".class"
        StringBuilder path = new StringBuilder(7 + binaryName.length());
        if (withLeadingSlash) {
            path.append('/');
        }
        path.append(binaryName.replace('.', '/'));
        path.append(CLASS_FILE_SUFFIX);
        return path.toString();
    }


    private String nameToPath(String name) {
        if (name.startsWith("/")) {
            return name;
        }
        StringBuilder path = new StringBuilder(
                1 + name.length());
        path.append('/');
        path.append(name);
        return path.toString();
    }


    /**
     * 如果指定的包名是根据给定的清单密封的，则返回true.
     *
     * @param name 要检查的路径名
     * @param man 关联的清单
     * @return <code>true</code>如果关联的清单是密封的
     */
    protected boolean isPackageSealed(String name, Manifest man) {

        String path = name.replace('.', '/') + '/';
        Attributes attr = man.getAttributes(path);
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);

    }


    /**
     * 如果前面已加载并缓存此类加载器，则使用给定名称查找类, 并返回Class 对象.
     * 如果这个类没有被缓存, 返回<code>null</code>.
     *
     * @param name 要返回的资源的二进制名称
     * @return 加载的类
     */
    protected Class<?> findLoadedClass0(String name) {

        String path = binaryNameToPath(name, true);

        ResourceEntry entry = resourceEntries.get(path);
        if (entry != null) {
            return entry.loadedClass;
        }
        return null;
    }


    /**
     * 刷新系统策略文件, 拾起最终的修改.
     */
    protected void refreshPolicy() {

        try {
            // 策略文件可能已被修改以调整权限, 所以当加载或重新加载上下文时，重新加载它
            Policy policy = Policy.getPolicy();
            policy.refresh();
        } catch (AccessControlException e) {
            // 一些策略文件可能会限制这一点, 即使是核心, 因此忽略此异常
        }

    }


    /**
     * @param name 类名
     * @param isClassName <code>true</code>如果名称是类名,
     *                <code>false</code>如果名称是资源名
     * @return <code>true</code>如果该类应该被过滤
     */
    protected boolean filter(String name, boolean isClassName) {

        if (name == null)
            return false;

        char ch;
        if (name.startsWith("javax")) {
            /* 5 == length("javax") */
            if (name.length() == 5) {
                return false;
            }
            ch = name.charAt(5);
            if (isClassName && ch == '.') {
                /* 6 == length("javax.") */
                if (name.startsWith("servlet.jsp.jstl.", 6)) {
                    return false;
                }
                if (name.startsWith("el.", 6) ||
                    name.startsWith("servlet.", 6) ||
                    name.startsWith("websocket.", 6) ||
                    name.startsWith("security.auth.message.", 6)) {
                    return true;
                }
            } else if (!isClassName && ch == '/') {
                /* 6 == length("javax/") */
                if (name.startsWith("servlet/jsp/jstl/", 6)) {
                    return false;
                }
                if (name.startsWith("el/", 6) ||
                    name.startsWith("servlet/", 6) ||
                    name.startsWith("websocket/", 6) ||
                    name.startsWith("security/auth/message/", 6)) {
                    return true;
                }
            }
        } else if (name.startsWith("org")) {
            /* 3 == length("org") */
            if (name.length() == 3) {
                return false;
            }
            ch = name.charAt(3);
            if (isClassName && ch == '.') {
                /* 4 == length("org.") */
                if (name.startsWith("apache.", 4)) {
                    /* 11 == length("org.apache.") */
                    if (name.startsWith("tomcat.jdbc.", 11)) {
                        return false;
                    }
                    if (name.startsWith("el.", 11) ||
                        name.startsWith("catalina.", 11) ||
                        name.startsWith("jasper.", 11) ||
                        name.startsWith("juli.", 11) ||
                        name.startsWith("tomcat.", 11) ||
                        name.startsWith("naming.", 11) ||
                        name.startsWith("coyote.", 11)) {
                        return true;
                    }
                }
            } else if (!isClassName && ch == '/') {
                /* 4 == length("org/") */
                if (name.startsWith("apache/", 4)) {
                    /* 11 == length("org/apache/") */
                    if (name.startsWith("tomcat/jdbc/", 11)) {
                        return false;
                    }
                    if (name.startsWith("el/", 11) ||
                        name.startsWith("catalina/", 11) ||
                        name.startsWith("jasper/", 11) ||
                        name.startsWith("juli/", 11) ||
                        name.startsWith("tomcat/", 11) ||
                        name.startsWith("naming/", 11) ||
                        name.startsWith("coyote/", 11)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * @param name 类名
     * @return <code>true</code>如果该类应该被过滤
     * @deprecated Use {@link #filter(String, boolean)} This will be removed in Tomcat 9
     */
    @Deprecated
    protected boolean filter(String name) {
        return filter(name, true) || filter(name, false);
    }


    @Override
    protected void addURL(URL url) {
        super.addURL(url);
        hasExternalRepositories = true;
    }


    @Override
    public String getWebappName() {
        return getContextName();
    }


    @Override
    public String getHostName() {
        if (resources != null) {
            Container host = resources.getContext().getParent();
            if (host != null) {
                return host.getName();
            }
        }
        return null;
    }


    @Override
    public String getServiceName() {
        if (resources != null) {
            Container host = resources.getContext().getParent();
            if (host != null) {
                Container engine = host.getParent();
                if (engine != null) {
                    return engine.getName();
                }
            }
        }
        return null;
    }


    @Override
    public boolean hasLoggingConfig() {
        if (Globals.IS_SECURITY_ENABLED) {
            Boolean result = AccessController.doPrivileged(new PrivilegedHasLoggingConfig());
            return result.booleanValue();
        } else {
            return findResource("logging.properties") != null;
        }
    }


    private class PrivilegedHasLoggingConfig implements PrivilegedAction<Boolean> {

        @Override
        public Boolean run() {
            return Boolean.valueOf(findResource("logging.properties") != null);
        }
    }
}
