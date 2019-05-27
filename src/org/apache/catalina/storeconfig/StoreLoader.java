package org.apache.catalina.storeconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.SAXException;

/**
 * <b>XML Format </b>
 *
 * <pre>
 * {@code
 *       <Registry name="" encoding="UTF8" >
 *         <Description
 *             tag="Server"
 *             standard="true"
 *             default="true"
 *             tagClass="org.apache.catalina.core.StandardServer"
 *             storeFactoryClass="org.apache.catalina.storeconfig.StandardServerSF">
 *           <TransientAttributes>
 *             <Attribute></Attribute>
 *           </TransientAttributes>
 *           <TransientChildren>
 *             <Child></Child>
 *           </TransientChildren>
 *         </Description>
 *   ...
 *       </Registry>
 * }
 * </pre>
 */
public class StoreLoader {
    private static Log log = LogFactory.getLog(StoreLoader.class);

    /**
     * 用于解析注册描述符的<code>Digester</code>实例.
     */
    protected static final Digester digester = createDigester();

    private StoreRegistry registry;

    private URL registryResource ;

    public StoreRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(StoreRegistry registry) {
        this.registry = registry;
    }

    /**
     * 创建并配置设置store注册的Digester.
     * 
     * @return 用于解析配置的XML digester
     */
    protected static Digester createDigester() {
        long t1 = System.currentTimeMillis();
        // Initialize the digester
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setClassLoader(StoreRegistry.class.getClassLoader());

        // Configure the actions we will be using
        digester.addObjectCreate("Registry",
                "org.apache.catalina.storeconfig.StoreRegistry", "className");
        digester.addSetProperties("Registry");
        digester
                .addObjectCreate("Registry/Description",
                        "org.apache.catalina.storeconfig.StoreDescription",
                        "className");
        digester.addSetProperties("Registry/Description");
        digester.addRule("Registry/Description", new StoreFactoryRule(
                "org.apache.catalina.storeconfig.StoreFactoryBase",
                "storeFactoryClass",
                "org.apache.catalina.storeconfig.StoreAppender",
                "storeAppenderClass"));
        digester.addSetNext("Registry/Description", "registerDescription",
                "org.apache.catalina.storeconfig.StoreDescription");
        digester.addCallMethod("Registry/Description/TransientAttribute",
                "addTransientAttribute", 0);
        digester.addCallMethod("Registry/Description/TransientChild",
                "addTransientChild", 0);

        long t2 = System.currentTimeMillis();
        if (log.isDebugEnabled())
            log.debug("Digester for server-registry.xml created " + (t2 - t1));
        return (digester);

    }

    /**
     * 查找主配置文件.
     * 
     * @param aFile 文件名, 绝对或相对于<code>${catalina.base}/conf</code>, 默认使用<code>server-registry.xml</code>
     * @return The file
     */
    protected File serverFile(String aFile) {

        if (aFile == null || aFile.length() < 1)
            aFile = "server-registry.xml";
        File file = new File(aFile);
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base") + "/conf",
                    aFile);
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            log.error(e);
        }
        return (file);
    }

    /**
     * 从外部资源加载主配置文件.
     *
     * @param aURL 配置文件的URL
     */
    public void load(String aURL) {
        synchronized (digester) {
            File aRegistryFile = serverFile(aURL);
            try {
                registry = (StoreRegistry) digester.parse(aRegistryFile);
                registryResource = aRegistryFile.toURI().toURL();
            } catch (IOException e) {
                log.error(e);
            } catch (SAXException e) {
                log.error(e);
            }
        }
    }

    /**
     * 加载默认的
     * <ul>
     * <li>系统属性 URL catalina.storeregistry</li>
     * <li>File ${catalina.base}/conf/server-registry.xml</li>
     * <li>类资源 org/apache/catalina/storeconfig/server-registry.xml
     * </li>
     * </ul>
     */
    public void load() {

        InputStream is = null;
        registryResource = null ;
        try {
            String configUrl = getConfigUrl();
            if (configUrl != null) {
                is = (new URL(configUrl)).openStream();
                if (log.isInfoEnabled())
                    log.info("Find registry server-registry.xml from system property at url "
                            + configUrl);
                registryResource = new URL(configUrl);
            }
        } catch (Throwable t) {
            // Ignore
        }
        if (is == null) {
            try {
                File home = new File(getCatalinaBase());
                File conf = new File(home, "conf");
                File reg = new File(conf, "server-registry.xml");
                is = new FileInputStream(reg);
                if (log.isInfoEnabled())
                    log.info("Find registry server-registry.xml at file "
                            + reg.getCanonicalPath());
                registryResource = reg.toURI().toURL();
            } catch (Throwable t) {
                // Ignore
            }
        }
        if (is == null) {
            try {
                is = StoreLoader.class
                        .getResourceAsStream("/org/apache/catalina/storeconfig/server-registry.xml");
                if (log.isDebugEnabled())
                    log.debug("Find registry server-registry.xml at classpath resource");
                registryResource = StoreLoader.class
                    .getResource("/org/apache/catalina/storeconfig/server-registry.xml");

            } catch (Throwable t) {
                // Ignore
            }
        }
        if (is != null) {
            try {
                synchronized (digester) {
                    registry = (StoreRegistry) digester.parse(is);
                }
            } catch (Throwable t) {
                log.error(t);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        if (is == null) {
            log.error("Failed to load server-registry.xml");
        }
    }

    /**
     * @return catalina.home 环境变量.
     */
    private static String getCatalinaHome() {
        return System.getProperty("catalina.home", System
                .getProperty("user.dir"));
    }

    /**
     * @return catalina.base 环境变量.
     */
    private static String getCatalinaBase() {
        return System.getProperty("catalina.base", getCatalinaHome());
    }

    /**
     * @return 配置 URL.
     */
    private static String getConfigUrl() {
        return System.getProperty("catalina.storeconfig");
    }

    /**
     * @return the registryResource.
     */
    public URL getRegistryResource() {
        return registryResource;
    }
}
