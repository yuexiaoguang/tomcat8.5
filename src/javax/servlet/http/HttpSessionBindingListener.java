package javax.servlet.http;

import java.util.EventListener;

/**
 * 使对象在会话绑定或未绑定时通知它.
 * 通过一个{@link HttpSessionBindingEvent}对象来通知.
 * 这可能是由于一个Servlet程序员显式从一个会话解绑属性，因为会话无效，或由于会话超时.
 */
public interface HttpSessionBindingListener extends EventListener {

    /**
     * 通知对象它正在绑定到会话并标识会话.
     *
     * @param event 标识会话的事件
     */
    public void valueBound(HttpSessionBindingEvent event);

    /**
     * 通知对象它从会话中解绑，并标识会话.
     *
     * @param event 标识会话的事件
     */
    public void valueUnbound(HttpSessionBindingEvent event);
}
