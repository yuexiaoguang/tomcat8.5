package org.apache.catalina.loader;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;

import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * Classloader实现类，它专门以最有效的方式处理Web应用程序, 在Catalina意识中(所有资源的访问是通过{@link org.apache.catalina.WebResourceRoot}).
 * 这个类装载器支持java类修改的检测, 它可以用来实现自动重新加载支持.
 * <p>
 * 这个类加载器通过Resources配置, 在调用<code>start()</code>之前. 当需要一个新的类时, 这些Resources 将首先咨询来定位类.
 * 如果它不在, 将使用系统类加载器代替.
 */
public class WebappLoader extends LifecycleMBeanBase implements Loader, PropertyChangeListener {


    // ----------------------------------------------------------- Constructors

    public WebappLoader() {
        this(null);
    }


    /**
     * @param parent 父类加载器
     */
    public WebappLoader(ClassLoader parent) {
        super();
        this.parentClassLoader = parent;
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 被管理的类加载器.
     */
    private WebappClassLoaderBase classLoader = null;


    /**
     * 关联的Context.
     */
    private Context context = null;


    /**
     * 将用于ClassLoader配置的“遵循标准委托模型”标志.
     */
    private boolean delegate = false;


    /**
     * ClassLoader实现类的类名.
     * 这个类应该继承WebappClassLoader, 否则, 另外一个加载器实现类将被使用
     */
    private String loaderClass = ParallelWebappClassLoader.class.getName();


    /**
     * 将创建的类加载器的父类加载器.
     */
    private ClassLoader parentClassLoader = null;


    /**
     * 重载标志.
     */
    private boolean reloadable = false;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 属性修改支持.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 加载器的Classpath.
     */
    private String classpath = null;


    // ------------------------------------------------------------- Properties

    /**
     * 返回这个Container使用的Java类加载器.
     */
    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }


    @Override
    public Context getContext() {
        return context;
    }


    @Override
    public void setContext(Context context) {

        if (this.context == context) {
            return;
        }

        if (getState().isAvailable()) {
            throw new IllegalStateException(
                    sm.getString("webappLoader.setContext.ise"));
        }

        // 从旧的Context注销
        if (this.context != null) {
            this.context.removePropertyChangeListener(this);
        }

        // 处理此属性更改
        Context oldContext = this.context;
        this.context = context;
        support.firePropertyChange("context", oldContext, this.context);

        // 注册进新的Container (if any)
        if (this.context != null) {
            setReloadable(this.context.getReloadable());
            this.context.addPropertyChangeListener(this);
        }
    }


    /**
     * 返回用于配置ClassLoader的“遵循标准委托模型”标志.
     */
    @Override
    public boolean getDelegate() {
        return this.delegate;
    }


