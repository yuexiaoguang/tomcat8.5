package javax.servlet;

/**
 * 用于通知应用程序中servlet请求属性的更改.
 */
public class ServletRequestAttributeEvent extends ServletRequestEvent {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final Object value;

    /**
     * @param sc 发送事件的ServletContext.
     * @param request 发送事件的ServletRequest.
     * @param name 请求属性的名称.
     * @param value 请求属性的值.
     */
    public ServletRequestAttributeEvent(ServletContext sc,
            ServletRequest request, String name, Object value) {
        super(sc, request);
        this.name = name;
        this.value = value;
    }

    /**
     * 返回ServletRequest上修改的属性的名称.
     *
     * @return 已更改请求属性的名称
     */
    public String getName() {
        return this.name;
    }

    /**
     * 返回已添加、删除或替换的属性的值.
     * 如果添加了属性, 这是属性的值.
     * 如果属性被移除, 这是已删除属性的值.
     * 如果属性被替换, 这是属性的旧值.
     *
     * @return 已更改请求属性的值
     */
    public Object getValue() {
        return this.value;
    }
}
