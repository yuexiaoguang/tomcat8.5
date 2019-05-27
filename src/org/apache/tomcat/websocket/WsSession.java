package org.apache.tomcat.websocket;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.WritePendingException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCode;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.MessageHandler.Partial;
import javax.websocket.MessageHandler.Whole;
import javax.websocket.PongMessage;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.InstanceManagerBindings;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

public class WsSession implements Session {

    // 省略号是一个单个字符，它看起来像一行中的三个周期，用来表示延续.
    private static final byte[] ELLIPSIS_BYTES = "\u2026".getBytes(StandardCharsets.UTF_8);
    // UTF-8中的省略号为三字节
    private static final int ELLIPSIS_BYTES_LEN = ELLIPSIS_BYTES.length;

    private static final StringManager sm = StringManager.getManager(WsSession.class);
    private static AtomicLong ids = new AtomicLong(0);

    private final Log log = LogFactory.getLog(WsSession.class);

    private final Endpoint localEndpoint;
    private final WsRemoteEndpointImplBase wsRemoteEndpoint;
    private final RemoteEndpoint.Async remoteEndpointAsync;
    private final RemoteEndpoint.Basic remoteEndpointBasic;
    private final ClassLoader applicationClassLoader;
    private final WsWebSocketContainer webSocketContainer;
    private final URI requestUri;
    private final Map<String, List<String>> requestParameterMap;
    private final String queryString;
    private final Principal userPrincipal;
    private final EndpointConfig endpointConfig;

    private final List<Extension> negotiatedExtensions;
    private final String subProtocol;
    private final Map<String, String> pathParameters;
    private final boolean secure;
    private final String httpSessionId;
    private final String id;

    // 希望只处理String类型的消息
    private volatile MessageHandler textMessageHandler = null;
    // 希望只处理<ByteBuffer>类型的消息
    private volatile MessageHandler binaryMessageHandler = null;
    private volatile MessageHandler.Whole<PongMessage> pongMessageHandler = null;
    private volatile State state = State.OPEN;
    private final Object stateLock = new Object();
    private final Map<String, Object> userProperties = new ConcurrentHashMap<>();
    private volatile int maxBinaryMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private volatile int maxTextMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private volatile long maxIdleTimeout = 0;
    private volatile long lastActive = System.currentTimeMillis();
    private Map<FutureToSendHandler, FutureToSendHandler> futures = new ConcurrentHashMap<>();

    /**
     * 为两个提供的端点之间的通信创建一个新的WebSocket 会话.
     * 当调用{@link Endpoint#onClose(Session, CloseReason)}时，将使用调用该构造函数时的{@link Thread#getContextClassLoader()}的结果.
     *
     * @param localEndpoint        此代码管理的端点
     * @param wsRemoteEndpoint     另一个远程端点
     * @param wsWebSocketContainer 创建此会话的容器
     * @param requestUri           用于连接到此端点的URI; 或<code>null</code>是客户端会话
     * @param requestParameterMap  与启动该会话的请求相关联的参数; 或<code>null</code>是客户端会话
     * @param queryString          与启动该会话的请求相关联的查询字符串; 或<code>null</code>是客户端会话
     * @param userPrincipal        与发起该会话的请求相关联的主体; 或<code>null</code>是客户端会话
     * @param httpSessionId        与启动该会话的请求相关联的HTTP会话ID; 或<code>null</code>是客户端会话
     * @param negotiatedExtensions 此会话使用的约定扩展名
     * @param subProtocol          此会话使用的约定子协议
     * @param pathParameters       与发起此会话的请求相关联的路径参数; 或<code>null</code>是客户端会话
     * @param secure               这个会话是通过安全连接启动的吗?
     * @param endpointConfig       端点的配置信息
     * 
     * @throws DeploymentException 如果指定了无效的编码
     */
    public WsSession(Endpoint localEndpoint,
            WsRemoteEndpointImplBase wsRemoteEndpoint,
            WsWebSocketContainer wsWebSocketContainer,
            URI requestUri, Map<String, List<String>> requestParameterMap,
            String queryString, Principal userPrincipal, String httpSessionId,
            List<Extension> negotiatedExtensions, String subProtocol, Map<String, String> pathParameters,
            boolean secure, EndpointConfig endpointConfig) throws DeploymentException {
        this.localEndpoint = localEndpoint;
        this.wsRemoteEndpoint = wsRemoteEndpoint;
        this.wsRemoteEndpoint.setSession(this);
        this.remoteEndpointAsync = new WsRemoteEndpointAsync(wsRemoteEndpoint);
        this.remoteEndpointBasic = new WsRemoteEndpointBasic(wsRemoteEndpoint);
        this.webSocketContainer = wsWebSocketContainer;
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
        wsRemoteEndpoint.setSendTimeout(wsWebSocketContainer.getDefaultAsyncSendTimeout());
        this.maxBinaryMessageBufferSize = webSocketContainer.getDefaultMaxBinaryMessageBufferSize();
        this.maxTextMessageBufferSize = webSocketContainer.getDefaultMaxTextMessageBufferSize();
        this.maxIdleTimeout = webSocketContainer.getDefaultMaxSessionIdleTimeout();
        this.requestUri = requestUri;
        if (requestParameterMap == null) {
            this.requestParameterMap = Collections.emptyMap();
        } else {
            this.requestParameterMap = requestParameterMap;
        }
        this.queryString = queryString;
        this.userPrincipal = userPrincipal;
        this.httpSessionId = httpSessionId;
        this.negotiatedExtensions = negotiatedExtensions;
        if (subProtocol == null) {
            this.subProtocol = "";
        } else {
            this.subProtocol = subProtocol;
        }
        this.pathParameters = pathParameters;
        this.secure = secure;
        this.wsRemoteEndpoint.setEncoders(endpointConfig);
        this.endpointConfig = endpointConfig;

        this.userProperties.putAll(endpointConfig.getUserProperties());
        this.id = Long.toHexString(ids.getAndIncrement());

        InstanceManager instanceManager = webSocketContainer.getInstanceManager();
        if (instanceManager == null) {
            instanceManager = InstanceManagerBindings.get(applicationClassLoader);
        }
        if (instanceManager != null) {
            try {
                instanceManager.newInstance(localEndpoint);
            } catch (Exception e) {
                throw new DeploymentException(sm.getString("wsSession.instanceNew"), e);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("wsSession.created", id));
        }
    }


