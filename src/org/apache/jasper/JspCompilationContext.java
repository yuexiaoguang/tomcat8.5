package org.apache.jasper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Set;
import java.util.jar.JarEntry;

import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.TagInfo;

import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.JspUtil;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.ServletWriter;
import org.apache.jasper.servlet.JasperLoader;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;

/**
 * 用于通过JSP引擎使用的各种事物的占位符. 这是每个请求/每个上下文数据结构. 一些实例变量设置在不同的点上.
 *
 * 大部分与路径相关的东西都在这里 - 名称, 版本, 目录,加载的资源, 处理的URI. 
 */
public class JspCompilationContext {

    private final Log log = LogFactory.getLog(JspCompilationContext.class); // must not be static

    private String className;
    private final String jspUri;
    private String basePackageName;
    private String derivedPackageName;
    private String servletJavaFileName;
    private String javaPath;
    private String classFileName;
    private ServletWriter writer;
    private final Options options;
    private final JspServletWrapper jsw;
    private Compiler jspCompiler;
    private String classPath;

    private final String baseURI;
    private String outputDir;
    private final ServletContext context;
    private ClassLoader loader;

    private final JspRuntimeContext rctxt;

    private volatile boolean removed = false;

    private URLClassLoader jspLoader;
    private URL baseUrl;
    private Class<?> servletClass;

    private final boolean isTagFile;
    private boolean protoTypeMode;
    private TagInfo tagInfo;
    private Jar tagJar;

    // jspURI 必须相对于上下文
    public JspCompilationContext(String jspUri, Options options,
            ServletContext context, JspServletWrapper jsw,
            JspRuntimeContext rctxt) {
        this(jspUri, null, options, context, jsw, rctxt, null, false);
    }

    public JspCompilationContext(String tagfile, TagInfo tagInfo,
            Options options, ServletContext context, JspServletWrapper jsw,
            JspRuntimeContext rctxt, Jar tagJar) {
        this(tagfile, tagInfo, options, context, jsw, rctxt, tagJar, true);
    }

    private JspCompilationContext(String jspUri, TagInfo tagInfo,
            Options options, ServletContext context, JspServletWrapper jsw,
            JspRuntimeContext rctxt, Jar tagJar, boolean isTagFile) {

        this.jspUri = canonicalURI(jspUri);
        this.options = options;
        this.jsw = jsw;
        this.context = context;

        String baseURI = jspUri.substring(0, jspUri.lastIndexOf('/') + 1);
        // hack fix for resolveRelativeURI
        if (baseURI.isEmpty()) {
            baseURI = "/";
        } else if (baseURI.charAt(0) != '/') {
            // 删除斜线, 因为它将与 uriBase 生成一个文件
            baseURI = "/" + baseURI;
        }
        if (baseURI.charAt(baseURI.length() - 1) != '/') {
            baseURI += '/';
        }
        this.baseURI = baseURI;

        this.rctxt = rctxt;
        this.basePackageName = Constants.JSP_PACKAGE_NAME;

        this.tagInfo = tagInfo;
        this.tagJar = tagJar;
        this.isTagFile = isTagFile;
    }


    /* ==================== Methods to override ==================== */

    /** ---------- Class path and loader ---------- */

    /**
     * @return Java编译器的classpath. 
     */
    public String getClassPath() {
        if( classPath != null ) {
            return classPath;
        }
        return rctxt.getClassPath();
    }

    /**
     * Java编译器的classpath.
     * 
     * @param classPath 要使用的类路径
     */
    public void setClassPath(String classPath) {
        this.classPath = classPath;
    }

