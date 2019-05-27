package org.apache.catalina.tribes.group.interceptors;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.group.AbsoluteOrder;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.membership.Membership;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.catalina.tribes.util.UUIDGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>一种简单的协调器算法的实现，该算法不仅选择协调器, 当发现成员不属于这个组时，它也自动合并组
 *    </p>
 * <p>该算法是非阻塞的, 这意味着当协调阶段正在进行时, 它允许进行事务处理
 * </p>
 * <p>此实现是基于home brewed算法, 该算法使用成员的AbsoluteOrder 传递当前成员的 token 环.<br>
 * 不同于只是使用 AbsoluteOrder! 考虑下面的情况:<br>
 * 节点, A,B,C,D,E 再一个网络上, 在那个优先权. AbsoluteOrder 只有当所有节点都接收来自所有其他节点的ping时才会工作.
 * 意味着, node{i} 接收从 node{all}-node{i}的ping<br>
 * 但是如果出现多播问题，可能会发生以下情况.
 * A has members {B,C,D}<br>
 * B has members {A,C}<br>
 * C has members {D,E}<br>
 * D has members {A,B,C,E}<br>
 * E has members {A,C,D}<br>
 * 因为默认的Tribes 成员实现类, 依赖于多播数据包正确到达所有节点, 没有什么能保证它会.<br>
 * <br>
 * 最好解释这个算法是如何工作的, 让我们举上面的例子:
 * 为简单起见, 对于所有节点来说, 发送行为是 O(1), 虽然该算法将在消息重叠的情况下工作, 因为它们都依赖于绝对排序<br>
 * Scenario 1: A,B,C,D,E 所有人同时在线.
 * A 认为自己是 leader, B 认为 A 是 leader, C 认为自己是 leader, D,E 认为 A 是 leader<br>
 * Token 阶段:<br>
 * (1) A 发出信息 X{A-ldr, A-src, mbrs-A,B,C,D} 给 B, X 是消息(和视图)的 id<br>
 * (1) C 发出信息 Y{C-ldr, C-src, mbrs-C,D,E} 给 D, Y 是消息(和视图)的 id<br>
 * (2) B 接收 X{A-ldr, A-src, mbrs-A,B,C,D}, 发送 X{A-ldr, A-src, mbrs-A,B,C,D} 给 C <br>
 * (2) D 接收 Y{C-ldr, C-src, mbrs-C,D,E} D 意识到 A,B, 发送 Y{A-ldr, C-src, mbrs-A,B,C,D,E} 给 E<br>
 * (3) C 接收 X{A-ldr, A-src, mbrs-A,B,C,D}, 发送 X{A-ldr, A-src, mbrs-A,B,C,D,E} 给 D<br>
 * (3) E 接收 Y{A-ldr, C-src, mbrs-A,B,C,D,E}, 发送  Y{A-ldr, C-src, mbrs-A,B,C,D,E} 给 A<br>
 * (4) D 接收 X{A-ldr, A-src, mbrs-A,B,C,D,E}, 发送 X{A-ldr, A-src, mbrs-A,B,C,D,E} 给 A<br>
 * (4) A 接收 Y{A-ldr, C-src, mbrs-A,B,C,D,E}, 保存信息, 添加 E 到自己的成员列表<br>
 * (5) A 接收 X{A-ldr, A-src, mbrs-A,B,C,D,E} <br>
 * 在这里, 状态看起来是这样的<br>
 * A - {A-ldr, mbrs-A,B,C,D,E, id=X}<br>
 * B - {A-ldr, mbrs-A,B,C,D, id=X}<br>
 * C - {A-ldr, mbrs-A,B,C,D,E, id=X}<br>
 * D - {A-ldr, mbrs-A,B,C,D,E, id=X}<br>
 * E - {A-ldr, mbrs-A,B,C,D,E, id=Y}<br>
 * <br>
 * 消息在到达原始发送者之前不会停止, 除非它被一个更高的leader抛弃.
 * 正如你所看到的, E 仍然认为 viewId=Y, 这是不正确的. 但是在这一点上, 我们已经到达同一个成员, 并且每个节点都被告知彼此.<br>
 * 为了同步其余部分，只需在A上执行以下检查, 当 A 接收到 X:<br>
 * 原始 X{A-ldr, A-src, mbrs-A,B,C,D} == Arrived X{A-ldr, A-src, mbrs-A,B,C,D,E}<br>
 * 因为条件是false, A, 将重新发送 token, 而且 A 发送 X{A-ldr, A-src, mbrs-A,B,C,D,E} 到 B, 当 A 再次接收 X, token 是完整的. <br>
 * 任意的, A 可以发送消息 X{A-ldr, A-src, mbrs-A,B,C,D,E confirmed} 到 A,B,C,D,E, 它们稍后会安装并接收 view.
 * </p>
 * <p>
 * 假设 C1 到达, C1 比 C有更低的优先权, 但比 D更高的优先级.<br>
 * 假设 C1 看到以下视图 {B,D,E}<br>
 * C1 等待一个 token 的到达. 当token 到达, 同样的情况也会发生.<br>
 * 当 C1 看到 {D,E}, 而且A,B,C 看不到 C1, 不会有 token 到达.<br>
 * 在这种情况下, C1 发送一个 Z{C1-ldr, C1-src, mbrs-C1,D,E} 到 D<br>
 * D 接收 Z{C1-ldr, C1-src, mbrs-C1,D,E} 并发送 Z{A-ldr, C1-src, mbrs-A,B,C,C1,D,E} 到 E<br>
 * E 接收 Z{A-ldr, C1-src, mbrs-A,B,C,C1,D,E} 并发送它到 A<br>
 * A 发送 Z{A-ldr, A-src, mbrs-A,B,C,C1,D,E} 到 B, 而且链条继续, 直到 A 再次接收到 token.
 * 此时, A 可任意的发送 Z{A-ldr, A-src, mbrs-A,B,C,C1,D,E, confirmed} 到 A,B,C,C1,D,E
 * </p>
 * <p>确保视图同时在所有节点上实现, A 将发送一个 VIEW_CONF 消息, 这是上面可选的'confirmed' 消息.
 * <p>理想情况下, 这个的下一个拦截器将会是 TcpFailureDetector 来确保正确的成员</p>
 *
 * <p>上面的例子中, 当然可以用有限的状态机来简化:<br>
 * 但是我很喜欢写状态机, 我的头脑变得混乱. 有一天我会记录这个算法.<br> 也许我会做一个状态图 :)
 * </p>
 * <h2>状态图</h2>
 * <a href="http://people.apache.org/~fhanik/tribes/docs/leader-election-initiate-election.jpg">发起选举</a><br><br>
 * <a href="http://people.apache.org/~fhanik/tribes/docs/leader-election-message-arrives.jpg">收到选举信息</a><br><br>
 */
