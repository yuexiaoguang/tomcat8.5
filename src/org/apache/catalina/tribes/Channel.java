package org.apache.catalina.tribes;

import java.io.Serializable;

/**
 * 表示一组节点，每个都参与某种通信.<br>
 * channel 是Tribes的主要的API类, 这本质上是应用程序需要知道的唯一的类. 通过channel 应用可以:<br>
 * 1. 发送消息<br>
 * 2. 接收消息 (通过注册一个<code>ChannelListener</code><br>
 * 3. 获取组中所有成员<code>getMembers()</code><br>
 * 4. 接收添加和删除成员的通知, 通过注册一个<code>MembershipListener</code><br>
 * <br>
 * channel 有5个主要的组件:<br>
 * 1. Data 接收器, 使用内置线程池来接收来自其他对等体的消息<br>
 * 2. Data 发送器, 使用NIO或java.io发送数据的实现类<br>
 * 3. Membership 监听器, 监听广播<br>
 * 4. Membership 广播器, 发出广播消息.<br>
 * 5. Channel 拦截器, 在消息发送或到达时操作消息<br><br>
 * 
 * channel 层级是:
 * <pre><code>
 *  ChannelListener_1..ChannelListener_N MembershipListener_1..MembershipListener_N [Application Layer]
 *            \          \                  /                   /
 *             \          \                /                   /
 *              \          \              /                   /
 *               \          \            /                   /
 *                \          \          /                   /
 *                 \          \        /                   /
 *                  ---------------------------------------
 *                                  |
 *                                  |
 *                               Channel
 *                                  |
 *                         ChannelInterceptor_1
 *                                  |                                               [Channel stack]
 *                         ChannelInterceptor_N
 *                                  |
 *                             Coordinator (implements MessageListener,MembershipListener,ChannelInterceptor)
 *                          --------------------
 *                         /        |           \
 *                        /         |            \
 *                       /          |             \
 *                      /           |              \
 *                     /            |               \
 *           MembershipService ChannelSender ChannelReceiver                        [IO layer]
 * </code></pre>
 *
 * For example usage @see org.apache.catalina.tribes.group.GroupChannel
 */
public interface Channel {

    /**
     * 启动和停止序列可以由这些常量控制.
     * 允许分开启动channel的组件 <br>
     * DEFAULT - 启动或关闭channel中的所有组件
     */
    public static final int DEFAULT = 15;

    /**
     * 启动和停止序列可以由这些常量控制.
     * 允许分开启动channel的组件 <br>
     * SND_RX_SEQ - 启动或关闭数据接收器. 启动意味着通过TCP实现类打开服务器 socket
     */
    public static final int SND_RX_SEQ = 1;

    /**
     * 启动和停止序列可以由这些常量控制.
     * 允许分开启动channel的组件 <br>
     * SND_TX_SEQ - 启动或关闭数据发送器. 这个不应该打开任何 socket, 当发送消息时, 按需打开套接字
     */
    public static final int SND_TX_SEQ = 2;

    /**
     * 启动和停止序列可以由这些常量控制.
     * 允许分开启动channel的组件 <br>
     * MBR_RX_SEQ - 启动或停止成员监听器. 在多播实现中, 将打开 datagram socket, 并加入一个组, 监听成员加入的消息
     */
    public static final int MBR_RX_SEQ = 4;

    /**
     * 启动和停止序列可以由这些常量控制.
     * 允许分开启动channel的组件 <br>
     * MBR_TX_SEQ - 启动或停止成员广播器. 在多播实现中, 将打开 datagram socket, 并加入一个组, 广播本地成员的消息
     */
    public static final int MBR_TX_SEQ = 8;

    /**
     * 发送选项, 发送消息时, 它可以有一个选项标志来触发某些行为. 当消息通过channel 堆栈时, 大多数标志用于触发channel 拦截器. <br>
     * 但是, 有五个channel实现类必须实现的默认标志<br>
     * SEND_OPTIONS_BYTE_MESSAGE - 该消息是纯字节消息，不会执行封装和解封装.<br>
     */
    public static final int SEND_OPTIONS_BYTE_MESSAGE = 0x0001;

