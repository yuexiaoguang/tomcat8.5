package org.apache.coyote.http2;

import java.io.IOException;
import java.util.Iterator;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.ErrorState;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

class StreamProcessor extends AbstractProcessor {

    private static final Log log = LogFactory.getLog(StreamProcessor.class);
    private static final StringManager sm = StringManager.getManager(StreamProcessor.class);

    private final Http2UpgradeHandler handler;
    private final Stream stream;


    StreamProcessor(Http2UpgradeHandler handler, Stream stream, Adapter adapter,
            SocketWrapperBase<?> socketWrapper) {
        super(socketWrapper.getEndpoint(), stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.handler = handler;
        this.stream = stream;
        setAdapter(adapter);
        setSocketWrapper(socketWrapper);
    }


    final void process(SocketEvent event) {
        try {
            // FIXME: socketWrapper上的常规处理器同步, 但这里是死锁
            synchronized (this) {
                // HTTP/2 等效于 AbstractConnectionHandler#process(), 不包括 socket <-> processor 映射
                ContainerThreadMarker.set();
                SocketState state = SocketState.CLOSED;
                try {
                    state = process(socketWrapper, event);

                    if (state == SocketState.CLOSED) {
                        if (!getErrorState().isConnectionIoAllowed()) {
                            ConnectionException ce = new ConnectionException(sm.getString(
                                    "streamProcessor.error.connection", stream.getConnectionId(),
                                    stream.getIdentifier()), Http2Error.INTERNAL_ERROR);
                            stream.close(ce);
                        } else if (!getErrorState().isIoAllowed()) {
                            StreamException se = new StreamException(sm.getString(
                                    "streamProcessor.error.stream", stream.getConnectionId(),
                                    stream.getIdentifier()), Http2Error.INTERNAL_ERROR,
                                    stream.getIdentifier().intValue());
                            stream.close(se);
                        }
                    }
                } catch (Exception e) {
                    ConnectionException ce = new ConnectionException(sm.getString(
                            "streamProcessor.error.connection", stream.getConnectionId(),
                            stream.getIdentifier()), Http2Error.INTERNAL_ERROR);
                    ce.initCause(e);
                    stream.close(ce);
                } finally {
                    ContainerThreadMarker.clear();
                }
            }
        } finally {
            handler.executeQueuedStream();
        }
    }


    @Override
    protected final void prepareResponse() throws IOException {
        response.setCommitted(true);
        stream.writeHeaders();
    }


    @Override
    protected final void finishResponse() throws IOException {
        stream.getOutputBuffer().close();
    }


    @Override
    protected final void ack() {
        if (!response.isCommitted() && request.hasExpectation()) {
            try {
                stream.writeAck();
            } catch (IOException ioe) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            }
        }
    }


    @Override
    protected final void flush() throws IOException {
        stream.flushData();
    }


    @Override
    protected final int available(boolean doRead) {
        return stream.getInputBuffer().available();
    }


    @Override
    protected final void setRequestBody(ByteChunk body) {
        stream.getInputBuffer().insertReplayedBody(body);
        try {
            stream.receivedEndOfStream();
        } catch (ConnectionException e) {
            // Exception will not be thrown in this case
        }
    }


    @Override
    protected final void setSwallowResponse() {
        // NO-OP
    }


    @Override
    protected final void disableSwallowRequest() {
        // NO-OP
        // HTTP/2 必须忽略接收到的任何输入, 确保流量控制窗口的正确跟踪.
    }


    @Override
    protected void processSocketEvent(SocketEvent event, boolean dispatch) {
        if (dispatch) {
            handler.processStreamOnContainerThread(this, event);
        } else {
            this.process(event);
        }
    }


    @Override
    protected final boolean isRequestBodyFullyRead() {
        return stream.getInputBuffer().isRequestBodyFullyRead();
    }


    @Override
    protected final void registerReadInterest() {
        stream.getInputBuffer().registerReadInterest();
    }


    @Override
    protected final boolean isReady() {
        return stream.getOutputBuffer().isReady();
    }


    @Override
    protected final void executeDispatches() {
        Iterator<DispatchType> dispatches = getIteratorAndClearDispatches();
        synchronized (this) {
            /*
             * TODO 检查同步是必要的.
             *      和使用SocketWrapper的父类比较
             */
            while (dispatches != null && dispatches.hasNext()) {
                DispatchType dispatchType = dispatches.next();
                processSocketEvent(dispatchType.getSocketStatus(), false);
            }
        }
    }


    @Override
    protected final boolean isPushSupported() {
        return stream.isPushSupported();
    }


    @Override
    protected final void doPush(Request pushTarget) {
        try {
            stream.push(pushTarget);
        } catch (IOException ioe) {
            setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            response.setErrorException(ioe);
        }
    }


    @Override
    public void recycle() {
        // StreamProcessor 实例不能被重用.
        // 清空可以清空的字段, 如果是重用的, 用来帮助 GC 并触发 NPE
        setSocketWrapper(null);
        setAdapter(null);
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    public void pause() {
        // NO-OP. 由 Http2UpgradeHandler 处理
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socket) throws IOException {
        try {
            adapter.service(request, response);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("streamProcessor.service.error"), e);
            }
            response.setStatus(500);
            setErrorState(ErrorState.CLOSE_NOW, e);
        }

        if (getErrorState().isError()) {
            action(ActionCode.CLOSE, null);
            request.updateCounters();
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else {
            action(ActionCode.CLOSE, null);
            request.updateCounters();
            return SocketState.CLOSED;
        }
    }


    @Override
    protected boolean flushBufferedWrite() throws IOException {
        if (stream.getOutputBuffer().flush(false)) {
            // 缓冲区没有完全刷新，所以重新注册流写入.
            // 注意, 这不是通过 Response, 因为该级别的写入注册状态应该保持不变.
            // 一旦缓存被清空, 然后下面的代码将调用 dispatch(), 启用对应于这个事件的 Response.
            if (stream.getOutputBuffer().isReady()) {
                // Unexpected
                throw new IllegalStateException();
            }
            return true;
        }
        return false;
    }


    @Override
    protected SocketState dispatchEndRequest() {
        return SocketState.CLOSED;
    }
}
