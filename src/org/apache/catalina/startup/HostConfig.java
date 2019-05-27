package org.apache.catalina.startup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.DistributedManager;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Manager;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.security.DeployXmlPermission;
import org.apache.catalina.util.ContextName;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * 开启<b>Host</b>的事件监听器，配置Host的属性, 及其相关的上下文.
 */
public class HostConfig implements LifecycleListener {

    private static final Log log = LogFactory.getLog(HostConfig.class);

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm = StringManager.getManager(HostConfig.class);

    /**
     * 文件修改时间, 以毫秒为单位.
     */
    protected static final long FILE_MODIFICATION_RESOLUTION_MS = 1000;


    // ----------------------------------------------------- Instance Variables

    /**
     * 使用的Context配置类的类名.
     */
    protected String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * 关联的Host
     */
    protected Host host = null;


    /**
     * The JMX ObjectName of this component.
     */
    protected ObjectName oname = null;


    /**
     * 应该部署xml上下文配置文件吗?
     */
    protected boolean deployXML = false;


    /**
     * XML文件是否应该被复制到
     * $CATALINA_BASE/conf/&lt;engine&gt;/&lt;host&gt;
     * 当Web应用部署时?
     */
    protected boolean copyXML = false;


    /**
     * 是否解压 WAR 文件，当在<code>appBase</code>目录自动部署应用的时候?
     */
    protected boolean unpackWARs = false;


    /**
     * 部署的应用
     */
    protected final Map<String, DeployedApplication> deployed =
            new ConcurrentHashMap<>();


    /**
     * 正在使用的应用程序列表, 目前不应该是deployed/undeployed/redeployed.
     */
    protected final ArrayList<String> serviced = new ArrayList<>();


    /**
     * 用于解析上下文描述符的 <code>Digester</code>实例.
     */
    protected Digester digester = createDigester(contextClass);
    private final Object digesterLock = new Object();

    /**
     * appBase中要被忽略的War的列表, 因为是无效的
     * (e.g. contain /../ sequences).
     */
    protected final Set<String> invalidWars = new HashSet<>();

    // ------------------------------------------------------------- Properties


    /**
     * @return Context实现类类名.
     */
    public String getContextClass() {
        return (this.contextClass);
    }


    /**
     * 设置Context实现类类名.
     *
     * @param contextClass Context实现类类名.
     */
    public void setContextClass(String contextClass) {

        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;

        if (!oldContextClass.equals(contextClass)) {
            synchronized (digesterLock) {
                digester = createDigester(getContextClass());
            }
        }
    }


    /**
     * @return 部署XML配置文件.
     */
    public boolean isDeployXML() {
        return (this.deployXML);
    }


    /**
     * 设置部署XML配置文件标志.
     *
     * @param deployXML The new deploy XML flag
     */
    public void setDeployXML(boolean deployXML) {
        this.deployXML = deployXML;
    }


    private boolean isDeployThisXML(File docBase, ContextName cn) {
        boolean deployThisXML = isDeployXML();
        if (Globals.IS_SECURITY_ENABLED && !deployThisXML) {
            // When running under a SecurityManager, deployXML may be overridden
            // on a per Context basis by the granting of a specific permission
            Policy currentPolicy = Policy.getPolicy();
            if (currentPolicy != null) {
                URL contextRootUrl;
                try {
                    contextRootUrl = docBase.toURI().toURL();
                    CodeSource cs = new CodeSource(contextRootUrl, (Certificate[]) null);
                    PermissionCollection pc = currentPolicy.getPermissions(cs);
                    Permission p = new DeployXmlPermission(cn.getBaseName());
                    if (pc.implies(p)) {
                        deployThisXML = true;
                    }
                } catch (MalformedURLException e) {
                    // Should never happen
                    log.warn("hostConfig.docBaseUrlInvalid", e);
                }
            }
        }

        return deployThisXML;
    }


    /**
     * @return 是否复制XML配置文件.
     */
    public boolean isCopyXML() {
        return (this.copyXML);
    }


    /**
     * 设置是否复制XML配置文件.
     *
     * @param copyXML The new copy XML flag
     */
    public void setCopyXML(boolean copyXML) {
        this.copyXML= copyXML;
    }


    /**
     * @return 是否解压 WAR 文件.
     */
    public boolean isUnpackWARs() {
        return (this.unpackWARs);
    }


