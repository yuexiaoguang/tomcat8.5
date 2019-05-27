package javax.servlet.http;

import java.util.EventListener;

/**
 * 以便获得此Web应用程序中会话属性列表更改的通知.
 */
public interface HttpSessionAttributeListener extends EventListener {

    /**
     * 属性已添加到会话中. 添加属性后调用.
     *
     * @param se 关于添加属性的信息
     */
    public void attributeAdded(HttpSessionBindingEvent se);

    /**
     * 从会话中删除属性. 在删除属性后调用.
     *
     * @param se 关于已删除属性的信息
     */
    public void attributeRemoved(HttpSessionBindingEvent se);

    /**
     * 在会话中替换属性. 属性被替换后调用.
     *
     * @param se 替换属性的信息
     */
    public void attributeReplaced(HttpSessionBindingEvent se);
}
