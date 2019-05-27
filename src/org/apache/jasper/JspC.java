package org.apache.jasper;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.jsp.JspFactory;
import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.jasper.servlet.JspCServletContext;
import org.apache.jasper.servlet.TldScanner;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.FileUtils;
import org.xml.sax.SAXException;

/**
 * jspc 编译器Shell脚本. 处理与命令行相关联的所有选项，并创建编译上下文，然后根据指定的选项编译.
 *
 * 这个版本可以马上处理从 a _single_ webapp获取的文件, 即可以指定单个docbase.
 *
 * 它可以用作Ant任务:
 * <pre>
 *   &lt;taskdef classname="org.apache.jasper.JspC" name="jasper" &gt;
 *      &lt;classpath&gt;
 *          &lt;pathelement location="${java.home}/../lib/tools.jar"/&gt;
 *          &lt;fileset dir="${ENV.CATALINA_HOME}/lib"&gt;
 *              &lt;include name="*.jar"/&gt;
 *          &lt;/fileset&gt;
 *          &lt;path refid="myjars"/&gt;
 *       &lt;/classpath&gt;
 *  &lt;/taskdef&gt;
 *
 *  &lt;jasper verbose="0"
 *           package="my.package"
 *           uriroot="${webapps.dir}/${webapp.name}"
 *           webXmlFragment="${build.dir}/generated_web.xml"
 *           outputDir="${webapp.dir}/${webapp.name}/WEB-INF/src/my/package" /&gt;
 * </pre>
 */
public class JspC extends Task implements Options {

    static {
        // 用于访问EL ExpressionFactory的 Validator
        JspFactory.setDefaultFactory(new JspFactoryImpl());
    }

    public static final String DEFAULT_IE_CLASS_ID =
            "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";

    // Logger
    private static final Log log = LogFactory.getLog(JspC.class);

    protected static final String SWITCH_VERBOSE = "-v";
    protected static final String SWITCH_HELP = "-help";
    protected static final String SWITCH_OUTPUT_DIR = "-d";
    protected static final String SWITCH_PACKAGE_NAME = "-p";
    protected static final String SWITCH_CACHE = "-cache";
    protected static final String SWITCH_CLASS_NAME = "-c";
    protected static final String SWITCH_FULL_STOP = "--";
    protected static final String SWITCH_COMPILE = "-compile";
    protected static final String SWITCH_SOURCE = "-source";
    protected static final String SWITCH_TARGET = "-target";
    protected static final String SWITCH_URI_BASE = "-uribase";
    protected static final String SWITCH_URI_ROOT = "-uriroot";
    protected static final String SWITCH_FILE_WEBAPP = "-webapp";
    protected static final String SWITCH_WEBAPP_INC = "-webinc";
    protected static final String SWITCH_WEBAPP_XML = "-webxml";
    protected static final String SWITCH_WEBAPP_XML_ENCODING = "-webxmlencoding";
    protected static final String SWITCH_ADD_WEBAPP_XML_MAPPINGS = "-addwebxmlmappings";
    protected static final String SWITCH_MAPPED = "-mapped";
    protected static final String SWITCH_XPOWERED_BY = "-xpoweredBy";
    protected static final String SWITCH_TRIM_SPACES = "-trimSpaces";
    protected static final String SWITCH_CLASSPATH = "-classpath";
    protected static final String SWITCH_DIE = "-die";
    protected static final String SWITCH_POOLING = "-poolingEnabled";
    protected static final String SWITCH_ENCODING = "-javaEncoding";
    protected static final String SWITCH_SMAP = "-smap";
    protected static final String SWITCH_DUMP_SMAP = "-dumpsmap";
    protected static final String SWITCH_VALIDATE_TLD = "-validateTld";
    protected static final String SWITCH_VALIDATE_XML = "-validateXml";
    protected static final String SWITCH_NO_BLOCK_EXTERNAL = "-no-blockExternal";
    protected static final String SWITCH_NO_STRICT_QUOTE_ESCAPING = "-no-strictQuoteEscaping";
    protected static final String SWITCH_QUOTE_ATTRIBUTE_EL = "-quoteAttributeEL";
    protected static final String SWITCH_NO_QUOTE_ATTRIBUTE_EL = "-no-quoteAttributeEL";
    protected static final String SHOW_SUCCESS ="-s";
    protected static final String LIST_ERRORS = "-l";
    protected static final int INC_WEBXML = 10;
    protected static final int ALL_WEBXML = 20;
    protected static final int DEFAULT_DIE_LEVEL = 1;
    protected static final int NO_DIE_LEVEL = 0;
    protected static final Set<String> insertBefore = new HashSet<>();

    static {
        insertBefore.add("</web-app>");
        insertBefore.add("<servlet-mapping>");
        insertBefore.add("<session-config>");
        insertBefore.add("<mime-mapping>");
        insertBefore.add("<welcome-file-list>");
        insertBefore.add("<error-page>");
        insertBefore.add("<taglib>");
        insertBefore.add("<resource-env-ref>");
        insertBefore.add("<resource-ref>");
        insertBefore.add("<security-constraint>");
        insertBefore.add("<login-config>");
        insertBefore.add("<security-role>");
        insertBefore.add("<env-entry>");
        insertBefore.add("<ejb-ref>");
        insertBefore.add("<ejb-local-ref>");
    }

