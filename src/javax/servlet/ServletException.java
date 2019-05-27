package javax.servlet;

/**
 * 定义servlet遇到困难时可以抛出的一般异常.
 */
public class ServletException extends Exception {

    private static final long serialVersionUID = 1L;

    public ServletException() {
        super();
    }

    /**
     * @param message 指定异常消息的文本
     */
    public ServletException(String message) {
        super(message);
    }

    /**
     * @param message 异常消息的文本
     * @param rootCause 异常
     */
    public ServletException(String message, Throwable rootCause) {
        super(message, rootCause);
    }

    /** 
     * @param rootCause 异常
     */
    public ServletException(Throwable rootCause) {
        super(rootCause);
    }

    /**
     * 返回导致此servlet异常的异常.
     */
    public Throwable getRootCause() {
        return getCause();
    }
}
