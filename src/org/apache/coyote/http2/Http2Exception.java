package org.apache.coyote.http2;

public abstract class Http2Exception extends Exception {

    private static final long serialVersionUID = 1L;

    private final Http2Error error;


    Http2Exception(String msg, Http2Error error) {
        super(msg);
        this.error = error;
    }


    Http2Exception(String msg, Http2Error error, Throwable cause) {
        super(msg, cause);
        this.error = error;
    }


    Http2Error getError() {
        return error;
    }
}
