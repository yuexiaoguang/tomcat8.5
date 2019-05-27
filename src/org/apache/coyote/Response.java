package org.apache.coyote;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.WriteListener;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.MediaType;
import org.apache.tomcat.util.res.StringManager;

/**
 * Response object.
 */
public final class Response {

    private static final StringManager sm = StringManager.getManager(Response.class);

    private static final Log log = LogFactory.getLog(Response.class);

    // ----------------------------------------------------- Class Variables

    /**
     * 由规范规定的默认区域设置.
     */
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();


    // ----------------------------------------------------- Instance Variables

    /**
     * 状态码.
     */
    int status = 200;


    /**
     * 状态消息.
     */
    String message = null;


    /**
     * Response headers.
     */
    final MimeHeaders headers = new MimeHeaders();


    /**
     * 关联的输出缓冲区.
     */
    OutputBuffer outputBuffer;


    /**
     * Notes.
     */
    final Object notes[] = new Object[Constants.MAX_NOTES];


    /**
     * 提交标志.
     */
    volatile boolean commited = false;


    /**
     * 动作钩子.
     */
    volatile ActionHook hook;


    /**
     * 特定于HTTP的字段.
     */
    String contentType = null;
    String contentLanguage = null;
    Charset charset = null;
    // 保留用于设置字符集的原始名称，以便在ContentType header中使用名称.
    // 有些用户代理非常特殊(可论证的非规范兼容)
    String characterEncoding = null;
    long contentLength = -1;
    private Locale locale = DEFAULT_LOCALE;

    // 普通信息
    private long contentWritten = 0;
    private long commitTime = -1;

    /**
     * 保存请求错误异常.
     */
    Exception errorException = null;

    Request req;

    // ------------------------------------------------------------- Properties

    public Request getRequest() {
        return req;
    }

    public void setRequest( Request req ) {
        this.req=req;
    }


    public void setOutputBuffer(OutputBuffer outputBuffer) {
        this.outputBuffer = outputBuffer;
    }


    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    protected void setHook(ActionHook hook) {
        this.hook = hook;
    }


    // -------------------- Per-Response "notes" --------------------

    public final void setNote(int pos, Object value) {
        notes[pos] = value;
    }


    public final Object getNote(int pos) {
        return notes[pos];
    }


    // -------------------- Actions --------------------

    public void action(ActionCode actionCode, Object param) {
        if (hook != null) {
            if (param == null) {
                hook.action(actionCode, this);
            } else {
                hook.action(actionCode, param);
            }
        }
    }


    // -------------------- State --------------------

    public int getStatus() {
        return status;
    }


    /**
     * 设置响应状态.
     *
     * @param status 设置的状态值
     */
    public void setStatus(int status) {
        this.status = status;
    }


    /**
     * 获取状态消息.
     *
     * @return 与当前状态相关联的消息
     */
    public String getMessage() {
        return message;
    }


    /**
     * 设置状态消息.
     *
     * @param message 要设置的状态消息
     */
    public void setMessage(String message) {
        this.message = message;
    }


    public boolean isCommitted() {
        return commited;
    }


    public void setCommitted(boolean v) {
        if (v && !this.commited) {
            this.commitTime = System.currentTimeMillis();
        }
        this.commited = v;
    }

    /**
     * 返回响应提交的时间 (基于 System.currentTimeMillis).
     *
     * @return 响应提交的时间
     */
    public long getCommitTime() {
        return commitTime;
    }

    // -----------------Error State --------------------


    /**
     * 设置请求处理过程中发生的错误异常.
     *
     * @param ex 发生的异常
     */
    public void setErrorException(Exception ex) {
        errorException = ex;
    }


    /**
     * 获取请求处理过程中发生的异常.
     *
     * @return 发生的异常
     */
    public Exception getErrorException() {
        return errorException;
    }


    public boolean isExceptionPresent() {
        return ( errorException != null );
    }


    // -------------------- Methods --------------------


    public void reset() throws IllegalStateException {

        if (commited) {
            throw new IllegalStateException();
        }

        recycle();
    }


