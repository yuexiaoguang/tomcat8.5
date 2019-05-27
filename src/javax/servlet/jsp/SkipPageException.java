package javax.servlet.jsp;

/**
 * 指示调用页面必须停止计算的异常.
 * 由简单的标记处理程序抛出，表示页面的剩余部分不能被计算. 在一个标记调用另一个的情况下，结果被传播回页面(标签文件的情况也是如此).
 * 其效果相似于Classic Tag Handler从doEndTag()返回 Tag.SKIP_PAGE. Jsp Fragment也会抛出这个异常.
 * 不应在JSP页面或标签文件中手动抛出此异常 - 行为是未定义的. 该异常将被抛出，在SimpleTag处理器和JSP片段中.
 */
public class SkipPageException extends JspException {

    private static final long serialVersionUID = 1L;

    public SkipPageException() {
        super();
    }

    public SkipPageException(String message) {
        super(message);
    }

    public SkipPageException(String message, Throwable rootCause) {
        super(message, rootCause);
    }

    public SkipPageException(Throwable rootCause) {
        super(rootCause);
    }
}
