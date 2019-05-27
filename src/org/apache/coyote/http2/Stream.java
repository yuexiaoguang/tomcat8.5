package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.Locale;

import org.apache.coyote.ActionCode;
import org.apache.coyote.CloseNowException;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

public class Stream extends AbstractStream implements HeaderEmitter {

    private static final Log log = LogFactory.getLog(Stream.class);
    private static final StringManager sm = StringManager.getManager(Stream.class);

    private static final int HEADER_STATE_START = 0;
    private static final int HEADER_STATE_PSEUDO = 1;
    private static final int HEADER_STATE_REGULAR = 2;
    private static final int HEADER_STATE_TRAILER = 3;

    private static final Response ACK_RESPONSE = new Response();

    static {
        ACK_RESPONSE.setStatus(100);
    }

    private volatile int weight = Constants.DEFAULT_WEIGHT;
    private volatile long contentLengthReceived = 0;

    private final Http2UpgradeHandler handler;
    private final StreamStateMachine state;
    // 状态机有太多的开销
    private int headerState = HEADER_STATE_START;
    private StreamException headerException = null;
    // TODO: null 完成时减少关闭流使用的内存
    private final Request coyoteRequest;
    private StringBuilder cookieHeader = null;
    private final Response coyoteResponse = new Response();
    private final StreamInputBuffer inputBuffer;
    private final StreamOutputBuffer outputBuffer = new StreamOutputBuffer();


    public Stream(Integer identifier, Http2UpgradeHandler handler) {
        this(identifier, handler, null);
    }


    public Stream(Integer identifier, Http2UpgradeHandler handler, Request coyoteRequest) {
        super(identifier);
        this.handler = handler;
        handler.addChild(this);
        setWindowSize(handler.getRemoteSettings().getInitialWindowSize());
        state = new StreamStateMachine(this);
        if (coyoteRequest == null) {
            // HTTP/2 new request
            this.coyoteRequest = new Request();
            this.inputBuffer = new StreamInputBuffer();
            this.coyoteRequest.setInputBuffer(inputBuffer);
        } else {
            // HTTP/1.1 upgrade
            this.coyoteRequest = coyoteRequest;
            this.inputBuffer = null;
            // 这里已经填充了Header
            state.receivedStartOfHeaders();
            // TODO 假设此时已读取的主体无效
            state.receivedEndOfStream();
        }
        // No sendfile for HTTP/2 (它在请求中默认启用)
        this.coyoteRequest.setSendfile(false);
        this.coyoteResponse.setOutputBuffer(outputBuffer);
        this.coyoteRequest.setResponse(coyoteResponse);
        this.coyoteRequest.protocol().setString("HTTP/2.0");
        if (this.coyoteRequest.getStartTime() < 0) {
            this.coyoteRequest.setStartTime(System.currentTimeMillis());
        }
    }


