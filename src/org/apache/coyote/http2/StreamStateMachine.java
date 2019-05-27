package org.apache.coyote.http2;

import java.util.HashSet;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * See <a href="https://tools.ietf.org/html/rfc7540#section-5.1">state
 * diagram</a> in RFC 7540.
 * <br>
 * 该状态机支持以下添加:
 * <ul>
 * <li>由复位引起的关闭（正常）和关闭之间的区别</li>
 * </ul>
 */
public class StreamStateMachine {

    private static final Log log = LogFactory.getLog(StreamStateMachine.class);
    private static final StringManager sm = StringManager.getManager(StreamStateMachine.class);

    private final Stream stream;
    private State state;


    public StreamStateMachine(Stream stream) {
        this.stream = stream;
        stateChange(null, State.IDLE);
    }


    public synchronized void sentPushPromise() {
        stateChange(State.IDLE, State.RESERVED_LOCAL);
    }


    public synchronized void receivedPushPromise() {
        stateChange(State.IDLE, State.RESERVED_REMOTE);
    }


    public synchronized void sentStartOfHeaders() {
        stateChange(State.IDLE, State.OPEN);
        stateChange(State.RESERVED_LOCAL, State.HALF_CLOSED_REMOTE);
    }


    public synchronized void receivedStartOfHeaders() {
        stateChange(State.IDLE, State.OPEN);
        stateChange(State.RESERVED_REMOTE, State.HALF_CLOSED_LOCAL);
    }


    public synchronized void sentEndOfStream() {
        stateChange(State.OPEN, State.HALF_CLOSED_LOCAL);
        stateChange(State.HALF_CLOSED_REMOTE, State.CLOSED_TX);
    }


    public synchronized void receivedEndOfStream() {
        stateChange(State.OPEN, State.HALF_CLOSED_REMOTE);
        stateChange(State.HALF_CLOSED_LOCAL, State.CLOSED_RX);
    }


    /**
     * 将流标记为重置. 此方法不会更改流状态, 如果:
     * <ul>
     * <li>流已经重置</li>
     * <li>流已经关闭</li>
     * </ul>
     *
     * @throws IllegalStateException 如果流处于不允许重置的状态
     */
    public synchronized void sendReset() {
        if (state == State.IDLE) {
            throw new IllegalStateException(sm.getString("streamStateMachine.debug.change",
                    stream.getConnectionId(), stream.getIdentifier(), state));
        }
        if (state.canReset()) {
            stateChange(state, State.CLOSED_RST_TX);
        }
    }


    final synchronized void receivedReset() {
        stateChange(state, State.CLOSED_RST_RX);
    }


    private void stateChange(State oldState, State newState) {
        if (state == oldState) {
            state = newState;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("streamStateMachine.debug.change", stream.getConnectionId(),
                        stream.getIdentifier(), oldState, newState));
            }
        }
    }


    public synchronized void checkFrameType(FrameType frameType) throws Http2Exception {
        // 没有状态修改. 检查接收的帧类型对该流的当前状态是否有效.
        if (!isFrameTypePermitted(frameType)) {
            if (state.connectionErrorForInvalidFrame) {
                throw new ConnectionException(sm.getString("streamStateMachine.invalidFrame",
                        stream.getConnectionId(), stream.getIdentifier(), state, frameType),
                        state.errorCodeForInvalidFrame);
            } else {
                throw new StreamException(sm.getString("streamStateMachine.invalidFrame",
                        stream.getConnectionId(), stream.getIdentifier(), state, frameType),
                        state.errorCodeForInvalidFrame, stream.getIdentifier().intValue());
            }
        }
    }


    public synchronized boolean isFrameTypePermitted(FrameType frameType) {
        return state.isFrameTypePermitted(frameType);
    }


    public synchronized boolean isActive() {
        return state.isActive();
    }


    public synchronized boolean canRead() {
        return state.canRead();
    }


    public synchronized boolean canWrite() {
        return state.canWrite();
    }


    public synchronized boolean isClosedFinal() {
        return state == State.CLOSED_FINAL;
    }

    public synchronized void closeIfIdle() {
        stateChange(State.IDLE, State.CLOSED_FINAL);
    }


    private enum State {
        IDLE               (false, false, false, true,
                            Http2Error.PROTOCOL_ERROR, FrameType.HEADERS,
                                                       FrameType.PRIORITY),
        OPEN               (true,  true,  true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.DATA,
                                                       FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.PUSH_PROMISE,
                                                       FrameType.WINDOW_UPDATE),
        RESERVED_LOCAL     (false, false, true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.WINDOW_UPDATE),
        RESERVED_REMOTE    (false, false, true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST),
        HALF_CLOSED_LOCAL  (true,  false, true,  true,
                            Http2Error.PROTOCOL_ERROR, FrameType.DATA,
                                                       FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.PUSH_PROMISE,
                                                       FrameType.WINDOW_UPDATE),
        HALF_CLOSED_REMOTE (false, true,  true,  true,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.WINDOW_UPDATE),
        CLOSED_RX          (false, false, false, true,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY),
        CLOSED_TX          (false, false, false, true,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.WINDOW_UPDATE),
        CLOSED_RST_RX      (false, false, false, false,
                            Http2Error.STREAM_CLOSED,  FrameType.PRIORITY),
        CLOSED_RST_TX      (false, false, false, false,
                            Http2Error.STREAM_CLOSED,  FrameType.DATA,
                                                       FrameType.HEADERS,
                                                       FrameType.PRIORITY,
                                                       FrameType.RST,
                                                       FrameType.PUSH_PROMISE,
                                                       FrameType.WINDOW_UPDATE),
        CLOSED_FINAL       (false, false, false, true,
                            Http2Error.PROTOCOL_ERROR, FrameType.PRIORITY);

        private final boolean canRead;
        private final boolean canWrite;
        private final boolean canReset;
        private final boolean connectionErrorForInvalidFrame;
        private final Http2Error errorCodeForInvalidFrame;
        private final Set<FrameType> frameTypesPermitted = new HashSet<>();

        private State(boolean canRead, boolean canWrite, boolean canReset,
                boolean connectionErrorForInvalidFrame, Http2Error errorCode,
                FrameType... frameTypes) {
            this.canRead = canRead;
            this.canWrite = canWrite;
            this.canReset = canReset;
            this.connectionErrorForInvalidFrame = connectionErrorForInvalidFrame;
            this.errorCodeForInvalidFrame = errorCode;
            for (FrameType frameType : frameTypes) {
                frameTypesPermitted.add(frameType);
            }
        }

        public boolean isActive() {
            return canWrite || canRead;
        }

        public boolean canRead() {
            return canRead;
        }

        public boolean canWrite() {
            return canWrite;
        }

        public boolean canReset() {
            return canReset;
        }

        public boolean isFrameTypePermitted(FrameType frameType) {
            return frameTypesPermitted.contains(frameType);
        }
    }
}
