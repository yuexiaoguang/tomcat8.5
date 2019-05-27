package org.apache.catalina.connector;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.SessionConfig;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.UEncoder;
import org.apache.tomcat.util.buf.UEncoder.SafeCharsSet;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.MediaTypeCache;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * Coyote response的封装对象.
 */
public class Response implements HttpServletResponse {

    private static final Log log = LogFactory.getLog(Response.class);
    protected static final StringManager sm = StringManager.getManager(Response.class);

    private static final MediaTypeCache MEDIA_TYPE_CACHE = new MediaTypeCache(100);

    /**
     * 遵守 SRV.15.2.22.1. 调用Response.getWriter()，如果未指定字符编码，则将导致后续的调用
     * Response.getCharacterEncoding() 返回 ISO-8859-1 ，并且 Content-Type
     * 响应 header将包括 charset=ISO-8859-1.
     */
    private static final boolean ENFORCE_ENCODING_IN_GET_WRITER;

    static {
        ENFORCE_ENCODING_IN_GET_WRITER = Boolean.parseBoolean(
                System.getProperty("org.apache.catalina.connector.Response.ENFORCE_ENCODING_IN_GET_WRITER",
                        "true"));
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 用于创建日期标头的日期格式.
     */
    protected SimpleDateFormat format = null;


    // ------------------------------------------------------------- Properties

    /**
     * 设置接收该请求的连接器.
     *
     * @param connector The new connector
     */
    public void setConnector(Connector connector) {
        if("AJP/1.3".equals(connector.getProtocol())) {
            // default size to size of one ajp-packet
            outputBuffer = new OutputBuffer(8184);
        } else {
            outputBuffer = new OutputBuffer();
        }
        outputStream = new CoyoteOutputStream(outputBuffer);
        writer = new CoyoteWriter(outputBuffer);
    }


    /**
     * Coyote 响应.
     */
    protected org.apache.coyote.Response coyoteResponse;

    /**
     * 设置Coyote 响应.
     *
     * @param coyoteResponse Coyote响应
     */
    public void setCoyoteResponse(org.apache.coyote.Response coyoteResponse) {
        this.coyoteResponse = coyoteResponse;
        outputBuffer.setResponse(coyoteResponse);
    }

    /**
     * @return Coyote响应.
     */
    public org.apache.coyote.Response getCoyoteResponse() {
        return this.coyoteResponse;
    }


    /**
     * @return 正在处理此请求的上下文.
     */
    public Context getContext() {
        return (request.getContext());
    }


    /**
     * 关联的输出缓冲区.
     */
    protected OutputBuffer outputBuffer;


    /**
     * 关联的输出流.
     */
    protected CoyoteOutputStream outputStream;


    /**
     * The associated writer.
     */
    protected CoyoteWriter writer;


    /**
     * 应用程序提交标志.
     */
    protected boolean appCommitted = false;


    /**
     * The included flag.
     */
    protected boolean included = false;


    /**
     * The characterEncoding flag
     */
    private boolean isCharacterEncodingSet = false;

    /**
     * 随着异步处理的引入和非容器线程调用sendError()跟踪当前错误状态的可能性， 确保正确的错误页被调用变得更加复杂.
     * 此状态属性通过跟踪当前错误状态帮助通知调用方，如果更改成功或如果另一个线程首先到达，则尝试更改状态.
     *
     * <pre>
     * 状态机非常简单:
     *
     * 0 - NONE
     * 1 - NOT_REPORTED
     * 2 - REPORTED
     *
     *
     *   -->---->-- >NONE
     *   |   |        |
     *   |   |        | setError()
     *   ^   ^        |
     *   |   |       \|/
     *   |   |-<-NOT_REPORTED
     *   |            |
     *   ^            | report()
     *   |            |
     *   |           \|/
     *   |----<----REPORTED
     * </pre>
     */
    private final AtomicInteger errorState = new AtomicInteger(0);


    /**
     * 是否使用输出流.
     */
    protected boolean usingOutputStream = false;


    /**
     * 是否使用writer.
     */
    protected boolean usingWriter = false;


    /**
     * URL 编码器.
     */
    protected final UEncoder urlEncoder = new UEncoder(SafeCharsSet.WITH_SLASH);


    /**
     * 可循环缓冲区来保存重定向URL.
     */
    protected final CharChunk redirectURLCC = new CharChunk();


    /*
     * 不是严格要求的, 但是如果保留这些信息，HTTP/2推送请求则会容易得多, 直到响应被回收为止.
     */
    private final List<Cookie> cookies = new ArrayList<>();

    private HttpServletResponse applicationResponse = null;


    // --------------------------------------------------------- Public Methods

    /**
     * 释放所有对象引用, 初始化实例变量, 准备重用这个对象.
     */
    public void recycle() {

        cookies.clear();
        outputBuffer.recycle();
        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        included = false;
        errorState.set(0);
        isCharacterEncodingSet = false;

        applicationResponse = null;
        if (Globals.IS_SECURITY_ENABLED || Connector.RECYCLE_FACADES) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
            if (outputStream != null) {
                outputStream.clear();
                outputStream = null;
            }
            if (writer != null) {
                writer.clear();
                writer = null;
            }
        } else {
            writer.recycle();
        }

    }


