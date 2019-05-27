package org.apache.coyote;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.RequestDispatcher;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * 提供所有支持的协议共同的功能和属性(当前仅 HTTP 和 AJP).
 */
public abstract class AbstractProcessor extends AbstractProcessorLight implements ActionHook {

    private static final StringManager sm = StringManager.getManager(AbstractProcessor.class);

    protected Adapter adapter;
    protected final AsyncStateMachine asyncStateMachine;
    private volatile long asyncTimeout = -1;
    protected final AbstractEndpoint<?> endpoint;
    protected final Request request;
    protected final Response response;
    protected volatile SocketWrapperBase<?> socketWrapper = null;
    protected volatile SSLSupport sslSupport;


    /**
     * 当前正在处理的请求/响应的错误状态.
     */
    private ErrorState errorState = ErrorState.NONE;


    public AbstractProcessor(AbstractEndpoint<?> endpoint) {
        this(endpoint, new Request(), new Response());
    }


    protected AbstractProcessor(AbstractEndpoint<?> endpoint, Request coyoteRequest,
            Response coyoteResponse) {
        this.endpoint = endpoint;
        asyncStateMachine = new AsyncStateMachine(this);
        request = coyoteRequest;
        response = coyoteResponse;
        response.setHook(this);
        request.setResponse(response);
        request.setHook(this);
    }

    /**
     * 更新当前错误状态, 如果新的错误状态比当前的错误状态更严重.
     * 
     * @param errorState 错误状态详细信息
     * @param t 发生的错误
     */
    protected void setErrorState(ErrorState errorState, Throwable t) {
        boolean blockIo = this.errorState.isIoAllowed() && !errorState.isIoAllowed();
        this.errorState = this.errorState.getMostSevere(errorState);
        // 不要修改 IOException的状态码, 因为这几乎肯定是客户端断开，在这种情况下，最好保留原始状态代码 http://markmail.org/message/4cxpwmxhtgnrwh7n
        if (response.getStatus() < 400 && !(t instanceof IOException)) {
            response.setStatus(500);
        }
        if (t != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
        }
        if (blockIo && !ContainerThreadMarker.isContainerThread() && isAsync()) {
            // 在异步处理期间发生在非容器线程上的错误，这意味着并非所有必要的清理都已完成. 调度到容器线程进行清理
            // 需要这样做，以确保执行所有必要的清理工作.
            asyncStateMachine.asyncMustError();
            getLog().info(sm.getString("abstractProcessor.nonContainerThreadError"), t);
            processSocketEvent(SocketEvent.ERROR, true);
        }
    }


    protected ErrorState getErrorState() {
        return errorState;
    }


    @Override
    public Request getRequest() {
        return request;
    }


    /**
     * 设置关联的适配器.
     *
     * @param adapter 新适配器
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    /**
     * 获取关联的适配器.
     */
    public Adapter getAdapter() {
        return adapter;
    }


    /**
     * 设置正在使用的socket包装器.
     * @param socketWrapper The socket wrapper
     */
    protected final void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    /**
     * @return 正在使用的socket包装器.
     */
    protected final SocketWrapperBase<?> getSocketWrapper() {
        return socketWrapper;
    }


    @Override
    public final void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    /**
     * @return 底层端点使用的Executor.
     */
    protected Executor getExecutor() {
        return endpoint.getExecutor();
    }


    @Override
    public boolean isAsync() {
        return asyncStateMachine.isAsync();
    }


    @Override
    public SocketState asyncPostProcess() {
        return asyncStateMachine.asyncPostProcess();
    }


