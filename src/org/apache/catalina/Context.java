package org.apache.catalina;

import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletSecurityElement;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.tomcat.ContextBind;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.http.CookieProcessor;

/**
 * <b>Context</b>是一个容器，表示一个servlet上下文, 即在Catalina servlet引擎中一个单独的Web应用程序.
 * 因此，它几乎在每个Catalina部署中都是有用的(即使一个Connector连接到一个Web服务器,如Apache Web服务器)
 * 使用Web服务器的工具来识别适当的Wrapper来处理此请求
 * 它还提供了一个方便的机制使用拦截器，查看由这个特定Web应用程序处理的每个请求.
 * <p>
 * 附加到上下文的父Container通常是一个Host，也可能是一些其他实现，而且如果没有必要，可以省略
 * <p>
 * 附加在上下文中的子容器通常是Wrapper的实现（表示单个servlet定义）
 * <p>
 */
public interface Context extends Container, ContextBind {


    // ----------------------------------------------------- Manifest Constants

    /**
     * 添加欢迎文件的容器事件.
     */
    public static final String ADD_WELCOME_FILE_EVENT = "addWelcomeFile";

    /**
     * 删除一个wrapper的容器事件.
     */
    public static final String REMOVE_WELCOME_FILE_EVENT = "removeWelcomeFile";

    /**
     * 清除欢迎文件的容器事件.
     */
    public static final String  CLEAR_WELCOME_FILES_EVENT = "clearWelcomeFiles";

    /**
     * 更改会话ID的容器事件.
     */
    public static final String CHANGE_SESSION_ID_EVENT = "changeSessionId";


    // ------------------------------------------------------------- Properties

    /**
     * 返回<code>true</code>， 如果请求映射到servlet， 不包括"multipart config"解析 multipart/form-data 请求.
     */
    public boolean getAllowCasualMultipartParsing();


   /**
     * 设置为<code>true</code>，允许请求映射到servlet， 不要显式声明 @MultipartConfig 或有&lt;multipart-config&gt;
     * 在web.xml中指定来解析multipart/form-data 请求.
     *
     * @param allowCasualMultipartParsing <code>true</code>允许这样的随意分析, 否则<code>false</code>.
     */
    public void setAllowCasualMultipartParsing(boolean allowCasualMultipartParsing);


    /**
     * 返回初始化的应用程序监听器对象集合, 按照在Web应用程序部署描述符中指定的顺序.
     */
    public Object[] getApplicationEventListeners();


    /**
     * 保存初始化应用监听器的对象集合,按照在Web应用程序部署描述符中指定的顺序.
     *
     * @param listeners 实例化的监听器对象集合.
     */
    public void setApplicationEventListeners(Object listeners[]);


    /**
     * 返回初始化的应用程序生命周期监听器对象集合, 按照在Web应用程序部署描述符中指定的顺序
     */
    public Object[] getApplicationLifecycleListeners();


    /**
     * 保存初始化应用程序生命周期监听器的对象集合,按照在Web应用程序部署描述符中指定的顺序.
     *
     * @param listeners 实例化的监听器对象集合.
     */
    public void setApplicationLifecycleListeners(Object listeners[]);


    /**
     * 获取与给定区域设置一起使用的字符集名称.
     * 注意，不同的上下文可能有不同的区域设置映射到字符集.
     *
     * @param locale 应返回映射字符集的区域设置
     */
    public String getCharset(Locale locale);


    /**
     * 返回此上下文的XML描述符的URL.
     */
    public URL getConfigFile();


    /**
     * 为这个上下文设置XML描述符的URL.
     *
     * @param configFile 此上下文的XML描述符的URL.
     */
    public void setConfigFile(URL configFile);


    /**
     * 返回是否“正确配置”的标志.
     */
    public boolean getConfigured();


    /**
     * 设置是否“正确配置”的标志。  可以通过启动监听器设置为false，为了避免使用中的应用检测到致命的配置错误.
     *
     * @param configured 正确配置标志
     */
    public void setConfigured(boolean configured);


    /**
     * 返回“使用cookie作为会话ID”标志.
     *
     * @return <code>true</code> 如果允许使用cookie跟踪此Web应用程序的会话ID, 否则 <code>false</code>
     */
    public boolean getCookies();


    /**
     * 设置“使用cookie作为会话ID”标志.
     *
     * @param cookies The new flag
     */
    public void setCookies(boolean cookies);


    /**
     * 获取用于会话cookie的名称. 覆盖应用程序可能指定的任何设置.
     *
     * @return  默认会话cookie名称的值或NULL
     */
    public String getSessionCookieName();


    /**
     * 设置用于会话cookie的名称. 覆盖应用程序可能指定的任何设置.
     *
     * @param sessionCookieName   要使用的名称
     */
    public void setSessionCookieName(String sessionCookieName);


    /**
     *返回是否为会话cookie使用HttpOnly cookie标志.
     *
     * @return <code>true</code>如果HttpOnly标志应该在会话cookie上设置
     */
    public boolean getUseHttpOnly();


    /**
     * 设置是否为会话cookie使用HttpOnly cookie标志.
     *
     * @param useHttpOnly   <code>true</code>在会话cookie上使用 HttpOnly cookie
     */
    public void setUseHttpOnly(boolean useHttpOnly);


