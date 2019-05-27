package javax.servlet.jsp;

/**
 * Tag Handler表明一些不可恢复的错误.
 * 此错误将被JSP页面的顶层捕获，并将导致一个错误页.
 */
public class JspTagException extends JspException {

    private static final long serialVersionUID = 1L;

    /**
     * @param msg 错误详情
     */
    public JspTagException(String msg) {
        super(msg);
    }

    public JspTagException() {
        super();
    }

    /**
     * @param message 错误详情
     * @param rootCause 
     * @since 2.0
     */
    public JspTagException(String message, Throwable rootCause) {
        super(message, rootCause);
    }

    /**
     * <p>
     * 这个方法调用<code>Throwable</code>异常上的<code>getLocalizedMessage</code>方法获取本地化的异常消息.
     * 如果子类化<code>JspTagException</code>, 可以重写此方法以创建针对特定区域设置的异常消息.
     *
     * @param rootCause 
     * @since 2.0
     */
    public JspTagException(Throwable rootCause) {
        super(rootCause);
    }
}
