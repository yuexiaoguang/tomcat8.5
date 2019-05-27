package javax.servlet.http;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;

/**
 * 继承{@link javax.servlet.ServletRequest}接口给HTTP servlet提供请求信息.
 * <p>
 * servlet容器创建一个<code>HttpServletRequest</code>对象并将它作为一个参数传递给servlet的service方法
 * (<code>doGet</code>, <code>doPost</code>, etc).
 */
public interface HttpServletRequest extends ServletRequest {

    /**
     * 基本身份验证的字符串标识符. Value "BASIC"
     */
    public static final String BASIC_AUTH = "BASIC";
    /**
     * 表单身份验证的字符串标识符. Value "FORM"
     */
    public static final String FORM_AUTH = "FORM";
    /**
     * 用于客户端证书身份验证的字符串标识符. Value "CLIENT_CERT"
     */
    public static final String CLIENT_CERT_AUTH = "CLIENT_CERT";
    /**
     * 摘要身份验证的字符串标识符. Value "DIGEST"
     */
    public static final String DIGEST_AUTH = "DIGEST";

    /**
     * 返回用于保护servlet的身份验证方案的名称.
     * 所有servlet容器都支持基本、表单和客户端证书身份验证，并且还可以支持摘要身份验证. 如果servlet未经验证，返回<code>null</code>.
     * <p>
     * 和CGI 变量 AUTH_TYPE的值相等.
     *
     * @return 静态成员BASIC_AUTH, FORM_AUTH, CLIENT_CERT_AUTH, DIGEST_AUTH的其中之一 (suitable for == comparison)
     * 			或表示身份验证方案的容器特定字符串, 或者<code>null</code>如果请求未经验证.
     */
    public String getAuthType();

    /**
     * 返回这个客户端发送的一组<code>Cookie</code>对象. 如果没有cookie发送返回<code>null</code>.
     *
     * @return 这个请求包含的<code>Cookies</code>, 或者<code>null</code>
     */
    public Cookie[] getCookies();

    /**
     * 返回表示一个<code>Date</code>对象的请求头. 和包含日期的header一起使用, 例如<code>If-Modified-Since</code>.
     * <p>
     * 日期以毫秒数返回，从January 1, 1970 GMT开始. header名称不区分大小写.
     * <p>
     * 如果请求没有指定名称的标头, 返回 -1.
     *
     * @param name 指定的header的名称
     * @return 日期以毫秒数返回，从January 1, 1970 GMT开始, 如果请求没有指定名称的标头, 返回 -1.
     * @exception IllegalArgumentException 如果标头的值不能转换为日期
     */
    public long getDateHeader(String name);

    /**
     * 返回指定请求标头的值.
     * 如果请求不包含指定名称的标头, 返回<code>null</code>. 如果有多个同名的标头, 此方法返回请求中的第一个标头.
     * 头名称不区分大小写. 您可以在任何请求标头中使用此方法.
     *
     * @param name 指定的header名称
     * @return 请求头的值, 或者<code>null</code>
     */
    public String getHeader(String name);

    /**
     * 返回指定请求标头的所有值.
     * <p>
     * 一些header, 例如<code>Accept-Language</code>可以由客户端发送为具有不同值的几个头，而不是以逗号分隔的列表发送头.
     * <p>
     * 如果请求不包含指定名称的任何标头, 返回空的<code>Enumeration</code>. 头名称不区分大小写. 您可以在任何请求标头中使用此方法.
     *
     * @param name 指定的header名称
     * @return 包含请求头的所有值的<code>Enumeration</code>. 如果请求没有该名称的任何标头，则返回空枚举. 如果容器不允许访问头信息, 返回null
     */
    public Enumeration<String> getHeaders(String name);

    /**
     * 返回此请求包含的所有请求头名称的枚举.
     *
     * @return  用此请求发送的所有头名称的枚举;
     * 			如果请求没有header, 返回一个空枚举;
     * 			一些servlet容器不允许servlet使用这种方法访问标头, 则返回<code>null</code>
     */
    public Enumeration<String> getHeaderNames();

    /**
     * 返回指定请求标头的值.
     * <p>
     * 标头名称不区分大小写.
     *
     * @param name  指定请求标头
     * @return 如果请求没有指定名称的标头, 返回 -1
     * @exception NumberFormatException  如果标头不能转换为<code>int</code>
     */
    public int getIntHeader(String name);

    /**
     * 返回请求的HTTP方法名称, 例如, GET, POST, PUT. 和CGI变量REQUEST_METHOD相同.
     */
    public String getMethod();

    /**
     * 返回与发出此请求的客户端发送的URL相关联的任何额外路径信息.
     * 额外的路径信息遵循servlet路径，但位于查询字符串之前，并且以 "/"字符开头.
     * <p>
     * 如果没有额外路径信息返回<code>null</code>.
     * <p>
     * 和CGI变量PATH_INFO相同.
     *
     * @return 由Web容器解码, 指定在servlet路径之后，但在请求URL中的查询字符串之前出现的额外路径信息; 或者<code>null</code>
     */
    public String getPathInfo();