    /**
     * 获取用于会话cookie的域名. 覆盖应用程序可能指定的任何设置.
     *
     * @return  默认会话cookie域名或NULL
     */
    public String getSessionCookieDomain();


    /**
     * 设置用于会话cookie的域名. 覆盖应用程序可能指定的任何设置.
     *
     * @param sessionCookieDomain   要使用的域名
     */
    public void setSessionCookieDomain(String sessionCookieDomain);


    /**
     * 获取用于会话cookie的路径. 覆盖应用程序可能指定的任何设置.
     *
     * @return  默认会话cookie路径或null
     */
    public String getSessionCookiePath();


    /**
     * 设置用于会话cookie的路径. 覆盖应用程序可能指定的任何设置.
     *
     * @param sessionCookiePath   要使用的路径
     */
    public void setSessionCookiePath(String sessionCookiePath);


    /**
     * Configures if a / is added to the end of the session cookie path to
     * ensure browsers, particularly IE, don't send a session cookie for context
     * /foo with requests intended for context /foobar.
     *
     * @return <code>true</code>如果加斜线, 否则<code>false</code>
     */
    public boolean getSessionCookiePathUsesTrailingSlash();


    /**
     * Configures if a / is added to the end of the session cookie path to
     * ensure browsers, particularly IE, don't send a session cookie for context
     * /foo with requests intended for context /foobar.
     *
     * @param sessionCookiePathUsesTrailingSlash   <code>true</code>如果加斜线, 否则<code>false</code>
     */
    public void setSessionCookiePathUsesTrailingSlash(
            boolean sessionCookiePathUsesTrailingSlash);


    /**
     * 返回“允许交叉servlet上下文”标志
     *
     * @return <code>true</code>如果该Web应用程序允许交叉请求, 否则<code>false</code>
     */
    public boolean getCrossContext();


    /**
     * 返回备用部署描述符名称.
     */
    public String getAltDDName();


    /**
     * 设置备用部署描述符名称.
     *
     * @param altDDName 名称
     */
    public void setAltDDName(String altDDName) ;


    /**
     * 设置“允许交叉servlet上下文”标志.
     *
     * @param crossContext The new cross contexts flag
     */
    public void setCrossContext(boolean crossContext);


    /**
     * 返回这个应用的deny-uncovered-http-methods（拒绝公开的HTTP方法） 标志.
     */
    public boolean getDenyUncoveredHttpMethods();


    /**
     * 设置这个应用的deny-uncovered-http-methods（拒绝公开的HTTP方法） 标志.
     *
     * @param denyUncoveredHttpMethods The new deny-uncovered-http-methods flag
     */
    public void setDenyUncoveredHttpMethods(boolean denyUncoveredHttpMethods);


    /**
     * 返回此Web应用程序的显示名称
     */
    public String getDisplayName();


    /**
     * 设置此Web应用程序的显示名称
     *
     * @param displayName 显示名称
     */
    public void setDisplayName(String displayName);


    /**
     * 返回该Web应用程序的发布标志.
     */
    public boolean getDistributable();


    /**
     * 设置该Web应用程序的发布标志.
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable);


    /**
     * 返回此上下文的文档根目录。这可以是绝对路径，相对路径，或一个URL.
     */
    public String getDocBase();


    /**
     * 设置此上下文的文档根目录。这可以是绝对路径，相对路径，或一个URL.
     *
     * @param docBase 文档根目录
     */
    public void setDocBase(String docBase);


    /**
     * 返回URL编码的上下文路径, 使用UTF-8.
     */
    public String getEncodedPath();


    /**
     * 确定注解解析当前是否已禁用
     *
     * @return {@code true}如果禁用了注解解析
     */
    public boolean getIgnoreAnnotations();


    /**
     * 是否禁用注解解析.
     *
     * @param ignoreAnnotations 是否禁用注解解析
     */
    public void setIgnoreAnnotations(boolean ignoreAnnotations);


    /**
     * @return 返回此Web应用程序的登录配置
     */
    public LoginConfig getLoginConfig();


    /**
     * 设置此Web应用程序的登录配置.
     *
     * @param config 登录配置
     */
    public void setLoginConfig(LoginConfig config);


    /**
     * 返回与此Web应用程序相关联的命名资源.
     */
    public NamingResourcesImpl getNamingResources();


    /**
     *  设置与此Web应用程序相关联的命名资源
     *
     * @param namingResources 命名资源
     */
    public void setNamingResources(NamingResourcesImpl namingResources);


    /**
     * 返回此Web应用程序的上下文路径.
     */
    public String getPath();


    /**
     * 设置此Web应用程序的上下文路径
     *
     * @param path 上下文路径
     */
    public void setPath(String path);


    /**
     * 返回当前正在解析的部署描述符DTD的公共标识符
     */
    public String getPublicId();


    /**
     * 设置当前正在解析的部署描述符DTD的公共标识符
     *
     * @param publicId 公共标识符
     */
    public void setPublicId(String publicId);


    /**
     * 返回是否可以重载的标识.
     */
    public boolean getReloadable();


    /**
     * 设置是否可以重载的标识.
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable);


    /**
     * 返回此Web应用程序的覆盖标志.
     */
    public boolean getOverride();