    protected String classPath = null;
    protected ClassLoader loader = null;
    protected boolean trimSpaces = false;
    protected boolean genStringAsCharArray = false;
    protected boolean validateTld;
    protected boolean validateXml;
    protected boolean blockExternal = true;
    protected boolean strictQuoteEscaping = true;
    protected boolean quoteAttributeEL = true;
    protected boolean xpoweredBy;
    protected boolean mappedFile = false;
    protected boolean poolingEnabled = true;
    protected File scratchDir;
    protected String ieClassId = DEFAULT_IE_CLASS_ID;
    protected String targetPackage;
    protected String targetClassName;
    protected String uriBase;
    protected String uriRoot;
    protected int dieLevel;
    protected boolean helpNeeded = false;
    protected boolean compile = false;
    protected boolean smapSuppressed = true;
    protected boolean smapDumped = false;
    protected boolean caching = true;
    protected final Map<String, TagLibraryInfo> cache = new HashMap<>();

    protected String compiler = null;

    protected String compilerTargetVM = "1.7";
    protected String compilerSourceVM = "1.7";

    protected boolean classDebugInfo = true;

    /**
     * 如果出现编译错误, 抛出一个异常.
     * 默认是 true 保存旧的行为.
     */
    protected boolean failOnError = true;

    /**
     * 分隔过程是否应进行编译?
     */
    private boolean fork = false;

    /**
     * 要作为JSP文件处理的文件扩展名.
     * 默认列表是 .jsp 和 .jspx.
     */
    protected List<String> extensions;

    /**
     * The pages.
     */
    protected final List<String> pages = new Vector<>();

    /**
     * 默认是True.
     */
    protected boolean errorOnUseBeanInvalidClassAttribute = true;

    /**
     * java文件的编码. 默认是 UTF-8. Added per bugzilla 19622.
     */
    protected String javaEncoding = "UTF-8";

    // web.xml 片段的生成
    protected String webxmlFile;
    protected int webxmlLevel;
    protected String webxmlEncoding = "UTF-8";
    protected boolean addWebXmlMappings = false;

    protected Writer mapout;
    protected CharArrayWriter servletout;
    protected CharArrayWriter mappingout;

    /**
     * The servlet context.
     */
    protected JspCServletContext context;

    /**
     * 运行上下文.
     * 保持一个假的 JspRuntimeContext 编译标签文件.
     */
    protected JspRuntimeContext rctxt;

    /**
     * 缓存TLD 位置
     */
    protected TldCache tldCache = null;

    protected JspConfig jspConfig = null;
    protected TagPluginManager tagPluginManager = null;

    protected TldScanner scanner = null;

    protected boolean verbose = false;
    protected boolean listErrors = false;
    protected boolean showSuccess = false;
    protected int argPos;
    protected boolean fullstop = false;
    protected String args[];

    public static void main(String arg[]) {
        if (arg.length == 0) {
            System.out.println(Localizer.getMessage("jspc.usage"));
        } else {
            JspC jspc = new JspC();
            try {
                jspc.setArgs(arg);
                if (jspc.helpNeeded) {
                    System.out.println(Localizer.getMessage("jspc.usage"));
                } else {
                    jspc.execute();
                }
            } catch (JasperException je) {
                System.err.println(je);
                if (jspc.dieLevel != NO_DIE_LEVEL) {
                    System.exit(jspc.dieLevel);
                }
            } catch (BuildException je) {
                System.err.println(je);
                if (jspc.dieLevel != NO_DIE_LEVEL) {
                    System.exit(jspc.dieLevel);
                }
            }
        }
    }

