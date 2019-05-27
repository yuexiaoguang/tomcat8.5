package org.apache.tomcat.websocket.server;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * 正常使用中, 此 {@link ServletContextListener} 不需要显式配置, 因为 {@link WsSci}执行所有必需的引导, 并在{@link ServletContext}中安装此监听器.
 * 如果禁用{@link WsSci}, 这个监听器必须手动添加到使用WebSocket正确引导{@link WsServerContainer}的每个{@link ServletContext}中.
 */
public class WsContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        // 如果WebSocket服务器容器已经存在, 不要触发WebSocket初始化
        if (sc.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE) == null) {
            WsSci.init(sce.getServletContext(), false);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        Object obj = sc.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
        if (obj instanceof WsServerContainer) {
            ((WsServerContainer) obj).destroy();
        }
    }
}
