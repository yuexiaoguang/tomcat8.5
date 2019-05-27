package javax.servlet.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.ResourceBundle;

import javax.servlet.DispatcherType;
import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;


/**
 * 创建适合Web站点的HTTP servlet的抽象类.
 * 一个<code>HttpServlet</code>子类必须重写至少一个方法, 通常是下列其中之一:
 *
 * <ul>
 * <li> <code>doGet</code>, 如果servlet支持HTTP GET请求
 * <li> <code>doPost</code>, HTTP POST请求
 * <li> <code>doPut</code>, HTTP PUT请求
 * <li> <code>doDelete</code>, HTTP DELETE请求
 * <li> <code>init</code>和<code>destroy</code>, 管理用于servlet生命的资源
 * <li> <code>getServletInfo</code>, servlet用来提供关于自身的信息
 * </ul>
 *
 * <p>几乎没有理由重写<code>service</code>方法.
 * <code>service</code>通过将HTTP请求分派给每个HTTP请求类型的处理程序方法来处理标准HTTP请求(<code>do</code><i>Method</i>上面列出的方法).
 *
 * <p>同样, 没有理由重写<code>doOptions</code>和<code>doTrace</code>方法.
 *
 * <p>servlet通常运行在多线程服务器, 因此，请注意servlet必须处理并发请求，并小心同步对共享资源的访问.
 * 共享资源包括内存数据，例如实例或类变量和外部对象，如文件、数据库连接和网络连接.
 */
public abstract class HttpServlet extends GenericServlet {

    private static final long serialVersionUID = 1L;

    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_TRACE = "TRACE";

    private static final String HEADER_IFMODSINCE = "If-Modified-Since";
    private static final String HEADER_LASTMOD = "Last-Modified";

    private static final String LSTRING_FILE =
        "javax.servlet.http.LocalStrings";
    private static final ResourceBundle lStrings =
        ResourceBundle.getBundle(LSTRING_FILE);


    public HttpServlet() {
    }


    /**
     * 由服务器调用允许servlet处理GET请求 (通过<code>service</code>方法).
     *
     * <p>重写这个方法来支持 GET请求，也自动支持 HTTP HEAD请求. HEAD请求是一个响应中没有主体的GET请求, 只有请求头字段.
     *
     * <p>当重写这个方法的时候, 读取请求数据, 写入响应头, 获取响应的writer或输出流对象, 最后, 写入响应数据.
     * 最好包括内容类型和编码. 当使用一个<code>PrintWriter</code>对象返回响应之前, 设置内容类型.
     *
     * <p>servlet容器必须写入响应头，在提交响应之前, 因为HTTP头必须在响应主体之前发送.
     *
     * <p>如果可能, 设置Content-Length header (使用{@link javax.servlet.ServletResponse#setContentLength}方法),
     * 允许servlet容器使用持久连接返回其对客户端的响应，从而提高性能. 如果整个响应位于响应缓冲区中，则内容长度将自动设置.
     *
     * <p>当使用HTTP 1.1分块编码(也就是说，响应有一个传输编码头), 不会设置Content-Length头.
     *
     * <p>GET方法应该是安全的, 也就是说，没有用户承担责任的任何副作用.
     * 例如，大多数表单查询没有副作用. 如果客户端请求更改存储数据, 请求应该使用其他HTTP方法.
     *
     * <p>如果请求格式不正确, <code>doGet</code>返回一个 HTTP "Bad Request"信息.
     *
     * @param req   客户端对servlet的请求
     *
     * @param resp  servlet发送给客户端的响应
     *
     * @exception IOException   如果servlet处理GET请求时检测到输入或输出错误
     *
     * @exception ServletException  如果无法处理GET请求
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        String protocol = req.getProtocol();
        String msg = lStrings.getString("http.method_get_not_supported");
        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }


    /**
     * 返回<code>HttpServletRequest</code>最后更新的时间, 从January 1, 1970 GMT午夜以来的毫秒数.
     * 如果时间未知, 返回一个负值(默认的).
     *
     * <p>支持HTTP GET请求和可以快速确定他们最后的修改时间的Servlet应该重写这个方法.
     * 这使得浏览器和代理缓存更有效地工作, 减少服务器和网络资源的负载.
     *
     * @param req   发送到servlet的<code>HttpServletRequest</code>对象
     *
     * @return  <code>HttpServletRequest</code>对象最后更新时间, 单位为毫秒，从January 1, 1970 GMT午夜以来的,
     * 			或者-1 如果时间未知
     */
    protected long getLastModified(HttpServletRequest req) {
        return -1;
    }