    /**
     * 发送选项, 发送消息时, 它可以有一个选项标志来触发某些行为. 当消息通过channel 堆栈时, 大多数标志用于触发channel 拦截器. <br>
     * 但是, 有五个channel实现类必须实现的默认标志<br>
     * SEND_OPTIONS_USE_ACK - 当接收方接收到消息时，发送消息并接收ACK<br> 如果没有接收到ack, 不认为消息是成功的<br>
     */
    public static final int SEND_OPTIONS_USE_ACK = 0x0002;

    /**
     * 发送选项, 发送消息时, 它可以有一个选项标志来触发某些行为. 当消息通过channel 堆栈时, 大多数标志用于触发channel 拦截器. <br>
     * 但是, 有五个channel实现类必须实现的默认标志<br>
     * SEND_OPTIONS_SYNCHRONIZED_ACK - 当消息已被接收者接收和处理时, 发送消息并接收ACK<br>如果没有接收到ack, 不认为消息是成功的<br>
     */
    public static final int SEND_OPTIONS_SYNCHRONIZED_ACK = 0x0004;

    /**
     * 发送选项, 发送消息时, 它可以有一个选项标志来触发某些行为. 当消息通过channel 堆栈时, 大多数标志用于触发channel 拦截器. <br>
     * 但是, 有五个channel实现类必须实现的默认标志<br>
     * SEND_OPTIONS_ASYNCHRONOUS - 当消息已被接收者接收和处理时, 发送消息并接收ACK<br>如果没有接收到ack, 不认为消息是成功的<br>
     */
    public static final int SEND_OPTIONS_ASYNCHRONOUS = 0x0008;

    /**
     * 发送选项, 发送消息时, 它可以有一个选项标志来触发某些行为. 当消息通过channel 堆栈时, 大多数标志用于触发channel 拦截器. <br>
     * 但是, 有五个channel实现类必须实现的默认标志<br>
     * SEND_OPTIONS_SECURE - 消息通过加密channel发送<br>
     */
    public static final int SEND_OPTIONS_SECURE = 0x0010;

    /**
     * 发送选项. 当系统中的此标志发送消息时, 使用UDP而不是TCP发送消息
     */
    public static final int SEND_OPTIONS_UDP =  0x0020;

    /**
     * 发送选项. 当系统中的此标志发送消息时, 将UDP消息发送到多播地址, 而不是UDP或TCP到单个地址
     */
    public static final int SEND_OPTIONS_MULTICAST =  0x0040;

    /**
     * 发送选项, 发送消息时, 它可以有一个选项标志来触发某些行为. 当消息通过channel 堆栈时, 大多数标志用于触发channel 拦截器. <br>
     * 但是, 有五个channel实现类必须实现的默认标志<br>
     * SEND_OPTIONS_DEFAULT - 默认的发送选项, 只是一个帮助变量. <br>
     * 默认是<code>int SEND_OPTIONS_DEFAULT = SEND_OPTIONS_USE_ACK;</code><br>
     */
    public static final int SEND_OPTIONS_DEFAULT = SEND_OPTIONS_USE_ACK;


    /**
     * 添加一个拦截器到 channel 信息链.
     * @param interceptor ChannelInterceptor
     */
    public void addInterceptor(ChannelInterceptor interceptor);

    /**
     * 启动 channel. 对于个别服务来说，这可以多次调用.
     * 
     * @param svc int value of <BR>
     * DEFAULT - 启动所有服务 <BR>
     * MBR_RX_SEQ - 启动成员接收器 <BR>
     * MBR_TX_SEQ - 启动成员广播器 <BR>
     * SND_TX_SEQ - 启动复制发送器<BR>
     * SND_RX_SEQ - 启动复制接收器<BR>
     * <b>Note:</b> 为了使成员广播器发送正确的信息, 它必须在复制接收器之后启动.
     * 
     * @throws ChannelException 如果发生启动错误或服务已启动或发生错误.
     */
    public void start(int svc) throws ChannelException;

    /**
     * 关闭 channel. 对于个别服务来说，这可以多次调用.
     * 
     * @param svc int value of <BR>
     * DEFAULT - 关闭所有服务 <BR>
     * MBR_RX_SEQ - 关闭成员接收器 <BR>
     * MBR_TX_SEQ - 关闭成员广播器 <BR>
     * SND_TX_SEQ - 关闭复制发送器 <BR>
     * SND_RX_SEQ - 关闭复制接收器 <BR>
     * @throws ChannelException 如果发生关闭错误或服务已关闭或发生错误.
     */
    public void stop(int svc) throws ChannelException;

