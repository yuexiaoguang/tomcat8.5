package org.apache.coyote;

/**
 * 需要指示失败时使用, 但是 (Servlet) API 不声明任何适当的异常.
 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocolException() {
        super();
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(Throwable cause) {
        super(cause);
    }
}
