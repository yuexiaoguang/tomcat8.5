package org.apache.catalina.tribes;

import java.io.IOException;


/**
 * 底层的数据发送者组件, IO 层.<br>
 * channel发送者必须支持"silent" 成员, 即, 能发送一个消息到不在组织中的成员, 但却是目标参数的一部分
 */
public interface ChannelSender extends Heartbeat
{
    /**
     * 通知发送者添加了一个成员.<br>
     * 可选. 可以是一个空方法
     * @param member Member
     */
    public void add(Member member);
    
    /**
     * 通知成员已被删除或崩溃. 可以用于清理打开的连接等.
     * 
     * @param member Member
     */
    public void remove(Member member);

    /**
     * 启动channel 发送者
     * @throws IOException 如果预处理发生并且发生错误
     */
    public void start() throws IOException;

    /**
     * 关闭 channel 发送者
     */
    public void stop();

    /**
     * channel 心跳, 使用这个方法清理资源
     */
    @Override
    public void heartbeat() ;

    /**
     * 向一个或多个收件人发送消息.
     * @param message ChannelMessage - 要发送的消息
     * @param destination Member[] - 目标
     * @throws ChannelException - 如果发生错误, ChannelSender 必须给每个成员逐个报告发送失败, 使用 ChannelException.addFaultyMember
     */
    public void sendMessage(ChannelMessage message, Member[] destination) throws ChannelException;

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
