package org.apache.coyote.http2;

/**
 * 如果HPACK 压缩上下文损坏. 这时候, 连接必须关闭.
 */
public class HpackException extends Exception {

    private static final long serialVersionUID = 1L;

    public HpackException(String message, Throwable cause) {
        super(message, cause);
    }
    public HpackException(String message) {
        super(message);
    }
    public HpackException() {
        super();
    }
}
