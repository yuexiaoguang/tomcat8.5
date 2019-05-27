package javax.servlet.http;

import java.util.EventListener;

/**
 * 此接口的实现将通知Web应用程序中活动会话列表的更改.
 * 要接收通知事件，必须在Web应用程序的部署描述符中配置实现类.
 */
public interface HttpSessionListener extends EventListener {

    /**
     * 创建会话.
     *
     * @param se 通知事件
     */
    public void sessionCreated(HttpSessionEvent se);

    /**
     * 一个会话即将失效.
     *
     * @param se 通知事件
     */
    public void sessionDestroyed(HttpSessionEvent se);
}
