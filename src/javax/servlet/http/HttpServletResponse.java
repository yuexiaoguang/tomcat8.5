package javax.servlet.http;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletResponse;

/**
 * 继承{@link ServletResponse}在发送响应时提供HTTP特定功能的接口. 例如, 它有访问HTTP头和cookie的方法.
 * <p>
 * servlet容器创建一个<code>HttpServletResponse</code>对象并将其作为一个参数传递给servlet的service方法(<code>doGet</code>, <code>doPost</code>,等).
 */
public interface HttpServletResponse extends ServletResponse {

    /**
     * 将指定cookie添加到响应. 此方法可以多次调用来设置多个cookie.
     *
     * @param cookie 返回给客户端的Cookie
     */
    public void addCookie(Cookie cookie);

    /**
     * 返回一个布尔值，指示已命名的响应标头是否已设置.
     *
     * @param name header名称
     * @return <code>true</code>如果响应头已经被设置; 否则<code>false</code>
     */
    public boolean containsHeader(String name);

    /**
     * 通过在其中包含会话ID来编码指定的URL，或者，如果不需要编码，则返回URL.
     * 此方法的实现包括，会话ID是否需要在URL中编码的逻辑. 例如，如果浏览器支持cookie，或者会话跟踪关闭，则不需要url编码.
     * <p>
     * 对于健壮的会话跟踪，servlet发出的所有URL都应该通过该方法运行. 否则，URL重写不能和不支持cookie的浏览器一起使用.
     *
     * @param url 要编码的URL.
     * @return 如果需要编码，则编码url; 否则未修改的 URL.
     */
    public String encodeURL(String url);

    /**
     * 编码指定的URL在<code>sendRedirect</code>方法中使用, 如果不需要编码, 返回未修改的URL.
     * 此方法的实现包括，确定会话ID是否需要在URL中编码的逻辑.
     * <p>
     * 发送到<code>HttpServletResponse.sendRedirect</code>方法的所有URL都应该通过该方法运行.
     * 否则，URL重写不能与不支持cookie的浏览器一起使用.
     *
     * @param url 要编码的URL.
     * @return 如果需要编码，则编码URL; 否则未修改的URL.
     */
    public String encodeRedirectURL(String url);

    /**
     * @param url  要编码的URL.
     * @return 如果需要编码，则编码URL; 否则未修改的URL.
     * @deprecated As of version 2.1, use encodeURL(String url) instead
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public String encodeUrl(String url);

    /**
     * @param url 要编码的URL.
     * @return 如果需要编码，则编码URL; 否则未修改的URL.
     * @deprecated As of version 2.1, use encodeRedirectURL(String url) instead
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public String encodeRedirectUrl(String url);

    /**
     * 使用指定的状态码向客户端发送错误响应，并清除输出缓冲区.
     * 服务器默认创建的响应类似于HTML格式的服务器错误页面，包含指定的消息, 将内容类型设置为"text/html", cookie和其他头文件未修改.
     * 如果对Web应用程序进行了错误页面声明，则对应于传入的状态码, 它将按建议的MSG参数返回.
     * <p>
     * 如果响应已经提交, 抛出IllegalStateException. 使用此方法后, 响应应该被提交，而不能再被写入.
     *
     * @param sc 错误状态码
     * @param msg 描述信息
     * @exception IOException 如果出现输入或输出异常
     * @exception IllegalStateException 如果响应被提交
     */
    public void sendError(int sc, String msg) throws IOException;

    /**
     * 使用指定的状态码向客户端发送错误响应并清除缓冲区.
     * 等效于调用{@link #sendError(int, String)}使用相同的状态码和<code>null</code>详情.
     *
     * @param sc 错误状态码
     * @exception IOException 如果出现输入或输出异常
     * @exception IllegalStateException 如果响应被提交
     */
    public void sendError(int sc) throws IOException;

