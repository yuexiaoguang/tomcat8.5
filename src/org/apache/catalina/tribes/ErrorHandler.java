package org.apache.catalina.tribes;


/**
 * 在异步发送消息和消息发送后应用仍然需要获取配置时使用.
 */
public interface ErrorHandler {

    /**
     * 异步发送消息时或发生错误时执行
     * 
     * @param x ChannelException - 发生的错误
     * @param id - 消息惟一的id
     */
    public void handleError(ChannelException x, UniqueId id);

    /**
     * 消息成功发送时执行
     * @param id - 消息惟一的id
     */
    public void handleCompletion(UniqueId id);

}