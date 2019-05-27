package javax.servlet;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import javax.servlet.descriptor.JspConfigDescriptor;

/**
 * 定义一组servlet用来与servlet容器通信的方法, 例如, 获取文件的MIME类型, 调度请求, 或写入一个日志文件.
 * <p>
 * 有一个上下文一个"web application"对应一个Java Virtual Machine.
 * (“Web应用程序”是一组servlet和上下文，安装在服务器的URL名称空间的特定子集之下， 例如<code>/catalog</code>
 * 并且可能通过一个<code>.war</code>文件安装.)
 * <p>
 * 对于在部署描述符中标记为“分布式”的Web应用程序, 每个虚拟机将有一个上下文实例.
 * 在这种情况下, 上下文不能用作共享全局信息的位置(因为这些信息并不是真正的全局的). 使用外部资源，比如数据库.
 * <p>
 * <code>ServletContext</code>对象包含在一个{@link ServletConfig}对象中, 当servlet初始化时，Web服务器提供servlet.
 */
public interface ServletContext {

    public static final String TEMPDIR = "javax.servlet.context.tempdir";

    public static final String ORDERED_LIBS = "javax.servlet.context.orderedLibs";

    /**
     * 返回与此上下文关联的主路径.
     */
    public String getContextPath();

    /**
     * 返回一个对应于服务器上指定URL的<code>ServletContext</code>对象.
     * <p>
     * 这种方法允许servlet来获取服务器的各个部分的上下文访问, 并根据需要从上下文获得{@link RequestDispatcher}对象.
     * 给定的路径必须以"/"开头, 相对于服务器的文档根目录，并与驻留在这个容器上的其他Web应用程序的上下文根目录相匹配.
     * <p>
     * 在有安全意识的环境中, servlet容器可能为给定的URL返回<code>null</code>.
     *
     * @param uripath 容器中另一个Web应用程序的上下文路径.
     * @return 对应于命名URL的<code>ServletContext</code>对象, 或者null.
     */
    public ServletContext getContext(String uripath);

    /**
     * 返回这个servlet容器支持的Java Servlet API的主要版本.
     * 版本 3.1 必须返回 3.
     */
    public int getMajorVersion();

    /**
     * 返回这个servlet容器支持的Java Servlet API的次要版本.
     * 版本 3.1 必须返回 1.
     */
    public int getMinorVersion();

    /**
     * @return TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public int getEffectiveMajorVersion();

    /**
     * @return TODO
     * @throws UnsupportedOperationException   它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public int getEffectiveMinorVersion();

    /**
     * 返回指定文件的MIME类型, 或者<code>null</code>.
     * MIME类型是由servlet容器的配置决定的, 可以在Web应用程序部署描述符中指定. 常见的MIME类型是<code>"text/html"</code>和<code>"image/gif"</code>.
     *
     * @param file 文件名
     * @return 指定文件的MIME类型
     */
    public String getMimeType(String file);

    /**
     * 返回一个目录，类似于Web应用程序中资源的所有路径的列表，其中最长的子路径与提供的路径参数匹配.
     * 表示子目录路径的路径以'/'结尾. 返回的路径都相对于Web应用程序的根目录，并以'/'开头. 例如, 对于包含web应用程序的<br>
     * <br>
     * /welcome.html<br>
     * /catalog/index.html<br>
     * /catalog/products.html<br>
     * /catalog/offers/books.html<br>
     * /catalog/offers/music.html<br>
     * /customer/login.jsp<br>
     * /WEB-INF/web.xml<br>
     * /WEB-INF/classes/com.acme.OrderServlet.class,<br>
     * <br>
     * getResourcePaths("/") returns {"/welcome.html", "/catalog/",
     * "/customer/", "/WEB-INF/"}<br>
     * getResourcePaths("/catalog/") returns {"/catalog/index.html",
     * "/catalog/products.html", "/catalog/offers/"}.<br>
     *
     * @param path 用于匹配资源的部分路径, 必须以 / 开头
     * @return 包含目录列表的集合, 或者null 如果其路径以提供的路径开始的Web应用程序中没有资源.
     */
    public Set<String> getResourcePaths(String path);