    /**
     * 设置此Web应用程序的覆盖标志.
     *
     * @param override The new override flag
     */
    public void setOverride(boolean override);


    /**
     * 返回此Web应用程序的特权标志
     */
    public boolean getPrivileged();


    /**
     * 设置此Web应用程序的特权标志.
     *
     * @param privileged The new privileged flag
     */
    public void setPrivileged(boolean privileged);


    /**
     * 返回servlet上下文， 这个上下文是一个外观模式.
     */
    public ServletContext getServletContext();


    /**
     * 返回此Web应用程序的默认会话超时（分钟）.
     */
    public int getSessionTimeout();


    /**
     * 设置此Web应用程序的默认会话超时（分钟）
     *
     * @param timeout 默认超时时间
     */
    public void setSessionTimeout(int timeout);


    /**
     * 返回<code>true</code>， 如果剩余请求数据将被读取（吞下），即使请求违反了数据大小约束.
     *
     * @return <code>true</code>如果数据将被吞掉(default), 否则<code>false</code>.
     */
    public boolean getSwallowAbortedUploads();


    /**
     * 设置为<code>false</code>禁用请求数据吞掉，由于大小限制，上传失败后.
     *
     * @param swallowAbortedUploads <code>false</code>禁用吞掉, 否则<code>true</code>(default).
     */
    public void setSwallowAbortedUploads(boolean swallowAbortedUploads);

    /**
     * 返回swallowOutput 标志的值.
     */
    public boolean getSwallowOutput();


    /**
     * 设置swallowOutput 的值.
     * 如果设置为true, system.out和system.err 将在servlet执行期间重定向到记录器.
     *
     * @param swallowOutput The new value
     */
    public void setSwallowOutput(boolean swallowOutput);


    /**
     * 获取用于在这个Context注册的servlet的Wrapper实现类的Java类名.
     */
    public String getWrapperClass();


    /**
     * 设置用于在这个Context注册的servlet的Wrapper实现类的Java类名.
     *
     * @param wrapperClass The new wrapper class
     */
    public void setWrapperClass(String wrapperClass);


    /**
     * web.xml 和 web-fragment.xml文件的解析是否由命名空间感知解析器执行?
     *
     * @return true 如果启用命名空间感知.
     */
    public boolean getXmlNamespaceAware();


    /**
     * web.xml 和 web-fragment.xml文件的解析是否由命名空间感知解析器执行.
     *
     * @param xmlNamespaceAware true启用命名空间感知
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware);


    /**
     * web.xml 和 web-fragment.xml文件的解析是否由验证解析器执行?
     *
     * @return true 如果启用验证.
     */
    public boolean getXmlValidation();


    /**
     * web.xml 和 web-fragment.xml文件的解析是否由验证解析器执行.
     *
     * @param xmlValidation true启用xml验证
     */
    public void setXmlValidation(boolean xmlValidation);


    /**
     * web.xml, web-fragment.xml, *.tld, *.jspx, *.tagx 和tagplugin.xml文件的解析是否阻塞外部实体的使用?
     *
     * @return true 阻塞外部实体的访问
     */
    public boolean getXmlBlockExternal();


    /**
     * web.xml, web-fragment.xml, *.tld, *.jspx, *.tagx 和tagplugin.xml文件的解析是否阻塞外部实体的使用.
     *
     * @param xmlBlockExternal true 阻塞外部实体的访问
     */
    public void setXmlBlockExternal(boolean xmlBlockExternal);


    /**
     * *.tld文件的解析是否由验证解析器执行?
     *
     * @return true 启用验证.
     */
    public boolean getTldValidation();


    /**
     * *.tld文件的解析是否由验证解析器执行.
     *
     * @param tldValidation true 启用xml验证
     */
    public void setTldValidation(boolean tldValidation);


    /**
     * 获取JAR扫描器用于扫描JAR资源.
     */
    public JarScanner getJarScanner();

    /**
     * 设置JAR扫描器用于扫描JAR资源.
     * 
     * @param jarScanner    JAR扫描器.
     */
    public void setJarScanner(JarScanner jarScanner);

    /**
     * 获取这个上下文使用的{@link Authenticator}，或<code>null</code>.
     */
    public Authenticator getAuthenticator();

    /**
     * 设置有效的web.xml 是否应该在上下文启动的时候记录.
     *
     * @param logEffectiveWebXml <code>true</code>记录完整的web.xml
     */
    public void setLogEffectiveWebXml(boolean logEffectiveWebXml);

    /**
     * 有效的web.xml 是否应该在上下文启动的时候记录?
     */
    public boolean getLogEffectiveWebXml();

    /**
     * 关联的实例管理器.
     */
    public InstanceManager getInstanceManager();

    /**
     * 关联的实例管理器.
     *
     * @param instanceManager 实例管理器
     */
    public void setInstanceManager(InstanceManager instanceManager);

    /**
     * 设置正则表达式， 提供SCI的容器应该被过滤出去，不能用于这个上下文.
     * 匹配使用{@link java.util.regex.Matcher#find()}， 因此，正则表达式只需匹配提供SCI的容器的完全限定类名的子字符串，以便将其过滤掉.
     *
     * @param containerSciFilter 应检查每个提供SCI的容器的完全限定类名的正则表达式
     */
    public void setContainerSciFilter(String containerSciFilter);

