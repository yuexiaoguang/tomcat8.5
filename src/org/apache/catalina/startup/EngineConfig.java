package org.apache.catalina.startup;


import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * 启动<b>Engine</b>的事件监听器, 及其关联的上下文.
 */
public class EngineConfig implements LifecycleListener {


    private static final Log log = LogFactory.getLog(EngineConfig.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 关联的Engine
     */
    protected Engine engine = null;


    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    /**
     * 处理START事件.
     *
     * @param event The lifecycle event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        // Identify the engine we are associated with
        try {
            engine = (Engine) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("engineConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.START_EVENT))
            start();
        else if (event.getType().equals(Lifecycle.STOP_EVENT))
            stop();

    }


    // -------------------------------------------------------- Protected Methods


    /**
     * 处理"start"事件.
     */
    protected void start() {

        if (engine.getLogger().isDebugEnabled())
            engine.getLogger().debug(sm.getString("engineConfig.start"));

    }


    /**
     * 处理"stop"事件.
     */
    protected void stop() {

        if (engine.getLogger().isDebugEnabled())
            engine.getLogger().debug(sm.getString("engineConfig.stop"));

    }
}