    /**
     * 返回映射到指定路径的资源的URL. 路径必须以"/"开头并被相对于当前上下文根目录.
     * <p>
     * 此方法允许servlet容器让一个资源对任何来源的servlet可用. 资源可以位于本地或远程文件系统中, 数据库中, 或在一个<code>.war</code>文件中.
     * <p>
     * servlet容器必须实现url处理程序和可用于访问资源的<code>URLConnection</code>对象.
     * <p>
     * 这个方法返回<code>null</code>，如果没有资源映射到指定的路径.
     * <p>
     * 一些容器可以允许使用URL类的方法对该方法返回的URL进行写入.
     * <p>
     * 资源内容直接返回, 所以要知道请求一个<code>.jsp</code>页面返回JSP的源代码. 使用一个内部的<code>RequestDispatcher</code>包含指定的结果.
     * <p>
     * 这个方法的目的不同于<code>java.lang.Class.getResource</code>, 它基于类加载器查找资源. 此方法不使用类装入器.
     *
     * @param path 资源的路径
     * @return 位于指定路径的资源, 或者<code>null</code>
     * @exception MalformedURLException 如果路径的形式不正确
     */
    public URL getResource(String path) throws MalformedURLException;

    /**
     * 返回指定路径的资源为一个<code>InputStream</code>对象.
     * <p>
     * <code>InputStream</code>中的数据可以是任何类型和长度.
     * 路径必须根据<code>getResource</code>指定的规则指定. 如果指定的路径中不存在资源，这个方法返回<code>null</code>.
     * <p>
     * 使用此方法时，通过<code>getResource</code>方法获得的内容长度和内容类型等元数据丢失.
     * <p>
     * servlet容器必须实现url处理程序和可用于访问资源的<code>URLConnection</code>对象.
     * 这个方法不同于使用类加载器的<code>java.lang.Class.getResourceAsStream</code>. 此方法允许servlet容器从任何位置向servlet提供资源，而不使用类装入器.
     *
     * @param path 资源路径
     * @return 返回给servlet的<code>InputStream</code>, 或者<code>null</code>
     */
    public InputStream getResourceAsStream(String path);

    /**
     * 返回一个{@link RequestDispatcher}对象作为位于给定路径上的资源的包装器.
     * 一个<code>RequestDispatcher</code>对象可用于将请求转发到资源或将资源包含在响应中. 资源可以是动态的，也可以是静态的.
     * <p>
     * 路径名必须以"/"开头并且相对于当前上下文根路径. 使用<code>getContext</code>获取外部上下文的资源的<code>RequestDispatcher</code>.
     * 这个方法返回<code>null</code>，如果<code>ServletContext</code>不能返回<code>RequestDispatcher</code>.
     *
     * @param path 资源的路径名
     * @return 作为位于给定路径上的资源的包装器的{@link RequestDispatcher}对象, 或者<code>null</code>
     */
    public RequestDispatcher getRequestDispatcher(String path);

    /**
     * 返回一个{@link RequestDispatcher}对象作为命名servlet的包装器.
     * <p>
     * Servlets (和JSP页面)可以通过服务器管理员或通过Web应用程序部署描述符来命名. servlet实例可以使用{@link ServletConfig#getServletName}来确定它的名称.
     * <p>
     * 这个方法返回<code>null</code>，如果<code>ServletContext</code>不能返回一个<code>RequestDispatcher</code>.
     *
     * @param name 要包装的servlet的名称
     * @return 作为命名servlet的包装器的<code>RequestDispatcher</code>对象, 或者<code>null</code>
     */
    public RequestDispatcher getNamedDispatcher(String name);

