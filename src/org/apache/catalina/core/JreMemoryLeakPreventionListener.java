package org.apache.catalina.core;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLConnection;
import java.sql.DriverManager;
import java.util.StringTokenizer;
import java.util.concurrent.ForkJoinPool;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.startup.SafeForkJoinWorkerThreadFactory;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.compat.JreVendor;
import org.apache.tomcat.util.res.StringManager;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;

/**
 * 为解决已知的问题，java运行环境可以导致内存泄漏或锁定文件.
 * <p>
 * 当JRE代码使用上下文类加载器加载单例时，内存泄漏会发生，因为如果Web应用程序类加载器恰好是当时的上下文类加载器，则会导致内存泄漏.
 * 当Tomcat的公共类加载器是上下文类加载器时, 周围的工作是初始化这些单例.
 * <p>
 * 锁定文件通常是在访问JAR内部的资源而不首先禁用JAR URL连接缓存时发生的. 解决方法是默认情况下禁用此缓存.
 */
public class JreMemoryLeakPreventionListener implements LifecycleListener {

    private static final Log log =
        LogFactory.getLog(JreMemoryLeakPreventionListener.class);
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private static final String FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.threadFactory";
    /**
     * 防止内存泄漏, 当Web应用第一次触发调用<code>sun.awt.AppContext.getAppContext()</code>时.
     * 默认是<code>false</code>， 因为{@link java.beans.Introspector#flushCaches()}从1.7.0_02开始不再使用AppContext.
     * 而且, 从 1.7.0_25 开始, 调用此方法需要图形环境并启动AWT线程.
     */
    private boolean appContextProtection = false;
    public boolean isAppContextProtection() { return appContextProtection; }
    public void setAppContextProtection(boolean appContextProtection) {
        this.appContextProtection = appContextProtection;
    }

    /**
     * 防止内存泄漏, 当Web应用第一次触发调用<code>java.awt.Toolkit.getDefaultToolkit()</code>时.
     * 默认是<code>false</code> 因为启动了一个新线程.
     */
    private boolean awtThreadProtection = false;
    public boolean isAWTThreadProtection() { return awtThreadProtection; }
    public void setAWTThreadProtection(boolean awtThreadProtection) {
      this.awtThreadProtection = awtThreadProtection;
    }

    /**
     * 防止内存泄漏, 当Web应用第一次触发调用<code>sun.misc.GC.requestLatency(long)</code>时.
     * 第一次调用将启动一个GC Daemon线程，线程的上下文类加载器将配置为Web应用类加载器.
     * 默认是<code>true</code>.
     */
    private boolean gcDaemonProtection = true;
    public boolean isGcDaemonProtection() { return gcDaemonProtection; }
    public void setGcDaemonProtection(boolean gcDaemonProtection) {
        this.gcDaemonProtection = gcDaemonProtection;
    }

     /**
      * 防止内存泄漏, 当Web应用第一次触发调用<code>javax.security.auth.Policy</code>时.
      * 第一个调用通过引用上下文类加载器填充静态变量.
      * 默认是<code>true</code>.
      */
     private boolean securityPolicyProtection = true;
     public boolean isSecurityPolicyProtection() {
         return securityPolicyProtection;
     }
     public void setSecurityPolicyProtection(boolean securityPolicyProtection) {
         this.securityPolicyProtection = securityPolicyProtection;
     }

    /**
     * 防止内存泄漏, 当Web应用第一次触发调用<code>javax.security.auth.login.Configuration</code>时.
     * 第一个调用通过引用上下文类加载器填充静态变量.
      * 默认是<code>true</code>.
     */
    private boolean securityLoginConfigurationProtection = true;
    public boolean isSecurityLoginConfigurationProtection() {
        return securityLoginConfigurationProtection;
    }
    public void setSecurityLoginConfigurationProtection(
            boolean securityLoginConfigurationProtection) {
        this.securityLoginConfigurationProtection = securityLoginConfigurationProtection;
    }