    @Override
    public WebSocketContainer getContainer() {
        checkState();
        return webSocketContainer;
    }


    @Override
    public void addMessageHandler(MessageHandler listener) {
        Class<?> target = Util.getMessageType(listener);
        doAddMessageHandler(target, listener);
    }


    @Override
    public <T> void addMessageHandler(Class<T> clazz, Partial<T> handler)
            throws IllegalStateException {
        doAddMessageHandler(clazz, handler);
    }


    @Override
    public <T> void addMessageHandler(Class<T> clazz, Whole<T> handler)
            throws IllegalStateException {
        doAddMessageHandler(clazz, handler);
    }


    @SuppressWarnings("unchecked")
    private void doAddMessageHandler(Class<?> target, MessageHandler listener) {
        checkState();

        // 需要解码器的消息处理程序可以映射到文本消息, 二进制消息.

        // 帧处理代码希望二进制消息处理程序接受ByteBuffer

        // 使用POJO消息处理程序包装器，因为它们被设计成用MessageHandler包装任意对象，并且可以同样容易地包装MessageHandler.

        Set<MessageHandlerResult> mhResults = Util.getMessageHandlers(target, listener,
                endpointConfig, this);

        for (MessageHandlerResult mhResult : mhResults) {
            switch (mhResult.getType()) {
            case TEXT: {
                if (textMessageHandler != null) {
                    throw new IllegalStateException(sm.getString("wsSession.duplicateHandlerText"));
                }
                textMessageHandler = mhResult.getHandler();
                break;
            }
            case BINARY: {
                if (binaryMessageHandler != null) {
                    throw new IllegalStateException(
                            sm.getString("wsSession.duplicateHandlerBinary"));
                }
                binaryMessageHandler = mhResult.getHandler();
                break;
            }
            case PONG: {
                if (pongMessageHandler != null) {
                    throw new IllegalStateException(sm.getString("wsSession.duplicateHandlerPong"));
                }
                MessageHandler handler = mhResult.getHandler();
                if (handler instanceof MessageHandler.Whole<?>) {
                    pongMessageHandler = (MessageHandler.Whole<PongMessage>) handler;
                } else {
                    throw new IllegalStateException(
                            sm.getString("wsSession.invalidHandlerTypePong"));
                }

                break;
            }
            default: {
                throw new IllegalArgumentException(
                        sm.getString("wsSession.unknownHandlerType", listener, mhResult.getType()));
            }
            }
        }
    }


