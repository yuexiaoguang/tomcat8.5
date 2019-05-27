package javax.el;

/**
 * 表示表达式求值期间可能出现的任何异常条件.
 */
public class ELException extends RuntimeException {

    private static final long serialVersionUID = -6228042809457459161L;

    public ELException() {
        super();
    }

    /**
     * @param message 详细信息
     */
    public ELException(String message) {
        super(message);
    }

    /**
     * @param cause 此异常的起源原因
     */
    public ELException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message 详细信息
     * @param cause 此异常的起源原因
     */
    public ELException(String message, Throwable cause) {
        super(message, cause);
    }
}
