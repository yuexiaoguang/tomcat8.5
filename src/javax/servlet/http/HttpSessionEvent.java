package javax.servlet.http;

/**
 * 表示Web应用程序中会话更改的事件通知的类.
 */
public class HttpSessionEvent extends java.util.EventObject {
    private static final long serialVersionUID = 1L;

    /**
     * @param source    更改发生的HTTP会话
     */
    public HttpSessionEvent(HttpSession source) {
        super(source);
    }

    /**
     * 获取更改的会话.
     *
     * @return 更改的会话
     */
    public HttpSession getSession() {
        return (HttpSession) super.getSource();
    }
}