    /**
     * <p>从<code>service</code>方法接收一个 HTTP HEAD请求并处理请求.
     * 客户端发送一个HEAD请求，当它只想看到响应头时, 例如Content-Type或Content-Length.
     * HTTP HEAD方法计数响应中的输出字节来准确的设置 Content-Length header.
     *
     * <p>如果重写此方法，可以避免计算响应主体，并直接设置响应头以提高性能. 确保<code>doHead</code>方法不会被一个HTTP HEAD请求调用多次.
     *
     * <p>如果HTTP HEAD请求格式不正确, <code>doHead</code>返回一个HTTP "Bad Request"信息.
     *
     * @param req   传递给servlet的请求对象
     *
     * @param resp  servlet用于将头返回给客户端的响应对象
     *
     * @exception IOException   如果出现输入或输出错误
     *
     * @exception ServletException  如果不能处理HEAD请求
     */
    protected void doHead(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (DispatcherType.INCLUDE.equals(req.getDispatcherType())) {
            doGet(req, resp);
        } else {
            NoBodyResponse response = new NoBodyResponse(resp);
            doGet(req, response);
            response.setContentLength();
        }
    }


    /**
     * 由服务器调用(通过<code>service</code>方法)允许一个servlet处理一个POST请求.
     *
     * HTTP POST方法允许客户端一次发送无限长度的数据到Web服务器，在发布诸如信用卡号等信息时非常有用.
     *
     * <p>重写此方法时，读取请求数据，编写响应头，获取响应的作者或输出流对象，最后写出响应数据. 最好包括内容类型和编码.
     * 如果使用<code>PrintWriter</code>对象返回响应, 在访问<code>PrintWriter</code>对象之前设置内容类型.
     *
     * <p>servlet容器在提交响应之前必须写入响应头, 因为在HTTP中header必须在响应主体之前发送.
     *
     * <p>在可能的情况下, 设置Content-Length header (使用{@link javax.servlet.ServletResponse#setContentLength}方法),
     * 允许servlet容器使用持久连接返回其对客户端的响应，从而提高性能. 如果整个响应位于响应缓冲区中，则内容长度将自动设置.
     *
     * <p>当使用HTTP 1.1块编码(意味着响应有一个Transfer-Encoding header), 不能设置Content-Length header.
     *
     * <p>此方法不需要是安全的或幂等的.
     * 通过POST请求的操作可能有副作用，用户可以承担责任，例如，更新存储的数据或在线购买物品.
     *
     * <p>如果HTTP POST请求格式不正确, <code>doPost</code>返回一个HTTP "Bad Request"信息.
     *
     *
     * @param req   客户端对servlet所做的请求
     *
     * @param resp  servlet发送给客户端的响应
     *
     * @exception IOException   如果servlet处理请求时检测到输入或输出错误
     *
     * @exception ServletException  如果不能处理POST请求
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String protocol = req.getProtocol();
        String msg = lStrings.getString("http.method_post_not_supported");
        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }


    /**
     * 由服务器调用(通过<code>service</code>方法)允许一个servlet处理一个PUT请求.
     *
     * PUT操作允许客户端在服务器上放置文件，类似于通过FTP发送文件.
     *
     * <p>重写此方法时，请原封不动地保留随请求发送的任何内容标头(包括
     * Content-Length, Content-Type, Content-Transfer-Encoding,
     * Content-Encoding, Content-Base, Content-Language, Content-Location, Content-MD5, and Content-Range).
     * 如果方法不能处理内容标头, 它必须发出错误消息 (HTTP 501 - Not Implemented)并丢弃请求.
     * 有关HTTP 1.1的更多信息, see RFC 2616 <a href="http://www.ietf.org/rfc/rfc2616.txt"></a>.
     *
     * <p>此方法不需要是安全的或幂等的. 使用此方法时，将受影响的URL副本保存在临时存储中可能是有用的.
     *
     * <p>如果HTTP PUT请求格式不正确, <code>doPut</code>返回一个HTTP "Bad Request"信息.
     *
     * @param req   客户端对servlet所做的请求
     *
     * @param resp  servlet发送给客户端的响应
     *
     * @exception IOException   如果servlet处理请求时检测到输入或输出错误
     *
     * @exception ServletException  如果不能处理PUT请求
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String protocol = req.getProtocol();
        String msg = lStrings.getString("http.method_put_not_supported");
        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }


    /**
     * 由服务器调用(通过<code>service</code>方法)允许一个servlet处理一个DELETE请求.
     *
     * DELETE操作允许客户端从服务器上删除文档或网页.
     *
     * <p>此方法不需要是安全的或幂等的. 使用此方法时，将受影响的URL副本保存在临时存储中可能是有用的.
     *
     * <p>如果HTTP DELETE请求格式不正确, <code>doDelete</code>返回一个HTTP "Bad Request"信息.
     *
     * @param req   客户端对servlet所做的请求
     *
     * @param resp  servlet发送给客户端的响应
     *
     * @exception IOException   如果servlet处理请求时检测到输入或输出错误
     *
     * @exception ServletException  如果不能处理请求
     */
    protected void doDelete(HttpServletRequest req,
                            HttpServletResponse resp)
        throws ServletException, IOException {

        String protocol = req.getProtocol();
        String msg = lStrings.getString("http.method_delete_not_supported");
        if (protocol.endsWith("1.1")) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }


    private static Method[] getAllDeclaredMethods(Class<?> c) {

        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());
        Method[] thisMethods = c.getDeclaredMethods();

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods =
                new Method[parentMethods.length + thisMethods.length];
            System.arraycopy(parentMethods, 0, allMethods, 0,
                             parentMethods.length);
            System.arraycopy(thisMethods, 0, allMethods, parentMethods.length,
                             thisMethods.length);

            thisMethods = allMethods;
        }

        return thisMethods;
    }


    /**
     * 由服务器调用(通过<code>service</code>方法)允许一个servlet处理一个OPTIONS请求.
     *
     * OPTIONS请求确定服务器支持哪些HTTP方法并返回适当的标头.
     * 例如, 如果一个servlet重写<code>doGet</code>, 此方法返回以下标头:
     *
     * <p><code>Allow: GET, HEAD, TRACE, OPTIONS</code>
     *
     * <p>除非servlet实现了新的HTTP方法，超过HTTP 1.1实现的那些，否则不需要重写此方法.
     *
     * @param req   客户端对servlet所做的请求
     *
     * @param resp  servlet发送给客户端的响应
     *
     * @exception IOException   如果servlet处理请求时检测到输入或输出错误
     *
     * @exception ServletException  如果不能处理请求
     */
    protected void doOptions(HttpServletRequest req,
            HttpServletResponse resp)
        throws ServletException, IOException {

        Method[] methods = getAllDeclaredMethods(this.getClass());

        boolean ALLOW_GET = false;
        boolean ALLOW_HEAD = false;
        boolean ALLOW_POST = false;
        boolean ALLOW_PUT = false;
        boolean ALLOW_DELETE = false;
        boolean ALLOW_TRACE = true;
        boolean ALLOW_OPTIONS = true;

        // Tomcat specific hack to see if TRACE is allowed
        Class<?> clazz = null;
        try {
            clazz = Class.forName("org.apache.catalina.connector.RequestFacade");
            Method getAllowTrace = clazz.getMethod("getAllowTrace", (Class<?>[]) null);
            ALLOW_TRACE = ((Boolean) getAllowTrace.invoke(req, (Object[]) null)).booleanValue();
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
                IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // Ignore. Not running on Tomcat. TRACE is always allowed.
        }
        // End of Tomcat specific hack

        for (int i=0; i<methods.length; i++) {
            Method m = methods[i];

            if (m.getName().equals("doGet")) {
                ALLOW_GET = true;
                ALLOW_HEAD = true;
            }
            if (m.getName().equals("doPost"))
                ALLOW_POST = true;
            if (m.getName().equals("doPut"))
                ALLOW_PUT = true;
            if (m.getName().equals("doDelete"))
                ALLOW_DELETE = true;
        }

        String allow = null;
        if (ALLOW_GET)
            allow=METHOD_GET;
        if (ALLOW_HEAD)
            if (allow==null) allow=METHOD_HEAD;
            else allow += ", " + METHOD_HEAD;
        if (ALLOW_POST)
            if (allow==null) allow=METHOD_POST;
            else allow += ", " + METHOD_POST;
        if (ALLOW_PUT)
            if (allow==null) allow=METHOD_PUT;
            else allow += ", " + METHOD_PUT;
        if (ALLOW_DELETE)
            if (allow==null) allow=METHOD_DELETE;
            else allow += ", " + METHOD_DELETE;
        if (ALLOW_TRACE)
            if (allow==null) allow=METHOD_TRACE;
            else allow += ", " + METHOD_TRACE;
        if (ALLOW_OPTIONS)
            if (allow==null) allow=METHOD_OPTIONS;
            else allow += ", " + METHOD_OPTIONS;

        resp.setHeader("Allow", allow);
    }


    /**
     * 由服务器调用(通过<code>service</code>方法)允许一个servlet处理一个TRACE请求.
     *
     * TRACE将TRACE请求发送的标头返回给客户机, 以便它们可以用于调试.
     * 没有必要重写这个方法.
     *
     * @param req   客户端对servlet所做的请求
     *
     * @param resp  servlet发送给客户端的响应
     *
     * @exception IOException   如果servlet处理请求时检测到输入或输出错误
     *
     * @exception ServletException  如果不能处理请求
     */
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {

        int responseLength;

        String CRLF = "\r\n";
        StringBuilder buffer = new StringBuilder("TRACE ").append(req.getRequestURI())
            .append(" ").append(req.getProtocol());

        Enumeration<String> reqHeaderEnum = req.getHeaderNames();

        while( reqHeaderEnum.hasMoreElements() ) {
            String headerName = reqHeaderEnum.nextElement();
            buffer.append(CRLF).append(headerName).append(": ")
                .append(req.getHeader(headerName));
        }

        buffer.append(CRLF);

        responseLength = buffer.length();

        resp.setContentType("message/http");
        resp.setContentLength(responseLength);
        ServletOutputStream out = resp.getOutputStream();
        out.print(buffer.toString());
        out.close();
        return;
    }


    /**
     * 从public <code>service</code>方法接收标准的HTTP请求，并分派它们到<code>do</code><i>Method</i>方法.
     * 这个方法是一个指定HTTP版本的{@link javax.servlet.Servlet#service}方法.
     * 没有必要重写这个方法.
     *
     * @param req   客户端对servlet所做的请求
     *
     * @param resp  servlet发送给客户端的响应
     *
     * @exception IOException   如果servlet处理请求时检测到输入或输出错误
     *
     * @exception ServletException  如果不能处理请求
     */
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String method = req.getMethod();

        if (method.equals(METHOD_GET)) {
            long lastModified = getLastModified(req);
            if (lastModified == -1) {
                // servlet不支持 if-modified-since, 没有理由再去考虑昂贵的逻辑
                doGet(req, resp);
            } else {
                long ifModifiedSince;
                try {
                    ifModifiedSince = req.getDateHeader(HEADER_IFMODSINCE);
                } catch (IllegalArgumentException iae) {
                    // 无效的日期头 - 继续，如果没有设置
                    ifModifiedSince = -1;
                }
                if (ifModifiedSince < (lastModified / 1000 * 1000)) {
                    // 如果servlet mod时间较晚, 调用doGet() 向下舍入到最近的第二个，以便进行适当的比较
                    // -1 ifModifiedSince总是少一些
                    maybeSetLastModified(resp, lastModified);
                    doGet(req, resp);
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                }
            }

        } else if (method.equals(METHOD_HEAD)) {
            long lastModified = getLastModified(req);
            maybeSetLastModified(resp, lastModified);
            doHead(req, resp);

        } else if (method.equals(METHOD_POST)) {
            doPost(req, resp);

        } else if (method.equals(METHOD_PUT)) {
            doPut(req, resp);

        } else if (method.equals(METHOD_DELETE)) {
            doDelete(req, resp);

        } else if (method.equals(METHOD_OPTIONS)) {
            doOptions(req,resp);

        } else if (method.equals(METHOD_TRACE)) {
            doTrace(req,resp);

        } else {
            //
            // 注意，这意味着servlet在服务器上的任何地方都不支持所请求的任何方法.
            //

            String errMsg = lStrings.getString("http.method_not_implemented");
            Object[] errArgs = new Object[1];
            errArgs[0] = method;
            errMsg = MessageFormat.format(errMsg, errArgs);

            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg);
        }
    }


    /*
     * 设置Last-Modified 实体头字段，如果它还没有设置，或者值是有意义的.
     * 在doGet之前调用, 确保在响应数据写入之前设置标头. 一个子类可能已经设置了这个头，所以我们检查.
     */
    private void maybeSetLastModified(HttpServletResponse resp,
                                      long lastModified) {
        if (resp.containsHeader(HEADER_LASTMOD))
            return;
        if (lastModified >= 0)
            resp.setDateHeader(HEADER_LASTMOD, lastModified);
    }


    /**
     * 分发客户端请求到protected <code>service</code>方法. 不需要重写这个方法.
     *
     * @param req   客户端对servlet所做的请求
     *
     * @param res   servlet返回给客户端的响应
     *
     * @exception IOException   如果servlet处理HTTP请求时发生输入或输出错误
     *
     * @exception ServletException  如果无法处理HTTP请求
     */
    @Override
    public void service(ServletRequest req, ServletResponse res)
        throws ServletException, IOException {

        HttpServletRequest  request;
        HttpServletResponse response;

        try {
            request = (HttpServletRequest) req;
            response = (HttpServletResponse) res;
        } catch (ClassCastException e) {
            throw new ServletException("non-HTTP request or response");
        }
        service(request, response);
    }
}