    /**
     * 发送一个消息到集群中一个或多个成员.
     * 
     * @param destination Member[] - 目标, 不能是 null 或空数组, 因为成员可能会发生变化, 而且在那时, 应用程序不确定消息实际上被发送到哪个组.
     * @param msg Serializable - 要发送的消息, 必须被序列化, 或一个<code>ByteMessage</code>发送纯字节数组
     * @param options int - 发送者选项, 用于触发配置的拦截器
     * 
     * @return 发送的消息的惟一的 Id
     * @throws ChannelException 如果发生序列化错误.
     */
    public UniqueId send(Member[] destination, Serializable msg, int options) throws ChannelException;

    /**
     * 发送一个消息到集群中一个或多个成员.
     * 
     * @param destination Member[] - 目标, null 或空数组意味着发送给所有人
     * @param msg ClusterMessage - 要发送的消息
     * @param options int - 发送者选项
     * @param handler ErrorHandler - 通过一个回调处理错误, 而不是抛出异常
     * 
     * @return 发送的消息的惟一的 Id
     * @exception ChannelException - 如果发生序列化错误.
     */
    public UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler) throws ChannelException;

    /**
     * 通过拦截器栈发送心跳.
     * 使用此方法让拦截器和其他组件清理垃圾, 超时信息等.<br>
     * 如果应用程序有后台线程, 然后你可以保存一个线程, 通过配置channel 不使用内部心跳线程, 而调用此方法.
     */
    public void heartbeat();

    /**
     * 启用或禁用内部心跳.
     * @param enable boolean - 默认值是特定于实现的
     */
    public void setHeartbeat(boolean enable);

    /**
     * 添加一个成员监听器, 当添加新成员, 成员离开, 成员崩溃时通知
     * <br>如果成员监听器实现了 Heartbeat 接口. <code>heartbeat()</code>方法将被调用，当心跳运行在 channel上时.
     * @param listener MembershipListener
     */
    public void addMembershipListener(MembershipListener listener);

    /**
     * 添加一个channel 监听器, 这是一个接收消息时的回调对象.
     * <br>如果 channel 监听器实现了 Heartbeat 接口. <code>heartbeat()</code>方法将被调用，当心跳运行在 channel上时.
     * @param listener ChannelListener
     */
    public void addChannelListener(ChannelListener listener);

    /**
     * 删除一个成员监听器, 将基于 Object.hashCode 和 Object.equals 移除监听器
     * @param listener MembershipListener
     */
    public void removeMembershipListener(MembershipListener listener);
    /**
     * 删除一个 channel 监听器, 将基于 Object.hashCode 和 Object.equals 移除监听器
     * @param listener ChannelListener
     */
    public void removeChannelListener(ChannelListener listener);

    /**
     * 返回 true, 如果组中有成员, 这个调用和 <code>getMembers().length &gt; 0</code>一样.
     * 
     * @return boolean - true 如果发现了成员
     */
    public boolean hasMembers() ;

    /**
     * 获取当前组中的所有成员.
     * 
     * @return 所有成员或空数组, 永远不会是 null
     */
    public Member[] getMembers() ;

    /**
     * 返回表示这个节点的成员. 这也是通过成员广播组件进行广播的数据.
     * 
     * @param incAlive - 优化, true 如果您想从会员服务开始计算生存时间.
     */
    public Member getLocalMember(boolean incAlive);

    /**
     * 从具有完整和最新数据的成员服务返回成员.
     * 一些实现可以序列化并发送消息, 而不是发送完整的会员详细信息, 只发送成员的主标识符, 而不发送有效载荷或其他信息.
     * 当接收到这样的消息时，应用程序可以通过这个调用检索缓存的成员.<br> 大多数情况下, 这不是必要的.
     * @param mbr Member
     */
    public Member getMember(Member mbr);

    /**
     * 返回这个channel的名称.
     * @return channel name
     */
    public String getName();

    /**
     * 设置这个channel的名称
     * @param name The new channel name
     */
    public void setName(String name);

}
