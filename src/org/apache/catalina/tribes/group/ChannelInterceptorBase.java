package org.apache.catalina.tribes.group;

import javax.management.ObjectName;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.jmx.JmxRegistry;

/**
 * 拦截器的父类.
 */
public abstract class ChannelInterceptorBase implements ChannelInterceptor {

    private ChannelInterceptor next;
    private ChannelInterceptor previous;
    private Channel channel;
    //default value, always process
    protected int optionFlag = 0;

    /**
     * 这个ChannelInterceptor的 ObjectName.
     */
    private ObjectName oname = null;

    public ChannelInterceptorBase() {

    }

    public boolean okToProcess(int messageFlags) {
        if (this.optionFlag == 0 ) return true;
        return ((optionFlag&messageFlags) == optionFlag);
    }

    @Override
    public final void setNext(ChannelInterceptor next) {
        this.next = next;
    }

    @Override
    public final ChannelInterceptor getNext() {
        return next;
    }

    @Override
    public final void setPrevious(ChannelInterceptor previous) {
        this.previous = previous;
    }

    @Override
    public void setOptionFlag(int optionFlag) {
        this.optionFlag = optionFlag;
    }

    @Override
    public final ChannelInterceptor getPrevious() {
        return previous;
    }

    @Override
    public int getOptionFlag() {
        return optionFlag;
    }

    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws
        ChannelException {
        if (getNext() != null) getNext().sendMessage(destination, msg, payload);
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if (getPrevious() != null) getPrevious().messageReceived(msg);
    }

    @Override
    public void memberAdded(Member member) {
        //notify upwards
        if (getPrevious() != null) getPrevious().memberAdded(member);
    }

    @Override
    public void memberDisappeared(Member member) {
        //notify upwards
        if (getPrevious() != null) getPrevious().memberDisappeared(member);
    }

    @Override
    public void heartbeat() {
        if (getNext() != null) getNext().heartbeat();
    }

    /**
     * 是否有成员
     */
    @Override
    public boolean hasMembers() {
        if ( getNext()!=null )return getNext().hasMembers();
        else return false;
    }

    /**
     * 获取当前集群的所有成员
     * @return 所有成员或空数组
     */
    @Override
    public Member[] getMembers() {
        if ( getNext()!=null ) return getNext().getMembers();
        else return null;
    }

    /**
     * @param mbr Member
     * @return Member
     */
    @Override
    public Member getMember(Member mbr) {
        if ( getNext()!=null) return getNext().getMember(mbr);
        else return null;
    }

    /**
     * 返回表示这个节点的成员.
     *
     * @return Member
     */
    @Override
    public Member getLocalMember(boolean incAlive) {
        if ( getNext()!=null ) return getNext().getLocalMember(incAlive);
        else return null;
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
        if ( getNext()!=null ) getNext().start(svc);
        // register jmx
        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
        if (jmxRegistry != null) this.oname = jmxRegistry.registerJmx(
                ",component=Interceptor,interceptorName=" + getClass().getSimpleName(), this);
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
        if (getNext() != null) getNext().stop(svc);
        if (oname != null) {
            JmxRegistry.getRegistry(channel).unregisterJmx(oname);
            oname = null;
        }
        channel = null;
    }

    @Override
    public void fireInterceptorEvent(InterceptorEvent event) {
        //empty operation
    }

    /**
     * 返回关联到拦截器的 channel
     * @return Channel
     */
    @Override
    public Channel getChannel() {
        return channel;
    }

    /**
     * 设置关联到拦截器的 channel
     * @param channel The channel
     */
    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
