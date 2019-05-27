package org.apache.tomcat.websocket.server;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsFrameBase;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsSession;

public class WsFrameServer extends WsFrameBase {

    private static final Log log = LogFactory.getLog(WsFrameServer.class);
    private static final StringManager sm = StringManager.getManager(WsFrameServer.class);

    private final SocketWrapperBase<?> socketWrapper;
    private final ClassLoader applicationClassLoader;


    public WsFrameServer(SocketWrapperBase<?> socketWrapper, WsSession wsSession,
            Transformation transformation, ClassLoader applicationClassLoader) {
        super(wsSession, transformation);
        this.socketWrapper = socketWrapper;
        this.applicationClassLoader = applicationClassLoader;
    }


    /**
     * 当处理ServletInputStream中的数据时调用.
     *
     * @throws IOException 如果在处理可用数据时发生I/O错误
     */
    private void onDataAvailable() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("wsFrameServer.onDataAvailable");
        }
        if (isOpen() && inputBuffer.hasRemaining() && !isSuspended()) {
            // 当读已暂停时，可能有一个数据留在缓冲区中.
            // 在从套接字读取之前消耗此数据.
            processInputBuffer();
        }

        while (isOpen() && !isSuspended()) {
            // 用尽可能多的数据填充输入缓冲区
            inputBuffer.mark();
            inputBuffer.position(inputBuffer.limit()).limit(inputBuffer.capacity());
            int read = socketWrapper.read(false, inputBuffer);
            inputBuffer.limit(inputBuffer.position()).reset();
            if (read < 0) {
                throw new EOFException();
            } else if (read == 0) {
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("wsFrameServer.bytesRead", Integer.toString(read)));
            }
            processInputBuffer();
        }
    }


    @Override
    protected boolean isMasked() {
        // 数据来自客户端，因此应该被屏蔽
        return true;
    }


    @Override
    protected Transformation getTransformation() {
        // 重写使该包中其他类可见
        return super.getTransformation();
    }


    @Override
    protected boolean isOpen() {
        // 重写使该包中其他类可见
        return super.isOpen();
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected void sendMessageText(boolean last) throws WsIOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            super.sendMessageText(last);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }


    @Override
    protected void sendMessageBinary(ByteBuffer msg, boolean last) throws WsIOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            super.sendMessageBinary(msg, last);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }


    @Override
    protected void resumeProcessing() {
        socketWrapper.processSocket(SocketEvent.OPEN_READ, true);
    }

    SocketState notifyDataAvailable() throws IOException {
        while (isOpen()) {
            switch (getReadState()) {
            case WAITING:
                if (!changeReadState(ReadState.WAITING, ReadState.PROCESSING)) {
                    continue;
                }
                try {
                    return doOnDataAvailable();
                } catch (IOException e) {
                    changeReadState(ReadState.CLOSING);
                    throw e;
                }
            case SUSPENDING_WAIT:
                if (!changeReadState(ReadState.SUSPENDING_WAIT, ReadState.SUSPENDED)) {
                    continue;
                }
                return SocketState.SUSPENDED;
            default:
                throw new IllegalStateException(
                        sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }

        return SocketState.CLOSED;
    }

    private SocketState doOnDataAvailable() throws IOException {
        onDataAvailable();
        while (isOpen()) {
            switch (getReadState()) {
            case PROCESSING:
                if (!changeReadState(ReadState.PROCESSING, ReadState.WAITING)) {
                    continue;
                }
                return SocketState.UPGRADED;
            case SUSPENDING_PROCESS:
                if (!changeReadState(ReadState.SUSPENDING_PROCESS, ReadState.SUSPENDED)) {
                    continue;
                }
                return SocketState.SUSPENDED;
            default:
                throw new IllegalStateException(
                        sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }

        return SocketState.CLOSED;
    }
}