    /**
     * 设置用于配置ClassLoader的“遵循标准委托模型”标志.
     *
     * @param delegate The new flag
     */
    @Override
    public void setDelegate(boolean delegate) {
        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", Boolean.valueOf(oldDelegate),
                                   Boolean.valueOf(this.delegate));
    }


    /**
     * @return ClassLoader的类名.
     */
    public String getLoaderClass() {
        return (this.loaderClass);
    }


    /**
     * 设置ClassLoader类名.
     *
     * @param loaderClass The new ClassLoader class name
     */
    public void setLoaderClass(String loaderClass) {
        this.loaderClass = loaderClass;
    }


    /**
     * 返回重新加载标志.
     */
    @Override
    public boolean getReloadable() {
        return this.reloadable;
    }


    /**
     * 设置重新加载标志.
     *
     * @param reloadable The new reloadable flag
     */
    @Override
    public void setReloadable(boolean reloadable) {
        // Process this property change
        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   Boolean.valueOf(oldReloadable),
                                   Boolean.valueOf(this.reloadable));
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    @Override
    public void backgroundProcess() {
        if (reloadable && modified()) {
            try {
                Thread.currentThread().setContextClassLoader
                    (WebappLoader.class.getClassLoader());
                if (context != null) {
                    context.reload();
                }
            } finally {
                if (context != null && context.getLoader() != null) {
                    Thread.currentThread().setContextClassLoader
                        (context.getLoader().getClassLoader());
                }
            }
        }
    }


    public String[] getLoaderRepositories() {
        if (classLoader == null) {
            return new String[0];
        }
        URL[] urls = classLoader.getURLs();
        String[] result = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            result[i] = urls[i].toExternalForm();
        }
        return result;
    }

    public String getLoaderRepositoriesString() {
        String repositories[]=getLoaderRepositories();
        StringBuilder sb=new StringBuilder();
        for( int i=0; i<repositories.length ; i++ ) {
            sb.append( repositories[i]).append(":");
        }
        return sb.toString();
    }


    /**
     * Classpath, 在org.apache.catalina.jsp_classpath 上下文属性中设置
     *
     * @return The classpath
     */
    public String getClasspath() {
        return classpath;
    }


    /**
     * 与此加载程序关联的内部存储库是否已被修改, 要重新加载类?
     */
    @Override
    public boolean modified() {
        return classLoader != null ? classLoader.modified() : false ;
    }


    /**
     * 移除一个属性修改监听器.
     *
     * @param listener 要删除的监听器
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WebappLoader[");
        if (context != null)
            sb.append(context.getName());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.starting"));

        if (context.getResources() == null) {
            log.info("No resources for " + context);
            setState(LifecycleState.STARTING);
            return;
        }

        // 基于当前的存储库列表构建类加载器
        try {

            classLoader = createClassLoader();
            classLoader.setResources(context.getResources());
            classLoader.setDelegate(this.delegate);

            // 配置库
            setClassPath();

            setPermissions();

            ((Lifecycle) classLoader).start();

            String contextName = context.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            ObjectName cloname = new ObjectName(context.getDomain() + ":type=" +
                    classLoader.getClass().getSimpleName() + ",host=" +
                    context.getParent().getName() + ",context=" + contextName);
            Registry.getRegistry(null, null)
                .registerComponent(classLoader, cloname, null);

        } catch (Throwable t) {
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
            log.error( "LifecycleException ", t );
            throw new LifecycleException("start: ", t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.stopping"));

        setState(LifecycleState.STOPPING);

        // 适当删除上下文属性
        ServletContext servletContext = context.getServletContext();
        servletContext.removeAttribute(Globals.CLASS_PATH_ATTR);

        // Throw away our current class loader if any
        if (classLoader != null) {
            try {
                classLoader.stop();
            } finally {
                classLoader.destroy();
            }

            // classLoader must be non-null to have been registered
            try {
                String contextName = context.getName();
                if (!contextName.startsWith("/")) {
                    contextName = "/" + contextName;
                }
                ObjectName cloname = new ObjectName(context.getDomain() + ":type=" +
                        classLoader.getClass().getSimpleName() + ",host=" +
                        context.getParent().getName() + ",context=" + contextName);
                Registry.getRegistry(null, null).unregisterComponent(cloname);
            } catch (Exception e) {
                log.error("LifecycleException ", e);
            }
        }

        classLoader = null;
    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * 处理属性修改事件.
     *
     * @param event 已发生的属性更改事件
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;

        // 处理相关的属性修改
        if (event.getPropertyName().equals("reloadable")) {
            try {
                setReloadable( ((Boolean) event.getNewValue()).booleanValue() );
            } catch (NumberFormatException e) {
                log.error(sm.getString("webappLoader.reloadable",
                                 event.getNewValue().toString()));
            }
        }
    }


    // ------------------------------------------------------- Private Methods

    /**
     * 创建关联的 classLoader.
     */
    private WebappClassLoaderBase createClassLoader()
        throws Exception {

        Class<?> clazz = Class.forName(loaderClass);
        WebappClassLoaderBase classLoader = null;

        if (parentClassLoader == null) {
            parentClassLoader = context.getParentClassLoader();
        }
        Class<?>[] argTypes = { ClassLoader.class };
        Object[] args = { parentClassLoader };
        Constructor<?> constr = clazz.getConstructor(argTypes);
        classLoader = (WebappClassLoaderBase) constr.newInstance(args);

        return classLoader;
    }


    /**
     * 配置关联的类加载器权限.
     */
    private void setPermissions() {

        if (!Globals.IS_SECURITY_ENABLED)
            return;
        if (context == null)
            return;

        // 告诉类加载器上下文的根
        ServletContext servletContext = context.getServletContext();

        // 为工作目录分配权限
        File workDir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        if (workDir != null) {
            try {
                String workDirPath = workDir.getCanonicalPath();
                classLoader.addPermission
                    (new FilePermission(workDirPath, "read,write"));
                classLoader.addPermission
                    (new FilePermission(workDirPath + File.separator + "-",
                                        "read,write,delete"));
            } catch (IOException e) {
                // Ignore
            }
        }

        for (URL url : context.getResources().getBaseUrls()) {
           classLoader.addPermission(url);
        }
    }


    /**
     * 为类路径设置适当的上下文属性.
     * 这仅仅是因为Jasper需要它.
     */
    private void setClassPath() {

        // Validate our current state information
        if (context == null)
            return;
        ServletContext servletContext = context.getServletContext();
        if (servletContext == null)
            return;

        StringBuilder classpath = new StringBuilder();

        // 从类加载器链中组装类路径信息
        ClassLoader loader = getClassLoader();

        if (delegate && loader != null) {
            // 在启用委托时跳过WebApp加载器
            loader = loader.getParent();
        }

        while (loader != null) {
            if (!buildClassPath(classpath, loader)) {
                break;
            }
            loader = loader.getParent();
        }

        if (delegate) {
            // 启用委托, 返回并添加WebApp路径
            loader = getClassLoader();
            if (loader != null) {
                buildClassPath(classpath, loader);
            }
        }

        this.classpath = classpath.toString();

        // 将已组装的类路径存储为servlet上下文属性
        servletContext.setAttribute(Globals.CLASS_PATH_ATTR, this.classpath);
    }


    private boolean buildClassPath(StringBuilder classpath, ClassLoader loader) {
        if (loader instanceof URLClassLoader) {
            URL repositories[] = ((URLClassLoader) loader).getURLs();
                for (int i = 0; i < repositories.length; i++) {
                    String repository = repositories[i].toString();
                    if (repository.startsWith("file://"))
                        repository = utf8Decode(repository.substring(7));
                    else if (repository.startsWith("file:"))
                        repository = utf8Decode(repository.substring(5));
                    else
                        continue;
                    if (repository == null)
                        continue;
                    if (classpath.length() > 0)
                        classpath.append(File.pathSeparator);
                    classpath.append(repository);
                }
        } else if (loader == ClassLoader.getSystemClassLoader()){
            // Java 9 onwards. 内部类加载器不再扩展URLCLassLoader
            String cp = System.getProperty("java.class.path");
            if (cp != null && cp.length() > 0) {
                if (classpath.length() > 0) {
                    classpath.append(File.pathSeparator);
                }
                classpath.append(cp);
            }
            return false;
        } else {
            log.info( "Unknown loader " + loader + " " + loader.getClass());
            return false;
        }
        return true;
    }

    private String utf8Decode(String input) {
        String result = null;
        try {
            result = URLDecoder.decode(input, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            // Impossible. 所有JVM需要支持 UTF-8.
        }
        return result;
    }


    private static final Log log = LogFactory.getLog(WebappLoader.class);


    @Override
    protected String getDomainInternal() {
        return context.getDomain();
    }

    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder name = new StringBuilder("type=Loader");

        name.append(",host=");
        name.append(context.getParent().getName());

        name.append(",context=");

        String contextName = context.getName();
        if (!contextName.startsWith("/")) {
            name.append("/");
        }
        name.append(contextName);
        return name.toString();
    }
}