    /**
     * 是否解压 WAR 文件.
     *
     * @param unpackWARs The new unpack WARs flag
     */
    public void setUnpackWARs(boolean unpackWARs) {
        this.unpackWARs = unpackWARs;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 处理关联的Host的START事件.
     *
     * @param event The lifecycle event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        // Identify the host we are associated with
        try {
            host = (Host) event.getLifecycle();
            if (host instanceof StandardHost) {
                setCopyXML(((StandardHost) host).isCopyXML());
                setDeployXML(((StandardHost) host).isDeployXML());
                setUnpackWARs(((StandardHost) host).isUnpackWARs());
                setContextClass(((StandardHost) host).getContextClass());
            }
        } catch (ClassCastException e) {
            log.error(sm.getString("hostConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.PERIODIC_EVENT)) {
            check();
        } else if (event.getType().equals(Lifecycle.BEFORE_START_EVENT)) {
            beforeStart();
        } else if (event.getType().equals(Lifecycle.START_EVENT)) {
            start();
        } else if (event.getType().equals(Lifecycle.STOP_EVENT)) {
            stop();
        }
    }


    /**
     * 向列表中添加应用程序服务.
     * 
     * @param name 上下文名称
     */
    public synchronized void addServiced(String name) {
        serviced.add(name);
    }


    /**
     * 是否包含应用程序?
     * 
     * @param name the context name
     * @return state of the application
     */
    public synchronized boolean isServiced(String name) {
        return (serviced.contains(name));
    }


    /**
     * 从列表中删除应用程序服务.
     * 
     * @param name the context name
     */
    public synchronized void removeServiced(String name) {
        serviced.remove(name);
    }


    /**
     * 获取应用程序部署的时间戳.
     * 
     * @param name the context name
     * @return 0L 如果没有部署该名称的应用程序, 或应用程序部署的瞬间
     */
    public long getDeploymentTime(String name) {
        DeployedApplication app = deployed.get(name);
        if (app == null) {
            return 0L;
        }

        return app.timestamp;
    }


    /**
     * 指定的应用程序是否已部署? 注意，server.xml中定义的应用不会被部署.
     * 
     * @param name the context name
     * @return <code>true</code>如果已部署应用程序;
     *  <code>false</code>如果应用程序尚未部署或不存在
     */
    public boolean isDeployed(String name) {
        DeployedApplication app = deployed.get(name);
        if (app == null) {
            return false;
        }

        return true;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 创建将用于解析上下文配置文件的digester.
     * 
     * @param contextClassName 用于创建上下文实例的类
     * @return the digester
     */
    protected static Digester createDigester(String contextClassName) {
        Digester digester = new Digester();
        digester.setValidating(false);
        // 添加对象创建规则
        digester.addObjectCreate("Context", contextClassName, "className");
        // 在该对象上设置属性 (设置额外属性并不重要)
        digester.addSetProperties("Context");
        return (digester);
    }

    protected File returnCanonicalPath(String path) {
        File file = new File(path);
        if (!file.isAbsolute())
            file = new File(host.getCatalinaBase(), path);
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }


    /**
     * 获取configBase的名称.
     * 用于使用 JMX 管理.
     * @return the config base
     */
    public String getConfigBaseName() {
        return host.getConfigBaseFile().getAbsolutePath();
    }


    /**
     * 部署所有在“应用根目录”找到的目录或 WAR 文件.
     */
    protected void deployApps() {

        File appBase = host.getAppBaseFile();
        File configBase = host.getConfigBaseFile();
        String[] filteredAppPaths = filterAppPaths(appBase.list());
        // Deploy XML descriptors from configBase
        deployDescriptors(configBase, configBase.list());
        // Deploy WARs
        deployWARs(appBase, filteredAppPaths);
        // Deploy expanded folders
        deployDirectories(appBase, filteredAppPaths);

    }


    /**
     * 筛选应用程序文件路径列表，以移除与{@link Host#getDeployIgnore()}定义的正则表达式匹配的列表.
     *
     * @param unfilteredAppPaths    筛选应用程序路径列表
     *
     * @return 应用程序路径的筛选列表
     */
    protected String[] filterAppPaths(String[] unfilteredAppPaths) {
        Pattern filter = host.getDeployIgnorePattern();
        if (filter == null || unfilteredAppPaths == null) {
            return unfilteredAppPaths;
        }

        List<String> filteredList = new ArrayList<>();
        Matcher matcher = null;
        for (String appPath : unfilteredAppPaths) {
            if (matcher == null) {
                matcher = filter.matcher(appPath);
            } else {
                matcher.reset(appPath);
            }
            if (matcher.matches()) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("hostConfig.ignorePath", appPath));
                }
            } else {
                filteredList.add(appPath);
            }
        }
        return filteredList.toArray(new String[filteredList.size()]);
    }


    /**
     * 部署所有在“应用根目录”找到的目录或 WAR 文件.
     * 
     * @param name 应该部署的上下文名称
     */
    protected void deployApps(String name) {

        File appBase = host.getAppBaseFile();
        File configBase = host.getConfigBaseFile();
        ContextName cn = new ContextName(name, false);
        String baseName = cn.getBaseName();

        if (deploymentExists(cn.getName())) {
            return;
        }

        // Deploy XML descriptor from configBase
        File xml = new File(configBase, baseName + ".xml");
        if (xml.exists()) {
            deployDescriptor(cn, xml);
            return;
        }
        // Deploy WAR
        File war = new File(appBase, baseName + ".war");
        if (war.exists()) {
            deployWAR(cn, war);
            return;
        }
        // Deploy expanded folder
        File dir = new File(appBase, baseName);
        if (dir.exists())
            deployDirectory(cn, dir);
    }


