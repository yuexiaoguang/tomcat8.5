package javax.servlet;

import java.util.EventListener;

/**
 * 可以实现ServletRequestListener， 如果开发者希望请求进入或超出web组件范围时通知自己.
 * 请求定义为在每个Web应用程序中输入第一个servlet或过滤器时进入范围, 当退出最后一个servlet或链中的第一个过滤器时，超出范围.
 */
public interface ServletRequestListener extends EventListener {

    /**
     * 请求即将超出Web应用程序的范围.
     * 
     * @param sre 关于请求的信息
     */
    public void requestDestroyed (ServletRequestEvent sre);

    /**
     * 请求即将进入Web应用程序的范围.
     * 
     * @param sre 关于请求的信息
     */
    public void requestInitialized (ServletRequestEvent sre);
}