     /**
     * 防止内存泄漏, 在Web应用部署期间，当Java Cryptography Architecture的初始化由MessageDigest初始化触发.
     * 这将偶尔启动一个令牌轮询线程，线程的上下文类加载器等于Web应用程序类加载器. 相反，早先初始化JCA.
     * 默认是<code>true</code>.
     */
    private boolean tokenPollerProtection = true;
    public boolean isTokenPollerProtection() { return tokenPollerProtection; }
    public void setTokenPollerProtection(boolean tokenPollerProtection) {
        this.tokenPollerProtection = tokenPollerProtection;
    }

    /**
     * 防止资源读取JAR文件，作为副作用，JAR文件被锁定.
     * 注意，这个禁用所有的{@link URLConnection}缓存, 不管什么类型.
     * 默认是<code>true</code>.
     */
    private boolean urlCacheProtection = true;
    public boolean isUrlCacheProtection() { return urlCacheProtection; }
    public void setUrlCacheProtection(boolean urlCacheProtection) {
        this.urlCacheProtection = urlCacheProtection;
    }

    /**
     * XML解析可以将Web应用程序类加载器锁定在内存中.
     * 这有多个根本原因. 其中一些是特别恶劣的，因为探查器可能无法识别与泄漏有关的任何GC根.
     * 例如, 使用YourKit 需要确保 HPROF 格式化内存快照用于跟踪一些泄露.
     */
    private boolean xmlParsingProtection = true;
    public boolean isXmlParsingProtection() { return xmlParsingProtection; }
    public void setXmlParsingProtection(boolean xmlParsingProtection) {
        this.xmlParsingProtection = xmlParsingProtection;
    }

    /**
     * 当<code>com.sun.jndi.ldap.LdapPoolManager</code>类初始化的时候产生线程，
     * 如果系统属性<code>com.sun.jndi.ldap.connect.pool.timeout</code>大于 0.
     * 该线程继承当前线程的上下文类加载器, 因此，如果Web应用程序首先使用<code>LdapPoolManager</code>，可能会有Web应用程序类加载程序泄漏.
     */
    private boolean ldapPoolProtection = true;
    public boolean isLdapPoolProtection() { return ldapPoolProtection; }
    public void setLdapPoolProtection(boolean ldapPoolProtection) {
        this.ldapPoolProtection = ldapPoolProtection;
    }

    /**
     * 第一次访问{@link DriverManager}将触发当前类加载器中所有的{@link java.sql.Driver}的加载.
     * 在大多数情况下，Web应用程序级的内存泄漏保护可以处理这一点，但是触发这里的加载具有较少的副作用.
     */
    private boolean driverManagerProtection = true;
    public boolean isDriverManagerProtection() {
        return driverManagerProtection;
    }
    public void setDriverManagerProtection(boolean driverManagerProtection) {
        this.driverManagerProtection = driverManagerProtection;
    }

    /**
     * {@link ForkJoinPool#commonPool()} 创建一个线程池, 默认情况下,
     * 创建线程，以保持对线程上下文类加载器的引用.
     */
    private boolean forkJoinCommonPoolProtection = true;
    public boolean getForkJoinCommonPoolProtection() {
        return forkJoinCommonPoolProtection;
    }
    public void setForkJoinCommonPoolProtection(boolean forkJoinCommonPoolProtection) {
        this.forkJoinCommonPoolProtection = forkJoinCommonPoolProtection;
    }

