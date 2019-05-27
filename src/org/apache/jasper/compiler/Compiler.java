package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.scan.JarFactory;

/**
 * 主要的JSP 编译器类. 这个类使用 Ant 编译.
 */
public abstract class Compiler {

    private final Log log = LogFactory.getLog(Compiler.class); // must not be static

    // ----------------------------------------------------- Instance Variables

    protected JspCompilationContext ctxt;

    protected ErrorDispatcher errDispatcher;

    protected PageInfo pageInfo;

    protected JspServletWrapper jsw;

    protected TagFileProcessor tfp;

    protected Options options;

    protected Node.Nodes pageNodes;


    // ------------------------------------------------------------ Constructor

    public void init(JspCompilationContext ctxt, JspServletWrapper jsw) {
        this.jsw = jsw;
        this.ctxt = ctxt;
        this.options = ctxt.getOptions();
    }

    // --------------------------------------------------------- Public Methods

    /**
     * <p>
     * 检索JSP页面的解析节点, 如果它们有效. 可能返回 null. 用于在开发模式中生成详细错误消息.
     * </p>
     * @return the page nodes
     */
    public Node.Nodes getPageNodes() {
        return this.pageNodes;
    }


    /**
     * 编译JSP文件转换成等效的servlet, 在 .java 文件中
     * @return 当前JSP页面的smap, 如果其中一个被生成,否则返回null
     * @throws Exception Error generating Java source
     */
    protected String[] generateJava() throws Exception {

        String[] smapStr = null;

        long t1, t2, t3, t4;

        t1 = t2 = t3 = t4 = 0;

        if (log.isDebugEnabled()) {
            t1 = System.currentTimeMillis();
        }

        // 设置页面信息区域
        pageInfo = new PageInfo(new BeanRepository(ctxt.getClassLoader(),
                errDispatcher), ctxt.getJspFile(), ctxt.isTagFile());

        JspConfig jspConfig = options.getJspConfig();
        JspConfig.JspProperty jspProperty = jspConfig.findJspProperty(ctxt
                .getJspFile());

        /*
         * 如果当前URI与web.xml中的jsp-property-group指定的模式匹配, 使用这些属性初始化pageInfo.
         */
        if (jspProperty.isELIgnored() != null) {
            pageInfo.setELIgnored(JspUtil.booleanValue(jspProperty
                    .isELIgnored()));
        }
        if (jspProperty.isScriptingInvalid() != null) {
            pageInfo.setScriptingInvalid(JspUtil.booleanValue(jspProperty
                    .isScriptingInvalid()));
        }
        if (jspProperty.getIncludePrelude() != null) {
            pageInfo.setIncludePrelude(jspProperty.getIncludePrelude());
        }
        if (jspProperty.getIncludeCoda() != null) {
            pageInfo.setIncludeCoda(jspProperty.getIncludeCoda());
        }
        if (jspProperty.isDeferedSyntaxAllowedAsLiteral() != null) {
            pageInfo.setDeferredSyntaxAllowedAsLiteral(JspUtil.booleanValue(jspProperty
                    .isDeferedSyntaxAllowedAsLiteral()));
        }
        if (jspProperty.isTrimDirectiveWhitespaces() != null) {
            pageInfo.setTrimDirectiveWhitespaces(JspUtil.booleanValue(jspProperty
                    .isTrimDirectiveWhitespaces()));
        }
        // 默认 ContentType处理被推迟，直到页面被解析
        if (jspProperty.getBuffer() != null) {
            pageInfo.setBufferValue(jspProperty.getBuffer(), null,
                    errDispatcher);
        }
        if (jspProperty.isErrorOnUndeclaredNamespace() != null) {
            pageInfo.setErrorOnUndeclaredNamespace(
                    JspUtil.booleanValue(
                            jspProperty.isErrorOnUndeclaredNamespace()));
        }
        if (ctxt.isTagFile()) {
            try {
                double libraryVersion = Double.parseDouble(ctxt.getTagInfo()
                        .getTagLibrary().getRequiredVersion());
                if (libraryVersion < 2.0) {
                    pageInfo.setIsELIgnored("true", null, errDispatcher, true);
                }
                if (libraryVersion < 2.1) {
                    pageInfo.setDeferredSyntaxAllowedAsLiteral("true", null,
                            errDispatcher, true);
                }
            } catch (NumberFormatException ex) {
                errDispatcher.jspError(ex);
            }
        }

        ctxt.checkOutputDir();
        String javaFileName = ctxt.getServletJavaFileName();

        try {
            /*
             * isELIgnored的设置以微妙的方式修改解析器的行为. 添加到'fun', isELIgnored 可以在翻译单元的表单部分的任何文件中设置,
             * 因此，将其设置在翻译单元末尾所包含的文件中，可以改变解析器的行为方式, 当将内容解析到设置了isELIgnored的地方.
             * Arghh! 以前试图绕过这个问题只是提供了部分解决方案. 现在使用两个方式来解析翻译单元.
             * 第一个只是解析指令，第二个解析整个翻译单元，一旦我们知道isELIgnored已经被设置.
             * TODO 这个过程有一些可能的优化.
             */
            // 解析文件
            ParserController parserCtl = new ParserController(ctxt, this);

            // Pass 1 - 指令
            Node.Nodes directives =
                parserCtl.parseDirectives(ctxt.getJspFile());
            Validator.validateDirectives(this, directives);

            // Pass 2 - 整个翻译单元
            pageNodes = parserCtl.parse(ctxt.getJspFile());

            // 离开这个直到现在，因为它只能设置一次 - bug 49726
            if (pageInfo.getContentType() == null &&
                    jspProperty.getDefaultContentType() != null) {
                pageInfo.setContentType(jspProperty.getDefaultContentType());
            }

            if (ctxt.isPrototypeMode()) {
                // 为标签文件生成原型 .java 文件
                try (ServletWriter writer = setupContextWriter(javaFileName)) {
                    Generator.generate(writer, this, pageNodes);
                    return null;
                }
            }

            // 验证和处理属性 - 不要重新验证已经在pass 1中已经验证的指令
            Validator.validateExDirectives(this, pageNodes);

            if (log.isDebugEnabled()) {
                t2 = System.currentTimeMillis();
            }

            // 收集页面信息
            Collector.collect(this, pageNodes);

            // 编译并加载在这个编译单元中引用的标记文件.
            tfp = new TagFileProcessor();
            tfp.loadTagFiles(this, pageNodes);

            if (log.isDebugEnabled()) {
                t3 = System.currentTimeMillis();
            }

            // 确定哪些自定义标签需要声明哪些脚本变量
            ScriptingVariabler.set(pageNodes, errDispatcher);

            // 标签插件优化
            TagPluginManager tagPluginManager = options.getTagPluginManager();
            tagPluginManager.apply(pageNodes, errDispatcher, pageInfo);

            // 优化: 将相邻模板文本联系起来.
            TextOptimizer.concatenate(this, pageNodes);

            // 生成静态函数映射代码.
            ELFunctionMapper.map(pageNodes);

            // 生成servlet .java文件
            try (ServletWriter writer = setupContextWriter(javaFileName)) {
                Generator.generate(writer, this, pageNodes);
            }

            // writer仅在编译期间使用, 在 JspCompilationContext中间接引用, 完成时，允许它进行GC并保存内存.
            ctxt.setWriter(null);

            if (log.isDebugEnabled()) {
                t4 = System.currentTimeMillis();
                log.debug("Generated " + javaFileName + " total=" + (t4 - t1)
                        + " generate=" + (t4 - t3) + " validate=" + (t2 - t1));
            }

        } catch (Exception e) {
            // 删除生成的 .java 文件
            File file = new File(javaFileName);
            if (file.exists()) {
                if (!file.delete()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.compiler.javafile.delete.fail",
                            file.getAbsolutePath()));
                }
            }
            throw e;
        }

        // JSR45 Support
        if (!options.isSmapSuppressed()) {
            smapStr = SmapUtil.generateSmap(ctxt, pageNodes);
        }

        // 如果生成了任何 .java 和 .class 文件, 原始的 .java 可能已被当前编译所取代 (如果标签文件是自身引用的),
        // 但是.class 文件需要被删除, 为了确保javac从新的.java文件生成 .class.
        tfp.removeProtoTypeFiles(ctxt.getClassFileName());

        return smapStr;
    }

    private ServletWriter setupContextWriter(String javaFileName)
            throws FileNotFoundException, JasperException {
        ServletWriter writer;
        // Setup the ServletWriter
        String javaEncoding = ctxt.getOptions().getJavaEncoding();
        OutputStreamWriter osw = null;

        try {
            osw = new OutputStreamWriter(
                    new FileOutputStream(javaFileName), javaEncoding);
        } catch (UnsupportedEncodingException ex) {
            errDispatcher.jspError("jsp.error.needAlternateJavaEncoding",
                    javaEncoding);
        }

        writer = new ServletWriter(new PrintWriter(osw));
        ctxt.setWriter(writer);
        return writer;
    }

    /**
     * Servlet编译. 将生成的源编译成Servlet.
     * 
     * @param smap 用于源调试的SMAP文件
     * 
     * @throws FileNotFoundException 找不到源文件
     * @throws JasperException 编译错误
     * @throws Exception 其它错误
     */
    protected abstract void generateClass(String[] smap)
            throws FileNotFoundException, JasperException, Exception;

    /**
     * 从当前引擎上下文编译JSP文件.
     * 
     * @throws FileNotFoundException 找不到源文件
     * @throws JasperException 编译错误
     * @throws Exception 其它错误
     */
    public void compile() throws FileNotFoundException, JasperException,
            Exception {
        compile(true);
    }

    /**
     * 从当前引擎上下文编译JSP文件. 作为副作用, 此页引用的标签文件也被编译.
     *
     * @param compileClass 如果是true, 生成 .java 和 .class 文件
     *                     如果是false, 只生成 .java 文件
     *                     
     * @throws FileNotFoundException 找不到源文件
     * @throws JasperException 编译错误
     * @throws Exception 其它错误
     */
    public void compile(boolean compileClass) throws FileNotFoundException,
            JasperException, Exception {
        compile(compileClass, false);
    }

    /**
     * 从当前引擎上下文编译JSP文件. 作为副作用, 此页引用的标签文件也被编译.
     *
     * @param compileClass 如果是true, 生成 .java 和 .class 文件
     *                     如果是false, 只生成 .java 文件
     * @param jspcMode true 如果调用来自JspC, 否则返回false
     * 
     * @throws FileNotFoundException 找不到源文件
     * @throws JasperException 编译错误
     * @throws Exception 其它错误
     */
    public void compile(boolean compileClass, boolean jspcMode)
            throws FileNotFoundException, JasperException, Exception {
        if (errDispatcher == null) {
            this.errDispatcher = new ErrorDispatcher(jspcMode);
        }

        try {
            String[] smap = generateJava();
            File javaFile = new File(ctxt.getServletJavaFileName());
            Long jspLastModified = ctxt.getLastModified(ctxt.getJspFile());
            javaFile.setLastModified(jspLastModified.longValue());
            if (compileClass) {
                generateClass(smap);
                // Fix for bugzilla 41606
                // 成功编译之后，设置 JspServletWrapper.servletClassLastModifiedTime
                File targetFile = new File(ctxt.getClassFileName());
                if (targetFile.exists()) {
                    targetFile.setLastModified(jspLastModified.longValue());
                    if (jsw != null) {
                        jsw.setServletClassLastModifiedTime(
                                jspLastModified.longValue());
                    }
                }
            }
        } finally {
            if (tfp != null && ctxt.isPrototypeMode()) {
                tfp.removeProtoTypeFiles(null);
            }
            // 确定这些对象只用于JSP页面的生成和编译过程中被引用, 这样就可以进行GC并减少内存占用.
            tfp = null;
            errDispatcher = null;
            pageInfo = null;

            // 只有在生产中剔除pageNodes.
            // 在开发模式中, 它们用于详细的错误消息.
            // http://bz.apache.org/bugzilla/show_bug.cgi?id=37062
            if (!this.options.getDevelopment()) {
                pageNodes = null;
            }

            if (ctxt.getWriter() != null) {
                ctxt.getWriter().close();
                ctxt.setWriter(null);
            }
        }
    }

    /**
     * 由编译器的子类重写. 编译方法使用它来完成所有编译.
     * 
     * @return <code>true</code>如果源生成和编译应该发生
     */
    public boolean isOutDated() {
        return isOutDated(true);
    }

    /**
     * 确定通过检查JSP页面和对应的.class 或 .java 文件的时间戳来进行编译是必要的.
     * 如果页面有依赖关系, 检查也扩展到其依赖, 等等. 这个方法可以通过子类重写编译器.
     *
     * @param checkClass 如果是true, 检查 .class 文件,
     *                   如果是false, 检查 .java 文件.
     *                   
     * @return <code>true</code>如果源生成和编译应该发生
     */
    public boolean isOutDated(boolean checkClass) {

        if (jsw != null
                && (ctxt.getOptions().getModificationTestInterval() > 0)) {

            if (jsw.getLastModificationTest()
                    + (ctxt.getOptions().getModificationTestInterval() * 1000) > System
                    .currentTimeMillis()) {
                return false;
            }
            jsw.setLastModificationTest(System.currentTimeMillis());
        }

        // 先测试目标文件. 除非有错误检查源的最后修改时间（不太可能），否则将不得不检查目标. 如果目标不存在（可能在启动期间），这节省了源的不必要检查.
        File targetFile;
        if (checkClass) {
            targetFile = new File(ctxt.getClassFileName());
        } else {
            targetFile = new File(ctxt.getServletJavaFileName());
        }
        if (!targetFile.exists()) {
            return true;
        }
        long targetLastModified = targetFile.lastModified();
        if (checkClass && jsw != null) {
            jsw.setServletClassLastModifiedTime(targetLastModified);
        }

        Long jspRealLastModified = ctxt.getLastModified(ctxt.getJspFile());
        if (jspRealLastModified.longValue() < 0) {
            // Something went wrong - 假设修改
            return true;
        }

        if (targetLastModified != jspRealLastModified.longValue()) {
            if (log.isDebugEnabled()) {
                log.debug("Compiler: outdated: " + targetFile + " "
                        + targetLastModified);
            }
            return true;
        }

        // 确定源依赖文件(包括使用 include 指令)是否已修改.
        if (jsw == null) {
            return false;
        }

        Map<String,Long> depends = jsw.getDependants();
        if (depends == null) {
            return false;
        }

        Iterator<Entry<String,Long>> it = depends.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String,Long> include = it.next();
            try {
                String key = include.getKey();
                URL includeUrl;
                long includeLastModified = 0;
                if (key.startsWith("jar:jar:")) {
                    // Assume we constructed this correctly
                    int entryStart = key.lastIndexOf("!/");
                    String entry = key.substring(entryStart + 2);
                    try (Jar jar = JarFactory.newInstance(new URL(key.substring(4, entryStart)))) {
                        includeLastModified = jar.getLastModified(entry);
                    }
                } else {
                    if (key.startsWith("jar:") || key.startsWith("file:")) {
                        includeUrl = new URL(key);
                    } else {
                        includeUrl = ctxt.getResource(include.getKey());
                    }
                    if (includeUrl == null) {
                        return true;
                    }
                    URLConnection iuc = includeUrl.openConnection();
                    if (iuc instanceof JarURLConnection) {
                        includeLastModified =
                            ((JarURLConnection) iuc).getJarEntry().getTime();
                    } else {
                        includeLastModified = iuc.getLastModified();
                    }
                    iuc.getInputStream().close();
                }

                if (includeLastModified != include.getValue().longValue()) {
                    return true;
                }
            } catch (Exception e) {
                if (log.isDebugEnabled())
                    log.debug("Problem accessing resource. Treat as outdated.",
                            e);
                return true;
            }
        }

        return false;

    }

    public ErrorDispatcher getErrorDispatcher() {
        return errDispatcher;
    }

    /**
     * @return 编译中的页面信息
     */
    public PageInfo getPageInfo() {
        return pageInfo;
    }

    public JspCompilationContext getCompilationContext() {
        return ctxt;
    }

    /**
     * 删除生成的文件
     */
    public void removeGeneratedFiles() {
        removeGeneratedClassFiles();

        try {
            File javaFile = new File(ctxt.getServletJavaFileName());
            if (log.isDebugEnabled())
                log.debug("Deleting " + javaFile);
            if (javaFile.exists()) {
                if (!javaFile.delete()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.compiler.javafile.delete.fail",
                            javaFile.getAbsolutePath()));
                }
            }
        } catch (Exception e) {
            // Remove as much as possible, log possible exceptions
            log.warn(Localizer.getMessage("jsp.warning.compiler.classfile.delete.fail.unknown"),
                     e);
        }
    }

    public void removeGeneratedClassFiles() {
        try {
            File classFile = new File(ctxt.getClassFileName());
            if (log.isDebugEnabled())
                log.debug("Deleting " + classFile);
            if (classFile.exists()) {
                if (!classFile.delete()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.compiler.classfile.delete.fail",
                            classFile.getAbsolutePath()));
                }
            }
        } catch (Exception e) {
            // Remove as much as possible, log possible exceptions
            log.warn(Localizer.getMessage("jsp.warning.compiler.classfile.delete.fail.unknown"),
                     e);
        }
    }
}