    /**
     * 获取正则表达式， 提供SCI的容器应该被过滤出去，不能用于这个上下文.
     * 匹配使用{@link java.util.regex.Matcher#find()}， 因此，正则表达式只需匹配提供SCI的容器的完全限定类名的子字符串，以便将其过滤掉.
     */
    public String getContainerSciFilter();


    // --------------------------------------------------------- Public Methods

    /**
     * 添加一个监听器类名到配置的监听器集合中.
     *
     * @param listener 监听器Java类名
     */
    public void addApplicationListener(String listener);


    /**
     * 添加应用参数.
     *
     * @param parameter 应用参数
     */
    public void addApplicationParameter(ApplicationParameter parameter);


    /**
     * 添加一个安全约束.
     *
     * @param constraint 安全约束
     */
    public void addConstraint(SecurityConstraint constraint);


    /**
     * 为指定的错误或Java异常添加一个错误页面.
     *
     * @param errorPage 错误页面
     */
    public void addErrorPage(ErrorPage errorPage);


    /**
     * 在此上下文中添加过滤器定义.
     *
     * @param filterDef 过滤器定义
     */
    public void addFilterDef(FilterDef filterDef);


    /**
     * 添加一个过滤器映射.
     *
     * @param filterMap 过滤器映射
     */
    public void addFilterMap(FilterMap filterMap);

    /**
     * 添加过滤器映射到这个Context， 在部署描述符中定义的映射之前， 但在通过此方法添加任何其他映射之后.
     *
     * @param filterMap 要添加的过滤器映射
     *
     * @exception IllegalArgumentException 如果指定的过滤器名称与现有过滤器定义不匹配, 或者过滤器映射是错误的
     */
    public void addFilterMapBefore(FilterMap filterMap);


    /**
     * 添加区域编码映射(see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale 要映射编码的区域
     * @param encoding 用于给定区域设置的编码
     */
    public void addLocaleEncodingMappingParameter(String locale, String encoding);


    /**
     * 添加MIME 映射, 将指定的扩展名替换为现有映射.
     *
     * @param extension 映射的文件名扩展名
     * @param mimeType 对应的MIME 类型
     */
    public void addMimeMapping(String extension, String mimeType);


    /**
     * 添加一个新的上下文初始化参数, 替换指定名称的任何现有值.
     *
     * @param name 参数名
     * @param value 参数值
     */
    public void addParameter(String name, String value);


    /**
     * 添加安全角色引用
     *
     * @param role 应用程序中使用的安全角色
     * @param link 实际要检查的安全角色
     */
    public void addRoleMapping(String role, String link);


    /**
     * 添加一个新的安全角色.
     *
     * @param role New security role
     */
    public void addSecurityRole(String role);


    /**
     * 添加一个新的servlet映射，以替换指定模式的所有现有映射.
     *
     * @param pattern 要映射的URL模式. 模式将使用UTF-8解码
     * @param name    要执行的对应servlet的名称
     *
     * @deprecated Will be removed in Tomcat 9. Use
     *             {@link #addServletMappingDecoded(String, String)}
     */
    @Deprecated
    public void addServletMapping(String pattern, String name);


    /**
     * 添加一个新的servlet映射，以替换指定模式的所有现有映射.
     *
     * @param pattern     要映射的URL模式. 模式将使用UTF-8解码
     * @param name        要执行的对应servlet的名称
     * @param jspWildcard true 如果名称标识JspServlet，而且模式包含通配符; 否则false
     *
     * @deprecated Will be removed in Tomcat 9. Use
     *             {@link #addServletMappingDecoded(String, String, boolean)}
     */
    @Deprecated
    public void addServletMapping(String pattern, String name, boolean jspWildcard);


    /**
     * 添加一个新的servlet映射，以替换指定模式的所有现有映射.
     *
     * @param pattern 要映射的URL 模式
     * @param name 要执行的对应servlet的名称
     */
    public void addServletMappingDecoded(String pattern, String name);


    /**
     * 添加一个新的servlet映射，以替换指定模式的所有现有映射.
     *
     * @param pattern 要映射的URL 模式
     * @param name 要执行的对应servlet的名称
     * @param jspWildcard true如果名称标识JspServlet，而且模式包含通配符; 否则false
     */
    public void addServletMappingDecoded(String pattern, String name,
            boolean jspWildcard);


    /**
     * 添加监视的资源，将由主机自动部署器重新加载. Note: 这不会在嵌入模式下使用.
     *
     * @param name 资源路径, 相对于docBase
     */
    public void addWatchedResource(String name);


    /**
     * 向该上下文识别的集合添加一个新的欢迎文件
     *
     * @param name 新的欢迎文件名称
     */
    public void addWelcomeFile(String name);


    /**
     * 添加LifecycleListener类名.
     *
     * @param listener LifecycleListener 类的Java类名
     */
    public void addWrapperLifecycle(String listener);


    /**
     * 添加ContainerListener类名.
     *
     * @param listener ContainerListener类的Java类名
     */
    public void addWrapperListener(String listener);


    /**
     * 创建并返回一个Wrapper实例的工厂方法, Context适当的实现类.
     * 初始化的Wrapper构造方法将被调用, 但没有设置属性.
     */
    public Wrapper createWrapper();


