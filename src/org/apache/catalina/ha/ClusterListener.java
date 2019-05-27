package org.apache.catalina.ha;

import java.io.Serializable;

import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Member;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * 在主会话节点失败后从其他备份节点接收 SessionID 集群修改.
 */
public abstract class ClusterListener implements ChannelListener {

    private static final Log log = LogFactory.getLog(ClusterListener.class);

    //--Instance Variables--------------------------------------

    protected CatalinaCluster cluster = null;

    //--Constructor---------------------------------------------

    public ClusterListener() {
        // NO-OP
    }

    //--Instance Getters/Setters--------------------------------

    public CatalinaCluster getCluster() {
        return cluster;
    }

    public void setCluster(CatalinaCluster cluster) {
        if (log.isDebugEnabled()) {
            if (cluster != null)
                log.debug("add ClusterListener " + this.toString() +
                        " to cluster" + cluster);
            else
                log.debug("remove ClusterListener " + this.toString() +
                        " from cluster");
        }
        this.cluster = cluster;
    }

    @Override
    public boolean equals(Object listener) {
        return super.equals(listener);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    //--Logic---------------------------------------------------

    @Override
    public final void messageReceived(Serializable msg, Member member) {
        if ( msg instanceof ClusterMessage ) messageReceived((ClusterMessage)msg);
    }
    @Override
    public final boolean accept(Serializable msg, Member member) {
        if ( msg instanceof ClusterMessage ) return true;
        return false;
    }



    /**
     * 集群的回调, 当接收到消息时, 集群将广播它，调用接收器上的messageReceived.
     *
     * @param msg ClusterMessage - 从集群接收的消息
     */
    public abstract void messageReceived(ClusterMessage msg) ;


    /**
     * 接收SessionIDMessages
     *
     * @param msg ClusterMessage
     * @return boolean - 返回 true 调用messageReceived. 返回false, 不调用messageReceived方法.
     */
    public abstract boolean accept(ClusterMessage msg) ;

}
