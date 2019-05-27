package org.apache.tomcat.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class WsFrameClient extends WsFrameBase {

    private final Log log = LogFactory.getLog(WsFrameClient.class);
    private static final StringManager sm = StringManager.getManager(WsFrameClient.class);

    private final AsyncChannelWrapper channel;
    private final CompletionHandler<Integer, Void> handler;
    // 不是final, 因为它可能需要重新调整大小
    private volatile ByteBuffer response;

    public WsFrameClient(ByteBuffer response, AsyncChannelWrapper channel, WsSession wsSession,
            Transformation transformation) {
        super(wsSession, transformation);
        this.response = response;
        this.channel = channel;
        this.handler = new WsFrameClientCompletionHandler();
    }


    void startInputProcessing() {
        try {
            processSocketRead();
        } catch (IOException e) {
            close(e);
        }
    }


    private void processSocketRead() throws IOException {
        while (true) {
            switch (getReadState()) {
            case WAITING:
                if (!changeReadState(ReadState.WAITING, ReadState.PROCESSING)) {
                    continue;
                }
                while (response.hasRemaining()) {
                    if (isSuspended()) {
                        if (!changeReadState(ReadState.SUSPENDING_PROCESS, ReadState.SUSPENDED)) {
                            continue;
                        }
                        // 在响应缓冲区中仍有可用的数据.
                        // 返回这里，以便响应缓冲区不会被清除，并且不会有数据从套接字读取.
                        // 因此，当首先恢复读取操作时，将消耗留在响应缓冲区中的数据，然后将执行新的套接字读取
                        return;
                    }
                    inputBuffer.mark();
                    inputBuffer.position(inputBuffer.limit()).limit(inputBuffer.capacity());

                    int toCopy = Math.min(response.remaining(), inputBuffer.remaining());

                    // 将HTTP阶段中读取的剩余字节复制到帧处理所使用的输入缓冲区中

                    int orgLimit = response.limit();
                    response.limit(response.position() + toCopy);
                    inputBuffer.put(response);
                    response.limit(orgLimit);

                    inputBuffer.limit(inputBuffer.position()).reset();

                    // Process the data we have
                    processInputBuffer();
                }
                response.clear();

                // Get some more data
                if (isOpen()) {
                    channel.read(response, null, handler);
                } else {
                    changeReadState(ReadState.CLOSING);
                }
                return;
            case SUSPENDING_WAIT:
                if (!changeReadState(ReadState.SUSPENDING_WAIT, ReadState.SUSPENDED)) {
                    continue;
                }
                return;
            default:
                throw new IllegalStateException(
                        sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }
    }


    private final void close(Throwable t) {
        changeReadState(ReadState.CLOSING);
        CloseReason cr;
        if (t instanceof WsIOException) {
            cr = ((WsIOException) t).getCloseReason();
        } else {
            cr = new CloseReason(CloseCodes.CLOSED_ABNORMALLY, t.getMessage());
        }

        try {
            wsSession.close(cr);
        } catch (IOException ignore) {
            // Ignore
        }
    }


    @Override
    protected boolean isMasked() {
        // 数据来自服务器，因此它不被屏蔽
        return false;
    }


    @Override
    protected Log getLog() {
        return log;
    }

    private class WsFrameClientCompletionHandler implements CompletionHandler<Integer, Void> {

        @Override
        public void completed(Integer result, Void attachment) {
            if (result.intValue() == -1) {
                // BZ 57762. 丢失的连接将被报告为EOF，而不是作为错误，因此在这里处理它.
                if (isOpen()) {
                    // 没有接收到闭合帧
                    close(new EOFException());
                }
                // 没有数据处理
                return;
            }
            response.flip();
            doResumeProcessing(true);
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            if (exc instanceof ReadBufferOverflowException) {
                // 如果抛出异常，响应将为空
                response = ByteBuffer
                        .allocate(((ReadBufferOverflowException) exc).getMinBufferSize());
                response.flip();
                doResumeProcessing(false);
            } else {
                close(exc);
            }
        }

        private void doResumeProcessing(boolean checkOpenOnError) {
            while (true) {
                switch (getReadState()) {
                case PROCESSING:
                    if (!changeReadState(ReadState.PROCESSING, ReadState.WAITING)) {
                        continue;
                    }
                    resumeProcessing(checkOpenOnError);
                    return;
                case SUSPENDING_PROCESS:
                    if (!changeReadState(ReadState.SUSPENDING_PROCESS, ReadState.SUSPENDED)) {
                        continue;
                    }
                    return;
                default:
                    throw new IllegalStateException(
                            sm.getString("wsFrame.illegalReadState", getReadState()));
                }
            }
        }
    }


    @Override
    protected void resumeProcessing() {
        resumeProcessing(true);
    }

    private void resumeProcessing(boolean checkOpenOnError) {
        try {
            processSocketRead();
        } catch (IOException e) {
            if (checkOpenOnError) {
                // 只有当客户端尚未从服务器接收到关闭控制消息时，才在IOException上发送关闭消息，因为IOException可能是在服务器发送关闭控制消息之后响应客户端继续发送消息.
                if (isOpen()) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("wsFrameClient.ioe"), e);
                    }
                    close(e);
                }
            } else {
                close(e);
            }
        }
    }
}