    /**
     * 返回servlet名称后面，查询字符串之前的的额外路径信息, 并将其转换为真正的路径. 和CGI变量PATH_TRANSLATED的值相同.
     * <p>
     * 如果URL没有任何额外的路径信息或者servlet容器不能以任何理由将虚拟路径转换为真正的路径, 返回<code>null</code>(例如，当Web应用程序从归档文件中执行时).
     * Web容器不解码此字符串.
     *
     * @return 指定真实路径, 或者<code>null</code>
     */
    public String getPathTranslated();

    /**
     * 返回指示请求上下文的请求URI的一部分.
     * 上下文路径总是首先出现在请求URI中. 以"/"开头但不以"/"字符结尾的的路径. 对于默认上下文的servlet, 返回"".
     * 容器不解码此字符串.
     *
     * @return 请求上下文的请求URI的一部分
     */
    public String getContextPath();

    /**
     * 返回路径后面请求URL中包含的查询字符串. 如果URL没有查询字符串返回<code>null</code>.
     * 和CGI变量QUERY_STRING的值相同.
     *
     * @return 包含的查询字符串或<code>null</code>. 容器不会解码该值.
     */
    public String getQueryString();

    /**
     * 返回此请求的用户登录名, 如果用户已被验证；或者如果用户未经身份验证返回<code>null</code>.
     * 每个后续请求是否发送用户名取决于浏览器和身份验证类型. 和CGI变量REMOTE_USER的值相同.
     *
     * @return 指定作出此请求的用户的登录名, 或<code>null</code>
     */
    public String getRemoteUser();

    /**
     * 返回一个布尔值，指示已验证的用户是否包含在指定的逻辑“角色”中.
     * 可以使用部署描述符定义的角色和角色成员关系. 如果用户未经身份验证, 返回<code>false</code>.
     *
     * @param role 角色名
     * @return 作出此请求的用户是否属于给定角色; 或者<code>false</code>
     */
    public boolean isUserInRole(String role);

    /**
     * 返回一个<code>java.security.Principal</code>对象， 包含当前已验证用户的名称.
     * 如果用户未经身份验证, 返回<code>null</code>.
     */
    public java.security.Principal getUserPrincipal();

    /**
     * 返回客户端指定的会话ID.
     * 这可能与此请求当前有效会话的ID不一样. 如果客户端没有指定会话ID, 返回<code>null</code>.
     */
    public String getRequestedSessionId();

    /**
     * 返回此请求的URL来自协议名称的一部分直到HTTP请求的第一行中的查询字符串.
     * Web容器不解码此字符串. 例如:
     * <table summary="Examples of Returned Values">
     * <tr align=left>
     * <th>First line of HTTP request</th>
     * <th>Returned Value</th>
     * <tr>
     * <td>POST /some/path.html HTTP/1.1
     * <td>
     * <td>/some/path.html
     * <tr>
     * <td>GET http://foo.bar/a.html HTTP/1.0
     * <td>
     * <td>/a.html
     * <tr>
     * <td>HEAD /xyz?a=b HTTP/1.1
     * <td>
     * <td>/xyz
     * </table>
     * <p>
     * 用一个方案和一个主机来重构一个URL, 使用{@link #getRequestURL}.
     *
     * @return 从协议名称到查询字符串的URL的一部分
     */
    public String getRequestURI();

    /**
     * 重建用于发出请求的客户端的url. 返回的URL包含一个协议、服务器名称、端口号和服务器路径，但不包含查询字符串参数.
     * <p>
     * 因为这个方法返回一个<code>StringBuffer</code>, 不是一个string, 可以很轻松的编辑URL, 例如, 插入查询参数.
     * <p>
     * 此方法对于创建重定向消息和报告错误非常有用.
     *
     * @return 重建的URL
     */
    public StringBuffer getRequestURL();

    /**
     * 返回调用servlet的请求URL的一部分.
     * 这个路径以"/"字符开头， 并包含servlet名称或servlet的路径, 但不包含任何额外的路径信息或查询字符串.
     * 和CGI变量 SCRIPT_NAME相同.
     * <p>
     * 此方法将返回空字符串("")，如果 servlet用于处理"/*"模式的请求.
     *
     * @return 被调用的servlet的名称或路径, 如请求URL中指定的, 解码, 或返回空字符串("")，如果 servlet用于处理"/*"模式的请求.
     */
    public String getServletPath();