    /**
     * 将指定的消息写入servlet日志文件，通常是事件日志.
     * servlet日志文件的名称和类型是特定于servlet容器的.
     *
     * @param msg 要写入日志文件的消息
     */
    public void log(String msg);

    /**
     * 写入解释信息和给定<code>Throwable</code>异常的堆栈跟踪到servlet日志文件.
     * servlet日志文件的名称和类型是特定于servlet容器的，通常是事件日志.
     *
     * @param message 描述错误或异常的信息
     * @param throwable 错误或异常
     */
    public void log(String message, Throwable throwable);

    /**
     * 返回包含给定虚拟路径的真实路径的<code>String</code>.
     * 例如, 路径"/index.html"返回服务器文件系统中的绝对文件路径将被请求为
     * "http://host/contextPath/index.html", contextPath是ServletContext的上下文路径.
     * <p>
     * 返回的实际路径将以适合于servlet容器运行的计算机和操作系统的形式，包括适当的路径分隔符.
     * 这个方法返回<code>null</code>， 如果servlet容器不能将虚拟路径转换为真实路径(例如，当内容来自一个<code>.war</code>文件).
     *
     * @param path 指定的虚拟路径
     * @return 指定的真实路径, 或者null
     */
    public String getRealPath(String path);

    /**
     * 返回servlet运行的servlet容器的名称和版本.
     * <p>
     * 返回的字符串格式为<i>servername</i>/<i>versionnumber</i>. 例如, JavaServer Web Development Kit可能返回字符串<code>JavaServer Web Dev Kit/1.0</code>.
     * <p>
     * servlet容器可以在括号中的主字符串后面返回其他可选信息, 例如,
     * <code>JavaServer Web Dev Kit/1.0 (JDK 1.1.6; Windows NT 4.0 x86)</code>.
     *
     * @return 至少包含servlet容器名称和版本号
     */
    public String getServerInfo();

    /**
     * 返回指定的上下文范围初始化参数的值, 或者<code>null</code>.
     * <p>
     * 方法可以提供对整个Web应用程序有用的配置信息. 例如, 它可以提供一个网站管理员的电子邮件地址或持有关键数据的系统的名称.
     *
     * @param name 请求其值的参数的名称
     * @return 初始化参数的值
     */
    public String getInitParameter(String name);

    /**
     * 返回上下文的初始化参数的名称,作为一个<code>Enumeration</code>, 或空枚举.
     *
     * @return <code>String</code>对象的<code>Enumeration</code>，包含上下文初始化参数的名称
     */

    public Enumeration<String> getInitParameterNames();

    /**
     * 设置指定的初始化参数为指定值.
     * @param name  初始化参数的名称
     * @param value 初始化参数的值
     * @return <code>true</code>如果调用成功，或<code>false</code>如果调用失败，因为具有相同名称的一个初始化参数已经设置
     * @throws IllegalStateException 如果这个ServletContext的初始化已经完成
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public boolean setInitParameter(String name, String value);

    /**
     * 返回给定名称的servlet容器属性, 或者<code>null</code>.
     * 属性允许servlet容器向servlet提供该接口未提供的附加信息. 有关服务器属性的信息，请参见服务器文档. 可以使用<code>getAttributeNames</code>检索支持的属性列表.
     * <p>
     * 属性名称应遵循与包名称相同的约定. Java Servlet API规范名称符合<code>java.*</code>, <code>javax.*</code>, <code>sun.*</code>.
     *
     * @param name 属性名
     * @return 属性值, 或<code>null</code>
     */
    public Object getAttribute(String name);

    /**
     * 返回这个servlet上下文中可用的属性名的<code>Enumeration</code>.
     * 使用{@link #getAttribute}获取指定属性的属性值.
     *
     * @return 属性名的<code>Enumeration</code>
     */
    public Enumeration<String> getAttributeNames();