    public List<Cookie> getCookies() {
        return cookies;
    }


    // ------------------------------------------------------- Response Methods

    /**
     * @return 应用程序实际写入输出流的字节数. 不包括分块, 压缩, etc. 包括headers.
     */
    public long getContentWritten() {
        return outputBuffer.getContentWritten();
    }


    /**
     * @return 实际写入套接字的字节数. 包括分块, 压缩, etc. 但排除 headers.
     * @param flush 如果是<code>true</code>将首先执行缓冲区刷新
     */
    public long getBytesWritten(boolean flush) {
        if (flush) {
            try {
                outputBuffer.flush();
            } catch (IOException ioe) {
                // Ignore - the client has probably closed the connection
            }
        }
        return getCoyoteResponse().getBytesWritten(flush);
    }

    /**
     * 应用是否提交.
     *
     * @param appCommitted 是否提交
     */
    public void setAppCommitted(boolean appCommitted) {
        this.appCommitted = appCommitted;
    }


    /**
     * 应用是否提交.
     *
     * @return <code>true</code>如果应用程序已提交响应
     */
    public boolean isAppCommitted() {
        return (this.appCommitted || isCommitted() || isSuspended()
                || ((getContentLength() > 0)
                    && (getContentWritten() >= getContentLength())));
    }


    /**
     * 与此响应相关联的请求.
     */
    protected Request request = null;

    /**
     * @return 与此响应相关联的请求.
     */
    public org.apache.catalina.connector.Request getRequest() {
        return (this.request);
    }

    /**
     * 设置与此响应相关联的请求.
     *
     * @param request 关联的请求
     */
    public void setRequest(org.apache.catalina.connector.Request request) {
        this.request = request;
    }


    /**
     * 这个响应的外观.
     */
    protected ResponseFacade facade = null;


    /**
     * @return 原始<code>ServletResponse</code>.
     */
    public HttpServletResponse getResponse() {
        if (facade == null) {
            facade = new ResponseFacade(this);
        }
        if (applicationResponse == null) {
            applicationResponse = facade;
        }
        return applicationResponse;
    }


    /**
     * 设置传递给应用的包装的HttpServletResponse.
     * 希望包装响应的组件应该得到响应, 通过{@link #getResponse()}, 包装它，然后用包裹的响应调用这个方法.
     *
     * @param applicationResponse 传递给应用程序的包装的响应
     */
    public void setResponse(HttpServletResponse applicationResponse) {
        // Check the wrapper wraps this request
        ServletResponse r = applicationResponse;
        while (r instanceof HttpServletResponseWrapper) {
            r = ((HttpServletResponseWrapper) r).getResponse();
        }
        if (r != facade) {
            throw new IllegalArgumentException(sm.getString("response.illegalWrap"));
        }
        this.applicationResponse = applicationResponse;
    }


    public void setSuspended(boolean suspended) {
        outputBuffer.setSuspended(suspended);
    }


    /**
     * @return <code>true</code> 如果响应暂停
     */
    public boolean isSuspended() {
        return outputBuffer.isSuspended();
    }


    /**
     * @return <code>true</code>如果响应已被关闭
     */
    public boolean isClosed() {
        return outputBuffer.isClosed();
    }


    /**
     * @return <code>false</code>如果错误标志已经被设置
     */
    public boolean setError() {
        boolean result = errorState.compareAndSet(0, 1);
        if (result) {
            Wrapper wrapper = getRequest().getWrapper();
            if (wrapper != null) {
                wrapper.incrementErrorCount();
            }
        }
        return result;
    }


    /**
     * @return <code>true</code>如果响应遇到错误
     */
    public boolean isError() {
        return errorState.get() > 0;
    }


    public boolean isErrorReportRequired() {
        return errorState.get() == 1;
    }


    public boolean setErrorReported() {
        return errorState.compareAndSet(1, 2);
    }


    /**
     * 执行任何需要刷新和关闭输出流或写入器的操作.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public void finishResponse() throws IOException {
        // Writing leftover bytes
        outputBuffer.close();
    }


    /**
     * @return 为该响应设置或计算的内容长度.
     */
    public int getContentLength() {
        return getCoyoteResponse().getContentLength();
    }


    /**
     * @return 该响应设置或计算的内容类型, 或<code>null</code>如果没有设置内容类型.
     */
    @Override
    public String getContentType() {
        return getCoyoteResponse().getContentType();
    }


