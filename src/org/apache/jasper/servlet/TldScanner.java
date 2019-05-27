package org.apache.jasper.servlet;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.TaglibDescriptor;

import org.apache.jasper.compiler.JarScannerFactory;
import org.apache.jasper.compiler.Localizer;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldParser;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.xml.sax.SAXException;

/**
 * 扫描并加载Web应用程序中包含的标记库描述符.
 */
public class TldScanner {
    private static final Log log = LogFactory.getLog(TldScanner.class);
    private static final String MSG = "org.apache.jasper.servlet.TldScanner";
    private static final String TLD_EXT = ".tld";
    private static final String WEB_INF = "/WEB-INF/";
    private final ServletContext context;
    private final TldParser tldParser;
    private final Map<String, TldResourcePath> uriTldResourcePathMap = new HashMap<>();
    private final Map<TldResourcePath, TaglibXml> tldResourcePathTaglibXmlMap = new HashMap<>();
    private final List<String> listeners = new ArrayList<>();

    /**
     * 使用应用程序的ServletContext初始化.
     *
     * @param context        应用程序的servletContext
     * @param namespaceAware 用于解析TLD文件的XML解析器是否应配置为可识别名称空间
     * @param validation     应该将用于解析TLD文件的XML解析器配置为使用验证
     * @param blockExternal  应该将用于解析TLD文件的XML解析器配置为对外部实体的块引用
     */
    public TldScanner(ServletContext context,
                      boolean namespaceAware,
                      boolean validation,
                      boolean blockExternal) {
        this.context = context;

        this.tldParser = new TldParser(namespaceAware, validation, blockExternal);
    }

    /**
     * 在规范定义的所有位置扫描TLD:
     * <ol>
     * <li>由平台定义的标记库</li>
     * <li>实体来自web.xml中的 &lt;jsp-config&gt;</li>
     * <li>/WEB-INF 下的资源</li>
     * <li>/WEB-INF/lib 下的jar文件</li>
     * <li>容器中的其他条目</li>
     * </ol>
     *
     * @throws IOException  如果扫描或加载TLD时出现问题
     * @throws SAXException 如果解析TLD时出现问题
     */
    public void scan() throws IOException, SAXException {
        scanPlatform();
        scanJspConfig();
        scanResourcePaths(WEB_INF);
        scanJars();
    }

    /**
     * 返回此扫描程序生成的TldResourcePath的URI映射.
     */
    public Map<String, TldResourcePath> getUriTldResourcePathMap() {
        return uriTldResourcePathMap;
    }

    /**
     * 返回由此扫描程序构建的TldResourcePath到已解析的XML文件的映射.
     */
    public Map<TldResourcePath,TaglibXml> getTldResourcePathTaglibXmlMap() {
        return tldResourcePathTaglibXmlMap;
    }

    /**
     * 返回扫描的TLD声明的所有监听器的列表.
     */
    public List<String> getListeners() {
        return listeners;
    }

    /**
     * 设置由digester 使用的类加载器，以便在此扫描时创建对象. 通常这只需要在使用JspC时设置.
     *
     * @param classLoader 在解析TLD时创建新对象时使用的类加载器
     */
    public void setClassLoader(ClassLoader classLoader) {
        tldParser.setClassLoader(classLoader);
    }

    /**
     * 扫描平台规范要求的TLD.
     */
    protected void scanPlatform() {
    }

