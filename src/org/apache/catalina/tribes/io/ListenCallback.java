package org.apache.catalina.tribes.io;

import org.apache.catalina.tribes.ChannelMessage;

/**
 * 内部接口, 类似于 MessageListener, 但在IO 基础使用.
 * 当接收到数据时, 复制系统使用监听回调接口. 接口不关心对象和编组, 只是直接传递字节.
 */
public interface ListenCallback
{
    /**
     * 通知它已经从一个集群节点接收到新数据.
     * 
     * @param data - 从集群/复制系统接收的消息字节
     */
     public void messageDataReceived(ChannelMessage data);

}