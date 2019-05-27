package org.apache.catalina.ha.session;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.io.ReplicationStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * DeltaManager仅通过复制数据中的增量来管理复制的会话.
 * 用于编写处理此应用程序, DeltaManager是复制数据的最佳方式.
 *
 * 这个代码几乎和StandardManager一样，唯一的区别是持久化会话和对它的一些修改.
 *
 * <b>IMPLEMENTATION NOTE </b>: 会话存储和重载的正确行为取决于在正确的时间从外部调用这个类的<code>start()</code>和<code>stop()</code>方法.
 */
public class DeltaManager extends ClusterManagerBase{

    // ---------------------------------------------------- Security Classes
    public final Log log = LogFactory.getLog(DeltaManager.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(DeltaManager.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * 这个Manager实现类的描述名称(用于记录日志).
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    protected static final String managerName = "DeltaManager";
    protected String name = null;

    private boolean expireSessionsOnShutdown = false;
    private boolean notifySessionListenersOnReplication = true;
    private boolean notifyContainerListenersOnReplication  = true;
    private volatile boolean stateTransfered = false ;
    private volatile boolean noContextManagerReceived = false ;
    private int stateTransferTimeout = 60;
    private boolean sendAllSessions = true;
    private int sendAllSessionsSize = 1000 ;

    /**
     * 发送会话块之间的等待时间(default 2 sec)
     */
    private int sendAllSessionsWaitTime = 2 * 1000 ;
    private final ArrayList<SessionMessage> receivedMessageQueue =
            new ArrayList<>();
    private boolean receiverQueue = false ;
    private boolean stateTimestampDrop = true ;
    private long stateTransferCreateSendTime;

    // -------------------------------------------------------- stats attributes

    private long sessionReplaceCounter = 0 ;
    private long counterReceive_EVT_GET_ALL_SESSIONS = 0 ;
    private long counterReceive_EVT_ALL_SESSION_DATA = 0 ;
    private long counterReceive_EVT_SESSION_CREATED = 0 ;
    private long counterReceive_EVT_SESSION_EXPIRED = 0;
    private long counterReceive_EVT_SESSION_ACCESSED = 0 ;
    private long counterReceive_EVT_SESSION_DELTA = 0;
    private int counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE = 0 ;
    private long counterReceive_EVT_CHANGE_SESSION_ID = 0 ;
    private long counterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER = 0 ;
    private long counterSend_EVT_GET_ALL_SESSIONS = 0 ;
    private long counterSend_EVT_ALL_SESSION_DATA = 0 ;
    private long counterSend_EVT_SESSION_CREATED = 0;
    private long counterSend_EVT_SESSION_DELTA = 0 ;
    private long counterSend_EVT_SESSION_ACCESSED = 0;
    private long counterSend_EVT_SESSION_EXPIRED = 0;
    private int counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE = 0 ;
    private long counterSend_EVT_CHANGE_SESSION_ID = 0;
    private int counterNoStateTransfered = 0 ;


    // ------------------------------------------------------------- Constructor
    public DeltaManager() {
        super();
    }

    // ------------------------------------------------------------- Properties

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public long getCounterSend_EVT_GET_ALL_SESSIONS() {
        return counterSend_EVT_GET_ALL_SESSIONS;
    }

    public long getCounterSend_EVT_SESSION_ACCESSED() {
        return counterSend_EVT_SESSION_ACCESSED;
    }

    public long getCounterSend_EVT_SESSION_CREATED() {
        return counterSend_EVT_SESSION_CREATED;
    }

    public long getCounterSend_EVT_SESSION_DELTA() {
        return counterSend_EVT_SESSION_DELTA;
    }

    public long getCounterSend_EVT_SESSION_EXPIRED() {
        return counterSend_EVT_SESSION_EXPIRED;
    }

    public long getCounterSend_EVT_ALL_SESSION_DATA() {
        return counterSend_EVT_ALL_SESSION_DATA;
    }

    public int getCounterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE() {
        return counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE;
    }

    public long getCounterSend_EVT_CHANGE_SESSION_ID() {
        return counterSend_EVT_CHANGE_SESSION_ID;
    }

    public long getCounterReceive_EVT_ALL_SESSION_DATA() {
        return counterReceive_EVT_ALL_SESSION_DATA;
    }

    public long getCounterReceive_EVT_GET_ALL_SESSIONS() {
        return counterReceive_EVT_GET_ALL_SESSIONS;
    }

    public long getCounterReceive_EVT_SESSION_ACCESSED() {
        return counterReceive_EVT_SESSION_ACCESSED;
    }

    public long getCounterReceive_EVT_SESSION_CREATED() {
        return counterReceive_EVT_SESSION_CREATED;
    }

    public long getCounterReceive_EVT_SESSION_DELTA() {
        return counterReceive_EVT_SESSION_DELTA;
    }

    public long getCounterReceive_EVT_SESSION_EXPIRED() {
        return counterReceive_EVT_SESSION_EXPIRED;
    }

    public int getCounterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE() {
        return counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE;
    }

    public long getCounterReceive_EVT_CHANGE_SESSION_ID() {
        return counterReceive_EVT_CHANGE_SESSION_ID;
    }

    public long getCounterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER() {
        return counterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER;
    }

    @Override
    public long getProcessingTime() {
        return processingTime;
    }

    public long getSessionReplaceCounter() {
        return sessionReplaceCounter;
    }

    public int getCounterNoStateTransfered() {
        return counterNoStateTransfered;
    }

    public int getReceivedQueueSize() {
        return receivedMessageQueue.size() ;
    }

    public int getStateTransferTimeout() {
        return stateTransferTimeout;
    }
    
    public void setStateTransferTimeout(int timeoutAllSession) {
        this.stateTransferTimeout = timeoutAllSession;
    }

    /**
     * @return <code>true</code> 如果状态转移完成.
     */
    public boolean getStateTransfered() {
        return stateTransfered;
    }

    /**
     * 设置状态转移是否完成.
     * 
     * @param stateTransfered Fag value
     */
    public void setStateTransfered(boolean stateTransfered) {
        this.stateTransfered = stateTransfered;
    }

    public boolean isNoContextManagerReceived() {
        return noContextManagerReceived;
    }

    public void setNoContextManagerReceived(boolean noContextManagerReceived) {
        this.noContextManagerReceived = noContextManagerReceived;
    }

    /**
     * @return 毫秒
     */
    public int getSendAllSessionsWaitTime() {
        return sendAllSessionsWaitTime;
    }

    /**
     * @param sendAllSessionsWaitTime 毫秒.
     */
    public void setSendAllSessionsWaitTime(int sendAllSessionsWaitTime) {
        this.sendAllSessionsWaitTime = sendAllSessionsWaitTime;
    }

    public boolean isStateTimestampDrop() {
        return stateTimestampDrop;
    }

    public void setStateTimestampDrop(boolean isTimestampDrop) {
        this.stateTimestampDrop = isTimestampDrop;
    }

    public boolean isSendAllSessions() {
        return sendAllSessions;
    }

    public void setSendAllSessions(boolean sendAllSessions) {
        this.sendAllSessions = sendAllSessions;
    }

    public int getSendAllSessionsSize() {
        return sendAllSessionsSize;
    }

    public void setSendAllSessionsSize(int sendAllSessionsSize) {
        this.sendAllSessionsSize = sendAllSessionsSize;
    }

    public boolean isNotifySessionListenersOnReplication() {
        return notifySessionListenersOnReplication;
    }

    public void setNotifySessionListenersOnReplication(
            boolean notifyListenersCreateSessionOnReplication) {
        this.notifySessionListenersOnReplication = notifyListenersCreateSessionOnReplication;
    }

    public boolean isExpireSessionsOnShutdown() {
        return expireSessionsOnShutdown;
    }

    public void setExpireSessionsOnShutdown(boolean expireSessionsOnShutdown) {
        this.expireSessionsOnShutdown = expireSessionsOnShutdown;
    }

    public boolean isNotifyContainerListenersOnReplication() {
        return notifyContainerListenersOnReplication;
    }

    public void setNotifyContainerListenersOnReplication(
            boolean notifyContainerListenersOnReplication) {
        this.notifyContainerListenersOnReplication = notifyContainerListenersOnReplication;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public Session createSession(String sessionId) {
        return createSession(sessionId, true);
    }

    /**
     * 创建会话并发送会话到其他集群节点.
     *
     * @param sessionId 会话应使用的会话ID
     * @param distribute <code>true</code>复制新会话
     * 
     * @return The session
     */
    public Session createSession(String sessionId, boolean distribute) {
        DeltaSession session = (DeltaSession) super.createSession(sessionId) ;
        if (distribute) {
            sendCreateSession(session.getId(), session);
        }
        if (log.isDebugEnabled())
            log.debug(sm.getString("deltaManager.createSession.newSession",
                    session.getId(), Integer.valueOf(sessions.size())));
        return (session);
    }

    /**
     * 向所有备份节点发送创建会话事件
     * 
     * @param sessionId 会话的会话ID
     * @param session 会话对象
     */
    protected void sendCreateSession(String sessionId, DeltaSession session) {
        if(cluster.getMembers().length > 0 ) {
            SessionMessage msg =
                new SessionMessageImpl(getName(),
                                       SessionMessage.EVT_SESSION_CREATED,
                                       null,
                                       sessionId,
                                       sessionId + "-" + System.currentTimeMillis());
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.sendMessage.newSession", name, sessionId));
            }
            msg.setTimestamp(session.getCreationTime());
            counterSend_EVT_SESSION_CREATED++;
            send(msg);
        }
    }

    /**
     * 向其他备份成员发送消息(domain or all)
     * 
     * @param msg 会话信息
     */
    protected void send(SessionMessage msg) {
        if(cluster != null) {
            cluster.send(msg);
        }
    }

    @Override
    public Session createEmptySession() {
        return getNewDeltaSession() ;
    }

    /**
     * 获取在doLoad()方法中使用的新会话.
     */
    protected DeltaSession getNewDeltaSession() {
        return new DeltaSession(this);
    }

    @Override
    public void changeSessionId(Session session) {
        changeSessionId(session, true);
    }

    @Override
    public void changeSessionId(Session session, String newId) {
        changeSessionId(session, newId, true);
    }

    protected void changeSessionId(Session session, boolean notify) {
        String orgSessionID = session.getId();
        super.changeSessionId(session);
        if (notify) sendChangeSessionId(session.getId(), orgSessionID);
    }

    protected void changeSessionId(Session session, String newId, boolean notify) {
        String orgSessionID = session.getId();
        super.changeSessionId(session, newId);
        if (notify) sendChangeSessionId(session.getId(), orgSessionID);
    }

    protected void sendChangeSessionId(String newSessionID, String orgSessionID) {
        if (cluster.getMembers().length > 0) {
            try {
                // serialize sessionID
                byte[] data = serializeSessionId(newSessionID);
                // notify change sessionID
                SessionMessage msg = new SessionMessageImpl(getName(),
                        SessionMessage.EVT_CHANGE_SESSION_ID, data,
                        orgSessionID, orgSessionID + "-"
                                + System.currentTimeMillis());
                msg.setTimestamp(System.currentTimeMillis());
                counterSend_EVT_CHANGE_SESSION_ID++;
                send(msg);
            } catch (IOException e) {
                log.error(sm.getString("deltaManager.unableSerializeSessionID",
                        newSessionID), e);
            }
        }
    }

    /**
     * @param sessionId 一个序列化的会话ID
     * 
     * @return 序列化的会话ID的字节数组
     * @throws IOException 如果发生输入/输出错误
     */
    protected byte[] serializeSessionId(String sessionId) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeUTF(sessionId);
        oos.flush();
        oos.close();
        return bos.toByteArray();
    }

    /**
     * 加载sessionID
     * 
     * @param data 序列化的会话ID
     * 
     * @return session id
     * @throws IOException 如果发生输入/输出错误
     */
    protected String deserializeSessionId(byte[] data) throws IOException {
        ReplicationStream ois = getReplicationStream(data);
        String sessionId = ois.readUTF();
        ois.close();
        return sessionId;
    }

    /**
     * 从外部节点加载Deltarequest. 在容器类加载器上加载Class.
     * 
     * @param session 对应的会话
     * @param data 消息数据
     * 
     * @return The request
     * 
     * @throws ClassNotFoundException 序列化错误
     * @throws IOException 序列化的IO错误
     */
    protected DeltaRequest deserializeDeltaRequest(DeltaSession session, byte[] data)
            throws ClassNotFoundException, IOException {
        session.lock();
        try {
            ReplicationStream ois = getReplicationStream(data);
            session.getDeltaRequest().readExternal(ois);
            ois.close();
            return session.getDeltaRequest();
        } finally {
            session.unlock();
        }
    }

    /**
     * 序列化DeltaRequest
     *
     * @param session 关联的会话
     * @param deltaRequest 要序列化的请求
     * 
     * @return 序列化的delta请求
     * @throws IOException 序列化IO错误
     */
    protected byte[] serializeDeltaRequest(DeltaSession session, DeltaRequest deltaRequest)
            throws IOException {
        session.lock();
        try {
            return deltaRequest.serialize();
        } finally {
            session.unlock();
        }
    }

    /**
     * 从其他集群节点加载会话.
     * 
     * FIXME 在没有通知的情况下替换当前具有相同ID的会话.
     * FIXME 会话替换的SSO处理并不正确!
     * 
     * @param data 序列化的数据
     * @exception ClassNotFoundException 如果在重新加载期间找不到序列化的类
     * @exception IOException 如果发生输入/输出错误
     */
    protected void deserializeSessions(byte[] data) throws ClassNotFoundException,IOException {

        // 向指定路径名打开输入流
        // 加载先前卸载的活动会话
        try (ObjectInputStream ois = getReplicationStream(data)) {
            Integer count = (Integer) ois.readObject();
            int n = count.intValue();
            for (int i = 0; i < n; i++) {
                DeltaSession session = (DeltaSession) createEmptySession();
                session.readObjectData(ois);
                session.setManager(this);
                session.setValid(true);
                session.setPrimarySession(false);
                // 如果集群中的节点同步超时, 这将确保有正确的时间戳, isValid 返回 true, 导致accessCount=1
                session.access();
                // 确保会话准备过期
                session.setAccessCount(0);
                session.resetDeltaRequest();
                // FIXME 如何通知其他会话ID缓存，如SingleSignOn 增量 sessionCounter 更正统计报表
                if (findSession(session.getIdInternal()) == null ) {
                    sessionCounter++;
                } else {
                    sessionReplaceCounter++;
                    // FIXME better is to grap this sessions again !
                    if (log.isWarnEnabled()) {
                        log.warn(sm.getString("deltaManager.loading.existing.session",
                                session.getIdInternal()));
                    }
                }
                add(session);
                if (notifySessionListenersOnReplication) {
                    session.tellNew();
                }
            }
        } catch (ClassNotFoundException e) {
            log.error(sm.getString("deltaManager.loading.cnfe", e), e);
            throw e;
        } catch (IOException e) {
            log.error(sm.getString("deltaManager.loading.ioe", e), e);
            throw e;
        }
    }


    /**
     * 在适当的持久机制中保存当前活动的会话.
     * 如果不支持持久化, 此方法不做任何事情而返回.
     *
     * @param currentSessions 要序列化的会话
     * @return 序列化的数据
     * @exception IOException 如果发生输入/输出错误
     */
    protected byte[] serializeSessions(Session[] currentSessions) throws IOException {

        // 将输出流打开到指定的路径名
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(fos))) {
            oos.writeObject(Integer.valueOf(currentSessions.length));
            for(int i=0 ; i < currentSessions.length;i++) {
                ((DeltaSession)currentSessions[i]).writeObjectData(oos);
            }
            // 刷新和关闭输出流
            oos.flush();
        } catch (IOException e) {
            log.error(sm.getString("deltaManager.unloading.ioe", e), e);
            throw e;
        }

