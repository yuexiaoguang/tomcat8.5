package javax.servlet;

import java.util.EventListener;

/**
 * 如果开发者获得请求属性修改的通知，可以实现这个 ServletRequestAttributeListener.
 * 当请求在监听器注册的Web应用程序的范围内时，将生成通知. 请求定义为在每个Web应用程序中输入第一个servlet或过滤器时进入范围, 当退出最后一个servlet或链中的第一个过滤器时，超出范围.
 */
public interface ServletRequestAttributeListener extends EventListener {
    /**
     * 向servlet请求添加新属性的通知. 添加属性后调用.
     * 
     * @param srae 关于新请求属性的信息
     */
    public void attributeAdded(ServletRequestAttributeEvent srae);

    /**
     * 从servlet请求中删除现有属性的通知. 在删除属性后调用.
     * 
     * @param srae 关于已删除请求属性的信息
     */
    public void attributeRemoved(ServletRequestAttributeEvent srae);

    /**
     * 在servlet请求中替换属性的通知. 属性被替换后调用.
     * 
     * @param srae 关于替换请求属性的信息
     */
    public void attributeReplaced(ServletRequestAttributeEvent srae);
}