    void rePrioritise(AbstractStream parent, boolean exclusive, int weight) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reprioritisation.debug",
                    getConnectionId(), getIdentifier(), Boolean.toString(exclusive),
                    parent.getIdentifier(), Integer.toString(weight)));
        }

        // 检查新的 parent 是否是该流的后代
        if (isDescendant(parent)) {
            parent.detachFromParent();
            // 总是安全的, 因为这个流的后代必须是 Stream 的实例
            getParentStream().addChild((Stream) parent);
        }

        if (exclusive) {
            // 需要移动新的parent的子级到这个流的子级. 有些复杂以避免 concurrent modification.
            Iterator<Stream> parentsChildren = parent.getChildStreams().iterator();
            while (parentsChildren.hasNext()) {
                Stream parentsChild = parentsChildren.next();
                parentsChildren.remove();
                this.addChild(parentsChild);
            }
        }
        detachFromParent();
        parent.addChild(this);
        this.weight = weight;
    }


    /*
     * 从树删除关闭的流时和已知不需要检查循环引用时, 使用.
     */
    final void rePrioritise(AbstractStream parent, int weight) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reprioritisation.debug",
                    getConnectionId(), getIdentifier(), Boolean.FALSE,
                    parent.getIdentifier(), Integer.toString(weight)));
        }

        parent.addChild(this);
        this.weight = weight;
    }


    void receiveReset(long errorCode) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reset.debug", getConnectionId(), getIdentifier(),
                    Long.toString(errorCode)));
        }
        // 首先设置新的状态，因为读和写都检查这个
        state.receivedReset();
        // 内部读取等待, 因此需要调用一个方法打断 wait()
        if (inputBuffer != null) {
            inputBuffer.receiveReset();
        }
        // 在Stream上写入等待, 因此可以直接通知
        synchronized (this) {
            this.notifyAll();
        }
    }


    void checkState(FrameType frameType) throws Http2Exception {
        state.checkFrameType(frameType);
    }


    @Override
    protected synchronized void incrementWindowSize(int windowSizeIncrement) throws Http2Exception {
        // 如果这是零，那么任何试图写入这个流的线程将一直等待. 通知线程可以继续.
        // 使用notify all, 即使只有一个线程等待.
        boolean notify = getWindowSize() < 1;
        super.incrementWindowSize(windowSizeIncrement);
        if (notify && getWindowSize() > 0) {
            notifyAll();
        }
    }


    private synchronized int reserveWindowSize(int reservation, boolean block) throws IOException {
        long windowSize = getWindowSize();
        while (windowSize < 1) {
            if (!canWrite()) {
                throw new CloseNowException(sm.getString("stream.notWritable",
                        getConnectionId(), getIdentifier()));
            }
            try {
                if (block) {
                    wait();
                } else {
                    return 0;
                }
            } catch (InterruptedException e) {
                // 可能 shutdown / rst 或类似. 使用一个 IOException 给客户端发信号, 对于这个Stream 无法再进行  I/O .
                throw new IOException(e);
            }
            windowSize = getWindowSize();
        }
        int allocation;
        if (windowSize < reservation) {
            allocation = (int) windowSize;
        } else {
            allocation = reservation;
        }
        decrementWindowSize(allocation);
        return allocation;
    }


    @Override
    protected synchronized void doNotifyAll() {
        if (coyoteResponse.getWriteListener() == null) {
            // 阻塞IO, 线程将等待. 释放它.
            // 使用 notifyAll() 更安全(应该没有必要)
            this.notifyAll();
        } else {
            if (outputBuffer.isRegisteredForWrite()) {
                coyoteResponse.action(ActionCode.DISPATCH_WRITE, null);
            }
        }
    }


    @Override
    public final void emitHeader(String name, String value) throws HpackException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.header.debug", getConnectionId(), getIdentifier(),
                    name, value));
        }

        // Header 名称必须小写
        if (!name.toLowerCase(Locale.US).equals(name)) {
            throw new HpackException(sm.getString("stream.header.case",
                    getConnectionId(), getIdentifier(), name));
        }

        if ("connection".equals(name)) {
            throw new HpackException(sm.getString("stream.header.connection",
                    getConnectionId(), getIdentifier()));
        }

        if ("te".equals(name)) {
            if (!"trailers".equals(value)) {
                throw new HpackException(sm.getString("stream.header.te",
                        getConnectionId(), getIdentifier(), value));
            }
        }

        if (headerException != null) {
            // 不要打扰处理 header, 因为流无论如何都会被重置
            return;
        }

        boolean pseudoHeader = name.charAt(0) == ':';

        if (pseudoHeader && headerState != HEADER_STATE_PSEUDO) {
            headerException = new StreamException(sm.getString(
                    "stream.header.unexpectedPseudoHeader", getConnectionId(), getIdentifier(),
                    name), Http2Error.PROTOCOL_ERROR, getIdentifier().intValue());
            // 无需进一步处理. 流将被重置.
            return;
        }

        if (headerState == HEADER_STATE_PSEUDO && !pseudoHeader) {
            headerState = HEADER_STATE_REGULAR;
        }

        switch(name) {
        case ":method": {
            if (coyoteRequest.method().isNull()) {
                coyoteRequest.method().setString(value);
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":method" ));
            }
            break;
        }
        case ":scheme": {
            if (coyoteRequest.scheme().isNull()) {
                coyoteRequest.scheme().setString(value);
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":scheme" ));
            }
            break;
        }
        case ":path": {
            if (!coyoteRequest.requestURI().isNull()) {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":path" ));
            }
            if (value.length() == 0) {
                throw new HpackException(sm.getString("stream.header.noPath",
                        getConnectionId(), getIdentifier()));
            }
            int queryStart = value.indexOf('?');
            String uri;
            if (queryStart == -1) {
                uri = value;
            } else {
                uri = value.substring(0, queryStart);
                String query = value.substring(queryStart + 1);
                coyoteRequest.queryString().setString(query);
            }
            // Bug 61120. 设置 URI 为字节而不是字符串, 因此:
            // - 任何路径参数被正确处理
            // - 执行规范化安全检查，防止目录遍历攻击
            byte[] uriBytes = uri.getBytes(StandardCharsets.ISO_8859_1);
            coyoteRequest.requestURI().setBytes(uriBytes, 0, uriBytes.length);
            break;
        }
        case ":authority": {
            if (coyoteRequest.serverName().isNull()) {
                int i = value.lastIndexOf(':');
                if (i > -1) {
                    coyoteRequest.serverName().setString(value.substring(0, i));
                    coyoteRequest.setServerPort(Integer.parseInt(value.substring(i + 1)));
                } else {
                    coyoteRequest.serverName().setString(value);
                }
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":authority" ));
            }
            break;
        }
        case "cookie": {
            // Cookie header需要被连接成单个 header
            // See RFC 7540 8.1.2.5
            if (cookieHeader == null) {
                cookieHeader = new StringBuilder();
            } else {
                cookieHeader.append("; ");
            }
            cookieHeader.append(value);
            break;
        }
        default: {
            if (headerState == HEADER_STATE_TRAILER && !handler.isTrailerHeaderAllowed(name)) {
                break;
            }
            if ("expect".equals(name) && "100-continue".equals(value)) {
                coyoteRequest.setExpectation(true);
            }
            if (pseudoHeader) {
                headerException = new StreamException(sm.getString(
                        "stream.header.unknownPseudoHeader", getConnectionId(), getIdentifier(),
                        name), Http2Error.PROTOCOL_ERROR, getIdentifier().intValue());
            }
            // 假设其他 HTTP header
            coyoteRequest.getMimeHeaders().addValue(name).setString(value);
        }
        }
    }


    @Override
    public void setHeaderException(StreamException streamException) {
        if (headerException == null) {
            headerException = streamException;
        }
    }


    @Override
    public void validateHeaders() throws StreamException {
        if (headerException == null) {
            return;
        }

        throw headerException;
    }


    final boolean receivedEndOfHeaders() throws ConnectionException {
        if (coyoteRequest.method().isNull() || coyoteRequest.scheme().isNull() ||
                coyoteRequest.requestURI().isNull()) {
            throw new ConnectionException(sm.getString("stream.header.required",
                    getConnectionId(), getIdentifier()), Http2Error.PROTOCOL_ERROR);
        }
        // Cookie header需要被连接成单个 header
        // See RFC 7540 8.1.2.5
        // 只有在完全接收到 header后这样做
        if (cookieHeader != null) {
            coyoteRequest.getMimeHeaders().addValue("cookie").setString(cookieHeader.toString());
        }
        return headerState == HEADER_STATE_REGULAR || headerState == HEADER_STATE_PSEUDO;
    }


    void writeHeaders() throws IOException {
        // TODO: Is 1k the optimal value?
        handler.writeHeaders(this, coyoteResponse, 1024);
    }

    void writeAck() throws IOException {
        // TODO: Is 64 too big? Just the status header with compression
        handler.writeHeaders(this, ACK_RESPONSE, 64);
    }



    void flushData() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.write", getConnectionId(), getIdentifier()));
        }
        outputBuffer.flush(true);
    }


    @Override
    protected final String getConnectionId() {
        return handler.getConnectionId();
    }


    @Override
    protected int getWeight() {
        return weight;
    }


    Request getCoyoteRequest() {
        return coyoteRequest;
    }


    Response getCoyoteResponse() {
        return coyoteResponse;
    }


    ByteBuffer getInputByteBuffer() {
        return inputBuffer.getInBuffer();
    }


    final void receivedStartOfHeaders(boolean headersEndStream) throws Http2Exception {
        if (headerState == HEADER_STATE_START) {
            headerState = HEADER_STATE_PSEUDO;
            handler.getHpackDecoder().setMaxHeaderCount(handler.getMaxHeaderCount());
            handler.getHpackDecoder().setMaxHeaderSize(handler.getMaxHeaderSize());
        } else if (headerState == HEADER_STATE_PSEUDO || headerState == HEADER_STATE_REGULAR) {
            // Trailer header 必须包括流标志的结尾
            if (headersEndStream) {
                headerState = HEADER_STATE_TRAILER;
                handler.getHpackDecoder().setMaxHeaderCount(handler.getMaxTrailerCount());
                handler.getHpackDecoder().setMaxHeaderSize(handler.getMaxTrailerSize());
            } else {
                throw new ConnectionException(sm.getString("stream.trailerHeader.noEndOfStream",
                        getConnectionId(), getIdentifier()), Http2Error.PROTOCOL_ERROR);
            }
        }
        // 解析器将捕获在流关闭后发送 header 帧的尝试.
        state.receivedStartOfHeaders();
    }


    final void receivedData(int payloadSize) throws ConnectionException {
        contentLengthReceived += payloadSize;
        long contentLengthHeader = coyoteRequest.getContentLengthLong();
        if (contentLengthHeader > -1 && contentLengthReceived > contentLengthHeader) {
            throw new ConnectionException(sm.getString("stream.header.contentLength",
                    getConnectionId(), getIdentifier(), Long.valueOf(contentLengthHeader),
                    Long.valueOf(contentLengthReceived)), Http2Error.PROTOCOL_ERROR);
        }
    }


    final void receivedEndOfStream() throws ConnectionException {
        long contentLengthHeader = coyoteRequest.getContentLengthLong();
        if (contentLengthHeader > -1 && contentLengthReceived != contentLengthHeader) {
            throw new ConnectionException(sm.getString("stream.header.contentLength",
                    getConnectionId(), getIdentifier(), Long.valueOf(contentLengthHeader),
                    Long.valueOf(contentLengthReceived)), Http2Error.PROTOCOL_ERROR);
        }
        state.receivedEndOfStream();
        if (inputBuffer != null) {
            inputBuffer.notifyEof();
        }
    }


    void sentEndOfStream() {
        outputBuffer.endOfStreamSent = true;
        state.sentEndOfStream();
    }


    StreamInputBuffer getInputBuffer() {
        return inputBuffer;
    }


    StreamOutputBuffer getOutputBuffer() {
        return outputBuffer;
    }


    void sentPushPromise() {
        state.sentPushPromise();
    }


    boolean isActive() {
        return state.isActive();
    }


    boolean canWrite() {
        return state.canWrite();
    }


    boolean isClosedFinal() {
        return state.isClosedFinal();
    }


    void closeIfIdle() {
        state.closeIfIdle();
    }


    boolean isInputFinished() {
        return !state.isFrameTypePermitted(FrameType.DATA);
    }


    void close(Http2Exception http2Exception) {
        if (http2Exception instanceof StreamException) {
            try {
                StreamException se = (StreamException) http2Exception;
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("stream.reset.send", getConnectionId(), getIdentifier(),
                            se.getError()));
                }
                state.sendReset();
                handler.sendStreamReset(se);
            } catch (IOException ioe) {
                ConnectionException ce = new ConnectionException(
                        sm.getString("stream.reset.fail"), Http2Error.PROTOCOL_ERROR);
                ce.initCause(ioe);
                handler.closeConnection(ce);
            }
        } else {
            handler.closeConnection(http2Exception);
        }
    }


    boolean isPushSupported() {
        return handler.getRemoteSettings().getEnablePush();
    }


    final void push(Request request) throws IOException {
        if (!isPushSupported()) {
            return;
        }
        // 设置特殊的 HTTP/2 header
        request.getMimeHeaders().addValue(":method").duplicate(request.method());
        request.getMimeHeaders().addValue(":scheme").duplicate(request.scheme());
        StringBuilder path = new StringBuilder(request.requestURI().toString());
        if (!request.queryString().isNull()) {
            path.append('?');
            path.append(request.queryString().toString());
        }
        request.getMimeHeaders().addValue(":path").setString(path.toString());

        // 只有在使用非标准端口时，权限才需要包含端口.
        if (!(request.scheme().equals("http") && request.getServerPort() == 80) &&
                !(request.scheme().equals("https") && request.getServerPort() == 443)) {
            request.getMimeHeaders().addValue(":authority").setString(
                    request.serverName().getString() + ":" + request.getServerPort());
        } else {
            request.getMimeHeaders().addValue(":authority").duplicate(request.serverName());
        }

        push(handler, request, this);
    }


    private static void push(final Http2UpgradeHandler handler, final Request request, final Stream stream)
            throws IOException {
        if (org.apache.coyote.Constants.IS_SECURITY_ENABLED) {
            try {
                AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Void>() {
                            @Override
                            public Void run() throws IOException {
                                handler.push(request, stream);
                                return null;
                            }
                        });
            } catch (PrivilegedActionException ex) {
                Exception e = ex.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException(ex);
                }
            }

        } else {
            handler.push(request, stream);
        }
    }

    class StreamOutputBuffer implements OutputBuffer {

        private final ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
        private volatile long written = 0;
        private volatile boolean closed = false;
        private volatile boolean endOfStreamSent = false;
        private volatile boolean writeInterest = false;

        /* 写方法进行同步, 以确保一次只有一个线程能够访问缓冲区. 没有这种保护, 一个客户端进行并发写操作可能损坏缓冲.
         */

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doWrite(ByteBuffer)}
         */
        @Deprecated
        @Override
        public synchronized int doWrite(ByteChunk chunk) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("stream.closed", getConnectionId(), getIdentifier()));
            }
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.sendHeaders();
            }
            int len = chunk.getLength();
            int offset = 0;
            while (len > 0) {
                int thisTime = Math.min(buffer.remaining(), len);
                buffer.put(chunk.getBytes(), chunk.getOffset() + offset, thisTime);
                offset += thisTime;
                len -= thisTime;
                if (len > 0 && !buffer.hasRemaining()) {
                    // 只有当有更多数据写入, 而且缓冲区满时刷新
                    if (flush(true, coyoteResponse.getWriteListener() == null)) {
                        break;
                    }
                }
            }
            written += offset;
            return offset;
        }

        @Override
        public synchronized int doWrite(ByteBuffer chunk) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("stream.closed", getConnectionId(), getIdentifier()));
            }
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.sendHeaders();
            }
            int chunkLimit = chunk.limit();
            int offset = 0;
            while (chunk.remaining() > 0) {
                int thisTime = Math.min(buffer.remaining(), chunk.remaining());
                chunk.limit(chunk.position() + thisTime);
                buffer.put(chunk);
                chunk.limit(chunkLimit);
                offset += thisTime;
                if (chunk.remaining() > 0 && !buffer.hasRemaining()) {
                    // 只有当有更多数据写入, 而且缓冲区满时刷新
                    if (flush(true, coyoteResponse.getWriteListener() == null)) {
                        break;
                    }
                }
            }
            written += offset;
            return offset;
        }

        public synchronized boolean flush(boolean block) throws IOException {
            return flush(false, block);
        }

        private synchronized boolean flush(boolean writeInProgress, boolean block)
                throws IOException {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("stream.outputBuffer.flush.debug", getConnectionId(),
                        getIdentifier(), Integer.toString(buffer.position()),
                        Boolean.toString(writeInProgress), Boolean.toString(closed)));
            }
            if (buffer.position() == 0) {
                if (closed && !endOfStreamSent) {
                    // 处理这种特殊的情况，比试图修改以下代码来处理更简单.
                    handler.writeBody(Stream.this, buffer, 0, true);
                }
                // 缓冲区为空. Nothing to do.
                return false;
            }
            buffer.flip();
            int left = buffer.remaining();
            while (left > 0) {
                int streamReservation  = reserveWindowSize(left, block);
                if (streamReservation == 0) {
                    // 必须是非阻塞
                    buffer.compact();
                    return true;
                }
                while (streamReservation > 0) {
                    int connectionReservation =
                                handler.reserveWindowSize(Stream.this, streamReservation);
                    // Do the write
                    handler.writeBody(Stream.this, buffer, connectionReservation,
                            !writeInProgress && closed && left == connectionReservation);
                    streamReservation -= connectionReservation;
                    left -= connectionReservation;
                }
            }
            buffer.clear();
            return false;
        }

        synchronized boolean isReady() {
            if (getWindowSize() > 0 && handler.getWindowSize() > 0) {
                return true;
            } else {
                writeInterest = true;
                return false;
            }
        }

        synchronized boolean isRegisteredForWrite() {
            if (writeInterest) {
                writeInterest = false;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public long getBytesWritten() {
            return written;
        }

        public void close() throws IOException {
            closed = true;
            flushData();
        }

        public boolean isClosed() {
            return closed;
        }

        /**
         * @return <code>true</code> 如果确定相关联的响应没有主体.
         */
        public boolean hasNoBody() {
            return ((written == 0) && closed);
        }
    }


    class StreamInputBuffer implements InputBuffer {

        /* 两个缓冲区以避免多线程问题.
         * 这些问题源于Stream (或 Request/Response)的事实, 用于应用在一个线程中处理, 但是连接在另一个线程中处理.
         * 因此，在应用程序准备读取之前，可以接收请求主体帧. 如果它没有被缓冲, 连接(和所有的流)的处理将被阻塞, 直到应用读取数据. 因此，传入的数据必须被缓冲.
         * 如果只使用一个缓冲区, 那么它可能会损坏, 如果连接线程在应用读取它的同时写入. 同时应尽可能小心使用缓冲区避免错误, 它仍然需要使用相同的备份, 即使用两个缓冲区, 而且行为将不太清除.
         *
         * 缓冲区延迟创建, 因为这将很快消耗大量内存, 而且大多数请求没有主体.
         */
        // 这个缓冲区用于填充读取方法中传入的 ByteChunk
        private byte[] outBuffer;
        // 这个缓冲区是传入的数据的目的地. 通常是'write mode'.
        private volatile ByteBuffer inBuffer;
        private volatile boolean readInterest;
        private boolean reset = false;

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doRead(ApplicationBufferHandler)}
         */
        @Deprecated
        @Override
        public int doRead(ByteChunk chunk) throws IOException {

            ensureBuffersExist();

            int written = -1;

            // 确保同一时间只有一个线程访问 inBuffer
            synchronized (inBuffer) {
                boolean canRead = false;
                while (inBuffer.position() == 0 && (canRead = isActive() && !isInputFinished())) {
                    // 需要阻塞, 直到写入数据
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("stream.inputBuffer.empty"));
                        }
                        inBuffer.wait();
                        if (reset) {
                            // TODO: i18n
                            throw new IOException("HTTP/2 Stream reset");
                        }
                    } catch (InterruptedException e) {
                        // 可能是 shutdown / rst 或类似的.
                        // 使用一个IOException 给客户端发信号, 无法对这个Stream进行更进一步的 I/O.
                        throw new IOException(e);
                    }
                }

                if (inBuffer.position() > 0) {
                    // inBuffer中的数据是可用的. 复制它到 outBuffer.
                    inBuffer.flip();
                    written = inBuffer.remaining();
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("stream.inputBuffer.copy",
                                Integer.toString(written)));
                    }
                    inBuffer.get(outBuffer, 0, written);
                    inBuffer.clear();
                } else if (!canRead) {
                    return -1;
                } else {
                    // Should never happen
                    throw new IllegalStateException();
                }
            }

            chunk.setBytes(outBuffer, 0,  written);

            // 增加客户端流量, 通过读取的字节数控制Windows
            handler.writeWindowUpdate(Stream.this, written, true);

            return written;
        }

        @Override
        public int doRead(ApplicationBufferHandler applicationBufferHandler) throws IOException {

            ensureBuffersExist();

            int written = -1;

            // 确保同一时间只有一个线程访问 inBuffer
            synchronized (inBuffer) {
                boolean canRead = false;
                while (inBuffer.position() == 0 && (canRead = isActive() && !isInputFinished())) {
                    // 需要阻塞, 直到写入数据
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("stream.inputBuffer.empty"));
                        }
                        inBuffer.wait();
                        if (reset) {
                            // TODO: i18n
                            throw new IOException("HTTP/2 Stream reset");
                        }
                    } catch (InterruptedException e) {
                        // 可能是 shutdown / rst 或类似的.
                        // 使用一个IOException 给客户端发信号, 无法对这个Stream进行更进一步的 I/O.
                        throw new IOException(e);
                    }
                }

                if (inBuffer.position() > 0) {
                    // inBuffer中的数据是可用的. 复制它到 outBuffer.
                    inBuffer.flip();
                    written = inBuffer.remaining();
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("stream.inputBuffer.copy",
                                Integer.toString(written)));
                    }
                    inBuffer.get(outBuffer, 0, written);
                    inBuffer.clear();
                } else if (!canRead) {
                    return -1;
                } else {
                    // Should never happen
                    throw new IllegalStateException();
                }
            }

            applicationBufferHandler.setByteBuffer(ByteBuffer.wrap(outBuffer, 0,  written));

            // 增加客户端流量, 通过读取的字节数控制Windows
            handler.writeWindowUpdate(Stream.this, written, true);

            return written;
        }


        void registerReadInterest() {
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    readInterest = true;
                }
            }
        }


        synchronized boolean isRequestBodyFullyRead() {
            return (inBuffer == null || inBuffer.position() == 0) && isInputFinished();
        }


        synchronized int available() {
            if (inBuffer == null) {
                return 0;
            }
            return inBuffer.position();
        }


        /*
         * 在inBuffer中放入一些数据后调用.
         */
        synchronized boolean onDataAvailable() {
            if (readInterest) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("stream.inputBuffer.dispatch"));
                }
                readInterest = false;
                coyoteRequest.action(ActionCode.DISPATCH_READ, null);
                // 因为这个线程正在处理传入的属于它自己的连接和流，所以总是需要分派
                coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
                return true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("stream.inputBuffer.signal"));
                }
                synchronized (inBuffer) {
                    inBuffer.notifyAll();
                }
                return false;
            }
        }


        public ByteBuffer getInBuffer() {
            ensureBuffersExist();
            return inBuffer;
        }


        protected synchronized void insertReplayedBody(ByteChunk body) {
            inBuffer = ByteBuffer.wrap(body.getBytes(),  body.getOffset(),  body.getLength());
        }


        private void ensureBuffersExist() {
            if (inBuffer == null) {
                // 客户端在发送时必须遵守Tomcat的窗口大小, 因此这是客户端使用的Tomcat设置的初始的窗口大小 (即这里需要本地设置).
                int size = handler.getLocalSettings().getInitialWindowSize();
                synchronized (this) {
                    if (inBuffer == null) {
                        inBuffer = ByteBuffer.allocate(size);
                        outBuffer = new byte[size];
                    }
                }
            }
        }


        protected void receiveReset() {
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    reset = true;
                    inBuffer.notifyAll();
                }
            }
        }

        private final void notifyEof() {
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    inBuffer.notifyAll();
                }
            }
        }
    }
}
