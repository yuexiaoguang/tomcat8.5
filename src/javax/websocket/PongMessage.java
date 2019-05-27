package javax.websocket;

import java.nio.ByteBuffer;

/**
 * 表示WebSocket Pong消息，并通过消息处理程序用来启用应用程序来处理他们发送的Ping的响应.
 */
public interface PongMessage {
    /**
     * 获取Pong消息的数据的有效负载.
     */
    ByteBuffer getApplicationData();
}
