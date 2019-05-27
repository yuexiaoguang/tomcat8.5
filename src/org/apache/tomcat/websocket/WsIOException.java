package org.apache.tomcat.websocket;

import java.io.IOException;

import javax.websocket.CloseReason;

/**
 * 允许 WebSocket 实现抛出 {@link IOException}, 该异常包含一个特定于可以传递给客户端的错误的 {@link CloseReason}.
 */
public class WsIOException extends IOException {

    private static final long serialVersionUID = 1L;

    private final CloseReason closeReason;

    public WsIOException(CloseReason closeReason) {
        this.closeReason = closeReason;
    }

    public CloseReason getCloseReason() {
        return closeReason;
    }
}
