package javax.websocket.server;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 表示请求升级到WebSocket的HTTP请求.
 */
public interface HandshakeRequest {

    static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    static final String SEC_WEBSOCKET_EXTENSIONS= "Sec-WebSocket-Extensions";

    Map<String,List<String>> getHeaders();

    Principal getUserPrincipal();

    URI getRequestURI();

    boolean isUserInRole(String role);

    /**
     * 获取与此请求关联的HTTP会话对象. 对象用于避免对servlet API的直接依赖.
     * 
     * @return 这个请求关联的javax.servlet.http.HttpSession对象.
     */
    Object getHttpSession();

    Map<String, List<String>> getParameterMap();

    String getQueryString();
}