    /**
     * 返回配置的应用监听器类名集合.
     */
    public String[] findApplicationListeners();


    /**
     * 返回应用参数集合.
     */
    public ApplicationParameter[] findApplicationParameters();


    /**
     * 返回此Web应用程序的安全约束集合。如果没有，则返回零长度数组.
     */
    public SecurityConstraint[] findConstraints();


    /**
     * 返回指定HTTP错误代码的错误页面；如果没有，返回<code>null</code>.
     *
     * @param errorCode 查找的异常状态码
     */
    public ErrorPage findErrorPage(int errorCode);


    /**
     * 返回指定Java异常类型的错误页面；如果没有，返回<code>null</code>.
     *
     * @param exceptionType 查找的异常类型
     */
    public ErrorPage findErrorPage(String exceptionType);



    /**
     * 返回所有指定的错误代码和异常类型的定义错误页面集合.
     */
    public ErrorPage[] findErrorPages();


    /**
     * 返回指定名称的过滤器;如果没有，返回 <code>null</code>.
     *
     * @param filterName 要查找的过滤器名称
     */
    public FilterDef findFilterDef(String filterName);


    /**
     * 返回所有的过滤器
     */
    public FilterDef[] findFilterDefs();


    /**
     * 返回所有过滤器映射集合.
     */
    public FilterMap[] findFilterMaps();


    /**
     * @return 映射到指定扩展名的MIME类型; 否则返回<code>null</code>.
     *
     * @param extension 映射到MIME类型的扩展名
     */
    public String findMimeMapping(String extension);


    /**
     * @return 定义MIME映射的扩展名. 或者返回零长度数组.
     */
    public String[] findMimeMappings();


    /**
     * @return 指定的上下文初始化参数名称的值; 否则返回<code>null</code>.
     *
     * @param name 要返回的参数的名称
     */
    public String findParameter(String name);


    /**
     * @return 所有定义的上下文初始化参数的名称. 或者返回零长度数组.
     */
    public String[] findParameters();


    /**
     * 对于给定的安全角色（应用程序所使用的安全角色），如果有一个角色，返回相应的角色名称（由基础域定义）。否则，返回指定的角色不变.
     *
     * @param role 要映射的安全角色
     * @return 映射到指定角色的角色名
     */
    public String findRoleMapping(String role);


    /**
     * 如果指定的安全角色被定义，返回 <code>true</code>;否则返回 <code>false</code>.
     *
     * @param role 安全角色
     */
    public boolean findSecurityRole(String role);


    /**
     * 返回为该应用程序定义的安全角色。如果没有，则返回零长度数组.
     */
    public String[] findSecurityRoles();


    /**
     * @return 指定模式映射的servlet名称; 否则返回<code>null</code>.
     *
     * @param pattern 请求映射的模式
     */
    public String findServletMapping(String pattern);


    /**
     * @return 所有定义的servlet映射的模式. 如果没有定义映射，则返回零长度数组.
     */
    public String[] findServletMappings();


    /**
     * @return 指定HTTP状态码的错误页面的上下文相对URI; 否则返回<code>null</code>.
     *
     * @param status 要查找的HTTP状态码
     */
    public String findStatusPage(int status);


    /**
     * @return 已指定错误页面的HTTP状态码. 如果没有指定，则返回零长度数组.
     */
    public int[] findStatusPages();


    /**
     * @return 关联的ThreadBindingListener.
     */
    public ThreadBindingListener getThreadBindingListener();


    /**
     * 关联的ThreadBindingListener.
     *
     * @param threadBindingListener 设置在进入和退出应用程序范围时接收通知的监听器
     */
    public void setThreadBindingListener(ThreadBindingListener threadBindingListener);


    /**
     * 返回所有被监视的资源. 如果没有，则返回零长度数组
     */
    public String[] findWatchedResources();


    /**
     * 如果指定的欢迎文件被指定，返回<code>true</code>; 否则，返回<code>false</code>..
     *
     * @param name Welcome file to verify
     */
    public boolean findWelcomeFile(String name);


    /**
     * 返回为此上下文定义的欢迎文件集合。如果没有，则返回零长度数组.
     */
    public String[] findWelcomeFiles();


    /**
     * 返回LifecycleListener类名集合.
     */
    public String[] findWrapperLifecycles();


    /**
     * 返回ContainerListener类名集合.
     */
    public String[] findWrapperListeners();


    /**
     * 通知所有的{@link javax.servlet.ServletRequestListener}，请求已经开始.
     *
     * @param request 将传递给监听器的请求对象
     * @return <code>true</code> 如果监听器成功触发, 否则<code>false</code>
     */
    public boolean fireRequestInitEvent(ServletRequest request);

    /**
     * 通知所有的{@link javax.servlet.ServletRequestListener}，请求已经结束.
     *
     * @param request 将传递给监听器的请求对象
     * @return <code>true</code>如果监听器成功触发, 否则<code>false</code>
     */
    public boolean fireRequestDestroyEvent(ServletRequest request);

    /**
     * 如果支持重新加载，则重新加载此Web应用程序.
     *
     * @exception IllegalStateException 如果<code>reloadable</code>属性被设置为<code>false</code>.
     */
    public void reload();


