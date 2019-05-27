package javax.servlet;

import java.util.EventListener;

/**
 * 这个接口的实现接收关于Web应用程序servlet上下文的更改的通知. 
 * 为了接收通知事件, 必须在Web应用程序的部署描述符中配置实现类.
 */
public interface ServletContextListener extends EventListener {

    /**
     * Web应用程序初始化过程正在启动的通知.
     * 所有的ServletContextListener都被通知上下文初始化，在Web应用程序中的任何过滤器或servlet初始化之前.
     * 
     * @param sce ServletContext初始化的信息
     */
    public void contextInitialized(ServletContextEvent sce);

    /**
     * 通知servlet上下文即将关闭.
     * 所有的Servlet和过滤器已经被destroy()， 在ServletContextListener被通知上下文已经销毁之前.
     * 
     * @param sce ServletContext销毁的信息
     */
    public void contextDestroyed(ServletContextEvent sce);
}
