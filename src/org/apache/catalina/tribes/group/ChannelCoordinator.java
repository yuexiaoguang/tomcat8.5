package org.apache.catalina.tribes.group;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.ChannelSender;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.MessageListener;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.membership.McastService;
import org.apache.catalina.tribes.membership.StaticMember;
import org.apache.catalina.tribes.transport.ReplicationTransmitter;
import org.apache.catalina.tribes.transport.SenderState;
import org.apache.catalina.tribes.transport.nio.NioReceiver;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.Logs;
import org.apache.catalina.tribes.util.StringManager;


/**
 * channel协调器对象协调成员服务、发送方和接收方.
 * 这是链中的最后一个拦截器.
 */
public class ChannelCoordinator extends ChannelInterceptorBase implements MessageListener {
    protected static final StringManager sm = StringManager.getManager(ChannelCoordinator.class);
    private ChannelReceiver clusterReceiver;
    private ChannelSender clusterSender;
    private MembershipService membershipService;

    private int startLevel = 0;

    public ChannelCoordinator() {
        this(new NioReceiver(), new ReplicationTransmitter(),
                new McastService());
    }

    public ChannelCoordinator(ChannelReceiver receiver,
                              ChannelSender sender,
                              MembershipService service) {

        this.optionFlag = Channel.SEND_OPTIONS_BYTE_MESSAGE |
                Channel.SEND_OPTIONS_USE_ACK |
                Channel.SEND_OPTIONS_SYNCHRONIZED_ACK;

        this.setClusterReceiver(receiver);
        this.setClusterSender(sender);
        this.setMembershipService(service);
    }

