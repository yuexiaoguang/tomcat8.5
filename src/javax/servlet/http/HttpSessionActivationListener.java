package javax.servlet.http;

import java.util.EventListener;

/**
 * 绑定到会话的对象可能会监听容器事件，通知它们会话将被钝化，会话将被激活.
 * 在VM或持久会话之间迁移会话的容器，需要通知绑定到会话上的实现了HttpSessionActivationListener的对象.
 */
public interface HttpSessionActivationListener extends EventListener {

    /**
     * 会话即将被钝化.
     *
     * @param se 关于即将要钝化的会话的信息
     */
    public void sessionWillPassivate(HttpSessionEvent se);

    /**
     * 刚刚激活会话.
     *
     * @param se 有关刚刚激活的会话的信息
     */
    public void sessionDidActivate(HttpSessionEvent se);
}

