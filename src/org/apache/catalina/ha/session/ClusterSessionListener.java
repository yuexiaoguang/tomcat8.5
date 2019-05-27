package org.apache.catalina.ha.session;

import java.util.Map;

import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 从其他群集节点接收复制的SessionMessage.
 */
public class ClusterSessionListener extends ClusterListener {

    private static final Log log =
        LogFactory.getLog(ClusterSessionListener.class);
    private static final StringManager sm = StringManager.getManager(ClusterSessionListener.class);

    //--Constructor---------------------------------------------

    public ClusterSessionListener() {
        // NO-OP
    }

    //--Logic---------------------------------------------------

    /**
     * 集群的回调, 当接收消息时, 集群将广播它，调用接收器上的messageReceived.
     *
     * @param myobj ClusterMessage - 从集群接收的消息
     */
    @Override
    public void messageReceived(ClusterMessage myobj) {
        if (myobj instanceof SessionMessage) {
            SessionMessage msg = (SessionMessage) myobj;
            String ctxname = msg.getContextName();
            // 检查消息是一个 EVT_GET_ALL_SESSIONS, 如果是这样, 等到全面启动
            Map<String,ClusterManager> managers = cluster.getManagers() ;
            if (ctxname == null) {
                for (Map.Entry<String, ClusterManager> entry :
                        managers.entrySet()) {
                    if (entry.getValue() != null)
                        entry.getValue().messageDataReceived(msg);
                    else {
                        // 这种情况在系统启动之前发生了很多
                        if (log.isDebugEnabled())
                            log.debug(sm.getString("clusterSessionListener.noManager", entry.getKey()));
                    }
                }
            } else {
                ClusterManager mgr = managers.get(ctxname);
                if (mgr != null) {
                    mgr.messageDataReceived(msg);
                } else {
                    if (log.isWarnEnabled())
                        log.warn(sm.getString("clusterSessionListener.noManager", ctxname));

                    // 没有上下文管理器消息被回复, 为了避免分阶段同步GET_ALL_SESSIONS超时.
                    if (msg.getEventType() == SessionMessage.EVT_GET_ALL_SESSIONS) {
                        SessionMessage replymsg = new SessionMessageImpl(ctxname,
                                SessionMessage.EVT_ALL_SESSION_NOCONTEXTMANAGER,
                                null, "NO-CONTEXT-MANAGER","NO-CONTEXT-MANAGER-" + ctxname);
                        cluster.send(replymsg, msg.getAddress());
                    }
                }

            }
        }
        return;
    }

    /**
     * 只接收 SessionMessage
     *
     * @param msg ClusterMessage
     * @return boolean - 返回 true调用messageReceived. 返回 false不调用 messageReceived方法.
     */
    @Override
    public boolean accept(ClusterMessage msg) {
        return (msg instanceof SessionMessage);
    }
}
