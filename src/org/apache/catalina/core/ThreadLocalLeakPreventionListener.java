package org.apache.catalina.core;

import java.util.concurrent.Executor;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

/**
 * <p>
 * 触发Executor池中线程更新的{@link LifecycleListener}, 当停止一个{@link Context}避免threadLocal相关内存泄漏.
 * </p>
 * <p>
 * Note : 活动线程在执行任务后将返回到池中，一个接一个地更新, see
 * {@link org.apache.tomcat.util.threads.ThreadPoolExecutor}.afterExecute().
 * </p>
 *
 * 必须在server.xml中声明的活动的监听器.
 */
public class ThreadLocalLeakPreventionListener implements LifecycleListener,
        ContainerListener {

    private static final Log log =
        LogFactory.getLog(ThreadLocalLeakPreventionListener.class);

    private volatile boolean serverStopping = false;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * 监听{@link Server}启动的{@link LifecycleEvent}来初始化自己, 以及随后的每个{@link Context}的after_stop事件.
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        try {
            Lifecycle lifecycle = event.getLifecycle();
            if (Lifecycle.AFTER_START_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Server) {
                // 当服务器启动时, 在所有上下文中注册自己为监听器
                // 以及容器事件监听器, 因此知道当新Context被部署的时候
                Server server = (Server) lifecycle;
                registerListenersForServer(server);
            }

            if (Lifecycle.BEFORE_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Server) {
                // Server被关闭, 因此线程池将被关闭，因此不需要清理线程
                serverStopping = true;
            }

            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Context) {
                stopIdleThreads((Context) lifecycle);
            }
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.lifecycleEvent.error",
                    event);
            log.error(msg, e);
        }
    }

    @Override
    public void containerEvent(ContainerEvent event) {
        try {
            String type = event.getType();
            if (Container.ADD_CHILD_EVENT.equals(type)) {
                processContainerAddChild(event.getContainer(),
                    (Container) event.getData());
            } else if (Container.REMOVE_CHILD_EVENT.equals(type)) {
                processContainerRemoveChild(event.getContainer(),
                    (Container) event.getData());
            }
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.containerEvent.error",
                    event);
            log.error(msg, e);
        }

    }

    private void registerListenersForServer(Server server) {
        for (Service service : server.findServices()) {
            Engine engine = service.getContainer();
            engine.addContainerListener(this);
            registerListenersForEngine(engine);
        }

    }

    private void registerListenersForEngine(Engine engine) {
        for (Container hostContainer : engine.findChildren()) {
            Host host = (Host) hostContainer;
            host.addContainerListener(this);
            registerListenersForHost(host);
        }
    }

    private void registerListenersForHost(Host host) {
        for (Container contextContainer : host.findChildren()) {
            Context context = (Context) contextContainer;
            registerContextListener(context);
        }
    }

    private void registerContextListener(Context context) {
        context.addLifecycleListener(this);
    }

    protected void processContainerAddChild(Container parent, Container child) {
        if (log.isDebugEnabled())
            log.debug("Process addChild[parent=" + parent + ",child=" + child +
                "]");

        if (child instanceof Context) {
            registerContextListener((Context) child);
        } else if (child instanceof Engine) {
            registerListenersForEngine((Engine) child);
        } else if (child instanceof Host) {
            registerListenersForHost((Host) child);
        }

    }

    protected void processContainerRemoveChild(Container parent,
        Container child) {

        if (log.isDebugEnabled())
            log.debug("Process removeChild[parent=" + parent + ",child=" +
                child + "]");

        if (child instanceof Context) {
            Context context = (Context) child;
            context.removeLifecycleListener(this);
        } else if (child instanceof Host || child instanceof Engine) {
            child.removeContainerListener(this);
        }
    }

    /**
     * 当前更新每个 ThreadPoolExecutor, 这是上下文被停止的时间.
     *
     * @param context 上下文被停止, 用于发现它的父级Service的所有的Connector
     */
    private void stopIdleThreads(Context context) {
        if (serverStopping) return;

        if (!(context instanceof StandardContext) ||
            !((StandardContext) context).getRenewThreadsWhenStoppingContext()) {
            log.debug("Not renewing threads when the context is stopping. "
                + "It is not configured to do it.");
            return;
        }

        Engine engine = (Engine) context.getParent().getParent();
        Service service = engine.getService();
        Connector[] connectors = service.findConnectors();
        if (connectors != null) {
            for (Connector connector : connectors) {
                ProtocolHandler handler = connector.getProtocolHandler();
                Executor executor = null;
                if (handler != null) {
                    executor = handler.getExecutor();
                }

                if (executor instanceof ThreadPoolExecutor) {
                    ThreadPoolExecutor threadPoolExecutor =
                        (ThreadPoolExecutor) executor;
                    threadPoolExecutor.contextStopping();
                } else if (executor instanceof StandardThreadExecutor) {
                    StandardThreadExecutor stdThreadExecutor =
                        (StandardThreadExecutor) executor;
                    stdThreadExecutor.contextStopping();
                }
            }
        }
    }
}