    /**
     * 返回可以用来输出错误消息的PrintWriter, 不管流或写入器是否已经被获取.
     *
     * @return Writer 可用于错误报告.
     * 如果响应不是一个sendError返回的错误报告，或者处理servlet期间抛出的异常触发(只有在那种情况下), 如果响应流已经使用返回null.
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public PrintWriter getReporter() throws IOException {
        if (outputBuffer.isNew()) {
            outputBuffer.checkConverter();
            if (writer == null) {
                writer = new CoyoteWriter(outputBuffer);
            }
            return writer;
        } else {
            return null;
        }
    }


    // ------------------------------------------------ ServletResponse Methods


    /**
     * 刷新缓冲区并提交此响应.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public void flushBuffer() throws IOException {
        outputBuffer.flush();
    }


    /**
     * @return 用于此响应的实际缓冲区大小.
     */
    @Override
    public int getBufferSize() {
        return outputBuffer.getBufferSize();
    }


    /**
     * @return 用于此响应的字符编码.
     */
    @Override
    public String getCharacterEncoding() {
        String charset = getCoyoteResponse().getCharacterEncoding();
        if (charset != null) {
            return charset;
        }

        Context context = getContext();
        String result = null;
        if (context != null) {
            result =  context.getResponseCharacterEncoding();
        }

        if (result == null) {
            result = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET.name();
        }

        return result;
    }


    /**
     * @return 与此响应相关联的servlet输出流.
     *
     * @exception IllegalStateException 如果这个响应的<code>getWriter</code>已经被调用
     * @exception IOException 如果出现输入/输出错误
     */
    @Override
    public ServletOutputStream getOutputStream()
        throws IOException {

        if (usingWriter) {
            throw new IllegalStateException
                (sm.getString("coyoteResponse.getOutputStream.ise"));
        }

        usingOutputStream = true;
        if (outputStream == null) {
            outputStream = new CoyoteOutputStream(outputBuffer);
        }
        return outputStream;

    }


    /**
     * @return 分配给此响应的Locale.
     */
    @Override
    public Locale getLocale() {
        return (getCoyoteResponse().getLocale());
    }


    /**
     * @return 与此响应相关联的 writer.
     *
     * @exception IllegalStateException 如果这个响应的<code>getOutputStream</code>已经被调用
     * @exception IOException 如果出现输入/输出错误
     */
    @Override
    public PrintWriter getWriter()
        throws IOException {

        if (usingOutputStream) {
            throw new IllegalStateException
                (sm.getString("coyoteResponse.getWriter.ise"));
        }

        if (ENFORCE_ENCODING_IN_GET_WRITER) {
            /*
             * 如果响应的字符编码没有如<code>getCharacterEncoding</code> 所描述的那样指定(i.e., 该方法只返回默认值<code>ISO-8859-1</code>),
             * <code>getWriter</code>更新它为<code>ISO-8859-1</code>
             * (随后调用getContentType()的结果将包含一个charset=ISO-8859-1 组件, 也将反映在Content-Type响应头中,
             * 从而满足servlet规范要求容器必须传递用于servlet响应的writer的字符编码到客户端).
             */
            setCharacterEncoding(getCharacterEncoding());
        }

        usingWriter = true;
        outputBuffer.checkConverter();
        if (writer == null) {
            writer = new CoyoteWriter(outputBuffer);
        }
        return writer;
    }


    /**
     * 这个响应的输出已经被提交了吗?
     *
     * @return <code>true</code>如果已提交响应
     */
    @Override
    public boolean isCommitted() {
        return getCoyoteResponse().isCommitted();
    }


    /**
     * 清除写入缓冲区的内容.
     *
     * @exception IllegalStateException 如果此响应已经提交
     */
    @Override
    public void reset() {
        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().reset();
        outputBuffer.reset();
        usingOutputStream = false;
        usingWriter = false;
        isCharacterEncodingSet = false;
    }


    /**
     * 重置数据缓冲区，但不设置任何状态或标头信息.
     *
     * @exception IllegalStateException 如果响应已经提交
     */
    @Override
    public void resetBuffer() {
        resetBuffer(false);
    }


    /**
     * 重置数据缓冲区和是否使用Writer/Stream标志，除了各个状态或header信息.
     *
     * @param resetWriterStreamFlags <code>true</code> 如果内部的
     *        <code>usingWriter</code>, <code>usingOutputStream</code>, <code>isCharacterEncodingSet</code>也应该被重置
     *
     * @exception IllegalStateException 如果响应已经提交
     */
    public void resetBuffer(boolean resetWriterStreamFlags) {

        if (isCommitted()) {
            throw new IllegalStateException
                (sm.getString("coyoteResponse.resetBuffer.ise"));
        }

        outputBuffer.reset(resetWriterStreamFlags);

        if(resetWriterStreamFlags) {
            usingOutputStream = false;
            usingWriter = false;
            isCharacterEncodingSet = false;
        }
    }