    /**
     * 应用命令行参数.
     * 
     * @param arg 参数
     * @throws JasperException JSPC错误
     */
    public void setArgs(String[] arg) throws JasperException {
        args = arg;
        String tok;

        dieLevel = NO_DIE_LEVEL;

        while ((tok = nextArg()) != null) {
            if (tok.equals(SWITCH_VERBOSE)) {
                verbose = true;
                showSuccess = true;
                listErrors = true;
            } else if (tok.equals(SWITCH_OUTPUT_DIR)) {
                tok = nextArg();
                setOutputDir( tok );
            } else if (tok.equals(SWITCH_PACKAGE_NAME)) {
                targetPackage = nextArg();
            } else if (tok.equals(SWITCH_COMPILE)) {
                compile=true;
            } else if (tok.equals(SWITCH_CLASS_NAME)) {
                targetClassName = nextArg();
            } else if (tok.equals(SWITCH_URI_BASE)) {
                uriBase=nextArg();
            } else if (tok.equals(SWITCH_URI_ROOT)) {
                setUriroot( nextArg());
            } else if (tok.equals(SWITCH_FILE_WEBAPP)) {
                setUriroot( nextArg());
            } else if ( tok.equals( SHOW_SUCCESS ) ) {
                showSuccess = true;
            } else if ( tok.equals( LIST_ERRORS ) ) {
                listErrors = true;
            } else if (tok.equals(SWITCH_WEBAPP_INC)) {
                webxmlFile = nextArg();
                if (webxmlFile != null) {
                    webxmlLevel = INC_WEBXML;
                }
            } else if (tok.equals(SWITCH_WEBAPP_XML)) {
                webxmlFile = nextArg();
                if (webxmlFile != null) {
                    webxmlLevel = ALL_WEBXML;
                }
            } else if (tok.equals(SWITCH_WEBAPP_XML_ENCODING)) {
                setWebXmlEncoding(nextArg());
            } else if (tok.equals(SWITCH_ADD_WEBAPP_XML_MAPPINGS)) {
                setAddWebXmlMappings(true);
            } else if (tok.equals(SWITCH_MAPPED)) {
                mappedFile = true;
            } else if (tok.equals(SWITCH_XPOWERED_BY)) {
                xpoweredBy = true;
            } else if (tok.equals(SWITCH_TRIM_SPACES)) {
                setTrimSpaces(true);
            } else if (tok.equals(SWITCH_CACHE)) {
                tok = nextArg();
                if ("false".equals(tok)) {
                    caching = false;
                } else {
                    caching = true;
                }
            } else if (tok.equals(SWITCH_CLASSPATH)) {
                setClassPath(nextArg());
            } else if (tok.startsWith(SWITCH_DIE)) {
                try {
                    dieLevel = Integer.parseInt(
                        tok.substring(SWITCH_DIE.length()));
                } catch (NumberFormatException nfe) {
                    dieLevel = DEFAULT_DIE_LEVEL;
                }
            } else if (tok.equals(SWITCH_HELP)) {
                helpNeeded = true;
            } else if (tok.equals(SWITCH_POOLING)) {
                tok = nextArg();
                if ("false".equals(tok)) {
                    poolingEnabled = false;
                } else {
                    poolingEnabled = true;
                }
            } else if (tok.equals(SWITCH_ENCODING)) {
                setJavaEncoding(nextArg());
            } else if (tok.equals(SWITCH_SOURCE)) {
                setCompilerSourceVM(nextArg());
            } else if (tok.equals(SWITCH_TARGET)) {
                setCompilerTargetVM(nextArg());
            } else if (tok.equals(SWITCH_SMAP)) {
                smapSuppressed = false;
            } else if (tok.equals(SWITCH_DUMP_SMAP)) {
                smapDumped = true;
            } else if (tok.equals(SWITCH_VALIDATE_TLD)) {
                setValidateTld(true);
            } else if (tok.equals(SWITCH_VALIDATE_XML)) {
                setValidateXml(true);
            } else if (tok.equals(SWITCH_NO_BLOCK_EXTERNAL)) {
                setBlockExternal(false);
            } else if (tok.equals(SWITCH_NO_STRICT_QUOTE_ESCAPING)) {
                setStrictQuoteEscaping(false);
            } else if (tok.equals(SWITCH_QUOTE_ATTRIBUTE_EL)) {
                setQuoteAttributeEL(true);
            } else if (tok.equals(SWITCH_NO_QUOTE_ATTRIBUTE_EL)) {
                setQuoteAttributeEL(false);
            } else {
                if (tok.startsWith("-")) {
                    throw new JasperException("Unrecognized option: " + tok +
                        ".  Use -help for help.");
                }
                if (!fullstop) {
                    argPos--;
                }
                // 开始将其余部分作为JSP页面处理
                break;
            }
        }

        // 将所有额外参数添加到文件列表中
        while( true ) {
            String file = nextFile();
            if( file==null ) {
                break;
            }
            pages.add( file );
        }
    }

    /**
     * 总是返回<code>true</code>.
     */
    @Override
    public boolean getKeepGenerated() {
        // isn't this why we are running jspc?
        return true;
    }

    @Override
    public boolean getTrimSpaces() {
        return trimSpaces;
    }

    /**
     * 设置移除完全由空格组成的模板文本的选项.
     *
     * @param ts New value
     */
    public void setTrimSpaces(boolean ts) {
        this.trimSpaces = ts;
    }

    @Override
    public boolean isPoolingEnabled() {
        return poolingEnabled;
    }

    /**
     * 设置启用标记处理池的选项.
     * 
     * @param poolingEnabled New value
     */
    public void setPoolingEnabled(boolean poolingEnabled) {
        this.poolingEnabled = poolingEnabled;
    }

    @Override
    public boolean isXpoweredBy() {
        return xpoweredBy;
    }

    /**
     * 是否生成X-Powered-By响应 header.
     * 
     * @param xpoweredBy New value
     */
    public void setXpoweredBy(boolean xpoweredBy) {
        this.xpoweredBy = xpoweredBy;
    }

    /**
     * 总是返回<code>true</code>.
     */
    @Override
    public boolean getDisplaySourceFragment() {
        return true;
    }

    @Override
    public int getMaxLoadedJsps() {
        return -1;
    }

    @Override
    public int getJspIdleTimeout() {
        return -1;
    }

    @Override
    public boolean getErrorOnUseBeanInvalidClassAttribute() {
        return errorOnUseBeanInvalidClassAttribute;
    }

    /**
     * 如果在useBean动作中指定的类属性无效，则设置发出编译错误的选项.
     * 
     * @param b New value
     */
    public void setErrorOnUseBeanInvalidClassAttribute(boolean b) {
        errorOnUseBeanInvalidClassAttribute = b;
    }

    @Override
    public boolean getMappedFile() {
        return mappedFile;
    }

    public void setMappedFile(boolean b) {
        mappedFile = b;
    }

    /**
     * 设置在编译类中包含调试信息的选项.
     * @param b New value
     */
    public void setClassDebugInfo( boolean b ) {
        classDebugInfo=b;
    }

    @Override
    public boolean getClassDebugInfo() {
        // 编译与调试信息
        return classDebugInfo;
    }

    @Override
    public boolean isCaching() {
        return caching;
    }

    /**
     * 设置启用缓存的选项.
     * 
     * @param caching New value
     */
    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    @Override
    public Map<String, TagLibraryInfo> getCache() {
        return cache;
    }

    /**
     * 总是返回<code>0</code>.
     */
    @Override
    public int getCheckInterval() {
        return 0;
    }

    /**
     * 总是返回<code>0</code>.
     */
    @Override
    public int getModificationTestInterval() {
        return 0;
    }


    /**
     * 总是返回<code>false</code>.
     */
    @Override
    public boolean getRecompileOnFail() {
        return false;
    }