    /**
     * 在servlet上下文中绑定对象到给定属性名. 如果指定的名称已经存在, 将会替换.
     * <p>
     * 如果在<code>ServletContext</code>上配置了监听器，容器相应地通知它们.
     * <p>
     * 如果传递了一个null值, 效果与调用<code>removeAttribute()</code>相同.
     * <p>
     * 属性名称应遵循与包名称相同的约定. Java Servlet API规范要求名称匹配<code>java.*</code>, <code>javax.*</code>, <code>sun.*</code>.
     *
     * @param name 属性名
     * @param object 属性值
     */
    public void setAttribute(String name, Object object);

    /**
     * 从servlet上下文中移除具有给定名称的属性.
     * <p>
     * 如果在<code>ServletContext</code>上配置了监听器，容器相应地通知它们.
     *
     * @param name 要删除的属性名
     */
    public void removeAttribute(String name);

    /**
     * 返回此Web应用程序对应于该ServletContext的名字，就如此Web应用程序的部署描述符中display-name元素所指定的.
     *
     * @return Web应用程序的名称或null.
     */
    public String getServletContextName();

    /**
     * 在这个ServletContext中注册一个servlet实现类.
     * @param servletName 要注册的servlet的名称
     * @param className   servlet的实现类
     * @return 允许进一步配置的注册对象
     * @throws IllegalStateException 如果上下文已经初始化
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public ServletRegistration.Dynamic addServlet(String servletName, String className);

    /**
     * 在这个ServletContext中注册一个servlet实例.
     * @param servletName 要注册的servlet的名称
     * @param servlet     要注册的Servlet实例
     * @return 允许进一步配置的注册对象
     * @throws IllegalStateException 如果上下文已经初始化
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet);

    /**
     * 将servlet添加到上下文.
     * @param   servletName  要添加的servlet的名称
     * @param   servletClass 要添加的servlet类
     * @return  <code>null</code> 如果servlet已被完全定义, 或者{@link javax.servlet.ServletRegistration.Dynamic}对象可用于进一步配置servlet
     * @throws IllegalStateException 如果上下文已经初始化
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public ServletRegistration.Dynamic addServlet(String servletName,
            Class<? extends Servlet> servletClass);

    /**
     * TODO SERVLET3 - Add comments
     * @param <T> TODO
     * @param c   TODO
     * @return TODO
     * @throws ServletException TODO
     * @throws UnsupportedOperationException   它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public <T extends Servlet> T createServlet(Class<T> c)
            throws ServletException;

    /**
     * 获取指定servlet的详细信息.
     *
     * @param servletName   感兴趣的servlet的名称
     *
     * @return  指定servlet的注册详细信息， 或者<code>null</code>如果没有以给定名称注册的servlet
     *
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public ServletRegistration getServletRegistration(String servletName);

    /**
     * TODO SERVLET3 - Add comments
     * @return TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public Map<String, ? extends ServletRegistration> getServletRegistrations();

    /**
     * 将过滤器添加到上下文.
     * @param   filterName  要添加的过滤器的名称
     * @param   className 过滤类名称
     * @return  <code>null</code>如果过滤器已被完全定义, 或者{@link javax.servlet.FilterRegistration.Dynamic}对象可用于进一步配置servlet
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     * @throws IllegalStateException 如果上下文已经初始化
     */
    public FilterRegistration.Dynamic addFilter(String filterName, String className);