    /**
     * 设置用于此响应的缓冲区大小.
     *
     * @param size 缓冲区大小
     *
     * @exception IllegalStateException 如果在该响应提交输出之后调用此方法
     */
    @Override
    public void setBufferSize(int size) {

        if (isCommitted() || !outputBuffer.isNew()) {
            throw new IllegalStateException
                (sm.getString("coyoteResponse.setBufferSize.ise"));
        }

        outputBuffer.setBufferSize(size);
    }


    /**
     * 设置此响应的内容长度（字节）.
     *
     * @param length 内容长度
     */
    @Override
    public void setContentLength(int length) {
        setContentLengthLong(length);
    }


    @Override
    public void setContentLengthLong(long length) {
        if (isCommitted()) {
            return;
        }

        // 忽略包含的servlet的任何调用
        if (included) {
            return;
        }

        getCoyoteResponse().setContentLength(length);
    }


    /**
     * 设置此响应的内容类型.
     *
     * @param type 内容类型
     */
    @Override
    public void setContentType(String type) {

        if (isCommitted()) {
            return;
        }

        // 忽略包含的servlet的任何调用
        if (included) {
            return;
        }

        if (type == null) {
            getCoyoteResponse().setContentType(null);
            return;
        }

        String[] m = MEDIA_TYPE_CACHE.parse(type);
        if (m == null) {
            // Invalid - 假设没有字符集，只是通过用户提供的任何东西.
            getCoyoteResponse().setContentTypeNoCharset(type);
            return;
        }

        getCoyoteResponse().setContentTypeNoCharset(m[0]);

        if (m[1] != null) {
            // 忽略字符集, 如果已经调用getWriter()
            if (!usingWriter) {
                try {
                    getCoyoteResponse().setCharacterEncoding(m[1]);
                } catch (IllegalArgumentException e) {
                    log.warn(sm.getString("coyoteResponse.encoding.invalid", m[1]), e);
                }

                isCharacterEncodingSet = true;
            }
        }
    }


    /**
     * 重写请求正文中使用的字符编码的名称. 在读取请求参数或使用getReader()读取输入之前必须调用此方法.
     *
     * @param characterEncoding 字符串编码的名称.
     */
    @Override
    public void setCharacterEncoding(String characterEncoding) {

        if (isCommitted()) {
            return;
        }

        // 忽略包含servlet的任何调用
        if (included) {
            return;
        }

        // 忽略getWriter调用之后的任何调用
        // 使用默认的
        if (usingWriter) {
            return;
        }

        try {
            getCoyoteResponse().setCharacterEncoding(characterEncoding);
        } catch (IllegalArgumentException e) {
            log.warn(sm.getString("coyoteResponse.encoding.invalid", characterEncoding), e);
            return;
        }
        isCharacterEncodingSet = true;
    }


    /**
     * 设置适合此响应的区域设置, 包括设置适当的字符编码.
     *
     * @param locale The new locale
     */
    @Override
    public void setLocale(Locale locale) {

        if (isCommitted()) {
            return;
        }

        // 忽略包含servlet的任何调用
        if (included) {
            return;
        }

        getCoyoteResponse().setLocale(locale);

        // 忽略getWriter调用之后的任何调用
        // 使用默认的
        if (usingWriter) {
            return;
        }

        if (isCharacterEncodingSet) {
            return;
        }

        String charset = getContext().getCharset(locale);
        if (charset != null) {
            try {
                getCoyoteResponse().setCharacterEncoding(charset);
            } catch (IllegalArgumentException e) {
                log.warn(sm.getString("coyoteResponse.encoding.invalid", charset), e);
            }
        }
    }


    // --------------------------------------------------- HttpResponse Methods


    @Override
    public String getHeader(String name) {
        return getCoyoteResponse().getMimeHeaders().getHeader(name);
    }