    /**
     * 在编译JSP时使用什么类装入器加载类?
     * 
     * @return 用于加载所有编译类的类加载器
     */
    public ClassLoader getClassLoader() {
        if( loader != null ) {
            return loader;
        }
        return rctxt.getParentClassLoader();
    }

    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }

    public ClassLoader getJspLoader() {
        if( jspLoader == null ) {
            jspLoader = new JasperLoader
                    (new URL[] {baseUrl},
                            getClassLoader(),
                            rctxt.getPermissionCollection());
        }
        return jspLoader;
    }

    public void clearJspLoader() {
        jspLoader = null;
    }


    /** ---------- Input/Output  ---------- */

    /**
     * 生成代码的输出目录. 输出目录由在选项中提供的暂存目录组成, 加上包名派生的目录.
     */
    public String getOutputDir() {
        if (outputDir == null) {
            createOutputDir();
        }

        return outputDir;
    }

    /**
     * 创建一个"Compiler"对象, 基于一些初始化参数数据. 这还没有完成. 现在我们只是硬编码创建的实际的编译器. 
     */
    public Compiler createCompiler() {
        if (jspCompiler != null ) {
            return jspCompiler;
        }
        jspCompiler = null;
        if (options.getCompilerClassName() != null) {
            jspCompiler = createCompiler(options.getCompilerClassName());
        } else {
            if (options.getCompiler() == null) {
                jspCompiler = createCompiler("org.apache.jasper.compiler.JDTCompiler");
                if (jspCompiler == null) {
                    jspCompiler = createCompiler("org.apache.jasper.compiler.AntCompiler");
                }
            } else {
                jspCompiler = createCompiler("org.apache.jasper.compiler.AntCompiler");
                if (jspCompiler == null) {
                    jspCompiler = createCompiler("org.apache.jasper.compiler.JDTCompiler");
                }
            }
        }
        if (jspCompiler == null) {
            throw new IllegalStateException(Localizer.getMessage("jsp.error.compiler.config",
                    options.getCompilerClassName(), options.getCompiler()));
        }
        jspCompiler.init(this, jsw);
        return jspCompiler;
    }

    protected Compiler createCompiler(String className) {
        Compiler compiler = null;
        try {
            compiler = (Compiler) Class.forName(className).getConstructor().newInstance();
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.error.compiler"), e);
            }
        } catch (ReflectiveOperationException e) {
            log.warn(Localizer.getMessage("jsp.error.compiler"), e);
        }
        return compiler;
    }

    public Compiler getCompiler() {
        return jspCompiler;
    }

    /** ---------- Access resources in the webapp ---------- */

    /**
     * 使用当前文件为基础得到一个URI相对于这个编译上下文的全部值.
     * 
     * @param uri The relative URI
     * @return absolute URI
     */
    public String resolveRelativeUri(String uri) {
        // 有时从文件中得到URI的信息, 所以检查根目录deperator字符
        if (uri.startsWith("/") || uri.startsWith(File.separator)) {
            return uri;
        } else {
            return baseURI + uri;
        }
    }

    /**
     * 将资源作为流获取, 相对于上下文实现的含义.
     * 
     * @param res 寻找的资源
     * @return null 如果无法找到资源.
     */
    public java.io.InputStream getResourceAsStream(String res) {
        return context.getResourceAsStream(canonicalURI(res));
    }


    public URL getResource(String res) throws MalformedURLException {
        return context.getResource(canonicalURI(res));
    }


    public Set<String> getResourcePaths(String path) {
        return context.getResourcePaths(canonicalURI(path));
    }

    /**
     * 获取与编译上下文相关的URI的实际路径.
     * 
     * @param path The webapp path
     * @return 文件系统中的对应路径
     */
    public String getRealPath(String path) {
        if (context != null) {
            return context.getRealPath(path);
        }
        return path;
    }

    /**
     * 返回JAR文件，其中创建该JspCompilationContext的标记文件被打包, 或null 如果这个JspCompilationContext 不对应于标记文件,
     * 或者如果相应的标签文件没有被打包进一个JAR中.
     * @return a JAR file
     */
    public Jar getTagFileJar() {
        return this.tagJar;
    }

    public void setTagFileJar(Jar tagJar) {
        this.tagJar = tagJar;
    }

    /* ==================== Common implementation ==================== */

    /**
     * 生成类的类名（不包括包名）.
     * @return the class name
     */
    public String getServletClassName() {

        if (className != null) {
            return className;
        }

        if (isTagFile) {
            className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            if (lastIndex != -1) {
                className = className.substring(lastIndex + 1);
            }
        } else {
            int iSep = jspUri.lastIndexOf('/') + 1;
            className = JspUtil.makeJavaIdentifier(jspUri.substring(iSep));
        }
        return className;
    }

    public void setServletClassName(String className) {
        this.className = className;
    }

    /**
     * JSP URI的路径. 注意，这不是一个文件名. 这是JSP文件的基于上下文的URI. 
     */
    public String getJspFile() {
        return jspUri;
    }


    public Long getLastModified(String resource) {
        return getLastModified(resource, tagJar);
    }


    public Long getLastModified(String resource, Jar tagJar) {
        long result = -1;
        URLConnection uc = null;
        try {
            if (tagJar != null) {
                if (resource.startsWith("/")) {
                    resource = resource.substring(1);
                }
                result = tagJar.getLastModified(resource);
            } else {
                URL jspUrl = getResource(resource);
                if (jspUrl == null) {
                    incrementRemoved();
                    return Long.valueOf(result);
                }
                uc = jspUrl.openConnection();
                if (uc instanceof JarURLConnection) {
                    JarEntry jarEntry = ((JarURLConnection) uc).getJarEntry();
                    if (jarEntry != null) {
                        result = jarEntry.getTime();
                    } else {
                        result = uc.getLastModified();
                    }
                } else {
                    result = uc.getLastModified();
                }
            }
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage(
                        "jsp.error.lastModified", getJspFile()), e);
            }
            result = -1;
        } finally {
            if (uc != null) {
                try {
                    uc.getInputStream().close();
                } catch (IOException e) {
                    if (log.isDebugEnabled()) {
                        log.debug(Localizer.getMessage(
                                "jsp.error.lastModified", getJspFile()), e);
                    }
                    result = -1;
                }
            }
        }
        return Long.valueOf(result);
    }

    public boolean isTagFile() {
        return isTagFile;
    }

    public TagInfo getTagInfo() {
        return tagInfo;
    }

    public void setTagInfo(TagInfo tagi) {
        tagInfo = tagi;
    }

    /**
     * @return <code>true</code>如果在原型模式下编译一个标签文件.
     * IE只生成带有空方法体的标签处理程序的类代码.
     */
    public boolean isPrototypeMode() {
        return protoTypeMode;
    }

    public void setPrototypeMode(boolean pm) {
        protoTypeMode = pm;
    }

    /**
     * 生成的类的包名由基本包名组成, 用户可设置, 以及派生包名. 导出的包名直接反映了JSP页面文件的文件结构.
     * @return the package name
     */
    public String getServletPackageName() {
        if (isTagFile()) {
            String className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            String pkgName = "";
            if (lastIndex != -1) {
                pkgName = className.substring(0, lastIndex);
            }
            return pkgName;
        } else {
            String dPackageName = getDerivedPackageName();
            if (dPackageName.length() == 0) {
                return basePackageName;
            }
            return basePackageName + '.' + getDerivedPackageName();
        }
    }

    protected String getDerivedPackageName() {
        if (derivedPackageName == null) {
            int iSep = jspUri.lastIndexOf('/');
            derivedPackageName = (iSep > 0) ?
                    JspUtil.makeJavaPackage(jspUri.substring(1,iSep)) : "";
        }
        return derivedPackageName;
    }

    /**
     * 生成servlet类的包名.
     * 
     * @param servletPackageName 要使用的包名
     */
    public void setServletPackageName(String servletPackageName) {
        this.basePackageName = servletPackageName;
    }

    /**
     * @return 由servlet生成的java文件的完整路径名.
     */
    public String getServletJavaFileName() {
        if (servletJavaFileName == null) {
            servletJavaFileName = getOutputDir() + getServletClassName() + ".java";
        }
        return servletJavaFileName;
    }

    /**
     * @return 此上下文的Options对象.
     */
    public Options getOptions() {
        return options;
    }

    public ServletContext getServletContext() {
        return context;
    }

    public JspRuntimeContext getRuntimeContext() {
        return rctxt;
    }

    /**
     * @return java文件相对于工作目录的路径.
     */
    public String getJavaPath() {

        if (javaPath != null) {
            return javaPath;
        }

        if (isTagFile()) {
            String tagName = tagInfo.getTagClassName();
            javaPath = tagName.replace('.', '/') + ".java";
        } else {
            javaPath = getServletPackageName().replace('.', '/') + '/' +
                    getServletClassName() + ".java";
        }
        return javaPath;
    }

    public String getClassFileName() {
        if (classFileName == null) {
            classFileName = getOutputDir() + getServletClassName() + ".class";
        }
        return classFileName;
    }

    /**
     * @return 用于写入生成的Servlet源的 writer.
     */
    public ServletWriter getWriter() {
        return writer;
    }

    public void setWriter(ServletWriter writer) {
        this.writer = writer;
    }

    /**
     * 获取给定标签库'uri'相关的TLD地 'location'.
     * 
     * @param uri The taglib URI
     * @return 两个字符串数组: 第一个元素表示TLD的真正路径. 如果指向TLD的路径指向JAR文件, 然后，第二个元素表示JAR文件中TLD条目的名称.
     * 返回 null， 如果给定的URI与Web应用程序中公开的任何标记库都不关联.
     */
    public TldResourcePath getTldResourcePath(String uri) {
        return getOptions().getTldCache().getTldResourcePath(uri);
    }

    /**
     * @return <code>true</code>保留生成的代码.
     */
    public boolean keepGenerated() {
        return getOptions().getKeepGenerated();
    }

    // ==================== Removal ====================

    public void incrementRemoved() {
        if (removed == false && rctxt != null) {
            rctxt.removeWrapper(jspUri);
        }
        removed = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    // ==================== Compile and reload ====================

    public void compile() throws JasperException, FileNotFoundException {
        createCompiler();
        if (jspCompiler.isOutDated()) {
            if (isRemoved()) {
                throw new FileNotFoundException(jspUri);
            }
            try {
                jspCompiler.removeGeneratedFiles();
                jspLoader = null;
                jspCompiler.compile();
                jsw.setReload(true);
                jsw.setCompilationException(null);
            } catch (JasperException ex) {
                // 缓存编译异常
                jsw.setCompilationException(ex);
                if (options.getDevelopment() && options.getRecompileOnFail()) {
                    // 强制重新编译下一个访问
                    jsw.setLastModificationTest(-1);
                }
                throw ex;
            } catch (FileNotFoundException fnfe) {
                // Re-throw to let caller handle this - will result in a 404
                throw fnfe;
            } catch (Exception ex) {
                JasperException je = new JasperException(
                        Localizer.getMessage("jsp.error.unable.compile"),
                        ex);
                // 缓存编译异常
                jsw.setCompilationException(je);
                throw je;
            }
        }
    }

    // ==================== Manipulating the class ====================

    public Class<?> load() throws JasperException {
        try {
            getJspLoader();

            String name = getFQCN();
            servletClass = jspLoader.loadClass(name);
        } catch (ClassNotFoundException cex) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.load"),
                    cex);
        } catch (Exception ex) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.compile"),
                    ex);
        }
        removed = false;
        return servletClass;
    }

    public String getFQCN() {
        String name;
        if (isTagFile()) {
            name = tagInfo.getTagClassName();
        } else {
            name = getServletPackageName() + "." + getServletClassName();
        }
        return name;
    }

    // ==================== protected methods ====================

    private static final Object outputDirLock = new Object();

    public void checkOutputDir() {
        if (outputDir != null) {
            if (!(new File(outputDir)).exists()) {
                makeOutputDir();
            }
        } else {
            createOutputDir();
        }
    }

    protected boolean makeOutputDir() {
        synchronized(outputDirLock) {
            File outDirFile = new File(outputDir);
            return (outDirFile.mkdirs() || outDirFile.isDirectory());
        }
    }

    protected void createOutputDir() {
        String path = null;
        if (isTagFile()) {
            String tagName = tagInfo.getTagClassName();
            path = tagName.replace('.', File.separatorChar);
            path = path.substring(0, path.lastIndexOf(File.separatorChar));
        } else {
            path = getServletPackageName().replace('.',File.separatorChar);
        }

        // 将servlet或标记处理程序路径追加到 scratch 目录
        try {
            File base = options.getScratchDir();
            baseUrl = base.toURI().toURL();
            outputDir = base.getAbsolutePath() + File.separator + path +
                    File.separator;
            if (!makeOutputDir()) {
                throw new IllegalStateException(Localizer.getMessage("jsp.error.outputfolder"));
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(Localizer.getMessage("jsp.error.outputfolder"), e);
        }
    }

    protected static final boolean isPathSeparator(char c) {
        return (c == '/' || c == '\\');
    }

    protected static final String canonicalURI(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        final int len = s.length();
        int pos = 0;
        while (pos < len) {
            char c = s.charAt(pos);
            if ( isPathSeparator(c) ) {
                /*
                 * multiple path separators.
                 * 'foo///bar' -> 'foo/bar'
                 */
                while (pos+1 < len && isPathSeparator(s.charAt(pos+1))) {
                    ++pos;
                }

                if (pos+1 < len && s.charAt(pos+1) == '.') {
                    /*
                     * 路径末端的一个点 - 已经完成.
                     */
                    if (pos+2 >= len) {
                        break;
                    }

                    switch (s.charAt(pos+2)) {
                        /*
                         * self directory in path
                         * foo/./bar -> foo/bar
                         */
                        case '/':
                        case '\\':
                            pos += 2;
                            continue;

                            /*
                             * 路径中的两个点: 返回上级目录.
                             * foo/bar/../baz -> foo/baz
                             */
                        case '.':
                            // only if we have exactly _two_ dots.
                            if (pos+3 < len && isPathSeparator(s.charAt(pos+3))) {
                                pos += 3;
                                int separatorPos = result.length()-1;
                                while (separatorPos >= 0 &&
                                        ! isPathSeparator(result
                                                .charAt(separatorPos))) {
                                    --separatorPos;
                                }
                                if (separatorPos >= 0) {
                                    result.setLength(separatorPos);
                                }
                                continue;
                        }
                    }
                }
            }
            result.append(c);
            ++pos;
        }
        return result.toString();
    }
}