public class NonBlockingCoordinator extends ChannelInterceptorBase {

    private static final Log log = LogFactory.getLog(NonBlockingCoordinator.class);
    protected static final StringManager sm = StringManager.getManager(NonBlockingCoordinator.class);

    /**
     * 协调消息的header
     */
    protected static final byte[] COORD_HEADER = new byte[] {-86, 38, -34, -29, -98, 90, 65, 63, -81, -122, -6, -110, 99, -54, 13, 63};
    /**
     * 协调请求
     */
    protected static final byte[] COORD_REQUEST = new byte[] {104, -95, -92, -42, 114, -36, 71, -19, -79, 20, 122, 101, -1, -48, -49, 30};
    /**
     * 协调确认, 为了阻塞装置
     */
    protected static final byte[] COORD_CONF = new byte[] {67, 88, 107, -86, 69, 23, 76, -70, -91, -23, -87, -25, -125, 86, 75, 20};

    /**
     * 存活消息
     */
    protected static final byte[] COORD_ALIVE = new byte[] {79, -121, -25, -15, -59, 5, 64, 94, -77, 113, -119, -88, 52, 114, -56, -46,
                                                            -18, 102, 10, 34, -127, -9, 71, 115, -70, 72, -101, 88, 72, -124, 127, 111,
                                                            74, 76, -116, 50, 111, 103, 65, 3, -77, 51, -35, 0, 119, 117, 9, -26,
                                                            119, 50, -75, -105, -102, 36, 79, 37, -68, -84, -123, 15, -22, -109, 106, -55};
    /**
     * 等待协调超时的时间
     */
    protected final long waitForCoordMsgTimeout = 15000;
    /**
     * 当前视图
     */
    protected volatile Membership view = null;
    /**
     * 当前 viewId
     */
    protected UniqueId viewId;