    /**
     * 扫描 &lt;jsp-config&gt; 中定义的TLD.
     * @throws IOException 读取资源时出错
     * @throws SAXException XML解析错误
     */
    protected void scanJspConfig() throws IOException, SAXException {
        JspConfigDescriptor jspConfigDescriptor = context.getJspConfigDescriptor();
        if (jspConfigDescriptor == null) {
            return;
        }

        Collection<TaglibDescriptor> descriptors = jspConfigDescriptor.getTaglibs();
        for (TaglibDescriptor descriptor : descriptors) {
            String taglibURI = descriptor.getTaglibURI();
            String resourcePath = descriptor.getTaglibLocation();
            // Note: 虽然Servlet 2.4 DTD暗示该位置必须是以'/'开头的上下文相对路径, JSP.7.3.6.1明确说明了如何处理不以'/'开头的路径.
            if (!resourcePath.startsWith("/")) {
                resourcePath = WEB_INF + resourcePath;
            }
            if (uriTldResourcePathMap.containsKey(taglibURI)) {
                log.warn(Localizer.getMessage(MSG + ".webxmlSkip",
                        resourcePath,
                        taglibURI));
                continue;
            }

            if (log.isTraceEnabled()) {
                log.trace(Localizer.getMessage(MSG + ".webxmlAdd",
                        resourcePath,
                        taglibURI));
            }

            URL url = context.getResource(resourcePath);
            if (url != null) {
                TldResourcePath tldResourcePath;
                if (resourcePath.endsWith(".jar")) {
                    // 如果路径指向jar文件, TLD 被认为是在 META-INF/taglib.tld 里面 
                    tldResourcePath = new TldResourcePath(url, resourcePath, "META-INF/taglib.tld");
                } else {
                    tldResourcePath = new TldResourcePath(url, resourcePath);
                }
                // 解析TLD但使用描述符中提供的URI进行存储
                TaglibXml tld = tldParser.parse(tldResourcePath);
                uriTldResourcePathMap.put(taglibURI, tldResourcePath);
                tldResourcePathTaglibXmlMap.put(tldResourcePath, tld);
                if (tld.getListeners() != null) {
                    listeners.addAll(tld.getListeners());
                }
            } else {
                log.warn(Localizer.getMessage(MSG + ".webxmlFailPathDoesNotExist",
                        resourcePath,
                        taglibURI));
                continue;
            }
        }
    }

