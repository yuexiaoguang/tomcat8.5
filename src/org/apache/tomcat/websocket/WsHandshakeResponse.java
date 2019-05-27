package org.apache.tomcat.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.websocket.HandshakeResponse;

import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;

/**
 * 表示对Web套接字握手的响应.
 */
public class WsHandshakeResponse implements HandshakeResponse {

    private final Map<String,List<String>> headers = new CaseInsensitiveKeyMap<>();


    public WsHandshakeResponse() {
    }


    public WsHandshakeResponse(Map<String,List<String>> headers) {
        for (Entry<String,List<String>> entry : headers.entrySet()) {
            if (this.headers.containsKey(entry.getKey())) {
                this.headers.get(entry.getKey()).addAll(entry.getValue());
            } else {
                List<String> values = new ArrayList<>(entry.getValue());
                this.headers.put(entry.getKey(), values);
            }
        }
    }


    @Override
    public Map<String,List<String>> getHeaders() {
        return headers;
    }
}
