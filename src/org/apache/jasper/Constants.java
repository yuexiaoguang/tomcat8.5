package org.apache.jasper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 编译器和运行时使用的一些常量和其他全局数据.
 */
public class Constants {

    /**
     * 所生成的servlet基类.
     */
    public static final String JSP_SERVLET_BASE =
        System.getProperty("org.apache.jasper.Constants.JSP_SERVLET_BASE", "org.apache.jasper.runtime.HttpJspBase");

    /**
     * _jspService 是方法的名称通过HttpJspBase.service()调用. 这是大多数从JSP生成的代码的地方.
     */
    public static final String SERVICE_METHOD_NAME =
        System.getProperty("org.apache.jasper.Constants.SERVICE_METHOD_NAME", "_jspService");

    /**
     * 这些classes/packages 生成代码的时候自动导入. 
     */
    private static final String[] PRIVATE_STANDARD_IMPORTS = {
        "javax.servlet.*",
        "javax.servlet.http.*",
        "javax.servlet.jsp.*"
    };
    public static final List<String> STANDARD_IMPORTS =
        Collections.unmodifiableList(Arrays.asList(PRIVATE_STANDARD_IMPORTS));

    /**
     * classpath的ServletContext 属性. 这是Tomcat特有的. 
     * 如果希望JSP引擎在其上运行，则其他servlet引擎可以选择支持此属性. 
     */
    public static final String SERVLET_CLASSPATH =
        System.getProperty("org.apache.jasper.Constants.SERVLET_CLASSPATH", "org.apache.catalina.jsp_classpath");

    /**
     * JSP缓冲区的默认大小
     */
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    /**
     * 标签缓冲区的默认大小.
     */
    public static final int DEFAULT_TAG_BUFFER_SIZE = 512;

    /**
     * 默认标签处理程序池大小.
     */
    public static final int MAX_POOL_SIZE = 5;

    /**
     * 查询参数，JSP引擎只是预先生成servlet, 但不调用它. 
     */
    public static final String PRECOMPILE =
        System.getProperty("org.apache.jasper.Constants.PRECOMPILE", "jsp_precompile");

    /**
     * 已编译JSP页面的默认包名.
     */
    public static final String JSP_PACKAGE_NAME =
        System.getProperty("org.apache.jasper.Constants.JSP_PACKAGE_NAME", "org.apache.jsp");

    /**
     * 标签文件生成的标签处理程序的默认包名
     */
    public static final String TAG_FILE_PACKAGE_NAME =
        System.getProperty("org.apache.jasper.Constants.TAG_FILE_PACKAGE_NAME", "org.apache.jsp.tag");

    /**
     * 下载Netscape和IE插件的默认 URL.
     */
    public static final String NS_PLUGIN_URL =
        "http://java.sun.com/products/plugin/";

    public static final String IE_PLUGIN_URL =
        "http://java.sun.com/products/plugin/1.2.2/jinstall-1_2_2-win.cab#Version=1,2,2,0";

    /**
     * 用于生成临时变量名的前缀
     */
    public static final String TEMP_VARIABLE_NAME_PREFIX =
        System.getProperty("org.apache.jasper.Constants.TEMP_VARIABLE_NAME_PREFIX", "_jspx_temp");

    /**
     * 安全已经开启?
     */
    public static final boolean IS_SECURITY_ENABLED =
        (System.getSecurityManager() != null);

    public static final boolean USE_INSTANCE_MANAGER_FOR_TAGS =
        Boolean.parseBoolean(System.getProperty("org.apache.jasper.Constants.USE_INSTANCE_MANAGER_FOR_TAGS", "false"));

    /**
     * 用于与客户端来回传递会话标识符的path参数的名称.
     */
    public static final String SESSION_PARAMETER_NAME =
        System.getProperty("org.apache.catalina.SESSION_PARAMETER_NAME",
                "jsessionid");

    /**
     * 包含Tomcat产品安装路径的系统属性的名称
     */
    public static final String CATALINA_HOME_PROP = "catalina.home";


    /**
     * ServletContext init-param的名称, 确定 XML解析器是否验证 *.tld 文件.
     * <p>
     * 这必须和 org.apache.catalina.Globals保持同步
     */
    public static final String XML_VALIDATION_TLD_INIT_PARAM =
            "org.apache.jasper.XML_VALIDATE_TLD";

    /**
     * ServletContext init-param的名称, 确定 XML解析器将阻塞外部实体的解析.
     * <p>
     * 这必须和 org.apache.catalina.Globals保持同步
     */
    public static final String XML_BLOCK_EXTERNAL_INIT_PARAM =
            "org.apache.jasper.XML_BLOCK_EXTERNAL";
}