    @Override
    public final SocketState dispatch(SocketEvent status) {

        if (status == SocketEvent.OPEN_WRITE && response.getWriteListener() != null) {
            asyncStateMachine.asyncOperation();
            try {
                if (flushBufferedWrite()) {
                    return SocketState.LONG;
                }
            } catch (IOException ioe) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Unable to write async data.", ioe);
                }
                status = SocketEvent.ERROR;
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
            }
        } else if (status == SocketEvent.OPEN_READ && request.getReadListener() != null) {
            dispatchNonBlockingRead();
        } else if (status == SocketEvent.ERROR) {
            // 在非容器线程上发生I/O错误. 包括:
            // - Poller触发的读/写超时 (NIO & APR)
            // - NIO2中的完成处理器故障

            if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) == null) {
                // 因为容器线程上没有发生错误，所以尚未设置请求的错误属性.
                // 如果 socketWrapper 的异常时可用的, 使用它来设置请求的错误属性，因此它对错误处理是可见的.
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, socketWrapper.getError());
            }

            if (request.getReadListener() != null || response.getWriteListener() != null) {
                // 非阻塞I/O期间发生错误. 设置正确的状态，否则错误处理将触发ISE.
                asyncStateMachine.asyncOperation();
            }
        }

        RequestInfo rp = request.getRequestProcessor();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            if (!getAdapter().asyncDispatch(request, response, status)) {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
        } catch (InterruptedIOException e) {
            setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setErrorState(ErrorState.CLOSE_NOW, t);
            getLog().error(sm.getString("http11processor.request.process"), t);
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError()) {
            request.updateCounters();
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else {
            request.updateCounters();
            return dispatchEndRequest();
        }
    }


    @Override
    public final void action(ActionCode actionCode, Object param) {
        switch (actionCode) {
        // 'Normal' servlet 支持
        case COMMIT: {
            if (!response.isCommitted()) {
                try {
                    // 验证并写入响应 header
                    prepareResponse();
                } catch (IOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                }
            }
            break;
        }
        case CLOSE: {
            action(ActionCode.COMMIT, null);
            try {
                finishResponse();
            } catch (CloseNowException cne) {
                setErrorState(ErrorState.CLOSE_NOW, cne);
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            }
            break;
        }
        case ACK: {
            ack();
            break;
        }
        case CLIENT_FLUSH: {
            action(ActionCode.COMMIT, null);
            try {
                flush();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                response.setErrorException(e);
            }
            break;
        }
        case AVAILABLE: {
            request.setAvailable(available(Boolean.TRUE.equals(param)));
            break;
        }
        case REQ_SET_BODY_REPLAY: {
            ByteChunk body = (ByteChunk) param;
            setRequestBody(body);
            break;
        }

        // Error handling
        case IS_ERROR: {
            ((AtomicBoolean) param).set(getErrorState().isError());
            break;
        }
        case IS_IO_ALLOWED: {
            ((AtomicBoolean) param).set(getErrorState().isIoAllowed());
            break;
        }
        case CLOSE_NOW: {
            // 防止对响应的进一步写入
            setSwallowResponse();
            if (param instanceof Throwable) {
                setErrorState(ErrorState.CLOSE_NOW, (Throwable) param);
            } else {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
            break;
        }
        case DISABLE_SWALLOW_INPUT: {
            // 中止上传或类似的行为.
            // 无需阅读剩余的请求.
            disableSwallowRequest();
            // 这是一个错误状态. 确保它被标记.
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            break;
        }

        // 请求属性支持
        case REQ_HOST_ADDR_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.remoteAddr().setString(socketWrapper.getRemoteAddr());
            }
            break;
        }
        case REQ_HOST_ATTRIBUTE: {
            populateRequestAttributeRemoteHost();
            break;
        }
        case REQ_LOCALPORT_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.setLocalPort(socketWrapper.getLocalPort());
            }
            break;
        }
        case REQ_LOCAL_ADDR_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.localAddr().setString(socketWrapper.getLocalAddr());
            }
            break;
        }
        case REQ_LOCAL_NAME_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.localName().setString(socketWrapper.getLocalName());
            }
            break;
        }
        case REQ_REMOTEPORT_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.setRemotePort(socketWrapper.getRemotePort());
            }
            break;
        }

        // SSL 请求属性支持
        case REQ_SSL_ATTRIBUTE: {
            populateSslRequestAttributes();
            break;
        }
        case REQ_SSL_CERTIFICATE: {
            try {
                sslReHandShake();
            } catch (IOException ioe) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            }
            break;
        }

        // Servlet 3.0 异步支持
        case ASYNC_START: {
            asyncStateMachine.asyncStart((AsyncContextCallback) param);
            break;
        }
        case ASYNC_COMPLETE: {
            clearDispatches();
            if (asyncStateMachine.asyncComplete()) {
                processSocketEvent(SocketEvent.OPEN_READ, true);
            }
            break;
        }
        case ASYNC_DISPATCH: {
            if (asyncStateMachine.asyncDispatch()) {
                processSocketEvent(SocketEvent.OPEN_READ, true);
            }
            break;
        }
        case ASYNC_DISPATCHED: {
            asyncStateMachine.asyncDispatched();
            break;
        }
        case ASYNC_ERROR: {
            asyncStateMachine.asyncError();
            break;
        }
        case ASYNC_IS_ASYNC: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsync());
            break;
        }
        case ASYNC_IS_COMPLETING: {
            ((AtomicBoolean) param).set(asyncStateMachine.isCompleting());
            break;
        }
        case ASYNC_IS_DISPATCHING: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncDispatching());
            break;
        }
        case ASYNC_IS_ERROR: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncError());
            break;
        }
        case ASYNC_IS_STARTED: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncStarted());
            break;
        }
        case ASYNC_IS_TIMINGOUT: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncTimingOut());
            break;
        }
        case ASYNC_RUN: {
            asyncStateMachine.asyncRun((Runnable) param);
            break;
        }
        case ASYNC_SETTIMEOUT: {
            if (param == null) {
                return;
            }
            long timeout = ((Long) param).longValue();
            setAsyncTimeout(timeout);
            break;
        }
        case ASYNC_TIMEOUT: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(asyncStateMachine.asyncTimeout());
            break;
        }
        case ASYNC_POST_PROCESS: {
            asyncStateMachine.asyncPostProcess();
            break;
        }

        // Servlet 3.1 非阻塞 I/O
        case REQUEST_BODY_FULLY_READ: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(isRequestBodyFullyRead());
            break;
        }
        case NB_READ_INTEREST: {
            if (!isRequestBodyFullyRead()) {
                registerReadInterest();
            }
            break;
        }
        case NB_WRITE_INTEREST: {
            AtomicBoolean isReady = (AtomicBoolean)param;
            isReady.set(isReady());
            break;
        }
        case DISPATCH_READ: {
            addDispatch(DispatchType.NON_BLOCKING_READ);
            break;
        }
        case DISPATCH_WRITE: {
            addDispatch(DispatchType.NON_BLOCKING_WRITE);
            break;
        }
        case DISPATCH_EXECUTE: {
            executeDispatches();
            break;
        }

        // Servlet 3.1 HTTP 升级
        case UPGRADE: {
            doHttpUpgrade((UpgradeToken) param);
            break;
        }

        // Servlet 4.0 Push requests
        case IS_PUSH_SUPPORTED: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(isPushSupported());
            break;
        }
        case PUSH_REQUEST: {
            doPush((Request) param);
            break;
        }
        }
    }


    /**
     * 在分配给适配器之前, 执行非阻塞读的任何必要处理.
     */
    protected void dispatchNonBlockingRead() {
        asyncStateMachine.asyncOperation();
    }


    @Override
    public void timeoutAsync(long now) {
        if (now < 0) {
            doTimeoutAsync();
        } else {
            long asyncTimeout = getAsyncTimeout();
            if (asyncTimeout > 0) {
                long asyncStart = asyncStateMachine.getLastAsyncStart();
                if ((now - asyncStart) > asyncTimeout) {
                    doTimeoutAsync();
                }
            }
        }
    }


    private void doTimeoutAsync() {
        // 避免多次超时
        setAsyncTimeout(-1);
        processSocketEvent(SocketEvent.TIMEOUT, true);
    }


    public void setAsyncTimeout(long timeout) {
        asyncTimeout = timeout;
    }


    public long getAsyncTimeout() {
        return asyncTimeout;
    }


    @Override
    public void recycle() {
        errorState = ErrorState.NONE;
        asyncStateMachine.recycle();
    }


    protected abstract void prepareResponse() throws IOException;


    protected abstract void finishResponse() throws IOException;


    protected abstract void ack();


    protected abstract void flush() throws IOException;


    protected abstract int available(boolean doRead);


    protected abstract void setRequestBody(ByteChunk body);


    protected abstract void setSwallowResponse();


    protected abstract void disableSwallowRequest();


    /**
     * 直接填充请求属性的处理器(e.g. AJP) 应该采用这种方法并返回 {@code false}.
     *
     * @return {@code true} 如果应该使用SocketWrapper来填充请求属性, 否则 {@code false}.
     */
    protected boolean getPopulateRequestAttributesFromSocket() {
        return true;
    }


    /**
     * 填充远程主机请求属性. 从另一个来源填充这个的Processor (e.g. AJP) 应该重写此方法.
     */
    protected void populateRequestAttributeRemoteHost() {
        if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
            request.remoteHost().setString(socketWrapper.getRemoteHost());
        }
    }


    /**
     * 从这个处理器关联的{@link SSLSupport}实例填充与TLS相关的请求属性.
     * 从不同来源填充TLS属性的协议 (e.g. AJP) 应该重写此方法.
     */
    protected void populateSslRequestAttributes() {
        try {
            if (sslSupport != null) {
                Object sslO = sslSupport.getCipherSuite();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CIPHER_SUITE_KEY, sslO);
                }
                sslO = sslSupport.getPeerCertificateChain();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
                }
                sslO = sslSupport.getKeySize();
                if (sslO != null) {
                    request.setAttribute (SSLSupport.KEY_SIZE_KEY, sslO);
                }
                sslO = sslSupport.getSessionId();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.SESSION_ID_KEY, sslO);
                }
                sslO = sslSupport.getProtocol();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.PROTOCOL_VERSION_KEY, sslO);
                }
                request.setAttribute(SSLSupport.SESSION_MGR, sslSupport);
            }
        } catch (Exception e) {
            getLog().warn(sm.getString("abstractProcessor.socket.ssl"), e);
        }
    }


    /**
     * 可以执行TLS重握手的处理器 (e.g. HTTP/1.1) 应该重写此方法, 并实现重握手.
     *
     * @throws IOException 如果需要身份验证，则客户端将会有I/O，如果出现错误，将引发此异常
     */
    protected void sslReHandShake() throws IOException {
        // NO-OP
    }


    protected void processSocketEvent(SocketEvent event, boolean dispatch) {
        SocketWrapperBase<?> socketWrapper = getSocketWrapper();
        if (socketWrapper != null) {
            socketWrapper.processSocket(event, dispatch);
        }
    }


    protected abstract boolean isRequestBodyFullyRead();


    protected abstract void registerReadInterest();


    protected abstract boolean isReady();


    protected void executeDispatches() {
        SocketWrapperBase<?> socketWrapper = getSocketWrapper();
        Iterator<DispatchType> dispatches = getIteratorAndClearDispatches();
        if (socketWrapper != null) {
            synchronized (socketWrapper) {
                /*
                 * 通过在非容器线程中定义读和/或写监听器来调用非阻塞IO时，调用此方法.
                 * 一旦非容器线程完成，就调用它, 因此第一次调用 onWritePossible() 和 onDataAvailable() 视容器而定.
                 *
                 * 分配需要 (至少对于 APR/native) socket已经被添加到 waitingRequests 队列.
                 * 在非容器线程完成触发调用此方法时，可能不会发生这种情况. 因此, SocketWrapper上代码的同步, 作为初始化这个非容器线程的容器线程保存了一个SocketWrapper上的锁.
                 * 容器线程将添加 socket 到waitingRequests 队列, 在释放socketWrapper上的锁之前.
                 * 因此, 在处理分配之前获取socketWrapper上的锁, 可以确保 socket 已经被添加到 waitingRequests 队列.
                 */
                while (dispatches != null && dispatches.hasNext()) {
                    DispatchType dispatchType = dispatches.next();
                    socketWrapper.processSocket(dispatchType.getSocketStatus(), false);
                }
            }
        }
    }


    /**
     * {@inheritDoc}
     * 实现HTTP升级的处理器必须重写此方法并提供必要的 token.
     */
    @Override
    public UpgradeToken getUpgradeToken() {
        // 不应该到这里，但如果我们这样做...
        throw new IllegalStateException(
                sm.getString("abstractProcessor.httpupgrade.notsupported"));
    }


    /**
     * 处理HTTP升级. 支持HTTP升级的处理器应重写此方法并处理所提供的 token.
     *
     * @param upgradeToken 包含处理器处理升级所需的所有信息
     *
     * @throws UnsupportedOperationException 如果协议不支持HTTP升级
     */
    protected void doHttpUpgrade(UpgradeToken upgradeToken) {
        // Should never happen
        throw new UnsupportedOperationException(
                sm.getString("abstractProcessor.httpupgrade.notsupported"));
    }


    /**
     * {@inheritDoc}
     * 实现HTTP升级的处理器必须重写此方法.
     */
    @Override
    public ByteBuffer getLeftoverInput() {
        // Should never reach this code but in case we do...
        throw new IllegalStateException(sm.getString("abstractProcessor.httpupgrade.notsupported"));
    }


    /**
     * {@inheritDoc}
     * 实现HTTP升级的处理器必须重写此方法.
     */
    @Override
    public boolean isUpgrade() {
        return false;
    }


    /**
     * 支持push的协议应该重写此方法并返回 {@code true}.
     *
     * @return {@code true} 如果这个处理器支持 push, 否则 {@code false}.
     */
    protected boolean isPushSupported() {
        return false;
    }


    /**
     * 处理 push. 支持push的处理器应重写此方法并处理所提供的 token.
     *
     * @param pushTarget 包含处理器处理push请求所需的所有信息
     *
     * @throws UnsupportedOperationException 如果协议不支持 push
     */
    protected void doPush(Request pushTarget) {
        throw new UnsupportedOperationException(
                sm.getString("abstractProcessor.pushrequest.notsupported"));
    }


    /**
     * 刷新任何等待的写入. 在非阻塞写入期间用于刷新以前的不完整的写入中的剩余数据.
     *
     * @return <code>true</code> 如果在方法结束时, 仍有待刷新的数据
     *
     * @throws IOException 如果试图刷新数据时发生I/O错误
     */
    protected abstract boolean flushBufferedWrite() throws IOException ;

    /**
     * 如果调度导致完成了当前请求的处理，则执行任何必要的清理.
     *
     * @return 一旦当前请求的清理完成，返回Socket的状态
     */
    protected abstract SocketState dispatchEndRequest();
}
