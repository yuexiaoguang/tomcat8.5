package org.apache.catalina.ha.tcp;

import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Valve;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterDeployer;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.ha.session.ClusterSessionListener;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.session.JvmRouteBinderValve;
import org.apache.catalina.ha.session.SessionMessage;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.group.interceptors.MessageDispatchInterceptor;
import org.apache.catalina.tribes.group.interceptors.TcpFailureDetector;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * <b>Cluster</b>实现使用简单多播. 负责建立集群，并为调用者提供有效的多播接收器/发送器.
 */
public class SimpleTcpCluster extends LifecycleMBeanBase
        implements CatalinaCluster, MembershipListener, ChannelListener{

    public static final Log log = LogFactory.getLog(SimpleTcpCluster.class);

    // ----------------------------------------------------- Instance Variables

    public static final String BEFORE_MEMBERREGISTER_EVENT = "before_member_register";

    public static final String AFTER_MEMBERREGISTER_EVENT = "after_member_register";

    public static final String BEFORE_MANAGERREGISTER_EVENT = "before_manager_register";

    public static final String AFTER_MANAGERREGISTER_EVENT = "after_manager_register";

    public static final String BEFORE_MANAGERUNREGISTER_EVENT = "before_manager_unregister";

    public static final String AFTER_MANAGERUNREGISTER_EVENT = "after_manager_unregister";

    public static final String BEFORE_MEMBERUNREGISTER_EVENT = "before_member_unregister";

    public static final String AFTER_MEMBERUNREGISTER_EVENT = "after_member_unregister";

    public static final String SEND_MESSAGE_FAILURE_EVENT = "send_message_failure";

    public static final String RECEIVE_MESSAGE_FAILURE_EVENT = "receive_message_failure";

    /**
     * Group channel.
     */
    protected Channel channel = new GroupChannel();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 要加入的集群名称
     */
    protected String clusterName ;

    /**
     * 在容器后台线程上调用 Channel.heartbeat()
     */
    protected boolean heartbeatBackgroundEnabled =false ;

    /**
     * 关联的Container.
     */
    protected Container container = null;

    /**
     * 属性修改支持.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);

    protected final Map<String, ClusterManager> managers = new HashMap<>();

    protected ClusterManager managerTemplate = new DeltaManager();

    private final List<Valve> valves = new ArrayList<>();

    private ClusterDeployer clusterDeployer;
    private ObjectName onameClusterDeployer;

    /**
     * 消息监听器
     */
    protected final List<ClusterListener> clusterListeners = new ArrayList<>();

    private boolean notifyLifecycleListenerOnFailure = false;

    private int channelSendOptions = Channel.SEND_OPTIONS_ASYNCHRONOUS;

    private int channelStartOptions = Channel.DEFAULT;

    private final Map<Member,ObjectName> memberOnameMap = new ConcurrentHashMap<>();

    // ------------------------------------------------------------- Properties

    public SimpleTcpCluster() {
        // NO-OP
    }

    /**
     * 是否启用心跳(默认 false)
     */
    public boolean isHeartbeatBackgroundEnabled() {
        return heartbeatBackgroundEnabled;
    }

    /**
     * 启用容器后台线程在通道中调用心跳
     * 
     * @param heartbeatBackgroundEnabled
     */
    public void setHeartbeatBackgroundEnabled(boolean heartbeatBackgroundEnabled) {
        this.heartbeatBackgroundEnabled = heartbeatBackgroundEnabled;
    }

    /**
     * 设置要加入的集群的名称, 如果没有此名称的集群，则创建一个.
     *
     * @param clusterName  要加入的集群的名称
     */
    @Override
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * 返回此服务器当前配置为在其内运行的集群的名称.
     */
    @Override
    public String getClusterName() {
        if(clusterName == null && container != null)
            return container.getName() ;
        return clusterName;
    }

    /**
     * 设置与集群相关联的容器
     *
     * @param container 要使用的容器
     */
    @Override
    public void setContainer(Container container) {
        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);
    }

    /**
     * 获取与集群相关联的容器
     */
    @Override
    public Container getContainer() {
        return (this.container);
    }

    public boolean isNotifyLifecycleListenerOnFailure() {
        return notifyLifecycleListenerOnFailure;
    }

    public void setNotifyLifecycleListenerOnFailure(
            boolean notifyListenerOnFailure) {
        boolean oldNotifyListenerOnFailure = this.notifyLifecycleListenerOnFailure;
        this.notifyLifecycleListenerOnFailure = notifyListenerOnFailure;
        support.firePropertyChange("notifyLifecycleListenerOnFailure",
                oldNotifyListenerOnFailure,
                this.notifyLifecycleListenerOnFailure);
    }

    /**
     * 添加集群阀门.
     * 集群阀门只在集群启动时添加到容器中!
     * 
     * @param valve 集群Valve.
     */
    @Override
    public void addValve(Valve valve) {
        if (valve instanceof ClusterValve && (!valves.contains(valve)))
            valves.add(valve);
    }

    /**
     * 获取所有集群阀门
     */
    @Override
    public Valve[] getValves() {
        return valves.toArray(new Valve[valves.size()]);
    }

    /**
     * 获取与此集群关联的集群监听器. 如果没有注册的监听器, 返回零长度数组.
     */
    public ClusterListener[] findClusterListeners() {
        if (clusterListeners.size() > 0) {
            ClusterListener[] listener = new ClusterListener[clusterListeners.size()];
            clusterListeners.toArray(listener);
            return listener;
        } else
            return new ClusterListener[0];

    }

    /**
     * 添加集群消息侦听器并向该侦听器注册集群.
     *
     * @param listener The new listener
     */
    @Override
    public void addClusterListener(ClusterListener listener) {
        if (listener != null && !clusterListeners.contains(listener)) {
            clusterListeners.add(listener);
            listener.setCluster(this);
        }
    }

    /**
     * 从监听器中删除消息监听器并取消注册集群.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removeClusterListener(ClusterListener listener) {
        if (listener != null) {
            clusterListeners.remove(listener);
            listener.setCluster(null);
        }
    }

    @Override
    public ClusterDeployer getClusterDeployer() {
        return clusterDeployer;
    }

    /**
     * 设置新的Deployer, 必须在集群启动之前设置!
     * 
     * @param clusterDeployer 关联的部署程序
     */
    @Override
    public void setClusterDeployer(ClusterDeployer clusterDeployer) {
        this.clusterDeployer = clusterDeployer;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void setManagerTemplate(ClusterManager managerTemplate) {
        this.managerTemplate = managerTemplate;
    }

    public void setChannelSendOptions(int channelSendOptions) {
        this.channelSendOptions = channelSendOptions;
    }

    /**
     * 是否有成员
     */
    protected boolean hasMembers = false;
    @Override
    public boolean hasMembers() {
        return hasMembers;
    }

    /**
     * 获取当前所有集群成员
     */
    @Override
    public Member[] getMembers() {
        return channel.getMembers();
    }

    /**
     * 返回表示此节点的成员.
     */
    @Override
    public Member getLocalMember() {
        return channel.getLocalMember(true);
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public Map<String, ClusterManager> getManagers() {
        return managers;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    public ClusterManager getManagerTemplate() {
        return managerTemplate;
    }

    public int getChannelSendOptions() {
        return channelSendOptions;
    }

    /**
     * 创建Manager, 但不添加到集群 (同时启动管理器)
     *
     * @param name 此管理器的上下文名称
     */
    @Override
    public synchronized Manager createManager(String name) {
        if (log.isDebugEnabled()) {
            log.debug("Creating ClusterManager for context " + name +
                    " using class " + getManagerTemplate().getClass().getName());
        }
        ClusterManager manager = null;
        try {
            manager = managerTemplate.cloneFromTemplate();
            manager.setName(name);
        } catch (Exception x) {
            log.error(sm.getString("simpleTcpCluster.clustermanager.cloneFailed"), x);
            manager = new org.apache.catalina.ha.session.DeltaManager();
        } finally {
            if ( manager != null) manager.setCluster(this);
        }
        return manager;
    }

    @Override
    public void registerManager(Manager manager) {

        if (! (manager instanceof ClusterManager)) {
            log.warn(sm.getString("simpleTcpCluster.clustermanager.notImplement", manager));
            return;
        }
        ClusterManager cmanager = (ClusterManager) manager;
        // Notify our interested LifecycleListeners
        fireLifecycleEvent(BEFORE_MANAGERREGISTER_EVENT, manager);
        String clusterName = getManagerName(cmanager.getName(), manager);
        cmanager.setName(clusterName);
        cmanager.setCluster(this);

        managers.put(clusterName, cmanager);
        // Notify our interested LifecycleListeners
        fireLifecycleEvent(AFTER_MANAGERREGISTER_EVENT, manager);
    }

    /**
     * 从集群复制总线移除应用程序.
     *
     * @param manager The manager
     */
    @Override
    public void removeManager(Manager manager) {
        if (manager instanceof ClusterManager) {
            ClusterManager cmgr = (ClusterManager) manager;
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(BEFORE_MANAGERUNREGISTER_EVENT,manager);
            managers.remove(getManagerName(cmgr.getName(),manager));
            cmgr.setCluster(null);
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(AFTER_MANAGERUNREGISTER_EVENT, manager);
        }
    }

    @Override
    public String getManagerName(String name, Manager manager) {
        String clusterName = name ;
        if (clusterName == null) clusterName = manager.getContext().getName();
        if (getContainer() instanceof Engine) {
            Context context = manager.getContext();
            Container host = context.getParent();
            if (host instanceof Host && clusterName != null &&
                    !(clusterName.startsWith(host.getName() +"#"))) {
                clusterName = host.getName() +"#" + clusterName ;
            }
        }
        return clusterName;
    }

    @Override
    public Manager getManager(String name) {
        return managers.get(name);
    }

    // ------------------------------------------------------ Lifecycle Methods

    /**
     * 执行周期性任务，如重新加载等.
     * 此方法将在容器的类加载上下文中调用. 异常会被捕获并记录下来.
     */
    @Override
    public void backgroundProcess() {
        if (clusterDeployer != null) clusterDeployer.backgroundProcess();

        // send a heartbeat through the channel
        if ( isHeartbeatBackgroundEnabled() && channel !=null ) channel.heartbeat();

        // 周期性事件
        fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }


    // ------------------------------------------------------ public

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        if (clusterDeployer != null) {
            StringBuilder name = new StringBuilder("type=Cluster");
            Container container = getContainer();
            if (container != null) {
                name.append(container.getMBeanKeyProperties());
            }
            name.append(",component=Deployer");
            onameClusterDeployer = register(clusterDeployer, name.toString());
        }
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if (log.isInfoEnabled()) log.info(sm.getString("simpleTcpCluster.start"));

        try {
            checkDefaults();
            registerClusterValve();
            channel.addMembershipListener(this);
            channel.addChannelListener(this);
            channel.setName(getClusterName() + "-Channel");
            channel.start(channelStartOptions);
            if (clusterDeployer != null) clusterDeployer.start();
            registerMember(channel.getLocalMember(false));
        } catch (Exception x) {
            log.error(sm.getString("simpleTcpCluster.startUnable"), x);
            throw new LifecycleException(x);
        }
        setState(LifecycleState.STARTING);
    }

    protected void checkDefaults() {
        if ( clusterListeners.size() == 0 && managerTemplate instanceof DeltaManager ) {
            addClusterListener(new ClusterSessionListener());
        }
        if ( valves.size() == 0 ) {
            addValve(new JvmRouteBinderValve());
            addValve(new ReplicationValve());
        }
        if ( clusterDeployer != null ) clusterDeployer.setCluster(this);
        if ( channel == null ) channel = new GroupChannel();
        if ( channel instanceof GroupChannel && !((GroupChannel)channel).getInterceptors().hasNext()) {
            channel.addInterceptor(new MessageDispatchInterceptor());
            channel.addInterceptor(new TcpFailureDetector());
        }
        if (heartbeatBackgroundEnabled) channel.setHeartbeat(false);
    }

    /**
     * 注册所有的集群阀门到主机或引擎
     */
    protected void registerClusterValve() {
        if(container != null ) {
            for (Iterator<Valve> iter = valves.iterator(); iter.hasNext();) {
                ClusterValve valve = (ClusterValve) iter.next();
                if (log.isDebugEnabled())
                    log.debug("Invoking addValve on " + getContainer()
                            + " with class=" + valve.getClass().getName());
                if (valve != null) {
                    container.getPipeline().addValve(valve);
                    valve.setCluster(this);
                }
            }
        }
    }

    /**
     * 注销所有的集群阀门到主机或引擎
     */
    protected void unregisterClusterValve() {
        for (Iterator<Valve> iter = valves.iterator(); iter.hasNext();) {
            ClusterValve valve = (ClusterValve) iter.next();
            if (log.isDebugEnabled())
                log.debug("Invoking removeValve on " + getContainer()
                        + " with class=" + valve.getClass().getName());
            if (valve != null) {
                container.getPipeline().removeValve(valve);
                valve.setCluster(null);
            }
        }
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);

        unregisterMember(channel.getLocalMember(false));
        if (clusterDeployer != null) clusterDeployer.stop();
        this.managers.clear();
        try {
            if ( clusterDeployer != null ) clusterDeployer.setCluster(null);
            channel.stop(channelStartOptions);
            channel.removeChannelListener(this);
            channel.removeMembershipListener(this);
            this.unregisterClusterValve();
        } catch (Exception x) {
            log.error(sm.getString("simpleTcpCluster.stopUnable"), x);
        }
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        if (onameClusterDeployer != null) {
            unregister(onameClusterDeployer);
            onameClusterDeployer = null;
        }
        super.destroyInternal();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (container == null) {
            sb.append("Container is null");
        } else {
            sb.append(container.getName());
        }
        sb.append(']');
        return sb.toString();
    }


    /**
     * 向所有集群成员发送消息
     * 
     * @param msg 传输的消息
     */
    @Override
    public void send(ClusterMessage msg) {
        send(msg, null);
    }

    /**
     * 向一个集群成员发送消息
     *
     * @param msg 传输的消息
     * @param dest 接收者
     */
    @Override
    public void send(ClusterMessage msg, Member dest) {
        try {
            msg.setAddress(getLocalMember());
            int sendOptions = channelSendOptions;
            if (msg instanceof SessionMessage
                    && ((SessionMessage)msg).getEventType() == SessionMessage.EVT_ALL_SESSION_DATA) {
                sendOptions = Channel.SEND_OPTIONS_SYNCHRONIZED_ACK|Channel.SEND_OPTIONS_USE_ACK;
            }
            if (dest != null) {
                if (!getLocalMember().equals(dest)) {
                    channel.send(new Member[] {dest}, msg, sendOptions);
                } else
                    log.error(sm.getString("simpleTcpCluster.unableSend.localMember", msg));
            } else {
                Member[] destmembers = channel.getMembers();
                if (destmembers.length>0)
                    channel.send(destmembers,msg, sendOptions);
                else if (log.isDebugEnabled())
                    log.debug("No members in cluster, ignoring message:"+msg);
            }
        } catch (Exception x) {
            log.error(sm.getString("simpleTcpCluster.sendFailed"), x);
        }
    }

    /**
     * 注册集群成员
     */
    @Override
    public void memberAdded(Member member) {
        try {
            hasMembers = channel.hasMembers();
            if (log.isInfoEnabled()) log.info(sm.getString("simpleTcpCluster.member.added", member));
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(BEFORE_MEMBERREGISTER_EVENT, member);

            registerMember(member);

            // Notify our interested LifecycleListeners
            fireLifecycleEvent(AFTER_MEMBERREGISTER_EVENT, member);
        } catch (Exception x) {
            log.error(sm.getString("simpleTcpCluster.member.addFailed"), x);
        }

    }

    /**
     * 集群成员消失
     */
    @Override
    public void memberDisappeared(Member member) {
        try {
            hasMembers = channel.hasMembers();
            if (log.isInfoEnabled()) log.info(sm.getString("simpleTcpCluster.member.disappeared", member));
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(BEFORE_MEMBERUNREGISTER_EVENT, member);

            unregisterMember(member);

            // Notify our interested LifecycleListeners
            fireLifecycleEvent(AFTER_MEMBERUNREGISTER_EVENT, member);
        } catch (Exception x) {
            log.error(sm.getString("simpleTcpCluster.member.removeFailed"), x);
        }
    }

    // --------------------------------------------------------- receiver
    // messages

    /**
     * 消息是否是ClusterMessage
     *
     * @param msg 接收的消息
     */
    @Override
    public boolean accept(Serializable msg, Member sender) {
        return (msg instanceof ClusterMessage);
    }


    @Override
    public void messageReceived(Serializable message, Member sender) {
        ClusterMessage fwd = (ClusterMessage)message;
        fwd.setAddress(sender);
        messageReceived(fwd);
    }

    public void messageReceived(ClusterMessage message) {

        if (log.isDebugEnabled() && message != null)
            log.debug("Assuming clocks are synched: Replication for "
                    + message.getUniqueId() + " took="
                    + (System.currentTimeMillis() - (message).getTimestamp())
                    + " ms.");

        //invoke all the listeners
        boolean accepted = false;
        if (message != null) {
            for (Iterator<ClusterListener> iter = clusterListeners.iterator();
                    iter.hasNext();) {
                ClusterListener listener = iter.next();
                if (listener.accept(message)) {
                    accepted = true;
                    listener.messageReceived(message);
                }
            }
            if (!accepted && notifyLifecycleListenerOnFailure) {
                Member dest = message.getAddress();
                // Notify our interested LifecycleListeners
                fireLifecycleEvent(RECEIVE_MESSAGE_FAILURE_EVENT,
                        new SendMessageData(message, dest, null));
                if (log.isDebugEnabled()) {
                    log.debug("Message " + message.toString() + " from type "
                            + message.getClass().getName()
                            + " transfered but no listener registered");
                }
            }
        }
        return;
    }

    public int getChannelStartOptions() {
        return channelStartOptions;
    }

    public void setChannelStartOptions(int channelStartOptions) {
        this.channelStartOptions = channelStartOptions;
    }


    // --------------------------------------------------------------------- JMX

    @Override
    protected String getDomainInternal() {
        Container container = getContainer();
        if (container == null) {
            return null;
        }
        return container.getDomain();
    }

    @Override
    protected String getObjectNameKeyProperties() {
        StringBuilder name = new StringBuilder("type=Cluster");

        Container container = getContainer();
        if (container != null) {
            name.append(container.getMBeanKeyProperties());
        }

        return name.toString();
    }

    private void registerMember(Member member) {
        // JMX registration
        StringBuilder name = new StringBuilder("type=Cluster");
        Container container = getContainer();
        if (container != null) {
            name.append(container.getMBeanKeyProperties());
        }
        name.append(",component=Member,name=");
        name.append(ObjectName.quote(member.getName()));

        ObjectName oname = register(member, name.toString());
        memberOnameMap.put(member, oname);
    }

    private void unregisterMember(Member member) {
        if (member == null) return;
        ObjectName oname = memberOnameMap.remove(member);
        if (oname != null) {
            unregister(oname);
        }
    }
}