    @Override
    public Set<MessageHandler> getMessageHandlers() {
        checkState();
        Set<MessageHandler> result = new HashSet<>();
        if (binaryMessageHandler != null) {
            result.add(binaryMessageHandler);
        }
        if (textMessageHandler != null) {
            result.add(textMessageHandler);
        }
        if (pongMessageHandler != null) {
            result.add(pongMessageHandler);
        }
        return result;
    }


    @Override
    public void removeMessageHandler(MessageHandler listener) {
        checkState();
        if (listener == null) {
            return;
        }

        MessageHandler wrapped = null;

        if (listener instanceof WrappedMessageHandler) {
            wrapped = ((WrappedMessageHandler) listener).getWrappedHandler();
        }

        if (wrapped == null) {
            wrapped = listener;
        }

        boolean removed = false;
        if (wrapped.equals(textMessageHandler) || listener.equals(textMessageHandler)) {
            textMessageHandler = null;
            removed = true;
        }

        if (wrapped.equals(binaryMessageHandler) || listener.equals(binaryMessageHandler)) {
            binaryMessageHandler = null;
            removed = true;
        }

        if (wrapped.equals(pongMessageHandler) || listener.equals(pongMessageHandler)) {
            pongMessageHandler = null;
            removed = true;
        }

        if (!removed) {
            // ISE现在. 如果ISE变成问题，可以忽略这个日志
            throw new IllegalStateException(
                    sm.getString("wsSession.removeHandlerFailed", listener));
        }
    }


    @Override
    public String getProtocolVersion() {
        checkState();
        return Constants.WS_VERSION_HEADER_VALUE;
    }


    @Override
    public String getNegotiatedSubprotocol() {
        checkState();
        return subProtocol;
    }


    @Override
    public List<Extension> getNegotiatedExtensions() {
        checkState();
        return negotiatedExtensions;
    }


    @Override
    public boolean isSecure() {
        checkState();
        return secure;
    }


    @Override
    public boolean isOpen() {
        return state == State.OPEN;
    }


    @Override
    public long getMaxIdleTimeout() {
        checkState();
        return maxIdleTimeout;
    }


    @Override
    public void setMaxIdleTimeout(long timeout) {
        checkState();
        this.maxIdleTimeout = timeout;
    }


    @Override
    public void setMaxBinaryMessageBufferSize(int max) {
        checkState();
        this.maxBinaryMessageBufferSize = max;
    }


    @Override
    public int getMaxBinaryMessageBufferSize() {
        checkState();
        return maxBinaryMessageBufferSize;
    }


    @Override
    public void setMaxTextMessageBufferSize(int max) {
        checkState();
        this.maxTextMessageBufferSize = max;
    }


    @Override
    public int getMaxTextMessageBufferSize() {
        checkState();
        return maxTextMessageBufferSize;
    }


