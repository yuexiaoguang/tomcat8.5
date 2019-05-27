package org.apache.coyote.ajp;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ErrorState;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * AJP 处理器实现.
 */
public class AjpProcessor extends AbstractProcessor {

    private static final Log log = LogFactory.getLog(AjpProcessor.class);
    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(AjpProcessor.class);


    /**
     * 结束消息数组.
     */
    private static final byte[] endMessageArray;
    private static final byte[] endAndCloseMessageArray;


    /**
     * 刷新消息数组.
     */
    private static final byte[] flushMessageArray;


    /**
     * Pong 消息数组.
     */
    private static final byte[] pongMessageArray;


    static {
        // 分配结束消息数组
        AjpMessage endMessage = new AjpMessage(16);
        endMessage.reset();
        endMessage.appendByte(Constants.JK_AJP13_END_RESPONSE);
        endMessage.appendByte(1);
        endMessage.end();
        endMessageArray = new byte[endMessage.getLen()];
        System.arraycopy(endMessage.getBuffer(), 0, endMessageArray, 0,
                endMessage.getLen());

        // 分配结束和关闭消息数组
        AjpMessage endAndCloseMessage = new AjpMessage(16);
        endAndCloseMessage.reset();
        endAndCloseMessage.appendByte(Constants.JK_AJP13_END_RESPONSE);
        endAndCloseMessage.appendByte(0);
        endAndCloseMessage.end();
        endAndCloseMessageArray = new byte[endAndCloseMessage.getLen()];
        System.arraycopy(endAndCloseMessage.getBuffer(), 0, endAndCloseMessageArray, 0,
                endAndCloseMessage.getLen());

        // 分配刷新消息数组
        AjpMessage flushMessage = new AjpMessage(16);
        flushMessage.reset();
        flushMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
        flushMessage.appendInt(0);
        flushMessage.appendByte(0);
        flushMessage.end();
        flushMessageArray = new byte[flushMessage.getLen()];
        System.arraycopy(flushMessage.getBuffer(), 0, flushMessageArray, 0,
                flushMessage.getLen());

        // 分配 pong 消息数组
        AjpMessage pongMessage = new AjpMessage(16);
        pongMessage.reset();
        pongMessage.appendByte(Constants.JK_AJP13_CPONG_REPLY);
        pongMessage.end();
        pongMessageArray = new byte[pongMessage.getLen()];
        System.arraycopy(pongMessage.getBuffer(), 0, pongMessageArray,
                0, pongMessage.getLen());
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * GetBody message array. 不像其它消息数组那样是 static, 因为消息随 packetSize变化, 并且每个连接器可以变化.
     */
    private final byte[] getBodyMessageArray;


    /**
     * AJP 包大小.
     */
    private final int outputMaxChunkSize;

    /**
     * Header 消息. 请注意，该 header 仅是在处理“请求”的第一条消息时使用的header, 因此它可能不是一个请求 header.
     * 在整个请求的处理过程中，它将保持不变.
     */
    private final AjpMessage requestHeaderMessage;


    /**
     * 用于响应合成的消息.
     */
    private final AjpMessage responseMessage;


    /**
     * 响应消息的下一次写入位置 (与非阻塞写入一起使用，当消息不能以单个写入方式写入时).
     * -1 表示没有消息写入缓冲区.
     */
    private int responseMsgPos = -1;


    /**
     * Body 消息.
     */
    private final AjpMessage bodyMessage;


    /**
     * Body 消息.
     */
    private final MessageBytes bodyBytes = MessageBytes.newInstance();


    /**
     * Host 名称 (用于避免主机名称上无用的B2C转换).
     */
    private char[] hostNameC = new char[0];


    /**
     * 用于处理的临时消息字节.
     */
    private final MessageBytes tmpMB = MessageBytes.newInstance();


    /**
     * 证书的字节块.
     */
    private final MessageBytes certificates = MessageBytes.newInstance();


    /**
     * 流结尾的标志.
     */
    private boolean endOfStream = false;


    /**
     * 空的请求主体的标志.
     */
    private boolean empty = true;


    /**
     * 第一次读取.
     */
    private boolean first = true;


    /**
     * 表示已经发送 'get body chunk' 消息, 但是还没有收到主体块.
     */
    private boolean waitingForBodyMessage = false;


    /**
     * 重新读取.
     */
    private boolean replay = false;


    /**
     * 任何响应主体应该被忽略, 不发送到客户端.
     */
    private boolean swallowResponse = false;


    /**
     * 结束的响应.
     */
    private boolean responseFinished = false;


    /**
     * 为当前请求写入客户端的字节.
     */
    private long bytesWritten = 0;




    // ------------------------------------------------------------ Constructor

    public AjpProcessor(int packetSize, AbstractEndpoint<?> endpoint) {

        super(endpoint);

        // 计算最大块大小, 因为 packetSize 可能会从默认的(Constants.MAX_PACKET_SIZE)修改
        this.outputMaxChunkSize =
                Constants.MAX_SEND_SIZE + packetSize - Constants.MAX_PACKET_SIZE;

        request.setInputBuffer(new SocketInputBuffer());

        requestHeaderMessage = new AjpMessage(packetSize);
        responseMessage = new AjpMessage(packetSize);
        bodyMessage = new AjpMessage(packetSize);

        // 设置 getBody 消息缓冲区
        AjpMessage getBodyMessage = new AjpMessage(16);
        getBodyMessage.reset();
        getBodyMessage.appendByte(Constants.JK_AJP13_GET_BODY_CHUNK);
        // 调整读取大小, 如果 packetSize != default (Constants.MAX_PACKET_SIZE)
        getBodyMessage.appendInt(Constants.MAX_READ_SIZE + packetSize -
                Constants.MAX_PACKET_SIZE);
        getBodyMessage.end();
        getBodyMessageArray = new byte[getBodyMessage.getLen()];
        System.arraycopy(getBodyMessage.getBuffer(), 0, getBodyMessageArray,
                0, getBodyMessage.getLen());

        response.setOutputBuffer(new SocketOutputBuffer());
    }


    // ------------------------------------------------------------- Properties


    /**
     * 刷新时发送AJP刷新包.
     * 刷新包是零字节的 AJP13 SEND_BODY_CHUNK 包. mod_jk 和 mod_proxy_ajp 将这个解释为刷新数据到客户端的请求.
     * AJP 总是在响应时刷新, 所以数据包流到客户端不重要, 不要使用额外的刷新包.
     * 对于兼容性和安全性, 默认情况下启用刷新包.
     */
    protected boolean ajpFlush = true;
    public boolean getAjpFlush() { return ajpFlush; }
    public void setAjpFlush(boolean ajpFlush) {
        this.ajpFlush = ajpFlush;
    }


    /**
     * 在关闭连接之前，Tomcat将等待后续请求的毫秒数. 默认 -1 不限制.
     */
    private int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }


