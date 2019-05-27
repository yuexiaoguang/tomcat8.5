package org.apache.catalina.tribes.group;

import java.io.Serializable;

import org.apache.catalina.tribes.Member;

/**
 * 用于Tribes channel 向请求的响应请求对象.
 */
public interface RpcCallback {

    /**
     * 允许发送一个响应到接收到的信息.
     * 
     * @param msg The message
     * @param sender Member
     * @return 可序列化对象, 或<code>null</code>
     */
    public Serializable replyRequest(Serializable msg, Member sender);

    /**
     * 如果答复已被发送到请求的线程, rpc 回调可以处理在之后出现的任何数据.
     * @param msg The message
     * @param sender Member
     */
    public void leftOver(Serializable msg, Member sender);

}