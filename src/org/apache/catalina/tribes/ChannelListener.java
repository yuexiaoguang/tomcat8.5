package org.apache.catalina.tribes;

import java.io.Serializable;

/**
 * <p>监听从channel过来的消息的接口</p>
 * 当接收消息时, Channel 将在一个条件序列中执行channel监听器.
 * <code>if ( listener.accept(msg,sender) ) listener.messageReceived(msg,sender);</code><br>
 * 如果不打算处理消息, ChannelListener 实现类的<code>accept(Serializable, Member)</code>绝对不能返回 true.
 * channel 使用上面的方法跟踪消息是否被处理, 或者只是接收到但没有处理, 一个特征需要支持 message-response(RPC) 调用<br>
 */
public interface ChannelListener {

    /**
     * 从 channel接收消息
     * @param msg Serializable
     * @param sender - 消息的源
     */
    public void messageReceived(Serializable msg, Member sender);

    /**
     * 由 channel 执行，来确定监听器是否将处理这个消息.
     * @param msg Serializable
     * @param sender Member
     * @return boolean
     */
    public boolean accept(Serializable msg, Member sender);

    /**
     * @param listener Object
     * @return boolean
     */
    @Override
    public boolean equals(Object listener);

    /**
     * @return int
     */
    @Override
    public int hashCode();

}
