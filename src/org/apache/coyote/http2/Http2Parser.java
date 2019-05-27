package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.ProtocolException;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.res.StringManager;

class Http2Parser {

    private static final Log log = LogFactory.getLog(Http2Parser.class);
    private static final StringManager sm = StringManager.getManager(Http2Parser.class);

    static final byte[] CLIENT_PREFACE_START =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    private final String connectionId;
    private final Input input;
    private final Output output;
    private final byte[] frameHeaderBuffer = new byte[9];

    private volatile HpackDecoder hpackDecoder;
    private volatile ByteBuffer headerReadBuffer =
            ByteBuffer.allocate(Constants.DEFAULT_HEADER_READ_BUFFER_SIZE);
    private volatile int headersCurrentStream = -1;
    private volatile boolean headersEndStream = false;

    Http2Parser(String connectionId, Input input, Output output) {
        this.connectionId = connectionId;
        this.input = input;
        this.output = output;
    }


    /**
     * 读取和处理单个帧. 一旦开始读取帧, 剩余部分将使用阻塞IO来读取.
     *
     * @param block 如果没有立即可用的帧，该方法是否会阻塞到帧可用?
     *
     * @return <code>true</code>如果读取了一个帧; 否则<code>false</code>
     *
     * @throws IOException 如果在尝试读取一个帧时发生不明错误
     */
    boolean readFrame(boolean block) throws Http2Exception, IOException {
        return readFrame(block, null);
    }


    private boolean readFrame(boolean block, FrameType expected)
            throws IOException, Http2Exception {

        if (!input.fill(block, frameHeaderBuffer)) {
            return false;
        }

        int payloadSize = ByteUtil.getThreeBytes(frameHeaderBuffer, 0);
        FrameType frameType = FrameType.valueOf(ByteUtil.getOneByte(frameHeaderBuffer, 3));
        int flags = ByteUtil.getOneByte(frameHeaderBuffer, 4);
        int streamId = ByteUtil.get31Bits(frameHeaderBuffer, 5);

        try {
            validateFrame(expected, frameType, streamId, flags, payloadSize);
        } catch (StreamException se) {
            swallow(streamId, payloadSize, false);
            throw se;
        }

        switch (frameType) {
        case DATA:
            readDataFrame(streamId, flags, payloadSize);
            break;
        case HEADERS:
            readHeadersFrame(streamId, flags, payloadSize);
            break;
        case PRIORITY:
            readPriorityFrame(streamId);
            break;
        case RST:
            readRstFrame(streamId);
            break;
        case SETTINGS:
            readSettingsFrame(flags, payloadSize);
            break;
        case PUSH_PROMISE:
            readPushPromiseFrame(streamId);
            break;
        case PING:
            readPingFrame(flags);
            break;
        case GOAWAY:
            readGoawayFrame(payloadSize);
            break;
        case WINDOW_UPDATE:
            readWindowUpdateFrame(streamId);
            break;
        case CONTINUATION:
            readContinuationFrame(streamId, flags, payloadSize);
            break;
        case UNKNOWN:
            readUnknownFrame(streamId, frameType, flags, payloadSize);
        }

        return true;
    }


