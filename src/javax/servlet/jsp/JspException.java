package javax.servlet.jsp;

/**
 * JSP引擎已知的通用异常; 捕获JspException将导致errorpage机制的调用.
 */
public class JspException extends Exception {

    private static final long serialVersionUID = 1L;


    public JspException() {
        // NOOP
    }

    public JspException(String msg) {
        super(msg);
    }

    public JspException(String message, Throwable cause) {
        super(message, cause);
    }


    public JspException(Throwable cause) {
        super(cause);
    }


    /**
     * @deprecated As of JSP 2.1, replaced by <code>java.lang.Throwable.getCause()</code>
     */
    @SuppressWarnings("dep-ann") // TCK signature test fails with annotation
    public Throwable getRootCause() {
        return getCause();
    }
}