    /**
     * 总是返回<code>false</code>.
     */
    @Override
    public boolean getDevelopment() {
        return false;
    }

    @Override
    public boolean isSmapSuppressed() {
        return smapSuppressed;
    }

    public void setSmapSuppressed(boolean smapSuppressed) {
        this.smapSuppressed = smapSuppressed;
    }

    @Override
    public boolean isSmapDumped() {
        return smapDumped;
    }

    public void setSmapDumped(boolean smapDumped) {
        this.smapDumped = smapDumped;
    }


    /**
     * 文本字符串是否生成为char 数组, 在某些情况下可以提高性能.
     *
     * @param genStringAsCharArray true 如果文本字符串被生成为char 数组, 否则false
     */
    public void setGenStringAsCharArray(boolean genStringAsCharArray) {
        this.genStringAsCharArray = genStringAsCharArray;
    }

    @Override
    public boolean genStringAsCharArray() {
        return genStringAsCharArray;
    }

    /**
     * 设置class-id值发送给Internet Explorer, 当使用<jsp:plugin>标签时.
     *
     * @param ieClassId
     *            Class-id value
     */
    public void setIeClassId(String ieClassId) {
        this.ieClassId = ieClassId;
    }

    @Override
    public String getIeClassId() {
        return ieClassId;
    }

    @Override
    public File getScratchDir() {
        return scratchDir;
    }

    @Override
    public String getCompiler() {
        return compiler;
    }

    /**
     * 设置选项以确定要使用的编译器.
     * 
     * @param c New value
     */
    public void setCompiler(String c) {
        compiler=c;
    }

    @Override
    public String getCompilerClassName() {
        return null;
    }

    @Override
    public String getCompilerTargetVM() {
        return compilerTargetVM;
    }

    /**
     * 设置编译器目标VM.
     * @param vm New value
     */
    public void setCompilerTargetVM(String vm) {
        compilerTargetVM = vm;
    }

     @Override
    public String getCompilerSourceVM() {
         return compilerSourceVM;
     }

     /**
      * 设置编译器源 VM.
      * @param vm New value
      */
    public void setCompilerSourceVM(String vm) {
        compilerSourceVM = vm;
    }

    @Override
    public TldCache getTldCache() {
        return tldCache;
    }

    /**
     * 返回java文件使用的编码. 默认是 UTF-8.
     *
     * @return String The encoding
     */
    @Override
    public String getJavaEncoding() {
        return javaEncoding;
    }

    /**
     * 设置java文件使用的编码.
     *
     * @param encodingName The name, e.g. "UTF-8"
     */
    public void setJavaEncoding(String encodingName) {
        javaEncoding = encodingName;
    }

    @Override
    public boolean getFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    @Override
    public String getClassPath() {
        if( classPath != null )
            return classPath;
        return System.getProperty("java.class.path");
    }

    /**
     * 设置编译JSP文件生成的servlet时使用的类路径
      * @param s New value
     */
    public void setClassPath(String s) {
        classPath=s;
    }

    /**
     * 返回被视为JSP文件的文件扩展名列表.
     *
     * @return The list of extensions
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * 将给定的文件扩展名添加到作为JSP文件处理的扩展名列表中.
     *
     * @param extension 要添加的扩展名, 即 "myjsp"
     */
    protected void addExtension(final String extension) {
        if(extension != null) {
            if(extensions == null) {
                extensions = new Vector<>();
            }

            extensions.add(extension);
        }
    }

    /**
     * webapp基础目录. 用于生成类名和解析包含
     * 
     * @param s New value
     */
    public void setUriroot( String s ) {
        if (s == null) {
            uriRoot = null;
            return;
        }
        try {
            uriRoot = resolveFile(s).getCanonicalPath();
        } catch( Exception ex ) {
            uriRoot = s;
        }
    }

    /**
     * 解析要处理的JSP文件的逗号分隔列表.
     *
     * <p>每个文件被解释相对于 uriroot, 除非它是绝对的,即它必须以 uriroot开头.
     *
     * @param jspFiles 要处理的JSP文件的逗号分隔列表
     */
    public void setJspFiles(final String jspFiles) {
        if(jspFiles == null) {
            return;
        }

        StringTokenizer tok = new StringTokenizer(jspFiles, ",");
        while (tok.hasMoreTokens()) {
            pages.add(tok.nextToken());
        }
    }

    public void setCompile( final boolean b ) {
        compile = b;
    }

    /**
     * 设置冗长级别. 实际数字并不重要: 如果大于零, 将为 true.
     *
     * @param level Positive means verbose
     */
    public void setVerbose( final int level ) {
        if (level > 0) {
            verbose = true;
            showSuccess = true;
            listErrors = true;
        }
    }

    public void setValidateTld( boolean b ) {
        this.validateTld = b;
    }

    public boolean isValidateTld() {
        return validateTld;
    }

    public void setValidateXml( boolean b ) {
        this.validateXml = b;
    }

    public boolean isValidateXml() {
        return validateXml;
    }

    public void setBlockExternal( boolean b ) {
        this.blockExternal = b;
    }

    public boolean isBlockExternal() {
        return blockExternal;
    }

    public void setStrictQuoteEscaping( boolean b ) {
        this.strictQuoteEscaping = b;
    }

    @Override
    public boolean getStrictQuoteEscaping() {
        return strictQuoteEscaping;
    }

    public void setQuoteAttributeEL(boolean b) {
        quoteAttributeEL = b;
    }

    @Override
    public boolean getQuoteAttributeEL() {
        return quoteAttributeEL;
    }