    /**
     * 返回这个请求当前关联的<code>HttpSession</code>, 如果没有当前会话且<code>create</code>是 true, 返回一个新的会话.
     * <p>
     * 如果<code>create</code>是<code>false</code>，而且请求没有有效的<code>HttpSession</code>, 返回<code>null</code>.
     * <p>
     * 确保会话正确维护, 必须在响应提交之前调用此方法. 如果容器使用cookie维护会话完整性，并要求在响应提交时创建一个新会话, 将抛出一个IllegalStateException异常.
     *
     * @param create <code>true</code>为这个请求创建一个新会话; <code>false</code> 返回<code>null</code>
     * @return 这个请求当前关联的<code>HttpSession</code>，或者<code>null</code>，如果<code>create</code>是<code>false</code>
     *         并且请求没有有效的会话
     */
    public HttpSession getSession(boolean create);

    /**
     * 返回与此请求关联的当前会话, 或者如果请求没有会话, 创建一个.
     *
     * @return 这个请求当前关联的<code>HttpSession</code>
     */
    public HttpSession getSession();

    /**
     * 更改与此请求关联的会话的ID.
     * 此方法不创建新会话对象，它只更改当前会话的ID.
     *
     * @return 分配给会话的新会话id
     */
    public String changeSessionId();

    /**
     * 检查请求的会话ID是否仍然有效.
     *
     * @return <code>true</code>如果此请求具有当前会话上下文中有效会话的ID; 否则<code>false</code>
     */
    public boolean isRequestedSessionIdValid();

    /**
     * 检查请求的会话ID是否作为cookie传入.
     *
     * @return <code>true</code>如果会话id作为cookie传入; 否则<code>false</code>
     */
    public boolean isRequestedSessionIdFromCookie();

    /**
     * 检查请求的会话ID是否作为请求URL的一部分传入.
     *
     * @return <code>true</code>如果会话id作为URL的一部分传入; 否则<code>false</code>
     */
    public boolean isRequestedSessionIdFromURL();

    /**
     * @return {@link #isRequestedSessionIdFromURL()}
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *             {@link #isRequestedSessionIdFromURL} instead.
     */
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public boolean isRequestedSessionIdFromUrl();

    /**
     * 触发相同的身份验证过程，如果请求是由受安全约束保护的资源触发的.
     *
     * @param response  用于返回任何身份验证的响应
     * @return <code>true</code> 如果用户已成功验证；否则<code>false</code>
     *
     * @throws IOException 如果身份验证过程试图从请求读取或写入响应，则发生I/O错误
     * @throws IllegalStateException 如果身份验证过程在提交后尝试写入响应
     * @throws ServletException 如果身份验证失败，调用者将要处理失败
     */
    public boolean authenticate(HttpServletResponse response)
            throws IOException, ServletException;

    /**
     * 验证所提供的用户名和密码，然后将经过身份验证的用户与请求相关联.
     *
     * @param username  要验证的用户名
     * @param password  用于对用户进行身份验证的密码
     *
     * @throws ServletException 如果{@link #getRemoteUser()}, {@link #getUserPrincipal()}, {@link #getAuthType()}其中之一 
     *             不是null, 如果配置身份验证不支持用户名和密码认证， 或者如果身份验证失败
     */
    public void login(String username, String password) throws ServletException;

    /**
     * 从请求中删除任何经过身份验证的用户.
     *
     * @throws ServletException 如果注销失败
     */
    public void logout() throws ServletException;

    /**
     * 返回所有上传部分的集合.
     *
     * @return 所有上传部分的集合.
     * @throws IOException 如果出现I/O错误
     * @throws IllegalStateException 如果大小超过限制或没有提供多重配置
     * @throws ServletException 如果请求不是multipart/form-data
     */
    public Collection<Part> getParts() throws IOException, ServletException;

    /**
     * 如果Part不存在，则获得命名Part或NULL. 触发所有Part的上传.
     *
     * @param name 要获得的Part的名称
     *
     * @return 命名Part 或 null
     * @throws IOException 如果出现I/O错误
     * @throws IllegalStateException 如果超过了大小限制
     * @throws ServletException 如果请求不是multipart/form-data
     */
    public Part getPart(String name) throws IOException,
            ServletException;

    /**
     * 启动HTTP升级进程，并在当前请求/响应对完成处理后将连接传递给所提供的协议处理程序.
     * 调用此方法将设置响应状态 {@link HttpServletResponse#SC_SWITCHING_PROTOCOLS}并刷新响应.
     * 在调用此方法之前，必须先设置特定于协议的标头.
     *
     * @param <T>                     升级处理程序的类型
     * @param httpUpgradeHandlerClass 实现升级处理程序的类
     *
     * @return 指定的升级处理程序类型的新创建实例
     *
     * @throws IOException 如果升级期间发生I/O错误
     * @throws ServletException 如果给定的httpUpgradeHandlerClass 未能实例化
     */
    public <T extends HttpUpgradeHandler> T upgrade( Class<T> httpUpgradeHandlerClass) throws java.io.IOException, ServletException;
}