    /**
     * 部署xml上下文描述符.
     * 
     * @param configBase The config base
     * @param files 应该部署的XML描述符
     */
    protected void deployDescriptors(File configBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            File contextXml = new File(configBase, files[i]);

            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".xml")) {
                ContextName cn = new ContextName(files[i], true);

                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                results.add(
                        es.submit(new DeployDescriptor(this, cn, contextXml)));
            }
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDescriptor.threaded.error"), e);
            }
        }
    }


    /**
     * 部署指定的上下文描述符.
     * 
     * @param cn The context name
     * @param contextXml The descriptor
     */
    @SuppressWarnings("null") // context is not null
    protected void deployDescriptor(ContextName cn, File contextXml) {

        DeployedApplication deployedApp =
                new DeployedApplication(cn.getName(), true);

        long startTime = 0;
        // 假设这是一个配置描述符并部署它
        if(log.isInfoEnabled()) {
           startTime = System.currentTimeMillis();
           log.info(sm.getString("hostConfig.deployDescriptor",
                    contextXml.getAbsolutePath()));
        }

        Context context = null;
        boolean isExternalWar = false;
        boolean isExternal = false;
        File expandedDocBase = null;

        try (FileInputStream fis = new FileInputStream(contextXml)) {
            synchronized (digesterLock) {
                try {
                    context = (Context) digester.parse(fis);
                } catch (Exception e) {
                    log.error(sm.getString(
                            "hostConfig.deployDescriptor.error",
                            contextXml.getAbsolutePath()), e);
                } finally {
                    digester.reset();
                    if (context == null) {
                        context = new FailedContext();
                    }
                }
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);

            context.setConfigFile(contextXml.toURI().toURL());
            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            // 添加关联的docBase到重新部署列表, 如果它是 WAR
            if (context.getDocBase() != null) {
                File docBase = new File(context.getDocBase());
                if (!docBase.isAbsolute()) {
                    docBase = new File(host.getAppBaseFile(), context.getDocBase());
                }
                // If external docBase, register .xml as redeploy first
                if (!docBase.getCanonicalPath().startsWith(
                        host.getAppBaseFile().getAbsolutePath() + File.separator)) {
                    isExternal = true;
                    deployedApp.redeployResources.put(
                            contextXml.getAbsolutePath(),
                            Long.valueOf(contextXml.lastModified()));
                    deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                            Long.valueOf(docBase.lastModified()));
                    if (docBase.getAbsolutePath().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                        isExternalWar = true;
                    }
                } else {
                    log.warn(sm.getString("hostConfig.deployDescriptor.localDocBaseSpecified",
                             docBase));
                    // Ignore specified docBase
                    context.setDocBase(null);
                }
            }

            host.addChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployDescriptor.error",
                                   contextXml.getAbsolutePath()), t);
        } finally {
            // Get paths for WAR and expanded WAR in appBase

            // default to appBase dir + name
            expandedDocBase = new File(host.getAppBaseFile(), cn.getBaseName());
            if (context.getDocBase() != null
                    && !context.getDocBase().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                // first assume docBase is absolute
                expandedDocBase = new File(context.getDocBase());
                if (!expandedDocBase.isAbsolute()) {
                    // if docBase specified and relative, it must be relative to appBase
                    expandedDocBase = new File(host.getAppBaseFile(), context.getDocBase());
                }
            }

            boolean unpackWAR = unpackWARs;
            if (unpackWAR && context instanceof StandardContext) {
                unpackWAR = ((StandardContext) context).getUnpackWAR();
            }

            // 添加最终的解压的WAR 和所有的需要监视的资源
            if (isExternalWar) {
                if (unpackWAR) {
                    deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                            Long.valueOf(expandedDocBase.lastModified()));
                    addWatchedResources(deployedApp, expandedDocBase.getAbsolutePath(), context);
                } else {
                    addWatchedResources(deployedApp, null, context);
                }
            } else {
                // 查找现有匹配的 WAR和扩展文件夹
                if (!isExternal) {
                    File warDocBase = new File(expandedDocBase.getAbsolutePath() + ".war");
                    if (warDocBase.exists()) {
                        deployedApp.redeployResources.put(warDocBase.getAbsolutePath(),
                                Long.valueOf(warDocBase.lastModified()));
                    } else {
                        // Trigger a redeploy if a WAR is added
                        deployedApp.redeployResources.put(
                                warDocBase.getAbsolutePath(),
                                Long.valueOf(0));
                    }
                }
                if (unpackWAR) {
                    deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                            Long.valueOf(expandedDocBase.lastModified()));
                    addWatchedResources(deployedApp,
                            expandedDocBase.getAbsolutePath(), context);
                } else {
                    addWatchedResources(deployedApp, null, context);
                }
                if (!isExternal) {
                    // 对于额外的 docBases, context.xml 将以上添加.
                    deployedApp.redeployResources.put(
                            contextXml.getAbsolutePath(),
                            Long.valueOf(contextXml.lastModified()));
                }
            }
            // 添加全局重新部署资源(永远不会删除) , 所以它们不会干扰删除过程
            addGlobalRedeployResources(deployedApp);
        }

        if (host.findChild(context.getName()) != null) {
            deployed.put(context.getName(), deployedApp);
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("hostConfig.deployDescriptor.finished",
                contextXml.getAbsolutePath(), Long.valueOf(System.currentTimeMillis() - startTime)));
        }
    }


    /**
     * 部署 WAR 文件.
     * 
     * @param appBase 应用的基础目录
     * @param files 要部署的WAR
     */
    protected void deployWARs(File appBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File war = new File(appBase, files[i]);
            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".war") &&
                    war.isFile() && !invalidWars.contains(files[i]) ) {

                ContextName cn = new ContextName(files[i], true);

                if (isServiced(cn.getName())) {
                    continue;
                }
                if (deploymentExists(cn.getName())) {
                    DeployedApplication app = deployed.get(cn.getName());
                    boolean unpackWAR = unpackWARs;
                    if (unpackWAR && host.findChild(cn.getName()) instanceof StandardContext) {
                        unpackWAR = ((StandardContext) host.findChild(cn.getName())).getUnpackWAR();
                    }
                    if (!unpackWAR && app != null) {
                        // 需要检查不应该存在的目录
                        File dir = new File(appBase, cn.getBaseName());
                        if (dir.exists()) {
                            if (!app.loggedDirWarning) {
                                log.warn(sm.getString(
                                        "hostConfig.deployWar.hiddenDir",
                                        dir.getAbsoluteFile(),
                                        war.getAbsoluteFile()));
                                app.loggedDirWarning = true;
                            }
                        } else {
                            app.loggedDirWarning = false;
                        }
                    }
                    continue;
                }

                // Check for WARs with /../ /./ or similar sequences in the name
                if (!validateContextPath(appBase, cn.getBaseName())) {
                    log.error(sm.getString(
                            "hostConfig.illegalWarName", files[i]));
                    invalidWars.add(files[i]);
                    continue;
                }

                results.add(es.submit(new DeployWar(this, cn, war)));
            }
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployWar.threaded.error"), e);
            }
        }
    }


    private boolean validateContextPath(File appBase, String contextPath) {
        // More complicated than the ideal as the canonical path may or may
        // not end with File.separator for a directory

        StringBuilder docBase;
        String canonicalDocBase = null;

        try {
            String canonicalAppBase = appBase.getCanonicalPath();
            docBase = new StringBuilder(canonicalAppBase);
            if (canonicalAppBase.endsWith(File.separator)) {
                docBase.append(contextPath.substring(1).replace(
                        '/', File.separatorChar));
            } else {
                docBase.append(contextPath.replace('/', File.separatorChar));
            }
            // At this point docBase should be canonical but will not end
            // with File.separator

            canonicalDocBase =
                (new File(docBase.toString())).getCanonicalPath();

            // If the canonicalDocBase ends with File.separator, add one to
            // docBase before they are compared
            if (canonicalDocBase.endsWith(File.separator)) {
                docBase.append(File.separator);
            }
        } catch (IOException ioe) {
            return false;
        }

        // Compare the two. If they are not the same, the contextPath must
        // have /../ like sequences in it
        return canonicalDocBase.equals(docBase.toString());
    }

    /**
     * 部署打包的WAR.
     * 
     * @param cn The context name
     * @param war The WAR file
     */
    protected void deployWAR(ContextName cn, File war) {

        File xml = new File(host.getAppBaseFile(),
                cn.getBaseName() + "/" + Constants.ApplicationContextXml);

        File warTracker = new File(host.getAppBaseFile(),
                cn.getBaseName() + "/" + Constants.WarTracker);

        boolean xmlInWar = false;
        try (JarFile jar = new JarFile(war)) {
            JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
            if (entry != null) {
                xmlInWar = true;
            }
        } catch (IOException e) {
            /* Ignore */
        }

        // 如果有扩展目录，则目录中的任何XML仅在目录不过期和unpackWARs是true时才使用. 注意下面的代码可以应用更多的限制
        boolean useXml = false;
        // 如果存在XML文件, 那么expandedDir必须存在, 所以不需要测试
        if (xml.exists() && unpackWARs &&
                (!warTracker.exists() || warTracker.lastModified() == war.lastModified())) {
            useXml = true;
        }

        Context context = null;
        boolean deployThisXML = isDeployThisXML(war, cn);

        try {
            if (deployThisXML && useXml && !copyXML) {
                synchronized (digesterLock) {
                    try {
                        context = (Context) digester.parse(xml);
                    } catch (Exception e) {
                        log.error(sm.getString(
                                "hostConfig.deployDescriptor.error",
                                war.getAbsolutePath()), e);
                    } finally {
                        digester.reset();
                        if (context == null) {
                            context = new FailedContext();
                        }
                    }
                }
                context.setConfigFile(xml.toURI().toURL());
            } else if (deployThisXML && xmlInWar) {
                synchronized (digesterLock) {
                    try (JarFile jar = new JarFile(war)) {
                        JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
                        try (InputStream istream = jar.getInputStream(entry)) {
                            context = (Context) digester.parse(istream);
                        }
                    } catch (Exception e) {
                        log.error(sm.getString(
                                "hostConfig.deployDescriptor.error",
                                war.getAbsolutePath()), e);
                    } finally {
                        digester.reset();
                        if (context == null) {
                            context = new FailedContext();
                        }
                        context.setConfigFile(
                                UriUtil.buildJarUrl(war, Constants.ApplicationContextXml));
                    }
                }
            } else if (!deployThisXML && xmlInWar) {
                // 块部署, 因为 META-INF/context.xml 可能包含安全部署所必需的安全配置.
                log.error(sm.getString("hostConfig.deployDescriptor.blocked",
                        cn.getPath(), Constants.ApplicationContextXml,
                        new File(host.getConfigBaseFile(), cn.getBaseName() + ".xml")));
            } else {
                context = (Context) Class.forName(contextClass).getConstructor().newInstance();
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployWar.error",
                    war.getAbsolutePath()), t);
        } finally {
            if (context == null) {
                context = new FailedContext();
            }
        }

        boolean copyThisXml = false;
        if (deployThisXML) {
            if (host instanceof StandardHost) {
                copyThisXml = ((StandardHost) host).isCopyXML();
            }

            // 如果Host使用默认值，Context可以重写它.
            if (!copyThisXml && context instanceof StandardContext) {
                copyThisXml = ((StandardContext) context).getCopyXML();
            }

            if (xmlInWar && copyThisXml) {
                // 将XML文件的位置更改为配置base
                xml = new File(host.getConfigBaseFile(),
                        cn.getBaseName() + ".xml");
                try (JarFile jar = new JarFile(war)) {
                    JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
                    try (InputStream istream = jar.getInputStream(entry);
                            FileOutputStream fos = new FileOutputStream(xml);
                            BufferedOutputStream ostream = new BufferedOutputStream(fos, 1024)) {
                        byte buffer[] = new byte[1024];
                        while (true) {
                            int n = istream.read(buffer);
                            if (n < 0) {
                                break;
                            }
                            ostream.write(buffer, 0, n);
                        }
                        ostream.flush();
                    }
                } catch (IOException e) {
                    /* Ignore */
                }
            }
        }

        DeployedApplication deployedApp = new DeployedApplication(cn.getName(),
                xml.exists() && deployThisXML && copyThisXml);

        long startTime = 0;
        // Deploy the application in this WAR file
        if(log.isInfoEnabled()) {
            startTime = System.currentTimeMillis();
            log.info(sm.getString("hostConfig.deployWar",
                    war.getAbsolutePath()));
        }

        try {
            // 填充重新部署资源, 使用 WAR 文件
            deployedApp.redeployResources.put
                (war.getAbsolutePath(), Long.valueOf(war.lastModified()));

            if (deployThisXML && xml.exists() && copyThisXml) {
                deployedApp.redeployResources.put(xml.getAbsolutePath(),
                        Long.valueOf(xml.lastModified()));
            } else {
                // 如果以后将XML文件添加到配置base中
                deployedApp.redeployResources.put(
                        (new File(host.getConfigBaseFile(),
                                cn.getBaseName() + ".xml")).getAbsolutePath(),
                        Long.valueOf(0));
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);

            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            context.setDocBase(cn.getBaseName() + ".war");
            host.addChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployWar.error",
                    war.getAbsolutePath()), t);
        } finally {
            // 如果是未打包的 WARs, docBase启动上下文后会发生变异
            boolean unpackWAR = unpackWARs;
            if (unpackWAR && context instanceof StandardContext) {
                unpackWAR = ((StandardContext) context).getUnpackWAR();
            }
            if (unpackWAR && context.getDocBase() != null) {
                File docBase = new File(host.getAppBaseFile(), cn.getBaseName());
                deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        Long.valueOf(docBase.lastModified()));
                addWatchedResources(deployedApp, docBase.getAbsolutePath(),
                        context);
                if (deployThisXML && !copyThisXml && (xmlInWar || xml.exists())) {
                    deployedApp.redeployResources.put(xml.getAbsolutePath(),
                            Long.valueOf(xml.lastModified()));
                }
            } else {
                // docBase是 null, 不需要监视资源. 这将被记录在调试级别.
                addWatchedResources(deployedApp, null, context);
            }
            // 添加全局重新部署资源 (永远不会删除), 所以它们不会干扰删除过程
            addGlobalRedeployResources(deployedApp);
        }

        deployed.put(cn.getName(), deployedApp);

        if (log.isInfoEnabled()) {
            log.info(sm.getString("hostConfig.deployWar.finished",
                war.getAbsolutePath(), Long.valueOf(System.currentTimeMillis() - startTime)));
        }
    }


    /**
     * 部署目录.
     * 
     * @param appBase 应用程序的基本路径
     * @param files 应该部署的Web应用程序
     */
    protected void deployDirectories(File appBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File dir = new File(appBase, files[i]);
            if (dir.isDirectory()) {
                ContextName cn = new ContextName(files[i], false);

                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                results.add(es.submit(new DeployDirectory(this, cn, dir)));
            }
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDir.threaded.error"), e);
            }
        }
    }


    /**
     * 部署扩展的Web应用.
     * @param cn The context name
     * @param dir Web应用的根文件夹的路径
     */
    protected void deployDirectory(ContextName cn, File dir) {


        long startTime = 0;
        // Deploy the application in this directory
        if( log.isInfoEnabled() ) {
            startTime = System.currentTimeMillis();
            log.info(sm.getString("hostConfig.deployDir",
                    dir.getAbsolutePath()));
        }

        Context context = null;
        File xml = new File(dir, Constants.ApplicationContextXml);
        File xmlCopy =
                new File(host.getConfigBaseFile(), cn.getBaseName() + ".xml");


        DeployedApplication deployedApp;
        boolean copyThisXml = isCopyXML();
        boolean deployThisXML = isDeployThisXML(dir, cn);

        try {
            if (deployThisXML && xml.exists()) {
                synchronized (digesterLock) {
                    try {
                        context = (Context) digester.parse(xml);
                    } catch (Exception e) {
                        log.error(sm.getString(
                                "hostConfig.deployDescriptor.error",
                                xml), e);
                        context = new FailedContext();
                    } finally {
                        digester.reset();
                        if (context == null) {
                            context = new FailedContext();
                        }
                    }
                }

                if (copyThisXml == false && context instanceof StandardContext) {
                    // Host is using default value. Context may override it.
                    copyThisXml = ((StandardContext) context).getCopyXML();
                }

                if (copyThisXml) {
                    Files.copy(xml.toPath(), xmlCopy.toPath());
                    context.setConfigFile(xmlCopy.toURI().toURL());
                } else {
                    context.setConfigFile(xml.toURI().toURL());
                }
            } else if (!deployThisXML && xml.exists()) {
                // Block deployment as META-INF/context.xml may contain security
                // configuration necessary for a secure deployment.
                log.error(sm.getString("hostConfig.deployDescriptor.blocked",
                        cn.getPath(), xml, xmlCopy));
                context = new FailedContext();
            } else {
                context = (Context) Class.forName(contextClass).getConstructor().newInstance();
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
            context.addLifecycleListener(listener);

            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            context.setDocBase(cn.getBaseName());
            host.addChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployDir.error",
                    dir.getAbsolutePath()), t);
        } finally {
            deployedApp = new DeployedApplication(cn.getName(),
                    xml.exists() && deployThisXML && copyThisXml);

            // Fake re-deploy resource to detect if a WAR is added at a later
            // point
            deployedApp.redeployResources.put(dir.getAbsolutePath() + ".war",
                    Long.valueOf(0));
            deployedApp.redeployResources.put(dir.getAbsolutePath(),
                    Long.valueOf(dir.lastModified()));
            if (deployThisXML && xml.exists()) {
                if (copyThisXml) {
                    deployedApp.redeployResources.put(
                            xmlCopy.getAbsolutePath(),
                            Long.valueOf(xmlCopy.lastModified()));
                } else {
                    deployedApp.redeployResources.put(
                            xml.getAbsolutePath(),
                            Long.valueOf(xml.lastModified()));
                    // Fake re-deploy resource to detect if a context.xml file is
                    // added at a later point
                    deployedApp.redeployResources.put(
                            xmlCopy.getAbsolutePath(),
                            Long.valueOf(0));
                }
            } else {
                // Fake re-deploy resource to detect if a context.xml file is
                // added at a later point
                deployedApp.redeployResources.put(
                        xmlCopy.getAbsolutePath(),
                        Long.valueOf(0));
                if (!xml.exists()) {
                    deployedApp.redeployResources.put(
                            xml.getAbsolutePath(),
                            Long.valueOf(0));
                }
            }
            addWatchedResources(deployedApp, dir.getAbsolutePath(), context);
            // 添加全局重新部署资源 (永远不会删除), 所以它们不会干扰删除过程
            addGlobalRedeployResources(deployedApp);
        }

        deployed.put(cn.getName(), deployedApp);

        if( log.isInfoEnabled() ) {
            log.info(sm.getString("hostConfig.deployDir.finished",
                    dir.getAbsolutePath(), Long.valueOf(System.currentTimeMillis() - startTime)));
        }
    }


    /**
     * 检查一个程序是否已经部署在该主机.
     *
     * @param contextName of the context which will be checked
     * @return <code>true</code>如果指定的部署存在
     */
    protected boolean deploymentExists(String contextName) {
        return (deployed.containsKey(contextName) ||
                (host.findChild(contextName) != null));
    }


    /**
     * 将受监视的资源添加到指定的上下文中.
     * 
     * @param app HostConfig deployed app
     * @param docBase web app docBase
     * @param context web application context
     */
    protected void addWatchedResources(DeployedApplication app, String docBase,
            Context context) {
        // FIXME: Feature idea. Add support for patterns (ex: WEB-INF/*,
        //        WEB-INF/*.xml), where we would only check if at least one
        //        resource is newer than app.timestamp
        File docBaseFile = null;
        if (docBase != null) {
            docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(host.getAppBaseFile(), docBase);
            }
        }
        String[] watchedResources = context.findWatchedResources();
        for (int i = 0; i < watchedResources.length; i++) {
            File resource = new File(watchedResources[i]);
            if (!resource.isAbsolute()) {
                if (docBase != null) {
                    resource = new File(docBaseFile, watchedResources[i]);
                } else {
                    if(log.isDebugEnabled())
                        log.debug("Ignoring non-existent WatchedResource '" +
                                resource.getAbsolutePath() + "'");
                    continue;
                }
            }
            if(log.isDebugEnabled())
                log.debug("Watching WatchedResource '" +
                        resource.getAbsolutePath() + "'");
            app.reloadResources.put(resource.getAbsolutePath(),
                    Long.valueOf(resource.lastModified()));
        }
    }


    protected void addGlobalRedeployResources(DeployedApplication app) {
        // 重新部署资源处理是硬编码的，永远不会删除这个文件
        File hostContextXml =
                new File(getConfigBaseName(), Constants.HostContextXml);
        if (hostContextXml.isFile()) {
            app.redeployResources.put(hostContextXml.getAbsolutePath(),
                    Long.valueOf(hostContextXml.lastModified()));
        }

        // Redeploy resources in CATALINA_BASE/conf are never deleted
        File globalContextXml =
                returnCanonicalPath(Constants.DefaultContextXml);
        if (globalContextXml.isFile()) {
            app.redeployResources.put(globalContextXml.getAbsolutePath(),
                    Long.valueOf(globalContextXml.lastModified()));
        }
    }


    /**
     * 检查资源以进行重新部署和重新加载.
     *
     * @param app   要检查的Web应用
     * @param skipFileModificationResolutionCheck  检查修改文件时, 要求任何文件修改必须发生的检查, 至少在跳过文件时间戳的解决之前
     */
    protected synchronized void checkResources(DeployedApplication app,
            boolean skipFileModificationResolutionCheck) {
        String[] resources =
            app.redeployResources.keySet().toArray(new String[0]);
        // Offset the current time by the resolution of File.lastModified()
        long currentTimeWithResolutionOffset =
                System.currentTimeMillis() - FILE_MODIFICATION_RESOLUTION_MS;
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name +
                        "] redeploy resource " + resource);
            long lastModified =
                    app.redeployResources.get(resources[i]).longValue();
            if (resource.exists() || lastModified == 0) {
                // File.lastModified() has a resolution of 1s (1000ms). The last
                // modified time has to be more than 1000ms ago to ensure that
                // modifications that take place in the same second are not
                // missed. See Bug 57765.
                if (resource.lastModified() != lastModified && (!host.getAutoDeploy() ||
                        resource.lastModified() < currentTimeWithResolutionOffset ||
                        skipFileModificationResolutionCheck)) {
                    if (resource.isDirectory()) {
                        // 修改目录不需要任何操作
                        app.redeployResources.put(resources[i],
                                Long.valueOf(resource.lastModified()));
                    } else if (app.hasDescriptor &&
                            resource.getName().toLowerCase(
                                    Locale.ENGLISH).endsWith(".war")) {
                        // 如果存在XML文件，WAR的修改触发重新加载
                        // 唯一应该删除的资源是扩展的WAR
                        Context context = (Context) host.findChild(app.name);
                        String docBase = context.getDocBase();
                        if (!docBase.toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                            // 这是一个扩展的目录
                            File docBaseFile = new File(docBase);
                            if (!docBaseFile.isAbsolute()) {
                                docBaseFile = new File(host.getAppBaseFile(),
                                        docBase);
                            }
                            reload(app, docBaseFile, resource.getAbsolutePath());
                        } else {
                            reload(app, null, null);
                        }
                        // Update times
                        app.redeployResources.put(resources[i],
                                Long.valueOf(resource.lastModified()));
                        app.timestamp = System.currentTimeMillis();
                        boolean unpackWAR = unpackWARs;
                        if (unpackWAR && context instanceof StandardContext) {
                            unpackWAR = ((StandardContext) context).getUnpackWAR();
                        }
                        if (unpackWAR) {
                            addWatchedResources(app, context.getDocBase(), context);
                        } else {
                            addWatchedResources(app, null, context);
                        }
                        return;
                    } else {
                        // 其他一切都触发了重新部署(只需要在这里卸载, 随后部署)
                        undeploy(app);
                        deleteRedeployResources(app, resources, i, false);
                        return;
                    }
                }
            } else {
                // 有可能只是暂时丢失资源，例如在文本编辑器保存过程中重命名
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    // Ignore
                }
                // 重新检查资源，看看它是否真的被删除了
                if (resource.exists()) {
                    continue;
                }
                // Undeploy application
                undeploy(app);
                deleteRedeployResources(app, resources, i, true);
                return;
            }
        }
        resources = app.reloadResources.keySet().toArray(new String[0]);
        boolean update = false;
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled()) {
                log.debug("Checking context[" + app.name + "] reload resource " + resource);
            }
            long lastModified = app.reloadResources.get(resources[i]).longValue();
            // File.lastModified() has a resolution of 1s (1000ms). The last
            // modified time has to be more than 1000ms ago to ensure that
            // modifications that take place in the same second are not
            // missed. See Bug 57765.
            if ((resource.lastModified() != lastModified &&
                    (!host.getAutoDeploy() ||
                            resource.lastModified() < currentTimeWithResolutionOffset ||
                            skipFileModificationResolutionCheck)) ||
                    update) {
                if (!update) {
                    // Reload application
                    reload(app, null, null);
                    update = true;
                }
                // 更新时间. 不止一个文件可能已被更新. 不想触发一系列的重载.
                app.reloadResources.put(resources[i],
                        Long.valueOf(resource.lastModified()));
            }
            app.timestamp = System.currentTimeMillis();
        }
    }


    /*
     * Note: If either of fileToRemove and newDocBase are null, both will be
     *       ignored.
     */
    private void reload(DeployedApplication app, File fileToRemove, String newDocBase) {
        if(log.isInfoEnabled())
            log.info(sm.getString("hostConfig.reload", app.name));
        Context context = (Context) host.findChild(app.name);
        if (context.getState().isAvailable()) {
            if (fileToRemove != null && newDocBase != null) {
                context.addLifecycleListener(
                        new ExpandedDirectoryRemovalListener(fileToRemove, newDocBase));
            }
            // Reload catches and logs exceptions
            context.reload();
        } else {
            // 如果上下文未启动 (例如 web.xml中的一个错误) 还是要试着开始
            if (fileToRemove != null && newDocBase != null) {
                ExpandWar.delete(fileToRemove);
                context.setDocBase(newDocBase);
            }
            try {
                context.start();
            } catch (Exception e) {
                log.warn(sm.getString
                         ("hostConfig.context.restart", app.name), e);
            }
        }
    }


    private void undeploy(DeployedApplication app) {
        if (log.isInfoEnabled())
            log.info(sm.getString("hostConfig.undeploy", app.name));
        Container context = host.findChild(app.name);
        try {
            host.removeChild(context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString
                     ("hostConfig.context.remove", app.name), t);
        }
        deployed.remove(app.name);
    }


    private void deleteRedeployResources(DeployedApplication app, String[] resources, int i,
            boolean deleteReloadResources) {

        // 删除其他重新部署资源
        for (int j = i + 1; j < resources.length; j++) {
            File current = new File(resources[j]);
            // Never delete per host context.xml defaults
            if (Constants.HostContextXml.equals(current.getName())) {
                continue;
            }
            // Only delete resources in the appBase or the
            // host's configBase
            if (isDeletableResource(app, current)) {
                if (log.isDebugEnabled()) {
                    log.debug("Delete " + current);
                }
                ExpandWar.delete(current);
            }
        }

        // 删除加载资源 (删除剩下的 .xml 描述符)
        if (deleteReloadResources) {
            String[] resources2 = app.reloadResources.keySet().toArray(new String[0]);
            for (int j = 0; j < resources2.length; j++) {
                File current = new File(resources2[j]);
                // Never delete per host context.xml defaults
                if (Constants.HostContextXml.equals(current.getName())) {
                    continue;
                }
                // Only delete resources in the appBase or the host's
                // configBase
                if (isDeletableResource(app, current)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Delete " + current);
                    }
                    ExpandWar.delete(current);
                }
            }
        }
    }


    /*
     * 删除资源将触发自动部署代码，来重新部署应用. 这意味着删除:
     * - appBase中的资源
     * - configBase中的部署描述符
     * - appBase 或 configBase中的symlinks
     */
    private boolean isDeletableResource(DeployedApplication app, File resource) {
        // The resource may be a file, a directory or a symlink to a file or
        // directory.

        // 检查资源是否是绝对的. 这种情况应该总是如此.
        if (!resource.isAbsolute()) {
            log.warn(sm.getString("hostConfig.resourceNotAbsolute", app.name, resource));
            return false;
        }

        // Determine where the resource is located
        String canonicalLocation;
        try {
            canonicalLocation = resource.getParentFile().getCanonicalPath();
        } catch (IOException e) {
            log.warn(sm.getString(
                    "hostConfig.canonicalizing", resource.getParentFile(), app.name), e);
            return false;
        }

        String canonicalAppBase;
        try {
            canonicalAppBase = host.getAppBaseFile().getCanonicalPath();
        } catch (IOException e) {
            log.warn(sm.getString(
                    "hostConfig.canonicalizing", host.getAppBaseFile(), app.name), e);
            return false;
        }

        if (canonicalLocation.equals(canonicalAppBase)) {
            // Resource is located in the appBase so it may be deleted
            return true;
        }

        String canonicalConfigBase;
        try {
            canonicalConfigBase = host.getConfigBaseFile().getCanonicalPath();
        } catch (IOException e) {
            log.warn(sm.getString(
                    "hostConfig.canonicalizing", host.getConfigBaseFile(), app.name), e);
            return false;
        }

        if (canonicalLocation.equals(canonicalConfigBase) &&
                resource.getName().endsWith(".xml")) {
            // Resource is an xml file in the configBase so it may be deleted
            return true;
        }

        // All other resources should not be deleted
        return false;
    }


    public void beforeStart() {
        if (host.getCreateDirs()) {
            File[] dirs = new File[] {host.getAppBaseFile(),host.getConfigBaseFile()};
            for (int i=0; i<dirs.length; i++) {
                if (!dirs[i].mkdirs() && !dirs[i].isDirectory()) {
                    log.error(sm.getString("hostConfig.createDirs",dirs[i]));
                }
            }
        }
    }


    /**
     * 处理一个"start" 事件.
     */
    public void start() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.start"));

        try {
            ObjectName hostON = host.getObjectName();
            oname = new ObjectName
                (hostON.getDomain() + ":type=Deployer,host=" + host.getName());
            Registry.getRegistry(null, null).registerComponent
                (this, oname, this.getClass().getName());
        } catch (Exception e) {
            log.error(sm.getString("hostConfig.jmx.register", oname), e);
        }

        if (!host.getAppBaseFile().isDirectory()) {
            log.error(sm.getString("hostConfig.appBase", host.getName(),
                    host.getAppBaseFile().getPath()));
            host.setDeployOnStartup(false);
            host.setAutoDeploy(false);
        }

        if (host.getDeployOnStartup())
            deployApps();

    }


    /**
     * 处理一个 "stop"事件.
     */
    public void stop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.stop"));

        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
            } catch (Exception e) {
                log.error(sm.getString("hostConfig.jmx.unregister", oname), e);
            }
        }
        oname = null;
    }


    /**
     * 检查所有应用程序状态.
     */
    protected void check() {

        if (host.getAutoDeploy()) {
            // 检查资源修改以触发重新部署
            DeployedApplication[] apps =
                deployed.values().toArray(new DeployedApplication[0]);
            for (int i = 0; i < apps.length; i++) {
                if (!isServiced(apps[i].name))
                    checkResources(apps[i], false);
            }

            // 检查旧版本的应用程序，这些应用程序现在无法部署
            if (host.getUndeployOldVersions()) {
                checkUndeploy();
            }

            // Hotdeploy applications
            deployApps();
        }
    }


    /**
     * 检查特定Web应用程序的状态并重新加载、重新部署或部署.
     * 此方法用于诸如管理Web应用程序的功能，并需要触发适当的操作来部署它们. 此方法假定Web应用程序当前被标记为服务，并且在调用该方法之前已经完成了任何上传/更新.
     * 作为检查结果采取的任何行动将在该方法返回之前完成.
     *
     * @param name 要检查的Web应用程序的名称
     */
    public void check(String name) {
        DeployedApplication app = deployed.get(name);
        if (app != null) {
            checkResources(app, true);
        }
        deployApps(name);
    }

    /**
     * 使用并行部署检查旧版本的应用程序，这些并行部署现在未被使用（没有活动会话），无法部署任何已找到的应用程序.
     */
    public synchronized void checkUndeploy() {
        // Need ordered set of names
        SortedSet<String> sortedAppNames = new TreeSet<>();
        sortedAppNames.addAll(deployed.keySet());

        if (sortedAppNames.size() < 2) {
            return;
        }
        Iterator<String> iter = sortedAppNames.iterator();

        ContextName previous = new ContextName(iter.next(), false);
        do {
            ContextName current = new ContextName(iter.next(), false);

            if (current.getPath().equals(previous.getPath())) {
                // 当前和先前的是相同路径 - 当前的将永远是一个后来的版本
                Context previousContext = (Context) host.findChild(previous.getName());
                Context currentContext = (Context) host.findChild(current.getName());
                if (previousContext != null && currentContext != null &&
                        currentContext.getState().isAvailable() &&
                        !isServiced(previous.getName())) {
                    Manager manager = previousContext.getManager();
                    if (manager != null) {
                        int sessionCount;
                        if (manager instanceof DistributedManager) {
                            sessionCount = ((DistributedManager) manager).getActiveSessionsFull();
                        } else {
                            sessionCount = manager.getActiveSessions();
                        }
                        if (sessionCount == 0) {
                            if (log.isInfoEnabled()) {
                                log.info(sm.getString(
                                        "hostConfig.undeployVersion", previous.getName()));
                            }
                            DeployedApplication app = deployed.get(previous.getName());
                            String[] resources = app.redeployResources.keySet().toArray(new String[0]);
                            // Version is unused - undeploy it completely
                            // The -1 is a 'trick' to ensure all redeploy
                            // resources are removed
                            undeploy(app);
                            deleteRedeployResources(app, resources, -1, true);
                        }
                    }
                }
            }
            previous = current;
        } while (iter.hasNext());
    }

    /**
     * 添加一个由我们管理的新上下文.
     * 管理应用的入口点, 和其他JMX Context 控制器.
     * 
     * @param context The context instance
     */
    public void manageApp(Context context)  {

        String contextName = context.getName();

        if (deployed.containsKey(contextName))
            return;

        DeployedApplication deployedApp =
                new DeployedApplication(contextName, false);

        // 添加相关docBase 到部署的列表，如果它是一个 WAR
        boolean isWar = false;
        if (context.getDocBase() != null) {
            File docBase = new File(context.getDocBase());
            if (!docBase.isAbsolute()) {
                docBase = new File(host.getAppBaseFile(), context.getDocBase());
            }
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                    Long.valueOf(docBase.lastModified()));
            if (docBase.getAbsolutePath().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                isWar = true;
            }
        }
        host.addChild(context);
        // 添加最终的未打包的 WAR和所有监视的资源
        boolean unpackWAR = unpackWARs;
        if (unpackWAR && context instanceof StandardContext) {
            unpackWAR = ((StandardContext) context).getUnpackWAR();
        }
        if (isWar && unpackWAR) {
            File docBase = new File(host.getAppBaseFile(), context.getBaseName());
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        Long.valueOf(docBase.lastModified()));
            addWatchedResources(deployedApp, docBase.getAbsolutePath(), context);
        } else {
            addWatchedResources(deployedApp, null, context);
        }
        deployed.put(contextName, deployedApp);
    }

    /**
     * 从我们的控制中删除Web应用程序.
     * 管理应用的入口点, 和其他JMX Context 控制器.
     * 
     * @param contextName The context name
     */
    public void unmanageApp(String contextName) {
        if(isServiced(contextName)) {
            deployed.remove(contextName);
            host.removeChild(host.findChild(contextName));
        }
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * 此类表示已部署应用程序的状态, 以及监控资源.
     */
    protected static class DeployedApplication {
        public DeployedApplication(String name, boolean hasDescriptor) {
            this.name = name;
            this.hasDescriptor = hasDescriptor;
        }

        /**
         * 应用上下文路径. 断言是(host.getChild(name) != null).
         */
        public final String name;

        /**
         * 这个应用在主机的configBase上是否有一个context.xml 描述符?
         */
        public final boolean hasDescriptor;

        /**
         * 对指定（静态）资源的任何修改都将导致应用程序的重新部署. 如果指定的任何资源被移除, 应用将被卸载.
    	 * 通常包含类似context.xml文件的资源, 一个压缩后的 WAR 路径.
         * 该值是最后一次修改时间.
         */
        public final LinkedHashMap<String, Long> redeployResources =
                new LinkedHashMap<>();

        /**
         * 对指定（静态）资源的任何修改都会导致应用程序的重新加载. 通常包含类似应用web.xml文件的资源, 但可以配置为包含附加描述符.
         * 该值是最后一次修改时间.
         */
        public final HashMap<String, Long> reloadResources = new HashMap<>();

        /**
         * 应用程序上一次提供服务的时间戳.
         */
        public long timestamp = System.currentTimeMillis();

        /**
         * 在某些情况下, 例如unpackWARs 是 true, 以将目录添加到被忽略的appBase中.
         * 此标志指示用户已被警告，因此警告不会在自动部署器的每次运行中记录.
         */
        public boolean loggedDirWarning = false;
    }

    private static class DeployDescriptor implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File descriptor;

        public DeployDescriptor(HostConfig config, ContextName cn,
                File descriptor) {
            this.config = config;
            this.cn = cn;
            this.descriptor= descriptor;
        }

        @Override
        public void run() {
            config.deployDescriptor(cn, descriptor);
        }
    }

    private static class DeployWar implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File war;

        public DeployWar(HostConfig config, ContextName cn, File war) {
            this.config = config;
            this.cn = cn;
            this.war = war;
        }

        @Override
        public void run() {
            config.deployWAR(cn, war);
        }
    }

    private static class DeployDirectory implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File dir;

        public DeployDirectory(HostConfig config, ContextName cn, File dir) {
            this.config = config;
            this.cn = cn;
            this.dir = dir;
        }

        @Override
        public void run() {
            config.deployDirectory(cn, dir);
        }
    }


    /*
     * 目的是为HostConfig提供一种方法，来获取Context删除一个扩展的 WAR, 在Context 停止之后.
     *
     * LifecycleListener方法提供更大的灵活性，并在将来启用修改/扩展/删除的行为, 而不用修改Context API.
     */
    private static class ExpandedDirectoryRemovalListener implements LifecycleListener {

        private final File toDelete;
        private final String newDocBase;

        /**
         * @param toDelete 要删除的文件(表示扩展的 WAR的目录)
         * @param newDocBase Context的docBase
         */
        public ExpandedDirectoryRemovalListener(File toDelete, String newDocBase) {
            this.toDelete = toDelete;
            this.newDocBase = newDocBase;
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType())) {
                // The context has stopped.
                Context context = (Context) event.getLifecycle();

                // 删除旧的扩展的 WAR.
                ExpandWar.delete(toDelete);

                // 重置 docBase to trigger re-expansion of the WAR.
                context.setDocBase(newDocBase);

                // 从Context删除这个监听器, 否则每当Context停止时，它都会运行
                context.removeLifecycleListener(this);
            }
        }
    }
}