    // -------------------- Headers --------------------
    /**
     * 响应是否包含指定的 header.
     * <br>
     * Warning: 对于Content-Type和Content-Length总是返回<code>false</code>.
     *
     * @param name header的名称
     *
     * @return {@code true} 如果响应包含 header.
     */
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }


    public void setHeader(String name, String value) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        headers.setValue(name).setString( value);
    }


    public void addHeader(String name, String value) {
        addHeader(name, value, null);
    }


    public void addHeader(String name, String value, Charset charset) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        MessageBytes mb = headers.addValue(name);
        if (charset != null) {
            mb.setCharset(charset);
        }
        mb.setString(value);
    }


    /**
     * 为特殊header名称设置内部字段.
     * 从 set/addHeader 调用. 返回 true, 如果 header是特殊的, 不需要设置 header.
     */
    private boolean checkSpecialHeader( String name, String value) {
        // XXX 消除冗余字段 !!! ( 包括 header 和特殊字段 )
        if( name.equalsIgnoreCase( "Content-Type" ) ) {
            setContentType( value );
            return true;
        }
        if( name.equalsIgnoreCase( "Content-Length" ) ) {
            try {
                long cL=Long.parseLong( value );
                setContentLength( cL );
                return true;
            } catch( NumberFormatException ex ) {
                // Do nothing - 规范没有任何 "throws"
                // 而且用户可能知道他要做什么
                return false;
            }
        }
        return false;
    }


    /** 
     * 正在处理 header, 随后处理 body.
     * 任何实现都需要通知 ContextManager, 来允许拦截器处理 header.
     */
    public void sendHeaders() {
        action(ActionCode.COMMIT, this);
        setCommitted(true);
    }


    // -------------------- I18N --------------------


    public Locale getLocale() {
        return locale;
    }

    /**
     * 用户显式调用来设置 Content-Language 和默认的编码.
     *
     * @param locale 用于此响应的区域设置
     */
    public void setLocale(Locale locale) {

        if (locale == null) {
            return;  // throw an exception?
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        contentLanguage = locale.toLanguageTag();
    }

    /**
     * 返回内容语言.
     *
     * @return 当前与此响应相关联的语言的语言代码
     */
    public String getContentLanguage() {
        return contentLanguage;
    }

    /**
     * 重写响应体中使用的字符编码的名称. 这个方法必须在使用getWriter()写入输出之前调用.
     *
     * @param characterEncoding 字符编码的名称.
     */
    public void setCharacterEncoding(String characterEncoding) {
        if (isCommitted()) {
            return;
        }
        if (characterEncoding == null) {
            return;
        }

        try {
            this.charset = B2CConverter.getCharset(characterEncoding);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
        this.characterEncoding = characterEncoding;
    }


    /**
     * @return 当前编码的名称
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }


    public Charset getCharset() {
        return charset;
    }


    /**
     * 设置内容类型.
     *
     * 此方法必须保存可能已经设置的任何响应字符集, 通过调用 response.setContentType(), response.setLocale(), response.setCharacterEncoding().
     *
     * @param type 内容类型
     */
    public void setContentType(String type) {

        if (type == null) {
            this.contentType = null;
            return;
        }

        MediaType m = null;
        try {
             m = MediaType.parseMediaType(new StringReader(type));
        } catch (IOException e) {
            // Ignore - null test below handles this
        }
        if (m == null) {
            // Invalid - 假设没有字符集，只是通过用户提供的任何东西.
            this.contentType = type;
            return;
        }

        this.contentType = m.toStringNoCharset();

        String charsetValue = m.getCharset();

        if (charsetValue != null) {
            charsetValue = charsetValue.trim();
            if (charsetValue.length() > 0) {
                try {
                    charset = B2CConverter.getCharset(charsetValue);
                } catch (UnsupportedEncodingException e) {
                    log.warn(sm.getString("response.encoding.invalid", charsetValue), e);
                }
            }
        }
    }

    public void setContentTypeNoCharset(String type) {
        this.contentType = type;
    }

    public String getContentType() {

        String ret = contentType;

        if (ret != null
            && charset != null) {
            ret = ret + ";charset=" + characterEncoding;
        }

        return ret;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public int getContentLength() {
        long length = getContentLengthLong();

        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return -1;
    }

    public long getContentLengthLong() {
        return contentLength;
    }


    /**
     * 写入字节.
     *
     * @param chunk 要写入的字节
     *
     * @throws IOException 如果在写入期间发生I/O错误
     *
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    public void doWrite(ByteChunk chunk) throws IOException {
        outputBuffer.doWrite(chunk);
        contentWritten+=chunk.getLength();
    }


    /**
     * 写入字节.
     *
     * @param chunk 要写入的ByteBuffer
     *
     * @throws IOException 如果在写入期间发生I/O错误
     */
    public void doWrite(ByteBuffer chunk) throws IOException {
        int len = chunk.remaining();
        outputBuffer.doWrite(chunk);
        contentWritten += len - chunk.remaining();
    }

    // --------------------

    public void recycle() {

        contentType = null;
        contentLanguage = null;
        locale = DEFAULT_LOCALE;
        charset = null;
        characterEncoding = null;
        contentLength = -1;
        status = 200;
        message = null;
        commited = false;
        commitTime = -1;
        errorException = null;
        headers.clear();
        // Servlet 3.1 非阻塞写入监听器
        listener = null;
        fireListener = false;
        registeredForWrite = false;

        // 更新计数器
        contentWritten=0;
    }

    /**
     * 应用程序编写的字节 - i.e. 在压缩, 组块, 等之前.
     *
     * @return 应用程序写入响应的字节总数. 这将不是写入到网络的字节数，它可能大于或小于此值.
     */
    public long getContentWritten() {
        return contentWritten;
    }

    /**
     * 写入socket的字节 - i.e. 在压缩, 组块, 等之后.
     *
     * @param flush 在返回总字节之前是否应该刷新所有剩余字节? 如果{@code false}, 缓冲区中剩余的字节将不包含在返回值中
     *
     * @return 为此响应写入到套接字的字节总数
     */
    public long getBytesWritten(boolean flush) {
        if (flush) {
            action(ActionCode.CLIENT_FLUSH, this);
        }
        return outputBuffer.getBytesWritten();
    }

    /*
     * 这里是非阻塞输出的状态，因为它是从CoyoteOutputStream和Processor都很容易到达的一个点，它们都需要访问状态.
     */
    volatile WriteListener listener;
    private boolean fireListener = false;
    private boolean registeredForWrite = false;
    private final Object nonBlockingStateLock = new Object();

    public WriteListener getWriteListener() {
        return listener;
}

    public void setWriteListener(WriteListener listener) {
        if (listener == null) {
            throw new NullPointerException(
                    sm.getString("response.nullWriteListener"));
        }
        if (getWriteListener() != null) {
            throw new IllegalStateException(
                    sm.getString("response.writeListenerSet"));
        }
        // Note: 该类不用于HTTP升级，因此只需要测试异步
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.ASYNC_IS_ASYNC, result);
        if (!result.get()) {
            throw new IllegalStateException(
                    sm.getString("response.notAsync"));
        }

        this.listener = listener;

        // 容器负责首先调用 listener.onWritePossible(). 如果 isReady() 返回 true, 容器需要从一个新线程调用 listener.onWritePossible().
        // 如果 isReady() 返回 false, 将注册用于写入的 socket, 而且一旦数据可以写入, 容器将调用 listener.onWritePossible().
        if (isReady()) {
            synchronized (nonBlockingStateLock) {
                // 确保我们没有多个写入注册, 如果 ServletOutputStream.isReady() 返回 false, 在调用  onDataAvailable()期间
                registeredForWrite = true;
                // 需要设置 fireListener, 否则当容器试图触发 onWritePossible时, 无效
                fireListener = true;
            }
            action(ActionCode.DISPATCH_WRITE, null);
            if (!ContainerThreadMarker.isContainerThread()) {
                // Not on a container thread so need to execute the dispatch
                action(ActionCode.DISPATCH_EXECUTE, null);
            }
        }
    }

    public boolean isReady() {
        if (listener == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("response.notNonBlocking"));
            }
            return false;
        }
        // 假设无法写入
        boolean ready = false;
        synchronized (nonBlockingStateLock) {
            if (registeredForWrite) {
                fireListener = true;
                return false;
            }
            ready = checkRegisterForWrite();
            fireListener = !ready;
        }
        return ready;
    }

    public boolean checkRegisterForWrite() {
        AtomicBoolean ready = new AtomicBoolean(false);
        synchronized (nonBlockingStateLock) {
            if (!registeredForWrite) {
                action(ActionCode.NB_WRITE_INTEREST, ready);
                registeredForWrite = !ready.get();
            }
        }
        return ready.get();
    }

    public void onWritePossible() throws IOException {
        // 在以前的非阻塞写入中留下的任何缓冲数据都将写入Processor中, 因此，如果达到这一点，应用程序就能够写入数据.
        boolean fire = false;
        synchronized (nonBlockingStateLock) {
            registeredForWrite = false;
            if (fireListener) {
                fireListener = false;
                fire = true;
            }
        }
        if (fire) {
            listener.onWritePossible();
        }
    }
}
