package org.apache.catalina.tribes.group;


import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.tribes.ByteMessage;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.ChannelSender;
import org.apache.catalina.tribes.ErrorHandler;
import org.apache.catalina.tribes.Heartbeat;
import org.apache.catalina.tribes.JmxChannel;
import org.apache.catalina.tribes.ManagedChannel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.RemoteProcessException;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.group.interceptors.MessageDispatchInterceptor;
import org.apache.catalina.tribes.io.BufferPool;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.Logs;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Channel的默认实现类.<br>
 * GroupChannel 管理复制 channel. 它通过成员通知来协调发送和接收的消息.
 * channel 有一个可修改消息或执行其他逻辑的拦截器链.<br>
 * 它管理着一个完整的组, 包括成员和复制.
 */
public class GroupChannel extends ChannelInterceptorBase
        implements ManagedChannel, JmxChannel, GroupChannelMBean {

    private static final Log log = LogFactory.getLog(GroupChannel.class);
    protected static final StringManager sm = StringManager.getManager(GroupChannel.class);

    /**
     * 确定channel是否管理自己的心跳.
     * 如果设置为 true, channel 将启动一个本地线程用于心跳.
     */
    protected boolean heartbeat = true;
    /**
     * 如果<code>heartbeat == true</code>, 那么多久心跳一次. 默认五秒
     */
    protected long heartbeatSleeptime = 5*1000;//every 5 seconds

    /**
     * 内部心跳线程
     */
    protected HeartbeatThread hbthread = null;

    /**
     * <code>ChannelCoordinator</code>协调底层组件:<br>
     * - MembershipService<br>
     * - ChannelSender <br>
     * - ChannelReceiver<br>
     */
    protected final ChannelCoordinator coordinator = new ChannelCoordinator();

    /**
     * 拦截器堆栈中的第一个拦截器.
     * 拦截器保存在链表中, 所以需要到第一个元素的引用
     */
    protected ChannelInterceptor interceptors = null;

    /**
     * 订阅成员通告的成员监听器列表
     */
    protected final List<MembershipListener> membershipListeners = new CopyOnWriteArrayList<>();

    /**
     * 订阅传入消息的channel监听器列表
     */
    protected final List<ChannelListener> channelListeners = new CopyOnWriteArrayList<>();

    /**
     * 如果设置为 true, GroupChannel 将检查
     */
    protected boolean optionCheck = false;

    /**
     * 这个 channel 的名称.
     */
    protected String name = null;

    /**
     * 这个channel注册的 jmx 域.
     */
    private String jmxDomain = "ClusterChannel";

    /**
     * channel ObjectName使用的 jmx 前缀.
     */
    private String jmxPrefix = "";

    /**
     * 如果设置为 true, 使用jmx注册这个 channel.
     */
    private boolean jmxEnabled = true;

    /**
     * 这个channel的ObjectName.
     */
    private ObjectName oname = null;

    /**
     * 添加的第一个拦截器总是 channel 本身.
     */
    public GroupChannel() {
        addInterceptor(this);
    }


    /**
     * 将一个拦截器添加到堆栈中进行消息处理<br> 拦截器按其添加的方式排序.<br>
     * <code>channel.addInterceptor(A);</code><br>
     * <code>channel.addInterceptor(C);</code><br>
     * <code>channel.addInterceptor(B);</code><br>
     * 将会是像这样的拦截器堆栈:<br>
     * <code>A -&gt; C -&gt; B</code><br>
     * 完整的堆栈看起来像这样:<br>
     * <code>Channel -&gt; A -&gt; C -&gt; B -&gt; ChannelCoordinator</code><br>
     * @param interceptor ChannelInterceptorBase
     */
    @Override
    public void addInterceptor(ChannelInterceptor interceptor) {
        if ( interceptors == null ) {
            interceptors = interceptor;
            interceptors.setNext(coordinator);
            interceptors.setPrevious(null);
            coordinator.setPrevious(interceptors);
        } else {
            ChannelInterceptor last = interceptors;
            while ( last.getNext() != coordinator ) {
                last = last.getNext();
            }
            last.setNext(interceptor);
            interceptor.setNext(coordinator);
            interceptor.setPrevious(last);
            coordinator.setPrevious(interceptor);
        }
    }

    /**
     * 通过拦截器栈发送心跳.<br>
     * 如果你关闭了内部心跳 <code>channel.setHeartbeat(false)</code>，就从应用程序周期性地调用这个方法
     */
    @Override
    public void heartbeat() {
        super.heartbeat();
        Iterator<MembershipListener> membershipListenerIterator = membershipListeners.iterator();
        while ( membershipListenerIterator.hasNext() ) {
            MembershipListener listener = membershipListenerIterator.next();
            if ( listener instanceof Heartbeat ) ((Heartbeat)listener).heartbeat();
        }
        Iterator<ChannelListener> channelListenerIterator = channelListeners.iterator();
        while ( channelListenerIterator.hasNext() ) {
            ChannelListener listener = channelListenerIterator.next();
            if ( listener instanceof Heartbeat ) ((Heartbeat)listener).heartbeat();
        }

    }


    /**
     * 发送一个消息到集群中一个或多个成员.
     * @param destination Member[] - 目标 &gt; 0
     * @param msg Serializable - 要发送的消息
     * @param options 发送者选项, 选项可以触发不同的拦截器对消息作出反应
     * 
     * @return UniqueId - 发送的消息的惟一的 Id
     * @throws ChannelException - 如果处理消息出现错误
     */
    @Override
    public UniqueId send(Member[] destination, Serializable msg, int options)
            throws ChannelException {
        return send(destination,msg,options,null);
    }

    /**
     * @param destination Member[] - 目标 &gt; 0
     * @param msg Serializable - 要发送的消息
     * @param options 发送者选项, 选项可以触发不同的拦截器对消息作出反应
     * @param handler - 用于错误处理和完成通知的回调对象, 当使用<code>Channel.SEND_OPTIONS_ASYNCHRONOUS</code> 标志启用异步发送消息时使用
     * 
     * @return UniqueId - 发送的消息的惟一的 Id
     * @throws ChannelException - 如果处理消息出现错误
     */
    @Override
    public UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler)
            throws ChannelException {
        if ( msg == null ) throw new ChannelException(sm.getString("groupChannel.nullMessage"));
        XByteBuffer buffer = null;
        try {
            if (destination == null || destination.length == 0) {
                throw new ChannelException(sm.getString("groupChannel.noDestination"));
            }
            ChannelData data = new ChannelData(true);//generates a unique Id
            data.setAddress(getLocalMember(false));
            data.setTimestamp(System.currentTimeMillis());
            byte[] b = null;
            if ( msg instanceof ByteMessage ){
                b = ((ByteMessage)msg).getMessage();
                options = options | SEND_OPTIONS_BYTE_MESSAGE;
            } else {
                b = XByteBuffer.serialize(msg);
                options = options & (~SEND_OPTIONS_BYTE_MESSAGE);
            }
            data.setOptions(options);
            //XByteBuffer buffer = new XByteBuffer(b.length+128,false);
            buffer = BufferPool.getBufferPool().getBuffer(b.length+128, false);
            buffer.append(b,0,b.length);
            data.setMessage(buffer);
            InterceptorPayload payload = null;
            if ( handler != null ) {
                payload = new InterceptorPayload();
                payload.setErrorHandler(handler);
            }
            getFirstInterceptor().sendMessage(destination, data, payload);
            if ( Logs.MESSAGES.isTraceEnabled() ) {
                Logs.MESSAGES.trace("GroupChannel - Sent msg:" + new UniqueId(data.getUniqueId()) +
                        " at " + new java.sql.Timestamp(System.currentTimeMillis()) + " to " +
                        Arrays.toNameString(destination));
                Logs.MESSAGES.trace("GroupChannel - Send Message:" +
                        new UniqueId(data.getUniqueId()) + " is " + msg);
            }

            return new UniqueId(data.getUniqueId());
        }catch ( Exception x ) {
            if ( x instanceof ChannelException ) throw (ChannelException)x;
            throw new ChannelException(x);
        } finally {
            if ( buffer != null ) BufferPool.getBufferPool().returnBuffer(buffer);
        }
    }


    /**
     * 拦截器栈的回调. <br>
     * 当接收远程节点的消息时, 这个方法将通过之前的拦截器调用.<br>
     * 此方法还可用于向同一应用程序中的其他组件发送消息, 但这是一个极端的例子, 最好在应用程序本身之间的逻辑.
     * @param msg ChannelMessage
     */
    @Override
    public void messageReceived(ChannelMessage msg) {
        if ( msg == null ) return;
        try {
            if ( Logs.MESSAGES.isTraceEnabled() ) {
                Logs.MESSAGES.trace("GroupChannel - Received msg:" +
                        new UniqueId(msg.getUniqueId()) + " at " +
                        new java.sql.Timestamp(System.currentTimeMillis()) + " from " +
                        msg.getAddress().getName());
            }

            Serializable fwd = null;
            if ( (msg.getOptions() & SEND_OPTIONS_BYTE_MESSAGE) == SEND_OPTIONS_BYTE_MESSAGE ) {
                fwd = new ByteMessage(msg.getMessage().getBytes());
            } else {
                try {
                    fwd = XByteBuffer.deserialize(msg.getMessage().getBytesDirect(), 0,
                            msg.getMessage().getLength());
                }catch (Exception sx) {
                    log.error(sm.getString("groupChannel.unable.deserialize", msg),sx);
                    return;
                }
            }
            if ( Logs.MESSAGES.isTraceEnabled() ) {
                Logs.MESSAGES.trace("GroupChannel - Receive Message:" +
                        new UniqueId(msg.getUniqueId()) + " is " + fwd);
            }

            //获取实际成员的正确生存时间
            Member source = msg.getAddress();
            boolean rx = false;
            boolean delivered = false;
            for ( int i=0; i<channelListeners.size(); i++ ) {
                ChannelListener channelListener = channelListeners.get(i);
                if (channelListener != null && channelListener.accept(fwd, source)) {
                    channelListener.messageReceived(fwd, source);
                    delivered = true;
                    //如果消息通过RPC channel接收, 该channel负责回复, 否则发送缺席答复
                    if ( channelListener instanceof RpcChannel ) rx = true;
                }
            }
            if ((!rx) && (fwd instanceof RpcMessage)) {
                //如果有一个需要响应的消息, 但是没有响应, 立即送回
                sendNoRpcChannelReply((RpcMessage)fwd,source);
            }
            if ( Logs.MESSAGES.isTraceEnabled() ) {
                Logs.MESSAGES.trace("GroupChannel delivered[" + delivered + "] id:" +
                        new UniqueId(msg.getUniqueId()));
            }

        } catch ( Exception x ) {
            //这可能是channel监听器抛出异常, 应该把它记录为警告.
            if ( log.isWarnEnabled() ) log.warn(sm.getString("groupChannel.receiving.error"),x);
            throw new RemoteProcessException("Exception:"+x.getMessage(),x);
        }
    }

    /**
     * 发送一个<code>NoRpcChannelReply</code>消息给成员<br>
     * 如果一个RPC消息进来并且没有channel监听器接收消息，这个方法将被 channel 调用. 避免了超时
     * @param msg RpcMessage
     * @param destination Member - 回复的目标
     */
    protected void sendNoRpcChannelReply(RpcMessage msg, Member destination) {
        try {
            //avoid circular loop
            if ( msg instanceof RpcMessage.NoRpcChannelReply) return;
            RpcMessage.NoRpcChannelReply reply =
                    new RpcMessage.NoRpcChannelReply(msg.rpcId, msg.uuid);
            send(new Member[]{destination},reply,Channel.SEND_OPTIONS_ASYNCHRONOUS);
        } catch ( Exception x ) {
            log.error(sm.getString("groupChannel.sendFail.noRpcChannelReply"),x);
        }
    }

    /**
     * 由拦截器调用，而且channel 将广播它给成员监听器
     * @param member Member - the new member
     */
    @Override
    public void memberAdded(Member member) {
        //notify upwards
        for (int i=0; i<membershipListeners.size(); i++ ) {
            MembershipListener membershipListener = membershipListeners.get(i);
            if (membershipListener != null) membershipListener.memberAdded(member);
        }
    }

    /**
     * 由拦截器调用，而且channel 将广播它给成员监听器
     * @param member Member - 删除或崩溃的成员
     */
    @Override
    public void memberDisappeared(Member member) {
        //notify upwards
        for (int i=0; i<membershipListeners.size(); i++ ) {
            MembershipListener membershipListener = membershipListeners.get(i);
            if (membershipListener != null) membershipListener.memberDisappeared(member);
        }
    }

    /**
     * 如果没有添加拦截器，则设置默认实现拦截器栈
     * @throws ChannelException Cluster error
     */
    protected synchronized void setupDefaultStack() throws ChannelException {
        if (getFirstInterceptor() != null &&
                ((getFirstInterceptor().getNext() instanceof ChannelCoordinator))) {
            addInterceptor(new MessageDispatchInterceptor());
        }
        Iterator<ChannelInterceptor> interceptors = getInterceptors();
        while (interceptors.hasNext()) {
            ChannelInterceptor channelInterceptor = interceptors.next();
            channelInterceptor.setChannel(this);
        }
        coordinator.setChannel(this);
    }

    /**
     * 验证每个拦截器所使用的选项标志，如果两个拦截器共享相同的标志，则报告错误.
     * 
     * @throws ChannelException Error with option flag
     */
    protected void checkOptionFlags() throws ChannelException {
        StringBuilder conflicts = new StringBuilder();
        ChannelInterceptor first = interceptors;
        while ( first != null ) {
            int flag = first.getOptionFlag();
            if ( flag != 0 ) {
                ChannelInterceptor next = first.getNext();
                while ( next != null ) {
                    int nflag = next.getOptionFlag();
                    if (nflag!=0 && (((flag & nflag) == flag ) || ((flag & nflag) == nflag)) ) {
                        conflicts.append("[");
                        conflicts.append(first.getClass().getName());
                        conflicts.append(":");
                        conflicts.append(flag);
                        conflicts.append(" == ");
                        conflicts.append(next.getClass().getName());
                        conflicts.append(":");
                        conflicts.append(nflag);
                        conflicts.append("] ");
                    }
                    next = next.getNext();
                }
            }
            first = first.getNext();
        }
        if ( conflicts.length() > 0 ) throw new ChannelException(sm.getString("groupChannel.optionFlag.conflict",
                conflicts.toString()));

    }

    /**
     * 启动 channel.
     * @param svc int - 要启动的服务
     * @throws ChannelException Start error
     */
    @Override
    public synchronized void start(int svc) throws ChannelException {
        setupDefaultStack();
        if (optionCheck) checkOptionFlags();
        // register jmx
        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(this);
        if (jmxRegistry != null) this.oname = jmxRegistry.registerJmx(",component=Channel", this);
        super.start(svc);
        if ( hbthread == null && heartbeat ) {
            hbthread = new HeartbeatThread(this,heartbeatSleeptime);
            hbthread.start();
        }
    }

    /**
     * 停止 channel.
     * @param svc int
     * @throws ChannelException Stop error
     */
    @Override
    public synchronized void stop(int svc) throws ChannelException {
        if (hbthread != null) {
            hbthread.stopHeartbeat();
            hbthread = null;
        }
        super.stop(svc);
        if (oname != null) {
            JmxRegistry.getRegistry(this).unregisterJmx(oname);
            oname = null;
        }
    }

    /**
     * 返回栈中的第一个拦截器. 有用的遍历.
     * @return ChannelInterceptor
     */
    public ChannelInterceptor getFirstInterceptor() {
        if (interceptors != null) return interceptors;
        else return coordinator;
    }

    /**
     * 返回channel 接收器组件
     * @return ChannelReceiver
     */
    @Override
    public ChannelReceiver getChannelReceiver() {
        return coordinator.getClusterReceiver();
    }

    /**
     * 返回 channel 发送者组件
     * @return ChannelSender
     */
    @Override
    public ChannelSender getChannelSender() {
        return coordinator.getClusterSender();
    }

    @Override
    public MembershipService getMembershipService() {
        return coordinator.getMembershipService();
    }

    @Override
    public void setChannelReceiver(ChannelReceiver clusterReceiver) {
        coordinator.setClusterReceiver(clusterReceiver);
    }

    @Override
    public void setChannelSender(ChannelSender clusterSender) {
        coordinator.setClusterSender(clusterSender);
    }

    @Override
    public void setMembershipService(MembershipService membershipService) {
        coordinator.setMembershipService(membershipService);
    }

    /**
     * 添加一个成员监听器到 channel.<br>
     * Membership 监听器使用 equals(Object) 方法确定唯一性
     * @param membershipListener MembershipListener
     */
    @Override
    public void addMembershipListener(MembershipListener membershipListener) {
        if (!this.membershipListeners.contains(membershipListener) )
            this.membershipListeners.add(membershipListener);
    }

    /**
     * 删除一个 membership 监听器.<br>
     * Membership 监听器使用 equals(Object) 方法确定唯一性
     * @param membershipListener MembershipListener
     */

    @Override
    public void removeMembershipListener(MembershipListener membershipListener) {
        membershipListeners.remove(membershipListener);
    }

    /**
     * 添加一个channel 监听器到 channel.<br>
     * Channel 监听器使用 equals(Object) 方法确定唯一性
     * @param channelListener ChannelListener
     */
    @Override
    public void addChannelListener(ChannelListener channelListener) {
        if (!this.channelListeners.contains(channelListener) ) {
            this.channelListeners.add(channelListener);
        } else {
            throw new IllegalArgumentException(sm.getString("groupChannel.listener.alreadyExist",
                    channelListener,channelListener.getClass().getName()));
        }
    }

    /**
     *
     * 删除一个 channel 监听器.<br>
     * Channel 监听器使用 equals(Object) 方法确定唯一性
     * @param channelListener ChannelListener
     */
    @Override
    public void removeChannelListener(ChannelListener channelListener) {
        channelListeners.remove(channelListener);
    }

    /**
     * 返回这个栈中所有的拦截器
     */
    @Override
    public Iterator<ChannelInterceptor> getInterceptors() {
        return new InterceptorIterator(this.getNext(),this.coordinator);
    }

    /**
     * 启用或禁用选项检查<br>
     * 设置为 true, GroupChannel 将对拦截器执行冲突检查. 如果两个拦截器使用相同的选项标志, 将在启动时抛出错误.
     * @param optionCheck boolean
     */
    public void setOptionCheck(boolean optionCheck) {
        this.optionCheck = optionCheck;
    }

    /**
     * 配置本地心跳睡眠时间<br>
     * 只有当<code>getHeartbeat()==true</code>时
     * @param heartbeatSleeptime long - 心跳之间以毫秒为单位的睡眠时间
     */
    public void setHeartbeatSleeptime(long heartbeatSleeptime) {
        this.heartbeatSleeptime = heartbeatSleeptime;
    }

    /**
     * 启用或禁用本地心跳.
     * 如果调用了<code>setHeartbeat(true)</code>, 那么 channel 将启动一个内部线程调用<code>Channel.heartbeat()</code>,
     * 每 <code>getHeartbeatSleeptime</code>毫秒一次.
     * @param heartbeat boolean
     */
    @Override
    public void setHeartbeat(boolean heartbeat) {
        this.heartbeat = heartbeat;
    }

    /**
     * 启用或禁用选项检查
     */
    @Override
    public boolean getOptionCheck() {
        return optionCheck;
    }

    /**
     * 启用或禁用本地心跳.
     */
    @Override
    public boolean getHeartbeat() {
        return heartbeat;
    }

    /**
     * 返回内部心跳两次调用<code>Channel.heartbeat()</code>的睡眠时间，以毫秒为单位
     */
    @Override
    public long getHeartbeatSleeptime() {
        return heartbeatSleeptime;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isJmxEnabled() {
        return jmxEnabled;
    }

    @Override
    public void setJmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    @Override
    public String getJmxDomain() {
        return jmxDomain;
    }

    @Override
    public void setJmxDomain(String jmxDomain) {
        this.jmxDomain = jmxDomain;
    }

    @Override
    public String getJmxPrefix() {
        return jmxPrefix;
    }

    @Override
    public void setJmxPrefix(String jmxPrefix) {
        this.jmxPrefix = jmxPrefix;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        // NOOP
        return null;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        // NOOP
    }

    @Override
    public void preDeregister() throws Exception {
        // NOOP
    }

    @Override
    public void postDeregister() {
        JmxRegistry.removeRegistry(this, true);
    }

    /**
     * <p>在一个channel中循环拦截器</p>
     */
    public static class InterceptorIterator implements Iterator<ChannelInterceptor> {
        private final ChannelInterceptor end;
        private ChannelInterceptor start;
        public InterceptorIterator(ChannelInterceptor start, ChannelInterceptor end) {
            this.end = end;
            this.start = start;
        }

        @Override
        public boolean hasNext() {
            return start!=null && start != end;
        }

        @Override
        public ChannelInterceptor next() {
            ChannelInterceptor result = null;
            if ( hasNext() ) {
                result = start;
                start = start.getNext();
            }
            return result;
        }

        @Override
        public void remove() {
            //empty operation
        }
    }

    /**
     * <p>如果 <code>Channel.getHeartbeat()==true</code>, 将创建这个类的一个线程</p>
     */
    public static class HeartbeatThread extends Thread {
        private static final Log log = LogFactory.getLog(HeartbeatThread.class);
        protected static int counter = 1;
        protected static synchronized int inc() {
            return counter++;
        }

        protected volatile boolean doRun = true;
        protected final GroupChannel channel;
        protected final long sleepTime;
        public HeartbeatThread(GroupChannel channel, long sleepTime) {
            super();
            this.setPriority(MIN_PRIORITY);
            String channelName = "";
            if (channel.getName() != null) channelName = "[" + channel.getName() + "]";
            setName("GroupChannel-Heartbeat" + channelName + "-" +inc());
            setDaemon(true);
            this.channel = channel;
            this.sleepTime = sleepTime;
        }
        public void stopHeartbeat() {
            doRun = false;
            interrupt();
        }

        @Override
        public void run() {
            while (doRun) {
                try {
                    Thread.sleep(sleepTime);
                    channel.heartbeat();
                } catch ( InterruptedException x ) {
                    // Ignore. 可能通过调用stopHeartbeat()触发.
                    // 在极不可能发生的事件中，这是一个不同的触发, 简单忽略它并继续.
                } catch ( Exception x ) {
                    log.error(sm.getString("groupChannel.unable.sendHeartbeat"),x);
                }
            }
        }
    }
}