    public void setListErrors( boolean b ) {
        listErrors = b;
    }

    public void setOutputDir( String s ) {
        if( s!= null ) {
            scratchDir = resolveFile(s).getAbsoluteFile();
        } else {
            scratchDir=null;
        }
    }

    /**
     * 设置用于生成的servlet类的包名.
     * @param p New value
     */
    public void setPackage( String p ) {
        targetPackage=p;
    }

    /**
     * 生成文件的类名(不包括包名). 仅在转换单个文件时才可使用.
     * XXX Do we need this feature ?
     * @param p New value
     */
    public void setClassName( String p ) {
        targetClassName=p;
    }

    /**
     * 生成一个类定义的web.xml片段.
     * @param s New value
     */
    public void setWebXmlFragment( String s ) {
        webxmlFile=resolveFile(s).getAbsolutePath();
        webxmlLevel=INC_WEBXML;
    }

    /**
     * 生成一个完整的类定义的web.xml文件.
     * @param s New value
     */
    public void setWebXml( String s ) {
        webxmlFile=resolveFile(s).getAbsolutePath();
        webxmlLevel=ALL_WEBXML;
    }

    /**
     * 设置用于读取和写入web.xml文件的编码.
     *
     * <p>
     * 如果未指定, 默认为 UTF-8.
     * </p>
     *
     * @param encoding   Encoding, e.g. "UTF-8".
     */
    public void setWebXmlEncoding(String encoding) {
        webxmlEncoding = encoding;
    }

    /**
     * 将生成的web.xml片段合并到我们正在处理的Web应用程序的WEB-INF/web.xml文件中.
     *
     * @param b  <code>true</code>将片段合并到已处理的Web应用程序的现有Web.xml文件中({uriroot}/WEB-INF/web.xml),
     * 			<code>false</code>保存生成的web.xml片段
     */
    public void setAddWebXmlMappings(boolean b) {
        addWebXmlMappings = b;
    }

    /**
     * 是否在编译错误时抛出异常.
     * @param b New value
     */
    public void setFailOnError(final boolean b) {
        failOnError = b;
    }

    /**
     * @return <code>true</code>在编译错误时抛出异常.
     */
    public boolean getFailOnError() {
        return failOnError;
    }

    @Override
    public JspConfig getJspConfig() {
        return jspConfig;
    }

    @Override
    public TagPluginManager getTagPluginManager() {
        return tagPluginManager;
    }

    /**
     * 将JSP页面servlet的servlet声明和映射添加到生成的web.xml片段.
     *
     * @param file JSP文件的上下文相对路径, 即<code>/index.jsp</code>
     * @param clctxt servlet的编译上下文
     * 
     * @throws IOException 发生IO错误
     */
    public void generateWebMapping( String file, JspCompilationContext clctxt )
        throws IOException
    {
        if (log.isDebugEnabled()) {
            log.debug("Generating web mapping for file " + file
                      + " using compilation context " + clctxt);
        }

        String className = clctxt.getServletClassName();
        String packageName = clctxt.getServletPackageName();

        String thisServletName;
        if  ("".equals(packageName)) {
            thisServletName = className;
        } else {
            thisServletName = packageName + '.' + className;
        }

        if (servletout != null) {
            servletout.write("\n    <servlet>\n        <servlet-name>");
            servletout.write(thisServletName);
            servletout.write("</servlet-name>\n        <servlet-class>");
            servletout.write(thisServletName);
            servletout.write("</servlet-class>\n    </servlet>\n");
        }
        if (mappingout != null) {
            mappingout.write("\n    <servlet-mapping>\n        <servlet-name>");
            mappingout.write(thisServletName);
            mappingout.write("</servlet-name>\n        <url-pattern>");
            mappingout.write(file.replace('\\', '/'));
            mappingout.write("</url-pattern>\n    </servlet-mapping>\n");

        }
    }

    /**
     * 包括webapp的web.xml中生成的web.xml.
     * 
     * @throws IOException 发生的 IO 错误
     */
    protected void mergeIntoWebXml() throws IOException {

        File webappBase = new File(uriRoot);
        File webXml = new File(webappBase, "WEB-INF/web.xml");
        File webXml2 = new File(webappBase, "WEB-INF/web2.xml");
        String insertStartMarker =
            Localizer.getMessage("jspc.webinc.insertStart");
        String insertEndMarker =
            Localizer.getMessage("jspc.webinc.insertEnd");

        try (BufferedReader reader = new BufferedReader(openWebxmlReader(webXml));
                BufferedReader fragmentReader =
                        new BufferedReader(openWebxmlReader(new File(webxmlFile)));
                PrintWriter writer = new PrintWriter(openWebxmlWriter(webXml2))) {

            // 插入 <servlet> 和 <servlet-mapping> 声明
            boolean inserted = false;
            int current = reader.read();
            while (current > -1) {
                if (current == '<') {
                    String element = getElement(reader);
                    if (!inserted && insertBefore.contains(element)) {
                        // 插入生成的内容
                        writer.println(insertStartMarker);
                        while (true) {
                            String line = fragmentReader.readLine();
                            if (line == null) {
                                writer.println();
                                break;
                            }
                            writer.println(line);
                        }
                        writer.println(insertEndMarker);
                        writer.println();
                        writer.write(element);
                        inserted = true;
                    } else if (element.equals(insertStartMarker)) {
                        // 跳过之前生成的内容
                        while (true) {
                            current = reader.read();
                            if (current < 0) {
                                throw new EOFException();
                            }
                            if (current == '<') {
                                element = getElement(reader);
                                if (element.equals(insertEndMarker)) {
                                    break;
                                }
                            }
                        }
                        current = reader.read();
                        while (current == '\n' || current == '\r') {
                            current = reader.read();
                        }
                        continue;
                    } else {
                        writer.write(element);
                    }
                } else {
                    writer.write(current);
                }
                current = reader.read();
            }
        }

        try (FileInputStream fis = new FileInputStream(webXml2);
                FileOutputStream fos = new FileOutputStream(webXml)) {

            byte buf[] = new byte[512];
            while (true) {
                int n = fis.read(buf);
                if (n < 0) {
                    break;
                }
                fos.write(buf, 0, n);
            }
        }

        if(!webXml2.delete() && log.isDebugEnabled())
            log.debug(Localizer.getMessage("jspc.delete.fail",
                    webXml2.toString()));

        if (!(new File(webxmlFile)).delete() && log.isDebugEnabled())
            log.debug(Localizer.getMessage("jspc.delete.fail", webxmlFile));

    }

