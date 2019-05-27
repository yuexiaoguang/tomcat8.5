package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.Utf8Decoder;
import org.apache.tomcat.util.res.StringManager;

/**
 * 接受 ServletInputStream, 处理它包含的WebSocket帧并提取消息.
 * 收到的WebSocket Ping将自动响应，而不需要应用程序的任何操作.
 */
public abstract class WsFrameBase {

    private static final StringManager sm = StringManager.getManager(WsFrameBase.class);

    // 连接级别属性
    protected final WsSession wsSession;
    protected final ByteBuffer inputBuffer;
    private final Transformation transformation;

    // 控制消息的属性
    // 控制消息可以出现在其他消息的中间，因此需要单独的属性
    private final ByteBuffer controlBufferBinary = ByteBuffer.allocate(125);
    private final CharBuffer controlBufferText = CharBuffer.allocate(125);

    // 当前消息的属性
    private final CharsetDecoder utf8DecoderControl = new Utf8Decoder().
            onMalformedInput(CodingErrorAction.REPORT).
            onUnmappableCharacter(CodingErrorAction.REPORT);
    private final CharsetDecoder utf8DecoderMessage = new Utf8Decoder().
            onMalformedInput(CodingErrorAction.REPORT).
            onUnmappableCharacter(CodingErrorAction.REPORT);
    private boolean continuationExpected = false;
    private boolean textMessage = false;
    private ByteBuffer messageBufferBinary;
    private CharBuffer messageBufferText;
    // 当消息启动时，强制缓存消息处理程序，以便在整个消息中始终使用它
    private MessageHandler binaryMsgHandler = null;
    private MessageHandler textMsgHandler = null;

    // 当前帧的属性
    private boolean fin = false;
    private int rsv = 0;
    private byte opCode = 0;
    private final byte[] mask = new byte[4];
    private int maskIndex = 0;
    private long payloadLength = 0;
    private volatile long payloadWritten = 0;

    // 属性跟踪状态
    private volatile State state = State.NEW_FRAME;
    private volatile boolean open = true;

    private static final AtomicReferenceFieldUpdater<WsFrameBase, ReadState> READ_STATE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(WsFrameBase.class, ReadState.class, "readState");
    private volatile ReadState readState = ReadState.WAITING;

    public WsFrameBase(WsSession wsSession, Transformation transformation) {
        inputBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
        inputBuffer.position(0).limit(0);
        messageBufferBinary = ByteBuffer.allocate(wsSession.getMaxBinaryMessageBufferSize());
        messageBufferText = CharBuffer.allocate(wsSession.getMaxTextMessageBufferSize());
        wsSession.setWsFrame(this);
        this.wsSession = wsSession;
        Transformation finalTransformation;
        if (isMasked()) {
            finalTransformation = new UnmaskTransformation();
        } else {
            finalTransformation = new NoopTransformation();
        }
        if (transformation == null) {
            this.transformation = finalTransformation;
        } else {
            transformation.setNext(finalTransformation);
            this.transformation = transformation;
        }
    }


    protected void processInputBuffer() throws IOException {
        while (!isSuspended()) {
            wsSession.updateLastActive();
            if (state == State.NEW_FRAME) {
                if (!processInitialHeader()) {
                    break;
                }
                // 如果已接收到关闭帧, 没有进一步的数据
                if (!open) {
                    throw new IOException(sm.getString("wsFrame.closed"));
                }
            }
            if (state == State.PARTIAL_HEADER) {
                if (!processRemainingHeader()) {
                    break;
                }
            }
            if (state == State.DATA) {
                if (!processData()) {
                    break;
                }
            }
        }
    }