    @Override
    public Collection<String> getHeaderNames() {

        MimeHeaders headers = getCoyoteResponse().getMimeHeaders();
        int n = headers.size();
        List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(headers.getName(i).toString());
        }
        return result;

    }


    @Override
    public Collection<String> getHeaders(String name) {

        Enumeration<String> enumeration =
                getCoyoteResponse().getMimeHeaders().values(name);
        Vector<String> result = new Vector<>();
        while (enumeration.hasMoreElements()) {
            result.addElement(enumeration.nextElement());
        }
        return result;
    }


    /**
     * @return 使用<code>sendError()</code>设置的错误信息.
     */
    public String getMessage() {
        return getCoyoteResponse().getMessage();
    }


    @Override
    public int getStatus() {
        return getCoyoteResponse().getStatus();
    }


    // -------------------------------------------- HttpServletResponse Methods

    /**
     * 将指定的cookie添加到将包含在该响应中的cookie中.
     *
     * @param cookie Cookie to be added
     */
    @Override
    public void addCookie(final Cookie cookie) {

        // 忽略包含的servlet的任何调用
        if (included || isCommitted()) {
            return;
        }

        cookies.add(cookie);

        String header = generateCookieString(cookie);
        // 如果到这里没有任何异常, cookie 是有效的
        // header名称是 Set-Cookie, 不管是在"old" 和 v.1 ( RFC2109 )
        // 浏览器不支持RFC2965，Servlet规范使用2109.
        addHeader("Set-Cookie", header, getContext().getCookieProcessor().getCharset());
    }

    /**
     * 添加会话cookie的特殊方法，应该覆盖之前的任何方法.
     *
     * @param cookie 添加响应的新会话cookie
     */
    public void addSessionCookieInternal(final Cookie cookie) {
        if (isCommitted()) {
            return;
        }

        String name = cookie.getName();
        final String headername = "Set-Cookie";
        final String startsWith = name + "=";
        String header = generateCookieString(cookie);
        boolean set = false;
        MimeHeaders headers = getCoyoteResponse().getMimeHeaders();
        int n = headers.size();
        for (int i = 0; i < n; i++) {
            if (headers.getName(i).toString().equals(headername)) {
                if (headers.getValue(i).toString().startsWith(startsWith)) {
                    headers.getValue(i).setString(header);
                    set = true;
                }
            }
        }
        if (!set) {
            addHeader(headername, header);
        }


    }

    public String generateCookieString(final Cookie cookie) {
        // Web应用程序代码可以从generateHeader()调用接收 IllegalArgumentException
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return AccessController.doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run(){
                    return getContext().getCookieProcessor().generateHeader(cookie);
                }
            });
        } else {
            return getContext().getCookieProcessor().generateHeader(cookie);
        }
    }


    /**
     * @param name header名称
     * @param value Date值
     */
    @Override
    public void addDateHeader(String name, long value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(FastHttpDateFormat.RFC1123_DATE,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        addHeader(name, FastHttpDateFormat.formatDate(value, format));

    }


    /**
     * @param name header名称
     * @param value 值
     */
    @Override
    public void addHeader(String name, String value) {
        addHeader(name, value, null);
    }


    private void addHeader(String name, String value, Charset charset) {

        if (name == null || name.length() == 0 || value == null) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        char cc=name.charAt(0);
        if (cc=='C' || cc=='c') {
            if (checkSpecialHeader(name, value))
            return;
        }

        getCoyoteResponse().addHeader(name, value, charset);
    }


    /**
     * {@link org.apache.coyote.Response}中的扩展版本.
     * 这个检查需要确保{@link #setContentType(String)}中的usingWriter检查有效，因为usingWriter对{@link org.apache.coyote.Response}不可见.
     *
     * Called from set/addHeader.
     * @return <code>true</code>如果header是特殊的, 不需要设置header.
     */
    private boolean checkSpecialHeader(String name, String value) {
        if (name.equalsIgnoreCase("Content-Type")) {
            setContentType(value);
            return true;
        }
        return false;
    }


    /**
     * @param name 标头的名称
     * @param value Integer值
     */
    @Override
    public void addIntHeader(String name, int value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        addHeader(name, "" + value);
    }


    /**
     * 这个响应中已经设置了指定的标头吗?
     *
     * @param name 要检查的标头名称
     * @return <code>true</code>如果已经设置header
     */
    @Override
    public boolean containsHeader(String name) {
        // 需要特殊处理 Content-Type 和 Content-Length， 由于coyoteResponse中的特殊处理
        char cc=name.charAt(0);
        if(cc=='C' || cc=='c') {
            if(name.equalsIgnoreCase("Content-Type")) {
                // Will return null if this has not been set
                return (getCoyoteResponse().getContentType() != null);
            }
            if(name.equalsIgnoreCase("Content-Length")) {
                // -1 means not known and is not sent to client
                return (getCoyoteResponse().getContentLengthLong() != -1);
            }
        }

        return getCoyoteResponse().containsHeader(name);
    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的重定向URL中.
     *
     * @param url 要编码的URL
     * @return <code>true</code>如果编码URL
     */
    @Override
    public String encodeRedirectURL(String url) {

        if (isEncodeable(toAbsolute(url))) {
            return (toEncoded(url, request.getSessionInternal().getIdInternal()));
        } else {
            return (url);
        }

    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的重定向URL中.
     *
     * @param url 要编码的URL
     * @return <code>true</code>如果编码URL
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>encodeRedirectURL()</code> instead.
     */
    @Override
    @Deprecated
    public String encodeRedirectUrl(String url) {
        return (encodeRedirectURL(url));
    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的URL中.
     *
     * @param url 要编码的URL
     * @return <code>true</code>如果编码URL
     */
    @Override
    public String encodeURL(String url) {

        String absolute;
        try {
            absolute = toAbsolute(url);
        } catch (IllegalArgumentException iae) {
            // Relative URL
            return url;
        }

        if (isEncodeable(absolute)) {
            // W3c spec clearly said
            if (url.equalsIgnoreCase("")) {
                url = absolute;
            } else if (url.equals(absolute) && !hasPath(url)) {
                url += '/';
            }
            return (toEncoded(url, request.getSessionInternal().getIdInternal()));
        } else {
            return (url);
        }

    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的URL中.
     *
     * @param url 要编码的URL
     * @return <code>true</code>如果编码URL
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>encodeURL()</code> instead.
     */
    @Override
    @Deprecated
    public String encodeUrl(String url) {
        return (encodeURL(url));
    }


    /**
     * 发送请求的确认.
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public void sendAcknowledgement()
        throws IOException {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().action(ActionCode.ACK, null);
    }


    /**
     * 用指定的状态和默认消息发送错误响应.
     *
     * @param status 要发送的HTTP状态码
     *
     * @exception IllegalStateException 如果此响应已经提交
     * @exception IOException 如果出现输入/输出错误
     */
    @Override
    public void sendError(int status) throws IOException {
        sendError(status, null);
    }


    /**
     * 用指定的状态和消息发送错误响应.
     *
     * @param status 要发送的HTTP状态码
     * @param message 发送相应的消息
     *
     * @exception IllegalStateException 如果此响应已经提交
     * @exception IOException 如果出现输入/输出错误
     */
    @Override
    public void sendError(int status, String message) throws IOException {

        if (isCommitted()) {
            throw new IllegalStateException
                (sm.getString("coyoteResponse.sendError.ise"));
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        setError();

        getCoyoteResponse().setStatus(status);
        getCoyoteResponse().setMessage(message);

        // Clear any data content that has been buffered
        resetBuffer();

        // Cause the response to be finished (from the application perspective)
        setSuspended(true);
    }


    /**
     * 将临时重定向发送到指定的重定向位置URL.
     *
     * @param location 重定向到的位置URL
     *
     * @exception IllegalStateException 如果此响应已经提交
     * @exception IOException 如果出现输入/输出错误
     */
    @Override
    public void sendRedirect(String location) throws IOException {
        sendRedirect(location, SC_FOUND);
    }


    /**
     * 允许用状态发送重定向，除了 {@link HttpServletResponse#SC_FOUND} (302).没有尝试验证状态码.
     *
     * @param location 重定向到的位置URL
     * @param status 将发送的HTTP状态码
     * @throws IOException an IO exception occurred
     */
    public void sendRedirect(String location, int status) throws IOException {
        if (isCommitted()) {
            throw new IllegalStateException(sm.getString("coyoteResponse.sendRedirect.ise"));
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        // 清除已缓冲的任何数据内容
        resetBuffer(true);

        // 生成对指定位置的临时重定向
        try {
            String locationUri;
            // Relative redirects require HTTP/1.1
            if (getRequest().getCoyoteRequest().getSupportsRelativeRedirects() &&
                    getContext().getUseRelativeRedirects()) {
                locationUri = location;
            } else {
                locationUri = toAbsolute(location);
            }
            setStatus(status);
            setHeader("Location", locationUri);
            if (getContext().getSendRedirectBody()) {
                PrintWriter writer = getWriter();
                writer.print(sm.getString("coyoteResponse.sendRedirect.note",
                        Escape.htmlElementContent(locationUri)));
                flushBuffer();
            }
        } catch (IllegalArgumentException e) {
            log.warn(sm.getString("response.sendRedirectFail", location), e);
            setStatus(SC_NOT_FOUND);
        }

        // 导致响应完成(从应用的角度)
        setSuspended(true);
    }


    /**
     * Set the specified date header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Date value to be set
     */
    @Override
    public void setDateHeader(String name, long value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(FastHttpDateFormat.RFC1123_DATE,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        setHeader(name, FastHttpDateFormat.formatDate(value, format));
    }


    /**
     * 将指定的标头设置为指定的值.
     *
     * @param name 标头的名称
     * @param value 要设置的值
     */
    @Override
    public void setHeader(String name, String value) {

        if (name == null || name.length() == 0 || value == null) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        char cc=name.charAt(0);
        if (cc=='C' || cc=='c') {
            if (checkSpecialHeader(name, value))
                return;
        }

        getCoyoteResponse().setHeader(name, value);
    }


    /**
     * @param name 标头的名称
     * @param value Integer值
     */
    @Override
    public void setIntHeader(String name, int value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        setHeader(name, "" + value);

    }


    /**
     * 设置要用此响应返回的HTTP状态.
     *
     * @param status The new HTTP status
     */
    @Override
    public void setStatus(int status) {
        setStatus(status, null);
    }


    /**
     * 设置要返回的HTTP状态和消息.
     *
     * @param status HTTP状态
     * @param message 关联的文本消息
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, this method
     *  has been deprecated due to the ambiguous meaning of the message
     *  parameter.
     */
    @Override
    @Deprecated
    public void setStatus(int status, String message) {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().setStatus(status);
        getCoyoteResponse().setMessage(message);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回<code>true</code> 如果指定的URL应该用会话标识符编码. 如果满足以下所有条件，这将是true:
     * <ul>
     * <li>正在响应的请求需要一个有效的会话
     * <li>通过cookie没有接收到所请求的会话ID
     * <li>指定的URL指向Web应用程序中响应此请求的某个地方
     * </ul>
     *
     * @param location 要验证的绝对URL
     * @return <code>true</code>如果应该编码URL
     */
    protected boolean isEncodeable(final String location) {

        if (location == null) {
            return false;
        }

        // 这是文档内引用吗?
        if (location.startsWith("#")) {
            return false;
        }

        // 是否在一个有效的会话中不使用cookie?
        final Request hreq = request;
        final Session session = hreq.getSessionInternal(false);
        if (session == null) {
            return false;
        }
        if (hreq.isRequestedSessionIdFromCookie()) {
            return false;
        }

        // Is URL encoding permitted
        if (!hreq.getServletContext().getEffectiveSessionTrackingModes().
                contains(SessionTrackingMode.URL)) {
            return false;
        }

        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (
                AccessController.doPrivileged(new PrivilegedAction<Boolean>() {

                @Override
                public Boolean run(){
                    return Boolean.valueOf(doIsEncodeable(hreq, session, location));
                }
            })).booleanValue();
        } else {
            return doIsEncodeable(hreq, session, location);
        }
    }

    private boolean doIsEncodeable(Request hreq, Session session,
                                   String location) {
        // 这是一个有效的绝对URL吗?
        URL url = null;
        try {
            url = new URL(location);
        } catch (MalformedURLException e) {
            return false;
        }

        // 这个URL是否匹配（并包括）上下文路径?
        if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol())) {
            return false;
        }
        if (!hreq.getServerName().equalsIgnoreCase(url.getHost())) {
            return false;
        }
        int serverPort = hreq.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(hreq.getScheme())) {
                serverPort = 443;
            } else {
                serverPort = 80;
            }
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol())) {
                urlPort = 443;
            } else {
                urlPort = 80;
            }
        }
        if (serverPort != urlPort) {
            return false;
        }

        String contextPath = getContext().getPath();
        if (contextPath != null) {
            String file = url.getFile();
            if (!file.startsWith(contextPath)) {
                return false;
            }
            String tok = ";" +
                    SessionConfig.getSessionUriParamName(request.getContext()) +
                    "=" + session.getIdInternal();
            if( file.indexOf(tok, contextPath.length()) >= 0 ) {
                return false;
            }
        }

        // 此URL属于我们的Web应用程序, 因此它是可编码的
        return true;
    }


    /**
     * 转换（如果需要的话）并返回该URL可能引用的资源的绝对URL.  如果这个URL已经是绝对的, 返回未修改的.
     *
     * @param location 要转换的URL
     * @return the encoded URL
     *
     * @exception IllegalArgumentException 当将相对URL转换为绝对URL时, 如果抛出 MalformedURLException
     */
    protected String toAbsolute(String location) {

        if (location == null) {
            return (location);
        }

        boolean leadingSlash = location.startsWith("/");

        if (location.startsWith("//")) {
            // Scheme relative
            redirectURLCC.recycle();
            // Add the scheme
            String scheme = request.getScheme();
            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append(':');
                redirectURLCC.append(location, 0, location.length());
                return redirectURLCC.toString();
            } catch (IOException e) {
                IllegalArgumentException iae =
                    new IllegalArgumentException(location);
                iae.initCause(e);
                throw iae;
            }

        } else if (leadingSlash || !UriUtil.hasScheme(location)) {

            redirectURLCC.recycle();

            String scheme = request.getScheme();
            String name = request.getServerName();
            int port = request.getServerPort();

            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append("://", 0, 3);
                redirectURLCC.append(name, 0, name.length());
                if ((scheme.equals("http") && port != 80)
                    || (scheme.equals("https") && port != 443)) {
                    redirectURLCC.append(':');
                    String portS = port + "";
                    redirectURLCC.append(portS, 0, portS.length());
                }
                if (!leadingSlash) {
                    String relativePath = request.getDecodedRequestURI();
                    int pos = relativePath.lastIndexOf('/');
                    CharChunk encodedURI = null;
                    final String frelativePath = relativePath;
                    final int fend = pos;
                    if (SecurityUtil.isPackageProtectionEnabled() ){
                        try{
                            encodedURI = AccessController.doPrivileged(
                                new PrivilegedExceptionAction<CharChunk>(){
                                    @Override
                                    public CharChunk run() throws IOException{
                                        return urlEncoder.encodeURL(frelativePath, 0, fend);
                                    }
                           });
                        } catch (PrivilegedActionException pae){
                            IllegalArgumentException iae =
                                new IllegalArgumentException(location);
                            iae.initCause(pae.getException());
                            throw iae;
                        }
                    } else {
                        encodedURI = urlEncoder.encodeURL(relativePath, 0, pos);
                    }
                    redirectURLCC.append(encodedURI);
                    encodedURI.recycle();
                    redirectURLCC.append('/');
                }
                redirectURLCC.append(location, 0, location.length());

                normalize(redirectURLCC);
            } catch (IOException e) {
                IllegalArgumentException iae =
                    new IllegalArgumentException(location);
                iae.initCause(e);
                throw iae;
            }

            return redirectURLCC.toString();
        } else {
            return (location);
        }
    }

    /**
     * 从绝对的URL中移除 /./ 和 /../.
     * 大量借用CoyoteAdapter.normalize()代码 
     *
     * @param cc 包含字符的规范化字符块
     */
    private void normalize(CharChunk cc) {
        // 以这样的方式首先查询字符串和/或片段，使规范化逻辑变得简单多了
        int truncate = cc.indexOf('?');
        if (truncate == -1) {
            truncate = cc.indexOf('#');
        }
        char[] truncateCC = null;
        if (truncate > -1) {
            truncateCC = Arrays.copyOfRange(cc.getBuffer(),
                    cc.getStart() + truncate, cc.getEnd());
            cc.setEnd(cc.getStart() + truncate);
        }

        if (cc.endsWith("/.") || cc.endsWith("/..")) {
            try {
                cc.append('/');
            } catch (IOException e) {
                throw new IllegalArgumentException(cc.toString(), e);
            }
        }

        char[] c = cc.getChars();
        int start = cc.getStart();
        int end = cc.getEnd();
        int index = 0;
        int startIndex = 0;

        // Advance past the first three / characters (should place index just
        // scheme://host[:port]

        for (int i = 0; i < 3; i++) {
            startIndex = cc.indexOf('/', startIndex + 1);
        }

        // Remove /./
        index = startIndex;
        while (true) {
            index = cc.indexOf("/./", 0, 3, index);
            if (index < 0) {
                break;
            }
            copyChars(c, start + index, start + index + 2,
                      end - start - index - 2);
            end = end - 2;
            cc.setEnd(end);
        }

        // Remove /../
        index = startIndex;
        int pos;
        while (true) {
            index = cc.indexOf("/../", 0, 4, index);
            if (index < 0) {
                break;
            }
            // 不能超过服务器根目录
            if (index == startIndex) {
                throw new IllegalArgumentException();
            }
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos --) {
                if (c[pos] == (byte) '/') {
                    index2 = pos;
                }
            }
            copyChars(c, start + index2, start + index + 3,
                      end - start - index - 3);
            end = end + index2 - index - 3;
            cc.setEnd(end);
            index = index2;
        }

        // 将查询字符串和片段加回去
        if (truncateCC != null) {
            try {
                cc.append(truncateCC, 0, truncateCC.length);
            } catch (IOException ioe) {
                throw new IllegalArgumentException(ioe);
            }
        }
    }

    private void copyChars(char[] c, int dest, int src, int len) {
        for (int pos = 0; pos < len; pos++) {
            c[pos + dest] = c[pos + src];
        }
    }


    /**
     * 确定绝对URL是否具有路径组件.
     *
     * @param uri 要检查的URL
     * @return <code>true</code>如果URL有路径
     */
    private boolean hasPath(String uri) {
        int pos = uri.indexOf("://");
        if (pos < 0) {
            return false;
        }
        pos = uri.indexOf('/', pos + 3);
        if (pos < 0) {
            return false;
        }
        return true;
    }

    /**
     * 返回指定的URL 和适当编码的指定的会话标识符.
     *
     * @param url 要用会话ID编码的URL
     * @param sessionId 将包含在编码URL中的会话ID
     * @return the encoded URL
     */
    protected String toEncoded(String url, String sessionId) {

        if ((url == null) || (sessionId == null)) {
            return (url);
        }

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        StringBuilder sb = new StringBuilder(path);
        if( sb.length() > 0 ) { // jsessionid can't be first.
            sb.append(";");
            sb.append(SessionConfig.getSessionUriParamName(
                    request.getContext()));
            sb.append("=");
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        return (sb.toString());
    }
}
