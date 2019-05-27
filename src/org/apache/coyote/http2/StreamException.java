package org.apache.coyote.http2;

/**
 * 发生 HTTP/2 流错误.
 */
public class StreamException extends Http2Exception {

    private static final long serialVersionUID = 1L;

    private final int streamId;

    public StreamException(String msg, Http2Error error, int streamId) {
        super(msg, error);
        this.streamId = streamId;
    }


    public int getStreamId() {
        return streamId;
    }
}