    /**
     * @return <code>true</code>如果存在足够的数据来处理所有初始 header
     */
    private boolean processInitialHeader() throws IOException {
        // 需要至少两个字节的数据才能做到这一点
        if (inputBuffer.remaining() < 2) {
            return false;
        }
        int b = inputBuffer.get();
        fin = (b & 0x80) != 0;
        rsv = (b & 0x70) >>> 4;
        opCode = (byte) (b & 0x0F);
        if (!transformation.validateRsv(rsv, opCode)) {
            throw new WsIOException(new CloseReason(
                    CloseCodes.PROTOCOL_ERROR,
                    sm.getString("wsFrame.wrongRsv", Integer.valueOf(rsv), Integer.valueOf(opCode))));
        }

        if (Util.isControl(opCode)) {
            if (!fin) {
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.controlFragmented")));
            }
            if (opCode != Constants.OPCODE_PING &&
                    opCode != Constants.OPCODE_PONG &&
                    opCode != Constants.OPCODE_CLOSE) {
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.invalidOpCode", Integer.valueOf(opCode))));
            }
        } else {
            if (continuationExpected) {
                if (!Util.isContinuation(opCode)) {
                    throw new WsIOException(new CloseReason(
                            CloseCodes.PROTOCOL_ERROR,
                            sm.getString("wsFrame.noContinuation")));
                }
            } else {
                try {
                    if (opCode == Constants.OPCODE_BINARY) {
                        // 新二进制消息
                        textMessage = false;
                        int size = wsSession.getMaxBinaryMessageBufferSize();
                        if (size != messageBufferBinary.capacity()) {
                            messageBufferBinary = ByteBuffer.allocate(size);
                        }
                        binaryMsgHandler = wsSession.getBinaryMessageHandler();
                        textMsgHandler = null;
                    } else if (opCode == Constants.OPCODE_TEXT) {
                        // 新文本消息
                        textMessage = true;
                        int size = wsSession.getMaxTextMessageBufferSize();
                        if (size != messageBufferText.capacity()) {
                            messageBufferText = CharBuffer.allocate(size);
                        }
                        binaryMsgHandler = null;
                        textMsgHandler = wsSession.getTextMessageHandler();
                    } else {
                        throw new WsIOException(new CloseReason(
                                CloseCodes.PROTOCOL_ERROR,
                                sm.getString("wsFrame.invalidOpCode", Integer.valueOf(opCode))));
                    }
                } catch (IllegalStateException ise) {
                    // 如果会话已关闭，则抛出
                    throw new WsIOException(new CloseReason(
                            CloseCodes.PROTOCOL_ERROR,
                            sm.getString("wsFrame.sessionClosed")));
                }
            }
            continuationExpected = !fin;
        }
        b = inputBuffer.get();
        // 客户端数据必须被屏蔽
        if ((b & 0x80) == 0 && isMasked()) {
            throw new WsIOException(new CloseReason(
                    CloseCodes.PROTOCOL_ERROR,
                    sm.getString("wsFrame.notMasked")));
        }
        payloadLength = b & 0x7F;
        state = State.PARTIAL_HEADER;
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("wsFrame.partialHeaderComplete", Boolean.toString(fin),
                    Integer.toString(rsv), Integer.toString(opCode), Long.toString(payloadLength)));
        }
        return true;
    }


    protected abstract boolean isMasked();
    protected abstract Log getLog();


    /**
     * @return <code>true</code>如果存在足够的数据来完成header的处理
     */
    private boolean processRemainingHeader() throws IOException {
        // 忽略已经读取的2个字节. 4用于掩码
        int headerLength;
        if (isMasked()) {
            headerLength = 4;
        } else {
            headerLength = 0;
        }
        // 根据长度, 添加附加的字节
        if (payloadLength == 126) {
            headerLength += 2;
        } else if (payloadLength == 127) {
            headerLength += 8;
        }
        if (inputBuffer.remaining() < headerLength) {
            return false;
        }
        // 计算新的有效载荷长度
        if (payloadLength == 126) {
            payloadLength = byteArrayToLong(inputBuffer.array(),
                    inputBuffer.arrayOffset() + inputBuffer.position(), 2);
            inputBuffer.position(inputBuffer.position() + 2);
        } else if (payloadLength == 127) {
            payloadLength = byteArrayToLong(inputBuffer.array(),
                    inputBuffer.arrayOffset() + inputBuffer.position(), 8);
            inputBuffer.position(inputBuffer.position() + 8);
        }
        if (Util.isControl(opCode)) {
            if (payloadLength > 125) {
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.controlPayloadTooBig", Long.valueOf(payloadLength))));
            }
            if (!fin) {
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.controlNoFin")));
            }
        }
        if (isMasked()) {
            inputBuffer.get(mask, 0, 4);
        }
        state = State.DATA;
        return true;
    }


    private boolean processData() throws IOException {
        boolean result;
        if (Util.isControl(opCode)) {
            result = processDataControl();
        } else if (textMessage) {
            if (textMsgHandler == null) {
                result = swallowInput();
            } else {
                result = processDataText();
            }
        } else {
            if (binaryMsgHandler == null) {
                result = swallowInput();
            } else {
                result = processDataBinary();
            }
        }
        checkRoomPayload();
        return result;
    }


    private boolean processDataControl() throws IOException {
        TransformationResult tr = transformation.getMoreData(opCode, fin, rsv, controlBufferBinary);
        if (TransformationResult.UNDERFLOW.equals(tr)) {
            return false;
        }
        // 控制消息具有固定的消息大小, 因此这里不可能是 TransformationResult.OVERFLOW

        controlBufferBinary.flip();
        if (opCode == Constants.OPCODE_CLOSE) {
            open = false;
            String reason = null;
            int code = CloseCodes.NORMAL_CLOSURE.getCode();
            if (controlBufferBinary.remaining() == 1) {
                controlBufferBinary.clear();
                // 有效载荷必须为零或2字节长
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.oneByteCloseCode")));
            }
            if (controlBufferBinary.remaining() > 1) {
                code = controlBufferBinary.getShort();
                if (controlBufferBinary.remaining() > 0) {
                    CoderResult cr = utf8DecoderControl.decode(controlBufferBinary,
                            controlBufferText, true);
                    if (cr.isError()) {
                        controlBufferBinary.clear();
                        controlBufferText.clear();
                        throw new WsIOException(new CloseReason(
                                CloseCodes.PROTOCOL_ERROR,
                                sm.getString("wsFrame.invalidUtf8Close")));
                    }
                    // 输出缓冲区足够大时, 不会有溢出现象. 当所有数据在一次调用中传递给解码器时，将不会出现下溢.
                    controlBufferText.flip();
                    reason = controlBufferText.toString();
                }
            }
            wsSession.onClose(new CloseReason(Util.getCloseCode(code), reason));
        } else if (opCode == Constants.OPCODE_PING) {
            if (wsSession.isOpen()) {
                wsSession.getBasicRemote().sendPong(controlBufferBinary);
            }
        } else if (opCode == Constants.OPCODE_PONG) {
            MessageHandler.Whole<PongMessage> mhPong = wsSession.getPongMessageHandler();
            if (mhPong != null) {
                try {
                    mhPong.onMessage(new WsPongMessage(controlBufferBinary));
                } catch (Throwable t) {
                    handleThrowableOnSend(t);
                } finally {
                    controlBufferBinary.clear();
                }
            }
        } else {
            // 应该早一点抓住，以防万一...
            controlBufferBinary.clear();
            throw new WsIOException(new CloseReason(
                    CloseCodes.PROTOCOL_ERROR,
                    sm.getString("wsFrame.invalidOpCode", Integer.valueOf(opCode))));
        }
        controlBufferBinary.clear();
        newFrame();
        return true;
    }


    @SuppressWarnings("unchecked")
    protected void sendMessageText(boolean last) throws WsIOException {
        if (textMsgHandler instanceof WrappedMessageHandler) {
            long maxMessageSize = ((WrappedMessageHandler) textMsgHandler).getMaxMessageSize();
            if (maxMessageSize > -1 && messageBufferText.remaining() > maxMessageSize) {
                throw new WsIOException(new CloseReason(CloseCodes.TOO_BIG,
                        sm.getString("wsFrame.messageTooBig",
                                Long.valueOf(messageBufferText.remaining()),
                                Long.valueOf(maxMessageSize))));
            }
        }

        try {
            if (textMsgHandler instanceof MessageHandler.Partial<?>) {
                ((MessageHandler.Partial<String>) textMsgHandler)
                        .onMessage(messageBufferText.toString(), last);
            } else {
                // Caller ensures last == true if this branch is used
                ((MessageHandler.Whole<String>) textMsgHandler)
                        .onMessage(messageBufferText.toString());
            }
        } catch (Throwable t) {
            handleThrowableOnSend(t);
        } finally {
            messageBufferText.clear();
        }
    }


    private boolean processDataText() throws IOException {
        // 将可用数据复制到缓冲区
        TransformationResult tr = transformation.getMoreData(opCode, fin, rsv, messageBufferBinary);
        while (!TransformationResult.END_OF_FRAME.equals(tr)) {
            // 帧不完整 - 东西用完了
            // Convert bytes to UTF-8
            messageBufferBinary.flip();
            while (true) {
                CoderResult cr = utf8DecoderMessage.decode(messageBufferBinary, messageBufferText,
                        false);
                if (cr.isError()) {
                    throw new WsIOException(new CloseReason(
                            CloseCodes.NOT_CONSISTENT,
                            sm.getString("wsFrame.invalidUtf8")));
                } else if (cr.isOverflow()) {
                    // 在文本缓冲区中耗尽空间 - 刷新
                    if (usePartial()) {
                        messageBufferText.flip();
                        sendMessageText(false);
                        messageBufferText.clear();
                    } else {
                        throw new WsIOException(new CloseReason(
                                CloseCodes.TOO_BIG,
                                sm.getString("wsFrame.textMessageTooBig")));
                    }
                } else if (cr.isUnderflow()) {
                    // 必须创造尽可能多的空间
                    messageBufferBinary.compact();

                    // 需要更多的输入
                    // 耗尽了什么?
                    if (TransformationResult.OVERFLOW.equals(tr)) {
                        // 消息缓冲区用完 - 结束内部循环, 并重新填充
                        break;
                    } else {
                        // TransformationResult.UNDERFLOW
                        // 输入数据用完 - 得到更多
                        return false;
                    }
                }
            }
            // 读取更多的输入数据
            tr = transformation.getMoreData(opCode, fin, rsv, messageBufferBinary);
        }

        messageBufferBinary.flip();
        boolean last = false;
        // 完全接收了帧
        // Convert bytes to UTF-8
        while (true) {
            CoderResult cr = utf8DecoderMessage.decode(messageBufferBinary, messageBufferText,
                    last);
            if (cr.isError()) {
                throw new WsIOException(new CloseReason(
                        CloseCodes.NOT_CONSISTENT,
                        sm.getString("wsFrame.invalidUtf8")));
            } else if (cr.isOverflow()) {
                // 在文本缓冲区中耗尽空间 - 刷新
                if (usePartial()) {
                    messageBufferText.flip();
                    sendMessageText(false);
                    messageBufferText.clear();
                } else {
                    throw new WsIOException(new CloseReason(
                            CloseCodes.TOO_BIG,
                            sm.getString("wsFrame.textMessageTooBig")));
                }
            } else if (cr.isUnderflow() && !last) {
                // 帧结束和可能的消息.

                if (continuationExpected) {
                    // 如果支持部分消息, 发送已经解码的内容
                    if (usePartial()) {
                        messageBufferText.flip();
                        sendMessageText(false);
                        messageBufferText.clear();
                    }
                    messageBufferBinary.compact();
                    newFrame();
                    // 处理下一帧
                    return true;
                } else {
                    // 确保编码器已刷新所有输出
                    last = true;
                }
            } else {
                // 消息结束
                messageBufferText.flip();
                sendMessageText(true);

                newMessage();
                return true;
            }
        }
    }


    private boolean processDataBinary() throws IOException {
        // 将可用数据复制到缓冲区
        TransformationResult tr = transformation.getMoreData(opCode, fin, rsv, messageBufferBinary);
        while (!TransformationResult.END_OF_FRAME.equals(tr)) {
            // 帧不完整 - 耗尽了什么?
            if (TransformationResult.UNDERFLOW.equals(tr)) {
                // 输入数据耗尽 - 获取更多
                return false;
            }

            // 消息缓冲区用完 - 刷新
            if (!usePartial()) {
                CloseReason cr = new CloseReason(CloseCodes.TOO_BIG,
                        sm.getString("wsFrame.bufferTooSmall",
                                Integer.valueOf(messageBufferBinary.capacity()),
                                Long.valueOf(payloadLength)));
                throw new WsIOException(cr);
            }
            messageBufferBinary.flip();
            ByteBuffer copy = ByteBuffer.allocate(messageBufferBinary.limit());
            copy.put(messageBufferBinary);
            copy.flip();
            sendMessageBinary(copy, false);
            messageBufferBinary.clear();
            // 读取更多数据
            tr = transformation.getMoreData(opCode, fin, rsv, messageBufferBinary);
        }

        // 完全接收了帧
        // 发送消息, 如果:
        // - 支持部分消息
        // - 消息是完整的
        if (usePartial() || !continuationExpected) {
            messageBufferBinary.flip();
            ByteBuffer copy = ByteBuffer.allocate(messageBufferBinary.limit());
            copy.put(messageBufferBinary);
            copy.flip();
            sendMessageBinary(copy, !continuationExpected);
            messageBufferBinary.clear();
        }

        if (continuationExpected) {
            // 希望此消息的更多数据, 开始一个新帧
            newFrame();
        } else {
            // 消息完成, 开始一个新消息
            newMessage();
        }

        return true;
    }


    private void handleThrowableOnSend(Throwable t) throws WsIOException {
        ExceptionUtils.handleThrowable(t);
        wsSession.getLocal().onError(wsSession, t);
        CloseReason cr = new CloseReason(CloseCodes.CLOSED_ABNORMALLY,
                sm.getString("wsFrame.ioeTriggeredClose"));
        throw new WsIOException(cr);
    }


    @SuppressWarnings("unchecked")
    protected void sendMessageBinary(ByteBuffer msg, boolean last) throws WsIOException {
        if (binaryMsgHandler instanceof WrappedMessageHandler) {
            long maxMessageSize = ((WrappedMessageHandler) binaryMsgHandler).getMaxMessageSize();
            if (maxMessageSize > -1 && msg.remaining() > maxMessageSize) {
                throw new WsIOException(new CloseReason(CloseCodes.TOO_BIG,
                        sm.getString("wsFrame.messageTooBig",
                                Long.valueOf(msg.remaining()),
                                Long.valueOf(maxMessageSize))));
            }
        }
        try {
            if (binaryMsgHandler instanceof MessageHandler.Partial<?>) {
                ((MessageHandler.Partial<ByteBuffer>) binaryMsgHandler).onMessage(msg, last);
            } else {
                // Caller ensures last == true if this branch is used
                ((MessageHandler.Whole<ByteBuffer>) binaryMsgHandler).onMessage(msg);
            }
        } catch (Throwable t) {
            handleThrowableOnSend(t);
        }
    }


    private void newMessage() {
        messageBufferBinary.clear();
        messageBufferText.clear();
        utf8DecoderMessage.reset();
        continuationExpected = false;
        newFrame();
    }


    private void newFrame() {
        if (inputBuffer.remaining() == 0) {
            inputBuffer.position(0).limit(0);
        }

        maskIndex = 0;
        payloadWritten = 0;
        state = State.NEW_FRAME;

        // 这些在 processInitialHeader()中重置
        // fin, rsv, opCode, payloadLength, mask

        checkRoomHeaders();
    }


    private void checkRoomHeaders() {
        // 当前帧的起始位置是否太靠近输入缓冲区的末尾?
        if (inputBuffer.capacity() - inputBuffer.position() < 131) {
            // 基于具有有效负载的控制帧的限制
            makeRoom();
        }
    }


    private void checkRoomPayload() {
        if (inputBuffer.capacity() - inputBuffer.position() - payloadLength + payloadWritten < 0) {
            makeRoom();
        }
    }


    private void makeRoom() {
        inputBuffer.compact();
        inputBuffer.flip();
    }


    private boolean usePartial() {
        if (Util.isControl(opCode)) {
            return false;
        } else if (textMessage) {
            return textMsgHandler instanceof MessageHandler.Partial;
        } else {
            // 必须是二进制的
            return binaryMsgHandler instanceof MessageHandler.Partial;
        }
    }


    private boolean swallowInput() {
        long toSkip = Math.min(payloadLength - payloadWritten, inputBuffer.remaining());
        inputBuffer.position(inputBuffer.position() + (int) toSkip);
        payloadWritten += toSkip;
        if (payloadWritten == payloadLength) {
            if (continuationExpected) {
                newFrame();
            } else {
                newMessage();
            }
            return true;
        } else {
            return false;
        }
    }


    protected static long byteArrayToLong(byte[] b, int start, int len) throws IOException {
        if (len > 8) {
            throw new IOException(sm.getString("wsFrame.byteToLongFail", Long.valueOf(len)));
        }
        int shift = 0;
        long result = 0;
        for (int i = start + len - 1; i >= start; i--) {
            result = result + ((b[i] & 0xFF) << shift);
            shift += 8;
        }
        return result;
    }


    protected boolean isOpen() {
        return open;
    }


    protected Transformation getTransformation() {
        return transformation;
    }


    private static enum State {
        NEW_FRAME, PARTIAL_HEADER, DATA
    }


    /**
     * WAITING            - 不暂停
     *                      Server case: 等待从套接字读取数据的通知, 套接字注册到轮询器
     *                      Client case: 已从套接字读取数据，并等待数据被处理
     * PROCESSING         - 不暂停
     *                      Server case: 从套接字读取和处理数据
     *                      Client case: 如果已经读取了数据，则将从套接字读取更多数据
     * SUSPENDING_WAIT    - 暂停, 在WAITING状态下对suspend()进行调用. 对resume()的调用将不做任何事情，并且将转换为WAITING状态
     * SUSPENDING_PROCESS - 暂停, 在PROCESSING状态下对suspend()进行调用. 对resume()的调用将不做任何事情，并且将转换为PROCESSING状态
     * SUSPENDED          - 暂停
     *                      Server case: 处理数据结束(SUSPENDING_PROCESS) / 收到准备从套接字读取数据的通知(SUSPENDING_WAIT),
     *                      套接字没有注册进轮询器
     *                      Client case: 处理数据结束(SUSPENDING_PROCESS) / 已从套接字读取数据并可用于处理(SUSPENDING_WAIT)
     *                      调用 resume() 将:
     *                      Server case: 将套接字注册到轮询器
     *                      Client case: 恢复数据处理
     * CLOSING            - 不暂停, 将发送关闭
     *
     * <pre>
     *     resume           data to be        resume
     *     no action        processed         no action
     *  |---------------| |---------------| |----------|
     *  |               v |               v v          |
     *  |  |----------WAITING«--------PROCESSING----|  |
     *  |  |             ^   processing             |  |
     *  |  |             |   finished               |  |
     *  |  |             |                          |  |
     *  | suspend        |                     suspend |
     *  |  |             |                          |  |
     *  |  |          resume                        |  |
     *  |  |    register socket to poller (server)  |  |
     *  |  |    resume data processing (client)     |  |
     *  |  |             |                          |  |
     *  |  v             |                          v  |
     * SUSPENDING_WAIT   |                  SUSPENDING_PROCESS
     *  |                |                             |
     *  | data available |        processing finished  |
     *  |-------------»SUSPENDED«----------------------|
     * </pre>
     */
    protected enum ReadState {
        WAITING           (false),
        PROCESSING        (false),
        SUSPENDING_WAIT   (true),
        SUSPENDING_PROCESS(true),
        SUSPENDED         (true),
        CLOSING           (false);

        private final boolean isSuspended;

        ReadState(boolean isSuspended) {
            this.isSuspended = isSuspended;
        }

        public boolean isSuspended() {
            return isSuspended;
        }
    }

    public void suspend() {
        while (true) {
            switch (readState) {
            case WAITING:
                if (!READ_STATE_UPDATER.compareAndSet(this, ReadState.WAITING,
                        ReadState.SUSPENDING_WAIT)) {
                    continue;
                }
                return;
            case PROCESSING:
                if (!READ_STATE_UPDATER.compareAndSet(this, ReadState.PROCESSING,
                        ReadState.SUSPENDING_PROCESS)) {
                    continue;
                }
                return;
            case SUSPENDING_WAIT:
                if (readState != ReadState.SUSPENDING_WAIT) {
                    continue;
                } else {
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(sm.getString("wsFrame.suspendRequested"));
                    }
                }
                return;
            case SUSPENDING_PROCESS:
                if (readState != ReadState.SUSPENDING_PROCESS) {
                    continue;
                } else {
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(sm.getString("wsFrame.suspendRequested"));
                    }
                }
                return;
            case SUSPENDED:
                if (readState != ReadState.SUSPENDED) {
                    continue;
                } else {
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(sm.getString("wsFrame.alreadySuspended"));
                    }
                }
                return;
            case CLOSING:
                return;
            default:
                throw new IllegalStateException(sm.getString("wsFrame.illegalReadState", state));
            }
        }
    }

    public void resume() {
        while (true) {
            switch (readState) {
            case WAITING:
                if (readState != ReadState.WAITING) {
                    continue;
                } else {
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(sm.getString("wsFrame.alreadyResumed"));
                    }
                }
                return;
            case PROCESSING:
                if (readState != ReadState.PROCESSING) {
                    continue;
                } else {
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(sm.getString("wsFrame.alreadyResumed"));
                    }
                }
                return;
            case SUSPENDING_WAIT:
                if (!READ_STATE_UPDATER.compareAndSet(this, ReadState.SUSPENDING_WAIT,
                        ReadState.WAITING)) {
                    continue;
                }
                return;
            case SUSPENDING_PROCESS:
                if (!READ_STATE_UPDATER.compareAndSet(this, ReadState.SUSPENDING_PROCESS,
                        ReadState.PROCESSING)) {
                    continue;
                }
                return;
            case SUSPENDED:
                if (!READ_STATE_UPDATER.compareAndSet(this, ReadState.SUSPENDED,
                        ReadState.WAITING)) {
                    continue;
                }
                resumeProcessing();
                return;
            case CLOSING:
                return;
            default:
                throw new IllegalStateException(sm.getString("wsFrame.illegalReadState", state));
            }
        }
    }

    protected boolean isSuspended() {
        return readState.isSuspended();
    }

    protected ReadState getReadState() {
        return readState;
    }

    protected void changeReadState(ReadState newState) {
        READ_STATE_UPDATER.set(this, newState);
    }

    protected boolean changeReadState(ReadState oldState, ReadState newState) {
        return READ_STATE_UPDATER.compareAndSet(this, oldState, newState);
    }

    /**
     * 当读取操作恢复时，将调用此方法.
     * 由于读取操作的挂起可以随时调用, 在实现此方法时, 应该考虑在从套接字再次读取之前, 内部缓冲区中可能仍然存在需要处理的数据.
     */
    protected abstract void resumeProcessing();


    private abstract class TerminalTransformation implements Transformation {

        @Override
        public boolean validateRsvBits(int i) {
            // 终端转换不使用RSV位，而且没有下一个转换，所以总是返回 true.
            return true;
        }

        @Override
        public Extension getExtensionResponse() {
            // 因为终端转换不是扩展
            return null;
        }

        @Override
        public void setNext(Transformation t) {
            // NO-OP 因为这是终端转换
        }

        /**
         * {@inheritDoc}
         * <p>
         * RSV以外的任何零值都无效.
         */
        @Override
        public boolean validateRsv(int rsv, byte opCode) {
            return rsv == 0;
        }

        @Override
        public void close() {
            // NO-OP for the terminal transformations
        }
    }


    /**
     * 用于需要获取有效载荷数据而不需要去除掩码的客户端实现.
     */
    private final class NoopTransformation extends TerminalTransformation {

        @Override
        public TransformationResult getMoreData(byte opCode, boolean fin, int rsv,
                ByteBuffer dest) {
            // 忽略opCode, 因为对于所有opCode的转换是相同的
            // 忽略RSV, 因为它现在已知为零
            long toWrite = Math.min(payloadLength - payloadWritten, inputBuffer.remaining());
            toWrite = Math.min(toWrite, dest.remaining());

            int orgLimit = inputBuffer.limit();
            inputBuffer.limit(inputBuffer.position() + (int) toWrite);
            dest.put(inputBuffer);
            inputBuffer.limit(orgLimit);
            payloadWritten += toWrite;

            if (payloadWritten == payloadLength) {
                return TransformationResult.END_OF_FRAME;
            } else if (inputBuffer.remaining() == 0) {
                return TransformationResult.UNDERFLOW;
            } else {
                // !dest.hasRemaining()
                return TransformationResult.OVERFLOW;
            }
        }


        @Override
        public List<MessagePart> sendMessagePart(List<MessagePart> messageParts) {
            // TODO 掩蔽应该移到这个方法
            // NO-OP 简单返回未修改的消息.
            return messageParts;
        }
    }


    /**
     * 供服务器实现使用，需要在任何进一步处理之前，获取有效载荷数据，并去除掩码.
     */
    private final class UnmaskTransformation extends TerminalTransformation {

        @Override
        public TransformationResult getMoreData(byte opCode, boolean fin, int rsv,
                ByteBuffer dest) {
            // 忽略opCode, 因为对于所有opCode的转换是相同的
            // 忽略RSV, 因为它现在已知为零
            while (payloadWritten < payloadLength && inputBuffer.remaining() > 0 &&
                    dest.hasRemaining()) {
                byte b = (byte) ((inputBuffer.get() ^ mask[maskIndex]) & 0xFF);
                maskIndex++;
                if (maskIndex == 4) {
                    maskIndex = 0;
                }
                payloadWritten++;
                dest.put(b);
            }
            if (payloadWritten == payloadLength) {
                return TransformationResult.END_OF_FRAME;
            } else if (inputBuffer.remaining() == 0) {
                return TransformationResult.UNDERFLOW;
            } else {
                // !dest.hasRemaining()
                return TransformationResult.OVERFLOW;
            }
        }

        @Override
        public List<MessagePart> sendMessagePart(List<MessagePart> messageParts) {
            // NO-OP 简单返回未修改的消息.
            return messageParts;
        }
    }
}
