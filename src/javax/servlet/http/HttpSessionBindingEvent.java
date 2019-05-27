package javax.servlet.http;

/**
 * 这种类型的事件都可以发送到一个实现了{@link HttpSessionBindingListener}的对象，当它从会话中绑定或解绑时,
 * 或者到一个已在部署描述符中配置的{@link HttpSessionAttributeListener}，当属性在会话中被绑定、解绑或替换时.
 * <p>
 * 会话绑定对象，通过调用<code>HttpSession.setAttribute</code> ，并通过解绑对象，通过调用<code>HttpSession.removeAttribute</code>.
 */
public class HttpSessionBindingEvent extends HttpSessionEvent {

    private static final long serialVersionUID = 1L;

    /* 对象被绑定或解绑的名称 */
    private final String name;

    /* 绑定或解绑的对象 */
    private final Object value;

    /**
     * 构造一个事件，通知对象在会话中它被绑定或解绑. 要接收事件, 对象必须实现{@link HttpSessionBindingListener}.
     *
     * @param session 对象被绑定或解绑的会话
     * @param name 对象被绑定或解绑的名称
     */
    public HttpSessionBindingEvent(HttpSession session, String name) {
        super(session);
        this.name = name;
        this.value = null;
    }

    /**
     * @param session 对象被绑定或解绑的会话
     * @param name 对象被绑定或解绑的名称
     * @param value 被绑定或解绑的对象
     */
    public HttpSessionBindingEvent(HttpSession session, String name,
            Object value) {
        super(session);
        this.name = name;
        this.value = value;
    }

    /**
     * 获取更改的会话.
     * 
     * @return 更改的会话
     */
    @Override
    public HttpSession getSession() {
        return super.getSession();
    }

    /**
     * 返回从会话中绑定或解绑的属性的名称.
     *
     * @return 属性的名称
     */
    public String getName() {
        return name;
    }

    /**
     * 返回已添加、删除或替换的属性的值.
     *
     * @return 如果属性被添加（或绑定）, 这是属性的值.
     * 			如果属性被移除(或解绑), 这是已删除属性的值.
     * 			如果属性被替换, 这是属性的旧值.
     */
    public Object getValue() {
        return this.value;
    }
}
