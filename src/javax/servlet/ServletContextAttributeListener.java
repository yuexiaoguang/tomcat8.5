package javax.servlet;

import java.util.EventListener;

/**
 * 此接口的实现接收Web应用程序的servlet上下文中属性列表的更改的通知. 接收通知事件, 必须在Web应用程序的部署描述符中配置实现类.
 */
public interface ServletContextAttributeListener extends EventListener {
    /**
     * 向servlet上下文添加新属性的通知. 添加属性后调用.
     * 
     * @param scae 关于新属性的信息
     */
    public void attributeAdded(ServletContextAttributeEvent scae);

    /**
     * 从servlet上下文中删除现有属性的通知. 在删除属性后调用.
     * 
     * @param scae 关于已删除属性的信息
     */
    public void attributeRemoved(ServletContextAttributeEvent scae);

    /**
     * 通知servlet上下文中的属性已被替换. 属性被替换后调用.
     * 
     * @param scae 替换属性的信息
     */
    public void attributeReplaced(ServletContextAttributeEvent scae);
}
