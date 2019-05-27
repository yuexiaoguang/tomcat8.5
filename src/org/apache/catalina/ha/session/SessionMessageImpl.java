package org.apache.catalina.ha.session;


import org.apache.catalina.ha.ClusterMessageBase;

/**
 * 会话集群消息
 */
public class SessionMessageImpl extends ClusterMessageBase implements SessionMessage {

    private static final long serialVersionUID = 2L;


    /*
     * 用于保持消息状态的私有可序列化变量
     */
    private final int mEvtType;
    private final byte[] mSession;
    private final String mSessionID;

    private final String mContextName;
    private long serializationTimestamp;
    private boolean timestampSet = false ;
    private String uniqueId;


    private SessionMessageImpl( String contextName,
                           int eventtype,
                           byte[] session,
                           String sessionID)
    {
        mEvtType = eventtype;
        mSession = session;
        mSessionID = sessionID;
        mContextName = contextName;
        uniqueId = sessionID;
    }

    /**
     * 创建会话消息. 取决于该消息代表的事件类型, 填充构造函数时的不同参数<BR>
      * 下列规则取决于您使用的是什么事件类型参数:<BR>
     * <B>EVT_SESSION_CREATED</B><BR>
     *    参数: session, sessionID必须设置.<BR>
     * <B>EVT_SESSION_EXPIRED</B><BR>
     *    参数: sessionID必须设置.<BR>
     * <B>EVT_SESSION_ACCESSED</B><BR>
     *    参数: sessionID必须设置.<BR>
     * <B>EVT_GET_ALL_SESSIONS</B><BR>
     *    从一个节点获取所有会话.<BR>
     * <B>EVT_SESSION_DELTA</B><BR>
     *    发送属性增量(add,update,remove attribute or principal, ...).<BR>
     * <B>EVT_ALL_SESSION_DATA</B><BR>
     *    发送完整的序列化会话列表<BR>
     * <B>EVT_ALL_SESSION_TRANSFERCOMPLETE</B><BR>
     *    发送所有会话状态信息, 在这个发送者接收 GET_ALL_SESSION 之后.<BR>
     * <B>EVT_CHANGE_SESSION_ID</B><BR>
     *    发送原始的 sessionID 和新的 sessionID.<BR>
     * <B>EVT_ALL_SESSION_NOCONTEXTMANAGER</B><BR>
     *    发送, 上下文管理器不存在, 在这个发送者接收 GET_ALL_SESSION之后.<BR>
     *    
     * @param contextName - 上下文的名称
     * @param eventtype - 这个类中定义的8个事件类型之一
     * @param session - 会话本身的序列化字节数组
     * @param sessionID - 标识此会话的ID
     * @param uniqueID - 标识此消息的ID
     */
    public SessionMessageImpl( String contextName,
                           int eventtype,
                           byte[] session,
                           String sessionID,
                           String uniqueID)
    {
        this(contextName,eventtype,session,sessionID);
        uniqueId = uniqueID;
    }

    /**
     * 返回事件类型
     * 
     * @return 事件类型 EVT_XXXX的其中之一
     */
    @Override
    public int getEventType() { return mEvtType; }

    /**
     * @return 会话的序列化数据
     */
    @Override
    public byte[] getSession() { return mSession;}

    @Override
    public String getSessionID(){ return mSessionID; }

    /**
     * 设置消息发送时间，但只有第一个设置工作(one shot)
     */
    @Override
    public void setTimestamp(long time) {
        synchronized(this) {
            if(!timestampSet) {
                serializationTimestamp=time;
                timestampSet = true ;
            }
        }
    }

    @Override
    public long getTimestamp() { return serializationTimestamp;}

    /**
     * 清除文本事件类型名称(只用于日志记录)
     * 
     * @return 字符串形式的事件类型, 用于调试
     */
    @Override
    public String getEventTypeString()
    {
        switch (mEvtType)
        {
            case EVT_SESSION_CREATED : return "SESSION-MODIFIED";
            case EVT_SESSION_EXPIRED : return "SESSION-EXPIRED";
            case EVT_SESSION_ACCESSED : return "SESSION-ACCESSED";
            case EVT_GET_ALL_SESSIONS : return "SESSION-GET-ALL";
            case EVT_SESSION_DELTA : return "SESSION-DELTA";
            case EVT_ALL_SESSION_DATA : return "ALL-SESSION-DATA";
            case EVT_ALL_SESSION_TRANSFERCOMPLETE : return "SESSION-STATE-TRANSFERED";
            case EVT_CHANGE_SESSION_ID : return "SESSION-ID-CHANGED";
            case EVT_ALL_SESSION_NOCONTEXTMANAGER : return "NO-CONTEXT-MANAGER";
            default : return "UNKNOWN-EVENT-TYPE";
        }
    }

    @Override
    public String getContextName() {
       return mContextName;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public String toString() {
        return getEventTypeString() + "#" + getContextName() + "#" + getSessionID() ;
    }
}
