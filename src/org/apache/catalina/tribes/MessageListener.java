package org.apache.catalina.tribes;

/**
 * <p>使用ChannelReceiver注册的监听器, 内部 Tribes 组件</p>
 */
public interface MessageListener {

    /**
     * 从Channel栈中的IO组件接收一个消息
     * @param msg ChannelMessage
     */
    public void messageReceived(ChannelMessage msg);

    public boolean accept(ChannelMessage msg);
}
