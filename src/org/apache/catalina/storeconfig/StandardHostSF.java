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
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.ha.ClusterValve;

/**
 * Store server.xml Element Host
 */
public class StandardHostSF extends StoreFactoryBase {

    /**
     * 保存指定的 Host 属性及其子级(Listener,Alias,Realm,Valve,Cluster, Context)
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素的缩进级别
     * @param aHost 要保存属性的 Host
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aHost,
            StoreDescription parentDesc) throws Exception {
        if (aHost instanceof StandardHost) {
            StandardHost host = (StandardHost) aHost;
            // 保存嵌套的 <Listener> 元素
            LifecycleListener listeners[] = ((Lifecycle) host)
                    .findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);

            // 保存嵌套的 <Alias> 元素
            String aliases[] = host.findAliases();
            getStoreAppender().printTagArray(aWriter, "Alias", indent + 2,
                    aliases);

            // 保存嵌套的 <Realm> 元素
            Realm realm = host.getRealm();
            if (realm != null) {
                Realm parentRealm = null;
                if (host.getParent() != null) {
                    parentRealm = host.getParent().getRealm();
                }
                if (realm != parentRealm) {
                    storeElement(aWriter, indent, realm);
                }
            }

            // 保存嵌套的 <Valve> 元素
            Valve valves[] = host.getPipeline().getValves();
            if(valves != null && valves.length > 0 ) {
                List<Valve> hostValves = new ArrayList<>() ;
                for(int i = 0 ; i < valves.length ; i++ ) {
                    if(!( valves[i] instanceof ClusterValve))
                        hostValves.add(valves[i]);
                }
                storeElementArray(aWriter, indent, hostValves.toArray());
            }

            // 保存所有的 <Cluster> 元素
            Cluster cluster = host.getCluster();
            if (cluster != null) {
                Cluster parentCluster = null;
                if (host.getParent() != null) {
                    parentCluster = host.getParent().getCluster();
                }
                if (cluster != parentCluster) {
                    storeElement(aWriter, indent, cluster);
                }
            }

            // 保存所有的 <Context> 元素
            Container children[] = host.findChildren();
            storeElementArray(aWriter, indent, children);
        }
    }

}