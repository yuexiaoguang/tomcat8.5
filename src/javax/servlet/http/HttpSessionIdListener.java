package javax.servlet.http;

import java.util.EventListener;

/**
 * 通知{@link HttpSession}的ID发生修改.
 * 要接收通知事件，必须在Web应用程序的部署描述符中配置实现类, 或者使用{@link javax.servlet.annotation.WebListener}注解，
 * 或者使用{@link javax.servlet.ServletContext}上的addListener方法.
 */
public interface HttpSessionIdListener extends EventListener {

    /**
     * 会话ID已更改.
     *
     * @param se 通知事件
     * @param oldSessionId 旧会话ID
     */
    public void sessionIdChanged(HttpSessionEvent se, String oldSessionId);
}
