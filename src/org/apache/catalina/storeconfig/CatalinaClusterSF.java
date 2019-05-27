package org.apache.catalina.storeconfig;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterDeployer;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.tcp.SimpleTcpCluster;
import org.apache.catalina.tribes.Channel;

/**
 * 使用Membership,Sender,Receiver,Deployer, ReplicationValve生成 Cluster元素
 */
public class CatalinaClusterSF extends StoreFactoryBase {

    /**
     * 存储指定的Cluster 子级.
     *
     * @param aWriter 
     *            PrintWriter to which we are storing
     * @param indent 缩进此元素的空格数量
     * @param aCluster 要存储属性的 Cluster
     *
     * @exception Exception 如果存储期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aCluster,
            StoreDescription parentDesc) throws Exception {
        if (aCluster instanceof CatalinaCluster) {
            CatalinaCluster cluster = (CatalinaCluster) aCluster;
            if (cluster instanceof SimpleTcpCluster) {
                SimpleTcpCluster tcpCluster = (SimpleTcpCluster) cluster;
                // 存储嵌套的<Manager> 元素
                ClusterManager manager = tcpCluster.getManagerTemplate();
                if (manager != null) {
                    storeElement(aWriter, indent, manager);
                }
            }
            // 存储嵌套的<Channel> 元素
            Channel channel = cluster.getChannel();
            if (channel != null) {
                storeElement(aWriter, indent, channel);
            }
            // 存储嵌套的<Deployer> 元素
            ClusterDeployer deployer = cluster.getClusterDeployer();
            if (deployer != null) {
                storeElement(aWriter, indent, deployer);
            }
            // 存储嵌套的<Valve> 元素
            // ClusterValve 没有存储在Hosts 元素, see
            Valve valves[] = cluster.getValves();
            storeElementArray(aWriter, indent, valves);

            if (aCluster instanceof SimpleTcpCluster) {
                // 存储嵌套的<Listener>元素
                LifecycleListener listeners[] = ((SimpleTcpCluster)cluster).findLifecycleListeners();
                storeElementArray(aWriter, indent, listeners);
                // 存储嵌套的<ClusterListener> 元素
                ClusterListener mlisteners[] = ((SimpleTcpCluster)cluster).findClusterListeners();
                List<ClusterListener> clusterListeners = new ArrayList<>();
                for (ClusterListener clusterListener : mlisteners) {
                    if (clusterListener != deployer) {
                        clusterListeners.add(clusterListener);
                    }
                }
                storeElementArray(aWriter, indent, clusterListeners.toArray());
            }
        }
    }
}