    /**
     * 发送消息到集群中的一个或多个成员.
     * @param destination Member[] - 目标, null 或空数组意味着发送给所有人
     * @param msg ClusterMessage - 要发送的消息
     * @param payload TBA
     */
    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        if ( destination == null ) destination = membershipService.getMembers();
        if ((msg.getOptions()&Channel.SEND_OPTIONS_MULTICAST) == Channel.SEND_OPTIONS_MULTICAST) {
            membershipService.broadcast(msg);
        } else {
            clusterSender.sendMessage(msg,destination);
        }
        if ( Logs.MESSAGES.isTraceEnabled() ) {
            Logs.MESSAGES.trace("ChannelCoordinator - Sent msg:" + new UniqueId(msg.getUniqueId()) +
                    " at " + new java.sql.Timestamp(System.currentTimeMillis()) + " to " +
                    Arrays.toNameString(destination));
        }
    }


    /**
     * 启动channel. 对于个别服务来说，这可以多次调用.
     * 
     * @param svc 以下值：<BR>
     * Channel.DEFAULT - 将启动所有的服务 <BR>
     * Channel.MBR_RX_SEQ - 启动接收器 <BR>
     * Channel.MBR_TX_SEQ - 启动广播器 <BR>
     * Channel.SND_TX_SEQ - 启动复制发送器<BR>
     * Channel.SND_RX_SEQ - 启动复制接收器<BR>
     * @throws ChannelException 如果发生启动错误或服务已启动.
     */
    @Override
    public void start(int svc) throws ChannelException {
        this.internalStart(svc);
    }

    /**
     * 关闭 channel. 对于个别服务来说，这可以多次调用.
     * 
     * @param svc 以下值：<BR>
     * Channel.DEFAULT - 将关闭所有服务 <BR>
     * Channel.MBR_RX_SEQ - 停止接收器 <BR>
     * Channel.MBR_TX_SEQ - 停止广播器 <BR>
     * Channel.SND_TX_SEQ - 停止复制发送器<BR>
     * Channel.SND_RX_SEQ - 停止复制接收器<BR>
     * @throws ChannelException 如果发生启动错误或服务已启动.
     */
    @Override
    public void stop(int svc) throws ChannelException {
        this.internalStop(svc);
    }


    /**
     * 启动channel. 对于个别服务来说，这可以多次调用.
     * 
     * @param svc 以下值：<BR>
     * Channel.DEFAULT - 将启动所有的服务 <BR>
     * Channel.MBR_RX_SEQ - 启动接收器 <BR>
     * Channel.MBR_TX_SEQ - 启动广播器 <BR>
     * Channel.SND_TX_SEQ - 启动复制发送器<BR>
     * Channel.SND_RX_SEQ - 启动复制接收器<BR>
     * @throws ChannelException 如果发生启动错误或服务已启动.
     */
    protected synchronized void internalStart(int svc) throws ChannelException {
        try {
            boolean valid = false;
            //确保不传递任何与底层无关的标志
            svc = svc & Channel.DEFAULT;

            if (startLevel == Channel.DEFAULT) return; //已经启动了所有组件
            if (svc == 0 ) return;//nothing to start

            if (svc == (svc & startLevel)) {
                throw new ChannelException(sm.getString("channelCoordinator.alreadyStarted",
                        Integer.toString(svc)));
            }

            //必须首先启动接收器，以便可以用本地成员设置来协调它所侦听的端口
            if ( Channel.SND_RX_SEQ==(svc & Channel.SND_RX_SEQ) ) {
                clusterReceiver.setMessageListener(this);
                clusterReceiver.setChannel(getChannel());
                clusterReceiver.start();
                //synchronize, big time FIXME
                Member localMember = getChannel().getLocalMember(false);
                if (localMember instanceof StaticMember) {
                    // static member
                    StaticMember staticMember = (StaticMember)localMember;
                    staticMember.setHost(getClusterReceiver().getHost());
                    staticMember.setPort(getClusterReceiver().getPort());
                    staticMember.setSecurePort(getClusterReceiver().getSecurePort());
                } else {
                    // multicast member
                    membershipService.setLocalMemberProperties(getClusterReceiver().getHost(),
                            getClusterReceiver().getPort(),
                            getClusterReceiver().getSecurePort(),
                            getClusterReceiver().getUdpPort());
                }
                valid = true;
            }
            if ( Channel.SND_TX_SEQ==(svc & Channel.SND_TX_SEQ) ) {
                clusterSender.setChannel(getChannel());
                clusterSender.start();
                valid = true;
            }

            if ( Channel.MBR_RX_SEQ==(svc & Channel.MBR_RX_SEQ) ) {
                membershipService.setMembershipListener(this);
                membershipService.setChannel(getChannel());
                if (membershipService instanceof McastService) {
                    ((McastService)membershipService).setMessageListener(this);
                }
                membershipService.start(MembershipService.MBR_RX);
                valid = true;
            }
            if ( Channel.MBR_TX_SEQ==(svc & Channel.MBR_TX_SEQ) ) {
                membershipService.setChannel(getChannel());
                membershipService.start(MembershipService.MBR_TX);
                valid = true;
            }

            if (!valid) {
                throw new IllegalArgumentException(sm.getString("channelCoordinator.invalid.startLevel"));
            }
            startLevel = (startLevel | svc);
        }catch ( ChannelException cx ) {
            throw cx;
        }catch ( Exception x ) {
            throw new ChannelException(x);
        }
    }

    /**
     * 关闭 channel. 对于个别服务来说，这可以多次调用.
     * 
     * @param svc 以下值：<BR>
     * Channel.DEFAULT - 将关闭所有服务 <BR>
     * Channel.MBR_RX_SEQ - 停止接收器 <BR>
     * Channel.MBR_TX_SEQ - 停止广播器 <BR>
     * Channel.SND_TX_SEQ - 停止复制发送器<BR>
     * Channel.SND_RX_SEQ - 停止复制接收器<BR>
     * @throws ChannelException 如果发生启动错误或服务已启动.
     */
    protected synchronized void internalStop(int svc) throws ChannelException {
        try {
            //确保不传递任何与底层无关的标志
            svc = svc & Channel.DEFAULT;

            if (startLevel == 0) return; //已经停止了所有组件
            if (svc == 0 ) return;//nothing to stop

            boolean valid = false;
            if ( Channel.SND_RX_SEQ==(svc & Channel.SND_RX_SEQ) ) {
                clusterReceiver.stop();
                clusterReceiver.setMessageListener(null);
                valid = true;
            }
            if ( Channel.SND_TX_SEQ==(svc & Channel.SND_TX_SEQ) ) {
                clusterSender.stop();
                valid = true;
            }

            if ( Channel.MBR_RX_SEQ==(svc & Channel.MBR_RX_SEQ) ) {
                membershipService.stop(MembershipService.MBR_RX);
                membershipService.setMembershipListener(null);
                valid = true;

            }
            if ( Channel.MBR_TX_SEQ==(svc & Channel.MBR_TX_SEQ) ) {
                valid = true;
                membershipService.stop(MembershipService.MBR_TX);
            }
            if ( !valid) {
                throw new IllegalArgumentException(sm.getString("channelCoordinator.invalid.startLevel"));
            }

            startLevel = (startLevel & (~svc));
            setChannel(null);
        } catch (Exception x) {
            throw new ChannelException(x);
        }
    }

    @Override
    public void memberAdded(Member member){
        SenderState.getSenderState(member);
        super.memberAdded(member);
    }

    @Override
    public void memberDisappeared(Member member){
        SenderState.removeSenderState(member);
        super.memberDisappeared(member);
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if ( Logs.MESSAGES.isTraceEnabled() ) {
            Logs.MESSAGES.trace("ChannelCoordinator - Received msg:" +
                    new UniqueId(msg.getUniqueId()) + " at " +
                    new java.sql.Timestamp(System.currentTimeMillis()) + " from " +
                    msg.getAddress().getName());
        }
        super.messageReceived(msg);
    }

    @Override
    public boolean accept(ChannelMessage msg) {
        return true;
    }

    public ChannelReceiver getClusterReceiver() {
        return clusterReceiver;
    }

    public ChannelSender getClusterSender() {
        return clusterSender;
    }

    public MembershipService getMembershipService() {
        return membershipService;
    }

    public void setClusterReceiver(ChannelReceiver clusterReceiver) {
        if ( clusterReceiver != null ) {
            this.clusterReceiver = clusterReceiver;
            this.clusterReceiver.setMessageListener(this);
        } else {
            if  (this.clusterReceiver!=null ) this.clusterReceiver.setMessageListener(null);
            this.clusterReceiver = null;
        }
    }

    public void setClusterSender(ChannelSender clusterSender) {
        this.clusterSender = clusterSender;
    }

    public void setMembershipService(MembershipService membershipService) {
        this.membershipService = membershipService;
        this.membershipService.setMembershipListener(this);
    }

    @Override
    public void heartbeat() {
        if ( clusterSender!=null ) clusterSender.heartbeat();
        super.heartbeat();
    }

    /**
     * 是否有成员
     */
    @Override
    public boolean hasMembers() {
        return this.getMembershipService().hasMembers();
    }

    /**
     * 获取当前集群的所有成员
     * @return 所有成员或空数组
     */
    @Override
    public Member[] getMembers() {
        return this.getMembershipService().getMembers();
    }

    /**
     * @param mbr Member
     * @return Member
     */
    @Override
    public Member getMember(Member mbr){
        return this.getMembershipService().getMember(mbr);
    }


    /**
     * 返回表示这个节点的成员.
     *
     * @return Member
     */
    @Override
    public Member getLocalMember(boolean incAlive) {
        return this.getMembershipService().getLocalMember(incAlive);
    }
}
