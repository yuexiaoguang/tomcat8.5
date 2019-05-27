package org.apache.coyote.http2;

/**
 * 发生 HTTP/2 连接错误时.
 */
public class ConnectionException extends Http2Exception {

    private static final long serialVersionUID = 1L;

    ConnectionException(String msg, Http2Error error) {
        super(msg, error);
    }


    ConnectionException(String msg, Http2Error error, Throwable cause) {
        super(msg, error, cause);
    }
}