    /*
     * 假设有效的XML
     */
    private String getElement(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        result.append('<');

        boolean done = false;

        while (!done) {
            int current = reader.read();
            while (current != '>') {
                if (current < 0) {
                    throw new EOFException();
                }
                result.append((char) current);
                current = reader.read();
            }
            result.append((char) current);

            int len = result.length();
            if (len > 4 && result.substring(0, 4).equals("<!--")) {
                // 这是一个注释 - 确保是在结尾
                if (len >= 7 && result.substring(len - 3, len).equals("-->")) {
                    done = true;
                }
            } else {
                done = true;
            }
        }


        return result.toString();
    }

    protected void processFile(String file)
        throws JasperException
    {
        if (log.isDebugEnabled()) {
            log.debug("Processing file: " + file);
        }

        ClassLoader originalClassLoader = null;

        try {
            // set up a scratch/output dir if none is provided
            if (scratchDir == null) {
                String temp = System.getProperty("java.io.tmpdir");
                if (temp == null) {
                    temp = "";
                }
                scratchDir = new File(temp).getAbsoluteFile();
            }

            String jspUri=file.replace('\\','/');
            JspCompilationContext clctxt = new JspCompilationContext
                ( jspUri, this, context, null, rctxt );

            /* Override the defaults */
            if ((targetClassName != null) && (targetClassName.length() > 0)) {
                clctxt.setServletClassName(targetClassName);
                targetClassName = null;
            }
            if (targetPackage != null) {
                clctxt.setServletPackageName(targetPackage);
            }

            originalClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);

            clctxt.setClassLoader(loader);
            clctxt.setClassPath(classPath);

            Compiler clc = clctxt.createCompiler();

            // 如果设置了编译, 生成 .java 和 .class, 如果 .jsp 文件比 .class 文件更新;
            // 否则只生成 .java, 如果 .jsp 文件比 .java 文件更新
            if( clc.isOutDated(compile) ) {
                if (log.isDebugEnabled()) {
                    log.debug(jspUri + " is out dated, compiling...");
                }

                clc.compile(compile, true);
            }

            // Generate mapping
            generateWebMapping( file, clctxt );
            if ( showSuccess ) {
                log.info( "Built File: " + file );
            }

        } catch (JasperException je) {
            Throwable rootCause = je;
            while (rootCause instanceof JasperException
                    && ((JasperException) rootCause).getRootCause() != null) {
                rootCause = ((JasperException) rootCause).getRootCause();
            }
            if (rootCause != je) {
                log.error(Localizer.getMessage("jspc.error.generalException",
                                               file),
                          rootCause);
            }

            // Bugzilla 35114.
            if(getFailOnError()) {
                throw je;
            } else {
                log.error(je.getMessage());
            }

        } catch (Exception e) {
            if ((e instanceof FileNotFoundException) && log.isWarnEnabled()) {
                log.warn(Localizer.getMessage("jspc.error.fileDoesNotExist",
                                              e.getMessage()));
            }
            throw new JasperException(e);
        } finally {
            if(originalClassLoader != null) {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    /**
     * 找到web应用中所有的jsp 文件的位置. 如果没有指定明确的JSP时使用.
     * 
     * @param base Base path
     */
    public void scanFiles( File base ) {
        Stack<String> dirs = new Stack<>();
        dirs.push(base.toString());

        // 确保默认扩展名始终包括在内
        if ((getExtensions() == null) || (getExtensions().size() < 2)) {
            addExtension("jsp");
            addExtension("jspx");
        }

        while (!dirs.isEmpty()) {
            String s = dirs.pop();
            File f = new File(s);
            if (f.exists() && f.isDirectory()) {
                String[] files = f.list();
                String ext;
                for (int i = 0; (files != null) && i < files.length; i++) {
                    File f2 = new File(s, files[i]);
                    if (f2.isDirectory()) {
                        dirs.push(f2.getPath());
                    } else {
                        String path = f2.getPath();
                        String uri = path.substring(uriRoot.length());
                        ext = files[i].substring(files[i].lastIndexOf('.') +1);
                        if (getExtensions().contains(ext) ||
                            jspConfig.isJspPage(uri)) {
                            pages.add(path);
                        }
                    }
                }
            }
        }
    }

    /**
     * 执行编译.
     */
    @Override
    public void execute() {
        if(log.isDebugEnabled()) {
            log.debug("execute() starting for " + pages.size() + " pages.");
        }

        try {
            if (uriRoot == null) {
                if( pages.size() == 0 ) {
                    throw new JasperException(
                        Localizer.getMessage("jsp.error.jspc.missingTarget"));
                }
                String firstJsp = pages.get( 0 );
                File firstJspF = new File( firstJsp );
                if (!firstJspF.exists()) {
                    throw new JasperException(
                        Localizer.getMessage("jspc.error.fileDoesNotExist",
                                             firstJsp));
                }
                locateUriRoot( firstJspF );
            }

            if (uriRoot == null) {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.jspc.no_uriroot"));
            }

            File uriRootF = new File(uriRoot);
            if (!uriRootF.isDirectory()) {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.jspc.uriroot_not_dir"));
            }

            if (loader == null) {
                loader = initClassLoader();
            }
            if (context == null) {
                initServletContext(loader);
            }

            // 没有明确的页面, 将处理web应用中所有的 .jsp
            if (pages.size() == 0) {
                scanFiles(uriRootF);
            }

            initWebXml();

            Iterator<String> iter = pages.iterator();
            while (iter.hasNext()) {
                String nextjsp = iter.next();
                File fjsp = new File(nextjsp);
                if (!fjsp.isAbsolute()) {
                    fjsp = new File(uriRootF, nextjsp);
                }
                if (!fjsp.exists()) {
                    if (log.isWarnEnabled()) {
                        log.warn
                            (Localizer.getMessage
                             ("jspc.error.fileDoesNotExist", fjsp.toString()));
                    }
                    continue;
                }
                String s = fjsp.getAbsolutePath();
                if (s.startsWith(uriRoot)) {
                    nextjsp = s.substring(uriRoot.length());
                }
                if (nextjsp.startsWith("." + File.separatorChar)) {
                    nextjsp = nextjsp.substring(2);
                }
                processFile(nextjsp);
            }

            completeWebXml();

            if (addWebXmlMappings) {
                mergeIntoWebXml();
            }

        } catch (IOException ioe) {
            throw new BuildException(ioe);

        } catch (JasperException je) {
            Throwable rootCause = je;
            while (rootCause instanceof JasperException
                    && ((JasperException) rootCause).getRootCause() != null) {
                rootCause = ((JasperException) rootCause).getRootCause();
            }
            if (rootCause != je) {
                rootCause.printStackTrace();
            }
            throw new BuildException(je);
        } finally {
            if (loader != null) {
                LogFactory.release(loader);
            }
        }
    }

    // ==================== protected utility methods ====================

    protected String nextArg() {
        if ((argPos >= args.length)
            || (fullstop = SWITCH_FULL_STOP.equals(args[argPos]))) {
            return null;
        } else {
            return args[argPos++];
        }
    }

    protected String nextFile() {
        if (fullstop) argPos++;
        if (argPos >= args.length) {
            return null;
        } else {
            return args[argPos++];
        }
    }

    protected void initWebXml() throws JasperException {
        try {
            if (webxmlLevel >= INC_WEBXML) {
                mapout = openWebxmlWriter(new File(webxmlFile));
                servletout = new CharArrayWriter();
                mappingout = new CharArrayWriter();
            } else {
                mapout = null;
                servletout = null;
                mappingout = null;
            }
            if (webxmlLevel >= ALL_WEBXML) {
                mapout.write(Localizer.getMessage("jspc.webxml.header", webxmlEncoding));
                mapout.flush();
            } else if ((webxmlLevel>= INC_WEBXML) && !addWebXmlMappings) {
                mapout.write(Localizer.getMessage("jspc.webinc.header"));
                mapout.flush();
            }
        } catch (IOException ioe) {
            mapout = null;
            servletout = null;
            mappingout = null;
            throw new JasperException(ioe);
        }
    }

    protected void completeWebXml() {
        if (mapout != null) {
            try {
                servletout.writeTo(mapout);
                mappingout.writeTo(mapout);
                if (webxmlLevel >= ALL_WEBXML) {
                    mapout.write(Localizer.getMessage("jspc.webxml.footer"));
                } else if ((webxmlLevel >= INC_WEBXML) && !addWebXmlMappings) {
                    mapout.write(Localizer.getMessage("jspc.webinc.footer"));
                }
                mapout.close();
            } catch (IOException ioe) {
                // nothing to do if it fails since we are done with it
            }
        }
    }


    protected void initTldScanner(JspCServletContext context, ClassLoader classLoader) {
        if (scanner != null) {
            return;
        }

        scanner = newTldScanner(context, true, isValidateTld(), isBlockExternal());
        scanner.setClassLoader(classLoader);
    }


    protected TldScanner newTldScanner(JspCServletContext context, boolean namespaceAware,
            boolean validate, boolean blockExternal) {
        return new TldScanner(context, namespaceAware, validate, blockExternal);
    }


    protected void initServletContext(ClassLoader classLoader)
            throws IOException, JasperException {
        // TODO: should we use the Ant Project's log?
        PrintWriter log = new PrintWriter(System.out);
        URL resourceBase = new File(uriRoot).getCanonicalFile().toURI().toURL();

        context = new JspCServletContext(log, resourceBase, classLoader,
                isValidateXml(), isBlockExternal());
        if (isValidateTld()) {
            context.setInitParameter(Constants.XML_VALIDATION_TLD_INIT_PARAM, "true");
        }


        initTldScanner(context, classLoader);

        try {
            scanner.scan();
        } catch (SAXException e) {
            throw new JasperException(e);
        }
        tldCache = new TldCache(context, scanner.getUriTldResourcePathMap(),
                scanner.getTldResourcePathTaglibXmlMap());
        context.setAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME, tldCache);
        rctxt = new JspRuntimeContext(context, this);
        jspConfig = new JspConfig(context);
        tagPluginManager = new TagPluginManager(context);
    }

    /**
     * 初始化的类装载器, 如果给定上下文需要编译.
     *
     * @param clctxt 编译环境
     * @throws IOException 如果出现错误
     */
    protected ClassLoader initClassLoader() throws IOException {

        classPath = getClassPath();

        ClassLoader jspcLoader = getClass().getClassLoader();
        if (jspcLoader instanceof AntClassLoader) {
            classPath += File.pathSeparator
                + ((AntClassLoader) jspcLoader).getClasspath();
        }

        // 将classPath 转换为 URL
        ArrayList<URL> urls = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(classPath,
                                                        File.pathSeparator);
        while (tokenizer.hasMoreTokens()) {
            String path = tokenizer.nextToken();
            try {
                File libFile = new File(path);
                urls.add(libFile.toURI().toURL());
            } catch (IOException ioe) {
                // Failing a toCanonicalPath on a file that
                // exists() should be a JVM regression test,
                // therefore we have permission to freak uot
                throw new RuntimeException(ioe.toString());
            }
        }

        File webappBase = new File(uriRoot);
        if (webappBase.exists()) {
            File classes = new File(webappBase, "/WEB-INF/classes");
            try {
                if (classes.exists()) {
                    classPath = classPath + File.pathSeparator
                        + classes.getCanonicalPath();
                    urls.add(classes.getCanonicalFile().toURI().toURL());
                }
            } catch (IOException ioe) {
                // failing a toCanonicalPath on a file that
                // exists() should be a JVM regression test,
                // therefore we have permission to freak out
                throw new RuntimeException(ioe.toString());
            }
            File lib = new File(webappBase, "/WEB-INF/lib");
            if (lib.exists() && lib.isDirectory()) {
                String[] libs = lib.list();
                if (libs != null) {
                    for (int i = 0; i < libs.length; i++) {
                        if( libs[i].length() <5 ) continue;
                        String ext=libs[i].substring( libs[i].length() - 4 );
                        if (! ".jar".equalsIgnoreCase(ext)) {
                            if (".tld".equalsIgnoreCase(ext)) {
                                log.warn("TLD files should not be placed in "
                                         + "/WEB-INF/lib");
                            }
                            continue;
                        }
                        try {
                            File libFile = new File(lib, libs[i]);
                            classPath = classPath + File.pathSeparator
                                + libFile.getAbsolutePath();
                            urls.add(libFile.getAbsoluteFile().toURI().toURL());
                        } catch (IOException ioe) {
                            // failing a toCanonicalPath on a file that
                            // exists() should be a JVM regression test,
                            // therefore we have permission to freak out
                            throw new RuntimeException(ioe.toString());
                        }
                    }
                }
            }
        }

        URL urlsA[]=new URL[urls.size()];
        urls.toArray(urlsA);
        loader = new URLClassLoader(urlsA, this.getClass().getClassLoader());
        return loader;
    }

    /**
     * 找到 WEB-INF 文件夹, 通过在目录树中查找.
     * 如果没有显式的设置docBase将使用, 但只有文件.
     * XXX Maybe we should require the docbase.
     * @param f 查找开始的路径
     */
    protected void locateUriRoot( File f ) {
        String tUriBase = uriBase;
        if (tUriBase == null) {
            tUriBase = "/";
        }
        try {
            if (f.exists()) {
                f = new File(f.getAbsolutePath());
                while (true) {
                    File g = new File(f, "WEB-INF");
                    if (g.exists() && g.isDirectory()) {
                        uriRoot = f.getCanonicalPath();
                        uriBase = tUriBase;
                        if (log.isInfoEnabled()) {
                            log.info(Localizer.getMessage(
                                        "jspc.implicit.uriRoot",
                                        uriRoot));
                        }
                        break;
                    }
                    if (f.exists() && f.isDirectory()) {
                        tUriBase = "/" + f.getName() + "/" + tUriBase;
                    }

                    String fParent = f.getParent();
                    if (fParent == null) {
                        break;
                    } else {
                        f = new File(fParent);
                    }

                    // 如果没有可以接受的候选, uriRoot 将保持 null 表示 CompilerContext 使用当前 working/user 目录.
                }

                if (uriRoot != null) {
                    File froot = new File(uriRoot);
                    uriRoot = froot.getCanonicalPath();
                }
            }
        } catch (IOException ioe) {
            // 因为这是一个可选的默认值, uriRoot是null具有非错误意义, 可以直接通过
        }
    }

    /**
     * 在Ant和命令行的情况下, 正确的解析相对或绝对路径.  如果Ant开始处理我们, 我们应该使用当前项目的basedir 解析相对路径.
     *
     * @param s The file
     * @return The file resolved
     */
     protected File resolveFile(final String s) {
         if(getProject() == null) {
             // Note FileUtils.getFileUtils replaces FileUtils.newFileUtils in Ant 1.6.3
             return FileUtils.getFileUtils().resolveFile(null, s);
         } else {
             return FileUtils.getFileUtils().resolveFile(getProject().getBaseDir(), s);
         }
     }

    private Reader openWebxmlReader(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            return webxmlEncoding != null ? new InputStreamReader(fis,
                    webxmlEncoding) : new InputStreamReader(fis);
        } catch (IOException ex) {
            fis.close();
            throw ex;
        }
    }

    private Writer openWebxmlWriter(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            return webxmlEncoding != null ? new OutputStreamWriter(fos,
                    webxmlEncoding) : new OutputStreamWriter(fos);
        } catch (IOException ex) {
            fos.close();
            throw ex;
        }
    }
}
