package org.apache.jasper;

import java.io.File;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 用于保存特定于JSP引擎的所有init参数. 
 */
public final class EmbeddedServletOptions implements Options {

    // Logger
    private final Log log = LogFactory.getLog(EmbeddedServletOptions.class);

    private Properties settings = new Properties();

    /**
     * 在开发模式中是否使用Jasper?
     */
    private boolean development = true;

    /**
     * Should Ant fork its java compiles of JSP pages.
     */
    public boolean fork = true;

    /**
     * 是否保留生成的Java文件?
     */
    private boolean keepGenerated = true;

    /**
     * 指令或操作之间的空格是否应该被删除?
     */
    private boolean trimSpaces = false;

    /**
     * 是否启用标签处理程序池.
     */
    private boolean isPoolingEnabled = true;

    /**
     * 是否支持 "mapped"文件? 这将生成servlet，每行的JSP文件有一个打印语句.
     * 似乎是一个非常好的调试功能.
     */
    private boolean mappedFile = true;

    /**
     * 是否在类文件中包含调试信息?
     */
    private boolean classDebugInfo = true;

    /**
     * 后台编译线程检查间隔, 以秒为单位.
     */
    private int checkInterval = 0;

    /**
     * 是否阻止JSR45调试的SMAP信息的生成?
     */
    private boolean isSmapSuppressed = false;

    /**
     * JSR45调试的SMAP信息是否保存到文件中?
     */
    private boolean isSmapDumped = false;

    /**
     * 文本字符串是否被生成为char数组?
     */
    private boolean genStringAsCharArray = false;

    private boolean errorOnUseBeanInvalidClassAttribute = true;

    /**
     * 生成的servlet所在的目录
     */
    private File scratchDir;

    /**
     * 需要有IE 4和5版本. 可以从initParam设置, 所以如果它在未来发生变化, 就是要有一个ieClassId="<value>"类型的jsp initParam
     */
    private String ieClassId = "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";

    /**
     * 当编译生成servlet时, 应该使用的classpath?
     */
    private String classpath = null;

    /**
     * 使用的编译器.
     */
    private String compiler = null;

    /**
     * 编译器目标 VM.
     */
    private String compilerTargetVM = "1.7";

    /**
     * 编译器源 VM.
     */
    private String compilerSourceVM = "1.7";

    /**
     * 编译器类名.
     */
    private String compilerClassName = null;

    /**
     * 缓存TLD URI, 资源路径和解析的文件.
     */
    private TldCache tldCache = null;

    /**
     * JSP的配置信息
     */
    private JspConfig jspConfig = null;

    /**
     * TagPluginManager
     */
    private TagPluginManager tagPluginManager = null;

    /**
     * java平台的编码生成的JSP页面servlet.
     */
    private String javaEncoding = "UTF8";

    /**
     * 修改测试间隔.
     */
    private int modificationTestInterval = 4;

    /**
     * 在失败后立即重新编译?
     */
    private boolean recompileOnFail = false;

    /**
     * 是否生成X-Powered-By 响应 header?
     */
    private boolean xpoweredBy;

    /**
     * 是否应该在异常消息中包含源片段，可以将其显示给开发人员?
     */
    private boolean displaySourceFragment = true;


    /**
     * 每个Web应用程序加载的JSP的最大数量. 如果有更多的JSP加载，它们将被卸载.
     */
    private int maxLoadedJsps = -1;

    /**
     * JSP卸载后几秒钟的空闲时间.
     * 如果未设置或小于等于0，则不卸载JSP.
     */
    private int jspIdleTimeout = -1;

    /**
     * JSP 1.6是否严格应用于使用脚本片段表达式定义的属性?
     */
    private boolean strictQuoteEscaping = true;

    /**
     * 当EL用于JSP属性值时, 应该将JSP 1.6中描述的属性引用规则应用到表达式中吗?
     */
    private boolean quoteAttributeEL = true;

    public String getProperty(String name ) {
        return settings.getProperty( name );
    }

    public void setProperty(String name, String value ) {
        if (name != null && value != null){
            settings.setProperty( name, value );
        }
    }

    public void setQuoteAttributeEL(boolean b) {
        this.quoteAttributeEL = b;
    }

    @Override
    public boolean getQuoteAttributeEL() {
        return quoteAttributeEL;
    }

    /**
     * 是否保留生成的代码?
     */
    @Override
    public boolean getKeepGenerated() {
        return keepGenerated;
    }

    /**
     * 指令或操作之间的空格是否应该被删除?
     */
    @Override
    public boolean getTrimSpaces() {
        return trimSpaces;
    }

