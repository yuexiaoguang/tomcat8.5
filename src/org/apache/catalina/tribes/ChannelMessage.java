package org.apache.catalina.tribes;

import java.io.Serializable;

import org.apache.catalina.tribes.io.XByteBuffer;

/**
 * 通过拦截器堆栈传递的消息, 在数据在Channel对象中序列化之后, 然后传递给拦截器, 最终传递给ChannelSender 组件
 */
public interface ChannelMessage extends Serializable, Cloneable {


    /**
     * 获取此消息来源的地址.
     * 几乎总是<code>Channel.getLocalMember(boolean)</code><br>
     * 这将被设置为不同的地址, 如果消息是从主机发送的，而不是原来发送的.
     * 
     * @return 此消息的来源或回复地址
     */
    public Member getAddress();

    /**
     * 设置此消息的源或答复地址
     * @param member Member
     */
    public void setAddress(Member member);

    /**
     * 创建消息时的时间戳.
     * @return long timestamp in milliseconds
     */
    public long getTimestamp();

    /**
     *
     * 设置此消息的时间戳
     * @param timestamp The timestamp
     */
    public void setTimestamp(long timestamp);

    /**
     * 每一个消息必须有一个全局惟一的 Id.
     * 拦截器很大程度上依赖于这个ID来进行消息处理
     * @return byte
     */
    public byte[] getUniqueId();

    /**
     * 包含实际消息有效载荷的字节缓冲区
     * @param buf XByteBuffer
     */
    public void setMessage(XByteBuffer buf);

    /**
     * 包含实际消息有效载荷的字节缓冲区
     */
    public XByteBuffer getMessage();

    /**
     * 消息选项是一个32位标志集，它触发拦截器和消息行为.
     * 
     * @return int - 此消息设置的选项位
     */
    public int getOptions();

    /**
     * 设置此消息的选项位
     * @param options int
     */
    public void setOptions(int options);

    /**
     * 浅克隆, 克隆什么取决于实现
     */
    public Object clone();

    /**
     * 深克隆, 必须克隆所有字段
     */
    public Object deepclone();
}