    /**
     * 将过滤器添加到上下文.
     * @param   filterName  要添加的过滤器的名称
     * @param   filter      要添加的Filter
     * @return  <code>null</code>如果过滤器已被完全定义, 或者{@link javax.servlet.FilterRegistration.Dynamic}对象可用于进一步配置servlet
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     * @throws IllegalStateException 如果上下文已经初始化
     */
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter);

    /**
     * 将过滤器添加到上下文.
     * @param   filterName  要添加的过滤器的名称
     * @param   filterClass 要添加的过滤器类
     * @return  <code>null</code>如果过滤器已被完全定义, 或者{@link javax.servlet.FilterRegistration.Dynamic}对象可用于进一步配置servlet
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     * @throws IllegalStateException 如果上下文已经初始化
     */
    public FilterRegistration.Dynamic addFilter(String filterName,
            Class<? extends Filter> filterClass);

    /**
     * TODO SERVLET3 - Add comments
     * @param <T> TODO
     * @param c   TODO
     * @return TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     * @throws ServletException TODO
     */
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException;

    /**
     * TODO SERVLET3 - Add comments
     * @param filterName TODO
     * @return TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public FilterRegistration getFilterRegistration(String filterName);

    /**
     * @return TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public Map<String, ? extends FilterRegistration> getFilterRegistrations();

    /**
     * @return TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public SessionCookieConfig getSessionCookieConfig();

    /**
     * 为这个Web应用程序配置可用的会话跟踪模式.
     * @param sessionTrackingModes 此Web应用程序使用的会话跟踪模式
     * 
     * @throws IllegalArgumentException 如果sessionTrackingModes指定{@link SessionTrackingMode#SSL}和其它{@link SessionTrackingMode}组合
     * 
     * @throws IllegalStateException 如果上下文已经初始化
     * 
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public void setSessionTrackingModes(
            Set<SessionTrackingMode> sessionTrackingModes);

    /**
     * 获取此Web应用程序的默认会话跟踪模式.
     * 默认的{@link SessionTrackingMode#URL}一直支持;
     * 如果要支持{@link SessionTrackingMode#COOKIE}， 除非<code>cookies</code>属性被设置为<code>false</code>;
     * 如果要支持{@link SessionTrackingMode#SSL}，这个上下文使用的连接器至少有一个将属性<code>secure</code>设置为<code>true</code>.
     * 
     * @return 此Web应用程序的默认会话跟踪模式集
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes();

    /**
     * 获取此Web应用程序当前启用的会话跟踪模式.
     * 
     * @return 通过{@link #setSessionTrackingModes(Set)}提供的值，如果之前设置了, 否则返回默认的
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes();

    /**
     * TODO SERVLET3 - Add comments
     * @param className TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public void addListener(String className);

    /**
     * TODO SERVLET3 - Add comments
     * @param <T> TODO
     * @param t   TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public <T extends EventListener> void addListener(T t);

    /**
     * TODO SERVLET3 - Add comments
     * @param listenerClass TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public void addListener(Class<? extends EventListener> listenerClass);

    /**
     * TODO SERVLET3 - Add comments
     * @param <T> TODO
     * @param c TODO
     * @return TODO
     * @throws ServletException TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public <T extends EventListener> T createListener(Class<T> c)
            throws ServletException;

    /**
     * @return TODO
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     */
    public JspConfigDescriptor getJspConfigDescriptor();

    /**
     * 获取这个ServletContext关联的Web应用程序类装入器.
     *
     * @return 关联的Web应用程序类加载器
     *
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     * @throws SecurityException 如果访问的类装载器被SecurityManager阻止
     */
    public ClassLoader getClassLoader();

    /**
     * 为这个ServletContext添加声明的角色.
     * @param roleNames 要添加的角色
     * @throws UnsupportedOperationException    它被没有在web.xml文件中定义或没有被{@link javax.servlet.annotation.WebListener}注解标注的
     * 	{@link ServletContextListener}的{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法调用.
     *  例如, TLD中定义的{@link ServletContextListener}不能使用这个方法.
     * @throws IllegalArgumentException 如果roleNames列表为空
     * @throws IllegalStateException 如果ServletContext已经初始化
     */
    public void declareRoles(String... roleNames);

    /**
     * 获取部署此上下文的虚拟主机的主名称. 该名称可能是或可能不是有效的主机名.
     */
    public String getVirtualServerName();
}