    @Override
    public boolean isPoolingEnabled() {
        return isPoolingEnabled;
    }

    /**
     * 是否支持HTML映射servlet?
     */
    @Override
    public boolean getMappedFile() {
        return mappedFile;
    }

    /**
     * 是否应该用调试信息编译类文件?
     */
    @Override
    public boolean getClassDebugInfo() {
        return classDebugInfo;
    }

    /**
     * 后台JSP编译线程检查间隔
     */
    @Override
    public int getCheckInterval() {
        return checkInterval;
    }

    /**
     * 修改测试间隔.
     */
    @Override
    public int getModificationTestInterval() {
        return modificationTestInterval;
    }

    /**
     * 在失败后立即重新编译?
     */
    @Override
    public boolean getRecompileOnFail() {
        return recompileOnFail;
    }

    /**
     * 在开发模式中是否使用Jasper?
     */
    @Override
    public boolean getDevelopment() {
        return development;
    }

    /**
     * 是否阻止JSR45调试的SMAP信息的生成?
     */
    @Override
    public boolean isSmapSuppressed() {
        return isSmapSuppressed;
    }

    /**
     * JSR45调试的SMAP信息是否保存到文件中?
     */
    @Override
    public boolean isSmapDumped() {
        return isSmapDumped;
    }

    /**
     * 文本字符串是否被生成为char数组?
     */
    @Override
    public boolean genStringAsCharArray() {
        return this.genStringAsCharArray;
    }

    /**
     * 当浏览器是IE时，在标签库中使用的Class ID. 
     */
    @Override
    public String getIeClassId() {
        return ieClassId;
    }

    /**
     * 生成的servlet所在的目录?
     */
    @Override
    public File getScratchDir() {
        return scratchDir;
    }

    /**
     * 当编译生成servlet时, 是否应该使用classpath
     */
    @Override
    public String getClassPath() {
        return classpath;
    }

    /**
     * 是否生成 X-Powered-By 响应头?
     */
    @Override
    public boolean isXpoweredBy() {
        return xpoweredBy;
    }

    /**
     * 使用的编译器.
     */
    @Override
    public String getCompiler() {
        return compiler;
    }

    @Override
    public String getCompilerTargetVM() {
        return compilerTargetVM;
    }

    @Override
    public String getCompilerSourceVM() {
        return compilerSourceVM;
    }

    /**
     * 要使用的Java编译器类.
     */
    @Override
    public String getCompilerClassName() {
        return compilerClassName;
    }

    @Override
    public boolean getErrorOnUseBeanInvalidClassAttribute() {
        return errorOnUseBeanInvalidClassAttribute;
    }

    public void setErrorOnUseBeanInvalidClassAttribute(boolean b) {
        errorOnUseBeanInvalidClassAttribute = b;
    }

    @Override
    public TldCache getTldCache() {
        return tldCache;
    }

    public void setTldCache(TldCache tldCache) {
        this.tldCache = tldCache;
    }

    @Override
    public String getJavaEncoding() {
        return javaEncoding;
    }

    @Override
    public boolean getFork() {
        return fork;
    }

    @Override
    public JspConfig getJspConfig() {
        return jspConfig;
    }

    @Override
    public TagPluginManager getTagPluginManager() {
        return tagPluginManager;
    }

    @Override
    public boolean isCaching() {
        return false;
    }

    @Override
    public Map<String, TagLibraryInfo> getCache() {
        return null;
    }

    /**
     * 是否应该在异常消息中包含源片段，可以将其显示给开发人员?
     */
    @Override
    public boolean getDisplaySourceFragment() {
        return displaySourceFragment;
    }

    /**
     * 每个Web应用程序加载的JSP的最大数量. 
     * 如果设置为大于0的值，则启动JSP. 默认: -1
     */
    @Override
    public int getMaxLoadedJsps() {
        return maxLoadedJsps;
    }

    /**
     * JSP卸载后几秒钟的空闲时间. 
     * 如果设置为大于0的值，则启动JSP. 默认: -1
     */
    @Override
    public int getJspIdleTimeout() {
        return jspIdleTimeout;
    }

    @Override
    public boolean getStrictQuoteEscaping() {
        return strictQuoteEscaping;
    }

    /**
     * 创建一个EmbeddedServletOption对象，使用从ServletConfig 和 ServletContext获取的有效数据. 
     * @param config The Servlet config
     * @param context The Servlet context
     */
    public EmbeddedServletOptions(ServletConfig config,
            ServletContext context) {

        Enumeration<String> enumeration=config.getInitParameterNames();
        while( enumeration.hasMoreElements() ) {
            String k=enumeration.nextElement();
            String v=config.getInitParameter( k );
            setProperty( k, v);
        }

        String keepgen = config.getInitParameter("keepgenerated");
        if (keepgen != null) {
            if (keepgen.equalsIgnoreCase("true")) {
                this.keepGenerated = true;
            } else if (keepgen.equalsIgnoreCase("false")) {
                this.keepGenerated = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.keepgen"));
                }
            }
        }


