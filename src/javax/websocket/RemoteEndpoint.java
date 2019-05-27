package javax.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;


public interface RemoteEndpoint {

    interface Async extends RemoteEndpoint {

        /**
         * 获取异步发送消息的超时时间（以毫秒为单位）.
         * 默认值由{@link WebSocketContainer#getDefaultAsyncSendTimeout()}确定.
         * 
         * @return  发送消息的超时时间. 非正值意味着不限制超时.
         */
        long getSendTimeout();

        /**
         * 设置异步发送消息的超时时间（以毫秒为单位）.
         * 默认值由{@link WebSocketContainer#getDefaultAsyncSendTimeout()}确定.
         * 
         * @param timeout   发送消息的超时时间. 非正值意味着不限制超时.
         */
        void setSendTimeout(long timeout);

        /**
         * 异步发送消息, 使用SendHandler在发送消息时向客户端发出信号.
         * 
         * @param text          要发送的文本消息
         * @param completion    用于发送消息时向客户端发出信号
         */
        void sendText(String text, SendHandler completion);

        /**
         * 异步发送消息, 使用Future在发送消息时向客户端发出信号.
         * 
         * @param text          要发送的文本消息
         * @return 发送消息时发出信号的Future.
         */
        Future<Void> sendText(String text);

        /**
         * 异步发送消息, 使用Future在发送消息时向客户端发出信号.
         * 
         * @param data          要发送的文本消息
         * 
         * @return 发送消息时发出信号的Future.
         * @throws IllegalArgumentException 如果{@code data} 是 {@code null}.
         */
        Future<Void> sendBinary(ByteBuffer data);

        /**
         * 异步发送消息, 使用SendHandler 在发送消息时向客户端发出信号.
         * 
         * @param data          要发送的文本消息
         * @param completion    用于发送消息时向客户端发出信号
         * 
         * @throws IllegalArgumentException 如果{@code data}或{@code completion}是{@code null}.
         */
        void sendBinary(ByteBuffer data, SendHandler completion);

        /**
         * 将对象编码为消息并异步发送它, 使用Future在发送消息时向客户端发出信号.
         * 
         * @param obj           要发送的对象.
         * 
         * @return 发送消息时发出信号的Future.
         * @throws IllegalArgumentException 如果{@code obj}是{@code null}.
         */
        Future<Void> sendObject(Object obj);

        /**
         * 将对象编码为消息并异步发送它, 使用SendHandler在发送消息时向客户端发出信号.
         * 
         * @param obj           要发送的对象.
         * @param completion    用于发送消息时向客户端发出信号
         * 
         * @throws IllegalArgumentException 如果{@code obj}或{@code completion}是{@code null}.
         */
        void sendObject(Object obj, SendHandler completion);

    }

    interface Basic extends RemoteEndpoint {

        /**
         * 发送消息, 阻塞直到发送消息为止.
         * 
         * @param text  要发送的文本消息.
         * 
         * @throws IllegalArgumentException 如果{@code text}是{@code null}.
         * @throws IOException 如果在发送消息期间发生I/O错误.
         */
        void sendText(String text) throws IOException;

        /**
         * 发送消息, 阻塞直到发送消息为止.
         * 
         * @param data  要发送的二进制消息
         * 
         * @throws IllegalArgumentException 如果{@code data}是{@code null}.
         * @throws IOException 如果在发送消息期间发生I/O错误.
         */
        void sendBinary(ByteBuffer data) throws IOException;

        /**
         * 将文本消息的一部分发送到远程端点.
         * 一旦消息的第一部分被发送, 在发送此消息的所有剩余部分之前，不可发送其他文本或二进制消息.
         *
         * @param fragment  要发送的部分消息
         * @param isLast    <code>true</code>如果这是消息的最后一部分, 否则<code>false</code>
         * 
         * @throws IllegalArgumentException 如果{@code fragment}是{@code null}.
         * @throws IOException 如果在发送消息期间发生I/O错误.
         */
        void sendText(String fragment, boolean isLast) throws IOException;

        /**
         * 将二进制消息的一部分发送到远程端点.
         * 一旦消息的第一部分被发送, 在发送此消息的所有剩余部分之前，不可发送其他文本或二进制消息.
         *
         * @param partialByte   要发送的部分消息
         * @param isLast        <code>true</code>如果这是消息的最后一部分, 否则<code>false</code>
         * @throws IllegalArgumentException 如果{@code partialByte}是{@code null}.
         * @throws IOException 如果在发送消息期间发生I/O错误.
         */
        void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException;

        OutputStream getSendStream() throws IOException;

        Writer getSendWriter() throws IOException;

        /**
         * 将对象编码为消息并将其发送到远程端点.
         * 
         * @param data  要发送的对象.
         * 
         * @throws EncodeException 如果编码{@code data}对象为一个websocket 消息时出现错误.
         * @throws IllegalArgumentException 如果{@code data}是{@code null}.
         * @throws IOException 如果在发送消息期间发生I/O错误.
         */
        void sendObject(Object data) throws IOException, EncodeException;

    }
    /**
     * 启用或禁用此端点的传出消息批处理.
     * 如果批处理被禁用，当它以前启用时，那么这个方法将阻塞直到当前批处理消息已经写入.
     *
     * @param batchingAllowed   新设置
     * 
     * @throws IOException      当调用{@link #flushBatch()}时，结果发生了改变
     */
    void setBatchingAllowed(boolean batchingAllowed) throws IOException;

    /**
     * 获取端点的当前批处理状态.
     *
     * @return <code>true</code> 如果启用批处理, 否则<code>false</code>.
     */
    boolean getBatchingAllowed();

    /**
     * 刷新当前批处理消息到远程端点. 此方法将阻塞直到刷新完成.
     *
     * @throws IOException 如果刷新时发生I/O错误
     */
    void flushBatch() throws IOException;

    /**
     * 发送ping消息，阻塞直到消息被发送为止.
     * 注意，如果消息是异步发送, 此方法将阻塞直到该消息和此ping发送完成.
     *
     * @param applicationData   ping消息的有效负载
     *
     * @throws IOException 如果在发送ping时发生I/O错误
     * @throws IllegalArgumentException 如果applicationData太大(最大 125 bytes)
     */
    void sendPing(ByteBuffer applicationData)
            throws IOException, IllegalArgumentException;

    /**
     * 发送 pong 消息，阻塞直到消息发送完成.
     * 注意，如果消息是异步发送, 此方法将阻塞直到该消息和此pong发送完成.
     *
     * @param applicationData   pong消息的有效负载
     *
     * @throws IOException 如果在发送pong时发生I/O错误
     * @throws IllegalArgumentException 如果applicationData太大(最大 125 bytes)
     */
    void sendPong(ByteBuffer applicationData)
            throws IOException, IllegalArgumentException;
}

