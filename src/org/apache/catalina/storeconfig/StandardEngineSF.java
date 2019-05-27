package org.apache.catalina.storeconfig;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.ha.ClusterValve;

/**
 * 保存 server.xml Element Engine
 */
public class StandardEngineSF extends StoreFactoryBase {

    /**
     * 保存指定的 Engine 属性.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aEngine 要保存属性的 Object
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aEngine,
            StoreDescription parentDesc) throws Exception {
        if (aEngine instanceof StandardEngine) {
            StandardEngine engine = (StandardEngine) aEngine;
            // 保存嵌套的 <Listener> 元素
            LifecycleListener listeners[] = ((Lifecycle) engine)
                    .findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);

            // 保存嵌套的 <Realm> 元素
            Realm realm = engine.getRealm();
            Realm parentRealm = null;
            // TODO is this case possible? (see it a old Server 5.0 impl)
            if (engine.getParent() != null) {
                parentRealm = engine.getParent().getRealm();
            }
            if (realm != parentRealm) {
                storeElement(aWriter, indent, realm);

            }

            // 保存嵌套的 <Valve> 元素
            Valve valves[] = engine.getPipeline().getValves();
            if(valves != null && valves.length > 0 ) {
                List<Valve> engineValves = new ArrayList<>() ;
                for(int i = 0 ; i < valves.length ; i++ ) {
                    if(!( valves[i] instanceof ClusterValve))
                        engineValves.add(valves[i]);
                }
                storeElementArray(aWriter, indent, engineValves.toArray());
            }

            // 保存所有的 <Cluster> 元素
            Cluster cluster = engine.getCluster();
            if (cluster != null) {
                storeElement(aWriter, indent, cluster);
            }
            // 保存所有的 <Host> 元素
            Container children[] = engine.findChildren();
            storeElementArray(aWriter, indent, children);

       }
    }
}