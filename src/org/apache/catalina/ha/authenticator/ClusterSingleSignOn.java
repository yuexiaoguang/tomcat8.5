package org.apache.catalina.ha.authenticator;

import java.security.Principal;

import org.apache.catalina.Container;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.SessionListener;
import org.apache.catalina.authenticator.SingleSignOn;
import org.apache.catalina.authenticator.SingleSignOnEntry;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.tipis.AbstractReplicatedMap.MapOwner;
import org.apache.catalina.tribes.tipis.ReplicatedMap;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 支持集群的每个节点上的“单点登录”用户经历的<strong>Valve</strong>, 其中成功认证到一个Web应用程序的用户的安全身份, 传播到其他Web应用程序和在同一安全域中的其他节点集群.
 * 为了成功使用, 必须满足以下要求:
 * <ul>
 * <li>这个Valve必须在表示虚拟主机的Container上配置 (通常是<code>Host</code>实现).</li>
 * <li>包含共享用户和角色信息的<code>Realm</code>必须在相同Container上配置(或更高一级), 并不会在Web应用程序级别上重写.</li>
 * <li>Web应用程序本身必须使用一个标准的Authenticator, 在<code>org.apache.catalina.authenticator</code>包中找到的.</li>
 * </ul>
 */
public class ClusterSingleSignOn extends SingleSignOn implements ClusterValve, MapOwner {

    private static final StringManager sm = StringManager.getManager(ClusterSingleSignOn.class);

    // -------------------------------------------------------------- Properties

    private CatalinaCluster cluster = null;
    @Override
    public CatalinaCluster getCluster() { return cluster; }
    @Override
    public void setCluster(CatalinaCluster cluster) {
        this.cluster = cluster;
    }


    private long rpcTimeout = 15000;
    public long getRpcTimeout() {
        return rpcTimeout;
    }
    public void setRpcTimeout(long rpcTimeout) {
        this.rpcTimeout = rpcTimeout;
    }


    private int mapSendOptions =
            Channel.SEND_OPTIONS_SYNCHRONIZED_ACK | Channel.SEND_OPTIONS_USE_ACK;
    public int getMapSendOptions() {
        return mapSendOptions;
    }
    public void setMapSendOptions(int mapSendOptions) {
        this.mapSendOptions = mapSendOptions;
    }


    private boolean terminateOnStartFailure = false;
    public boolean getTerminateOnStartFailure() {
        return terminateOnStartFailure;
    }

    public void setTerminateOnStartFailure(boolean terminateOnStartFailure) {
        this.terminateOnStartFailure = terminateOnStartFailure;
    }

    private long accessTimeout = 5000;
    public long getAccessTimeout() {
        return accessTimeout;
    }

    public void setAccessTimeout(long accessTimeout) {
        this.accessTimeout = accessTimeout;
    }

    // ---------------------------------------------------- SingleSignOn Methods

    @Override
    protected boolean associate(String ssoId, Session session) {
        boolean result = super.associate(ssoId, session);
        if (result) {
            ((ReplicatedMap<String,SingleSignOnEntry>) cache).replicate(ssoId, true);
        }
        return result;
    }

    @Override
    protected boolean update(String ssoId, Principal principal, String authType,
            String username, String password) {
        boolean result = super.update(ssoId, principal, authType, username, password);
        if (result) {
            ((ReplicatedMap<String,SingleSignOnEntry>) cache).replicate(ssoId, true);
        }
        return result;
    }

    @Override
    protected SessionListener getSessionListener(String ssoId) {
        return new ClusterSingleSignOnListener(ssoId);
    }


    // -------------------------------------------------------- MapOwner Methods

    @Override
    public void objectMadePrimary(Object key, Object value) {
        // NO-OP
    }


    // ------------------------------------------------------- Lifecycle Methods

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Load the cluster component, if any
        try {
            if(cluster == null) {
                Container host = getContainer();
                if(host instanceof Host) {
                    if(host.getCluster() instanceof CatalinaCluster) {
                        setCluster((CatalinaCluster) host.getCluster());
                    }
                }
            }
            if (cluster == null) {
                throw new LifecycleException(sm.getString("clusterSingleSignOn.nocluster"));
            }

            ClassLoader[] cls = new ClassLoader[] { this.getClass().getClassLoader() };

            ReplicatedMap<String,SingleSignOnEntry> cache = new ReplicatedMap<>(
                    this, cluster.getChannel(), rpcTimeout, cluster.getClusterName() + "-SSO-cache",
                    cls, terminateOnStartFailure);
            cache.setChannelSendOptions(mapSendOptions);
            cache.setAccessTimeout(accessTimeout);
            this.cache = cache;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            throw new LifecycleException(sm.getString("clusterSingleSignOn.clusterLoad.fail"), t);
        }

        super.startInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        super.stopInternal();

        if (getCluster() != null) {
            ((ReplicatedMap<?,?>) cache).breakdown();
        }
    }
}
