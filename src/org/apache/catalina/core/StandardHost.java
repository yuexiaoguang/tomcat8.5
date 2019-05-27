package org.apache.catalina.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * <b>Host</b>接口的标准实现类. 
 * 每个子容器必须是Context实现类， 处理指向特定Web应用程序的请求.
 */
public class StandardHost extends ContainerBase implements Host {

    private static final Log log = LogFactory.getLog(StandardHost.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardHost component with the default basic Valve.
     */
    public StandardHost() {
        super();
        pipeline.setBasic(new StandardHostValve());
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此主机的别名集合.
     */
    private String[] aliases = new String[0];

    private final Object aliasesLock = new Object();


    /**
     * 此主机的应用程序根目录.
     */
    private String appBase = "webapps";
    private volatile File appBaseFile = null;

    /**
     * 这个主机的XML 根.
     */
    private String xmlBase = null;

    /**
     * 主机的默认配置路径
     */
    private volatile File hostConfigBase = null;

    /**
     * 是否自动部署.
     */
    private boolean autoDeploy = true;


    /**
     * 默认的上下文配置类的类名, 用于部署web应用程序.
     */
    private String configClass =
        "org.apache.catalina.startup.ContextConfig";


    /**
     * 默认的Context实现类类名, 用于部署web应用程序.
     */
    private String contextClass =
        "org.apache.catalina.core.StandardContext";


    /**
     * 此主机在启动部署标志.
     */
    private boolean deployOnStartup = true;


    /**
     * 部署 Context XML 配置文件标志.
     */
    private boolean deployXML = !Globals.IS_SECURITY_ENABLED;


    /**
     * 是否默认将 XML文件复制到
     * $CATALINA_BASE/conf/&lt;engine&gt;/&lt;host&gt;
     * 当Web应用被部署的时候?
     */
    private boolean copyXML = false;


    /**
     * 部署Web应用程序默认错误报告实现类的类名.
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";


    /**
     * 是否解压 WAR.
     */
    private boolean unpackWARs = true;


    /**
     * 应用程序工作目录.
     */
    private String workDir = null;


    /**
     * 是否应该在启动时为appBase 和 xmlBase创建目录
     */
    private boolean createDirs = true;


    /**
     * 跟踪子Web应用的类加载器，从而可以检测内存泄漏.
     */
    private final Map<ClassLoader, String> childClassLoaders = new WeakHashMap<>();


    /**
     * 这个模式匹配的{@link #appBase}中的任何文件或目录是否在自动部署过程中将被忽略
     * (包括 {@link #deployOnStartup} 和 {@link #autoDeploy}).
     */
    private Pattern deployIgnore = null;


    private boolean undeployOldVersions = false;

    private boolean failCtxIfServletStartFails = false;


    // ------------------------------------------------------------- Properties

    @Override
    public boolean getUndeployOldVersions() {
        return undeployOldVersions;
    }


    @Override
    public void setUndeployOldVersions(boolean undeployOldVersions) {
        this.undeployOldVersions = undeployOldVersions;
    }


    @Override
    public ExecutorService getStartStopExecutor() {
        return startStopExecutor;
    }


    /**
     * 返回此主机的应用程序根目录. 
     * 这可以是绝对路径，相对路径, 或一个URL.
     */
    @Override
    public String getAppBase() {
        return (this.appBase);
    }


    /**
     * ({@inheritDoc}
     */
    @Override
    public File getAppBaseFile() {

        if (appBaseFile != null) {
            return appBaseFile;
        }

        File file = new File(getAppBase());

        // 如果不是绝对的, 让它变成绝对的
        if (!file.isAbsolute()) {
            file = new File(getCatalinaBase(), file.getPath());
        }

        // 使其规范化
        try {
            file = file.getCanonicalFile();
        } catch (IOException ioe) {
            // Ignore
        }

        this.appBaseFile = file;
        return file;
    }


    /**
     * 设置此主机的应用程序根目录.
     * 这可以是绝对路径，相对路径, 或一个URL.
     *
     * @param appBase The new application root
     */
    @Override
    public void setAppBase(String appBase) {

        if (appBase.trim().equals("")) {
            log.warn(sm.getString("standardHost.problematicAppBase", getName()));
        }
        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);
        this.appBaseFile = null;
    }


    /**
     * 返回这个Host的XML根.
     * 可以是绝对路径，相对路径, 或一个URL.
     * 如果是null, 默认是
     * ${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; 目录
     */
    @Override
    public String getXmlBase() {
        return (this.xmlBase);
    }


    /**
     * 设置这个Host的XML根.
     * 可以是绝对路径，相对路径, 或一个URL.
     * 如果是null, 默认是
     * ${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; 目录
     *
     * @param xmlBase The new XML root
     */
    @Override
    public void setXmlBase(String xmlBase) {

        String oldXmlBase = this.xmlBase;
        this.xmlBase = xmlBase;
        support.firePropertyChange("xmlBase", oldXmlBase, this.xmlBase);

    }


    /**
     * ({@inheritDoc}
     */
    @Override
    public File getConfigBaseFile() {
        if (hostConfigBase != null) {
            return hostConfigBase;
        }
        String path = null;
        if (getXmlBase()!=null) {
            path = getXmlBase();
        } else {
            StringBuilder xmlDir = new StringBuilder("conf");
            Container parent = getParent();
            if (parent instanceof Engine) {
                xmlDir.append('/');
                xmlDir.append(parent.getName());
            }
            xmlDir.append('/');
            xmlDir.append(getName());
            path = xmlDir.toString();
        }
        File file = new File(path);
        if (!file.isAbsolute())
            file = new File(getCatalinaBase(), path);
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {// ignore
        }
        this.hostConfigBase = file;
        return file;
    }


    /**
     * @return <code>true</code>如果 Host将尝试创建appBase 和 xmlBase目录，除非它们已经存在.
     */
    @Override
    public boolean getCreateDirs() {
        return createDirs;
    }

    /**
     * 设置为<code>true</code>如果 Host启动时将尝试创建appBase 和 xmlBase目录
     * 
     * @param createDirs the new flag value
     */
    @Override
    public void setCreateDirs(boolean createDirs) {
        this.createDirs = createDirs;
    }

    /**
     * @return 是否自动部署. 如果是true, 这个主机的子Web应用将被自动部署.
     */
    @Override
    public boolean getAutoDeploy() {
        return (this.autoDeploy);
    }


    /**
     * 是否自动部署.
     *
     * @param autoDeploy 是否自动部署
     */
    @Override
    public void setAutoDeploy(boolean autoDeploy) {
        boolean oldAutoDeploy = this.autoDeploy;
        this.autoDeploy = autoDeploy;
        support.firePropertyChange("autoDeploy", oldAutoDeploy,
                                   this.autoDeploy);
    }


    /**
     * @return Web应用程序的上下文配置类的java类名.
     */
    @Override
    public String getConfigClass() {
        return (this.configClass);
    }


    /**
     * 设置Web应用程序的上下文配置类的java类名.
     *
     * @param configClass 上下文配置类
     */
    @Override
    public void setConfigClass(String configClass) {
        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass",
                                   oldConfigClass, this.configClass);
    }


