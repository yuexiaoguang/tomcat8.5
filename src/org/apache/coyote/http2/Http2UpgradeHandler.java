package org.apache.coyote.http2;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.WebConnection;

import org.apache.coyote.Adapter;
import org.apache.coyote.CloseNowException;
import org.apache.coyote.ProtocolException;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.coyote.http2.HpackEncoder.State;
import org.apache.coyote.http2.Http2Parser.Input;
import org.apache.coyote.http2.Http2Parser.Output;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * 表示一个从客户端到Tomcat HTTP/2得连接. 它基于同一时间只有一个线程处理 I/O.
 * <br>
 * 对于读取, 这个实现可能阻塞, 也可能非阻塞.
 * <br>
 * Note:
 * <ul>
 * <li>需要在server.xml中启用的TLS Connector中嵌套一个 &lt;UpgradeProtocol
 *     className="org.apache.coyote.http2.Http2Protocol" /&gt; 元素, 来启用 HTTP/2 支持.
 *     </li>
 * </ul>
 */
public class Http2UpgradeHandler extends AbstractStream implements InternalHttpUpgradeHandler,
        Input, Output {

    private static final Log log = LogFactory.getLog(Http2UpgradeHandler.class);
    private static final StringManager sm = StringManager.getManager(Http2UpgradeHandler.class);

    private static final AtomicInteger connectionIdGenerator = new AtomicInteger(0);
    private static final Integer STREAM_ID_ZERO = Integer.valueOf(0);

    private static final int FLAG_END_OF_STREAM = 1;
    private static final int FLAG_END_OF_HEADERS = 4;

    private static final byte[] PING = { 0x00, 0x00, 0x08, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] PING_ACK = { 0x00, 0x00, 0x08, 0x06, 0x01, 0x00, 0x00, 0x00, 0x00 };

    private static final byte[] SETTINGS_ACK = { 0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00 };

    private static final byte[] GOAWAY = { 0x07, 0x00, 0x00, 0x00, 0x00, 0x00 };

    private static final String HTTP2_SETTINGS_HEADER = "HTTP2-Settings";

    private static final HeaderSink HEADER_SINK = new HeaderSink();

    private final String connectionId;

    private final Adapter adapter;
    private volatile SocketWrapperBase<?> socketWrapper;
    private volatile SSLSupport sslSupport;

    private volatile Http2Parser parser;

    // 简单的状态机 (状态序列)
    private AtomicReference<ConnectionState> connectionState = new AtomicReference<>(ConnectionState.NEW);
    private volatile long pausedNanoTime = Long.MAX_VALUE;

    /**
     * 远程设置是客户端定义的并发送到Tomcat的设置，在与客户端通信时，Tomcat必须使用该设置.
     */
    private final ConnectionSettingsRemote remoteSettings;
    /**
     * 本地设置是由Tomcat定义的设置，并发送给客户端，客户端在与Tomcat通信时必须使用该设置.
     */
    private final ConnectionSettingsLocal localSettings;

    private HpackDecoder hpackDecoder;
    private HpackEncoder hpackEncoder;

    // 所有的超时时间, 毫秒
    private long readTimeout = Http2Protocol.DEFAULT_READ_TIMEOUT;
    private long keepAliveTimeout = Http2Protocol.DEFAULT_KEEP_ALIVE_TIMEOUT;
    private long writeTimeout = Http2Protocol.DEFAULT_WRITE_TIMEOUT;

    private final Map<Integer,Stream> streams = new ConcurrentHashMap<>();
    private final AtomicInteger activeRemoteStreamCount = new AtomicInteger(0);
    // 从 -1 开始, 因此在closeIdleStreams()中使用 'add 2'逻辑
    private volatile int maxActiveRemoteStreamId = -1;
    private volatile int maxProcessedStreamId;
    private final AtomicInteger nextLocalStreamId = new AtomicInteger(2);
    private final PingManager pingManager = new PingManager();
    private volatile int newStreamsSinceLastPrune = 0;
    // 连接阻塞时跟踪 (windowSize < 1)
    private final ConcurrentMap<AbstractStream,int[]> backLogStreams = new ConcurrentHashMap<>();
    private long backLogSize = 0;

    // Stream 并发控制
    private int maxConcurrentStreamExecution = Http2Protocol.DEFAULT_MAX_CONCURRENT_STREAM_EXECUTION;
    private AtomicInteger streamConcurrency = null;
    private Queue<StreamRunnable> queuedRunnable = null;

    // Limits
    private Set<String> allowedTrailerHeaders = Collections.emptySet();
    private int maxHeaderCount = Constants.DEFAULT_MAX_HEADER_COUNT;
    private int maxHeaderSize = Constants.DEFAULT_MAX_HEADER_SIZE;
    private int maxTrailerCount = Constants.DEFAULT_MAX_TRAILER_COUNT;
    private int maxTrailerSize = Constants.DEFAULT_MAX_TRAILER_SIZE;


    public Http2UpgradeHandler(Adapter adapter, Request coyoteRequest) {
        super (STREAM_ID_ZERO);
        this.adapter = adapter;
        this.connectionId = Integer.toString(connectionIdGenerator.getAndIncrement());

        remoteSettings = new ConnectionSettingsRemote(connectionId);
        localSettings = new ConnectionSettingsLocal(connectionId);

        // 初始HTTP请求变为流1.
        if (coyoteRequest != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.upgrade", connectionId));
            }
            Integer key = Integer.valueOf(1);
            Stream stream = new Stream(key, this, coyoteRequest);
            streams.put(key, stream);
            maxActiveRemoteStreamId = 1;
            activeRemoteStreamCount.set(1);
            maxProcessedStreamId = 1;
        }
    }


    @Override
    public void init(WebConnection webConnection) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.init", connectionId, connectionState.get()));
        }

        if (!connectionState.compareAndSet(ConnectionState.NEW, ConnectionState.CONNECTED)) {
            return;
        }

        // 初始化并发控制
        if (maxConcurrentStreamExecution < localSettings.getMaxConcurrentStreams()) {
            streamConcurrency = new AtomicInteger(0);
            queuedRunnable = new ConcurrentLinkedQueue<>();
        }

        parser = new Http2Parser(connectionId, this, this);

        Stream stream = null;

        socketWrapper.setReadTimeout(getReadTimeout());
        socketWrapper.setWriteTimeout(getWriteTimeout());

        if (webConnection != null) {
            // HTTP/2 通过 HTTP 升级启动.
            // 初始化的 HTTP/1.1 请求可用, 作为Stream 1.

            try {
                // 处理初始化的设置帧
                stream = getStream(1, true);
                String base64Settings = stream.getCoyoteRequest().getHeader(HTTP2_SETTINGS_HEADER);
                byte[] settings = Base64.decodeBase64(base64Settings);

                // 只有在流 0 上设置可用
                FrameType.SETTINGS.check(0, settings.length);

                for (int i = 0; i < settings.length % 6; i++) {
                    int id = ByteUtil.getTwoBytes(settings, i * 6);
                    long value = ByteUtil.getFourBytes(settings, (i * 6) + 2);
                    remoteSettings.set(Setting.valueOf(id), value);
                }
            } catch (Http2Exception e) {
                throw new ProtocolException(
                        sm.getString("upgradeHandler.upgrade.fail", connectionId));
            }
        }

        // 发送初始化的设置帧
        try {
            byte[] settings = localSettings.getSettingsFrameForPending();
            socketWrapper.write(true, settings, 0, settings.length);
            socketWrapper.flush(true);
        } catch (IOException ioe) {
            String msg = sm.getString("upgradeHandler.sendPrefaceFail", connectionId);
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
            throw new ProtocolException(msg, ioe);
        }

        // 确保客户端发送了一个有效的连接部分, 在通过HTTP/2向原始请求发送响应之前.
        try {
            parser.readConnectionPreface();
        } catch (Http2Exception e) {
            String msg = sm.getString("upgradeHandler.invalidPreface", connectionId);
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
            throw new ProtocolException(msg);
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.prefaceReceived", connectionId));
        }

        // 发送一个 ping, 得到的往返时间尽可能的早
        try {
            pingManager.sendPing(true);
        } catch (IOException ioe) {
            throw new ProtocolException(sm.getString("upgradeHandler.pingFailed"), ioe);
        }

        if (webConnection != null) {
            processStreamOnContainerThread(stream);
        }
    }


    private void processStreamOnContainerThread(Stream stream) {
        StreamProcessor streamProcessor = new StreamProcessor(this, stream, adapter, socketWrapper);
        streamProcessor.setSslSupport(sslSupport);
        processStreamOnContainerThread(streamProcessor, SocketEvent.OPEN_READ);
    }


    void processStreamOnContainerThread(StreamProcessor streamProcessor, SocketEvent event) {
        StreamRunnable streamRunnable = new StreamRunnable(streamProcessor, event);
        if (streamConcurrency == null) {
            socketWrapper.getEndpoint().getExecutor().execute(streamRunnable);
        } else {
            if (getStreamConcurrency() < maxConcurrentStreamExecution) {
                increaseStreamConcurrency();
                socketWrapper.getEndpoint().getExecutor().execute(streamRunnable);
            } else {
                queuedRunnable.offer(streamRunnable);
            }
        }
    }


    @Override
    public void setSocketWrapper(SocketWrapperBase<?> wrapper) {
        this.socketWrapper = wrapper;
    }


    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    @Override
    public SocketState upgradeDispatch(SocketEvent status) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.upgradeDispatch.entry", connectionId, status));
        }

        // 不使用 WebConnection, 在这里传递 null 是可以的
        // 可能不是必要的. init() 将处理这个.
        init(null);


        SocketState result = SocketState.CLOSED;

        try {
            pingManager.sendPing(false);

            checkPauseState();

            switch(status) {
            case OPEN_READ:
                try {
                    // 有数据可读, 因此，在读取帧时使用读取超时.
                   socketWrapper.setReadTimeout(getReadTimeout());
                    while (true) {
                        try {
                            if (!parser.readFrame(false)) {
                                break;
                            }
                        } catch (StreamException se) {
                            // Stream 错误对于连接来说不是致命的, 因此继续读取帧
                            Stream stream = getStream(se.getStreamId(), false);
                            if (stream == null) {
                                sendStreamReset(se);
                            } else {
                                stream.close(se);
                            }
                        }
                    }
                    // 没有更多的帧来读取, 因此转换到 keep-alive 超时.
                    socketWrapper.setReadTimeout(getKeepAliveTimeout());
                } catch (Http2Exception ce) {
                    // Really ConnectionException
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("upgradeHandler.connectionError"), ce);
                    }
                    closeConnection(ce);
                    break;
                }

                if (connectionState.get() != ConnectionState.CLOSED) {
                    result = SocketState.UPGRADED;
                }
                break;

            case OPEN_WRITE:
                processWrites();

                result = SocketState.UPGRADED;
                break;

            case DISCONNECT:
            case ERROR:
            case TIMEOUT:
            case STOP:
                close();
                break;
            }
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.ioerror", connectionId), ioe);
            }
            close();
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.upgradeDispatch.exit", connectionId, result));
        }
        return result;
    }


    ConnectionSettingsRemote getRemoteSettings() {
        return remoteSettings;
    }


    ConnectionSettingsLocal getLocalSettings() {
        return localSettings;
    }


    @Override
    public void pause() {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.pause.entry", connectionId));
        }

        if (connectionState.compareAndSet(ConnectionState.CONNECTED, ConnectionState.PAUSING)) {
            pausedNanoTime = System.nanoTime();

            try {
                writeGoAwayFrame((1 << 31) - 1, Http2Error.NO_ERROR.getCode(), null);
            } catch (IOException ioe) {
                // 这对于连接来说是致命的. 忽略它. 在upgradeDispatch()中还会有更多的 I/O 尝试, 它可以更好地处理IO错误.
            }
        }
    }


    @Override
    public void destroy() {
        // NO-OP
    }


    private void checkPauseState() throws IOException {
        if (connectionState.get() == ConnectionState.PAUSING) {
            if (pausedNanoTime + pingManager.getRoundTripTimeNano() < System.nanoTime()) {
                connectionState.compareAndSet(ConnectionState.PAUSING, ConnectionState.PAUSED);
                writeGoAwayFrame(maxProcessedStreamId, Http2Error.NO_ERROR.getCode(), null);
            }
        }
    }


    private int increaseStreamConcurrency() {
        return streamConcurrency.incrementAndGet();
    }

    private int decreaseStreamConcurrency() {
        return streamConcurrency.decrementAndGet();
    }

    private int getStreamConcurrency() {
        return streamConcurrency.get();
    }

    void executeQueuedStream() {
        if (streamConcurrency == null) {
            return;
        }
        decreaseStreamConcurrency();
        if (getStreamConcurrency() < maxConcurrentStreamExecution) {
            StreamRunnable streamRunnable = queuedRunnable.poll();
            if (streamRunnable != null) {
                increaseStreamConcurrency();
                socketWrapper.getEndpoint().getExecutor().execute(streamRunnable);
            }
        }
    }


    void sendStreamReset(StreamException se) throws IOException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.rst.debug", connectionId,
                    Integer.toString(se.getStreamId()), se.getError(), se.getMessage()));
        }

        // Write a RST frame
        byte[] rstFrame = new byte[13];
        // Length
        ByteUtil.setThreeBytes(rstFrame, 0, 4);
        // Type
        rstFrame[3] = FrameType.RST.getIdByte();
        // No flags
        // Stream ID
        ByteUtil.set31Bits(rstFrame, 5, se.getStreamId());
        // Payload
        ByteUtil.setFourBytes(rstFrame, 9, se.getError().getCode());

        synchronized (socketWrapper) {
            socketWrapper.write(true, rstFrame, 0, rstFrame.length);
            socketWrapper.flush(true);
        }
    }


    void closeConnection(Http2Exception ce) {
        try {
            writeGoAwayFrame(maxProcessedStreamId, ce.getError().getCode(),
                    ce.getMessage().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioe) {
            // Ignore. 尽可能发送GOAWAY, 并且原始错误已经被记录下来.
        }
        close();
    }


    private void writeGoAwayFrame(int maxStreamId, long errorCode, byte[] debugMsg)
            throws IOException {
        byte[] fixedPayload = new byte[8];
        ByteUtil.set31Bits(fixedPayload, 0, maxStreamId);
        ByteUtil.setFourBytes(fixedPayload, 4, errorCode);
        int len = 8;
        if (debugMsg != null) {
            len += debugMsg.length;
        }
        byte[] payloadLength = new byte[3];
        ByteUtil.setThreeBytes(payloadLength, 0, len);

        synchronized (socketWrapper) {
            socketWrapper.write(true, payloadLength, 0, payloadLength.length);
            socketWrapper.write(true, GOAWAY, 0, GOAWAY.length);
            socketWrapper.write(true, fixedPayload, 0, 8);
            if (debugMsg != null) {
                socketWrapper.write(true, debugMsg, 0, debugMsg.length);
            }
            socketWrapper.flush(true);
        }
    }

    void writeHeaders(Stream stream, Response coyoteResponse, int payloadSize)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.writeHeaders", connectionId,
                    stream.getIdentifier()));
        }

        if (!stream.canWrite()) {
            return;
        }

        prepareHeaders(coyoteResponse);

        byte[] header = new byte[9];
        ByteBuffer target = ByteBuffer.allocate(payloadSize);
        boolean first = true;
        State state = null;
        // 确保 Stream 处理线程可以控制 socket.
        synchronized (socketWrapper) {
            while (state != State.COMPLETE) {
                state = getHpackEncoder().encode(coyoteResponse.getMimeHeaders(), target);
                target.flip();
                if (state == State.COMPLETE || target.limit() > 0) {
                    ByteUtil.setThreeBytes(header, 0, target.limit());
                    if (first) {
                        first = false;
                        header[3] = FrameType.HEADERS.getIdByte();
                        if (stream.getOutputBuffer().hasNoBody()) {
                            header[4] = FLAG_END_OF_STREAM;
                        }
                    } else {
                        header[3] = FrameType.CONTINUATION.getIdByte();
                    }
                    if (state == State.COMPLETE) {
                        header[4] += FLAG_END_OF_HEADERS;
                    }
                    if (log.isDebugEnabled()) {
                        log.debug(target.limit() + " bytes");
                    }
                    ByteUtil.set31Bits(header, 5, stream.getIdentifier().intValue());
                    try {
                        socketWrapper.write(true, header, 0, header.length);
                        socketWrapper.write(true, target);
                        socketWrapper.flush(true);
                    } catch (IOException ioe) {
                        handleAppInitiatedIOException(ioe);
                    }
                }
                if (state == State.UNDERFLOW && target.limit() == 0) {
                    target = ByteBuffer.allocate(target.capacity() * 2);
                } else {
                    target.clear();
                }
            }
        }
    }


    private void prepareHeaders(Response coyoteResponse) {
        MimeHeaders headers = coyoteResponse.getMimeHeaders();
        int statusCode = coyoteResponse.getStatus();

        // 为状态添加伪 header
        headers.addValue(":status").setString(Integer.toString(statusCode));

        // 检查响应主体是否存在
        if (!(statusCode < 200 || statusCode == 205 || statusCode == 304)) {
            String contentType = coyoteResponse.getContentType();
            if (contentType != null) {
                headers.setValue("content-type").setString(contentType);
            }
            String contentLanguage = coyoteResponse.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("content-language").setString(contentLanguage);
            }
        }


        // 添加 date header, 除非是信息响应或应用程序已经设置了
        if (statusCode >= 200 && headers.getValue("date") == null) {
            headers.addValue("date").setString(FastHttpDateFormat.getCurrentDate());
        }
    }


    void writePushHeaders(Stream stream, int pushedStreamId, Request coyoteRequest, int payloadSize)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.writePushHeaders", connectionId,
                    stream.getIdentifier(), Integer.toString(pushedStreamId)));
        }

        byte[] header = new byte[9];
        ByteBuffer target = ByteBuffer.allocate(payloadSize);
        boolean first = true;
        State state = null;
        byte[] pushedStreamIdBytes = new byte[4];
        ByteUtil.set31Bits(pushedStreamIdBytes, 0, pushedStreamId);
        // 确保 Stream 处理线程可以控制 socket.
        synchronized (socketWrapper) {
            target.put(pushedStreamIdBytes);
            while (state != State.COMPLETE) {
                state = getHpackEncoder().encode(coyoteRequest.getMimeHeaders(), target);
                target.flip();
                if (state == State.COMPLETE || target.limit() > 0) {
                    ByteUtil.setThreeBytes(header, 0, target.limit());
                    if (first) {
                        first = false;
                        header[3] = FrameType.PUSH_PROMISE.getIdByte();
                    } else {
                        header[3] = FrameType.CONTINUATION.getIdByte();
                    }
                    if (state == State.COMPLETE) {
                        header[4] += FLAG_END_OF_HEADERS;
                    }
                    if (log.isDebugEnabled()) {
                        log.debug(target.limit() + " bytes");
                    }
                    ByteUtil.set31Bits(header, 5, stream.getIdentifier().intValue());
                    try {
                        socketWrapper.write(true, header, 0, header.length);
                        socketWrapper.write(true, target);
                        socketWrapper.flush(true);
                    } catch (IOException ioe) {
                        handleAppInitiatedIOException(ioe);
                    }
                }
                if (state == State.UNDERFLOW && target.limit() == 0) {
                    target = ByteBuffer.allocate(target.capacity() * 2);
                } else {
                    target.clear();
                }
            }
        }
    }


    private HpackEncoder getHpackEncoder() {
        if (hpackEncoder == null) {
            hpackEncoder = new HpackEncoder();
        }
        // 确保使用最新的表大小
        hpackEncoder.setMaxTableSize(remoteSettings.getHeaderTableSize());
        return hpackEncoder;
    }


    void writeBody(Stream stream, ByteBuffer data, int len, boolean finished) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.writeBody", connectionId, stream.getIdentifier(),
                    Integer.toString(len)));
        }
        // 现在需要检查这个，因为发送流的结尾会改变这个.
        boolean writeable = stream.canWrite();
        byte[] header = new byte[9];
        ByteUtil.setThreeBytes(header, 0, len);
        header[3] = FrameType.DATA.getIdByte();
        if (finished) {
            header[4] = FLAG_END_OF_STREAM;
            stream.sentEndOfStream();
            if (!stream.isActive()) {
                activeRemoteStreamCount.decrementAndGet();
            }
        }
        if (writeable) {
            ByteUtil.set31Bits(header, 5, stream.getIdentifier().intValue());
            synchronized (socketWrapper) {
                try {
                    socketWrapper.write(true, header, 0, header.length);
                    int orgLimit = data.limit();
                    data.limit(data.position() + len);
                    socketWrapper.write(true, data);
                    data.limit(orgLimit);
                    socketWrapper.flush(true);
                } catch (IOException ioe) {
                    handleAppInitiatedIOException(ioe);
                }
            }
        }
    }


    /*
     * 处理一个 HTTP/2 连接下层的socket的 I/O 错误 (通常是读取请求或写入响应). 这样的I/O错误是致命的，所以关闭连接.
     * 异常重新抛出使客户端代码注意到问题.
     *
     * Note: 不能依靠这种异常到达socket 处理器, 因为应用代码可能忽略它.
     */
    private void handleAppInitiatedIOException(IOException ioe) throws IOException {
        close();
        throw ioe;
    }


    /*
     * 需要知道这是否是启动的应用程序，因为这影响了错误处理.
     */
    void writeWindowUpdate(Stream stream, int increment, boolean applicationInitiated)
            throws IOException {
        if (!stream.canWrite()) {
            return;
        }
        synchronized (socketWrapper) {
            // Build window update frame for stream 0
            byte[] frame = new byte[13];
            ByteUtil.setThreeBytes(frame, 0,  4);
            frame[3] = FrameType.WINDOW_UPDATE.getIdByte();
            ByteUtil.set31Bits(frame, 9, increment);
            socketWrapper.write(true, frame, 0, frame.length);
            // Change stream Id and re-use
            ByteUtil.set31Bits(frame, 5, stream.getIdentifier().intValue());
            try {
                socketWrapper.write(true, frame, 0, frame.length);
                socketWrapper.flush(true);
            } catch (IOException ioe) {
                if (applicationInitiated) {
                    handleAppInitiatedIOException(ioe);
                } else {
                    throw ioe;
                }
            }
        }
    }


    private void processWrites() throws IOException {
        synchronized (socketWrapper) {
            if (socketWrapper.flush(false)) {
                socketWrapper.registerWriteInterest();
                return;
            }
        }
    }


    int reserveWindowSize(Stream stream, int reservation) throws IOException {
        // 需要持有流的锁, 因此 releaseBacklog() 不能通知这个线程, 直到这个线程进入 wait()
        int allocation = 0;
        synchronized (stream) {
            do {
                synchronized (this) {
                    if (!stream.canWrite()) {
                        throw new CloseNowException(
                                sm.getString("upgradeHandler.stream.notWritable",
                                        stream.getConnectionId(), stream.getIdentifier()));
                    }
                    long windowSize = getWindowSize();
                    if (windowSize < 1 || backLogSize > 0) {
                        // 这个流被分配了吗
                        int[] value = backLogStreams.get(stream);
                        if (value == null) {
                            value = new int[] { reservation, 0 };
                            backLogStreams.put(stream, value);
                            backLogSize += reservation;
                            // Add the parents as well
                            AbstractStream parent = stream.getParentStream();
                            while (parent != null && backLogStreams.putIfAbsent(parent, new int[2]) == null) {
                                parent = parent.getParentStream();
                            }
                        } else {
                            if (value[1] > 0) {
                                allocation = value[1];
                                decrementWindowSize(allocation);
                                if (value[0] == 0) {
                                    // 已全部分配完毕, 因此这个流可以从  backlog 删除.
                                    backLogStreams.remove(stream);
                                } else {
                                    // 此分配已被使用. 将分配重置为零. 留下 backlog 中的流, 因为它还有很多字节要写入.
                                    value[1] = 0;
                                }
                            }
                        }
                    } else if (windowSize < reservation) {
                        allocation = (int) windowSize;
                        decrementWindowSize(allocation);
                    } else {
                        allocation = reservation;
                        decrementWindowSize(allocation);
                    }
                }
                if (allocation == 0) {
                    try {
                        stream.wait();
                    } catch (InterruptedException e) {
                        throw new IOException(sm.getString(
                                "upgradeHandler.windowSizeReservationInterrupted", connectionId,
                                stream.getIdentifier(), Integer.toString(reservation)), e);
                    }
                }
            } while (allocation == 0);
        }
        return allocation;
    }



    @SuppressWarnings("sync-override") // notifyAll() 需要外部同步来避免死锁
    @Override
    protected void incrementWindowSize(int increment) throws Http2Exception {
        Set<AbstractStream> streamsToNotify = null;

        synchronized (this) {
            long windowSize = getWindowSize();
            if (windowSize < 1 && windowSize + increment > 0) {
                streamsToNotify = releaseBackLog((int) (windowSize +increment));
            }
            super.incrementWindowSize(increment);
        }

        if (streamsToNotify != null) {
            for (AbstractStream stream : streamsToNotify) {
                synchronized (stream) {
                    stream.notifyAll();
                }
            }
        }
    }


    private synchronized Set<AbstractStream> releaseBackLog(int increment) {
        Set<AbstractStream> result = new HashSet<>();
        if (backLogSize < increment) {
            // 清空整个 backlog
            result.addAll(backLogStreams.keySet());
            backLogStreams.clear();
            backLogSize = 0;
        } else {
            int leftToAllocate = increment;
            while (leftToAllocate > 0) {
                leftToAllocate = allocate(this, leftToAllocate);
            }
            for (Entry<AbstractStream,int[]> entry : backLogStreams.entrySet()) {
                int allocation = entry.getValue()[1];
                if (allocation > 0) {
                    backLogSize -= allocation;
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }



    @Override
    protected synchronized void doNotifyAll() {
        this.notifyAll();
    }


    private int allocate(AbstractStream stream, int allocation) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.allocate.debug", getConnectionId(),
                    stream.getIdentifier(), Integer.toString(allocation)));
        }
        // 分配给指定的流
        int[] value = backLogStreams.get(stream);
        if (value[0] >= allocation) {
            value[0] -= allocation;
            value[1] += allocation;
            return 0;
        }

        // 有一些剩下的, 因此分配给流的子级.
        int leftToAllocate = allocation;
        value[1] = value[0];
        value[0] = 0;
        leftToAllocate -= value[1];

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.allocate.left",
                    getConnectionId(), stream.getIdentifier(), Integer.toString(leftToAllocate)));
        }

        // Recipients 是位于backlog中的当前流的子级.
        Set<AbstractStream> recipients = new HashSet<>();
        recipients.addAll(stream.getChildStreams());
        recipients.retainAll(backLogStreams.keySet());

        // 循环直到分配或接受者用完为止
        while (leftToAllocate > 0) {
            if (recipients.size() == 0) {
                backLogStreams.remove(stream);
                return leftToAllocate;
            }

            int totalWeight = 0;
            for (AbstractStream recipient : recipients) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("upgradeHandler.allocate.recipient",
                            getConnectionId(), stream.getIdentifier(), recipient.getIdentifier(),
                            Integer.toString(recipient.getWeight())));
                }
                totalWeight += recipient.getWeight();
            }

            // 使用 Iterator, 因此完全分配的 children/recipients 可以删除.
            Iterator<AbstractStream> iter = recipients.iterator();
            int allocated = 0;
            while (iter.hasNext()) {
                AbstractStream recipient = iter.next();
                int share = leftToAllocate * recipient.getWeight() / totalWeight;
                if (share == 0) {
                    // 这是为了避免触发无限循环的舍入问题. 这将导致非常轻微的过度分配，但HTTP/2应该应付这一点.
                    share = 1;
                }
                int remainder = allocate(recipient, share);
                // 删除收到分配的接收者, 因此他们被排除在下一个分配回合之外.
                if (remainder > 0) {
                    iter.remove();
                }
                allocated += (share - remainder);
            }
            leftToAllocate -= allocated;
        }

        return 0;
    }


    private Stream getStream(int streamId, boolean unknownIsError) throws ConnectionException {
        Integer key = Integer.valueOf(streamId);
        Stream result = streams.get(key);
        if (result == null && unknownIsError) {
            // 流已经关闭并从map中删除
            throw new ConnectionException(sm.getString("upgradeHandler.stream.closed", key),
                    Http2Error.PROTOCOL_ERROR);
        }
        return result;
    }


    private Stream createRemoteStream(int streamId) throws ConnectionException {
        Integer key = Integer.valueOf(streamId);

        if (streamId %2 != 1) {
            throw new ConnectionException(
                    sm.getString("upgradeHandler.stream.even", key), Http2Error.PROTOCOL_ERROR);
        }

        pruneClosedStreams();

        Stream result = new Stream(key, this);
        streams.put(key, result);
        return result;
    }


    private Stream createLocalStream(Request request) {
        int streamId = nextLocalStreamId.getAndAdd(2);

        Integer key = Integer.valueOf(streamId);

        Stream result = new Stream(key, this, request);
        streams.put(key, result);
        return result;
    }


    private void close() {
        connectionState.set(ConnectionState.CLOSED);
        for (Stream stream : streams.values()) {
            // 关闭连接. 关闭关联的流.
            stream.receiveReset(Http2Error.CANCEL.getCode());
        }
        try {
            socketWrapper.close();
        } catch (IOException ioe) {
            log.debug(sm.getString("upgradeHandler.socketCloseFailed"), ioe);
        }
    }


    private void pruneClosedStreams() {
        // 只删除 10 个新流
        if (newStreamsSinceLastPrune < 9) {
            // 非原子的. 增量可能会丢失. 这不是问题.
            newStreamsSinceLastPrune++;
            return;
        }
        // 重置计数器
        newStreamsSinceLastPrune = 0;

        // RFC 7540, 5.3.4 端点应该保持状态, 为了至少并发流的最大数目
        long max = localSettings.getMaxConcurrentStreams();

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.pruneStart", connectionId,
                    Long.toString(max), Integer.toString(streams.size())));
        }

        // 允许额外的 10% , 为了优先树中使用的关闭的流
        max = max + max / 10;
        if (max > Integer.MAX_VALUE) {
            max = Integer.MAX_VALUE;
        }

        int toClose = streams.size() - (int) max;
        if (toClose < 1) {
            return;
        }

        // 需要尝试关闭一些流.
        // 按以下顺序关闭流: 
        // 1. 用于无子级请求的完整流
        // 2. 用于有子级请求的完整流
        // 3. 关闭最后的流
        //
        // Steps 1 和 2 将总是会完成.
        // Step 3 将完成必要的最低限度，使流的总数量在限制之下.

        // 使用这些设置来跟踪流的不同类别
        TreeSet<Integer> candidatesStepOne = new TreeSet<>();
        TreeSet<Integer> candidatesStepTwo = new TreeSet<>();
        TreeSet<Integer> candidatesStepThree = new TreeSet<>();

        Iterator<Entry<Integer,Stream>> entryIter = streams.entrySet().iterator();
        while (entryIter.hasNext()) {
            Entry<Integer,Stream> entry = entryIter.next();
            Stream stream = entry.getValue();
            // 不要删除活动的流
            if (stream.isActive()) {
                continue;
            }

            if (stream.isClosedFinal()) {
                // 这个流从 IDLE 到 CLOSED, 并有可能被客户端创建为优先树的一部分.
                candidatesStepThree.add(entry.getKey());
            } else if (stream.getChildStreams().size() == 0) {
                // Closed, no children
                candidatesStepOne.add(entry.getKey());
            } else {
                // Closed, with children
                candidatesStepTwo.add(entry.getKey());
            }
        }

        // 处理第一个列表
        Iterator<Integer> stepOneIter = candidatesStepOne.iterator();
        while (stepOneIter.hasNext()) {
            Integer streamIdToRemove = stepOneIter.next();
            // 删除有子级的流
            Stream removedStream = streams.remove(streamIdToRemove);
            removedStream.detachFromParent();
            toClose--;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.pruned", connectionId, streamIdToRemove));
            }

            // Did this make the parent childless?
            AbstractStream parent = removedStream.getParentStream();
            while (parent instanceof Stream && !((Stream) parent).isActive() &&
                    !((Stream) parent).isClosedFinal() && parent.getChildStreams().size() == 0) {
                streams.remove(parent.getIdentifier());
                parent.detachFromParent();
                toClose--;
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("upgradeHandler.pruned", connectionId, streamIdToRemove));
                }
                // 也需要从 p2 列表删除这个流
                candidatesStepTwo.remove(parent.getIdentifier());
                parent = parent.getParentStream();
            }
        }

        // Process the P2 list
        Iterator<Integer> stepTwoIter = candidatesStepTwo.iterator();
        while (stepTwoIter.hasNext()) {
            Integer streamIdToRemove = stepTwoIter.next();
            removeStreamFromPriorityTree(streamIdToRemove);
            toClose--;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.pruned", connectionId, streamIdToRemove));
            }
        }

        while (toClose > 0 && candidatesStepThree.size() > 0) {
            Integer streamIdToRemove = candidatesStepThree.pollLast();
            removeStreamFromPriorityTree(streamIdToRemove);
            toClose--;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.prunedPriority", connectionId, streamIdToRemove));
            }
        }

        if (toClose > 0) {
            log.warn(sm.getString("upgradeHandler.pruneIncomplete", connectionId,
                    Integer.toString(toClose)));
        }
    }


    private void removeStreamFromPriorityTree(Integer streamIdToRemove) {
        Stream streamToRemove = streams.remove(streamIdToRemove);
        // 移动删除的流的子级到其父级.
        Set<Stream> children = streamToRemove.getChildStreams();
        if (streamToRemove.getChildStreams().size() == 1) {
            // Shortcut
            streamToRemove.getChildStreams().iterator().next().rePrioritise(
                    streamToRemove.getParentStream(), streamToRemove.getWeight());
        } else {
            int totalWeight = 0;
            for (Stream child : children) {
                totalWeight += child.getWeight();
            }
            for (Stream child : children) {
                streamToRemove.getChildStreams().iterator().next().rePrioritise(
                        streamToRemove.getParentStream(),
                        streamToRemove.getWeight() * child.getWeight() / totalWeight);
            }
        }
        streamToRemove.detachFromParent();
        streamToRemove.getChildStreams().clear();
    }


    void push(Request request, Stream associatedStream) throws IOException {
        Stream pushStream  = createLocalStream(request);

        // TODO: Is 1k the optimal value?
        writePushHeaders(associatedStream, pushStream.getIdentifier().intValue(), request, 1024);

        pushStream.sentPushPromise();

        processStreamOnContainerThread(pushStream);
    }


    @Override
    protected final String getConnectionId() {
        return connectionId;
    }


    @Override
    protected final int getWeight() {
        return 0;
    }


    boolean isTrailerHeaderAllowed(String headerName) {
        return allowedTrailerHeaders.contains(headerName);
    }


    // ------------------------------------------- Configuration getters/setters

    public long getReadTimeout() {
        return readTimeout;
    }


    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }


    public long getKeepAliveTimeout() {
        return keepAliveTimeout;
    }


    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }


    public long getWriteTimeout() {
        return writeTimeout;
    }


    public void setWriteTimeout(long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }


    public void setMaxConcurrentStreams(long maxConcurrentStreams) {
        localSettings.set(Setting.MAX_CONCURRENT_STREAMS, maxConcurrentStreams);
    }


    public void setMaxConcurrentStreamExecution(int maxConcurrentStreamExecution) {
        this.maxConcurrentStreamExecution = maxConcurrentStreamExecution;
    }


    public void setInitialWindowSize(int initialWindowSize) {
        localSettings.set(Setting.INITIAL_WINDOW_SIZE, initialWindowSize);
    }


    public void setAllowedTrailerHeaders(Set<String> allowedTrailerHeaders) {
        this.allowedTrailerHeaders = allowedTrailerHeaders;
    }


    public void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }


    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }


    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }


    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }


    public void setMaxTrailerCount(int maxTrailerCount) {
        this.maxTrailerCount = maxTrailerCount;
    }


    public int getMaxTrailerCount() {
        return maxTrailerCount;
    }


    public void setMaxTrailerSize(int maxTrailerSize) {
        this.maxTrailerSize = maxTrailerSize;
    }


    public int getMaxTrailerSize() {
        return maxTrailerSize;
    }


    public void setInitiatePingDisabled(boolean initiatePingDisabled) {
        pingManager.initiateDisabled = initiatePingDisabled;
    }


    // ----------------------------------------------- Http2Parser.Input methods

    @Override
    public boolean fill(boolean block, byte[] data) throws IOException {
        return fill(block, data, 0, data.length);
    }

    @Override
    public boolean fill(boolean block, ByteBuffer data, int len) throws IOException {
        boolean result = fill(block, data.array(), data.arrayOffset() + data.position(), len);
        if (result) {
            data.position(data.position() + len);
        }
        return result;
    }

    @Override
    public boolean fill(boolean block, byte[] data, int offset, int length) throws IOException {
        int len = length;
        int pos = offset;
        boolean nextReadBlock = block;
        int thisRead = 0;

        while (len > 0) {
            thisRead = socketWrapper.read(nextReadBlock, data, pos, len);
            if (thisRead == 0) {
                if (nextReadBlock) {
                    // Should never happen
                    throw new IllegalStateException();
                } else {
                    return false;
                }
            } else if (thisRead == -1) {
                if (connectionState.get().isNewStreamAllowed()) {
                    throw new EOFException();
                } else {
                    return false;
                }
            } else {
                pos += thisRead;
                len -= thisRead;
                nextReadBlock = true;
            }
        }

        return true;
    }


    @Override
    public int getMaxFrameSize() {
        return localSettings.getMaxFrameSize();
    }


    // ---------------------------------------------- Http2Parser.Output methods

    @Override
    public HpackDecoder getHpackDecoder() {
        if (hpackDecoder == null) {
            hpackDecoder = new HpackDecoder(localSettings.getHeaderTableSize());
        }
        return hpackDecoder;
    }


    @Override
    public ByteBuffer startRequestBodyFrame(int streamId, int payloadSize) throws Http2Exception {
        Stream stream = getStream(streamId, true);
        stream.checkState(FrameType.DATA);
        stream.receivedData(payloadSize);
        return stream.getInputByteBuffer();
    }



    @Override
    public void endRequestBodyFrame(int streamId) throws Http2Exception {
        Stream stream = getStream(streamId, true);
        stream.getInputBuffer().onDataAvailable();
    }


    @Override
    public void receivedEndOfStream(int streamId) throws ConnectionException {
        Stream stream = getStream(streamId, connectionState.get().isNewStreamAllowed());
        if (stream != null) {
            stream.receivedEndOfStream();
            if (!stream.isActive()) {
                activeRemoteStreamCount.decrementAndGet();
            }
        }
    }


    @Override
    public void swallowedPadding(int streamId, int paddingLength) throws
            ConnectionException, IOException {
        Stream stream = getStream(streamId, true);
        // +1 是用于定义填充长度的有效载荷字节
        writeWindowUpdate(stream, paddingLength + 1, false);
    }


    @Override
    public HeaderEmitter headersStart(int streamId, boolean headersEndStream) throws Http2Exception {
        if (connectionState.get().isNewStreamAllowed()) {
            Stream stream = getStream(streamId, false);
            if (stream == null) {
                stream = createRemoteStream(streamId);
            }
            if (streamId < maxActiveRemoteStreamId) {
                throw new ConnectionException(sm.getString("upgradeHandler.stream.old",
                        Integer.valueOf(streamId), Integer.valueOf(maxActiveRemoteStreamId)),
                        Http2Error.PROTOCOL_ERROR);
            }
            stream.checkState(FrameType.HEADERS);
            stream.receivedStartOfHeaders(headersEndStream);
            closeIdleStreams(streamId);
            if (localSettings.getMaxConcurrentStreams() < activeRemoteStreamCount.incrementAndGet()) {
                activeRemoteStreamCount.decrementAndGet();
                throw new StreamException(sm.getString("upgradeHandler.tooManyRemoteStreams",
                        Long.toString(localSettings.getMaxConcurrentStreams())),
                        Http2Error.REFUSED_STREAM, streamId);
            }
            return stream;
        } else {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeHandler.noNewStreams",
                        connectionId, Integer.toString(streamId)));
            }
            // 无状态, 因此一个 static 可以用于在GC上保存
            return HEADER_SINK;
        }
    }


    private void closeIdleStreams(int newMaxActiveRemoteStreamId) throws Http2Exception {
        for (int i = maxActiveRemoteStreamId + 2; i < newMaxActiveRemoteStreamId; i += 2) {
            Stream stream = getStream(i, false);
            if (stream != null) {
                stream.closeIfIdle();
            }
        }
        maxActiveRemoteStreamId = newMaxActiveRemoteStreamId;
    }


    @Override
    public void reprioritise(int streamId, int parentStreamId,
            boolean exclusive, int weight) throws Http2Exception {
        if (streamId == parentStreamId) {
            throw new ConnectionException(sm.getString("upgradeHandler.dependency.invalid",
                    getConnectionId(), Integer.valueOf(streamId)), Http2Error.PROTOCOL_ERROR);
        }
        Stream stream = getStream(streamId, false);
        if (stream == null) {
            stream = createRemoteStream(streamId);
        }
        stream.checkState(FrameType.PRIORITY);
        AbstractStream parentStream = getStream(parentStreamId, false);
        if (parentStream == null) {
            parentStream = this;
        }
        stream.rePrioritise(parentStream, exclusive, weight);
    }


    @Override
    public void headersEnd(int streamId) throws ConnectionException {
        setMaxProcessedStream(streamId);
        Stream stream = getStream(streamId, connectionState.get().isNewStreamAllowed());
        if (stream != null && stream.isActive()) {
            if (stream.receivedEndOfHeaders()) {
                processStreamOnContainerThread(stream);
            }
        }
    }


    private void setMaxProcessedStream(int streamId) {
        if (maxProcessedStreamId < streamId) {
            maxProcessedStreamId = streamId;
        }
    }


    @Override
    public void reset(int streamId, long errorCode) throws Http2Exception  {
        Stream stream = getStream(streamId, true);
        stream.checkState(FrameType.RST);
        stream.receiveReset(errorCode);
    }


    @Override
    public void setting(Setting setting, long value) throws ConnectionException {
        // 需要的特殊处理
        if (setting == Setting.INITIAL_WINDOW_SIZE) {
            long oldValue = remoteSettings.getInitialWindowSize();
            // 在新值无效的情况下，首先执行此操作
            remoteSettings.set(setting, value);
            int diff = (int) (value - oldValue);
            for (Stream stream : streams.values()) {
                try {
                    stream.incrementWindowSize(diff);
                } catch (Http2Exception h2e) {
                    stream.close(new StreamException(sm.getString(
                            "upgradeHandler.windowSizeTooBig", connectionId,
                            stream.getIdentifier()),
                            h2e.getError(), stream.getIdentifier().intValue()));
               }
            }
        } else {
            remoteSettings.set(setting, value);
        }
    }


    @Override
    public void settingsEnd(boolean ack) throws IOException {
        if (ack) {
            if (!localSettings.ack()) {
                // Ack was unexpected
                log.warn(sm.getString(
                        "upgradeHandler.unexpectedAck", connectionId, getIdentifier()));
            }
        } else {
            synchronized (socketWrapper) {
                socketWrapper.write(true, SETTINGS_ACK, 0, SETTINGS_ACK.length);
                socketWrapper.flush(true);
            }
        }
    }


    @Override
    public void pingReceive(byte[] payload, boolean ack) throws IOException {
        pingManager.receivePing(payload, ack);
    }


    @Override
    public void goaway(int lastStreamId, long errorCode, String debugData) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.goaway.debug", connectionId,
                    Integer.toString(lastStreamId), Long.toHexString(errorCode), debugData));
        }
        close();
    }


    @Override
    public void incrementWindowSize(int streamId, int increment) throws Http2Exception {
        if (streamId == 0) {
            incrementWindowSize(increment);
        } else {
            Stream stream = getStream(streamId, true);
            stream.checkState(FrameType.WINDOW_UPDATE);
            stream.incrementWindowSize(increment);
        }
    }


    @Override
    public void swallowed(int streamId, FrameType frameType, int flags, int size)
            throws IOException {
        // NO-OP.
    }


    private class PingManager {

        protected boolean initiateDisabled = false;

        // 10 seconds
        private final long pingIntervalNano = 10000000000L;

        private int sequence = 0;
        private long lastPingNanoTime = Long.MIN_VALUE;

        private Queue<PingRecord> inflightPings = new ConcurrentLinkedQueue<>();
        private Queue<Long> roundTripTimes = new ConcurrentLinkedQueue<>();

        /**
         * 检查最近是否发送过 ping, 如果没有, 发送一个.
         *
         * @param force 发送一个 ping, 即使刚刚发送了一个
         *
         * @throws IOException 如果I/O问题阻止ping被发送
         */
        public void sendPing(boolean force) throws IOException {
            if (initiateDisabled) {
                return;
            }
            long now = System.nanoTime();
            if (force || now - lastPingNanoTime > pingIntervalNano) {
                lastPingNanoTime = now;
                byte[] payload = new byte[8];
                synchronized (socketWrapper) {
                    int sentSequence = ++sequence;
                    PingRecord pingRecord = new PingRecord(sentSequence, now);
                    inflightPings.add(pingRecord);
                    ByteUtil.set31Bits(payload, 4, sentSequence);
                    socketWrapper.write(true, PING, 0, PING.length);
                    socketWrapper.write(true, payload, 0, payload.length);
                    socketWrapper.flush(true);
                }
            }
        }

        public void receivePing(byte[] payload, boolean ack) throws IOException {
            if (ack) {
                // 从有效载荷提取序列
                int receivedSequence = ByteUtil.get31Bits(payload, 4);
                PingRecord pingRecord = inflightPings.poll();
                while (pingRecord != null && pingRecord.getSequence() < receivedSequence) {
                    pingRecord = inflightPings.poll();
                }
                if (pingRecord == null) {
                    // Unexpected ACK. Log it.
                } else {
                    long roundTripTime = System.nanoTime() - pingRecord.getSentNanoTime();
                    roundTripTimes.add(Long.valueOf(roundTripTime));
                    while (roundTripTimes.size() > 3) {
                        // 忽略返回值，因为我们只想将队列减少到3个条目, 用于滚动平均值.
                        roundTripTimes.poll();
                    }
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("pingManager.roundTripTime",
                                connectionId, Long.valueOf(roundTripTime)));
                    }
                }

            } else {
                // 客户端发起ping. Echo it back.
                synchronized (socketWrapper) {
                    socketWrapper.write(true, PING_ACK, 0, PING_ACK.length);
                    socketWrapper.write(true, payload, 0, payload.length);
                    socketWrapper.flush(true);
                }
            }
        }

        public long getRoundTripTimeNano() {
            long sum = 0;
            long count = 0;
            for (Long roundTripTime: roundTripTimes) {
                sum += roundTripTime.longValue();
                count++;
            }
            if (count > 0) {
                return sum / count;
            }
            return 0;
        }
    }


    private static class PingRecord {

        private final int sequence;
        private final long sentNanoTime;

        public PingRecord(int sequence, long sentNanoTime) {
            this.sequence = sequence;
            this.sentNanoTime = sentNanoTime;
        }

        public int getSequence() {
            return sequence;
        }

        public long getSentNanoTime() {
            return sentNanoTime;
        }
    }


    private enum ConnectionState {

        NEW(true),
        CONNECTED(true),
        PAUSING(true),
        PAUSED(false),
        CLOSED(false);

        private final boolean newStreamsAllowed;

        private ConnectionState(boolean newStreamsAllowed) {
            this.newStreamsAllowed = newStreamsAllowed;
        }

        public boolean isNewStreamAllowed() {
            return newStreamsAllowed;
        }
     }
}