    /**
     * 非阻塞成员
     */
    protected Membership membership = null;

    /**
     * 表明正在竞选，这是正在运行的
     */
    protected UniqueId suggestedviewId;
    protected volatile Membership suggestedView;

    protected volatile boolean started = false;
    protected final int startsvc = 0xFFFF;

    protected final Object electionMutex = new Object();

    protected final AtomicBoolean coordMsgReceived = new AtomicBoolean(false);

    public NonBlockingCoordinator() {
        super();
    }

//============================================================================================================
//              COORDINATION HANDLING
//============================================================================================================

    public void startElection(boolean force) throws ChannelException {
        synchronized (electionMutex) {
            Member local = getLocalMember(false);
            Member[] others = membership.getMembers();
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START_ELECT,this,"Election initiated"));
            if ( others.length == 0 ) {
                this.viewId = new UniqueId(UUIDGenerator.randomUUID(false));
                this.view = new Membership(local,AbsoluteOrder.comp, true);
                this.handleViewConf(createElectionMsg(local,others,local), view);
                return; //惟一的成员, 不需要竞选
            }
            if ( suggestedviewId != null ) {

                if ( view != null && Arrays.diff(view,suggestedView,local).length == 0 &&  Arrays.diff(suggestedView,view,local).length == 0) {
                    suggestedviewId = null;
                    suggestedView = null;
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_ELECT_ABANDONED,this,"Election abandoned, running election matches view"));
                } else {
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_ELECT_ABANDONED,this,"Election abandoned, election running"));
                }
                return; //竞选已经运行, 不允许有两个
            }
            if ( view != null && Arrays.diff(view,membership,local).length == 0 &&  Arrays.diff(membership,view,local).length == 0) {
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_ELECT_ABANDONED,this,"Election abandoned, view matches membership"));
                return; //已经安装了这个视图
            }
            int prio = AbsoluteOrder.comp.compare(local,others[0]);
            Member leader = ( prio < 0 )?local:others[0];//am I the leader in my view?
            if ( local.equals(leader) || force ) {
                CoordinationMessage msg = createElectionMsg(local, others, leader);
                suggestedviewId = msg.getId();
                suggestedView = new Membership(local,AbsoluteOrder.comp,true);
                Arrays.fill(suggestedView,msg.getMembers());
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_PROCESS_ELECT,this,"Election, sending request"));
                sendElectionMsg(local,others[0],msg);
            } else {
                try {
                    coordMsgReceived.set(false);
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_WAIT_FOR_MSG,this,"Election, waiting for request"));
                    electionMutex.wait(waitForCoordMsgTimeout);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
                String msg;
                if (suggestedviewId == null && !coordMsgReceived.get()) {
                    if (Thread.interrupted()) {
                        msg = "Election abandoned, waiting interrupted.";
                    } else {
                        msg = "Election abandoned, waiting timed out.";
                    }
                } else {
                    msg = "Election abandoned, received a message";
                }
                fireInterceptorEvent(new CoordinationEvent(
                        CoordinationEvent.EVT_ELECT_ABANDONED, this, msg));
            }
        }
    }

    private CoordinationMessage createElectionMsg(Member local, Member[] others, Member leader) {
        Membership m = new Membership(local,AbsoluteOrder.comp,true);
        Arrays.fill(m,others);
        Member[] mbrs = m.getMembers();
        m.reset();
        CoordinationMessage msg = new CoordinationMessage(leader, local, mbrs,new UniqueId(UUIDGenerator.randomUUID(true)), COORD_REQUEST);
        return msg;
    }

    protected void sendElectionMsg(Member local, Member next, CoordinationMessage msg) throws ChannelException {
        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_SEND_MSG,this,"Sending election message to("+next.getName()+")"));
        super.sendMessage(new Member[] {next}, createData(msg, local), null);
    }

    protected void sendElectionMsgToNextInline(Member local, CoordinationMessage msg) throws ChannelException {
        int next = Arrays.nextIndex(local,msg.getMembers());
        int current = next;
        msg.leader = msg.getMembers()[0];
        boolean sent =  false;
        while ( !sent && current >= 0 ) {
            try {
                sendElectionMsg(local, msg.getMembers()[current], msg);
                sent = true;
            }catch ( ChannelException x  ) {
                log.warn(sm.getString("nonBlockingCoordinator.electionMessage.sendfailed", msg.getMembers()[current]));
                current = Arrays.nextIndex(msg.getMembers()[current],msg.getMembers());
                if ( current == next ) throw x;
            }
        }
    }

    public ChannelData createData(CoordinationMessage msg, Member local) {
        msg.write();
        ChannelData data = new ChannelData(true);
        data.setAddress(local);
        data.setMessage(msg.getBuffer());
        data.setOptions(Channel.SEND_OPTIONS_USE_ACK);
        data.setTimestamp(System.currentTimeMillis());
        return data;
    }

    protected boolean alive(Member mbr) {
        return memberAlive(mbr, waitForCoordMsgTimeout);
    }

    protected boolean memberAlive(Member mbr, long conTimeout) {
        //可能是关机通知
        if ( Arrays.equals(mbr.getCommand(),Member.SHUTDOWN_PAYLOAD) ) return false;

        try (Socket socket = new Socket()) {
            InetAddress ia = InetAddress.getByAddress(mbr.getHost());
            InetSocketAddress addr = new InetSocketAddress(ia, mbr.getPort());
            socket.connect(addr, (int) conTimeout);
            return true;
        } catch (SocketTimeoutException sx) {
            //do nothing, we couldn't connect
        } catch (ConnectException cx) {
            //do nothing, we couldn't connect
        } catch (Exception x) {
            log.error(sm.getString("nonBlockingCoordinator.memberAlive.failed"),x);
        }
        return false;
    }

    protected Membership mergeOnArrive(CoordinationMessage msg) {
        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_PRE_MERGE,this,"Pre merge"));
        Member local = getLocalMember(false);
        Membership merged = new Membership(local,AbsoluteOrder.comp,true);
        Arrays.fill(merged,msg.getMembers());
        Arrays.fill(merged,getMembers());
        Member[] diff = Arrays.diff(merged,membership,local);
        for ( int i=0; i<diff.length; i++ ) {
            if (!alive(diff[i])) merged.removeMember(diff[i]);
            else memberAdded(diff[i],false);
        }
        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_POST_MERGE,this,"Post merge"));
        return merged;
    }

    protected void processCoordMessage(CoordinationMessage msg) throws ChannelException {
        if ( !coordMsgReceived.get() ) {
            coordMsgReceived.set(true);
            synchronized (electionMutex) { electionMutex.notifyAll();}
        }
        Membership merged = mergeOnArrive(msg);
        if (isViewConf(msg)) handleViewConf(msg, merged);
        else handleToken(msg, merged);
    }

    protected void handleToken(CoordinationMessage msg, Membership merged) throws ChannelException {
        Member local = getLocalMember(false);
        if ( local.equals(msg.getSource()) ) {
            //my message msg.src=local
            handleMyToken(local, msg, merged);
        } else {
            handleOtherToken(local, msg, merged);
        }
    }

    protected void handleMyToken(Member local, CoordinationMessage msg, Membership merged) throws ChannelException {
        if ( local.equals(msg.getLeader()) ) {
            //no leadership change
            if ( Arrays.sameMembers(msg.getMembers(),merged.getMembers()) ) {
                msg.type = COORD_CONF;
                super.sendMessage(Arrays.remove(msg.getMembers(),local),createData(msg,local),null);
                handleViewConf(msg, merged);
            } else {
                //membership change
                suggestedView = new Membership(local,AbsoluteOrder.comp,true);
                suggestedviewId = msg.getId();
                Arrays.fill(suggestedView,merged.getMembers());
                msg.view = merged.getMembers();
                sendElectionMsgToNextInline(local,msg);
            }
        } else {
            //leadership change
            suggestedView = null;
            suggestedviewId = null;
            msg.view = merged.getMembers();
            sendElectionMsgToNextInline(local,msg);
        }
    }

    protected void handleOtherToken(Member local, CoordinationMessage msg, Membership merged) throws ChannelException {
        if ( local.equals(msg.getLeader()) ) {
            //I am the new leader
            //startElection(false);
        } else {
            msg.view = merged.getMembers();
            sendElectionMsgToNextInline(local,msg);
        }
    }

    protected void handleViewConf(CoordinationMessage msg, Membership merged) throws ChannelException {
        if ( viewId != null && msg.getId().equals(viewId) ) return;//we already have this view
        view = new Membership(getLocalMember(false),AbsoluteOrder.comp,true);
        Arrays.fill(view,msg.getMembers());
        viewId = msg.getId();

        if ( viewId.equals(suggestedviewId) ) {
            suggestedView = null;
            suggestedviewId = null;
        }

        if (suggestedView != null && AbsoluteOrder.comp.compare(suggestedView.getMembers()[0],merged.getMembers()[0])<0 ) {
            suggestedView = null;
            suggestedviewId = null;
        }

        fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_CONF_RX,this,"Accepted View"));

        if ( suggestedviewId == null && hasHigherPriority(merged.getMembers(),membership.getMembers()) ) {
            startElection(false);
        }
    }

    protected boolean isViewConf(CoordinationMessage msg) {
        return Arrays.contains(msg.getType(),0,COORD_CONF,0,COORD_CONF.length);
    }

    protected boolean hasHigherPriority(Member[] complete, Member[] local) {
        if ( local == null || local.length == 0 ) return false;
        if ( complete == null || complete.length == 0 ) return true;
        AbsoluteOrder.absoluteOrder(complete);
        AbsoluteOrder.absoluteOrder(local);
        return (AbsoluteOrder.comp.compare(complete[0],local[0]) > 0);

    }


    /**
     * 返回一个协调员，如果是可用的
     * @return Member
     */
    public Member getCoordinator() {
        return (view != null && view.hasMembers()) ? view.getMembers()[0] : null;
    }

    public Member[] getView() {
        return (view != null && view.hasMembers()) ? view.getMembers() : new Member[0];
    }

    public UniqueId getViewId() {
        return viewId;
    }

    /**
    * 阻塞输入/输出消息, 当竞选的时候
    */
   protected void halt() {

   }

   /**
    * 释放输入/输出消息的锁, 当竞选完成时
    */
   protected void release() {

   }

   /**
    * 等待竞选完成
    */
   protected void waitForRelease() {

   }


