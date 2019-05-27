package javax.servlet;

/**
 * 这是通知Web应用程序servlet上下文属性更改的事件类.
 */
public class ServletContextAttributeEvent extends ServletContextEvent {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final Object value;

    /**
     * @param source 这个属性事件关联的ServletContext
     * @param name   servlet上下文属性的名称
     * @param value  servlet上下文属性的值
     */
    public ServletContextAttributeEvent(ServletContext source, String name, Object value) {
        super(source);
        this.name = name;
        this.value = value;
    }

    /**
     * 返回ServletContext中改变的属性的名称.
     */
    public String getName() {
        return this.name;
    }

    /**
     * 返回已添加、删除或替换的属性的值.
     *
     * @return 如果添加了属性，则为属性的值.
     *         如果属性被移除，这就是被删除属性的值.
     *         如果属性被替换，则为属性的旧值.
     */
    public Object getValue() {
        return this.value;
    }
}
