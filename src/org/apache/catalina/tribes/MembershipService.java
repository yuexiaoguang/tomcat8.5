package org.apache.catalina.tribes;


/**
 * 底层的会员身份组件, IO 层.<br>
 */
public interface MembershipService {

    public static final int MBR_RX = Channel.MBR_RX_SEQ;
    public static final int MBR_TX = Channel.MBR_TX_SEQ;

    /**
     * 设置属性. 必须在<code>start()</code>方法调用之前调用.
     * 属性是特定于实现的.
     * 
     * @param properties - 用于配置成员资格服务.
     */
    public void setProperties(java.util.Properties properties);

    /**
     * @return 使用的配置的属性.
     */
    public java.util.Properties getProperties();

    /**
     * 启动. 如果添加了监听器，监听器将开始接收事件.
     * 执行开始级别1和2
     * @throws java.lang.Exception 如果启动失败.
     */
    public void start() throws java.lang.Exception;

    /**
     * 启动. 如果添加了监听器，监听器将开始接收事件.
     * 
     * @param level - 级别 MBR_RX 开始监听成员, 级别 MBR_TX开始广播服务器
     * 
     * @throws java.lang.Exception 如果启动失败.
     * @throws java.lang.IllegalArgumentException 如果级别不正确.
     */
    public void start(int level) throws java.lang.Exception;


    /**
     * @param level - 级别 MBR_RX 停止监听成员, 级别 MBR_TX 停止广播服务器
     * @throws java.lang.IllegalArgumentException 如果级别不正确.
     */
    public void stop(int level);

    /**
     * @return true 如果组中有成员
     */
    public boolean hasMembers();

    /**
     * 获取指定的成员.
     * @param mbr 要检索的成员
     * @return the member
     */
    public Member getMember(Member mbr);

    /**
     * @return 集群中所有的成员.
     */
    public Member[] getMembers();

    /**
     * 获取本地成员.
     * @param incAliveTime <code>true</code>设置本地成员的存活时间
     */
    public Member getLocalMember(boolean incAliveTime);

    /**
     * @return 所有成员的名称
     */
    public String[] getMembersByName();

    /**
     * 获取一个成员.
     * @param name 成员的名称
     * @return the member
     */
    public Member findMemberByName(String name);

    /**
     * 设置本地成员属性.
     *
     * @param listenHost 监听的主机
     * @param listenPort 监听的端口
     * @param securePort 安全端口
     * @param udpPort   UDP端口
     */
    public void setLocalMemberProperties(String listenHost, int listenPort, int securePort, int udpPort);

    /**
     * 设置监听器, 只能添加一个.
     * 如果调用这个方法两次, 将使用最后一个监听器.
     * @param listener The listener
     */
    public void setMembershipListener(MembershipListener listener);

    /**
     * 删除监听器.
     */
    public void removeMembershipListener();

    /**
     * 设置要广播的有效负荷.
     * @param payload byte[]
     */
    public void setPayload(byte[] payload);

    public void setDomain(byte[] domain);

    /**
     * 广播一个消息给所有成员.
     * @param message 要广播的消息
     * @throws ChannelException 广播消息失败
     */
    public void broadcast(ChannelMessage message) throws ChannelException;

    /**
     * 返回相关的 channel
     */
    public Channel getChannel();

    /**
     * 设置相关的 channel
     * @param channel The channel
     */
    public void setChannel(Channel channel);

}
