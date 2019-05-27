package org.apache.tomcat.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.Utf8Encoder;
import org.apache.tomcat.util.res.StringManager;

public abstract class WsRemoteEndpointImplBase implements RemoteEndpoint {

    private static final StringManager sm =
            StringManager.getManager(WsRemoteEndpointImplBase.class);

    protected static final SendResult SENDRESULT_OK = new SendResult();

    private final Log log = LogFactory.getLog(WsRemoteEndpointImplBase.class);

    private final StateMachine stateMachine = new StateMachine();

    private final IntermediateMessageHandler intermediateMessageHandler =
            new IntermediateMessageHandler(this);

    private Transformation transformation = null;
    private final Semaphore messagePartInProgress = new Semaphore(1);
    private final Queue<MessagePart> messagePartQueue = new ArrayDeque<>();
    private final Object messagePartLock = new Object();

    // State
    private volatile boolean closed = false;
    private boolean fragmented = false;
    private boolean nextFragmented = false;
    private boolean text = false;
    private boolean nextText = false;

    // WebSocket header的最大大小是 14 bytes
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(14);
    private final ByteBuffer outputBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private final CharsetEncoder encoder = new Utf8Encoder();
    private final ByteBuffer encoderBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private final AtomicBoolean batchingAllowed = new AtomicBoolean(false);
    private volatile long sendTimeout = -1;
    private WsSession wsSession;
    private List<EncoderEntry> encoderEntries = new ArrayList<>();


    protected void setTransformation(Transformation transformation) {
        this.transformation = transformation;
    }


    public long getSendTimeout() {
        return sendTimeout;
    }


    public void setSendTimeout(long timeout) {
        this.sendTimeout = timeout;
    }


    @Override
    public void setBatchingAllowed(boolean batchingAllowed) throws IOException {
        boolean oldValue = this.batchingAllowed.getAndSet(batchingAllowed);

        if (oldValue && !batchingAllowed) {
            flushBatch();
        }
    }


    @Override
    public boolean getBatchingAllowed() {
        return batchingAllowed.get();
    }


    @Override
    public void flushBatch() throws IOException {
        sendMessageBlock(Constants.INTERNAL_OPCODE_FLUSH, null, true);
    }


    public void sendBytes(ByteBuffer data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.binaryStart();
        sendMessageBlock(Constants.OPCODE_BINARY, data, true);
        stateMachine.complete(true);
    }


    public Future<Void> sendBytesByFuture(ByteBuffer data) {
        FutureToSendHandler f2sh = new FutureToSendHandler(wsSession);
        sendBytesByCompletion(data, f2sh);
        return f2sh;
    }


