package org.apache.tomcat.websocket;

import java.io.IOException;

public class ReadBufferOverflowException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int minBufferSize;

    public ReadBufferOverflowException(int minBufferSize) {
        this.minBufferSize = minBufferSize;
    }

    public int getMinBufferSize() {
        return minBufferSize;
    }
}
