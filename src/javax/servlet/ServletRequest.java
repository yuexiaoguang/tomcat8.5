package javax.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * 定义一个对象，以向servlet提供客户端请求信息.
 * servlet容器创建一个 <code>ServletRequest</code>对象并将它作为一个参数传递给servlet的<code>service</code>方法.
 * <p>
 * 一个<code>ServletRequest</code>对象提供数据，包括参数名和参数值, 属性, 和一个输入流.
 * 继承<code>ServletRequest</code>的接口可以提供特定于协议的附加的数据(例如, HTTP数据是通过{@link javax.servlet.http.HttpServletRequest}提供的).
 */
public interface ServletRequest {

    /**
     * 返回指定属性的值, 或者<code>null</code>.
     * <p>
     * 属性可以以两种方式设置. servlet容器可以设置属性以提供关于请求的自定义信息. 例如, 对于使用HTTPS的请求, 属性
     * <code>javax.servlet.request.X509Certificate</code>可以用于检索客户端证书上的信息. 属性也可以用编程方式设置 {@link ServletRequest#setAttribute}.
     * 这允许将信息嵌入到请求中，在调用{@link RequestDispatcher}之前.
     * <p>
     * 属性名称应该遵循与包名称相同的约定. 名称以<code>java.*</code>和<code>javax.*</code>开头的属性保留为servlet规范使用.
     * 名称以<code>sun.*</code>, <code>com.sun.*</code>, <code>oracle.*</code>, <code>com.oracle.*</code>)开头的属性
     * 保留供Oracle公司使用.
     *
     * @param name 属性名
     * @return 属性值, 或者<code>null</code>
     */
    public Object getAttribute(String name);

    /**
     * 返回这个请求的所有属性名.
     * 这个方法返回一个空的<code>Enumeration</code>, 如果请求没有可用的属性.
     *
     * @return 请求的所有属性名
     */
    public Enumeration<String> getAttributeNames();

    /**
     * 返回该请求正文中使用的字符编码的名称. 这个方法返回<code>null</code>，如果请求没有指定字符编码
     *
     * @return 字符编码, 或者<code>null</code>如果请求没有指定字符编码
     */
    public String getCharacterEncoding();

    /**
     * 设置此请求正文中使用的字符编码的名称. 必须在读取请求参数或使用getReader()读取输入之前调用此方法.
     * 
     * @param env 字符编码名称.
     * @throws java.io.UnsupportedEncodingException 如果这不是一个有效的编码
     */
    public void setCharacterEncoding(String env)
            throws java.io.UnsupportedEncodingException;

    /**
     * 返回请求主体的长度（以字节为单位），并由输入流提供, 或者-1如果长度未知.
     * 对于HTTP servlet, 和CGI 变量 CONTENT_LENGTH一样.
     *
     * @return 包含请求体长度的整数，或者 -1，如果长度未知或大于{@link Integer#MAX_VALUE}
     */
    public int getContentLength();

    /**
     * 返回请求主体的长度（以字节为单位），并由输入流提供, 或者-1如果长度未知.
     * 对于HTTP servlet, 和CGI 变量 CONTENT_LENGTH一样.
     *
     * @return 包含请求主体长度的长整数，或者 -1 如果长度未知
     */
    public long getContentLengthLong();

    /**
     * 返回请求主体的MIME类型, 或者<code>null</code>如果类型未知. 对于HTTP servlet, 等同于CGI 变量CONTENT_TYPE.
     *
     * @return 请求的MIME类型名称, 或者null如果类型未知
     */
    public String getContentType();

    /**
     * 获取请求的正文为二进制数据，使用一个{@link ServletInputStream}.
     * 使用这个方法或者{@link #getReader}读取主体, 不能两个都调用.
     *
     * @return 包含请求主体的{@link ServletInputStream}对象
     * @exception IllegalStateException 如果{@link #getReader}方法已经被调用
     * @exception IOException 如果出现输入或输出异常
     */
    public ServletInputStream getInputStream() throws IOException;

