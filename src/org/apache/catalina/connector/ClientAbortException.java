package org.apache.catalina.connector;

import java.io.IOException;

/**
 * 包装一个IOException ，确认它是由远程客户端请求中止引起的.
 */
public final class ClientAbortException extends IOException {

    private static final long serialVersionUID = 1L;


    //------------------------------------------------------------ Constructors

    public ClientAbortException() {
        super();
    }


    public ClientAbortException(String message) {
        super(message);
    }


    public ClientAbortException(Throwable throwable) {
        super(throwable);
    }


    public ClientAbortException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