    /**
     * 从这个应用程序的监听器集合中删除指定的应用程序监听器类.
     *
     * @param listener 要删除的监听器的Java 类名
     */
    public void removeApplicationListener(String listener);


    /**
     * 从该应用程序的集合中移除具有指定名称的应用程序参数.
     *
     * @param name 要删除的应用程序参数的名称
     */
    public void removeApplicationParameter(String name);


    /**
     * 从这个Web应用程序中移除指定的安全约束.
     *
     * @param constraint 要删除的约束
     */
    public void removeConstraint(SecurityConstraint constraint);


    /**
     * 移除指定错误编码或Java异常对应的错误页面；如果没有，什么都不做.
     *
     * @param errorPage 要删除的错误页面定义
     */
    public void removeErrorPage(ErrorPage errorPage);


    /**
     * 移除指定过滤器定义;如果没有，什么都不做.
     *
     * @param filterDef 要删除的过滤器定义
     */
    public void removeFilterDef(FilterDef filterDef);


    /**
     * 删除过滤器器映射.
     *
     * @param filterMap 要删除的筛选器映射
     */
    public void removeFilterMap(FilterMap filterMap);


    /**
     * 删除指定扩展名的MIME映射.
     *
     * @param extension 扩展名
     */
    public void removeMimeMapping(String extension);


    /**
     * 删除指定的名称的上下文初始化参数.
     *
     * @param name 要移除的参数的名称
     */
    public void removeParameter(String name);


    /**
     * 删除指定名称的任何安全角色引用
     *
     * @param role 安全角色
     */
    public void removeRoleMapping(String role);


    /**
     * 删除指定名称的安全角色.
     *
     * @param role 要删除的安全角色
     */
    public void removeSecurityRole(String role);


    /**
     * 删除指定模式的任何servlet映射.
     *
     * @param pattern 要删除的映射的URL模式
     */
    public void removeServletMapping(String pattern);


    /**
     * 从列表中删除指定的受监视资源名称.
     *
     * @param name 要删除的被监视资源的名称
     */
    public void removeWatchedResource(String name);


    /**
     * 从列表中删除指定的欢迎文件名.
     *
     * @param name 要删除的欢迎文件的名称
     */
    public void removeWelcomeFile(String name);


    /**
     * 从LifecycleListener类名集合中删除指定的类名.
     *
     * @param listener LifecycleListener类的类名
     */
    public void removeWrapperLifecycle(String listener);


    /**
     * 从ContainerListener类名集合中删除指定的类名.
     *
     * @param listener ContainerListener类的类名
     */
    public void removeWrapperListener(String listener);


    /**
     * @return 给定虚拟路径的实际路径; 否则返回<code>null</code>.
     *
     * @param path 所需资源的路径
     */
    public String getRealPath(String path);


    /**
     * @return servlet规范的有效主版本.
     */
    public int getEffectiveMajorVersion();


    /**
     * 设置servlet规范的有效主版本.
     *
     * @param major 设置版本号
     */
    public void setEffectiveMajorVersion(int major);


    /**
     * @return servlet规范的有效次要版本.
     */
    public int getEffectiveMinorVersion();


    /**
     * 设置servlet规范的有效的次要版本.
     *
     * @param minor 设置版本号
     */
    public void setEffectiveMinorVersion(int minor);


    /**
     * @return 此上下文的JSP配置. 如果没有JSP配置，则为null.
     */
    public JspConfigDescriptor getJspConfigDescriptor();

    /**
     * 设置JspConfigDescriptor. 如果没有JSP配置，则为null.
     *
     * @param descriptor JSP 配置
     */
    public void setJspConfigDescriptor(JspConfigDescriptor descriptor);

    /**
     * 添加一个ServletContainerInitializer 实例到这个web应用.
     *
     * @param sci       要添加的实例
     * @param classes   The classes in which the initializer expressed an
     *                  interest
     */
    public void addServletContainerInitializer(
            ServletContainerInitializer sci, Set<Class<?>> classes);

    /**
     * 重新加载时是否暂停这个Context?
     *
     * @return <code>true</code>如果暂停这个上下文
     */
    public boolean getPaused();


    /**
     * 这个上下文是使用servlet规范的2.2版本吗?
     *
     * @return <code>true</code>对于一个传统的Servlet 2.2的应用
     */
    boolean isServlet22();

    /**
     * 已动态设置servlet安全性的通知, 在{@link javax.servlet.ServletRegistration.Dynamic}中
     * 
     * @param registration servlet安全性被修改为
     * @param servletSecurityElement 此servlet的新安全约束
     * @return 已经映射到web.xml的url
     */
    Set<String> addServletSecurity(ServletRegistration.Dynamic registration,
            ServletSecurityElement servletSecurityElement);

    /**
     * 设置(逗号分隔)Servlet列表.
     * 当没有资源时，用于确保欢迎文件和Servlet期望的资源不是相互映射的.
     *
     * @param resourceOnlyServlets servlet名称逗号分隔列表
     */
    public void setResourceOnlyServlets(String resourceOnlyServlets);

    /**
     * 获取期望资源的Servlet列表.
     *
     * @return 在web.xml中使用的逗号分隔的servlet名称列表
     */
    public String getResourceOnlyServlets();