    /**
     * 以递归方式扫描TLD的Web应用程序资源.
     *
     * @param startPath 要扫描的目录资源
     * @throws IOException  如果扫描或加载TLD时出现问题
     * @throws SAXException 如果解析TLD时出现问题
     */
    protected void scanResourcePaths(String startPath)
            throws IOException, SAXException {

        boolean found = false;
        Set<String> dirList = context.getResourcePaths(startPath);
        if (dirList != null) {
            for (String path : dirList) {
                if (path.startsWith("/WEB-INF/classes/")) {
                    // Skip: JSP.7.3.1
                } else if (path.startsWith("/WEB-INF/lib/")) {
                    // Skip: JSP.7.3.1
                } else if (path.endsWith("/")) {
                    scanResourcePaths(path);
                } else if (path.startsWith("/WEB-INF/tags/")) {
                    // JSP 7.3.1: in /WEB-INF/tags only consider implicit.tld
                    if (path.endsWith("/implicit.tld")) {
                        found = true;
                        parseTld(path);
                    }
                } else if (path.endsWith(TLD_EXT)) {
                    found = true;
                    parseTld(path);
                }
            }
        }
        if (found) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.tldCache.tldInResourcePath", startPath));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.tldCache.noTldInResourcePath", startPath));
            }
        }
    }

    /**
     * 扫描 /WEB-INF/lib 中JAR 文件中的 TLD.
     */
    public void scanJars() {
        JarScanner scanner = JarScannerFactory.getJarScanner(context);
        TldScannerCallback callback = new TldScannerCallback();
        scanner.scan(JarScanType.TLD, context, callback);
        if (callback.scanFoundNoTLDs()) {
            log.info(Localizer.getMessage("jsp.tldCache.noTldSummary"));
        }
    }

    protected void parseTld(String resourcePath) throws IOException, SAXException {
        TldResourcePath tldResourcePath =
                new TldResourcePath(context.getResource(resourcePath), resourcePath);
        parseTld(tldResourcePath);
    }

    protected void parseTld(TldResourcePath path) throws IOException, SAXException {
        if (tldResourcePathTaglibXmlMap.containsKey(path)) {
            // 由于处理了web.xml，TLD已经被解析
            return;
        }
        TaglibXml tld = tldParser.parse(path);
        String uri = tld.getUri();
        if (uri != null) {
            if (!uriTldResourcePathMap.containsKey(uri)) {
                uriTldResourcePathMap.put(uri, path);
            }
        }
        tldResourcePathTaglibXmlMap.put(path, tld);
        if (tld.getListeners() != null) {
            listeners.addAll(tld.getListeners());
        }
    }

    class TldScannerCallback implements JarScannerCallback {
        private boolean foundJarWithoutTld = false;
        private boolean foundFileWithoutTld = false;


        @Override
        public void scan(Jar jar, String webappPath, boolean isWebapp) throws IOException {
            boolean found = false;
            URL jarFileUrl = jar.getJarFileURL();
            jar.nextEntry();
            for (String entryName = jar.getEntryName();
                entryName != null;
                jar.nextEntry(), entryName = jar.getEntryName()) {
                if (!(entryName.startsWith("META-INF/") &&
                        entryName.endsWith(TLD_EXT))) {
                    continue;
                }
                found = true;
                TldResourcePath tldResourcePath =
                        new TldResourcePath(jarFileUrl, webappPath, entryName);
                try {
                    parseTld(tldResourcePath);
                } catch (SAXException e) {
                    throw new IOException(e);
                }
            }
            if (found) {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.tldCache.tldInJar", jarFileUrl.toString()));
                }
            } else {
                foundJarWithoutTld = true;
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage(
                            "jsp.tldCache.noTldInJar", jarFileUrl.toString()));
                }
            }
        }

        @Override
        public void scan(File file, final String webappPath, boolean isWebapp)
                throws IOException {
            File metaInf = new File(file, "META-INF");
            if (!metaInf.isDirectory()) {
                return;
            }
            foundFileWithoutTld = false;
            final Path filePath = file.toPath();
            Files.walkFileTree(metaInf.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs)
                        throws IOException {
                    Path fileName = file.getFileName();
                    if (fileName == null || !fileName.toString().toLowerCase(
                            Locale.ENGLISH).endsWith(TLD_EXT)) {
                        return FileVisitResult.CONTINUE;
                    }

                    foundFileWithoutTld = true;
                    String resourcePath;
                    if (webappPath == null) {
                        resourcePath = null;
                    } else {
                        String subPath = file.subpath(
                                filePath.getNameCount(), file.getNameCount()).toString();
                        if ('/' != File.separatorChar) {
                            subPath = subPath.replace(File.separatorChar, '/');
                        }
                        resourcePath = webappPath + "/" + subPath;
                    }

                    try {
                        URL url = file.toUri().toURL();
                        TldResourcePath path = new TldResourcePath(url, resourcePath);
                        parseTld(path);
                    } catch (SAXException e) {
                        throw new IOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (foundFileWithoutTld) {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.tldCache.tldInDir",
                            file.getAbsolutePath()));
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(Localizer.getMessage("jsp.tldCache.noTldInDir",
                            file.getAbsolutePath()));
                }
            }
        }

        @Override
        public void scanWebInfClasses() throws IOException {
            // 当启用scanAllDirectories并且一些或多个JAR已经解压缩到 WEB-INF/classes 中时使用，就像某些IDE一样.

            Set<String> paths = context.getResourcePaths(WEB_INF + "classes/META-INF");
            if (paths == null) {
                return;
            }

            for (String path : paths) {
                if (path.endsWith(TLD_EXT)) {
                    try {
                        parseTld(path);
                    } catch (SAXException e) {
                        throw new IOException(e);
                    }
                }
            }
        }


        boolean scanFoundNoTLDs() {
            return foundJarWithoutTld;
        }
    }
}
