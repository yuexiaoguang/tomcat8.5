package org.apache.tomcat.util.scan;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.servlet.ServletContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;

/**
 * 默认{@link JarScanner}实现扫描 WEB-INF/lib 目录, 接下来是提供的类加载器, 然后再处理类加载器层次结构.
 * 这个实现足以满足Servlet 3.0规范的要求, 并提供许多特定于Tomcat的扩展. 扩展是:
 * <ul>
 *   <li>扫描类加载器层次结构 (默认启用)</li>
 *   <li>测试所有文件, 看看它们是否是JAR (默认禁用)</li>
 *   <li>测试所有目录, 看看它们是否是加压后的JAR (默认禁用)</li>
 * </ul>
 * 所有的扩展都可以通过配置来控制.
 */
public class StandardJarScanner implements JarScanner {

    private static final Log log = LogFactory.getLog(StandardJarScanner.class);

    /**
     * 此包的字符串资源.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    private static final Set<ClassLoader> CLASSLOADER_HIERARCHY;

    static {
        Set<ClassLoader> cls = new HashSet<>();

        ClassLoader cl = StandardJarScanner.class.getClassLoader();
        while (cl != null) {
            cls.add(cl);
            cl = cl.getParent();
        }

        CLASSLOADER_HIERARCHY = Collections.unmodifiableSet(cls);
    }

    /**
     * 控制类路径扫描扩展.
     */
    private boolean scanClassPath = true;
    public boolean isScanClassPath() {
        return scanClassPath;
    }
    public void setScanClassPath(boolean scanClassPath) {
        this.scanClassPath = scanClassPath;
    }

    /**
     * 控制JAR文件清单扫描扩展.
     */
    private boolean scanManifest = true;
    public boolean isScanManifest() {
        return scanManifest;
    }
    public void setScanManifest(boolean scanManifest) {
        this.scanManifest = scanManifest;
    }

    /**
     * 控制测试所有文件, 以查看它们是jar文件扩展名.
     */
    private boolean scanAllFiles = false;
    public boolean isScanAllFiles() {
        return scanAllFiles;
    }
    public void setScanAllFiles(boolean scanAllFiles) {
        this.scanAllFiles = scanAllFiles;
    }

    /**
     * 控制测试所有目录, 以查看它们是解压后的jar文件扩展名.
     */
    private boolean scanAllDirectories = true;
    public boolean isScanAllDirectories() {
        return scanAllDirectories;
    }
    public void setScanAllDirectories(boolean scanAllDirectories) {
        this.scanAllDirectories = scanAllDirectories;
    }

    /**
     * 控制引导程序类路径的测试，引导程序类路径由JVM提供的运行时类和任何已安装的系统扩展组成.
     */
    private boolean scanBootstrapClassPath = false;
    public boolean isScanBootstrapClassPath() {
        return scanBootstrapClassPath;
    }
    public void setScanBootstrapClassPath(boolean scanBootstrapClassPath) {
        this.scanBootstrapClassPath = scanBootstrapClassPath;
    }

    /**
     * 控制对扫描JAR的结果的过滤
     */
    private JarScanFilter jarScanFilter = new StandardJarScanFilter();
    @Override
    public JarScanFilter getJarScanFilter() {
        return jarScanFilter;
    }
    @Override
    public void setJarScanFilter(JarScanFilter jarScanFilter) {
        this.jarScanFilter = jarScanFilter;
    }

    /**
     * 扫描jar文件提供的ServletContext和类加载器. 找到的每个jar文件将传递给回调处理程序进行处理.
     *
     * @param scanType      执行JAR扫描的类型. 这将传递给过滤器，过滤器使用它来确定如何过滤结果
     * @param context       ServletContext - 用于定位和访问WEB-INF/lib
     * @param callback      处理找到的任何JAR的处理程序
     */
    @Override
    public void scan(JarScanType scanType, ServletContext context,
            JarScannerCallback callback) {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.webinflibStart"));
        }

        Set<URL> processedURLs = new HashSet<>();