    public void sendBytesByCompletion(ByteBuffer data, SendHandler handler) {
        if (data == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        if (handler == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullHandler"));
        }
        StateUpdateSendHandler sush = new StateUpdateSendHandler(handler, stateMachine);
        stateMachine.binaryStart();
        startMessage(Constants.OPCODE_BINARY, data, true, sush);
    }


    public void sendPartialBytes(ByteBuffer partialByte, boolean last)
            throws IOException {
        if (partialByte == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.binaryPartialStart();
        sendMessageBlock(Constants.OPCODE_BINARY, partialByte, last);
        stateMachine.complete(last);
    }


    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        if (applicationData.remaining() > 125) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.tooMuchData"));
        }
        sendMessageBlock(Constants.OPCODE_PING, applicationData, true);
    }


    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        if (applicationData.remaining() > 125) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.tooMuchData"));
        }
        sendMessageBlock(Constants.OPCODE_PONG, applicationData, true);
    }


    public void sendString(String text) throws IOException {
        if (text == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.textStart();
        sendMessageBlock(CharBuffer.wrap(text), true);
    }


    public Future<Void> sendStringByFuture(String text) {
        FutureToSendHandler f2sh = new FutureToSendHandler(wsSession);
        sendStringByCompletion(text, f2sh);
        return f2sh;
    }


    public void sendStringByCompletion(String text, SendHandler handler) {
        if (text == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        if (handler == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullHandler"));
        }
        stateMachine.textStart();
        TextMessageSendHandler tmsh = new TextMessageSendHandler(handler,
                CharBuffer.wrap(text), true, encoder, encoderBuffer, this);
        tmsh.write();
        // TextMessageSendHandler 将在完成时更新 stateMachine
    }


    public void sendPartialString(String fragment, boolean isLast)
            throws IOException {
        if (fragment == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.textPartialStart();
        sendMessageBlock(CharBuffer.wrap(fragment), isLast);
    }


    public OutputStream getSendStream() {
        stateMachine.streamStart();
        return new WsOutputStream(this);
    }


    public Writer getSendWriter() {
        stateMachine.writeStart();
        return new WsWriter(this);
    }


    void sendMessageBlock(CharBuffer part, boolean last) throws IOException {
        long timeoutExpiry = getTimeoutExpiry();
        boolean isDone = false;
        while (!isDone) {
            encoderBuffer.clear();
            CoderResult cr = encoder.encode(part, encoderBuffer, true);
            if (cr.isError()) {
                throw new IllegalArgumentException(cr.toString());
            }
            isDone = !cr.isOverflow();
            encoderBuffer.flip();
            sendMessageBlock(Constants.OPCODE_TEXT, encoderBuffer, last && isDone, timeoutExpiry);
        }
        stateMachine.complete(last);
    }


    void sendMessageBlock(byte opCode, ByteBuffer payload, boolean last)
            throws IOException {
        sendMessageBlock(opCode, payload, last, getTimeoutExpiry());
    }


    private long getTimeoutExpiry() {
        // 在发送消息之前获取超时时间. 消息可以触发会话关闭，并且取决于客户端会话在读取超时之前可能关闭的时间.
        long timeout = getBlockingSendTimeout();
        if (timeout < 0) {
            return Long.MAX_VALUE;
        } else {
            return System.currentTimeMillis() + timeout;
        }
    }


    private void sendMessageBlock(byte opCode, ByteBuffer payload, boolean last,
            long timeoutExpiry) throws IOException {
        wsSession.updateLastActive();

        BlockingSendHandler bsh = new BlockingSendHandler();

        List<MessagePart> messageParts = new ArrayList<>();
        messageParts.add(new MessagePart(last, 0, opCode, payload, bsh, bsh, timeoutExpiry));

        messageParts = transformation.sendMessagePart(messageParts);

        // 某些扩展/转换可能会缓冲消息，因此不可能返回任何消息部分. 如果这种情况简单地返回.
        if (messageParts.size() == 0) {
            return;
        }

        long timeout = timeoutExpiry - System.currentTimeMillis();
        try {
            if (!messagePartInProgress.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
                String msg = sm.getString("wsRemoteEndpoint.acquireTimeout");
                wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, msg),
                        new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg));
                throw new SocketTimeoutException(msg);
            }
        } catch (InterruptedException e) {
            String msg = sm.getString("wsRemoteEndpoint.sendInterrupt");
            wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, msg),
                    new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg));
            throw new IOException(msg, e);
        }

        for (MessagePart mp : messageParts) {
            writeMessagePart(mp);
            if (!bsh.getSendResult().isOK()) {
                messagePartInProgress.release();
                Throwable t = bsh.getSendResult().getException();
                wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, t.getMessage()),
                        new CloseReason(CloseCodes.CLOSED_ABNORMALLY, t.getMessage()));
                throw new IOException (t);
            }
            // BlockingSendHandler 不调用结束消息，因此更新标志.
            fragmented = nextFragmented;
            text = nextText;
        }

        if (payload != null) {
            payload.clear();
        }

        endMessage(null, null);
    }


    void startMessage(byte opCode, ByteBuffer payload, boolean last,
            SendHandler handler) {

        wsSession.updateLastActive();

        List<MessagePart> messageParts = new ArrayList<>();
        messageParts.add(new MessagePart(last, 0, opCode, payload,
                intermediateMessageHandler,
                new EndMessageHandler(this, handler), -1));

        messageParts = transformation.sendMessagePart(messageParts);

        // 某些扩展/转换可能会缓冲消息，因此不可能返回任何消息部分. 如果是这种情况，则触发提供的SendHandler
        if (messageParts.size() == 0) {
            handler.onResult(new SendResult());
            return;
        }

        MessagePart mp = messageParts.remove(0);

        boolean doWrite = false;
        synchronized (messagePartLock) {
            if (Constants.OPCODE_CLOSE == mp.getOpCode() && getBatchingAllowed()) {
                // 不应该发生. 由于会话已关闭，所以现在要发送批处理消息太迟了.
                log.warn(sm.getString("wsRemoteEndpoint.flushOnCloseFailed"));
            }
            if (messagePartInProgress.tryAcquire()) {
                doWrite = true;
            } else {
                // 当发送另一条消息时发送控制消息, 控制消息被排队. 在发送控制消息时，后续数据消息部分将结束排队.
                // 此类中的逻辑（状态机, EndMessageHandler, TextMessageSendHandler）确保队列中将只有一个数据消息部分.
            	// 队列中可能存在多个控制消息.

                // 添加到队列
                messagePartQueue.add(mp);
            }
            // 向队列中添加任何剩余消息
            messagePartQueue.addAll(messageParts);
        }
        if (doWrite) {
            // 实际写入必须是外部同步块，以避免o.a.coyote.http11.upgrade.AbstractServletOutputStream中的messagePartLock和writeLock之间可能出现死锁
            writeMessagePart(mp);
        }
    }


    void endMessage(SendHandler handler, SendResult result) {
        boolean doWrite = false;
        MessagePart mpNext = null;
        synchronized (messagePartLock) {

            fragmented = nextFragmented;
            text = nextText;

            mpNext = messagePartQueue.poll();
            if (mpNext == null) {
                messagePartInProgress.release();
            } else if (!closed){
                //在发送关闭端点的分段消息的过程中，会话可能已被意外关闭.
                // 如果发生这种情况, 显然, 试图发送其余信息是没有意义的.
                doWrite = true;
            }
        }
        if (doWrite) {
            // 实际写入必须是外部同步块，以避免o.a.coyote.http11.upgrade.AbstractServletOutputStream中的messagePartLock和writeLock 之间可能出现死锁
            writeMessagePart(mpNext);
        }

        wsSession.updateLastActive();

        // 一些处理程序, 例如 IntermediateMessageHandler, 没有嵌套处理程序, 因此处理程序可能为 null.
        if (handler != null) {
            handler.onResult(result);
        }
    }


    void writeMessagePart(MessagePart mp) {
        if (closed) {
            throw new IllegalStateException(
                    sm.getString("wsRemoteEndpoint.closed"));
        }

        if (Constants.INTERNAL_OPCODE_FLUSH == mp.getOpCode()) {
            nextFragmented = fragmented;
            nextText = text;
            outputBuffer.flip();
            SendHandler flushHandler = new OutputBufferFlushSendHandler(
                    outputBuffer, mp.getEndHandler());
            doWrite(flushHandler, mp.getBlockingWriteTimeoutExpiry(), outputBuffer);
            return;
        }

        // 控制消息可以在分段消息的中间发送, 因此它们对片段或文本标志没有影响
        boolean first;
        if (Util.isControl(mp.getOpCode())) {
            nextFragmented = fragmented;
            nextText = text;
            if (mp.getOpCode() == Constants.OPCODE_CLOSE) {
                closed = true;
            }
            first = true;
        } else {
            boolean isText = Util.isText(mp.getOpCode());

            if (fragmented) {
                // Currently fragmented
                if (text != isText) {
                    throw new IllegalStateException(
                            sm.getString("wsRemoteEndpoint.changeType"));
                }
                nextText = text;
                nextFragmented = !mp.isFin();
                first = false;
            } else {
                // 不是分段的. Might be now
                if (mp.isFin()) {
                    nextFragmented = false;
                } else {
                    nextFragmented = true;
                    nextText = isText;
                }
                first = true;
            }
        }

        byte[] mask;

        if (isMasked()) {
            mask = Util.generateMask();
        } else {
            mask = null;
        }

        headerBuffer.clear();
        writeHeader(headerBuffer, mp.isFin(), mp.getRsv(), mp.getOpCode(),
                isMasked(), mp.getPayload(), mask, first);
        headerBuffer.flip();

        if (getBatchingAllowed() || isMasked()) {
            // 需要通过输出缓冲区写入
            OutputBufferSendHandler obsh = new OutputBufferSendHandler(
                    mp.getEndHandler(), mp.getBlockingWriteTimeoutExpiry(),
                    headerBuffer, mp.getPayload(), mask,
                    outputBuffer, !getBatchingAllowed(), this);
            obsh.write();
        } else {
            // 可以直接写入
            doWrite(mp.getEndHandler(), mp.getBlockingWriteTimeoutExpiry(),
                    headerBuffer, mp.getPayload());
        }
    }


    private long getBlockingSendTimeout() {
        Object obj = wsSession.getUserProperties().get(Constants.BLOCKING_SEND_TIMEOUT_PROPERTY);
        Long userTimeout = null;
        if (obj instanceof Long) {
            userTimeout = (Long) obj;
        }
        if (userTimeout == null) {
            return Constants.DEFAULT_BLOCKING_SEND_TIMEOUT;
        } else {
            return userTimeout.longValue();
        }
    }


    /**
     * 包装用户提供的处理程序，以便在消息完成时通知端点.
     */
    private static class EndMessageHandler implements SendHandler {

        private final WsRemoteEndpointImplBase endpoint;
        private final SendHandler handler;

        public EndMessageHandler(WsRemoteEndpointImplBase endpoint,
                SendHandler handler) {
            this.endpoint = endpoint;
            this.handler = handler;
        }


        @Override
        public void onResult(SendResult result) {
            endpoint.endMessage(handler, result);
        }
    }


    /**
     * 如果转换需要分割 {@link MessagePart} 为多个 {@link MessagePart}, 它使用这个处理程序作为每个额外的 {@link MessagePart}的结束处理程序.
     * 这个处理程序通知这个类已经处理{@link MessagePart}, 队列中的下一个 {@link MessagePart}应该启动.
     * 最后的{@link MessagePart}将使用提供了原始 {@link MessagePart}的{@link EndMessageHandler} .
     */
    private static class IntermediateMessageHandler implements SendHandler {

        private final WsRemoteEndpointImplBase endpoint;

        public IntermediateMessageHandler(WsRemoteEndpointImplBase endpoint) {
            this.endpoint = endpoint;
        }


        @Override
        public void onResult(SendResult result) {
            endpoint.endMessage(null, result);
        }
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    public void sendObject(Object obj) throws IOException, EncodeException {
        if (obj == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        /*
         * 注意，默认情况下，实现将转换原语和它们的对象对象，但是如果用户愿意，他们可以自由地为此指定自己的编码器和解码器.
         */
        Encoder encoder = findEncoder(obj);
        if (encoder == null && Util.isPrimitive(obj.getClass())) {
            String msg = obj.toString();
            sendString(msg);
            return;
        }
        if (encoder == null && byte[].class.isAssignableFrom(obj.getClass())) {
            ByteBuffer msg = ByteBuffer.wrap((byte[]) obj);
            sendBytes(msg);
            return;
        }

        if (encoder instanceof Encoder.Text) {
            String msg = ((Encoder.Text) encoder).encode(obj);
            sendString(msg);
        } else if (encoder instanceof Encoder.TextStream) {
            try (Writer w = getSendWriter()) {
                ((Encoder.TextStream) encoder).encode(obj, w);
            }
        } else if (encoder instanceof Encoder.Binary) {
            ByteBuffer msg = ((Encoder.Binary) encoder).encode(obj);
            sendBytes(msg);
        } else if (encoder instanceof Encoder.BinaryStream) {
            try (OutputStream os = getSendStream()) {
                ((Encoder.BinaryStream) encoder).encode(obj, os);
            }
        } else {
            throw new EncodeException(obj, sm.getString(
                    "wsRemoteEndpoint.noEncoder", obj.getClass()));
        }
    }


    public Future<Void> sendObjectByFuture(Object obj) {
        FutureToSendHandler f2sh = new FutureToSendHandler(wsSession);
        sendObjectByCompletion(obj, f2sh);
        return f2sh;
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    public void sendObjectByCompletion(Object obj, SendHandler completion) {

        if (obj == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        if (completion == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullHandler"));
        }

        /*
         * 注意，默认情况下，实现将转换原语和它们的对象对象，但是如果用户愿意，他们可以自由地为此指定自己的编码器和解码器.
         */
        Encoder encoder = findEncoder(obj);
        if (encoder == null && Util.isPrimitive(obj.getClass())) {
            String msg = obj.toString();
            sendStringByCompletion(msg, completion);
            return;
        }
        if (encoder == null && byte[].class.isAssignableFrom(obj.getClass())) {
            ByteBuffer msg = ByteBuffer.wrap((byte[]) obj);
            sendBytesByCompletion(msg, completion);
            return;
        }

        try {
            if (encoder instanceof Encoder.Text) {
                String msg = ((Encoder.Text) encoder).encode(obj);
                sendStringByCompletion(msg, completion);
            } else if (encoder instanceof Encoder.TextStream) {
                try (Writer w = getSendWriter()) {
                    ((Encoder.TextStream) encoder).encode(obj, w);
                }
                completion.onResult(new SendResult());
            } else if (encoder instanceof Encoder.Binary) {
                ByteBuffer msg = ((Encoder.Binary) encoder).encode(obj);
                sendBytesByCompletion(msg, completion);
            } else if (encoder instanceof Encoder.BinaryStream) {
                try (OutputStream os = getSendStream()) {
                    ((Encoder.BinaryStream) encoder).encode(obj, os);
                }
                completion.onResult(new SendResult());
            } else {
                throw new EncodeException(obj, sm.getString(
                        "wsRemoteEndpoint.noEncoder", obj.getClass()));
            }
        } catch (Exception e) {
            SendResult sr = new SendResult(e);
            completion.onResult(sr);
        }
    }


    protected void setSession(WsSession wsSession) {
        this.wsSession = wsSession;
    }


    protected void setEncoders(EndpointConfig endpointConfig)
            throws DeploymentException {
        encoderEntries.clear();
        for (Class<? extends Encoder> encoderClazz :
                endpointConfig.getEncoders()) {
            Encoder instance;
            try {
                instance = encoderClazz.getConstructor().newInstance();
                instance.init(endpointConfig);
            } catch (ReflectiveOperationException e) {
                throw new DeploymentException(
                        sm.getString("wsRemoteEndpoint.invalidEncoder",
                                encoderClazz.getName()), e);
            }
            EncoderEntry entry = new EncoderEntry(
                    Util.getEncoderType(encoderClazz), instance);
            encoderEntries.add(entry);
        }
    }


    private Encoder findEncoder(Object obj) {
        for (EncoderEntry entry : encoderEntries) {
            if (entry.getClazz().isAssignableFrom(obj.getClass())) {
                return entry.getEncoder();
            }
        }
        return null;
    }


    public final void close() {
        for (EncoderEntry entry : encoderEntries) {
            entry.getEncoder().destroy();
        }
        // 转换处理输入和输出. 它只需要关闭一次，所以它就在输出端关闭.
        transformation.close();
        doClose();
    }


    protected abstract void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry,
            ByteBuffer... data);
    protected abstract boolean isMasked();
    protected abstract void doClose();

    private static void writeHeader(ByteBuffer headerBuffer, boolean fin,
            int rsv, byte opCode, boolean masked, ByteBuffer payload,
            byte[] mask, boolean first) {

        byte b = 0;

        if (fin) {
            // Set the fin bit
            b -= 128;
        }

        b += (rsv << 4);

        if (first) {
            // 这是这个消息的第一个片段
            b += opCode;
        }
        // 如果不是第一个片段, 它是零opCode的延续

        headerBuffer.put(b);

        if (masked) {
            b = (byte) 0x80;
        } else {
            b = 0;
        }

        // Next write the mask && length length
        if (payload.limit() < 126) {
            headerBuffer.put((byte) (payload.limit() | b));
        } else if (payload.limit() < 65536) {
            headerBuffer.put((byte) (126 | b));
            headerBuffer.put((byte) (payload.limit() >>> 8));
            headerBuffer.put((byte) (payload.limit() & 0xFF));
        } else {
            // Will never be more than 2^31-1
            headerBuffer.put((byte) (127 | b));
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) (payload.limit() >>> 24));
            headerBuffer.put((byte) (payload.limit() >>> 16));
            headerBuffer.put((byte) (payload.limit() >>> 8));
            headerBuffer.put((byte) (payload.limit() & 0xFF));
        }
        if (masked) {
            headerBuffer.put(mask[0]);
            headerBuffer.put(mask[1]);
            headerBuffer.put(mask[2]);
            headerBuffer.put(mask[3]);
        }
    }


    private class TextMessageSendHandler implements SendHandler {

        private final SendHandler handler;
        private final CharBuffer message;
        private final boolean isLast;
        private final CharsetEncoder encoder;
        private final ByteBuffer buffer;
        private final WsRemoteEndpointImplBase endpoint;
        private volatile boolean isDone = false;

        public TextMessageSendHandler(SendHandler handler, CharBuffer message,
                boolean isLast, CharsetEncoder encoder,
                ByteBuffer encoderBuffer, WsRemoteEndpointImplBase endpoint) {
            this.handler = handler;
            this.message = message;
            this.isLast = isLast;
            this.encoder = encoder.reset();
            this.buffer = encoderBuffer;
            this.endpoint = endpoint;
        }

        public void write() {
            buffer.clear();
            CoderResult cr = encoder.encode(message, buffer, true);
            if (cr.isError()) {
                throw new IllegalArgumentException(cr.toString());
            }
            isDone = !cr.isOverflow();
            buffer.flip();
            endpoint.startMessage(Constants.OPCODE_TEXT, buffer,
                    isDone && isLast, this);
        }

        @Override
        public void onResult(SendResult result) {
            if (isDone) {
                endpoint.stateMachine.complete(isLast);
                handler.onResult(result);
            } else if(!result.isOK()) {
                handler.onResult(result);
            } else if (closed){
                SendResult sr = new SendResult(new IOException(
                        sm.getString("wsRemoteEndpoint.closedDuringMessage")));
                handler.onResult(sr);
            } else {
                write();
            }
        }
    }


    /**
     * 用于将数据写入输出缓冲区, 如果缓冲区充满, 则刷新该缓冲区.
     */
    private static class OutputBufferSendHandler implements SendHandler {

        private final SendHandler handler;
        private final long blockingWriteTimeoutExpiry;
        private final ByteBuffer headerBuffer;
        private final ByteBuffer payload;
        private final byte[] mask;
        private final ByteBuffer outputBuffer;
        private final boolean flushRequired;
        private final WsRemoteEndpointImplBase endpoint;
        private int maskIndex = 0;

        public OutputBufferSendHandler(SendHandler completion,
                long blockingWriteTimeoutExpiry,
                ByteBuffer headerBuffer, ByteBuffer payload, byte[] mask,
                ByteBuffer outputBuffer, boolean flushRequired,
                WsRemoteEndpointImplBase endpoint) {
            this.blockingWriteTimeoutExpiry = blockingWriteTimeoutExpiry;
            this.handler = completion;
            this.headerBuffer = headerBuffer;
            this.payload = payload;
            this.mask = mask;
            this.outputBuffer = outputBuffer;
            this.flushRequired = flushRequired;
            this.endpoint = endpoint;
        }

        public void write() {
            // Write the header
            while (headerBuffer.hasRemaining() && outputBuffer.hasRemaining()) {
                outputBuffer.put(headerBuffer.get());
            }
            if (headerBuffer.hasRemaining()) {
                // 还要写入更多的 header, 需要刷新
                outputBuffer.flip();
                endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                return;
            }

            // Write the payload
            int payloadLeft = payload.remaining();
            int payloadLimit = payload.limit();
            int outputSpace = outputBuffer.remaining();
            int toWrite = payloadLeft;

            if (payloadLeft > outputSpace) {
                toWrite = outputSpace;
                // 暂时减少限制
                payload.limit(payload.position() + toWrite);
            }

            if (mask == null) {
                // Use a bulk copy
                outputBuffer.put(payload);
            } else {
                for (int i = 0; i < toWrite; i++) {
                    outputBuffer.put(
                            (byte) (payload.get() ^ (mask[maskIndex++] & 0xFF)));
                    if (maskIndex > 3) {
                        maskIndex = 0;
                    }
                }
            }

            if (payloadLeft > outputSpace) {
                // 恢复原始限制
                payload.limit(payloadLimit);
                // 还有更多的数据要写入, 需要刷新
                outputBuffer.flip();
                endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                return;
            }

            if (flushRequired) {
                outputBuffer.flip();
                if (outputBuffer.remaining() == 0) {
                    handler.onResult(SENDRESULT_OK);
                } else {
                    endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                }
            } else {
                handler.onResult(SENDRESULT_OK);
            }
        }

        // ------------------------------------------------- SendHandler methods
        @Override
        public void onResult(SendResult result) {
            if (result.isOK()) {
                if (outputBuffer.hasRemaining()) {
                    endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                } else {
                    outputBuffer.clear();
                    write();
                }
            } else {
                handler.onResult(result);
            }
        }
    }


    /**
     * 确保输出缓冲区被刷新后清除.
     */
    private static class OutputBufferFlushSendHandler implements SendHandler {

        private final ByteBuffer outputBuffer;
        private final SendHandler handler;

        public OutputBufferFlushSendHandler(ByteBuffer outputBuffer, SendHandler handler) {
            this.outputBuffer = outputBuffer;
            this.handler = handler;
        }

        @Override
        public void onResult(SendResult result) {
            if (result.isOK()) {
                outputBuffer.clear();
            }
            handler.onResult(result);
        }
    }


    private static class WsOutputStream extends OutputStream {

        private final WsRemoteEndpointImplBase endpoint;
        private final ByteBuffer buffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
        private final Object closeLock = new Object();
        private volatile boolean closed = false;
        private volatile boolean used = false;

        public WsOutputStream(WsRemoteEndpointImplBase endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void write(int b) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("wsRemoteEndpoint.closedOutputStream"));
            }

            used = true;
            if (buffer.remaining() == 0) {
                flush();
            }
            buffer.put((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("wsRemoteEndpoint.closedOutputStream"));
            }
            if (len == 0) {
                return;
            }
            if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }

            used = true;
            if (buffer.remaining() == 0) {
                flush();
            }
            int remaining = buffer.remaining();
            int written = 0;

            while (remaining < len - written) {
                buffer.put(b, off + written, remaining);
                written += remaining;
                flush();
                remaining = buffer.remaining();
            }
            buffer.put(b, off + written, len - written);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("wsRemoteEndpoint.closedOutputStream"));
            }

            // 优化. 如果没有数据要刷新，则不要发送空消息.
            if (!Constants.STREAMS_DROP_EMPTY_MESSAGES || buffer.position() > 0) {
                doWrite(false);
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (closeLock) {
                if (closed) {
                    return;
                }
                closed = true;
            }

            doWrite(true);
        }

        private void doWrite(boolean last) throws IOException {
            if (!Constants.STREAMS_DROP_EMPTY_MESSAGES || used) {
                buffer.flip();
                endpoint.sendMessageBlock(Constants.OPCODE_BINARY, buffer, last);
            }
            endpoint.stateMachine.complete(last);
            buffer.clear();
        }
    }


    private static class WsWriter extends Writer {

        private final WsRemoteEndpointImplBase endpoint;
        private final CharBuffer buffer = CharBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
        private final Object closeLock = new Object();
        private volatile boolean closed = false;
        private volatile boolean used = false;

        public WsWriter(WsRemoteEndpointImplBase endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("wsRemoteEndpoint.closedWriter"));
            }
            if (len == 0) {
                return;
            }
            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                    ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }

            used = true;
            if (buffer.remaining() == 0) {
                flush();
            }
            int remaining = buffer.remaining();
            int written = 0;

            while (remaining < len - written) {
                buffer.put(cbuf, off + written, remaining);
                written += remaining;
                flush();
                remaining = buffer.remaining();
            }
            buffer.put(cbuf, off + written, len - written);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("wsRemoteEndpoint.closedWriter"));
            }

            if (!Constants.STREAMS_DROP_EMPTY_MESSAGES || buffer.position() > 0) {
                doWrite(false);
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (closeLock) {
                if (closed) {
                    return;
                }
                closed = true;
            }

            doWrite(true);
        }

        private void doWrite(boolean last) throws IOException {
            if (!Constants.STREAMS_DROP_EMPTY_MESSAGES || used) {
                buffer.flip();
                endpoint.sendMessageBlock(buffer, last);
                buffer.clear();
            } else {
                endpoint.stateMachine.complete(last);
            }
        }
    }


    private static class EncoderEntry {

        private final Class<?> clazz;
        private final Encoder encoder;

        public EncoderEntry(Class<?> clazz, Encoder encoder) {
            this.clazz = clazz;
            this.encoder = encoder;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public Encoder getEncoder() {
            return encoder;
        }
    }


    private static enum State {
        OPEN,
        STREAM_WRITING,
        WRITER_WRITING,
        BINARY_PARTIAL_WRITING,
        BINARY_PARTIAL_READY,
        BINARY_FULL_WRITING,
        TEXT_PARTIAL_WRITING,
        TEXT_PARTIAL_READY,
        TEXT_FULL_WRITING
    }


    private static class StateMachine {
        private State state = State.OPEN;

        public synchronized void streamStart() {
            checkState(State.OPEN);
            state = State.STREAM_WRITING;
        }

        public synchronized void writeStart() {
            checkState(State.OPEN);
            state = State.WRITER_WRITING;
        }

        public synchronized void binaryPartialStart() {
            checkState(State.OPEN, State.BINARY_PARTIAL_READY);
            state = State.BINARY_PARTIAL_WRITING;
        }

        public synchronized void binaryStart() {
            checkState(State.OPEN);
            state = State.BINARY_FULL_WRITING;
        }

        public synchronized void textPartialStart() {
            checkState(State.OPEN, State.TEXT_PARTIAL_READY);
            state = State.TEXT_PARTIAL_WRITING;
        }

        public synchronized void textStart() {
            checkState(State.OPEN);
            state = State.TEXT_FULL_WRITING;
        }

        public synchronized void complete(boolean last) {
            if (last) {
                checkState(State.TEXT_PARTIAL_WRITING, State.TEXT_FULL_WRITING,
                        State.BINARY_PARTIAL_WRITING, State.BINARY_FULL_WRITING,
                        State.STREAM_WRITING, State.WRITER_WRITING);
                state = State.OPEN;
            } else {
                checkState(State.TEXT_PARTIAL_WRITING, State.BINARY_PARTIAL_WRITING,
                        State.STREAM_WRITING, State.WRITER_WRITING);
                if (state == State.TEXT_PARTIAL_WRITING) {
                    state = State.TEXT_PARTIAL_READY;
                } else if (state == State.BINARY_PARTIAL_WRITING){
                    state = State.BINARY_PARTIAL_READY;
                } else if (state == State.WRITER_WRITING) {
                    // NO-OP. Leave state as is.
                } else if (state == State.STREAM_WRITING) {
                 // NO-OP. Leave state as is.
                } else {
                    // Should never happen
                    // 上面的if ... else ... 块 above 应该覆盖前面 checkState() 调用所允许的所有状态
                    throw new IllegalStateException(
                            "BUG: This code should never be called");
                }
            }
        }

        private void checkState(State... required) {
            for (State state : required) {
                if (this.state == state) {
                    return;
                }
            }
            throw new IllegalStateException(
                    sm.getString("wsRemoteEndpoint.wrongState", this.state));
        }
    }


    private static class StateUpdateSendHandler implements SendHandler {

        private final SendHandler handler;
        private final StateMachine stateMachine;

        public StateUpdateSendHandler(SendHandler handler, StateMachine stateMachine) {
            this.handler = handler;
            this.stateMachine = stateMachine;
        }

        @Override
        public void onResult(SendResult result) {
            if (result.isOK()) {
                stateMachine.complete(true);
            }
            handler.onResult(result);
        }
    }


    private static class BlockingSendHandler implements SendHandler {

        private SendResult sendResult = null;

        @Override
        public void onResult(SendResult result) {
            sendResult = result;
        }

        public SendResult getSendResult() {
            return sendResult;
        }
    }
}
