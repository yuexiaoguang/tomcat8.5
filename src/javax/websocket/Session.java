package javax.websocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Session extends Closeable {

    /**
     * 获取创建此会话的容器.
     * 
     * @return 创建此会话的容器.
     */
    WebSocketContainer getContainer();

    /**
     * 为传入的消息注册一个 {@link MessageHandler}.
     * 每个消息类型(text, binary, pong)只能注册一个{@link MessageHandler}. 消息类型将在运行时从提供的{@link MessageHandler}实例派生.
     * 并非总是可以做到这一点，因此最好使用{@link #addMessageHandler(Class, javax.websocket.MessageHandler.Partial)}
     * 或
     * {@link #addMessageHandler(Class, javax.websocket.MessageHandler.Whole)}.
     *
     * @param handler   传入消息的消息处理程序
     *
     * @throws IllegalStateException  如果已经为关联消息类型注册了消息处理程序
     */
    void addMessageHandler(MessageHandler handler) throws IllegalStateException;

    Set<MessageHandler> getMessageHandlers();

    void removeMessageHandler(MessageHandler listener);

    String getProtocolVersion();

    String getNegotiatedSubprotocol();

    List<Extension> getNegotiatedExtensions();

    boolean isSecure();

    boolean isOpen();

    /**
     * 获取此会话的空闲超时时间.
     * 
     * @return 此会话的当前空闲超时时间，以毫秒为单位. 零或负值表示不限制超时.
     */
    long getMaxIdleTimeout();

    /**
     * 设置此会话的空闲超时时间.
     * 
     * @param timeout 此会话的空闲超时时间，以毫秒为单位. 零或负值表示不限制超时.
     */
    void setMaxIdleTimeout(long timeout);

    /**
     * 为二进制消息设置当前最大缓冲区大小.
     * 
     * @param max 新的最大缓冲区大小为字节
     */
    void setMaxBinaryMessageBufferSize(int max);

    /**
     * 获取当前二进制消息的最大缓冲区大小.
     * 
     * @return 当前最大缓冲区大小，单位为字节
     */
    int getMaxBinaryMessageBufferSize();

    /**
     * 设置文本消息的最大缓冲区大小.
     * 
     * @param max 字符的最大缓冲区大小.
     */
    void setMaxTextMessageBufferSize(int max);

    /**
     * 获取文本消息的最大缓冲区大小.
     * 
     * @return 字符的最大缓冲区大小.
     */
    int getMaxTextMessageBufferSize();

    RemoteEndpoint.Async getAsyncRemote();

    RemoteEndpoint.Basic getBasicRemote();

    /**
     * 为会话提供唯一标识符. 不应该依靠安全随机源生成此标识符.
     * 
     * @return 会话的唯一标识符.
     */
    String getId();

    /**
     * 关闭与远程端点的连接，使用{@link javax.websocket.CloseReason.CloseCodes#NORMAL_CLOSURE}.
     *
     * @throws IOException 如果出现I/O错误，在WebSocket会话关闭期间.
     */
    @Override
    void close() throws IOException;


    /**
     * 关闭与远程端点的连接，使用指定代码和原因.
     * 
     * @param closeReason WebSocket会话关闭的原因.
     *
     * @throws IOException 如果出现I/O错误，在WebSocket会话关闭期间.
     */
    void close(CloseReason closeReason) throws IOException;

    URI getRequestURI();

    Map<String, List<String>> getRequestParameterMap();

    String getQueryString();

    Map<String,String> getPathParameters();

    Map<String,Object> getUserProperties();

    Principal getUserPrincipal();

    /**
     * 获取与此会话相同的本地端点关联的打开的会话集合.
     *
     * @return 此会话关联的本地端点的当前开放的会话集合.
     */
    Set<Session> getOpenSessions();

    /**
     * 为部分传入的消息注册一个{@link MessageHandler}.
     * 每个消息类型(text, binary, pong 消息从不呈现为部分消息)只能注册一个{@link MessageHandler}.
     *
     * @param <T>       给定处理程序用于的消息类型
     * @param clazz     实现T的Class
     * @param handler   传入消息的消息处理程序
     *
     * @throws IllegalStateException  如果已经为关联消息类型注册了消息处理程序
     *
     * @since WebSocket 1.1
     */
    <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler)
            throws IllegalStateException;

    /**
     * 为整个传入的消息注册一个{@link MessageHandler}. Only
     * 每个消息类型(text, binary, pong)只能注册一个{@link MessageHandler}.
     *
     * @param <T>       给定处理程序用于的消息类型
     * @param clazz     实现T的Class
     * @param handler   传入消息的消息处理程序
     *
     * @throws IllegalStateException  如果已经为关联消息类型注册了消息处理程序
     *
     * @since WebSocket 1.1
     */
    <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler)
            throws IllegalStateException;
}
