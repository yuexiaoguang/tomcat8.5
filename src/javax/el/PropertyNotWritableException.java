package javax.el;

public class PropertyNotWritableException extends ELException {

    private static final long serialVersionUID = 827987155471214717L;

    public PropertyNotWritableException() {
        super();
    }

    public PropertyNotWritableException(String message) {
        super(message);
    }

    public PropertyNotWritableException(Throwable cause) {
        super(cause);
    }

    public PropertyNotWritableException(String message, Throwable cause) {
        super(message, cause);
    }
}