//============================================================================================================
//              OVERRIDDEN METHODS FROM CHANNEL INTERCEPTOR BASE
//============================================================================================================
    @Override
    public void start(int svc) throws ChannelException {
            if (membership == null) setupMembership();
            if (started)return;
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START, this, "Before start"));
            super.start(startsvc);
            started = true;
            if (view == null) view = new Membership(super.getLocalMember(true), AbsoluteOrder.comp, true);
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START, this, "After start"));
            startElection(false);
    }

    @Override
    public void stop(int svc) throws ChannelException {
        try {
            halt();
            synchronized (electionMutex) {
                if (!started)return;
                started = false;
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_STOP, this, "Before stop"));
                super.stop(startsvc);
                this.view = null;
                this.viewId = null;
                this.suggestedView = null;
                this.suggestedviewId = null;
                this.membership.reset();
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_STOP, this, "After stop"));
            }
        }finally {
            release();
        }
    }


    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        waitForRelease();
        super.sendMessage(destination, msg, payload);
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if ( Arrays.contains(msg.getMessage().getBytesDirect(),0,COORD_ALIVE,0,COORD_ALIVE.length) ) {
            //忽略消息, 它的存活消息
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MSG_ARRIVE,this,"Alive Message"));

        } else if ( Arrays.contains(msg.getMessage().getBytesDirect(),0,COORD_HEADER,0,COORD_HEADER.length) ) {
            try {
                CoordinationMessage cmsg = new CoordinationMessage(msg.getMessage());
                Member[] cmbr = cmsg.getMembers();
                fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MSG_ARRIVE,this,"Coord Msg Arrived("+Arrays.toNameString(cmbr)+")"));
                processCoordMessage(cmsg);
            }catch ( ChannelException x ) {
                log.error(sm.getString("nonBlockingCoordinator.processCoordinationMessage.failed"),x);
            }
        } else {
            super.messageReceived(msg);
        }
    }

    @Override
    public void memberAdded(Member member) {
        memberAdded(member,true);
    }

    public void memberAdded(Member member,boolean elect) {
        if (membership == null) setupMembership();
        if (membership.memberAlive(member)) super.memberAdded(member);
        try {
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MBR_ADD,this,"Member add("+member.getName()+")"));
            if (started && elect) startElection(false);
        } catch (ChannelException x) {
            log.error(sm.getString("nonBlockingCoordinator.memberAdded.failed"),x);
        }
    }

    @Override
    public void memberDisappeared(Member member) {
        membership.removeMember(member);
        super.memberDisappeared(member);
        try {
            fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_MBR_DEL,this,"Member remove("+member.getName()+")"));
            if (started && (isCoordinator() || isHighest()))
                startElection(true); //to do, if a member disappears, only the coordinator can start
        } catch (ChannelException x) {
            log.error(sm.getString("nonBlockingCoordinator.memberDisappeared.failed"),x);
        }
    }

    public boolean isHighest() {
        Member local = getLocalMember(false);
        if ( membership.getMembers().length == 0 ) return true;
        else return AbsoluteOrder.comp.compare(local,membership.getMembers()[0])<=0;
    }

    public boolean isCoordinator() {
        Member coord = getCoordinator();
        return coord != null && getLocalMember(false).equals(coord);
    }

    @Override
    public void heartbeat() {
        try {
            Member local = getLocalMember(false);
            if ( view != null && (Arrays.diff(view,membership,local).length != 0 ||  Arrays.diff(membership,view,local).length != 0) ) {
                if ( isHighest() ) {
                    fireInterceptorEvent(new CoordinationEvent(CoordinationEvent.EVT_START_ELECT, this,
                            sm.getString("nonBlockingCoordinator.heartbeat.inconsistency")));
                    startElection(true);
                }
            }
        } catch ( Exception x  ){
            log.error(sm.getString("nonBlockingCoordinator.heartbeat.failed"),x);
        } finally {
            super.heartbeat();
        }
    }

    /**
     * 是否有成员
     */
    @Override
    public boolean hasMembers() {
        return membership.hasMembers();
    }

    /**
     * 获取当前的所有集群成员
     * @return 所有成员或空数组
     */
    @Override
    public Member[] getMembers() {
        return membership.getMembers();
    }

    /**
     * @param mbr Member
     * @return Member
     */
    @Override
    public Member getMember(Member mbr) {
        return membership.getMember(mbr);
    }

    /**
     * 返回表示此节点的成员.
     */
    @Override
    public Member getLocalMember(boolean incAlive) {
        Member local = super.getLocalMember(incAlive);
        if ( view == null && (local != null)) setupMembership();
        return local;
    }

    protected synchronized void setupMembership() {
        if ( membership == null ) {
            membership  = new Membership(super.getLocalMember(true),AbsoluteOrder.comp,false);
        }
    }