    /**
     * 检查指定的servlet，以确定它是否期望资源存在.
     *
     * @param servletName   要检查的Servlet的名称 (as per web.xml)
     * @return              <code>true</code>如果Servlet期望一个资源, 否则<code>false</code>
     */
    public boolean isResourceOnlyServlet(String servletName);

    /**
     * @return 用于WAR, 目录或context.xml文件的基础名称.
     */
    public String getBaseName();

    /**
     * 设置此Web应用程序的版本 - 用于在使用并行部署时区分同一Web应用程序的不同版本.
     *
     * @param webappVersion 与上下文相关的开源版本，这应该是唯一的
     */
    public void setWebappVersion(String webappVersion);

    /**
     * @return 此Web应用程序的版本, 用于在使用并行部署时区分同一Web应用程序的不同版本.
     */
    public String getWebappVersion();

    /**
     * 配置请求监听器是否在该上下文转发时触发.
     *
     * @param enable <code>true</code>在转发时触发请求监听器
     */
    public void setFireRequestListenersOnForwards(boolean enable);

    /**
     * @return 请求监听器是否在该上下文转发时触发.
     */
    public boolean getFireRequestListenersOnForwards();

    /**
     * 配置用户是否呈现身份验证凭据, 当请求是非受保护资源时，上下文是否会处理它们.
     *
     * @param enable <code>true</code>即使在外部安全约束下也要进行身份验证
     */
    public void setPreemptiveAuthentication(boolean enable);

    /**
     * @return 如果用户呈现身份验证凭据, 当请求是非受保护资源时，上下文是否会处理它们.
     */
    public boolean getPreemptiveAuthentication();

    /**
     * 配置当将重定向响应发送到客户端时是否包含响应主体.
     *
     * @param enable <code>true</code>发送响应主体
     */
    public void setSendRedirectBody(boolean enable);

    /**
     * @return 如果将上下文配置为将响应主体包括为重定向响应的一部分.
     */
    public boolean getSendRedirectBody();

    /**
     * @return 与此上下文关联的加载程序.
     */
    public Loader getLoader();

    /**
     * 设置与此上下文关联的加载程序.
     *
     * @param loader 关联的加载器
     */
    public void setLoader(Loader loader);

    /**
     * @return 与此上下文关联的资源.
     */
    public WebResourceRoot getResources();

    /**
     * 设置关联的Resource对象.
     *
     * @param resources 关联的Resource
     */
    public void setResources(WebResourceRoot resources);

    /**
     * @return 关联的Manager. 如果没有关联的Manager, 返回<code>null</code>.
     */
    public Manager getManager();


    /**
     * 设置关联的Manager.
     *
     * @param manager 关联的Manager
     */
    public void setManager(Manager manager);

    /**
     * /WEB-INF/classes 是否应该被视为一个解压的JAR和JAR资源，就像在JAR中一样.
     *
     * @param addWebinfClassesResources The new value for the flag
     */
    public void setAddWebinfClassesResources(boolean addWebinfClassesResources);

    /**
     * @return /WEB-INF/classes 是否应该被视为一个解压的JAR和JAR资源，就像在JAR中一样.
     */
    public boolean getAddWebinfClassesResources();

    /**
     * 为给定的类添加一个Post构造方法定义.
     *
     * @param clazz 完全限定类名
     * @param method Post 构造方法名称
     * @throws IllegalArgumentException 如果完全限定类名或方法名为<code>NULL</code>; 如果给定类已经有Post构造方法定义
     */
    public void addPostConstructMethod(String clazz, String method);

    /**
     * 为给定类添加预销毁方法定义.
     *
     * @param clazz 完全限定类名
     * @param method Post 构造方法名称
     * @throws IllegalArgumentException 如果完全限定类名或方法名为<code>NULL</code>; 如果给定类已经有预销毁方法定义
     */
    public void addPreDestroyMethod(String clazz, String method);

    /**
     * 删除给定类的Post构造方法定义.
     *
     * @param clazz 完全限定类名
     */
    public void removePostConstructMethod(String clazz);

    /**
     * 删除给定类的预销毁方法定义.
     *
     * @param clazz 完全限定类名
     */
    public void removePreDestroyMethod(String clazz);

    /**
     * 返回指定类的POST方法的方法名; 否则返回<code>NULL</code>.
     *
     * @param clazz 完全限定类名
     */
    public String findPostConstructMethod(String clazz);

    /**
     * 返回指定类的预销毁方法的方法名; 否则返回<code>NULL</code>.
     *
     * @param clazz 完全限定类名
     */
    public String findPreDestroyMethod(String clazz);

    /**
     * 返回一个Map - 具有post构造方法的类的完全限定类名作为key, 对应于方法名的值.
     * 如果没有这样的类，将返回空映射.
     */
    public Map<String, String> findPostConstructMethods();

    /**
     * 返回一个Map - 具有预销毁方法的类的完全限定类名作为key, 对应于方法名的值.
     * 如果没有这样的类，将返回空映射.
     */
    public Map<String, String> findPreDestroyMethods();

    /**
     * @return  关联的JNDI命名上下文的操作所需的token.
     */
    public Object getNamingToken();