    /**
     * 使用指定的重定向位置URL向客户端发送临时重定向响应.
     * 该方法可以接受相对URL; 在发送响应到客户端之前，servlet容器必须将相对URL转换为绝对URL.
     * 如果位置是相对的，但不是以'/'开头，容器将其解释为相对于当前请求URI.
     * 如果位置是相对的，并且以'/'开头，容器将其解释为相对于servlet容器根目录.
     * <p>
     * 如果响应已经提交, 抛出IllegalStateException. 使用此方法后, 响应应该被提交，而不能再被写入.
     *
     * @param location 重定向位置URL
     * @exception IOException  如果出现输入或输出异常
     * @exception IllegalStateException 如果提交了响应，或者给定了部分URL，不能转换成有效URL
     */
    public void sendRedirect(String location) throws IOException;

    /**
     * 设置给定名称和日期值的响应头.
     * 日期是从纪元以来的毫秒数. 如果header已经被设置, 新值会覆盖之前的.
     * <code>containsHeader</code>方法可以用于测试是否已经设置header.
     *
     * @param name 要设置的标头的名称
     * @param date 指定的日期值
     */
    public void setDateHeader(String name, long date);

    /**
     * 添加具有指定名称和日期值的响应头.
     * 日期是从纪元以来的毫秒数. 此方法允许响应头具有多个值.
     *
     * @param name 要设置的标头的名称
     * @param date 附加日期值
     */
    public void addDateHeader(String name, long date);

    /**
     * 设置给定名称和值的响应头.
     * 如果header已经被设置, 新值会覆盖之前的.
     * <code>containsHeader</code>方法可以用于测试是否已经设置header.
     *
     * @param name 要设置的标头的名称
     * @param value 标头的值, 需要符合 RFC 2047
     *            (http://www.ietf.org/rfc/rfc2047.txt)
     */
    public void setHeader(String name, String value);

    /**
     * 添加具有给定名称和值的响应头. 此方法允许响应头具有多个值.
     *
     * @param name 标头的名称
     * @param value 标头的值, 需要符合 RFC 2047
     *            (http://www.ietf.org/rfc/rfc2047.txt)
     */
    public void addHeader(String name, String value);

    /**
     * 设置具有给定名称和整数值的响应头.
     * 如果header已经被设置, 新值会覆盖之前的.
     * <code>containsHeader</code>方法可以用于测试是否已经设置header.
     *
     * @param name 要设置的标头的名称
     * @param value 标头的值
     */
    public void setIntHeader(String name, int value);

    /**
     * 添加具有给定名称和整数值的响应头. 此方法允许响应头具有多个值.
     *
     * @param name 标头的名称
     * @param value 标头的值
     */
    public void addIntHeader(String name, int value);

    /**
     * 设置此响应的状态码.
     * 此方法用于在没有错误时设置返回的状态码(例如, 状态码 SC_OK 或 SC_MOVED_TEMPORARILY).
     * 如果有错误，调用者希望调用Web应用程序中定义的错误页面, 应该使用<code>sendError</code>方法.
     * <p>
     * 容器清除缓冲区并设置Location header, 保存cookie和其他头文件.
     *
     * @param sc 状态码
     */
    public void setStatus(int sc);

    /**
     * 设置此响应的状态码和消息.
     *
     * @param sc 状态码
     * @param sm 状态信息
     * @deprecated As of version 2.1, due to ambiguous meaning of the message
     *             parameter. To set a status code use
     *             <code>setStatus(int)</code>, to send an error with a
     *             description use <code>sendError(int, String)</code>.
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public void setStatus(int sc, String sm);

    /**
     * 获取此响应的HTTP状态码.
     *
     * @return 此响应的HTTP状态码
     *
     * @since Servlet 3.0
     */
    public int getStatus();