        // Scan WEB-INF/lib
        Set<String> dirList = context.getResourcePaths(Constants.WEB_INF_LIB);
        if (dirList != null) {
            for (String path : dirList) {
                if (path.endsWith(Constants.JAR_EXT) &&
                        getJarScanFilter().check(scanType,
                                path.substring(path.lastIndexOf('/')+1))) {
                    // 需要扫描这个 JAR
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jarScan.webinflibJarScan", path));
                    }
                    URL url = null;
                    try {
                        url = context.getResource(path);
                        processedURLs.add(url);
                        process(scanType, callback, url, path, true, null);
                    } catch (IOException e) {
                        log.warn(sm.getString("jarScan.webinflibFail", url), e);
                    }
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("jarScan.webinflibJarNoScan", path));
                    }
                }
            }
        }

        // Scan WEB-INF/classes
        try {
            URL webInfURL = context.getResource(Constants.WEB_INF_CLASSES);
            if (webInfURL != null) {
                // WEB-INF/classes 还将包含在web应用程序类加载器返回的URL中，因此确保下面的类路径扫描不会重新扫描此位置.
                processedURLs.add(webInfURL);

                if (isScanAllDirectories()) {
                    URL url = context.getResource(Constants.WEB_INF_CLASSES + "/META-INF");
                    if (url != null) {
                        try {
                            callback.scanWebInfClasses();
                        } catch (IOException e) {
                            log.warn(sm.getString("jarScan.webinfclassesFail"), e);
                        }
                    }
                }
            }
        } catch (MalformedURLException e) {
            // Ignore. Won't happen. URL是正确的形式.
        }

        // Scan the classpath
        if (isScanClassPath()) {
            doScanClassPath(scanType, context, callback, processedURLs);
        }
    }


    protected void doScanClassPath(JarScanType scanType, ServletContext context,
            JarScannerCallback callback, Set<URL> processedURLs) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.classloaderStart"));
        }

        ClassLoader stopLoader = null;
        if (!isScanBootstrapClassPath()) {
            // 当到达引导类加载器时停止
            stopLoader = ClassLoader.getSystemClassLoader().getParent();
        }

        ClassLoader classLoader = context.getClassLoader();

        // JAR被当作应用程序提供，直到达到公共类加载器.
        boolean isWebapp = true;

        // 使用Deque，这样URL可以在处理过程中被移除，并且在处理过程中可以发现新URL.
        Deque<URL> classPathUrlsToProcess = new LinkedList<>();

        while (classLoader != null && classLoader != stopLoader) {
            if (classLoader instanceof URLClassLoader) {
                if (isWebapp) {
                    isWebapp = isWebappClassLoader(classLoader);
                }

                classPathUrlsToProcess.addAll(
                        Arrays.asList(((URLClassLoader) classLoader).getURLs()));

                processURLs(scanType, callback, processedURLs, isWebapp, classPathUrlsToProcess);
            }
            classLoader = classLoader.getParent();
        }

        if (JreCompat.isJre9Available()) {
            // 应用程序和平台类加载器不是URLClassLoader的实例. 在这种情况下使用类路径.
            addClassPath(classPathUrlsToProcess);
            // 还添加任何模块
            JreCompat.getInstance().addBootModulePath(classPathUrlsToProcess);
            processURLs(scanType, callback, processedURLs, false, classPathUrlsToProcess);
        }
    }


    protected void processURLs(JarScanType scanType, JarScannerCallback callback,
            Set<URL> processedURLs, boolean isWebapp, Deque<URL> classPathUrlsToProcess) {
        while (!classPathUrlsToProcess.isEmpty()) {
            URL url = classPathUrlsToProcess.pop();

            if (processedURLs.contains(url)) {
                // 跳过这个已经处理过的URL
                continue;
            }

            ClassPathEntry cpe = new ClassPathEntry(url);

            // 扫描JAR, 除非过滤器不能.
            // Directories are scanned for pluggability scans or
            // if scanAllDirectories is enabled unless the
            // filter says not to.
            if ((cpe.isJar() ||
                    scanType == JarScanType.PLUGGABILITY ||
                    isScanAllDirectories()) &&
                            getJarScanFilter().check(scanType,
                                    cpe.getName())) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jarScan.classloaderJarScan", url));
                }
                try {
                    processedURLs.add(url);
                    process(scanType, callback, url, null, isWebapp, classPathUrlsToProcess);
                } catch (IOException ioe) {
                    log.warn(sm.getString("jarScan.classloaderFail", url), ioe);
                }
            } else {
                // JAR / directory has been skipped
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("jarScan.classloaderJarNoScan", url));
                }
            }
        }
    }


    protected void addClassPath(Deque<URL> classPathUrlsToProcess) {
        String classPath = System.getProperty("java.class.path");

        if (classPath == null || classPath.length() == 0) {
            return;
        }

        String[] classPathEntries = classPath.split(File.pathSeparator);
        for (String classPathEntry : classPathEntries) {
            File f = new File(classPathEntry);
            try {
                classPathUrlsToProcess.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
                log.warn(sm.getString("jarScan.classPath.badEntry", classPathEntry), e);
            }
        }
    }


    /*
     * 由于类加载器层次结构可能会变得复杂, 此方法尝试应用以下规则:
     * 类加载器是Web应用程序类加载器，除非它加载了该类 (StandardJarScanner) 或是加载此类的类加载器的父级.
     *
     * 这应该意味着:
     *   WebApp类加载器是一个应用程序类加载器
     *   共享类加载器是应用程序类加载器
     *   服务器类加载器不是应用程序类加载器
     *   公共类加载器不是应用程序类加载器
     *   系统类加载器不是应用程序类加载器
     *   Bootstrap类加载器不是应用程序类加载器
     */
    private static boolean isWebappClassLoader(ClassLoader classLoader) {
        return !CLASSLOADER_HIERARCHY.contains(classLoader);
    }


    /*
     * 扫描带有可选扩展的JAR的URL，查看所有文件和所有目录.
     */
    protected void process(JarScanType scanType, JarScannerCallback callback,
            URL url, String webappPath, boolean isWebapp, Deque<URL> classPathUrlsToProcess)
            throws IOException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.jarUrlStart", url));
        }

        if ("jar".equals(url.getProtocol()) || url.getPath().endsWith(Constants.JAR_EXT)) {
            try (Jar jar = JarFactory.newInstance(url)) {
                if (isScanManifest()) {
                    processManifest(jar, isWebapp, classPathUrlsToProcess);
                }
                callback.scan(jar, webappPath, isWebapp);
            }
        } else if ("file".equals(url.getProtocol())) {
            File f;
            try {
                f = new File(url.toURI());
                if (f.isFile() && isScanAllFiles()) {
                    // 将此文件视为 JAR
                    URL jarURL = UriUtil.buildJarUrl(f);
                    try (Jar jar = JarFactory.newInstance(jarURL)) {
                        if (isScanManifest()) {
                            processManifest(jar, isWebapp, classPathUrlsToProcess);
                        }
                        callback.scan(jar, webappPath, isWebapp);
                    }
                } else if (f.isDirectory()) {
                    if (scanType == JarScanType.PLUGGABILITY) {
                        callback.scan(f, webappPath, isWebapp);
                    } else {
                        File metainf = new File(f.getAbsoluteFile() + File.separator + "META-INF");
                        if (metainf.isDirectory()) {
                            callback.scan(f, webappPath, isWebapp);
                        }
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // Wrap the exception and re-throw
                IOException ioe = new IOException();
                ioe.initCause(t);
                throw ioe;
            }
        }
    }


    private static void processManifest(Jar jar, boolean isWebapp,
            Deque<URL> classPathUrlsToProcess) throws IOException {

        // 未处理Web应用程序的JAR, 或者调用者未提供插入URL的 Deque.
        if (isWebapp || classPathUrlsToProcess == null) {
            return;
        }

        Manifest manifest = jar.getManifest();
        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            String classPathAttribute = attributes.getValue("Class-Path");
            if (classPathAttribute == null) {
                return;
            }
            String[] classPathEntries = classPathAttribute.split(" ");
            for (String classPathEntry : classPathEntries) {
                classPathEntry = classPathEntry.trim();
                if (classPathEntry.length() == 0) {
                    continue;
                }
                URL jarURL = jar.getJarFileURL();
                URL classPathEntryURL;
                try {
                    URI jarURI = jarURL.toURI();
                    /*
                     * Note: 从清单中解析相对URL有可能引入安全问题. 但是, 因为只有容器提供的JAR, 而不是Web应用程序提供的JAR被处理, 应该没有问题.
                     *       如果这个特性被扩展到包含Web应用程序提供的JAR, 应该添加检查以确保任何相对URL不超出Web应用程序之外.
                     */
                    URI classPathEntryURI = jarURI.resolve(classPathEntry);
                    classPathEntryURL = classPathEntryURI.toURL();
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jarScan.invalidUri", jarURL), e);
                    }
                    continue;
                }
                classPathUrlsToProcess.add(classPathEntryURL);
            }
        }
    }


    private static class ClassPathEntry {

        private final boolean jar;
        private final String name;

        public ClassPathEntry(URL url) {
            String path = url.getPath();
            int end = path.lastIndexOf(Constants.JAR_EXT);
            if (end != -1) {
                jar = true;
                int start = path.lastIndexOf('/', end);
                name = path.substring(start + 1, end + 4);
            } else {
                jar = false;
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                int start = path.lastIndexOf('/');
                name = path.substring(start + 1);
            }

        }

        public boolean isJar() {
            return jar;
        }

        public String getName() {
            return name;
        }
    }
}
