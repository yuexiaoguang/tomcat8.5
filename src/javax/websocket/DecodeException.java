package javax.websocket;

import java.nio.ByteBuffer;

public class DecodeException extends Exception {

    private static final long serialVersionUID = 1L;

    private ByteBuffer bb;
    private String encodedString;

    public DecodeException(ByteBuffer bb, String message, Throwable cause) {
        super(message, cause);
        this.bb = bb;
    }

    public DecodeException(String encodedString, String message,
            Throwable cause) {
        super(message, cause);
        this.encodedString = encodedString;
    }

    public DecodeException(ByteBuffer bb, String message) {
        super(message);
        this.bb = bb;
    }

    public DecodeException(String encodedString, String message) {
        super(message);
        this.encodedString = encodedString;
    }

    public ByteBuffer getBytes() {
        return bb;
    }

    public String getText() {
        return encodedString;
    }
}