    /**
     * 返回请求参数的值, 或者<code>null</code>.
     * 请求参数是请求发送的额外信息. 对于HTTP servlet, 参数包含在查询字符串或已发布的表单数据中.
     * <p>
     * 只有在确定参数只有一个值时，才应该使用此方法. 如果参数可能有多个值, 使用{@link #getParameterValues}.
     * <p>
     * 如果使用多值参数的方法, 返回的值等于<code>getParameterValues</code>返回的数组的第一个值.
     * <p>
     * 如果参数数据是在请求体中发送的, 比如在HTTP POST 请求, 直接通过{@link #getInputStream}或{@link #getReader}读取请求主体会干扰这种方法的执行.
     *
     * @param name 参数名
     * @return 参数值
     */
    public String getParameter(String name);

    /**
     * 此请求中包含的参数的名称. 如果请求中没有参数, 这个方法返回一个空的<code>Enumeration</code>.
     */
    public Enumeration<String> getParameterNames();

    /**
     * 给定的请求参数的所有值, 或者<code>null</code>.
     * <p>
     * 如果参数只有一个值, 数组的长度为 1.
     *
     * @param name 指定的参数名
     * @return 参数的值
     */
    public String[] getParameterValues(String name);

    /**
     * 返回请求参数的java.util.Map. 请求参数是请求发送的额外信息.
     * 对于HTTP servlet, 参数包含在查询字符串或post的表单数据中.
     */
    public Map<String, String[]> getParameterMap();

    /**
     * 返回请求的协议的名称和版本，格式为<i>protocol/majorVersion.minorVersion</i>, 例如, HTTP/1.1.
     * 对于HTTP servlet, 返回的值和 CGI变量<code>SERVER_PROTOCOL</code>一样.
     */
    public String getProtocol();

    /**
     * 返回用于生成此请求的方案的名称, 例如, <code>http</code>, <code>https</code>, <code>ftp</code>.
     * 不同的方案对URL的构建有不同的规则, 注意查看RFC 1738.
     */
    public String getScheme();

    /**
     * 返回发送请求的服务器的主机名.
     * 它是<code>Host</code> header值的":"之前的部分, 如果有的话，或解析的服务器名称，或服务器的IP地址.
     *
     * @return 服务器名称
     */
    public String getServerName();

    /**
     * 返回请求发送的端口号.
     * 它是<code>Host</code> header值的":"之后的部分, 如果有的话，接受客户端连接的服务器端口.
     *
     * @return 指定的端口号
     */
    public int getServerPort();

    /**
     * 获取请求的主体为字符数据使用<code>BufferedReader</code>.
     * 读取器根据正文上使用的字符编码来转换字符数据. 不管这个方法或{@link #getInputStream}都可以读取主体, 但不能两个都调用.
     *
     * @return 包含请求主体的<code>BufferedReader</code>
     * @exception java.io.UnsupportedEncodingException 如果不支持字符集编码，无法解码文本
     * @exception IllegalStateException 如果{@link #getInputStream}已经被调用
     * @exception IOException 如果出现输入或输出异常
     */
    public BufferedReader getReader() throws IOException;

    /**
     * 返回客户端的Internet协议（IP）地址或发送请求的最后一个代理.
     * 对于HTTP servlet, 和CGI变量<code>REMOTE_ADDR</code>相同.
     *
     * @return 发送请求的客户端的IP地址
     */
    public String getRemoteAddr();

    /**
     * 返回客户端的全部限定名称或发送请求的最后一个代理.
     * 如果引擎不能或选择不解析主机名 (以提高性能), 此方法返回IP地址的虚线字符串形式. 对于HTTP servlet, 和CGI变量<code>REMOTE_HOST</code>的值相同.
     */
    public String getRemoteHost();

    /**
     * 在这个请求中存储属性. 属性在请求之间重置. 这种方法最常用于{@link RequestDispatcher}.
     * <p>
     * 属性名称应该遵循与包名称相同的约定. 名称以<code>java.*</code>和<code>javax.*</code>开头的保留为servlet规范使用.
     * 名称以<code>sun.*</code>, <code>com.sun.*</code>, <code>oracle.*</code>, <code>com.oracle.*</code>开头的保留为Oracle公司使用.
     * <br>
     * 如果传递的值为 null, 作用和调用{@link #removeAttribute}相同. <br>
     * 当请求从servlet发出时，驻留在不同的Web应用程序中, 通过<code>RequestDispatcher</code>, 此方法设置的对象可能无法在调用servlet中正确检索.
     *
     * @param name 指定的属性名
     * @param o 属性值
     */
    public void setAttribute(String name, Object o);