    private void readDataFrame(int streamId, int flags, int payloadSize)
            throws Http2Exception, IOException {
        // 处理流
        int padLength = 0;

        boolean endOfStream = Flags.isEndOfStream(flags);

        int dataLength;
        if (Flags.hasPadding(flags)) {
            byte[] b = new byte[1];
            input.fill(true, b);
            padLength = b[0] & 0xFF;

            if (padLength >= payloadSize) {
                throw new ConnectionException(
                        sm.getString("http2Parser.processFrame.tooMuchPadding", connectionId,
                                Integer.toString(streamId), Integer.toString(padLength),
                                Integer.toString(payloadSize)), Http2Error.PROTOCOL_ERROR);
            }
            // +1 , 为了上面刚刚读取的 padding 长度字节
            dataLength = payloadSize - (padLength + 1);
        } else {
            dataLength = payloadSize;
        }

        if (log.isDebugEnabled()) {
            String padding;
            if (Flags.hasPadding(flags)) {
                padding = Integer.toString(padLength);
            } else {
                padding = "none";
            }
            log.debug(sm.getString("http2Parser.processFrameData.lengths", connectionId,
                    Integer.toString(streamId), Integer.toString(dataLength), padding));
        }

        ByteBuffer dest = output.startRequestBodyFrame(streamId, payloadSize);
        if (dest == null) {
            swallow(streamId, dataLength, false);
            // 发送任何通知之前, 处理 padding, 在padding 无效的情况下
            if (padLength > 0) {
                swallow(streamId, padLength, true);
            }
            if (endOfStream) {
                output.receivedEndOfStream(streamId);
            }
        } else {
            synchronized (dest) {
                if (dest.remaining() < dataLength) {
                    swallow(streamId, dataLength, false);
                    // 客户端发送的数据比窗口大小允许的更多
                    throw new StreamException("Client sent more data than stream window allowed", Http2Error.FLOW_CONTROL_ERROR, streamId);
                }
                input.fill(true, dest, dataLength);
                // 发送任何通知之前, 处理 padding, 在padding 无效的情况下
                if (padLength > 0) {
                    swallow(streamId, padLength, true);
                }
                if (endOfStream) {
                    output.receivedEndOfStream(streamId);
                }
                output.endRequestBodyFrame(streamId);
            }
        }
        if (padLength > 0) {
            output.swallowedPadding(streamId, padLength);
        }
    }


    private void readHeadersFrame(int streamId, int flags, int payloadSize)
            throws Http2Exception, IOException {

        headersEndStream = Flags.isEndOfStream(flags);

        if (hpackDecoder == null) {
            hpackDecoder = output.getHpackDecoder();
        }
        try {
            hpackDecoder.setHeaderEmitter(output.headersStart(streamId, headersEndStream));
        } catch (StreamException se) {
            swallow(streamId, payloadSize, false);
            throw se;
        }

        int padLength = 0;
        boolean padding = Flags.hasPadding(flags);
        boolean priority = Flags.hasPriority(flags);
        int optionalLen = 0;
        if (padding) {
            optionalLen = 1;
        }
        if (priority) {
            optionalLen += 5;
        }
        if (optionalLen > 0) {
            byte[] optional = new byte[optionalLen];
            input.fill(true, optional);
            int optionalPos = 0;
            if (padding) {
                padLength = ByteUtil.getOneByte(optional, optionalPos++);
                if (padLength >= payloadSize) {
                    throw new ConnectionException(
                            sm.getString("http2Parser.processFrame.tooMuchPadding", connectionId,
                                    Integer.toString(streamId), Integer.toString(padLength),
                                    Integer.toString(payloadSize)), Http2Error.PROTOCOL_ERROR);
                }
            }
            if (priority) {
                boolean exclusive = ByteUtil.isBit7Set(optional[optionalPos]);
                int parentStreamId = ByteUtil.get31Bits(optional, optionalPos);
                int weight = ByteUtil.getOneByte(optional, optionalPos + 4) + 1;
                output.reprioritise(streamId, parentStreamId, exclusive, weight);
            }

            payloadSize -= optionalLen;
            payloadSize -= padLength;
        }

        readHeaderPayload(streamId, payloadSize);

        swallow(streamId, padLength, true);

        if (Flags.isEndOfHeaders(flags)) {
            onHeadersComplete(streamId);
        } else {
            headersCurrentStream = streamId;
        }
    }


    private void readPriorityFrame(int streamId) throws Http2Exception, IOException {
        byte[] payload = new byte[5];
        input.fill(true, payload);

        boolean exclusive = ByteUtil.isBit7Set(payload[0]);
        int parentStreamId = ByteUtil.get31Bits(payload, 0);
        int weight = ByteUtil.getOneByte(payload, 4) + 1;

        if (streamId == parentStreamId) {
            throw new StreamException(sm.getString("http2Parser.processFramePriority.invalidParent",
                    connectionId, Integer.valueOf(streamId)), Http2Error.PROTOCOL_ERROR, streamId);
        }

        output.reprioritise(streamId, parentStreamId, exclusive, weight);
    }


    private void readRstFrame(int streamId) throws Http2Exception, IOException {
        byte[] payload = new byte[4];
        input.fill(true, payload);

        long errorCode = ByteUtil.getFourBytes(payload, 0);
        output.reset(streamId, errorCode);
        headersCurrentStream = -1;
        headersEndStream = false;
    }


