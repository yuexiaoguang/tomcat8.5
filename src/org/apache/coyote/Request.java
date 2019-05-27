package org.apache.coyote;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ReadListener;

import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * 这是服务器请求的低级、高效表示. 大多数字段是无GC的, 昂贵的操作被延迟, 直到用户代码需要信息为止.
 *
 * 处理委托给模块, 使用钩子机制.
 *
 * 此类不用于用户代码 - 它在Tomcat内部用于以最有效的方式处理请求. Users ( servlets ) 可以使用代理访问信息, 它提供请求的高级视图.
 *
 * Tomcat定义了许多属性:
 * <ul>
 *   <li>"org.apache.tomcat.request" - 允许访问受信任的应用程序中的低级请求对象
 * </ul>
 */
public final class Request {

    private static final StringManager sm = StringManager.getManager(Request.class);

    // 每个请求的最大cookie数.
    private static final int INITIAL_COOKIE_SIZE = 4;

    // ----------------------------------------------------------- Constructors

    public Request() {
        parameters.setQuery(queryMB);
        parameters.setURLDecoder(urlDecoder);
    }


    // ----------------------------------------------------- Instance Variables

    private int serverPort = -1;
    private final MessageBytes serverNameMB = MessageBytes.newInstance();

    private int remotePort;
    private int localPort;

    private final MessageBytes schemeMB = MessageBytes.newInstance();

    private final MessageBytes methodMB = MessageBytes.newInstance();
    private final MessageBytes uriMB = MessageBytes.newInstance();
    private final MessageBytes decodedUriMB = MessageBytes.newInstance();
    private final MessageBytes queryMB = MessageBytes.newInstance();
    private final MessageBytes protoMB = MessageBytes.newInstance();

    // remote address/host
    private final MessageBytes remoteAddrMB = MessageBytes.newInstance();
    private final MessageBytes localNameMB = MessageBytes.newInstance();
    private final MessageBytes remoteHostMB = MessageBytes.newInstance();
    private final MessageBytes localAddrMB = MessageBytes.newInstance();

    private final MimeHeaders headers = new MimeHeaders();


    /**
     * 路径参数
     */
    private final Map<String,String> pathParameters = new HashMap<>();

    /**
     * Notes.
     */
    private final Object notes[] = new Object[Constants.MAX_NOTES];


    /**
     * 关联的输入缓冲区.
     */
    private InputBuffer inputBuffer = null;


    /**
     * URL 解码器.
     */
    private final UDecoder urlDecoder = new UDecoder();


    /**
     * 特定于HTTP的字段. (remove them ?)
     */
    private long contentLength = -1;
    private MessageBytes contentTypeMB = null;
    private Charset charset = null;
    // 保留原始的、用户指定的字符编码， 因此即使无效，也可以返回
    private String characterEncoding = null;

    /**
     * Is there an expectation ?
     */
    private boolean expectation = false;

    private final ServerCookies serverCookies = new ServerCookies(INITIAL_COOKIE_SIZE);
    private final Parameters parameters = new Parameters();

    private final MessageBytes remoteUser = MessageBytes.newInstance();
    private boolean remoteUserNeedsAuthorization = false;
    private final MessageBytes authType = MessageBytes.newInstance();
    private final HashMap<String,Object> attributes = new HashMap<>();

    private Response response;
    private volatile ActionHook hook;

    private long bytesRead=0;
    // 请求的时间 - 避免重复调用 System.currentTime
    private long startTime = -1;
    private int available = 0;

    private final RequestInfo reqProcessorMX=new RequestInfo(this);

    private boolean sendfile = true;

    volatile ReadListener listener;

    public ReadListener getReadListener() {
        return listener;
    }

    public void setReadListener(ReadListener listener) {
        if (listener == null) {
            throw new NullPointerException(
                    sm.getString("request.nullReadListener"));
        }
        if (getReadListener() != null) {
            throw new IllegalStateException(
                    sm.getString("request.readListenerSet"));
        }
        // Note: 该类不用于HTTP升级，因此只需要测试异步
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.ASYNC_IS_ASYNC, result);
        if (!result.get()) {
            throw new IllegalStateException(
                    sm.getString("request.notAsync"));
        }

