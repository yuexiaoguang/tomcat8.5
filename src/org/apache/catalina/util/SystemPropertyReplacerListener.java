package org.apache.catalina.util;


import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.tomcat.util.digester.Digester;


/**
 * 用于对系统属性进行属性替换的帮助类.
 */
public class SystemPropertyReplacerListener implements LifecycleListener {

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            Digester.replaceSystemProperties();
        }
    }
}
