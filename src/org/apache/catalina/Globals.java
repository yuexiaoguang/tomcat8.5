package org.apache.catalina;

/**
 * 全局常量，适用于Catalina中的多个包
 */
public final class Globals {

    /**
     * 为这个Web应用程序存储的备用部署描述符的servlet上下文属性
     */
    public static final String ALT_DD_ATTR =
        "org.apache.catalina.deploy.alt_dd";


    /**
     * 请求属性,保存的X509Certificate对象的数组作为客户端提交的证书链.
     */
    public static final String CERTIFICATES_ATTR =
        "javax.servlet.request.X509Certificate";


    /**
     * 请求属性,保存的SSL连接上使用的密码套件的名称(java.lang.String类型).
     */
    public static final String CIPHER_SUITE_ATTR =
        "javax.servlet.request.cipher_suite";


    /**
     * 请求调度器状态.
     */
    public static final String DISPATCHER_TYPE_ATTR =
        "org.apache.catalina.core.DISPATCHER_TYPE";


    /**
     * 请求调度器路径.
     */
    public static final String DISPATCHER_REQUEST_PATH_ATTR =
        "org.apache.catalina.core.DISPATCHER_REQUEST_PATH";


    /**
     * JNDI目录上下文. 此上下文可用于处理静态文件.
     */
    public static final String RESOURCES_ATTR =
        "org.apache.catalina.resources";


    /**
     * servlet上下文属性，保存的应用类加载器路径,
     * 为这个平台使用适当的路径分隔符分隔.
     */
    public static final String CLASS_PATH_ATTR =
        "org.apache.catalina.jsp_classpath";


    /**
     * 请求属性，用于SSL连接的秘钥大小(java.lang.Integer类型)..
     */
    public static final String KEY_SIZE_ATTR =
        "javax.servlet.request.key_size";


    /**
     * 存储用于此SSL连接的会话ID的请求属性(java.lang.String类型).
     */
    public static final String SSL_SESSION_ID_ATTR =
        "javax.servlet.request.ssl_session_id";


    /**
     * 会话管理器的请求属性 key.
     * 这一个是servlet规范的Tomcat扩展.
     */
    public static final String SSL_SESSION_MGR_ATTR =
        "javax.servlet.request.ssl_session_mgr";


    /**
     * 请求属性，将servlet名称存储在指定的调度请求上.
     */
    public static final String NAMED_DISPATCHER_ATTR =
        "org.apache.catalina.NAMED";


    /**
     * servlet上下文属性，存储一个用来标记这个请求已经由SSIServlet处理的标志.
     * 这样做的原因是，当CGIServlet和SSI servlet一起使用时，pathInfo 将损坏.
     */
     public static final String SSI_FLAG_ATTR =
         "org.apache.catalina.ssi.SSIServlet";


    /**
     * AccessControlContext运行的主题.
     */
    public static final String SUBJECT_ATTR =
        "javax.security.auth.subject";


    public static final String GSS_CREDENTIAL_ATTR =
        "org.apache.catalina.realm.GSS_CREDENTIAL";


    /**
     * 请求属性设置为{@code Boolean.TRUE}，如果连接器处理这个请求支持使用sendfile.
     */
    public static final String SENDFILE_SUPPORTED_ATTR =
            org.apache.coyote.Constants.SENDFILE_SUPPORTED_ATTR;


    /**
     * 由servlet使用的请求属性将sendfile提供的文件的名称传递给连接器.
     * 这个值是文件的{@code File.getCanonicalPath()}方法返回值.
     */
    public static final String SENDFILE_FILENAME_ATTR =
            org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR;


    /**
     * 由servlet使用的请求属性将sendfile提供的文件部分的开始偏移量传递给连接器.
     * 值类型是{@code java.lang.Long}. 对于完整的文件，值应该是{@code Long.valueOf(0)}.
     */
    public static final String SENDFILE_FILE_START_ATTR =
            org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR;


    /**
     * 由servlet使用的请求属性将sendfile提供的文件部分的结束偏移量传递给连接器.
     * 值类型是{@code java.lang.Long}. 对于完整的文件，值应等于文件的长度.
     */
    public static final String SENDFILE_FILE_END_ATTR =
            org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR;


    /**
     * 由RemoteIpFilter设置的请求属性, RemoteIpValve 标识连接器，当通过一个或多个代理接收请求时，与此请求关联的远程IP地址.
     * 通常通过X-Forwarded-For HTTP header提供.
     */
    public static final String REMOTE_ADDR_ATTRIBUTE =
            org.apache.coyote.Constants.REMOTE_ADDR_ATTRIBUTE;


    public static final String ASYNC_SUPPORTED_ATTR =
        "org.apache.catalina.ASYNC_SUPPORTED";


    /**
     * 请求属性设置为{@code Boolean.TRUE}，如果在请求参数解析期间忽略了一些请求参数.
     * 这有可能发生，例如，如果有解析的参数总数的限制，或如果参数不能解码，或任何其他错误.
     */
    public static final String PARAMETER_PARSE_FAILED_ATTR =
        "org.apache.catalina.parameter_parse_failed";


    /**
     * 参数解析失败的原因.
     */
    public static final String PARAMETER_PARSE_FAILED_REASON_ATTR =
            "org.apache.catalina.parameter_parse_failed_reason";


    /**
     * 控制严格的servlet规范遵从性的主标志.
     */
    public static final boolean STRICT_SERVLET_COMPLIANCE =
        Boolean.parseBoolean(System.getProperty("org.apache.catalina.STRICT_SERVLET_COMPLIANCE", "false"));


    /**
     * 是否打开安全管理器?
     */
    public static final boolean IS_SECURITY_ENABLED =
        (System.getSecurityManager() != null);


    /**
     * MBeans的默认域名
     */
    public static final String DEFAULT_MBEAN_DOMAIN = "Catalina";


    /**
     * 包含Tomcat产品安装路径的系统属性的名称
     */
    public static final String CATALINA_HOME_PROP = "catalina.home";


    /**
     * 包含Tomcat实例安装路径的系统属性的名称
     */
    public static final String CATALINA_BASE_PROP = "catalina.base";


    /**
     * ServletContext init-param名称，确定JSP 引擎是否应该在解析它们时验证 *.tld 文件.
     * <p>
     * 必须和org.apache.jasper.Constants保持同步
     */
    public static final String JASPER_XML_VALIDATION_TLD_INIT_PARAM =
            "org.apache.jasper.XML_VALIDATE_TLD";


    /**
     * ServletContext init-param名称， 确定JSP 引擎是否将阻塞外部实体，当在 *.tld, *.jspx, *.tagx, tagplugin.xml 文件中使用时.
     * <p>
     * 必须和org.apache.jasper.Constants保持同步
     */
    public static final String JASPER_XML_BLOCK_EXTERNAL_INIT_PARAM =
            "org.apache.jasper.XML_BLOCK_EXTERNAL";

    /**
     * ServletContext 属性名称，保存上下文Realm的 CredentialHandler (如果Realm 和CredentialHandler都存在).
     */
    public static final String CREDENTIAL_HANDLER
            = "org.apache.catalina.CredentialHandler";
}
