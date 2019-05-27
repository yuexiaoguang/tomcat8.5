package javax.servlet;

/**
 * 这是用于通知Web应用程序的servlet上下文的更改的事件类.
 */
public class ServletContextEvent extends java.util.EventObject {

    private static final long serialVersionUID = 1L;

    /**
     * @param source 发送事件的ServletContext.
     */
    public ServletContextEvent(ServletContext source) {
        super(source);
    }

    /**
     * 返回修改的ServletContext.
     *
     * @return 发送事件的ServletContext.
     */
    public ServletContext getServletContext() {
        return (ServletContext) super.getSource();
    }
}
