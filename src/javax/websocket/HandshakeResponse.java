package javax.websocket;

import java.util.List;
import java.util.Map;

public interface HandshakeResponse {

    /**
     * 接收HTTP header的WebSocket的名称.
     */
    public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";

    Map<String,List<String>> getHeaders();
}