    /**
     * 从这个请求中删除一个属性. 这种方法一般不需要，因为只要处理请求，属性只会持久.
     * <p>
     * 属性名称应该遵循与包名称相同的约定.
     * 属性名称应该遵循与包名称相同的约定. 名称以<code>java.*</code>和<code>javax.*</code>开头的保留为servlet规范使用.
     * 名称以<code>sun.*</code>, <code>com.sun.*</code>, <code>oracle.*</code>, <code>com.oracle.*</code>开头的保留为Oracle公司使用.
     *
     * @param name 要删除的属性名
     */
    public void removeAttribute(String name);

    /**
     * 返回客户端将接受内容的首选区域, 基于 Accept-Language header.
     * 如果客户端请求没有提供 Accept-Language header, 此方法返回服务器的默认区域设置.
     *
     * @return 客户端的首选<code>Locale</code>
     */
    public Locale getLocale();

    /**
     * 区域的<code>Enumeration</code>, 从首选区域开始递减顺序, 客户端可以接受的区域，基于Accept-Language header.
     * 如果客户端请求没有提供 Accept-Language header, 此方法返回服务器的默认区域设置.
     */
    public Enumeration<Locale> getLocales();

    /**
     * 返回一个布尔值，指示此请求是否使用安全通道进行, 例如 HTTPS.
     *
     * @return 一个布尔值，指示是否使用安全通道进行请求
     */
    public boolean isSecure();

    /**
     * 返回一个{@link RequestDispatcher}作为位于给定路径上的资源的包装器.
     * <code>RequestDispatcher</code>对象可用于将请求转发到资源或将资源包含在响应中. 资源可以是动态的，也可以是静态的.
     * <p>
     * 指定的路径可能是相对的，虽然它不能扩展当前servlet上下文. 如果路径以"/"开头，它被解释为相对于当前上下文根.
     * 这个方法返回<code>null</code>，如果servlet容器不能返回<code>RequestDispatcher</code>.
     * <p>
     * 这个方法和{@link ServletContext#getRequestDispatcher}之间的差异是，这个方法可以获取相对路径.
     *
     * @param path 资源的路径. 如果是相对的, 他必须相对于当前servlet.
     * @return 一个{@link RequestDispatcher}作为位于给定路径上的资源的包装器, 或者<code>null</code>
     */
    public RequestDispatcher getRequestDispatcher(String path);

    /**
     * @param path 要转换为真实路径的虚拟路径
     * @return {@link ServletContext#getRealPath(String)}
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *             {@link ServletContext#getRealPath} instead.
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public String getRealPath(String path);

    /**
     * 返回客户端的Internet协议（IP）源端口或发送请求的最后一个代理.
     *
     * @return 指定端口号的整数
     */
    public int getRemotePort();

    /**
     * 返回收到请求的Internet协议（IP）接口的主机名.
     *
     * @return 接收请求的IP的主机名.
     */
    public String getLocalName();

    /**
     * 返回接收请求的接口的Internet协议（IP）地址.
     *
     * @return 接收请求的IP地址.
     */
    public String getLocalAddr();

    /**
     * 返回收到请求的接口的Internet协议（IP）端口号.
     *
     * @return 指定端口号的整数
     */
    public int getLocalPort();

    /**
     * @return TODO
     */
    public ServletContext getServletContext();

    /**
     * @return TODO
     * @throws IllegalStateException 如果请求不支持异步
     */
    public AsyncContext startAsync() throws IllegalStateException;

    /**
     * @param servletRequest    初始化异步上下文的ServletRequest
     * @param servletResponse   初始化异步上下文的ServletResponse
     * @return TODO
     * @throws IllegalStateException 如果请求不支持异步
     */
    public AsyncContext startAsync(ServletRequest servletRequest,
            ServletResponse servletResponse) throws IllegalStateException;

    /**
     * @return TODO
     */
    public boolean isAsyncStarted();

    /**
     * @return TODO
     */
    public boolean isAsyncSupported();

    /**
     * 获取当前AsyncContext.
     *
     * @return 当前AsyncContext
     *
     * @throws IllegalStateException 如果请求不是异步模式的(即 @link #isAsyncStarted() 是 {@code false})
     */
    public AsyncContext getAsyncContext();

    /**
     * @return TODO
     */
    public DispatcherType getDispatcherType();
}