    /**
     * 返回指定header的值, 或者<code>null</code>.
     * 如果为这个名称添加多个值，则只返回第一个值; 使用{@link #getHeaders(String)}获取所有的值.
     *
     * @param name 要查找的Header名称
     *
     * @return 指定标头的第一个值. 这是原始值，因此如果在第一个头中指定多个值，那么它们将作为单个头值返回.
     *
     * @since Servlet 3.0
     */
    public String getHeader(String name);

    /**
     * 返回与指定标头名称相关联的所有标头值的集合.
     *
     * @param name 要查找的Header名称
     *
     * @return 指定标头的值. 这些是原始值，因此如果在单个标头中指定多个值，则将作为单个标头值返回.
     *
     * @since Servlet 3.0
     */
    public Collection<String> getHeaders(String name);

    /**
     * 获取此HTTP响应的头名称.
     *
     * @return 为HTTP响应设置的标头名称.
     *
     * @since Servlet 3.0
     */
    public Collection<String> getHeaderNames();

    /*
     * Server status codes; see RFC 2068.
     */

    /**
     * 状态码(100) 指示客户端可以继续.
     */
    public static final int SC_CONTINUE = 100;

    /**
     * 状态码(101) 指示服务器正在交换协议，根据 Upgrade header.
     */
    public static final int SC_SWITCHING_PROTOCOLS = 101;

    /**
     * 状态码(200) 指示请求成功正常.
     */
    public static final int SC_OK = 200;

    /**
     * 状态码(201) 指示请求成功并在服务器上创建新资源.
     */
    public static final int SC_CREATED = 201;

    /**
     * 状态码(202) 表示已经接受并处理请求, 但没有完成.
     */
    public static final int SC_ACCEPTED = 202;

    /**
     * 状态码(203) 指示客户端呈现的元信息不是来自服务器.
     */
    public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;

    /**
     * 状态码(204) 指示请求成功，但没有新信息返回.
     */
    public static final int SC_NO_CONTENT = 204;

    /**
     * 状态码(205)指示代理<em>SHOULD</em>重新设置导致发送请求的文档视图.
     */
    public static final int SC_RESET_CONTENT = 205;

    /**
     * 状态码(206) 指示服务器已完成资源的部分GET请求.
     */
    public static final int SC_PARTIAL_CONTENT = 206;

    /**
     * 状态码(300) 指示所请求的资源对应于一组中的任何一个，每一个都有自己的特定位置.
     */
    public static final int SC_MULTIPLE_CHOICES = 300;

    /**
     * 状态码(301) 指示资源已永久移动到新位置, 未来的参考应该使用一个新的URI和它们的请求.
     */
    public static final int SC_MOVED_PERMANENTLY = 301;

    /**
     * 状态码(302) 指示资源已临时移到另一位置, 但是将来的引用仍然应该使用原始URI来访问资源.
     * 此定义保留为向后兼容性. SC_FOUND 现在是首选的定义.
     */
    public static final int SC_MOVED_TEMPORARILY = 302;

    /**
     * 状态码(302) 指示资源暂时驻留在不同的URI下.
     * 因为重定向有时可能会被更改, 客户端应该继续使用请求URI来满足将来的请求. (HTTP/1.1) 表示状态码(302), 建议使用这个变量.
     */
    public static final int SC_FOUND = 302;

    /**
     * 状态码(303) 指示对请求的响应可以在不同的URI下找到.
     */
    public static final int SC_SEE_OTHER = 303;

    /**
     * 状态码(304) 指示条件GET操作发现资源可用且未修改.
     */
    public static final int SC_NOT_MODIFIED = 304;

    /**
     * 状态码(305) 指示必须通过<code><em>Location</em></code>字段指定的代理访问所请求的资源.
     */
    public static final int SC_USE_PROXY = 305;

    /**
     * 状态码 (307)指示请求的资源暂时驻留在不同的URI下.
     * 临时URI应该在响应中的<code><em>Location</em></code>字段指定.
     */
    public static final int SC_TEMPORARY_REDIRECT = 307;

    /**
     * 状态码(400) 指示客户端发送的请求有语法错误.
     */
    public static final int SC_BAD_REQUEST = 400;