    private void readSettingsFrame(int flags, int payloadSize) throws Http2Exception, IOException {
        boolean ack = Flags.isAck(flags);
        if (payloadSize > 0 && ack) {
            throw new ConnectionException(sm.getString(
                    "http2Parser.processFrameSettings.ackWithNonZeroPayload"),
                    Http2Error.FRAME_SIZE_ERROR);
        }

        if (payloadSize != 0) {
            // 处理设置
            byte[] setting = new byte[6];
            for (int i = 0; i < payloadSize / 6; i++) {
                input.fill(true, setting);
                int id = ByteUtil.getTwoBytes(setting, 0);
                long value = ByteUtil.getFourBytes(setting, 2);
                output.setting(Setting.valueOf(id), value);
            }
        }
        output.settingsEnd(ack);
    }


    private void readPushPromiseFrame(int streamId) throws Http2Exception {
        throw new ConnectionException(sm.getString("http2Parser.processFramePushPromise",
                connectionId, Integer.valueOf(streamId)), Http2Error.PROTOCOL_ERROR);
    }


    private void readPingFrame(int flags) throws IOException {
        // 读取负荷
        byte[] payload = new byte[8];
        input.fill(true, payload);
        output.pingReceive(payload, Flags.isAck(flags));
    }


    private void readGoawayFrame(int payloadSize) throws IOException {
        byte[] payload = new byte[payloadSize];
        input.fill(true, payload);

        int lastStreamId = ByteUtil.get31Bits(payload, 0);
        long errorCode = ByteUtil.getFourBytes(payload, 4);
        String debugData = null;
        if (payloadSize > 8) {
            debugData = new String(payload, 8, payloadSize - 8, StandardCharsets.UTF_8);
        }
        output.goaway(lastStreamId, errorCode, debugData);
    }


