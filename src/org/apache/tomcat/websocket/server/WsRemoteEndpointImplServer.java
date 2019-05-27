package org.apache.tomcat.websocket.server;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;

/**
 * 这是服务器端 {@link javax.websocket.RemoteEndpoint} 实现 - 即服务器使用什么来向客户端发送数据.
 */
public class WsRemoteEndpointImplServer extends WsRemoteEndpointImplBase {

    private static final StringManager sm =
            StringManager.getManager(WsRemoteEndpointImplServer.class);
    private static final Log log = LogFactory.getLog(WsRemoteEndpointImplServer.class);

    private final SocketWrapperBase<?> socketWrapper;
    private final WsWriteTimeout wsWriteTimeout;
    private volatile SendHandler handler = null;
    private volatile ByteBuffer[] buffers = null;

    private volatile long timeoutExpiry = -1;
    private volatile boolean close;

    public WsRemoteEndpointImplServer(SocketWrapperBase<?> socketWrapper,
            WsServerContainer serverContainer) {
        this.socketWrapper = socketWrapper;
        this.wsWriteTimeout = serverContainer.getTimeout();
    }


    @Override
    protected final boolean isMasked() {
        return false;
    }


    @Override
    protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry,
            ByteBuffer... buffers) {
        if (blockingWriteTimeoutExpiry == -1) {
            this.handler = handler;
            this.buffers = buffers;
            // 这绝对是触发写入的同一线程，因此需要一个调度.
            onWritePossible(true);
        } else {
            // Blocking
            try {
                for (ByteBuffer buffer : buffers) {
                    long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                    if (timeout <= 0) {
                        SendResult sr = new SendResult(new SocketTimeoutException());
                        handler.onResult(sr);
                        return;
                    }
                    socketWrapper.setWriteTimeout(timeout);
                    socketWrapper.write(true, buffer);
                }
                long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                if (timeout <= 0) {
                    SendResult sr = new SendResult(new SocketTimeoutException());
                    handler.onResult(sr);
                    return;
                }
                socketWrapper.setWriteTimeout(timeout);
                socketWrapper.flush(true);
                handler.onResult(SENDRESULT_OK);
            } catch (IOException e) {
                SendResult sr = new SendResult(e);
                handler.onResult(sr);
            }
        }
    }


    public void onWritePossible(boolean useDispatch) {
        ByteBuffer[] buffers = this.buffers;
        if (buffers == null) {
            // servlet 3.1将调用写入监听器一次，即使没有写入
            return;
        }
        boolean complete = false;
        try {
            socketWrapper.flush(false);
            // 如果这是false，当它是true时会有一个回调
            while (socketWrapper.isReadyForWrite()) {
                complete = true;
                for (ByteBuffer buffer : buffers) {
                    if (buffer.hasRemaining()) {
                        complete = false;
                        socketWrapper.write(false, buffer);
                        break;
                    }
                }
                if (complete) {
                    socketWrapper.flush(false);
                    complete = socketWrapper.isReadyForWrite();
                    if (complete) {
                        wsWriteTimeout.unregister(this);
                        clearHandler(null, useDispatch);
                        if (close) {
                            close();
                        }
                    }
                    break;
                }
            }
        } catch (IOException | IllegalStateException e) {
            wsWriteTimeout.unregister(this);
            clearHandler(e, useDispatch);
            close();
        }

        if (!complete) {
            // 异步写入正在进行中
            long timeout = getSendTimeout();
            if (timeout > 0) {
                // 用超时线程注册
                timeoutExpiry = timeout + System.currentTimeMillis();
                wsWriteTimeout.register(this);
            }
        }
    }


    @Override
    protected void doClose() {
        if (handler != null) {
            // close() 可以由各种各样的场景触发.
            // 始终使用分派, 比, 尝试跟踪是否由触发写入操作的同一线程调用此方法, 要简单得多
            clearHandler(new EOFException(), true);
        }
        try {
            socketWrapper.close();
        } catch (IOException e) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("wsRemoteEndpointServer.closeFailed"), e);
            }
        }
        wsWriteTimeout.unregister(this);
    }


    protected long getTimeoutExpiry() {
        return timeoutExpiry;
    }


    /*
     * 当前只从后台线程调用, 因此只是使用useDispatch == false 调用 clearHandler(),
     * 但是添加了方法参数, 以防其他调用者开始使用这个方法来确保那些调用者仔细考虑useDispatch 的正确值对于他们来说是什么.
     */
    protected void onTimeout(boolean useDispatch) {
        if (handler != null) {
            clearHandler(new SocketTimeoutException(), useDispatch);
        }
        close();
    }


    @Override
    protected void setTransformation(Transformation transformation) {
        // 完全重写, 因此该包中其他类可见
        super.setTransformation(transformation);
    }


    /**
     * @param t             发生的错误
     * @param useDispatch   是否应该从新线程调用{@link SendHandler#onResult(SendResult)},
     * 						请记住{@link javax.websocket.RemoteEndpoint.Async}的要求
     */
    private void clearHandler(Throwable t, boolean useDispatch) {
        // 将结果标记为（部分）消息已完成，这意味着可以发送下一条消息，该消息可以更新处理程序的值.
        // 因此，在发出（部分）消息结束之前，保持一个本地副本.
        SendHandler sh = handler;
        handler = null;
        buffers = null;
        if (sh != null) {
            if (useDispatch) {
                OnResultRunnable r = new OnResultRunnable(sh, t);
                AbstractEndpoint<?> endpoint = socketWrapper.getEndpoint();
                Executor containerExecutor = endpoint.getExecutor();
                if (endpoint.isRunning() && containerExecutor != null) {
                    containerExecutor.execute(r);
                } else {
                    // 不能使用执行器直接调用runnable.
                    // 这并非在所有情况下都严格符合规范，但在关机期间只发送关闭消息，因此不应该出现导致堆栈溢出的嵌套调用，如bug 55715中所述.
                    // 嵌套调用的问题是规范中需要单独线程的原因.
                    r.run();
                }
            } else {
                if (t == null) {
                    sh.onResult(new SendResult());
                } else {
                    sh.onResult(new SendResult(t));
                }
            }
        }
    }


    private static class OnResultRunnable implements Runnable {

        private final SendHandler sh;
        private final Throwable t;

        private OnResultRunnable(SendHandler sh, Throwable t) {
            this.sh = sh;
            this.t = t;
        }

        @Override
        public void run() {
            if (t == null) {
                sh.onResult(new SendResult());
            } else {
                sh.onResult(new SendResult(t));
            }
        }
    }
}