    /**
     * 设置用于处理cookie的 {@link CookieProcessor}.
     *
     * @param cookieProcessor  Cookie处理器
     *
     * @throws IllegalArgumentException 如果指定了一个{@code null} CookieProcessor
     */
    public void setCookieProcessor(CookieProcessor cookieProcessor);

    /**
     * @return 用于处理cookie的{@link CookieProcessor}.
     */
    public CookieProcessor getCookieProcessor();

    /**
     * 当客户端提供新会话的id时，是否应该验证该ID?
     * 使用客户端提供会话ID的唯一用例是在多个Web应用程序上拥有一个公共会话ID. 因此，任何客户端提供的会话ID应该已经存在于另一个Web应用程序中.
     * 如果启用了此检查，则仅在会话ID存在于当前主机的至少一个其他Web应用程序中时，才使用客户端提供的会话ID.
     * 请注意，无论这个设置如何，总是应用以下附加测试:
     * <ul>
     * <li>会话ID由cookie提供</li>
     * <li>会话cookie 有一个路径{@code /}</li>
     * </ul>
     *
     * @param validateClientProvidedNewSessionId {@code true} 如果启用验证
     */
    public void setValidateClientProvidedNewSessionId(boolean validateClientProvidedNewSessionId);

    /**
     * 客户端提供的会话ID是否在使用之前验证?
     *
     * @return {@code true} 如果验证将被应用. 否则{@code false}
     */
    public boolean getValidateClientProvidedNewSessionId();

    /**
     * 如果启用, web应用上下文根的请求将被Mapper重定向.
     * 这是有效的，但有确认上下文路径有效的副作用.
     *
     * @param mapperContextRootRedirectEnabled 是否启用重定向?
     */
    public void setMapperContextRootRedirectEnabled(boolean mapperContextRootRedirectEnabled);

    /**
     *  web应用上下文根的请求是否将被Mapper重定向.
     *  这是有效的，但有确认上下文路径有效的副作用.
     *
     * @return {@code true}如果启用了Mapper等级重定向.
     */
    public boolean getMapperContextRootRedirectEnabled();

    /**
     * 如果启用, 目录的请求将被Mapper重定向.
     * 这是有效的，但有确认目录是否有效的副作用.
     *
     * @param mapperDirectoryRedirectEnabled 是否启用重定向?
     */
    public void setMapperDirectoryRedirectEnabled(boolean mapperDirectoryRedirectEnabled);

    /**
     * 目录的请求是否将被Mapper重定向.
     * 这是有效的，但有确认目录是否有效的副作用.
     *
     * @return {@code true}如果启用了 Mapper等级重定向.
     */
    public boolean getMapperDirectoryRedirectEnabled();

    /**
     * 控制HTTP 1.1和更新版本location header通过调用{@link javax.servlet.http.HttpServletResponse#sendRedirect(String)}生成，
     * 将使用相对或绝对重定向.
     * <p>
     * 相对重定向更有效，但不能与改变上下文路径的反向代理一起工作. 应该注意的是，不建议使用反向代理来更改上下文路径，因为它会导致多个问题.
     * <p>
     * 绝对重定向应该与改变上下文路径的反向代理工作，但是导致了{@link org.apache.catalina.filters.RemoteIpFilter},
     * 如果过滤器正在改变计划和/或端口.
     *
     * @param useRelativeRedirects {@code true}使用相对重定向，
     *                             {@code false}使用绝对重定向
     */
    public void setUseRelativeRedirects(boolean useRelativeRedirects);

    /**
     * 控制HTTP 1.1和更新版本location header通过调用{@link javax.servlet.http.HttpServletResponse#sendRedirect(String)}生成，
     * 将使用相对或绝对重定向.
     *
     * @return {@code true}使用相对重定向，
     *         {@code false}使用绝对重定向
     */
    public boolean getUseRelativeRedirects();

    /**
     * 获取请求调度器的路径是否需要编码?
     * 这将影响Tomcat如何处理调用以获得请求调度器，以及Tomcat如何生成用于在内部获取请求调度器的路径.
     *
     * @param dispatchersUseEncodedPaths {@code true}使用编码路径, 否则{@code false}
     */
    public void setDispatchersUseEncodedPaths(boolean dispatchersUseEncodedPaths);

    /**
     * 获取请求调度器的路径是否需要编码?
     * 这将影响Tomcat如何处理调用以获得请求调度器，以及Tomcat如何生成用于在内部获取请求调度器的路径.
     *
     * @return {@code true}使用编码路径, 否则{@code false}
     */
    public boolean getDispatchersUseEncodedPaths();

    /**
     * 为这个Web应用程序设置默认的请求正文编码.
     *
     * @param encoding 默认编码
     */
    public void setRequestCharacterEncoding(String encoding);

    /**
     * 获取此Web应用程序的默认请求正文编码.
     *
     * @return 默认请求正文编码
     */
    public String getRequestCharacterEncoding();

    /**
     * 设置此Web应用程序的默认响应主体编码.
     *
     * @param encoding 默认编码
     */
    public void setResponseCharacterEncoding(String encoding);

    /**
     * 获取此Web应用程序的默认响应主体编码.
     *
     * @return 默认响应主体编码
     */
    public String getResponseCharacterEncoding();
}
