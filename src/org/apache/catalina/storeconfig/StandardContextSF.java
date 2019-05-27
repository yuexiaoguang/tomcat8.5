package org.apache.catalina.storeconfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.util.ContextName;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.http.CookieProcessor;

/**
 * 存储server.xml Context元素及其子级
 * <ul>
 * <li>在server.xml中存储所有的上下文</li>
 * <li>存储现有的 app.xml context a conf/enginename/hostname/app.xml</li>
 * <li>备份存储</li>
 * </ul>
 */
public class StandardContextSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(StandardContextSF.class);

    /**
     * Store a Context as Separate file as configFile value from context exists.
     * filename can be relative to catalina.base.
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aContext)
            throws Exception {

        if (aContext instanceof StandardContext) {
            StoreDescription desc = getRegistry().findDescription(
                    aContext.getClass());
            if (desc.isStoreSeparate()) {
                URL configFile = ((StandardContext) aContext)
                        .getConfigFile();
                if (configFile != null) {
                    if (desc.isExternalAllowed()) {
                        if (desc.isBackup())
                            storeWithBackup((StandardContext) aContext);
                        else
                            storeContextSeparate(aWriter, indent,
                                    (StandardContext) aContext);
                        return;
                    }
                } else if (desc.isExternalOnly()) {
                    // Set a configFile so that the configuration is actually saved
                    Context context = ((StandardContext) aContext);
                    Host host = (Host) context.getParent();
                    File configBase = host.getConfigBaseFile();
                    ContextName cn = new ContextName(context.getName(), false);
                    String baseName = cn.getBaseName();
                    File xml = new File(configBase, baseName + ".xml");
                    context.setConfigFile(xml.toURI().toURL());
                    if (desc.isBackup())
                        storeWithBackup((StandardContext) aContext);
                    else
                        storeContextSeparate(aWriter, indent,
                                (StandardContext) aContext);
                    return;
                }
            }
        }
        super.store(aWriter, indent, aContext);

    }

    /**
     * Store a Context without backup add separate file or when configFile =
     * null a aWriter.
     *
     * @param aWriter Current output writer
     * @param indent Indentation level
     * @param aContext The context which will be stored
     * @throws Exception Configuration storing error
     */
    protected void storeContextSeparate(PrintWriter aWriter, int indent,
            StandardContext aContext) throws Exception {
        URL configFile = aContext.getConfigFile();
        if (configFile != null) {
            File config = new File(configFile.toURI());
            if (!config.isAbsolute()) {
                config = new File(System.getProperty("catalina.base"),
                        config.getPath());
            }
            if( (!config.isFile()) || (!config.canWrite())) {
                log.error("Cannot write context output file at "
                            + configFile + ", not saving.");
                throw new IOException("Context save file at "
                                      + configFile
                                      + " not a file, or not writable.");
            }
            if (log.isInfoEnabled())
                log.info("Store Context " + aContext.getPath()
                        + " separate at file " + config);
            try (FileOutputStream fos = new FileOutputStream(config);
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                            fos , getRegistry().getEncoding()))) {
                storeXMLHead(writer);
                super.store(writer, -2, aContext);
            }
        } else {
            super.store(aWriter, indent, aContext);
        }
    }

    /**
     * Store the Context with a Backup.
     *
     * @param aContext The context which will be stored
     * @throws Exception Configuration storing error
     */
    protected void storeWithBackup(StandardContext aContext) throws Exception {
        StoreFileMover mover = getConfigFileWriter(aContext);
        if (mover != null) {
            // Bugzilla 37781 Check to make sure we can write this output file
            if ((mover.getConfigOld() == null)
                    || (mover.getConfigOld().isDirectory())
                    || (mover.getConfigOld().exists() &&
                            !mover.getConfigOld().canWrite())) {
                log.error("Cannot move orignal context output file at "
                        + mover.getConfigOld());
                throw new IOException("Context orginal file at "
                        + mover.getConfigOld()
                        + " is null, not a file or not writable.");
            }
            File dir = mover.getConfigSave().getParentFile();
            if (dir != null && dir.isDirectory() && (!dir.canWrite())) {
                log.error("Cannot save context output file at "
                        + mover.getConfigSave());
                throw new IOException("Context save file at "
                        + mover.getConfigSave() + " is not writable.");
            }
            if (log.isInfoEnabled())
                log.info("Store Context " + aContext.getPath()
                        + " separate with backup (at file "
                        + mover.getConfigSave() + " )");

            try (PrintWriter writer = mover.getWriter()) {
                storeXMLHead(writer);
                super.store(writer, -2, aContext);
            }
            mover.move();
        }
    }

    /**
     * 获取上下文显式的writer (context.getConfigFile()).
     *
     * @param context 将被存储的上下文
     * @return The file mover
     * @throws Exception 为配置文件获取writer时出错
     */
    protected StoreFileMover getConfigFileWriter(Context context)
            throws Exception {
        URL configFile = context.getConfigFile();
        StoreFileMover mover = null;
        if (configFile != null) {
            File config = new File(configFile.toURI());
            if (!config.isAbsolute()) {
                config = new File(System.getProperty("catalina.base"),
                        config.getPath());
            }
            // Open an output writer for the new configuration file
            mover = new StoreFileMover("", config.getCanonicalPath(),
                    getRegistry().getEncoding());
        }
        return mover;
    }

    /**
     * 保存指定的上下文元素子级.
     *
     * @param aWriter Current output writer
     * @param indent 缩进等级
     * @param aContext 要保存的Context
     * @param parentDesc 元素描述
     * @throws Exception 配置存储错误
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aContext,
            StoreDescription parentDesc) throws Exception {
        if (aContext instanceof StandardContext) {
            StandardContext context = (StandardContext) aContext;
            // 保存嵌套的 <Listener> 元素
            LifecycleListener listeners[] = context.findLifecycleListeners();
            ArrayList<LifecycleListener> listenersArray = new ArrayList<>();
            for (LifecycleListener listener : listeners) {
                if (!(listener instanceof ThreadLocalLeakPreventionListener)) {
                    listenersArray.add(listener);
                }
            }
            storeElementArray(aWriter, indent, listenersArray.toArray());

            // 保存嵌套的 <Valve> 元素
            Valve valves[] = context.getPipeline().getValves();
            storeElementArray(aWriter, indent, valves);

            // 保存嵌套的 <Loader> 元素
            Loader loader = context.getLoader();
            storeElement(aWriter, indent, loader);

            // 保存嵌套的 <Manager> 元素
            if (context.getCluster() == null || !context.getDistributable()) {
                Manager manager = context.getManager();
                storeElement(aWriter, indent, manager);
            }

            // 保存嵌套的 <Realm> 元素
            Realm realm = context.getRealm();
            if (realm != null) {
                Realm parentRealm = null;
                // @TODO is this case possible?
                if (context.getParent() != null) {
                    parentRealm = context.getParent().getRealm();
                }
                if (realm != parentRealm) {
                    storeElement(aWriter, indent, realm);
                }
            }
            // 保存嵌套的资源
            WebResourceRoot resources = context.getResources();
            storeElement(aWriter, indent, resources);

            // 保存嵌套的 <WrapperListener> 元素
            String wLifecycles[] = context.findWrapperLifecycles();
            getStoreAppender().printTagArray(aWriter, "WrapperListener",
                    indent + 2, wLifecycles);
            // 保存嵌套的 <WrapperLifecycle> 元素
            String wListeners[] = context.findWrapperListeners();
            getStoreAppender().printTagArray(aWriter, "WrapperLifecycle",
                    indent + 2, wListeners);

            // 保存嵌套的 <Parameter> 元素
            ApplicationParameter[] appParams = context
                    .findApplicationParameters();
            storeElementArray(aWriter, indent, appParams);

            // 保存嵌套的命名资源元素 (EJB,Resource,...)
            NamingResourcesImpl nresources = context.getNamingResources();
            storeElement(aWriter, indent, nresources);

            // 保存嵌套的监视资源 <WatchedResource>
            String[] wresources = context.findWatchedResources();
            wresources = filterWatchedResources(context, wresources);
            getStoreAppender().printTagArray(aWriter, "WatchedResource",
                    indent + 2, wresources);

            // 保存嵌套的 <JarScanner> 元素
            JarScanner jarScanner = context.getJarScanner();
            storeElement(aWriter, indent, jarScanner);

            // 保存嵌套的 <CookieProcessor> 元素
            CookieProcessor cookieProcessor = context.getCookieProcessor();
            storeElement(aWriter, indent, cookieProcessor);
        }
    }

    /**
     * 返回关联的Host的 "configuration root"目录.
     * @param context context实例
     * @return a file to the configuration base path
     */
    protected File configBase(Context context) {

        File file = new File(System.getProperty("catalina.base"), "conf");
        Container host = context.getParent();

        if (host instanceof Host) {
            Container engine = host.getParent();
            if (engine instanceof Engine) {
                file = new File(file, engine.getName());
            }
            file = new File(file, host.getName());
            try {
                file = file.getCanonicalFile();
            } catch (IOException e) {
                log.error(e);
            }
        }
        return (file);

    }

    /**
     * 过滤出默认的受监视的资源, 并删除标准的那个.
     *
     * @param context The context instance
     * @param wresources 原始监视资源列表
     * @return 过滤的受监视的资源
     * @throws Exception 配置存储错误
     * 
     * TODO relative watched resources
     * TODO absolute handling configFile
     * TODO Filename case handling for Windows?
     * TODO digester variable substitution $catalina.base, $catalina.home
     */
    protected String[] filterWatchedResources(StandardContext context,
            String[] wresources) throws Exception {
        File configBase = configBase(context);
        String confContext = new File(System.getProperty("catalina.base"),
                "conf/context.xml").getCanonicalPath();
        String confWeb = new File(System.getProperty("catalina.base"),
                "conf/web.xml").getCanonicalPath();
        String confHostDefault = new File(configBase, "context.xml.default")
                .getCanonicalPath();
        String configFile = (context.getConfigFile() != null ? new File(context.getConfigFile().toURI()).getCanonicalPath() : null);
        String webxml = "WEB-INF/web.xml" ;

        List<String> resource = new ArrayList<>();
        for (int i = 0; i < wresources.length; i++) {

            if (wresources[i].equals(confContext))
                continue;
            if (wresources[i].equals(confWeb))
                continue;
            if (wresources[i].equals(confHostDefault))
                continue;
            if (wresources[i].equals(configFile))
                continue;
            if (wresources[i].equals(webxml))
                continue;
            resource.add(wresources[i]);
        }
        return resource.toArray(new String[resource.size()]);
    }

}
