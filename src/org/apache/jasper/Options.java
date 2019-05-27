package org.apache.jasper;

import java.io.File;
import java.util.Map;

import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;

/**
 * 用于保存特定于JSP引擎的所有init参数. 
 */
public interface Options {

    /**
     * 返回 true, 如果Jasper发生编译错误而不是运行错误.
     * 初始化错误，如果useBean行为中指定的类属性是无效的.
     * 
     * @return <code>true</code> to get an error
     */
    public boolean getErrorOnUseBeanInvalidClassAttribute();

    /**
     * @return <code>true</code>保留生成的源
     */
    public boolean getKeepGenerated();

    /**
     * @return true, 如果启用了标记处理程序池, 否则返回false.
     */
    public boolean isPoolingEnabled();

    /**
     * @return <code>true</code>支持HTML映射servlet?
     */
    public boolean getMappedFile();

    /**
     * @return <code>true</code>如果在编译类中包含调试信息.
     */
    public boolean getClassDebugInfo();

    /**
     * @return 后台编译线程检查间隔, 以秒为单位
     */
    public int getCheckInterval();

    /**
     * 启用详细的错误报告, 以及JSP和tag文件的自动重新编译.
     * 这个设置应该通常是<code>false</code>, 在生产环境运行时.
     * 
     * @return <code>true</code>如果Jasper在开发模式中
     */
    public boolean getDevelopment();

    /**
     * @return <code>true</code>在异常消息中包含源片段.
     */
    public boolean getDisplaySourceFragment();

    /**
     * @return <code>true</code>阻止JSR45调试的SMAP信息的生成.
     */
    public boolean isSmapSuppressed();

    /**
     * 这个设置将被忽略，如果 suppressSmap() 是 <code>true</code>.
     * 
     * @return <code>true</code>写入 SMAP信息到一个文件.
     */
    public boolean isSmapDumped();

    /**
     * @return <code>true</code>删除完全由空格组成的模板文本
     */
    public boolean getTrimSpaces();

    /**
     * 获取发送到Internet Explorer的 class-id 值, 当使用 &lt;jsp:plugin&gt; 标签时.
     * @return Class-id value
     */
    public String getIeClassId();

    /**
     * @return 工作目录
     */
    public File getScratchDir();

    /**
     * @return 编译JSP文件生成的servlet时使用的 classpath
     */
    public String getClassPath();

    /**
     * 使用的编译器.
     *
     * <p>
     * 如果是<code>null</code> (默认的), Eclipse JDT项目的Java编译器, 与Tomcat捆绑, 将被使用.
     * 否则, Apache Ant的<code>javac</code>任务将用于调用外部java编译器，此选项的值将被传递给它.
     */
    public String getCompiler();

    /**
     * @return 编译器目标 VM, e.g. 1.8.
     */
    public String getCompilerTargetVM();

    /**
     * @return 编译器源 VM, e.g. 1.8.
     */
    public String getCompilerSourceVM();

    /**
     * @return 要使用的Jasper Java编译器类.
     */
    public String getCompilerClassName();

    /**
     * 映射URI的缓存, Web应用程序暴露的各种标签库的资源路径和解析的TLD文件.
     * 标记库在web.xml中显式地或隐式地“暴露”, 通过jar文件中部署的taglib的TLD的 uri标签 (WEB-INF/lib).
     *
     * @return Web应用的 TldLocationsCache的实例
     */
    public TldCache getTldCache();

    /**
     * @return java平台的编码生成的JSP页面servlet.
     */
    public String getJavaEncoding();

    /**
     * 告诉Ant JSP 页面是否被编辑.
     *
     * <p>
     * 仅当Jasper 使用一个外部的java编译器 (通过一个<code>javac</code> Apache Ant任务包装).
     * @return <code>true</code> to fork a process during compilation
     */
    public boolean getFork();

    /**
     * @return 在web.xml中指定的JSP的配置信息.  
     */
    public JspConfig getJspConfig();

    /**
     * @return <code>true</code>生成 X-Powered-By 响应头
     */
    public boolean isXpoweredBy();

    /**
     * @return a Tag Plugin Manager
     */
    public TagPluginManager getTagPluginManager();

    /**
     * 是否是生成字符数组的文本字符串.
     *
     * @return <code>true</code>如果文本字符串将被生成为字符数组, 否则<code>false</code>
     */
    public boolean genStringAsCharArray();

    /**
     * @return 修改测试间隔.
     */
    public int getModificationTestInterval();


    /**
     * @return <code>true</code>如果重新编译将在失败时发生.
     */
    public boolean getRecompileOnFail();

    /**
     * @return <code>true</code>是否启用缓存(用于预编译).
     */
    public boolean isCaching();

    /**
     * 通过TagLibraryInfoImpl.parseTLD中的parseXMLDocument返回的TreeNode的web应用范围内的缓存,
     * 如果{@link #isCaching()}返回true.
     *
     * <p>
     * 使用此缓存避免了重复解析标记库描述符XML文件的成本(通过执行 TagLibraryInfoImpl.parseTLD).
     * </p>
     *
     * @return the Map(String uri, TagLibraryInfo tld) instance.
     */
    public Map<String, TagLibraryInfo> getCache();

    /**
     * 每个Web应用程序加载的JSP的最大数量. 如果有更多的JSP加载, 它们将被卸载. 如果未设置或小于0, 不需要卸载jsp.
     * @return The JSP count
     */
    public int getMaxLoadedJsps();

    /**
     * @return JSP卸载后几秒钟的空闲时间. 如果未设置或小于0, 不需要卸载jsp.
     */
    public int getJspIdleTimeout();

    /**
     * @return {@code true}如果JSP规范的JSP 1.6部分所需的引号转义应该用于脚本表达式.
     */
    public boolean getStrictQuoteEscaping();

    /**
     * @return {@code true}如果在属性中使用的EL表达式应该有引用规则在JSP 1.6应用表达式中.
     */
    public boolean getQuoteAttributeEL();
}