/*
 * 在 "HEAD"支持中使用的响应的包装对象.
 * 没有主体，计算字节，以便适当地设置内容长度. 所有其他方法都委托给包装好的servlet响应对象.
 */
class NoBodyResponse extends HttpServletResponseWrapper {
    private final NoBodyOutputStream noBody;
    private PrintWriter writer;
    private boolean didSetContentLength;

    // file private
    NoBodyResponse(HttpServletResponse r) {
        super(r);
        noBody = new NoBodyOutputStream();
    }

    // file private
    void setContentLength() {
        if (!didSetContentLength) {
            if (writer != null) {
                writer.flush();
            }
            super.setContentLength(noBody.getContentLength());
        }
    }


    // SERVLET RESPONSE interface methods

    @Override
    public void setContentLength(int len) {
        super.setContentLength(len);
        didSetContentLength = true;
    }

    @Override
    public void setContentLengthLong(long len) {
        super.setContentLengthLong(len);
        didSetContentLength = true;
    }

    @Override
    public void setHeader(String name, String value) {
        super.setHeader(name, value);
        checkHeader(name);
    }

    @Override
    public void addHeader(String name, String value) {
        super.addHeader(name, value);
        checkHeader(name);
    }

    @Override
    public void setIntHeader(String name, int value) {
        super.setIntHeader(name, value);
        checkHeader(name);
    }

