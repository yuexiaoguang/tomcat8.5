package org.apache.tomcat.websocket;

public enum TransformationResult {
    /**
     * 在WebSocket框架被完全读取之前，已到达可用数据的末尾.
     */
    UNDERFLOW,

    /**
     * 所提供的目标缓冲区在可以从WebSocket 框架中处理所有可用数据之前被填充.
     */
    OVERFLOW,

    /**
     * 到达WebSocket 框架的末尾，并将来自该框架的所有数据处理到提供的目标缓冲区中.
     */
    END_OF_FRAME
}