    /**
     * 状态码(401) 指示请求需要HTTP身份验证.
     */
    public static final int SC_UNAUTHORIZED = 401;

    /**
     * 状态码(402)预留供将来使用.
     */
    public static final int SC_PAYMENT_REQUIRED = 402;

    /**
     * 状态码(403)指示服务器理解请求，但拒绝执行请求.
     */
    public static final int SC_FORBIDDEN = 403;

    /**
     * 状态码(404) 指示所请求的资源不可用.
     */
    public static final int SC_NOT_FOUND = 404;

    /**
     * 状态码(405) 指示<code><em>Request-Line</em></code>指定的方法不被<code><em>Request-URI</em></code>支持的资源允许.
     */
    public static final int SC_METHOD_NOT_ALLOWED = 405;

    /**
     * 状态码(406) 指示请求标识的资源只能够生成, 根据请求中发送的接受标头，内容特征不可接受的响应实体.
     */
    public static final int SC_NOT_ACCEPTABLE = 406;

    /**
     * 状态码(407)指示客户端必须首先用代理进行身份验证.
     */
    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;

    /**
     * 状态码(408) 指示客户端在服务器准备等待的时间内没有生成请求.
     */
    public static final int SC_REQUEST_TIMEOUT = 408;

    /**
     * 状态码(409)指示由于资源的当前状态发生冲突，无法完成请求.
     */
    public static final int SC_CONFLICT = 409;

    /**
     * 状态码(410) 指示资源在服务器上不再可用，并且不知道转发地址. 这种情况应被视为永久性的.
     */
    public static final int SC_GONE = 410;

    /**
     * 状态码(411) 指示请求没有<code><em>Content-Length</em></code>, 不能被处理.
     */
    public static final int SC_LENGTH_REQUIRED = 411;

    /**
     * 状态码(412) 指示在服务器上测试时，一个或多个请求头字段中给出的前提条件被评估为false.
     */
    public static final int SC_PRECONDITION_FAILED = 412;

    /**
     * 状态码(413) 表示服务器拒绝处理请求，因为请求实体比服务器愿意或能够处理的要大.
     */
    public static final int SC_REQUEST_ENTITY_TOO_LARGE = 413;

    /**
     * 状态码(414) 指示服务器拒绝服务请求，因为<code><em>Request-URI</em></code>比服务器愿意解释的时间长.
     */
    public static final int SC_REQUEST_URI_TOO_LONG = 414;

    /**
     * 状态码(415)指示服务器拒绝提供请求，因为请求实体不是所请求的资源支持的请求方法的格式.
     */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;

    /**
     * 状态码(416)指示服务器不能为所请求的字节范围服务.
     */
    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;

    /**
     * 状态码(417)表示服务器不能满足预期请求标头中的期望值.
     */
    public static final int SC_EXPECTATION_FAILED = 417;

    /**
     * 状态码(500)指示HTTP服务器内部的错误，从而阻止它执行请求.
     */
    public static final int SC_INTERNAL_SERVER_ERROR = 500;

    /**
     * 状态码(501) 指示HTTP服务器不支持实现请求所需的功能.
     */
    public static final int SC_NOT_IMPLEMENTED = 501;

    /**
     * 状态码(502) 指示HTTP服务器在作为代理或网关时从服务器处接收到无效响应.
     */
    public static final int SC_BAD_GATEWAY = 502;

    /**
     * 状态码(503) 表示HTTP服务器暂时超载，无法处理请求.
     */
    public static final int SC_SERVICE_UNAVAILABLE = 503;

    /**
     * 状态码(504)指示服务器在作为网关或代理时没有收到来自上游服务器的及时响应.
     */
    public static final int SC_GATEWAY_TIMEOUT = 504;

    /**
     * 状态码(505) 指示服务器不支持或拒绝支持请求消息中使用的HTTP协议版本.
     */
    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;
}