    /**
     * 是否使用 Tomcat 验证 ?
     */
    private boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }


    /**
     * 是否使用 Tomcat 验证 ?
     */
    private boolean tomcatAuthorization = false;
    public boolean getTomcatAuthorization() { return tomcatAuthorization; }
    public void setTomcatAuthorization(boolean tomcatAuthorization) {
        this.tomcatAuthorization = tomcatAuthorization;
    }


    /**
     * 必需的安全.
     */
    private String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) {
        this.requiredSecret = requiredSecret;
    }


    /**
     * 当客户端证书信息以表单形式呈现时, 而不是{@link java.security.cert.X509Certificate}实例, 它需要在使用之前转换, 此属性控制用于执行转换的JSSE提供程序.
     */
    private String clientCertProvider = null;
    public String getClientCertProvider() { return clientCertProvider; }
    public void setClientCertProvider(String clientCertProvider) {
        this.clientCertProvider = clientCertProvider;
    }

    @Deprecated
    private boolean sendReasonPhrase = false;
    @Deprecated
    void setSendReasonPhrase(boolean sendReasonPhrase) {
        this.sendReasonPhrase = sendReasonPhrase;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    protected boolean flushBufferedWrite() throws IOException {
        if (hasDataToWrite()) {
            socketWrapper.flush(false);
            if (hasDataToWrite()) {
                // 有数据要写，但要通过响应来保持非阻塞状态的一致视图
                response.checkRegisterForWrite();
                return true;
            }
        }
        return false;
    }


    @Override
    protected void dispatchNonBlockingRead() {
        if (available(true) > 0) {
            super.dispatchNonBlockingRead();
        }
    }


    @Override
    protected SocketState dispatchEndRequest() {
        // 如果启用，请为下一个请求设置keep alive超时时间
        if (keepAliveTimeout > 0) {
            socketWrapper.setReadTimeout(keepAliveTimeout);
        }
        recycle();
        return SocketState.OPEN;
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socket) throws IOException {

        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // 设置 socket
        this.socketWrapper = socket;

        int soTimeout = endpoint.getConnectionTimeout();
        boolean cping = false;

        boolean keptAlive = false;

        while (!getErrorState().isError() && !endpoint.isPaused()) {
            // 解析请求 header
            try {
                // 获取请求的第一条消息
                if (!readMessage(requestHeaderMessage, !keptAlive)) {
                    break;
                }
                // Set back timeout if keep alive timeout is enabled
                if (keepAliveTimeout > 0) {
                    socketWrapper.setReadTimeout(soTimeout);
                }
                // 检查消息类型, 立即处理和中断，如果不是常规请求处理
                int type = requestHeaderMessage.getByte();
                if (type == Constants.JK_AJP13_CPING_REQUEST) {
                    if (endpoint.isPaused()) {
                        recycle();
                        break;
                    }
                    cping = true;
                    try {
                        socketWrapper.write(true, pongMessageArray, 0, pongMessageArray.length);
                        socketWrapper.flush(true);
                    } catch (IOException e) {
                        setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                    }
                    recycle();
                    continue;
                } else if(type != Constants.JK_AJP13_FORWARD_REQUEST) {
                    // 不希望的包类型. Unread body packets should have
                    // been swallowed in finish().
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Unexpected message: " + type);
                    }
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, null);
                    break;
                }
                keptAlive = true;
                request.setStartTime(System.currentTimeMillis());
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                break;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                getLog().debug(sm.getString("ajpprocessor.header.error"), t);
                // 400 - Bad Request
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, t);
                getAdapter().log(request, response, 0);
            }

            if (!getErrorState().isError()) {
                // 设置过滤器, 并解析一些请求 header
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                try {
                    prepareRequest();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().debug(sm.getString("ajpprocessor.request.prepare"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            if (!getErrorState().isError() && !cping && endpoint.isPaused()) {
                // 503 - Service unavailable
                response.setStatus(503);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
                getAdapter().log(request, response, 0);
            }
            cping = false;

            // 在适配器中处理请求
            if (!getErrorState().isError()) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    getAdapter().service(request, response);
                } catch (InterruptedIOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().error(sm.getString("ajpprocessor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            if (isAsync() && !getErrorState().isError()) {
                break;
            }

            // 如果尚未完成，则完成响应
            if (!responseFinished && getErrorState().isIoAllowed()) {
                try {
                    action(ActionCode.COMMIT, null);
                    finishResponse();
                } catch (IOException ioe){
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    setErrorState(ErrorState.CLOSE_NOW, t);
                }
            }

            // 如果有错误, 确保请求被计数为错误，并更新统计计数器
            if (getErrorState().isError()) {
                response.setStatus(500);
            }
            request.updateCounters();

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);
            // 如果启用, 为下一个请求设置 keep alive超时时间
            if (keepAliveTimeout > 0) {
                socketWrapper.setReadTimeout(keepAliveTimeout);
            }

            recycle();
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || endpoint.isPaused()) {
            return SocketState.CLOSED;
        } else {
            if (isAsync()) {
                return SocketState.LONG;
            } else {
                return SocketState.OPEN;
            }
        }
    }


    @Override
    public void recycle() {
        getAdapter().checkRecycled(request, response);
        super.recycle();
        request.recycle();
        response.recycle();
        first = true;
        endOfStream = false;
        waitingForBodyMessage = false;
        empty = true;
        replay = false;
        responseFinished = false;
        certificates.recycle();
        swallowResponse = false;
        bytesWritten = 0;
    }


    @Override
    public void pause() {
        // NOOP for AJP
    }


    // ------------------------------------------------------ Protected Methods

    // Methods used by SocketInputBuffer
    /**
     * 读取 AJP 主体消息. 用于读取ajp13中的 'special' 包并接收数据，在发送了一个 GET_BODY 包之后.
     *
     * @param block 如果调用该方法时没有可读取的数据, 在数据可用之前该调用是否阻塞?
     *
     * @return <code>true</code> 如果读取至少一个主体字节, 否则<code>false</code>
     */
    private boolean receive(boolean block) throws IOException {

        bodyMessage.reset();

        if (!readMessage(bodyMessage, block)) {
            return false;
        }

        waitingForBodyMessage = false;

        // 未接收到数据.
        if (bodyMessage.getLen() == 0) {
            // 只有 header
            return false;
        }
        int blen = bodyMessage.peekInt();
        if (blen == 0) {
            return false;
        }

        bodyMessage.getBodyBytes(bodyBytes);
        empty = false;
        return true;
    }


    /**
     * 读取 AJP 消息.
     *
     * @param message   要填充的信息
     * @param block 如果调用该方法时没有可读取的数据, 在数据可用之前该调用是否阻塞?

     * @return true 如果消息已被读取, false 如果没有读取数据
     *
     * @throws IOException 任何其他失败, 包括不完整的读取
     */
    private boolean readMessage(AjpMessage message, boolean block)
        throws IOException {

        byte[] buf = message.getBuffer();

        if (!read(buf, 0, Constants.H_SIZE, block)) {
            return false;
        }

        int messageLength = message.processHeader(true);
        if (messageLength < 0) {
            // 无效的 AJP header 签名
            throw new IOException(sm.getString("ajpmessage.invalidLength",
                    Integer.valueOf(messageLength)));
        }
        else if (messageLength == 0) {
            // 零长度消息.
            return true;
        }
        else {
            if (messageLength > message.getBuffer().length) {
                // 缓冲区消息过长
                // 需要触发一个 400 消息
                throw new IllegalArgumentException(sm.getString(
                        "ajpprocessor.header.tooLong",
                        Integer.valueOf(messageLength),
                        Integer.valueOf(buf.length)));
            }
            read(buf, Constants.H_SIZE, messageLength, true);
            return true;
        }
    }


    /**
     * 从Web服务器获取更多请求主体数据并将其存储在内部缓冲区中.
     * 
     * @param block <code>true</code>如果是阻塞 IO
     * @return <code>true</code>如果有更多数据, 否则<code>false</code>.
     * @throws IOException 发生IO错误
     */
    protected boolean refillReadBuffer(boolean block) throws IOException {
        // 使用重用时 (e.g. after FORM auth) 所有读取的数据都已缓冲，因此没有机会重新填充缓冲区.
        if (replay) {
            endOfStream = true; // 已经阅读了那里的一切
        }
        if (endOfStream) {
            return false;
        }

        if (first) {
            first = false;
            long contentLength = request.getContentLengthLong();
            // - 当内容长度 > 0, AJP 自动发送第一个主体消息.
            // - 当内容长度 == 0, AJP 不发送主体信息.
            // - 当内容长度未知, AJP 不会自动发送第一个主体消息.
            if (contentLength > 0) {
                waitingForBodyMessage = true;
            } else if (contentLength == 0) {
                endOfStream = true;
                return false;
            }
        }

        // 立即请求更多数据
        if (!waitingForBodyMessage) {
            socketWrapper.write(true, getBodyMessageArray, 0, getBodyMessageArray.length);
            socketWrapper.flush(true);
            waitingForBodyMessage = true;
        }

        boolean moreData = receive(block);
        if (!moreData && !waitingForBodyMessage) {
            endOfStream = true;
        }
        return moreData;
    }


    /**
     * 在读取请求header之后, 必须设置请求过滤器.
     */
    private void prepareRequest() {

        // 将 HTTP 方法代码转换为 String.
        byte methodCode = requestHeaderMessage.getByte();
        if (methodCode != Constants.SC_M_JK_STORED) {
            String methodName = Constants.getMethodForCode(methodCode - 1);
            request.method().setString(methodName);
        }

        requestHeaderMessage.getBytes(request.protocol());
        requestHeaderMessage.getBytes(request.requestURI());

        requestHeaderMessage.getBytes(request.remoteAddr());
        requestHeaderMessage.getBytes(request.remoteHost());
        requestHeaderMessage.getBytes(request.localName());
        request.setLocalPort(requestHeaderMessage.getInt());

        boolean isSSL = requestHeaderMessage.getByte() != 0;
        if (isSSL) {
            request.scheme().setString("https");
        }

        // 解码 header
        MimeHeaders headers = request.getMimeHeaders();

        // 每次通过JMX改变限制
        headers.setLimit(endpoint.getMaxHeaderCount());

        boolean contentLengthSet = false;
        int hCount = requestHeaderMessage.getInt();
        for(int i = 0 ; i < hCount ; i++) {
            String hName = null;

            // Header 名称被编码为以 0xA0 开始的整数码, 或是正常的字符串 (前两个字节是长度).
            int isc = requestHeaderMessage.peekInt();
            int hId = isc & 0xFF;

            MessageBytes vMB = null;
            isc &= 0xFF00;
            if(0xA000 == isc) {
                requestHeaderMessage.getInt(); // 提前读取位置
                hName = Constants.getHeaderForCode(hId - 1);
                vMB = headers.addValue(hName);
            } else {
                // 重置 hId -- 如果当前正在读取的报头恰好是7字节或8字节长, 下面的代码将认为它是 content-type header 或 content-length header - SC_REQ_CONTENT_TYPE=7,
                // SC_REQ_CONTENT_LENGTH=8 - 导致意想不到的行为.  see bug 5861 for more information.
                hId = -1;
                requestHeaderMessage.getBytes(tmpMB);
                ByteChunk bc = tmpMB.getByteChunk();
                vMB = headers.addValue(bc.getBuffer(),
                        bc.getStart(), bc.getLength());
            }

            requestHeaderMessage.getBytes(vMB);

            if (hId == Constants.SC_REQ_CONTENT_LENGTH ||
                    (hId == -1 && tmpMB.equalsIgnoreCase("Content-Length"))) {
                long cl = vMB.getLong();
                if (contentLengthSet) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                } else {
                    contentLengthSet = true;
                    // 设置请求的 content-length header
                    request.setContentLength(cl);
                }
            } else if (hId == Constants.SC_REQ_CONTENT_TYPE ||
                    (hId == -1 && tmpMB.equalsIgnoreCase("Content-Type"))) {
                // 只读取 content-type header, 因此设置它
                ByteChunk bchunk = vMB.getByteChunk();
                request.contentType().setBytes(bchunk.getBytes(),
                        bchunk.getOffset(),
                        bchunk.getLength());
            }
        }

        // 解码额外的属性
        boolean secret = false;
        byte attributeCode;
        while ((attributeCode = requestHeaderMessage.getByte())
                != Constants.SC_A_ARE_DONE) {

            switch (attributeCode) {

            case Constants.SC_A_REQ_ATTRIBUTE :
                requestHeaderMessage.getBytes(tmpMB);
                String n = tmpMB.toString();
                requestHeaderMessage.getBytes(tmpMB);
                String v = tmpMB.toString();
                /*
                 * AJP13 没有转发本地IP地址和远程端口的错误. 允许AJP连接器通过私有请求属性添加此信息.
                 * 将接受转发的数据，并将其从请求属性的公共列表中删除.
                 */
                if(n.equals(Constants.SC_A_REQ_LOCAL_ADDR)) {
                    request.localAddr().setString(v);
                } else if(n.equals(Constants.SC_A_REQ_REMOTE_PORT)) {
                    try {
                        request.setRemotePort(Integer.parseInt(v));
                    } catch (NumberFormatException nfe) {
                        // Ignore invalid value
                    }
                } else if(n.equals(Constants.SC_A_SSL_PROTOCOL)) {
                    request.setAttribute(SSLSupport.PROTOCOL_VERSION_KEY, v);
                } else {
                    request.setAttribute(n, v );
                }
                break;

            case Constants.SC_A_CONTEXT :
                requestHeaderMessage.getBytes(tmpMB);
                // nothing
                break;

            case Constants.SC_A_SERVLET_PATH :
                requestHeaderMessage.getBytes(tmpMB);
                // nothing
                break;

            case Constants.SC_A_REMOTE_USER :
                if (tomcatAuthorization || !tomcatAuthentication) {
                    // Implies tomcatAuthentication == false
                    requestHeaderMessage.getBytes(request.getRemoteUser());
                    request.setRemoteUserNeedsAuthorization(tomcatAuthorization);
                } else {
                    // 从反向代理忽略用户信息
                    requestHeaderMessage.getBytes(tmpMB);
                }
                break;

            case Constants.SC_A_AUTH_TYPE :
                if (tomcatAuthentication) {
                    // ignore server
                    requestHeaderMessage.getBytes(tmpMB);
                } else {
                    requestHeaderMessage.getBytes(request.getAuthType());
                }
                break;

            case Constants.SC_A_QUERY_STRING :
                requestHeaderMessage.getBytes(request.queryString());
                break;

            case Constants.SC_A_JVM_ROUTE :
                requestHeaderMessage.getBytes(tmpMB);
                // nothing
                break;

            case Constants.SC_A_SSL_CERT :
                // SSL证书提取是延迟的, 移动到 JkCoyoteHandler
                requestHeaderMessage.getBytes(certificates);
                break;

            case Constants.SC_A_SSL_CIPHER :
                requestHeaderMessage.getBytes(tmpMB);
                request.setAttribute(SSLSupport.CIPHER_SUITE_KEY,
                        tmpMB.toString());
                break;

            case Constants.SC_A_SSL_SESSION :
                requestHeaderMessage.getBytes(tmpMB);
                request.setAttribute(SSLSupport.SESSION_ID_KEY,
                        tmpMB.toString());
                break;

            case Constants.SC_A_SSL_KEY_SIZE :
                request.setAttribute(SSLSupport.KEY_SIZE_KEY,
                        Integer.valueOf(requestHeaderMessage.getInt()));
                break;

            case Constants.SC_A_STORED_METHOD:
                requestHeaderMessage.getBytes(request.method());
                break;

            case Constants.SC_A_SECRET:
                requestHeaderMessage.getBytes(tmpMB);
                if (requiredSecret != null) {
                    secret = true;
                    if (!tmpMB.equals(requiredSecret)) {
                        response.setStatus(403);
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                    }
                }
                break;

            default:
                // 向后兼容忽略未知属性
                break;
            }
        }

        // 检查是否需要提交安全文件
        if ((requiredSecret != null) && !secret) {
            response.setStatus(403);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
        }

        // 检查一个完整的URI (包括协议://host:port/)
        ByteChunk uriBC = request.requestURI().getByteChunk();
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 0, 3, 4);
            int uriBCStart = uriBC.getStart();
            int slashPos = -1;
            if (pos != -1) {
                byte[] uriB = uriBC.getBytes();
                slashPos = uriBC.indexOf('/', pos + 3);
                if (slashPos == -1) {
                    slashPos = uriBC.getLength();
                    // Set URI as "/"
                    request.requestURI().setBytes
                    (uriB, uriBCStart + pos + 1, 1);
                } else {
                    request.requestURI().setBytes
                    (uriB, uriBCStart + slashPos,
                            uriBC.getLength() - slashPos);
                }
                MessageBytes hostMB = headers.setValue("host");
                hostMB.setBytes(uriB, uriBCStart + pos + 3,
                        slashPos - pos - 3);
            }

        }

        MessageBytes valueMB = request.getMimeHeaders().getValue("host");
        parseHost(valueMB);

        if (getErrorState().isError()) {
            getAdapter().log(request, response, 0);
        }
    }


    /**
     * 解析主机.
     */
    private void parseHost(MessageBytes valueMB) {

        if (valueMB == null || valueMB.isNull()) {
            // HTTP/1.0
            request.setServerPort(request.getLocalPort());
            try {
                request.serverName().duplicate(request.localName());
            } catch (IOException e) {
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, e);
            }
            return;
        }

        ByteChunk valueBC = valueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();
        int colonPos = -1;
        if (hostNameC.length < valueL) {
            hostNameC = new char[valueL];
        }

        boolean ipv6 = (valueB[valueS] == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB[i + valueS];
            hostNameC[i] = b;
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            request.serverName().setChars(hostNameC, 0, valueL);
        } else {

            request.serverName().setChars(hostNameC, 0, colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.getDec(valueB[i + valueS]);
                if (charValue == -1) {
                    // Invalid character
                    // 400 - Bad request
                    response.setStatus(400);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                    break;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);
        }
    }


    /**
     * 提交响应时, 必须验证一组 header, 以及设置响应过滤器.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected final void prepareResponse() throws IOException {

        response.setCommitted(true);

        tmpMB.recycle();
        responseMsgPos = -1;
        responseMessage.reset();
        responseMessage.appendByte(Constants.JK_AJP13_SEND_HEADERS);

        // 某些状态码的响应不允许包括响应体.
        int statusCode = response.getStatus();
        if (statusCode < 200 || statusCode == 204 || statusCode == 205 ||
                statusCode == 304) {
            // 没有实体主体
            swallowResponse = true;
        }

        // HEAD 请求的响应不允许包括响应主体.
        MessageBytes methodMB = request.method();
        if (methodMB.equals("HEAD")) {
            // No entity body
            swallowResponse = true;
        }

        // HTTP header 内容
        responseMessage.appendInt(statusCode);
        if (sendReasonPhrase) {
            String message = null;
            if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER &&
                    HttpMessages.isSafeInHttpHeader(response.getMessage())) {
                message = response.getMessage();
            }
            if (message == null) {
                message = HttpMessages.getInstance(
                        response.getLocale()).getMessage(response.getStatus());
            }
            if (message == null) {
                // mod_jk + httpd 2.x fails with a null status message - bug 45026
                message = Integer.toString(response.getStatus());
            }
            tmpMB.setString(message);
        } else {
            // Reason phrase is optional but mod_jk + httpd 2.x fails with a null
            // reason phrase - bug 45026
            tmpMB.setString(Integer.toString(response.getStatus()));
        }
        responseMessage.appendBytes(tmpMB);

        // Special headers
        MimeHeaders headers = response.getMimeHeaders();
        String contentType = response.getContentType();
        if (contentType != null) {
            headers.setValue("Content-Type").setString(contentType);
        }
        String contentLanguage = response.getContentLanguage();
        if (contentLanguage != null) {
            headers.setValue("Content-Language").setString(contentLanguage);
        }
        long contentLength = response.getContentLengthLong();
        if (contentLength >= 0) {
            headers.setValue("Content-Length").setLong(contentLength);
        }

        // Other headers
        int numHeaders = headers.size();
        responseMessage.appendInt(numHeaders);
        for (int i = 0; i < numHeaders; i++) {
            MessageBytes hN = headers.getName(i);
            int hC = Constants.getResponseAjpIndex(hN.toString());
            if (hC > 0) {
                responseMessage.appendInt(hC);
            }
            else {
                responseMessage.appendBytes(hN);
            }
            MessageBytes hV=headers.getValue(i);
            responseMessage.appendBytes(hV);
        }

        // 写入缓冲区
        responseMessage.end();
        socketWrapper.write(true, responseMessage.getBuffer(), 0, responseMessage.getLen());
        socketWrapper.flush(true);
    }


    /**
     * 从缓冲区写入数据的回调.
     */
    @Override
    protected final void flush() throws IOException {
        // 调用代码应该确保缓冲区中没有数据用于非阻塞写入.
        // TODO Validate the assertion above
        if (!responseFinished) {
            if (ajpFlush) {
                // 发送刷新消息
                socketWrapper.write(true, flushMessageArray, 0, flushMessageArray.length);
            }
            socketWrapper.flush(true);
        }
    }


    /**
     * 结束 AJP 响应.
     */
    @Override
    protected final void finishResponse() throws IOException {
        if (responseFinished)
            return;

        responseFinished = true;

        // 忽略未读的主体包
        if (waitingForBodyMessage || first && request.getContentLengthLong() > 0) {
            refillReadBuffer(true);
        }

        // 添加结束消息
        if (getErrorState().isError()) {
            socketWrapper.write(true, endAndCloseMessageArray, 0, endAndCloseMessageArray.length);
        } else {
            socketWrapper.write(true, endMessageArray, 0, endMessageArray.length);
        }
        socketWrapper.flush(true);
    }


    @Override
    protected final void ack() {
        // NO-OP for AJP
    }


    @Override
    protected final int available(boolean doRead) {
        if (endOfStream) {
            return 0;
        }
        if (empty && doRead) {
            try {
                refillReadBuffer(false);
            } catch (IOException timeout) {
                // Not ideal. 这将指示数据是可用的, 并触发一个读取, 依次触发另一个 IOException.
                return 1;
            }
        }
        if (empty) {
            return 0;
        } else {
            return bodyBytes.getByteChunk().getLength();
        }
    }


    @Override
    protected final void setRequestBody(ByteChunk body) {
        int length = body.getLength();
        bodyBytes.setBytes(body.getBytes(), body.getStart(), length);
        request.setContentLength(length);
        first = false;
        empty = false;
        replay = true;
        endOfStream = false;
    }


    @Override
    protected final void setSwallowResponse() {
        swallowResponse = true;
    }


    @Override
    protected final void disableSwallowRequest() {
        /* NO-OP
         * 使用 AJP, 当客户端发送请求主体数据时，Tomcat控制. 至多会有一个单独的数据包读取, 而且那些将在 finishResponse() 中处理.
         */
    }


    @Override
    protected final boolean getPopulateRequestAttributesFromSocket() {
        // NO-OPs 属性请求, 因为在解析第一个AJP消息时它们是预先填充的.
        return false;
    }


    @Override
    protected final void populateRequestAttributeRemoteHost() {
        // 使用DNS解析获取远程主机名
        if (request.remoteHost().isNull()) {
            try {
                request.remoteHost().setString(InetAddress.getByName
                        (request.remoteAddr().toString()).getHostName());
            } catch (IOException iex) {
                // Ignore
            }
        }
    }


    @Override
    protected final void populateSslRequestAttributes() {
        if (!certificates.isNull()) {
            ByteChunk certData = certificates.getByteChunk();
            X509Certificate jsseCerts[] = null;
            ByteArrayInputStream bais =
                new ByteArrayInputStream(certData.getBytes(),
                        certData.getStart(),
                        certData.getLength());
            // 填充数据.
            try {
                CertificateFactory cf;
                String clientCertProvider = getClientCertProvider();
                if (clientCertProvider == null) {
                    cf = CertificateFactory.getInstance("X.509");
                } else {
                    cf = CertificateFactory.getInstance("X.509",
                            clientCertProvider);
                }
                while(bais.available() > 0) {
                    X509Certificate cert = (X509Certificate)
                    cf.generateCertificate(bais);
                    if(jsseCerts == null) {
                        jsseCerts = new X509Certificate[1];
                        jsseCerts[0] = cert;
                    } else {
                        X509Certificate [] temp = new X509Certificate[jsseCerts.length+1];
                        System.arraycopy(jsseCerts,0,temp,0,jsseCerts.length);
                        temp[jsseCerts.length] = cert;
                        jsseCerts = temp;
                    }
                }
            } catch (java.security.cert.CertificateException e) {
                getLog().error(sm.getString("ajpprocessor.certs.fail"), e);
                return;
            } catch (NoSuchProviderException e) {
                getLog().error(sm.getString("ajpprocessor.certs.fail"), e);
                return;
            }
            request.setAttribute(SSLSupport.CERTIFICATE_KEY, jsseCerts);
        }
    }


    @Override
    protected final boolean isRequestBodyFullyRead() {
        return endOfStream;
    }


    @Override
    protected final void registerReadInterest() {
        socketWrapper.registerReadInterest();
    }


    @Override
    protected final boolean isReady() {
        return responseMsgPos == -1 && socketWrapper.isReadyForWrite();
    }


    /**
     * 至少读取指定数量的字节, 并把它们放在输入缓冲区中. 
     * 注意，如果有任何数据可供读取，则该方法将始终阻塞，直到至少已读取指定数量的字节为止.
     *
     * @param buf   要读取数据的缓冲区
     * @param pos   开始位置
     * @param n     要读取的最小字节数
     * @param block 如果调用该方法时没有可读取的数据, 在数据可用之前该调用是否阻塞?
     * 
     * @return  <code>true</code> 如果读取了请求数量的字节, 否则<code>false</code>
     * @throws IOException
     */
    private boolean read(byte[] buf, int pos, int n, boolean block) throws IOException {
        int read = socketWrapper.read(block, buf, pos, n);
        if (read > 0 && read < n) {
            int left = n - read;
            int start = pos + read;
            while (left > 0) {
                read = socketWrapper.read(true, buf, start, left);
                if (read == -1) {
                    throw new EOFException();
                }
                left = left - read;
                start = start + read;
            }
        } else if (read == -1) {
            throw new EOFException();
        }

        return read > 0;
    }


    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    private void writeData(ByteChunk chunk) throws IOException {
        boolean blocking = (response.getWriteListener() == null);

        int len = chunk.getLength();
        int off = 0;

        // 写入这个块
        while (len > 0) {
            int thisTime = Math.min(len, outputMaxChunkSize);

            responseMessage.reset();
            responseMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
            responseMessage.appendBytes(chunk.getBytes(), chunk.getOffset() + off, thisTime);
            responseMessage.end();
            socketWrapper.write(blocking, responseMessage.getBuffer(), 0, responseMessage.getLen());
            socketWrapper.flush(blocking);

            len -= thisTime;
            off += thisTime;
        }

        bytesWritten += off;
    }


    private void writeData(ByteBuffer chunk) throws IOException {
        boolean blocking = (response.getWriteListener() == null);

        int len = chunk.remaining();
        int off = 0;

        // 写入这个块
        while (len > 0) {
            int thisTime = Math.min(len, outputMaxChunkSize);

            responseMessage.reset();
            responseMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
            chunk.limit(chunk.position() + thisTime);
            responseMessage.appendBytes(chunk);
            responseMessage.end();
            socketWrapper.write(blocking, responseMessage.getBuffer(), 0, responseMessage.getLen());
            socketWrapper.flush(blocking);

            len -= thisTime;
            off += thisTime;
        }

        bytesWritten += off;
    }


    private boolean hasDataToWrite() {
        return responseMsgPos != -1 || socketWrapper.hasDataToWrite();
    }


    @Override
    protected Log getLog() {
        return log;
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class

    /**
     * 这个类是一个输入缓冲区，它将从一个输入流读取它的数据.
     */
    protected class SocketInputBuffer implements InputBuffer {

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doRead(ApplicationBufferHandler)}
         */
        @Deprecated
        @Override
        public int doRead(ByteChunk chunk) throws IOException {

            if (endOfStream) {
                return -1;
            }
            if (empty) {
                if (!refillReadBuffer(true)) {
                    return -1;
                }
            }
            ByteChunk bc = bodyBytes.getByteChunk();
            chunk.setBytes(bc.getBuffer(), bc.getStart(), bc.getLength());
            empty = true;
            return chunk.getLength();
        }

        @Override
        public int doRead(ApplicationBufferHandler handler) throws IOException {

            if (endOfStream) {
                return -1;
            }
            if (empty) {
                if (!refillReadBuffer(true)) {
                    return -1;
                }
            }
            ByteChunk bc = bodyBytes.getByteChunk();
            handler.setByteBuffer(ByteBuffer.wrap(bc.getBuffer(), bc.getStart(), bc.getLength()));
            empty = true;
            return handler.getByteBuffer().remaining();
        }
    }


    // ----------------------------------- OutputStreamOutputBuffer Inner Class

    /**
     * 这个类是一个输出缓冲区，它将把数据写入输出流.
     */
    protected class SocketOutputBuffer implements OutputBuffer {

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doWrite(ByteBuffer)}
         */
        @Deprecated
        @Override
        public int doWrite(ByteChunk chunk) throws IOException {

            if (!response.isCommitted()) {
                // Validate and write response headers
                try {
                    prepareResponse();
                } catch (IOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                }
            }

            if (!swallowResponse) {
                writeData(chunk);
            }
            return chunk.getLength();
        }

        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {

            if (!response.isCommitted()) {
                // 验证并写入响应 header
                try {
                    prepareResponse();
                } catch (IOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                }
            }

            int len = 0;
            if (!swallowResponse) {
                try {
                    len = chunk.remaining();
                    writeData(chunk);
                    len -= chunk.remaining();
                } catch (IOException ioe) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
                    // Re-throw
                    throw ioe;
                }
            }
            return len;
        }

        @Override
        public long getBytesWritten() {
            return bytesWritten;
        }
    }
}