//============================================================================================================
//              HELPER CLASSES FOR COORDINATION
//============================================================================================================


    public static class CoordinationMessage {
        //X{A-ldr, A-src, mbrs-A,B,C,D}
        protected final XByteBuffer buf;
        protected Member leader;
        protected Member source;
        protected Member[] view;
        protected UniqueId id;
        protected byte[] type;

        public CoordinationMessage(XByteBuffer buf) {
            this.buf = buf;
            parse();
        }

        public CoordinationMessage(Member leader,
                                   Member source,
                                   Member[] view,
                                   UniqueId id,
                                   byte[] type) {
            this.buf = new XByteBuffer(4096,false);
            this.leader = leader;
            this.source = source;
            this.view = view;
            this.id = id;
            this.type = type;
            this.write();
        }


        public byte[] getHeader() {
            return NonBlockingCoordinator.COORD_HEADER;
        }

        public Member getLeader() {
            if ( leader == null ) parse();
            return leader;
        }

        public Member getSource() {
            if ( source == null ) parse();
            return source;
        }

        public UniqueId getId() {
            if ( id == null ) parse();
            return id;
        }

        public Member[] getMembers() {
            if ( view == null ) parse();
            return view;
        }

        public byte[] getType() {
            if (type == null ) parse();
            return type;
        }

        public XByteBuffer getBuffer() {
            return this.buf;
        }

        public void parse() {
            //header
            int offset = 16;
            //leader
            int ldrLen = XByteBuffer.toInt(buf.getBytesDirect(),offset);
            offset += 4;
            byte[] ldr = new byte[ldrLen];
            System.arraycopy(buf.getBytesDirect(),offset,ldr,0,ldrLen);
            leader = MemberImpl.getMember(ldr);
            offset += ldrLen;
            //source
            int srcLen = XByteBuffer.toInt(buf.getBytesDirect(),offset);
            offset += 4;
            byte[] src = new byte[srcLen];
            System.arraycopy(buf.getBytesDirect(),offset,src,0,srcLen);
            source = MemberImpl.getMember(src);
            offset += srcLen;
            //view
            int mbrCount = XByteBuffer.toInt(buf.getBytesDirect(),offset);
            offset += 4;
            view = new Member[mbrCount];
            for (int i=0; i<view.length; i++ ) {
                int mbrLen = XByteBuffer.toInt(buf.getBytesDirect(),offset);
                offset += 4;
                byte[] mbr = new byte[mbrLen];
                System.arraycopy(buf.getBytesDirect(), offset, mbr, 0, mbrLen);
                view[i] = MemberImpl.getMember(mbr);
                offset += mbrLen;
            }
            //id
            this.id = new UniqueId(buf.getBytesDirect(),offset,16);
            offset += 16;
            type = new byte[16];
            System.arraycopy(buf.getBytesDirect(), offset, type, 0, type.length);
            offset += 16;

        }

        public void write() {
            buf.reset();
            //header
            buf.append(COORD_HEADER,0,COORD_HEADER.length);
            //leader
            byte[] ldr = leader.getData(false,false);
            buf.append(ldr.length);
            buf.append(ldr,0,ldr.length);
            ldr = null;
            //source
            byte[] src = source.getData(false,false);
            buf.append(src.length);
            buf.append(src,0,src.length);
            src = null;
            //view
            buf.append(view.length);
            for (int i=0; i<view.length; i++ ) {
                byte[] mbr = view[i].getData(false,false);
                buf.append(mbr.length);
                buf.append(mbr,0,mbr.length);
            }
            //id
            buf.append(id.getBytes(),0,id.getBytes().length);
            buf.append(type,0,type.length);
        }
    }

    @Override
    public void fireInterceptorEvent(InterceptorEvent event) {
        if (event instanceof CoordinationEvent &&
            ((CoordinationEvent)event).type == CoordinationEvent.EVT_CONF_RX)
            log.info(event);
    }

    public static class CoordinationEvent implements InterceptorEvent {
        public static final int EVT_START = 1;
        public static final int EVT_MBR_ADD = 2;
        public static final int EVT_MBR_DEL = 3;
        public static final int EVT_START_ELECT = 4;
        public static final int EVT_PROCESS_ELECT = 5;
        public static final int EVT_MSG_ARRIVE = 6;
        public static final int EVT_PRE_MERGE = 7;
        public static final int EVT_POST_MERGE = 8;
        public static final int EVT_WAIT_FOR_MSG = 9;
        public static final int EVT_SEND_MSG = 10;
        public static final int EVT_STOP = 11;
        public static final int EVT_CONF_RX = 12;
        public static final int EVT_ELECT_ABANDONED = 13;

        final int type;
        final ChannelInterceptor interceptor;
        final Member coord;
        final Member[] mbrs;
        final String info;
        final Membership view;
        final Membership suggestedView;
        public CoordinationEvent(int type,ChannelInterceptor interceptor, String info) {
            this.type = type;
            this.interceptor = interceptor;
            this.coord = ((NonBlockingCoordinator)interceptor).getCoordinator();
            this.mbrs = ((NonBlockingCoordinator)interceptor).membership.getMembers();
            this.info = info;
            this.view = ((NonBlockingCoordinator)interceptor).view;
            this.suggestedView = ((NonBlockingCoordinator)interceptor).suggestedView;
        }

        @Override
        public int getEventType() {
            return type;
        }

        @Override
        public String getEventTypeDesc() {
            switch (type) {
                case  EVT_START: return "EVT_START:"+info;
                case  EVT_MBR_ADD: return "EVT_MBR_ADD:"+info;
                case  EVT_MBR_DEL: return "EVT_MBR_DEL:"+info;
                case  EVT_START_ELECT: return "EVT_START_ELECT:"+info;
                case  EVT_PROCESS_ELECT: return "EVT_PROCESS_ELECT:"+info;
                case  EVT_MSG_ARRIVE: return "EVT_MSG_ARRIVE:"+info;
                case  EVT_PRE_MERGE: return "EVT_PRE_MERGE:"+info;
                case  EVT_POST_MERGE: return "EVT_POST_MERGE:"+info;
                case  EVT_WAIT_FOR_MSG: return "EVT_WAIT_FOR_MSG:"+info;
                case  EVT_SEND_MSG: return "EVT_SEND_MSG:"+info;
                case  EVT_STOP: return "EVT_STOP:"+info;
                case  EVT_CONF_RX: return "EVT_CONF_RX:"+info;
                case EVT_ELECT_ABANDONED: return "EVT_ELECT_ABANDONED:"+info;
                default: return "Unknown";
            }
        }

        @Override
        public ChannelInterceptor getInterceptor() {
            return interceptor;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("CoordinationEvent[type=");
            buf.append(type).append("\n\tLocal:");
            Member local = interceptor.getLocalMember(false);
            buf.append(local!=null?local.getName():"").append("\n\tCoord:");
            buf.append(coord!=null?coord.getName():"").append("\n\tView:");
            buf.append(Arrays.toNameString(view!=null?view.getMembers():null)).append("\n\tSuggested View:");
            buf.append(Arrays.toNameString(suggestedView!=null?suggestedView.getMembers():null)).append("\n\tMembers:");
            buf.append(Arrays.toNameString(mbrs)).append("\n\tInfo:");
            buf.append(info).append("]");
            return buf.toString();
        }
    }
}