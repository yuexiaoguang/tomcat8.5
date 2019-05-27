package javax.servlet;

/**
 * 这类事件表明一个ServletRequest生命周期事件. 事件的源是这个web应用的 ServletContext.
 */
public class ServletRequestEvent extends java.util.EventObject {
    private static final long serialVersionUID = 1L;

    private final transient ServletRequest request;

    /**
     * @param sc Web应用的ServletContext.
     * @param request 发送事件的ServletRequest.
     */
    public ServletRequestEvent(ServletContext sc, ServletRequest request) {
        super(sc);
        this.request = request;
    }

    /**
     * 获取关联的ServletRequest.
     * 
     * @return 正在变化的ServletRequest.
     */
    public ServletRequest getServletRequest() {
        return this.request;
    }

    /**
     * 获取关联的ServletContext.
     * 
     * @return 正在变化的ServletContext.
     */
    public ServletContext getServletContext() {
        return (ServletContext) super.getSource();
    }
}
