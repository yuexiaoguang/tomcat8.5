package org.apache.catalina.tribes.group;

import java.io.Serializable;

import org.apache.catalina.tribes.Member;

/**
 * 扩展{@link RpcCallback} 接口.
 * 允许一个 RPC messenger 得到确认, 如果答复成功发送到原始发送方.
 */
public interface ExtendedRpcCallback extends RpcCallback {

    /**
     * 回复失败.
     * 
     * @param request - 回复的请求的原始消息
     * @param response - 回复的消息
     * @param sender - 回复的请求的发送者
     * @param reason - 回复失败的原因
     */
    public void replyFailed(Serializable request, Serializable response, Member sender, Exception reason);

    /**
     * 回复成功
     * 
     * @param request - 回复的请求的原始消息
     * @param response - 回复的消息
     * @param sender - 回复的请求的发送者
     */
    public void replySucceeded(Serializable request, Serializable response, Member sender);
}
