package javax.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

public interface WebSocketContainer {

    /**
     * 获取异步发送消息的默认超时时间.
     * 
     * @return 当前默认超时时间，单位为毫秒. 非正值意味着不限制超时.
     */
    long getDefaultAsyncSendTimeout();

    /**
     * 设置异步发送消息的默认超时时间.
     * 
     * @param timeout 当前默认超时时间，单位为毫秒. 非正值意味着不限制超时.
     *
     * @deprecated 在Tomcat 9中被删除. 使用{@link #setDefaultAsyncSendTimeout(long)}
     */
    @Deprecated
    void setAsyncSendTimeout(long timeout);

    /**
     * 设置异步发送消息的默认超时时间.
     * 
     * @param timeout 默认超时时间，单位为毫秒. 非正值意味着不限制超时.
     */
    void setDefaultAsyncSendTimeout(long timeout);

    Session connectToServer(Object endpoint, URI path)
            throws DeploymentException, IOException;

    Session connectToServer(Class<?> annotatedEndpointClass, URI path)
            throws DeploymentException, IOException;

    /**
     * 创建一个连接到WebSocket的连接.
     *
     * @param endpoint 将处理服务器响应的端点实例
     * @param clientEndpointConfiguration 用于配置新连接
     * @param path WebSocket端点完整的URL连接
     *
     * @return 连接的WebSocket会话
     *
     * @throws DeploymentException  如果无法建立连接
     * @throws IOException 如果在试图建立连接时发生了I/O异常
     */
    Session connectToServer(Endpoint endpoint,
            ClientEndpointConfig clientEndpointConfiguration, URI path)
            throws DeploymentException, IOException;

    /**
     * 创建一个连接到WebSocket的连接.
     *
     * @param endpoint 处理服务器的响应的这个类的实例
     * @param clientEndpointConfiguration 用于配置新连接
     * @param path WebSocket端点完整的URL连接
     *
     * @return 连接的WebSocket会话
     *
     * @throws DeploymentException  如果无法建立连接
     * @throws IOException 如果在试图建立连接时发生了I/O异常
     */
    Session connectToServer(Class<? extends Endpoint> endpoint,
            ClientEndpointConfig clientEndpointConfiguration, URI path)
            throws DeploymentException, IOException;

    /**
     * 获取当前默认会话空闲超时时间.
     * 
     * @return 当前默认会话空闲超时时间（毫秒）. 零或负值表示不限制超时.
     */
    long getDefaultMaxSessionIdleTimeout();

    /**
     * 设置默认会话空闲超时时间.
     * 
     * @param timeout 默认会话空闲超时时间（毫秒）. 零或负值表示不限制超时.
     */
    void setDefaultMaxSessionIdleTimeout(long timeout);

    /**
     * 获取二进制消息的默认最大缓冲区大小.
     * 
     * @return 当前默认最大缓冲区大小，单位为字节
     */
    int getDefaultMaxBinaryMessageBufferSize();

    /**
     * 设置二进制消息的默认最大缓冲区大小.
     * 
     * @param max 默认最大缓冲区大小，字节
     */
    void setDefaultMaxBinaryMessageBufferSize(int max);

    /**
     * 获取文本消息的默认最大缓冲区大小.
     * 
     * @return 当前默认最大字符缓冲区大小
     */
    int getDefaultMaxTextMessageBufferSize();

    /**
     * 设置文本消息的默认最大缓冲区大小.
     * 
     * @param max 字符的默认最大缓冲区大小
     */
    void setDefaultMaxTextMessageBufferSize(int max);

    /**
     * 获取已安装的扩展名.
     * 
     * @return 这个WebSocket实现支持的一组扩展.
     */
    Set<Extension> getInstalledExtensions();
}