        String trimsp = config.getInitParameter("trimSpaces");
        if (trimsp != null) {
            if (trimsp.equalsIgnoreCase("true")) {
                trimSpaces = true;
            } else if (trimsp.equalsIgnoreCase("false")) {
                trimSpaces = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.trimspaces"));
                }
            }
        }

        this.isPoolingEnabled = true;
        String poolingEnabledParam
        = config.getInitParameter("enablePooling");
        if (poolingEnabledParam != null
                && !poolingEnabledParam.equalsIgnoreCase("true")) {
            if (poolingEnabledParam.equalsIgnoreCase("false")) {
                this.isPoolingEnabled = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.enablePooling"));
                }
            }
        }

        String mapFile = config.getInitParameter("mappedfile");
        if (mapFile != null) {
            if (mapFile.equalsIgnoreCase("true")) {
                this.mappedFile = true;
            } else if (mapFile.equalsIgnoreCase("false")) {
                this.mappedFile = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.mappedFile"));
                }
            }
        }

        String debugInfo = config.getInitParameter("classdebuginfo");
        if (debugInfo != null) {
            if (debugInfo.equalsIgnoreCase("true")) {
                this.classDebugInfo  = true;
            } else if (debugInfo.equalsIgnoreCase("false")) {
                this.classDebugInfo  = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.classDebugInfo"));
                }
            }
        }

        String checkInterval = config.getInitParameter("checkInterval");
        if (checkInterval != null) {
            try {
                this.checkInterval = Integer.parseInt(checkInterval);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.checkInterval"));
                }
            }
        }

        String modificationTestInterval = config.getInitParameter("modificationTestInterval");
        if (modificationTestInterval != null) {
            try {
                this.modificationTestInterval = Integer.parseInt(modificationTestInterval);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.modificationTestInterval"));
                }
            }
        }

        String recompileOnFail = config.getInitParameter("recompileOnFail");
        if (recompileOnFail != null) {
            if (recompileOnFail.equalsIgnoreCase("true")) {
                this.recompileOnFail = true;
            } else if (recompileOnFail.equalsIgnoreCase("false")) {
                this.recompileOnFail = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.recompileOnFail"));
                }
            }
        }
        String development = config.getInitParameter("development");
        if (development != null) {
            if (development.equalsIgnoreCase("true")) {
                this.development = true;
            } else if (development.equalsIgnoreCase("false")) {
                this.development = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.development"));
                }
            }
        }

        String suppressSmap = config.getInitParameter("suppressSmap");
        if (suppressSmap != null) {
            if (suppressSmap.equalsIgnoreCase("true")) {
                isSmapSuppressed = true;
            } else if (suppressSmap.equalsIgnoreCase("false")) {
                isSmapSuppressed = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.suppressSmap"));
                }
            }
        }

        String dumpSmap = config.getInitParameter("dumpSmap");
        if (dumpSmap != null) {
            if (dumpSmap.equalsIgnoreCase("true")) {
                isSmapDumped = true;
            } else if (dumpSmap.equalsIgnoreCase("false")) {
                isSmapDumped = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.dumpSmap"));
                }
            }
        }

        String genCharArray = config.getInitParameter("genStringAsCharArray");
        if (genCharArray != null) {
            if (genCharArray.equalsIgnoreCase("true")) {
                genStringAsCharArray = true;
            } else if (genCharArray.equalsIgnoreCase("false")) {
                genStringAsCharArray = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.genchararray"));
                }
            }
        }

        String errBeanClass =
            config.getInitParameter("errorOnUseBeanInvalidClassAttribute");
        if (errBeanClass != null) {
            if (errBeanClass.equalsIgnoreCase("true")) {
                errorOnUseBeanInvalidClassAttribute = true;
            } else if (errBeanClass.equalsIgnoreCase("false")) {
                errorOnUseBeanInvalidClassAttribute = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.errBean"));
                }
            }
        }

        String ieClassId = config.getInitParameter("ieClassId");
        if (ieClassId != null)
            this.ieClassId = ieClassId;

        String classpath = config.getInitParameter("classpath");
        if (classpath != null)
            this.classpath = classpath;

        /*
         * scratchdir
         */
        String dir = config.getInitParameter("scratchdir");
        if (dir != null && Constants.IS_SECURITY_ENABLED) {
            log.info(Localizer.getMessage("jsp.info.ignoreSetting", "scratchdir", dir));
            dir = null;
        }
        if (dir != null) {
            scratchDir = new File(dir);
        } else {
            // First try the Servlet 2.2 javax.servlet.context.tempdir property
            scratchDir = (File) context.getAttribute(ServletContext.TEMPDIR);
            if (scratchDir == null) {
                // Not running in a Servlet 2.2 container.
                // Try to get the JDK 1.2 java.io.tmpdir property
                dir = System.getProperty("java.io.tmpdir");
                if (dir != null)
                    scratchDir = new File(dir);
            }
        }
        if (this.scratchDir == null) {
            log.fatal(Localizer.getMessage("jsp.error.no.scratch.dir"));
            return;
        }

        if (!(scratchDir.exists() && scratchDir.canRead() &&
                scratchDir.canWrite() && scratchDir.isDirectory()))
            log.fatal(Localizer.getMessage("jsp.error.bad.scratch.dir",
                    scratchDir.getAbsolutePath()));

        this.compiler = config.getInitParameter("compiler");

        String compilerTargetVM = config.getInitParameter("compilerTargetVM");
        if(compilerTargetVM != null) {
            this.compilerTargetVM = compilerTargetVM;
        }

        String compilerSourceVM = config.getInitParameter("compilerSourceVM");
        if(compilerSourceVM != null) {
            this.compilerSourceVM = compilerSourceVM;
        }

        String javaEncoding = config.getInitParameter("javaEncoding");
        if (javaEncoding != null) {
            this.javaEncoding = javaEncoding;
        }

        String compilerClassName = config.getInitParameter("compilerClassName");
        if (compilerClassName != null) {
            this.compilerClassName = compilerClassName;
        }

        String fork = config.getInitParameter("fork");
        if (fork != null) {
            if (fork.equalsIgnoreCase("true")) {
                this.fork = true;
            } else if (fork.equalsIgnoreCase("false")) {
                this.fork = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.fork"));
                }
            }
        }

        String xpoweredBy = config.getInitParameter("xpoweredBy");
        if (xpoweredBy != null) {
            if (xpoweredBy.equalsIgnoreCase("true")) {
                this.xpoweredBy = true;
            } else if (xpoweredBy.equalsIgnoreCase("false")) {
                this.xpoweredBy = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.xpoweredBy"));
                }
            }
        }

        String displaySourceFragment = config.getInitParameter("displaySourceFragment");
        if (displaySourceFragment != null) {
            if (displaySourceFragment.equalsIgnoreCase("true")) {
                this.displaySourceFragment = true;
            } else if (displaySourceFragment.equalsIgnoreCase("false")) {
                this.displaySourceFragment = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.displaySourceFragment"));
                }
            }
        }

        String maxLoadedJsps = config.getInitParameter("maxLoadedJsps");
        if (maxLoadedJsps != null) {
            try {
                this.maxLoadedJsps = Integer.parseInt(maxLoadedJsps);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.maxLoadedJsps", ""+this.maxLoadedJsps));
                }
            }
        }

        String jspIdleTimeout = config.getInitParameter("jspIdleTimeout");
        if (jspIdleTimeout != null) {
            try {
                this.jspIdleTimeout = Integer.parseInt(jspIdleTimeout);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.jspIdleTimeout", ""+this.jspIdleTimeout));
                }
            }
        }

        String strictQuoteEscaping = config.getInitParameter("strictQuoteEscaping");
        if (strictQuoteEscaping != null) {
            if (strictQuoteEscaping.equalsIgnoreCase("true")) {
                this.strictQuoteEscaping = true;
            } else if (strictQuoteEscaping.equalsIgnoreCase("false")) {
                this.strictQuoteEscaping = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.strictQuoteEscaping"));
                }
            }
        }

        String quoteAttributeEL = config.getInitParameter("quoteAttributeEL");
        if (quoteAttributeEL != null) {
            if (quoteAttributeEL.equalsIgnoreCase("true")) {
                this.quoteAttributeEL = true;
            } else if (quoteAttributeEL.equalsIgnoreCase("false")) {
                this.quoteAttributeEL = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.quoteAttributeEL"));
                }
            }
        }

        // 设置这个Web应用的全局Tag Libraries位置.
        tldCache = TldCache.getInstance(context);

        // 为这个Web应用程序设置JSP配置信息.
        jspConfig = new JspConfig(context);

        // Create a Tag plugin instance
        tagPluginManager = new TagPluginManager(context);
    }
}