    private void readWindowUpdateFrame(int streamId) throws Http2Exception, IOException {
        byte[] payload = new byte[4];
        input.fill(true,  payload);
        int windowSizeIncrement = ByteUtil.get31Bits(payload, 0);

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrameWindowUpdate.debug", connectionId,
                    Integer.toString(streamId), Integer.toString(windowSizeIncrement)));
        }

        // 验证数据
        if (windowSizeIncrement == 0) {
            if (streamId == 0) {
                throw new ConnectionException(
                        sm.getString("http2Parser.processFrameWindowUpdate.invalidIncrement"),
                        Http2Error.PROTOCOL_ERROR);
            } else {
                throw new StreamException(
                        sm.getString("http2Parser.processFrameWindowUpdate.invalidIncrement"),
                        Http2Error.PROTOCOL_ERROR, streamId);
            }
        }

        output.incrementWindowSize(streamId, windowSizeIncrement);
    }


    private void readContinuationFrame(int streamId, int flags, int payloadSize)
            throws Http2Exception, IOException {
        if (headersCurrentStream == -1) {
            // No headers to continue
            throw new ConnectionException(sm.getString(
                    "http2Parser.processFrameContinuation.notExpected", connectionId,
                    Integer.toString(streamId)), Http2Error.PROTOCOL_ERROR);
        }

        readHeaderPayload(streamId, payloadSize);

        if (Flags.isEndOfHeaders(flags)) {
            headersCurrentStream = -1;
            onHeadersComplete(streamId);
        }
    }


    private void readHeaderPayload(int streamId, int payloadSize)
            throws Http2Exception, IOException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrameHeaders.payload", connectionId,
                    Integer.valueOf(streamId), Integer.valueOf(payloadSize)));
        }

        int remaining = payloadSize;

        while (remaining > 0) {
            if (headerReadBuffer.remaining() == 0) {
                // 缓冲区需要扩展
                int newSize;
                if (headerReadBuffer.capacity() < payloadSize) {
                    // First step, 扩展到当前的有效载荷. 应该可以覆盖很多种情况.
                    newSize = payloadSize;
                } else {
                    // Header 必须分布在多个帧. 再翻一番的缓冲区大小，直到header 可以读.
                    newSize = headerReadBuffer.capacity() * 2;
                }
                headerReadBuffer = ByteBufferUtils.expand(headerReadBuffer, newSize);
            }
            int toRead = Math.min(headerReadBuffer.remaining(), remaining);
            // headerReadBuffer 在写入模式
            input.fill(true, headerReadBuffer, toRead);
            // 切换到读取模式
            headerReadBuffer.flip();
            try {
                hpackDecoder.decode(headerReadBuffer);
            } catch (HpackException hpe) {
                throw new ConnectionException(
                        sm.getString("http2Parser.processFrameHeaders.decodingFailed"),
                        Http2Error.COMPRESSION_ERROR, hpe);
            }

            // 切换到写入模式
            headerReadBuffer.compact();
            remaining -= toRead;

            if (hpackDecoder.isHeaderCountExceeded()) {
                StreamException headerException = new StreamException(sm.getString(
                        "http2Parser.headerLimitCount", connectionId, Integer.valueOf(streamId)),
                        Http2Error.ENHANCE_YOUR_CALM, streamId);
                hpackDecoder.getHeaderEmitter().setHeaderException(headerException);
            }

            if (hpackDecoder.isHeaderSizeExceeded(headerReadBuffer.position())) {
                StreamException headerException = new StreamException(sm.getString(
                        "http2Parser.headerLimitSize", connectionId, Integer.valueOf(streamId)),
                        Http2Error.ENHANCE_YOUR_CALM, streamId);
                hpackDecoder.getHeaderEmitter().setHeaderException(headerException);
            }

            if (hpackDecoder.isHeaderSwallowSizeExceeded(headerReadBuffer.position())) {
                throw new ConnectionException(sm.getString("http2Parser.headerLimitSize",
                        connectionId, Integer.valueOf(streamId)), Http2Error.ENHANCE_YOUR_CALM);
            }
        }
    }


    private void onHeadersComplete(int streamId) throws Http2Exception {
        // 任何遗留数据都是压缩错误
        if (headerReadBuffer.position() > 0) {
            throw new ConnectionException(
                    sm.getString("http2Parser.processFrameHeaders.decodingDataLeft"),
                    Http2Error.COMPRESSION_ERROR);
        }

        // 延迟验证 (和触发异常), 直到这里
        // 因为所有的header仍然必须被读取, 如果抛出一个StreamException
        hpackDecoder.getHeaderEmitter().validateHeaders();

        output.headersEnd(streamId);

        if (headersEndStream) {
            output.receivedEndOfStream(streamId);
            headersEndStream = false;
        }

        // 如果缓冲区以前扩大了, 为了新的请求重置大小
        if (headerReadBuffer.capacity() > Constants.DEFAULT_HEADER_READ_BUFFER_SIZE) {
            headerReadBuffer = ByteBuffer.allocate(Constants.DEFAULT_HEADER_READ_BUFFER_SIZE);
        }
    }


    private void readUnknownFrame(int streamId, FrameType frameType, int flags, int payloadSize)
            throws IOException {
        try {
            swallow(streamId, payloadSize, false);
        } catch (ConnectionException e) {
            // Will never happen because swallow() is called with mustBeZero set
            // to false
        }
        output.swallowed(streamId, frameType, flags, payloadSize);
    }


    private void swallow(int streamId, int len, boolean mustBeZero)
            throws IOException, ConnectionException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.swallow.debug", connectionId,
                    Integer.toString(streamId), Integer.toString(len)));
        }
        if (len == 0) {
            return;
        }
        int read = 0;
        byte[] buffer = new byte[1024];
        while (read < len) {
            int thisTime = Math.min(buffer.length, len - read);
            input.fill(true, buffer, 0, thisTime);
            if (mustBeZero) {
                // 验证 padding 是零, 因为接收非零 padding是客户端或服务器端错误的强烈指示.
                for (int i = 0; i < thisTime; i++) {
                    if (buffer[i] != 0) {
                        throw new ConnectionException(sm.getString("http2Parser.nonZeroPadding",
                                connectionId, Integer.toString(streamId)), Http2Error.PROTOCOL_ERROR);
                    }
                }
            }
            read += thisTime;
        }
    }


    /*
     * 实现注意 :
     * 适用于所有传入帧的验证应在此处实现.
     * 指定验证的帧类型应该在对应的 readXxxFrame() 方法执行.
     * 适用于某些但不是所有帧类型的验证, 使用自己的判断.
     */
    private void validateFrame(FrameType expected, FrameType frameType, int streamId, int flags,
            int payloadSize) throws Http2Exception {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("http2Parser.processFrame", connectionId,
                    Integer.toString(streamId), frameType, Integer.toString(flags),
                    Integer.toString(payloadSize)));
        }

        if (expected != null && frameType != expected) {
            throw new StreamException(sm.getString("http2Parser.processFrame.unexpectedType",
                    expected, frameType), Http2Error.PROTOCOL_ERROR, streamId);
        }

        int maxFrameSize = input.getMaxFrameSize();
        if (payloadSize > maxFrameSize) {
            throw new ConnectionException(sm.getString("http2Parser.payloadTooBig",
                    Integer.toString(payloadSize), Integer.toString(maxFrameSize)),
                    Http2Error.FRAME_SIZE_ERROR);
        }

        if (headersCurrentStream != -1) {
            if (headersCurrentStream != streamId) {
                throw new ConnectionException(sm.getString("http2Parser.headers.wrongStream",
                        connectionId, Integer.toString(headersCurrentStream),
                        Integer.toString(streamId)), Http2Error.COMPRESSION_ERROR);
            }
            if (frameType == FrameType.RST) {
                // NO-OP: RST is OK here
            } else if (frameType != FrameType.CONTINUATION) {
                throw new ConnectionException(sm.getString("http2Parser.headers.wrongFrameType",
                        connectionId, Integer.toString(headersCurrentStream),
                        frameType), Http2Error.COMPRESSION_ERROR);
            }
        }

        frameType.check(streamId, payloadSize);
    }


    /**
     * 使用阻塞IO从输入读取和验证连接开端.
     */
    void readConnectionPreface() throws Http2Exception {
        byte[] data = new byte[CLIENT_PREFACE_START.length];
        try {
            input.fill(true, data);

            for (int i = 0; i < CLIENT_PREFACE_START.length; i++) {
                if (CLIENT_PREFACE_START[i] != data[i]) {
                    throw new ProtocolException(sm.getString("http2Parser.preface.invalid"));
                }
            }

            // 必须始终遵循设置帧
            readFrame(true, FrameType.SETTINGS);
        } catch (IOException ioe) {
            throw new ProtocolException(sm.getString("http2Parser.preface.io"), ioe);
        }
    }


    /**
     * 必须由解析器的数据源实现的接口.
     */
    static interface Input {

        /**
         * 用数据填充给定数组, 除非需要非阻塞和没有数据可用. 如果数据是可用的, 然后缓冲区将使用阻塞I/O填充.
         *
         * @param block 第一次读取提供的缓冲区是否为阻塞读取.
         * @param data  要填充的缓冲区
         * @param offset 在缓冲区中开始写入的位置
         * @param length 要读取的字节数
         *
         * @return <code>true</code>如果缓冲区已填满; 否则 <code>false</code>
         *
         * @throws IOException 在获取填充缓冲区的数据过程中发生一个 I/O 错误
         */
        boolean fill(boolean block, byte[] data, int offset, int length) throws IOException;

        boolean fill(boolean block, byte[] data) throws IOException;

        boolean fill(boolean block, ByteBuffer data, int len) throws IOException;

        int getMaxFrameSize();
    }


    /**
     * 必须实现的接口, 用于从解析器接收通知, 在其处理传入的帧时.
     */
    static interface Output {

        HpackDecoder getHpackDecoder();

        // Data frames
        ByteBuffer startRequestBodyFrame(int streamId, int payloadSize) throws Http2Exception;
        void endRequestBodyFrame(int streamId) throws Http2Exception;
        void receivedEndOfStream(int streamId) throws ConnectionException;
        void swallowedPadding(int streamId, int paddingLength) throws ConnectionException, IOException;

        // Header frames
        HeaderEmitter headersStart(int streamId, boolean headersEndStream) throws Http2Exception;
        void headersEnd(int streamId) throws ConnectionException;

        // Priority frames (also headers)
        void reprioritise(int streamId, int parentStreamId, boolean exclusive, int weight)
                throws Http2Exception;

        // Reset frames
        void reset(int streamId, long errorCode) throws Http2Exception;

        // Settings frames
        void setting(Setting setting, long value) throws ConnectionException;
        void settingsEnd(boolean ack) throws IOException;

        // Ping frames
        void pingReceive(byte[] payload, boolean ack) throws IOException;

        // Goaway
        void goaway(int lastStreamId, long errorCode, String debugData);

        // Window size
        void incrementWindowSize(int streamId, int increment) throws Http2Exception;

        // Testing
        void swallowed(int streamId, FrameType frameType, int flags, int size) throws IOException;
    }
}