    @Override
    public Set<Session> getOpenSessions() {
        checkState();
        return webSocketContainer.getOpenSessions(localEndpoint);
    }


    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        checkState();
        return remoteEndpointAsync;
    }


    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        checkState();
        return remoteEndpointBasic;
    }


    @Override
    public void close() throws IOException {
        close(new CloseReason(CloseCodes.NORMAL_CLOSURE, ""));
    }


    @Override
    public void close(CloseReason closeReason) throws IOException {
        doClose(closeReason, closeReason);
    }


    /**
     * WebSocket 1.0. Section 2.1.5.
     * 规范要求内部关闭方法, 本地端点接收一个1006超时.
     *
     * @param closeReasonMessage 传递到远程端点的关闭原因
     * @param closeReasonLocal   传递到本地端点的关闭原因
     */
    public void doClose(CloseReason closeReasonMessage, CloseReason closeReasonLocal) {
        // 双重检查锁. OK 因为状态是 volatile
        if (state != State.OPEN) {
            return;
        }

        synchronized (stateLock) {
            if (state != State.OPEN) {
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("wsSession.doClose", id));
            }
            try {
                wsRemoteEndpoint.setBatchingAllowed(false);
            } catch (IOException e) {
                log.warn(sm.getString("wsSession.flushFailOnClose"), e);
                fireEndpointOnError(e);
            }

            state = State.OUTPUT_CLOSED;

            sendCloseMessage(closeReasonMessage);
            fireEndpointOnClose(closeReasonLocal);
        }

        IOException ioe = new IOException(sm.getString("wsSession.messageFailed"));
        SendResult sr = new SendResult(ioe);
        for (FutureToSendHandler f2sh : futures.keySet()) {
            f2sh.onResult(sr);
        }
    }


    /**
     * 当接收到关闭消息时调用. 应该只发生一次.
     * 当ProtocolHandler需要强制关闭连接时发生协议错误，也会调用.
     *
     * @param closeReason 包含在接收到的关闭消息中的原因.
     */
    public void onClose(CloseReason closeReason) {

        synchronized (stateLock) {
            if (state != State.CLOSED) {
                try {
                    wsRemoteEndpoint.setBatchingAllowed(false);
                } catch (IOException e) {
                    log.warn(sm.getString("wsSession.flushFailOnClose"), e);
                    fireEndpointOnError(e);
                }
                if (state == State.OPEN) {
                    state = State.OUTPUT_CLOSED;
                    sendCloseMessage(closeReason);
                    fireEndpointOnClose(closeReason);
                }
                state = State.CLOSED;

                // 关闭套接字
                wsRemoteEndpoint.close();
            }
        }
    }

    private void fireEndpointOnClose(CloseReason closeReason) {

        // 触发 onClose 事件
        Throwable throwable = null;
        InstanceManager instanceManager = webSocketContainer.getInstanceManager();
        if (instanceManager == null) {
            instanceManager = InstanceManagerBindings.get(applicationClassLoader);
        }
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            localEndpoint.onClose(this, closeReason);
        } catch (Throwable t1) {
            ExceptionUtils.handleThrowable(t1);
            throwable = t1;
        } finally {
            if (instanceManager != null) {
                try {
                    instanceManager.destroyInstance(localEndpoint);
                } catch (Throwable t2) {
                    ExceptionUtils.handleThrowable(t2);
                    if (throwable == null) {
                        throwable = t2;
                    }
                }
            }
            t.setContextClassLoader(cl);
        }

        if (throwable != null) {
            fireEndpointOnError(throwable);
        }
    }



    private void fireEndpointOnError(Throwable throwable) {

        // 触发 onError事件
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            localEndpoint.onError(this, throwable);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    private void sendCloseMessage(CloseReason closeReason) {
        // 125是控制消息有效载荷的最大大小
        ByteBuffer msg = ByteBuffer.allocate(125);
        CloseCode closeCode = closeReason.getCloseCode();
        // CLOSED_ABNORMALLY should not be put on the wire
        if (closeCode == CloseCodes.CLOSED_ABNORMALLY) {
            // PROTOCOL_ERROR 可能好于GOING_AWAY
            msg.putShort((short) CloseCodes.PROTOCOL_ERROR.getCode());
        } else {
            msg.putShort((short) closeCode.getCode());
        }

        String reason = closeReason.getReasonPhrase();
        if (reason != null && reason.length() > 0) {
            appendCloseReasonWithTruncation(msg, reason);
        }
        msg.flip();
        try {
            wsRemoteEndpoint.sendMessageBlock(Constants.OPCODE_CLOSE, msg, true);
        } catch (IOException | WritePendingException e) {
            // 无法发送关闭消息. 关闭套接字，让调用方处理异常
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("wsSession.sendCloseFail", id), e);
            }
            wsRemoteEndpoint.close();
            // 在异常关闭的情况下，不发送一个关闭消息不是意外的 (通常是由于未能从客户端读取/写入而触发的).
            // 在这种情况下，不触发端点的错误处理
            if (closeCode != CloseCodes.CLOSED_ABNORMALLY) {
                localEndpoint.onError(this, e);
            }
        } finally {
            webSocketContainer.unregisterSession(localEndpoint, this);
        }
    }


    /**
     * 使用protected, 因此单元测试可以直接访问此方法.
     * 
     * @param msg 消息
     * @param reason 原因
     */
    protected static void appendCloseReasonWithTruncation(ByteBuffer msg, String reason) {
        // 一旦添加了关闭代码，原因短语的最大字节数就剩下123个字节.
        // 如果它被截断，那么需要注意确保在多字节的UTF-8字符中不截断字节.
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);

        if (reasonBytes.length <= 123) {
            // No need to truncate
            msg.put(reasonBytes);
        } else {
            // Need to truncate
            int remaining = 123 - ELLIPSIS_BYTES_LEN;
            int pos = 0;
            byte[] bytesNext = reason.substring(pos, pos + 1).getBytes(StandardCharsets.UTF_8);
            while (remaining >= bytesNext.length) {
                msg.put(bytesNext);
                remaining -= bytesNext.length;
                pos++;
                bytesNext = reason.substring(pos, pos + 1).getBytes(StandardCharsets.UTF_8);
            }
            msg.put(ELLIPSIS_BYTES);
        }
    }


    /**
     * 使会话知道 {@link FutureToSendHandler}, 如果会话在{@link FutureToSendHandler}完成之前关闭, 则需要强制关闭.
     * 
     * @param f2sh 处理程序
     */
    protected void registerFuture(FutureToSendHandler f2sh) {
        // 理想的, 此代码应该在stateLock 上同步, 以便根据连接的当前状态采取正确的操作.
        // 但是, 这里不能使用stateLock同步, 因为它可能会造成死锁. See BZ 61183.
        // 因此, 使用效率较低的方法.

        // Always register the future.
        futures.put(f2sh, f2sh);

        if (state == State.OPEN) {
            // 会话是打开的. Future已在打开的会话上注册. 正常处理继续.
            return;
        }

        // 会话关闭. 不确定有没有及时注册Future，以便在会话结束时进行处理.

        if (f2sh.isDone()) {
            // Future已经完成. 不知道Future 是否由I/O层正常完成, 或者 doClose()抛出错误.
            // 这不要紧. 这里没有别的事可做了.
            return;
        }

        // 会话关闭. 上次检查时，未来还没有完成.
        // 有一个小的时间窗口，意味着Future可能已经完成自上次检查. 还有一种可能性是，在会话结束之前，没有及时注册Future进行清理.
        // 尝试以错误结果完成Future, 确保Future完成, 并且任何等待它的客户端代码不会挂起.
        // 这是稍微低效的，因为Future可能在另一个线程中完成，或者另一个线程可能即将完成Future，但是这种情况需要在stateLock上同步 (see above).
        // Note: 如果做了多次尝试来完成 Future, 第二次和后续尝试被忽略.

        IOException ioe = new IOException(sm.getString("wsSession.messageFailed"));
        SendResult sr = new SendResult(ioe);
        f2sh.onResult(sr);
    }


    /**
     * 从跟踪实例集合中移除 {@link FutureToSendHandler}.
     * 
     * @param f2sh The handler
     */
    protected void unregisterFuture(FutureToSendHandler f2sh) {
        futures.remove(f2sh);
    }


    @Override
    public URI getRequestURI() {
        checkState();
        return requestUri;
    }


    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        checkState();
        return requestParameterMap;
    }


    @Override
    public String getQueryString() {
        checkState();
        return queryString;
    }


    @Override
    public Principal getUserPrincipal() {
        checkState();
        return userPrincipal;
    }


    @Override
    public Map<String, String> getPathParameters() {
        checkState();
        return pathParameters;
    }


    @Override
    public String getId() {
        return id;
    }


    @Override
    public Map<String, Object> getUserProperties() {
        checkState();
        return userProperties;
    }


    public Endpoint getLocal() {
        return localEndpoint;
    }


    public String getHttpSessionId() {
        return httpSessionId;
    }


    protected MessageHandler getTextMessageHandler() {
        return textMessageHandler;
    }


    protected MessageHandler getBinaryMessageHandler() {
        return binaryMessageHandler;
    }


    protected MessageHandler.Whole<PongMessage> getPongMessageHandler() {
        return pongMessageHandler;
    }


    protected void updateLastActive() {
        lastActive = System.currentTimeMillis();
    }


    protected void checkExpiration() {
        long timeout = maxIdleTimeout;
        if (timeout < 1) {
            return;
        }

        if (System.currentTimeMillis() - lastActive > timeout) {
            String msg = sm.getString("wsSession.timeout", getId());
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
            doClose(new CloseReason(CloseCodes.GOING_AWAY, msg),
                    new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg));
        }
    }


    private void checkState() {
        if (state == State.CLOSED) {
            /*
             * 按照RFC 6455, 当一个对等点发送和接收一个WebSocket关闭帧时，就认为关闭一个WebSocket连接.
             */
            throw new IllegalStateException(sm.getString("wsSession.closed", id));
        }
    }

    private static enum State {
        OPEN,
        OUTPUT_CLOSED,
        CLOSED
    }


    private WsFrameBase wsFrame;
    void setWsFrame(WsFrameBase wsFrame) {
        this.wsFrame = wsFrame;
    }


    /**
     * 暂停读取传入消息.
     */
    public void suspend() {
        wsFrame.suspend();
    }


    /**
     * 恢复传入消息的读取.
     */
    public void resume() {
        wsFrame.resume();
    }
}
