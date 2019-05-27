package javax.el;

public class MethodNotFoundException extends ELException {

    private static final long serialVersionUID = -3631968116081480328L;

    public MethodNotFoundException() {
        super();
    }

    public MethodNotFoundException(String message) {
        super(message);
    }

    public MethodNotFoundException(Throwable cause) {
        super(cause);
    }

    public MethodNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