    @Override
    public void addIntHeader(String name, int value) {
        super.addIntHeader(name, value);
        checkHeader(name);
    }

    private void checkHeader(String name) {
        if ("content-length".equalsIgnoreCase(name)) {
            didSetContentLength = true;
        }
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return noBody;
    }

    @Override
    public PrintWriter getWriter() throws UnsupportedEncodingException {

        if (writer == null) {
            OutputStreamWriter w;

            w = new OutputStreamWriter(noBody, getCharacterEncoding());
            writer = new PrintWriter(w);
        }
        return writer;
    }
}


/*
 * servlet输出流，没有数据.
 */

// file private
class NoBodyOutputStream extends ServletOutputStream {

    private static final String LSTRING_FILE =
        "javax.servlet.http.LocalStrings";
    private static final ResourceBundle lStrings =
        ResourceBundle.getBundle(LSTRING_FILE);

    private int contentLength = 0;

    // file private
    NoBodyOutputStream() {
        // NOOP
    }

    // file private
    int getContentLength() {
        return contentLength;
    }

    @Override
    public void write(int b) {
        contentLength++;
    }

    @Override
    public void write(byte buf[], int offset, int len) throws IOException {
        if (buf == null) {
            throw new NullPointerException(
                    lStrings.getString("err.io.nullArray"));
        }

        if (offset < 0 || len < 0 || offset+len > buf.length) {
            String msg = lStrings.getString("err.io.indexOutOfBounds");
            Object[] msgArgs = new Object[3];
            msgArgs[0] = Integer.valueOf(offset);
            msgArgs[1] = Integer.valueOf(len);
            msgArgs[2] = Integer.valueOf(buf.length);
            msg = MessageFormat.format(msg, msgArgs);
            throw new IndexOutOfBoundsException(msg);
        }

        contentLength += len;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setWriteListener(javax.servlet.WriteListener listener) {
    }
}