        this.listener = listener;
    }

    private final AtomicBoolean allDataReadEventSent = new AtomicBoolean(false);

    public boolean sendAllDataReadEvent() {
        return allDataReadEventSent.compareAndSet(false, true);
    }


    // ------------------------------------------------------------- Properties

    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    public UDecoder getURLDecoder() {
        return urlDecoder;
    }

    // -------------------- Request data --------------------


    public MessageBytes scheme() {
        return schemeMB;
    }

    public MessageBytes method() {
        return methodMB;
    }

    public MessageBytes requestURI() {
        return uriMB;
    }

    public MessageBytes decodedURI() {
        return decodedUriMB;
    }

    public MessageBytes queryString() {
        return queryMB;
    }

    public MessageBytes protocol() {
        return protoMB;
    }

    /**
     * 获取"virtual host", 来自 Host: 请求关联的 header.
     *
     * @return 保存服务器名称的缓冲区. 使用 isNull() 检查是否没有设置值.
     */
    public MessageBytes serverName() {
        return serverNameMB;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort ) {
        this.serverPort=serverPort;
    }

    public MessageBytes remoteAddr() {
        return remoteAddrMB;
    }

    public MessageBytes remoteHost() {
        return remoteHostMB;
    }

    public MessageBytes localName() {
        return localNameMB;
    }

    public MessageBytes localAddr() {
        return localAddrMB;
    }

    public int getRemotePort(){
        return remotePort;
    }

    public void setRemotePort(int port){
        this.remotePort = port;
    }

    public int getLocalPort(){
        return localPort;
    }

    public void setLocalPort(int port){
        this.localPort = port;
    }

    // -------------------- encoding/type --------------------


    /**
     * 获取用于此请求的字符编码.
     *
     * @return 通过 {@link #setCharacterEncoding(String)}设置值, 或者如果没有调用该方法，则尝试从内容类型中获取.
     */
    public String getCharacterEncoding() {
        if (characterEncoding == null) {
            characterEncoding = getCharsetFromContentType(getContentType());
        }

        return characterEncoding;
    }


    /**
     * 获取用于此请求的字符编码.
     *
     * @return 通过 {@link #setCharacterEncoding(String)}设置值, 或者如果没有调用该方法，则尝试从内容类型中获取.
     *
     * @throws UnsupportedEncodingException 如果用户代理已指定了无效字符编码
     */
    public Charset getCharset() throws UnsupportedEncodingException {
        if (charset == null) {
            getCharacterEncoding();
            if (characterEncoding != null) {
                charset = B2CConverter.getCharset(characterEncoding);
            }
        }

        return charset;
    }


    /**
     * @param enc 新编码
     *
     * @throws UnsupportedEncodingException 如果编码无效
     *
     * @deprecated This method will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {
        setCharset(B2CConverter.getCharset(enc));
    }


    public void setCharset(Charset charset) {
        this.charset = charset;
        this.characterEncoding = charset.name();
    }


    public void setContentLength(long len) {
        this.contentLength = len;
    }


    public int getContentLength() {
        long length = getContentLengthLong();

        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return -1;
    }

    public long getContentLengthLong() {
        if( contentLength > -1 ) {
            return contentLength;
        }

        MessageBytes clB = headers.getUniqueValue("content-length");
        contentLength = (clB == null || clB.isNull()) ? -1 : clB.getLong();

        return contentLength;
    }

    public String getContentType() {
        contentType();
        if ((contentTypeMB == null) || contentTypeMB.isNull()) {
            return null;
        }
        return contentTypeMB.toString();
    }


    public void setContentType(String type) {
        contentTypeMB.setString(type);
    }


    public MessageBytes contentType() {
        if (contentTypeMB == null) {
            contentTypeMB = headers.getValue("content-type");
        }
        return contentTypeMB;
    }


    public void setContentType(MessageBytes mb) {
        contentTypeMB=mb;
    }


    public String getHeader(String name) {
        return headers.getHeader(name);
    }


    public void setExpectation(boolean expectation) {
        this.expectation = expectation;
    }


    public boolean hasExpectation() {
        return expectation;
    }


    // -------------------- Associated response --------------------

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
        response.setRequest(this);
    }

    protected void setHook(ActionHook hook) {
        this.hook = hook;
    }

    public void action(ActionCode actionCode, Object param) {
        if (hook != null) {
            if (param == null) {
                hook.action(actionCode, this);
            } else {
                hook.action(actionCode, param);
            }
        }
    }


    // -------------------- Cookies --------------------

    public ServerCookies getCookies() {
        return serverCookies;
    }


    // -------------------- Parameters --------------------

    public Parameters getParameters() {
        return parameters;
    }


    public void addPathParameter(String name, String value) {
        pathParameters.put(name, value);
    }

    public String getPathParameter(String name) {
        return pathParameters.get(name);
    }


    // -------------------- Other attributes --------------------
    // We can use notes for most - need to discuss what is of general interest

    public void setAttribute( String name, Object o ) {
        attributes.put( name, o );
    }

    public HashMap<String,Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name ) {
        return attributes.get(name);
    }

    public MessageBytes getRemoteUser() {
        return remoteUser;
    }

    public boolean getRemoteUserNeedsAuthorization() {
        return remoteUserNeedsAuthorization;
    }

    public void setRemoteUserNeedsAuthorization(boolean remoteUserNeedsAuthorization) {
        this.remoteUserNeedsAuthorization = remoteUserNeedsAuthorization;
    }

    public MessageBytes getAuthType() {
        return authType;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public boolean getSendfile() {
        return sendfile;
    }

    public void setSendfile(boolean sendfile) {
        this.sendfile = sendfile;
    }

    public boolean isFinished() {
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.REQUEST_BODY_FULLY_READ, result);
        return result.get();
    }

    public boolean getSupportsRelativeRedirects() {
        if (protocol().equals("") || protocol().equals("HTTP/1.0")) {
            return false;
        }
        return true;
    }


    // -------------------- Input Buffer --------------------

    public InputBuffer getInputBuffer() {
        return inputBuffer;
    }


    public void setInputBuffer(InputBuffer inputBuffer) {
        this.inputBuffer = inputBuffer;
    }


    /**
     * 从输入缓冲区读取数据并将其放入字节块中.
     *
     * 缓冲区由协议实现拥有 - 它将在下次读取中被重复使用. Adapter 必须对数据进行适当的处理或将其复制到单独的缓冲区中，如果需要保存该数据.
     * 在大多数情况下，这样做了, 在 byte-&gt;char 转换或通过 InputStream期间. 不像 InputStream, 此接口允许应用程序在适当的位置处理数据, 而不复制.
     *
     * @param chunk 要复制数据的目的地
     *
     * @return 复制的字节数
     *
     * @throws IOException 如果在复制期间发生I/O错误
     *
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    public int doRead(ByteChunk chunk) throws IOException {
        int n = inputBuffer.doRead(chunk);
        if (n > 0) {
            bytesRead+=n;
        }
        return n;
    }


    /**
     * 从输入缓冲区读取数据并将其放入 ApplicationBufferHandler中.
     *
     * 缓冲区由协议实现拥有 - 它将在下次读取中被重复使用. Adapter 必须对数据进行适当的处理或将其复制到单独的缓冲区中，如果需要保存该数据.
     * 在大多数情况下，这样做了, 在 byte-&gt;char 转换或通过 InputStream期间. 不像 InputStream, 此接口允许应用程序在适当的位置处理数据, 而不复制.
     *
     * @param handler 要复制数据的目的地
     *
     * @return 复制的字节数
     *
     * @throws IOException 如果在复制期间发生I/O错误
     */
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        int n = inputBuffer.doRead(handler);
        if (n > 0) {
            bytesRead+=n;
        }
        return n;
    }


    // -------------------- debug --------------------

    @Override
    public String toString() {
        return "R( " + requestURI().toString() + ")";
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    // -------------------- Per-Request "notes" --------------------


    /**
     * 用于存储私有数据. 可以使用线程数据代替 - 但是如果你有要求, getting/setting 一个note 只是数组访问, 可能在非常频繁的操作中快于 ThreadLocal.
     *
     *  Example use:
     *   Catalina CoyoteAdapter:
     *      ADAPTER_NOTES = 1 - stores the HttpServletRequest object ( req/res)
     *
     *   为了避免冲突, 0 - 8范围内的note 为servlet容器保留 ( catalina connector, etc ), 9 - 16范围内的给连接器使用.
     *
     *   17-31 范围没有分配或使用.
     *
     * @param pos 用于存储note的索引
     * @param value 存储在该索引中的值
     */
    public final void setNote(int pos, Object value) {
        notes[pos] = value;
    }


    public final Object getNote(int pos) {
        return notes[pos];
    }


    // -------------------- Recycling --------------------


    public void recycle() {
        bytesRead=0;

        contentLength = -1;
        contentTypeMB = null;
        charset = null;
        characterEncoding = null;
        expectation = false;
        headers.recycle();
        serverNameMB.recycle();
        serverPort=-1;
        localAddrMB.recycle();
        localNameMB.recycle();
        localPort = -1;
        remoteAddrMB.recycle();
        remoteHostMB.recycle();
        remotePort = -1;
        available = 0;
        sendfile = true;

        serverCookies.recycle();
        parameters.recycle();
        pathParameters.clear();

        uriMB.recycle();
        decodedUriMB.recycle();
        queryMB.recycle();
        methodMB.recycle();
        protoMB.recycle();

        schemeMB.recycle();

        remoteUser.recycle();
        remoteUserNeedsAuthorization = false;
        authType.recycle();
        attributes.clear();

        listener = null;
        allDataReadEventSent.set(false);

        startTime = -1;
    }

    // -------------------- Info  --------------------
    public void updateCounters() {
        reqProcessorMX.updateCounters();
    }

    public RequestInfo getRequestProcessor() {
        return reqProcessorMX;
    }

    public long getBytesRead() {
        return bytesRead;
    }

    public boolean isProcessing() {
        return reqProcessorMX.getStage()==org.apache.coyote.Constants.STAGE_SERVICE;
    }

    /**
     * 从指定的内容类型头解析字符编码.
     * 如果内容类型是 null, 或者没有明确的字符编码, 返回<code>null</code>.
     *
     * @param contentType 内容类型 header
     */
    private static String getCharsetFromContentType(String contentType) {

        if (contentType == null) {
            return null;
        }
        int start = contentType.indexOf("charset=");
        if (start < 0) {
            return null;
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0) {
            encoding = encoding.substring(0, end);
        }
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
            && (encoding.endsWith("\""))) {
            encoding = encoding.substring(1, encoding.length() - 1);
        }

        return encoding.trim();
    }
}