    /**
     * 在监听器启动期间，加载并初始化逗号分隔的完全限定类名列表.
     * 这允许预先加载已知的类，如果在请求处理过程中加载，则会引发类装载程序泄漏.
     */
    private String classesToInitialize = null;
    public String getClassesToInitialize() {
        return classesToInitialize;
    }
    public void setClassesToInitialize(String classesToInitialize) {
        this.classesToInitialize = classesToInitialize;
    }



    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // 当Tomcat启动时初始化这些类
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {

            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            try
            {
                // Use the system classloader as the victim for all this
                // ClassLoader pinning we're about to do.
                Thread.currentThread().setContextClassLoader(
                        ClassLoader.getSystemClassLoader());

                /*
                 * 第一次调用加载当前类加载器中的所有驱动程序
                 */
                if (driverManagerProtection) {
                    DriverManager.getDrivers();
                }

                /*
                 * 几个组件最终调用:
                 * sun.awt.AppContext.getAppContext()
                 *
                 * 那些已知触发内存泄漏的库/组件， 由于最终调用 getAppContext():
                 * - Google Web Toolkit 通过使用javax.imageio
                 * - Tomcat 通过使用 java.beans.Introspector.flushCaches()
                 *   在 1.7.0 到 1.7.0_01. 从 1.7.0_02 开始Introspector.flushCaches()使用的AppContext替换为ThreadGroupContext
                 * - others TBD
                 *
                 * 从 1.7.0_25开始, 由AWT-AppKit启动的线程中调用sun.awt.AppContext.getAppContext(),
                 * 需要一个图形环境可用.
                 */

                // 触发调用sun.awt.AppContext.getAppContext(). 将系统类加载器存储在内存中，但这不应该是问题.
                if (appContextProtection && !JreCompat.isJre8Available()) {
                    ImageIO.getCacheDirectory();
                }

                // 触发AWT (AWT-Windows, AWT-XAWT,等.)线程的创建
                if (awtThreadProtection && !JreCompat.isJre9Available()) {
                    java.awt.Toolkit.getDefaultToolkit();
                }

                /*
                 * 几个组件最终调用sun.misc.GC.requestLatency(long), 在不设置TCCL的情况下创建守护线程.
                 *
                 * 那些已知触发内存泄漏的库/组件
                 * 由于最终调用requestLatency(long):
                 * - javax.management.remote.rmi.RMIConnectorServer.start()
                 *
                 * Note: Long.MAX_VALUE 是导致线程终止的特殊情况
                 *
                 */
                if (gcDaemonProtection && !JreCompat.isJre9Available()) {
                    try {
                        Class<?> clazz = Class.forName("sun.misc.GC");
                        Method method = clazz.getDeclaredMethod(
                                "requestLatency",
                                new Class[] {long.class});
                        method.invoke(null, Long.valueOf(Long.MAX_VALUE - 1));
                    } catch (ClassNotFoundException e) {
                        if (JreVendor.IS_ORACLE_JVM) {
                            log.error(sm.getString(
                                    "jreLeakListener.gcDaemonFail"), e);
                        } else {
                            log.debug(sm.getString(
                                    "jreLeakListener.gcDaemonFail"), e);
                        }
                    } catch (SecurityException | NoSuchMethodException | IllegalArgumentException |
                            IllegalAccessException e) {
                        log.error(sm.getString("jreLeakListener.gcDaemonFail"),
                                e);
                    } catch (InvocationTargetException e) {
                        ExceptionUtils.handleThrowable(e.getCause());
                        log.error(sm.getString("jreLeakListener.gcDaemonFail"),
                                e);
                    }
                }

                /*
                 * 调用 getPolicy 将静态引用保留到上下文类加载器.
                 */
                if (securityPolicyProtection && !JreCompat.isJre8Available()) {
                    try {
                        // Policy.getPolicy();
                        Class<?> policyClass = Class
                                .forName("javax.security.auth.Policy");
                        Method method = policyClass.getMethod("getPolicy");
                        method.invoke(null);
                    } catch(ClassNotFoundException e) {
                        // Ignore. The class is deprecated.
                    } catch(SecurityException e) {
                        // Ignore. 不需要调用getPolicy()成功, 只需要触发静态初始化器.
                    } catch (NoSuchMethodException e) {
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    } catch (IllegalArgumentException e) {
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    } catch (IllegalAccessException e) {
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    } catch (InvocationTargetException e) {
                        ExceptionUtils.handleThrowable(e.getCause());
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    }
                }


                /*
                 * 初始化 javax.security.auth.login.Configuration 将静态引用保留到上下文类加载器.
                 */
                if (securityLoginConfigurationProtection && !JreCompat.isJre8Available()) {
                    try {
                        Class.forName("javax.security.auth.login.Configuration", true, ClassLoader.getSystemClassLoader());
                    } catch(ClassNotFoundException e) {
                        // Ignore
                    }
                }

                /*
                 * 在Web应用启动期间创建一个 MessageDigest初始化Java Cryptography Architecture.
                 * 在某些条件下，使用与Web应用程序类加载器相等的TCCL启动令牌轮询线程.
                 *
                 * 相反，现在初始化JCA.
                 *
                 * Fixed in Java 9 onwards (from early access build 133)
                 */
                if (tokenPollerProtection && !JreCompat.isJre9Available()) {
                    java.security.Security.getProviders();
                }

                /*
                 * 在不禁用缓存的情况下，几个组件最终打开JarURLConnection.
                 * 这实际上锁定了文件. 在Windows上更明显和更难忽视, 它影响所有操作系统.
                 *
                 * 那些已知触发这个问题的库/组件包括:
                 * - log4j 版本 1.2.15 和更早
                 * - javax.xml.bind.JAXBContext.newInstance()
                 *
                 * Java 9 onwards disables caching for JAR URLConnections
                 * Java 8 and earlier disables caching for all URLConnections
                 */

                // 将默认URL缓存策略设置为不缓存
                if (urlCacheProtection) {
                    try {
                        JreCompat.getInstance().disableCachingForJarUrlConnections();
                    } catch (IOException e) {
                        log.error(sm.getString("jreLeakListener.jarUrlConnCacheFail"), e);
                    }
                }

                /*
                 * Fixed in Java 9 onwards (from early access build 133)
                 */
                if (xmlParsingProtection && !JreCompat.isJre9Available()) {
                    // 影响XML解析的两个已知问题影响Java 7+. 这些问题都涉及缓存的异常实例，这些实例通过backtrace字段保存到TCCL的链接.
                    // 注意，当使用HPROF格式内存快照时，YourKit只显示此字段.
                    // https://bz.apache.org/bugzilla/show_bug.cgi?id=58486
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    try {
                        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
                        // Issue 1
                        // com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl
                        Document document = documentBuilder.newDocument();
                        document.createElement("dummy");
                        DOMImplementationLS implementation =
                                (DOMImplementationLS)document.getImplementation();
                        implementation.createLSSerializer().writeToString(document);
                        // Issue 1
                        // com.sun.org.apache.xerces.internal.dom.DOMNormalizer
                        document.normalize();
                    } catch (ParserConfigurationException e) {
                        log.error(sm.getString("jreLeakListener.xmlParseFail"),
                                e);
                    }
                }

                if (ldapPoolProtection && !JreCompat.isJre9Available()) {
                    try {
                        Class.forName("com.sun.jndi.ldap.LdapPoolManager");
                    } catch (ClassNotFoundException e) {
                        if (JreVendor.IS_ORACLE_JVM) {
                            log.error(sm.getString(
                                    "jreLeakListener.ldapPoolManagerFail"), e);
                        } else {
                            log.debug(sm.getString(
                                    "jreLeakListener.ldapPoolManagerFail"), e);
                        }
                    }
                }

                /*
                 * Present in Java 8 onwards
                 */
                if (forkJoinCommonPoolProtection && JreCompat.isJre8Available()) {
                    // 不要重写任何显式设置的属性
                    if (System.getProperty(FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY) == null) {
                        System.setProperty(FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY,
                                SafeForkJoinWorkerThreadFactory.class.getName());
                    }
                }

                if (classesToInitialize != null) {
                    StringTokenizer strTok =
                        new StringTokenizer(classesToInitialize, ", \r\n\t");
                    while (strTok.hasMoreTokens()) {
                        String classNameToLoad = strTok.nextToken();
                        try {
                            Class.forName(classNameToLoad);
                        } catch (ClassNotFoundException e) {
                            log.error(
                                sm.getString("jreLeakListener.classToInitializeFail",
                                    classNameToLoad), e);
                            // 继续加载下一个类
                        }
                    }
                }

            } finally {
                Thread.currentThread().setContextClassLoader(loader);
            }
        }
    }
}
