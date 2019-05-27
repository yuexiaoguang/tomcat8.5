package org.apache.catalina.tribes;

import org.apache.catalina.tribes.group.InterceptorPayload;

/**
 * 拦截channel栈中的消息和成员消息的拦截器.
 * 这允许截取器在发送或接收消息时修改消息或执行其他操作.<br>
 * 拦截器在一个链表中捆绑在一起.
 */
public interface ChannelInterceptor extends MembershipListener, Heartbeat {

    /**
     * 拦截器可以根据消息选项上的设置位对消息作出反应. <br>
     * 当消息发送的时候, 选项可以从 ChannelMessage.getOptions()检索，而且 bit 被设置, 这个拦截器会对它作出反应.<br>
     * 如果拦截器应该对消息作出反应，将进行简单的评估:<br>
     * <code>boolean react = (getOptionFlag() == (getOptionFlag() &amp; ChannelMessage.getOptions()));</code><br>
     * 默认的选项是 0, 这意味着应用程序无法触发拦截器. 由拦截器本身决定.<br>
     */
    public int getOptionFlag();

    /**
     * 设置选项标志
     * @param flag int
     */
    public void setOptionFlag(int flag);

    /**
     * 设置拦截器列表中下一个拦截器
     * @param next ChannelInterceptor
     */
    public void setNext(ChannelInterceptor next) ;

    /**
     * 获取列表中下一个拦截器
     * @return ChannelInterceptor - 下一个拦截器或 null
     */
    public ChannelInterceptor getNext();

    /**
     * 设置列表中上一个拦截器
     * @param previous ChannelInterceptor
     */
    public void setPrevious(ChannelInterceptor previous);

    /**
     * 获取列表中上一个拦截器
     * @return ChannelInterceptor - 上一个拦截器或 null
     */
    public ChannelInterceptor getPrevious();

    /**
     * 发送消息给目标.
     * 拦截器可以编辑任何参数，并通过调用<code>getNext().sendMessage(destination,msg,payload)</code>向下传递<br>
     * 同样，拦截器可以选择不调用 <code>getNext().sendMessage(destination,msg,payload)</code>而停止消息发送<br>
     * 如果要异步发送消息，则可以通过附加到有效载荷对象的错误处理程序传递, 来通知应用程序是否完成和错误.<br>
     * ChannelMessage.getAddress 包含 Channel.getLocalMember, 并且可以被从另一个节点发送的相似的消息覆盖.<br>
     * 
     * @param destination Member[] - 这个消息的目标
     * @param msg ChannelMessage - 要发送的消息
     * @param payload InterceptorPayload - 有效负荷, 携带错误处理程序和将来有用的数据, 可以是 null
     * @throws ChannelException 如果发生序列化错误
     */
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException;

    /**
     * 接收消息时执行.
     * <code>ChannelMessage.getAddress()</code>是发送者, 如果已经覆盖, 则是回复地址.
     * @param data ChannelMessage
     */
    public void messageReceived(ChannelMessage data);

    /**
     * 周期性的执行，允许拦截器清理资源, 超时对象和执行与发送/接收数据无关的操作.
     */
    @Override
    public void heartbeat();

    /**
     * @return boolean - channel的组中是否有成员
     */
    public boolean hasMembers() ;

    /**
     * @return Member[]
     */
    public Member[] getMembers() ;

    /**
     * @param incAliveTime boolean
     * @return Member
     */
    public Member getLocalMember(boolean incAliveTime) ;

    /**
     * @param mbr Member
     * @return Member - 实际的成员信息, 包括是否存活
     */
    public Member getMember(Member mbr);

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
    public void start(int svc) throws ChannelException;

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
    public void stop(int svc) throws ChannelException;

    public void fireInterceptorEvent(InterceptorEvent event);

    /**
     * 返回关联到拦截器的 channel
     */
    public Channel getChannel();

    /**
     * 设置关联到拦截器的 channel
     * @param channel The channel
     */
    public void setChannel(Channel channel);

    interface InterceptorEvent {
        int getEventType();
        String getEventTypeDesc();
        ChannelInterceptor getInterceptor();
    }
}