        return fos.toByteArray();
    }

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        super.startInternal();

        // 加载未加载的会话
        try {
            if (cluster == null) {
                log.error(sm.getString("deltaManager.noCluster", getName()));
                return;
            } else {
                if (log.isInfoEnabled()) {
                    String type = "unknown" ;
                    if( cluster.getContainer() instanceof Host){
                        type = "Host" ;
                    } else if( cluster.getContainer() instanceof Engine){
                        type = "Engine" ;
                    }
                    log.info(sm.getString("deltaManager.registerCluster",
                            getName(), type, cluster.getClusterName()));
                }
            }
            if (log.isInfoEnabled()) {
                log.info(sm.getString("deltaManager.startClustering", getName()));
            }

            getAllClusterSessions();

        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("deltaManager.managerLoad"), t);
        }

        setState(LifecycleState.STARTING);
    }

    /**
     * 从第一个会话获得来自所有集群会话的备份
     */
    public synchronized void getAllClusterSessions() {
        if (cluster != null && cluster.getMembers().length > 0) {
            long beforeSendTime = System.currentTimeMillis();
            Member mbr = findSessionMasterMember();
            if(mbr == null) { // 没有发现域名成员
                 return;
            }
            SessionMessage msg = new SessionMessageImpl(this.getName(),
                    SessionMessage.EVT_GET_ALL_SESSIONS, null, "GET-ALL", "GET-ALL-" + getName());
            msg.setTimestamp(beforeSendTime);
            // 设置引用时间
            stateTransferCreateSendTime = beforeSendTime ;
            // 请求会话状态
            counterSend_EVT_GET_ALL_SESSIONS++;
            stateTransfered = false ;
            // FIXME 此发送调用阻塞部署线程, 当发送者启用waitForAck
            try {
                synchronized(receivedMessageQueue) {
                     receiverQueue = true ;
                }
                cluster.send(msg, mbr);
                if (log.isInfoEnabled())
                    log.info(sm.getString("deltaManager.waitForSessionState",
                            getName(), mbr, Integer.valueOf(getStateTransferTimeout())));
                // FIXME 在发送方ACK模式下，该方法只检查状态转移，重新发送是一个问题!
                waitForSendAllSessions(beforeSendTime);
            } finally {
                synchronized(receivedMessageQueue) {
                    for (Iterator<SessionMessage> iter = receivedMessageQueue.iterator();
                            iter.hasNext();) {
                        SessionMessage smsg = iter.next();
                        if (!stateTimestampDrop) {
                            messageReceived(smsg,
                                    smsg.getAddress() != null ? (Member) smsg.getAddress() : null);
                        } else {
                            if (smsg.getEventType() != SessionMessage.EVT_GET_ALL_SESSIONS &&
                                    smsg.getTimestamp() >= stateTransferCreateSendTime) {
                                // FIXME handle EVT_GET_ALL_SESSIONS later
                                messageReceived(smsg,
                                        smsg.getAddress() != null ?
                                                (Member) smsg.getAddress() :
                                                null);
                            } else {
                                if (log.isWarnEnabled()) {
                                    log.warn(sm.getString("deltaManager.dropMessage",
                                            getName(),
                                            smsg.getEventTypeString(),
                                            new Date(stateTransferCreateSendTime),
                                            new Date(smsg.getTimestamp())));
                                }
                            }
                        }
                    }
                    receivedMessageQueue.clear();
                    receiverQueue = false ;
                }
           }
        } else {
            if (log.isInfoEnabled()) log.info(sm.getString("deltaManager.noMembers", getName()));
        }
    }

    /**
     * 查找会话状态的所有者
     * 
     * @return master member of sessions
     */
    protected Member findSessionMasterMember() {
        Member mbr = null;
        Member mbrs[] = cluster.getMembers();
        if(mbrs.length != 0 ) mbr = mbrs[0];
        if(mbr == null && log.isWarnEnabled()) {
            log.warn(sm.getString("deltaManager.noMasterMember",getName(), ""));
        }
        if(mbr != null && log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.foundMasterMember",getName(), mbr));
        }
        return mbr;
    }

    /**
     * 等待集群会话状态被转移，或超时60 Sec之后，并且 stateTransferTimeout == -1 等待备份被转移(永恒模式)
     * 
     * @param beforeSendTime 操作开始时间
     */
    protected void waitForSendAllSessions(long beforeSendTime) {
        long reqStart = System.currentTimeMillis();
        long reqNow = reqStart ;
        boolean isTimeout = false;
        if(getStateTransferTimeout() > 0) {
            // 等待用超时检查转移状态
            do {
                try {
                    Thread.sleep(100);
                } catch (Exception sleep) {
                    //
                }
                reqNow = System.currentTimeMillis();
                isTimeout = ((reqNow - reqStart) > (1000L * getStateTransferTimeout()));
            } while ((!getStateTransfered()) && (!isTimeout) && (!isNoContextManagerReceived()));
        } else {
            if(getStateTransferTimeout() == -1) {
                // 等待状态转移
                do {
                    try {
                        Thread.sleep(100);
                    } catch (Exception sleep) {
                    }
                } while ((!getStateTransfered())&& (!isNoContextManagerReceived()));
                reqNow = System.currentTimeMillis();
            }
        }
        if (isTimeout) {
            counterNoStateTransfered++ ;
            log.error(sm.getString("deltaManager.noSessionState", getName(),
                    new Date(beforeSendTime), Long.valueOf(reqNow - beforeSendTime)));
        }else if (isNoContextManagerReceived()) {
            if (log.isWarnEnabled())
                log.warn(sm.getString("deltaManager.noContextManager", getName(),
                        new Date(beforeSendTime), Long.valueOf(reqNow - beforeSendTime)));
        } else {
            if (log.isInfoEnabled())
                log.info(sm.getString("deltaManager.sessionReceived",getName(),
                        new Date(beforeSendTime), Long.valueOf(reqNow - beforeSendTime)));
        }
    }

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("deltaManager.stopped", getName()));

        setState(LifecycleState.STOPPING);

        // 终止所有活动会话
        if (log.isInfoEnabled()) log.info(sm.getString("deltaManager.expireSessions", getName()));
        Session sessions[] = findSessions();
        for (int i = 0; i < sessions.length; i++) {
            DeltaSession session = (DeltaSession) sessions[i];
            if (!session.isValid())
                continue;
            try {
                session.expire(true, isExpireSessionsOnShutdown());
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
        }

        // 如果重新启动，需要一个新的随机数生成器
        super.stopInternal();
    }

    // -------------------------------------------------------- Replication

    /**
     * 从另一个节点接收到消息，如果这是感兴趣的复制消息，则要实现这个回调方法.
     *
     * @param cmsg - 接收的消息.
     */
    @Override
    public void messageDataReceived(ClusterMessage cmsg) {
        if (cmsg instanceof SessionMessage) {
            SessionMessage msg = (SessionMessage) cmsg;
            switch (msg.getEventType()) {
                case SessionMessage.EVT_GET_ALL_SESSIONS:
                case SessionMessage.EVT_SESSION_CREATED:
                case SessionMessage.EVT_SESSION_EXPIRED:
                case SessionMessage.EVT_SESSION_ACCESSED:
                case SessionMessage.EVT_SESSION_DELTA:
                case SessionMessage.EVT_CHANGE_SESSION_ID:
                    synchronized(receivedMessageQueue) {
                        if(receiverQueue) {
                            receivedMessageQueue.add(msg);
                            return ;
                        }
                    }
                   break;
                default:
                    break;
            }
            messageReceived(msg, msg.getAddress() != null ? (Member) msg.getAddress() : null);
        }
    }

    /**
     * 当请求完成后, 复制阀门将通知管理器, 并且管理者将决定是否需要复制.
     * 如果需要复制, 管理器将创建会话消息，并将复制该会话消息. 集群决定它的发送位置.
     *
     * @param sessionId - 刚刚完成的sessionId.
     * @return 要发送的SessionMessage
     */
    @Override
    public ClusterMessage requestCompleted(String sessionId) {
         return requestCompleted(sessionId, false);
    }

    /**
     * 当请求完成时, 复制阀门将通知管理器, 并且管理者将决定是否需要复制.
     * 如果需要复制, 管理器将创建会话消息，并将复制该会话消息. 集群决定它的发送位置.
     *
     * 会话过期也调用此方法, 但是expires == true.
     *
     * @param sessionId - 刚刚完成的sessionId.
     * @param expires - 在会话到期期间是否调用此方法
     * @return 要发送的SessionMessage
     */
    @SuppressWarnings("null")
    public ClusterMessage requestCompleted(String sessionId, boolean expires) {
        DeltaSession session = null;
        SessionMessage msg = null;
        try {
            session = (DeltaSession) findSession(sessionId);
            if (session == null) {
                // 并行请求已经调用 session.invalidate()， 该方法将从Manager删除会话.
                return null;
            }
            DeltaRequest deltaRequest = session.getDeltaRequest();
            session.lock();
            if (deltaRequest.getSize() > 0) {
                counterSend_EVT_SESSION_DELTA++;
                byte[] data = serializeDeltaRequest(session,deltaRequest);
                msg = new SessionMessageImpl(getName(),
                                             SessionMessage.EVT_SESSION_DELTA,
                                             data,
                                             sessionId,
                                             sessionId + "-" + System.currentTimeMillis());
                session.resetDeltaRequest();
            }
        } catch (IOException x) {
            log.error(sm.getString("deltaManager.createMessage.unableCreateDeltaRequest",
                    sessionId), x);
            return null;
        } finally {
            if (session!=null) session.unlock();
        }
        if(msg == null) {
            if(!expires && !session.isPrimarySession()) {
                counterSend_EVT_SESSION_ACCESSED++;
                msg = new SessionMessageImpl(getName(),
                                             SessionMessage.EVT_SESSION_ACCESSED,
                                             null,
                                             sessionId,
                                             sessionId + "-" + System.currentTimeMillis());
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("deltaManager.createMessage.accessChangePrimary",
                            getName(), sessionId));
                }
            }
        } else { // 只记录外部同步块!
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.createMessage.delta", getName(), sessionId));
            }
        }
        if (!expires) session.setPrimarySession(true);
        // 检查是否需要发送访问消息
        if (!expires && (msg == null)) {
            long replDelta = System.currentTimeMillis() - session.getLastTimeReplicated();
            if (session.getMaxInactiveInterval() >=0 &&
                    replDelta > (session.getMaxInactiveInterval() * 1000L)) {
                counterSend_EVT_SESSION_ACCESSED++;
                msg = new SessionMessageImpl(getName(),
                                             SessionMessage.EVT_SESSION_ACCESSED,
                                             null,
                                             sessionId,
                                             sessionId + "-" + System.currentTimeMillis());
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("deltaManager.createMessage.access",
                            getName(),sessionId));
                }
            }
        }

        // 更新上次复制时间
        if (msg != null) {
           session.setLastTimeReplicated(System.currentTimeMillis());
           msg.setTimestamp(session.getLastTimeReplicated());
        }
        return msg;
    }
    
    /**
     * 重置管理器统计
     */
    public synchronized void resetStatistics() {
        processingTime = 0 ;
        expiredSessions.set(0);
        synchronized (sessionCreationTiming) {
            sessionCreationTiming.clear();
            while (sessionCreationTiming.size() <
                    ManagerBase.TIMING_STATS_CACHE_SIZE) {
                sessionCreationTiming.add(null);
            }
        }
        synchronized (sessionExpirationTiming) {
            sessionExpirationTiming.clear();
            while (sessionExpirationTiming.size() <
                    ManagerBase.TIMING_STATS_CACHE_SIZE) {
                sessionExpirationTiming.add(null);
            }
        }
        rejectedSessions = 0 ;
        sessionReplaceCounter = 0 ;
        counterNoStateTransfered = 0 ;
        setMaxActive(getActiveSessions());
        sessionCounter = getActiveSessions() ;
        counterReceive_EVT_ALL_SESSION_DATA = 0;
        counterReceive_EVT_GET_ALL_SESSIONS = 0;
        counterReceive_EVT_SESSION_ACCESSED = 0 ;
        counterReceive_EVT_SESSION_CREATED = 0 ;
        counterReceive_EVT_SESSION_DELTA = 0 ;
        counterReceive_EVT_SESSION_EXPIRED = 0 ;
        counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE = 0;
        counterReceive_EVT_CHANGE_SESSION_ID = 0;
        counterSend_EVT_ALL_SESSION_DATA = 0;
        counterSend_EVT_GET_ALL_SESSIONS = 0;
        counterSend_EVT_SESSION_ACCESSED = 0 ;
        counterSend_EVT_SESSION_CREATED = 0 ;
        counterSend_EVT_SESSION_DELTA = 0 ;
        counterSend_EVT_SESSION_EXPIRED = 0 ;
        counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE = 0;
        counterSend_EVT_CHANGE_SESSION_ID = 0;

    }

    //  -------------------------------------------------------- expire

    /**
     * 向其他集群节点发送会话过期
     *
     * @param id 会话id
     */
    protected void sessionExpired(String id) {
        if(cluster.getMembers().length > 0 ) {
            counterSend_EVT_SESSION_EXPIRED++ ;
            SessionMessage msg = new SessionMessageImpl(getName(),
                    SessionMessage.EVT_SESSION_EXPIRED, null, id, id+ "-EXPIRED-MSG");
            msg.setTimestamp(System.currentTimeMillis());
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.createMessage.expire", getName(), id));
            }
            send(msg);
        }
    }

    /**
     * 终止所有查找的会话.
     */
    public void expireAllLocalSessions()
    {
        long timeNow = System.currentTimeMillis();
        Session sessions[] = findSessions();
        int expireDirect  = 0 ;
        int expireIndirect = 0 ;

        if(log.isDebugEnabled()) {
            log.debug("Start expire all sessions " + getName() + " at " + timeNow +
                    " sessioncount " + sessions.length);
        }
        for (int i = 0; i < sessions.length; i++) {
            if (sessions[i] instanceof DeltaSession) {
                DeltaSession session = (DeltaSession) sessions[i];
                if (session.isPrimarySession()) {
                    if (session.isValid()) {
                        session.expire();
                        expireDirect++;
                    } else {
                        expireIndirect++;
                    }
                }
            }
        }
        long timeEnd = System.currentTimeMillis();
        if(log.isDebugEnabled()) {
            log.debug("End expire sessions " + getName() +
                    " expire processingTime " + (timeEnd - timeNow) +
                    " expired direct sessions: " + expireDirect +
                    " expired direct sessions: " + expireIndirect);
        }
    }

    @Override
    public String[] getInvalidatedSessions() {
        return new String[0];
    }

    //  -------------------------------------------------------- message receive

    /**
     * 此方法由接收线程调用, 当SessionMessage已经从集群中的其他节点之一接收.
     *
     * @param msg - 接受的消息
     * @param sender - 消息的发送方, 如果接收EVT_GET_ALL_SESSION 消息使用它, 这样只回复请求节点
     */
    protected void messageReceived(SessionMessage msg, Member sender) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader[] loaders = getClassLoaders();
            Thread.currentThread().setContextClassLoader(loaders[0]);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.eventType",
                        getName(), msg.getEventTypeString(), sender));
            }

            switch (msg.getEventType()) {
                case SessionMessage.EVT_GET_ALL_SESSIONS:
                    handleGET_ALL_SESSIONS(msg,sender);
                    break;
                case SessionMessage.EVT_ALL_SESSION_DATA:
                    handleALL_SESSION_DATA(msg,sender);
                    break;
                case SessionMessage.EVT_ALL_SESSION_TRANSFERCOMPLETE:
                    handleALL_SESSION_TRANSFERCOMPLETE(msg,sender);
                    break;
                case SessionMessage.EVT_SESSION_CREATED:
                    handleSESSION_CREATED(msg,sender);
                    break;
                case SessionMessage.EVT_SESSION_EXPIRED:
                    handleSESSION_EXPIRED(msg,sender);
                    break;
                case SessionMessage.EVT_SESSION_ACCESSED:
                    handleSESSION_ACCESSED(msg,sender);
                    break;
                case SessionMessage.EVT_SESSION_DELTA:
                   handleSESSION_DELTA(msg,sender);
                   break;
                case SessionMessage.EVT_CHANGE_SESSION_ID:
                    handleCHANGE_SESSION_ID(msg,sender);
                    break;
                case SessionMessage.EVT_ALL_SESSION_NOCONTEXTMANAGER:
                    handleALL_SESSION_NOCONTEXTMANAGER(msg,sender);
                    break;
                default:
                    // 没有认出消息类型, 什么都不做
                    break;
            }
        } catch (Exception x) {
            log.error(sm.getString("deltaManager.receiveMessage.error",getName()), x);
        } finally {
            Thread.currentThread().setContextClassLoader(contextLoader);
        }
    }

    // -------------------------------------------------------- message receiver handler


    /**
     * 处理接收的完全转移的会话状态
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     */
    protected void handleALL_SESSION_TRANSFERCOMPLETE(SessionMessage msg, Member sender) {
        counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE++ ;
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.transfercomplete",
                    getName(), sender.getHost(), Integer.valueOf(sender.getPort())));
        }
        stateTransferCreateSendTime = msg.getTimestamp() ;
        stateTransfered = true ;
    }

    /**
     * 处理接收的会话增量
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     * 
     * @throws IOException 序列化的IO错误
     * @throws ClassNotFoundException 序列化错误
     */
    protected void handleSESSION_DELTA(SessionMessage msg, Member sender)
            throws IOException, ClassNotFoundException {
        counterReceive_EVT_SESSION_DELTA++;
        byte[] delta = msg.getSession();
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.delta",
                        getName(), msg.getSessionID()));
            }
            session.lock();
            try {
                DeltaRequest dreq = deserializeDeltaRequest(session, delta);
                dreq.execute(session, isNotifyListenersOnReplication());
                session.setPrimarySession(false);
            } finally {
                session.unlock();
            }
        }
    }

    /**
     * 处理在其他节点上接收的会话的访问 (主要的会话是false)
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     * 
     * @throws IOException 传播的IO错误
     */
    protected void handleSESSION_ACCESSED(SessionMessage msg,Member sender) throws IOException {
        counterReceive_EVT_SESSION_ACCESSED++;
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.accessed",
                        getName(), msg.getSessionID()));
            }
            session.access();
            session.setPrimarySession(false);
            session.endAccess();
        }
    }

    /**
     * 处理在其他节点接收的会话的过期
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     * 
     * @throws IOException 传播的IO错误
     */
    protected void handleSESSION_EXPIRED(SessionMessage msg,Member sender) throws IOException {
        counterReceive_EVT_SESSION_EXPIRED++;
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.expired",
                        getName(), msg.getSessionID()));
            }
            session.expire(notifySessionListenersOnReplication, false);
        }
    }

    /**
     * 处理在其他节点接收的会话的创建 (创建备份 - false)
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     */
    protected void handleSESSION_CREATED(SessionMessage msg,Member sender) {
        counterReceive_EVT_SESSION_CREATED++;
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.createNewSession",
                    getName(), msg.getSessionID()));
        }
        DeltaSession session = (DeltaSession) createEmptySession();
        session.setManager(this);
        session.setValid(true);
        session.setPrimarySession(false);
        session.setCreationTime(msg.getTimestamp());
        // 使用容器 maxInactiveInterval, 因此会话在一次传输的情况下将正确终止
        session.setMaxInactiveInterval(getContext().getSessionTimeout() * 60, false);
        session.access();
        session.setId(msg.getSessionID(), notifySessionListenersOnReplication);
        session.resetDeltaRequest();
        session.endAccess();

    }

    /**
     * 处理接收的会话handle receive sessions from other not ( restart )
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     * 
     * @throws ClassNotFoundException 序列化错误
     * @throws IOException 序列化的IO错误
     */
    protected void handleALL_SESSION_DATA(SessionMessage msg,Member sender)
            throws ClassNotFoundException, IOException {
        counterReceive_EVT_ALL_SESSION_DATA++;
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.allSessionDataBegin", getName()));
        }
        byte[] data = msg.getSession();
        deserializeSessions(data);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.allSessionDataAfter", getName()));
        }
        //stateTransferred = true;
    }

    /**
     * 处理其他节点的所有会话的接收 (重启)
     * a) 用一个消息发送所有会话
     * b) 在块中发送会话
     * 发送完成后发送状态完成
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     * @throws IOException 发送消息的IO错误
     */
    protected void handleGET_ALL_SESSIONS(SessionMessage msg, Member sender) throws IOException {
        counterReceive_EVT_GET_ALL_SESSIONS++;
        // 从该管理器获取所有会话的列表
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.unloadingBegin", getName()));
        }
        // 写入活动会话的数目, 得到的所有的会话和序列化没有同步
        Session[] currentSessions = findSessions();
        long findSessionTimestamp = System.currentTimeMillis() ;
        if (isSendAllSessions()) {
            sendSessions(sender, currentSessions, findSessionTimestamp);
        } else {
            // 在块中发送会话
            int remain = currentSessions.length;
            for (int i = 0; i < currentSessions.length; i += getSendAllSessionsSize()) {
                int len = i + getSendAllSessionsSize() > currentSessions.length ?
                        currentSessions.length - i :
                        getSendAllSessionsSize();
                Session[] sendSessions = new Session[len];
                System.arraycopy(currentSessions, i, sendSessions, 0, len);
                sendSessions(sender, sendSessions,findSessionTimestamp);
                remain = remain - len;
                if (getSendAllSessionsWaitTime() > 0 && remain > 0) {
                    try {
                        Thread.sleep(getSendAllSessionsWaitTime());
                    } catch (Exception sleep) {
                    }
                }
            }
        }

        SessionMessage newmsg = new SessionMessageImpl(name,
                SessionMessage.EVT_ALL_SESSION_TRANSFERCOMPLETE, null, "SESSION-STATE-TRANSFERED",
                "SESSION-STATE-TRANSFERED" + getName());
        newmsg.setTimestamp(findSessionTimestamp);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.createMessage.allSessionTransfered",getName()));
        }
        counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE++;
        cluster.send(newmsg, sender);
    }

    /**
     * 在其他节点处理接收的会话ID的更改
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     * 
     * @throws IOException 序列化的IO错误
     */
    protected void handleCHANGE_SESSION_ID(SessionMessage msg,Member sender) throws IOException {
        counterReceive_EVT_CHANGE_SESSION_ID++;
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session != null) {
            String newSessionID = deserializeSessionId(msg.getSession());
            session.setPrimarySession(false);
            // change session id
            changeSessionId(session, newSessionID, notifySessionListenersOnReplication,
                    notifyContainerListenersOnReplication);
        }
    }

    /**
     * 处理没有上下文管理器的接收.
     * 
     * @param msg 会话消息
     * @param sender 发送消息的成员
     */
    protected void handleALL_SESSION_NOCONTEXTMANAGER(SessionMessage msg, Member sender) {
        counterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER++ ;
        if (log.isDebugEnabled())
            log.debug(sm.getString("deltaManager.receiveMessage.noContextManager",
                    getName(), sender.getHost(), Integer.valueOf(sender.getPort())));
        noContextManagerReceived = true ;
    }

    /**
     * 向发送方发送会话块
     * 
     * @param sender 发送方成员
     * @param currentSessions 要发送的会话
     * @param sendTimestamp Timestamp
     * 
     * @throws IOException 发送消息的IO错误
     */
    protected void sendSessions(Member sender, Session[] currentSessions,long sendTimestamp)
            throws IOException {
        byte[] data = serializeSessions(currentSessions);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.unloadingAfter", getName()));
        }
        SessionMessage newmsg = new SessionMessageImpl(name, SessionMessage.EVT_ALL_SESSION_DATA,
                data, "SESSION-STATE", "SESSION-STATE-" + getName());
        newmsg.setTimestamp(sendTimestamp);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.createMessage.allSessionData", getName()));
        }
        counterSend_EVT_ALL_SESSION_DATA++;
        cluster.send(newmsg, sender);
    }

    @Override
    public ClusterManager cloneFromTemplate() {
        DeltaManager result = new DeltaManager();
        clone(result);
        result.expireSessionsOnShutdown = expireSessionsOnShutdown;
        result.notifySessionListenersOnReplication = notifySessionListenersOnReplication;
        result.notifyContainerListenersOnReplication = notifyContainerListenersOnReplication;
        result.stateTransferTimeout = stateTransferTimeout;
        result.sendAllSessions = sendAllSessions;
        result.sendAllSessionsSize = sendAllSessionsSize;
        result.sendAllSessionsWaitTime = sendAllSessionsWaitTime ;
        result.stateTimestampDrop = stateTimestampDrop ;
        return result;
    }
}