    /**
     * @return Web应用程序的Context实现类的java类名.
     */
    public String getContextClass() {
        return (this.contextClass);
    }


    /**
     * 设置Web应用程序的Context实现类的java类名.
     *
     * @param contextClass 上下文实现类类名
     */
    public void setContextClass(String contextClass) {
        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass",
                                   oldContextClass, this.contextClass);
    }


    /**
     * @return 此主机启动部署标志. 如果是true, 这个主机的子应用应该自动发现和部署在启动的时候.
     */
    @Override
    public boolean getDeployOnStartup() {
        return (this.deployOnStartup);
    }


    /**
     * 设置此主机启动部署标志.
     *
     * @param deployOnStartup The new deploy on startup flag
     */
    @Override
    public void setDeployOnStartup(boolean deployOnStartup) {

        boolean oldDeployOnStartup = this.deployOnStartup;
        this.deployOnStartup = deployOnStartup;
        support.firePropertyChange("deployOnStartup", oldDeployOnStartup,
                                   this.deployOnStartup);

    }


    /**
     * @return <code>true</code>如果XML上下文部署符应该被部署.
     */
    public boolean isDeployXML() {
        return deployXML;
    }


    /**
     * 是否部署XML上下文配置文件.
     *
     * @param deployXML <code>true</code>如果上下文描述符应该被部署
     */
    public void setDeployXML(boolean deployXML) {
        this.deployXML = deployXML;
    }


    /**
     * @return 是否复制XML配置文件.
     */
    public boolean isCopyXML() {
        return this.copyXML;
    }


    /**
     * 是否复制XML配置文件.
     *
     * @param copyXML The new copy XML flag
     */
    public void setCopyXML(boolean copyXML) {
        this.copyXML = copyXML;
    }


    /**
     * @return 部署Web应用程序默认错误报告valve实现类的类名.
     */
    public String getErrorReportValveClass() {
        return (this.errorReportValveClass);
    }


    /**
     * 设置部署Web应用程序默认错误报告valve实现类的类名.
     *
     * @param errorReportValveClass 错误报告阀门类
     */
    public void setErrorReportValveClass(String errorReportValveClass) {
        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        support.firePropertyChange("errorReportValveClass",
                                   oldErrorReportValveClassClass,
                                   this.errorReportValveClass);
    }


    /**
     * @return 此容器表示的虚拟主机的规范的、完全限定的名称.
     */
    @Override
    public String getName() {
        return (name);
    }


    /**
     * 设置此容器表示的虚拟主机的规范的、完全限定的名称.
     *
     * @param name 虚拟主机名
     *
     * @exception IllegalArgumentException 如果名称是null
     */
    @Override
    public void setName(String name) {

        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardHost.nullName"));

        name = name.toLowerCase(Locale.ENGLISH);      // 内部所有名称都是小写字母

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }


    /**
     * @return <code>true</code>如果WAR应该在部署的时候解压.
     */
    public boolean isUnpackWARs() {
        return (unpackWARs);
    }


    /**
     * WAR是否应该在部署的时候解压.
     *
     * @param unpackWARs <code>true</code>在部署的时候解压WAR
     */
    public void setUnpackWARs(boolean unpackWARs) {
        this.unpackWARs = unpackWARs;
    }


    /**
     * @return 主机工作目录.
     */
    public String getWorkDir() {
        return (workDir);
    }


    /**
     * 设置主机工作目录.
     *
     * @param workDir 此主机的新基本工作文件夹
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }


    /**
     * @return 匹配主机的{@link #getAppBase}自动部署过程中将被忽略的文件和目录的正则表达式.
     */
    @Override
    public String getDeployIgnore() {
        if (deployIgnore == null) {
            return null;
        }
        return this.deployIgnore.toString();
    }


    /**
     * @return 匹配主机的{@link #getAppBase}自动部署过程中将被忽略的文件和目录的编译后的正则表达式.
     */
    @Override
    public Pattern getDeployIgnorePattern() {
        return this.deployIgnore;
    }


    /**
     * 设置匹配主机的{@link #getAppBase}自动部署过程中将被忽略的文件和目录的正则表达式.
     *
     * @param deployIgnore the regexp
     */
    @Override
    public void setDeployIgnore(String deployIgnore) {
        String oldDeployIgnore;
        if (this.deployIgnore == null) {
            oldDeployIgnore = null;
        } else {
            oldDeployIgnore = this.deployIgnore.toString();
        }
        if (deployIgnore == null) {
            this.deployIgnore = null;
        } else {
            this.deployIgnore = Pattern.compile(deployIgnore);
        }
        support.firePropertyChange("deployIgnore",
                                   oldDeployIgnore,
                                   deployIgnore);
    }


    /**
     * @return <code>true</code>如果servlet启动失败，WebApp启动会失败
     */
    public boolean isFailCtxIfServletStartFails() {
        return failCtxIfServletStartFails;
    }


    /**
     * 更改Web应用程序启动时Servlet启动错误的行为.
     * 
     * @param failCtxIfServletStartFails <code>false</code>忽略Web应用程序启动时所声明的Servlet上的错误
     */
    public void setFailCtxIfServletStartFails(boolean failCtxIfServletStartFails) {
        boolean oldFailCtxIfServletStartFails = this.failCtxIfServletStartFails;
        this.failCtxIfServletStartFails = failCtxIfServletStartFails;
        support.firePropertyChange("failCtxIfServletStartFails",
                oldFailCtxIfServletStartFails,
                failCtxIfServletStartFails);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加应该映射到同一主机的别名.
     *
     * @param alias 别名
     */
    @Override
    public void addAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {
            // 跳过重复的别名
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias))
                    return;
            }
            // 将此别名添加到列表中
            String newAliases[] = new String[aliases.length + 1];
            for (int i = 0; i < aliases.length; i++)
                newAliases[i] = aliases[i];
            newAliases[aliases.length] = alias;
            aliases = newAliases;
        }
        // 通知相关的监听器
        fireContainerEvent(ADD_ALIAS_EVENT, alias);

    }


    /**
     * 添加子级Container, 只有所提出的子级是Context的实现类.
     *
     * @param child 要添加的子级容器
     */
    @Override
    public void addChild(Container child) {

        child.addLifecycleListener(new MemoryLeakTrackingListener());

        if (!(child instanceof Context))
            throw new IllegalArgumentException(sm.getString("standardHost.notContext"));
        
        super.addChild(child);

    }


    /**
     * 用于确保{@link Context}实现的无关性, 每次上下文启动时，使用类加载器的记录.
     */
    private class MemoryLeakTrackingListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
                if (event.getSource() instanceof Context) {
                    Context context = ((Context) event.getSource());
                    childClassLoaders.put(context.getLoader().getClassLoader(),
                            context.getServletContext().getContextPath());
                }
            }
        }
    }


    /**
     * 尝试识别具有类加载器内存泄漏的上下文. 这通常是在上下文重载下触发的.
     * Note: 此方法试图强制完全垃圾回收. 这应该在生产系统中极其谨慎地使用.
     *
     * @return 可能泄漏上下文的列表
     */
    public String[] findReloadedContextMemoryLeaks() {

        System.gc();

        List<String> result = new ArrayList<>();

        for (Map.Entry<ClassLoader, String> entry :
                childClassLoaders.entrySet()) {
            ClassLoader cl = entry.getKey();
            if (cl instanceof WebappClassLoaderBase) {
                if (!((WebappClassLoaderBase) cl).getState().isAvailable()) {
                    result.add(entry.getValue());
                }
            }
        }

        return result.toArray(new String[result.size()]);
    }

    /**
     * @return 此主机的别名集合. 如果没有，返回零长度数组.
     */
    @Override
    public String[] findAliases() {
        synchronized (aliasesLock) {
            return (this.aliases);
        }
    }


    /**
     * 从该主机的别名中删除指定的别名.
     *
     * @param alias 别名
     */
    @Override
    public void removeAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {

            // 请确保此别名当前存在
            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // 删除指定的别名
            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n)
                    results[j++] = aliases[i];
            }
            aliases = results;

        }

        // 通知相关的监听器
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);

    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Set error report valve
        String errorValve = getErrorReportValveClass();
        if ((errorValve != null) && (!errorValve.equals(""))) {
            try {
                boolean found = false;
                Valve[] valves = getPipeline().getValves();
                for (Valve valve : valves) {
                    if (errorValve.equals(valve.getClass().getName())) {
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    Valve valve =
                        (Valve) Class.forName(errorValve).getConstructor().newInstance();
                    getPipeline().addValve(valve);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString(
                        "standardHost.invalidErrorReportValveClass",
                        errorValve), t);
            }
        }
        super.startInternal();
    }


    // -------------------- JMX  --------------------
    /**
      * @return 这个Host关联的Valve的MBean名称
      *
      * @exception Exception 如果不能创建或注册的MBean
      */
     public String[] getValveNames() throws Exception {
         Valve [] valves = this.getPipeline().getValves();
         String [] mbeanNames = new String[valves.length];
         for (int i = 0; i < valves.length; i++) {
             if (valves[i] instanceof JmxEnabled) {
                 ObjectName oname = ((JmxEnabled) valves[i]).getObjectName();
                 if (oname != null) {
                     mbeanNames[i] = oname.toString();
                 }
             }
         }

         return mbeanNames;
     }

    public String[] getAliases() {
        synchronized (aliasesLock) {
            return aliases;
        }
    }

    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("type=Host");
        keyProperties.append(getMBeanKeyProperties());

        return keyProperties.toString();
    }
}
