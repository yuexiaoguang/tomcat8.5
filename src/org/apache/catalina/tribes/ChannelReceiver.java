package org.apache.catalina.tribes;


/**
 * 底层的数据接收者组件, IO 层.
 * 这个类可以可选的实现线程池，批量处理到来的消息.
 */
public interface ChannelReceiver extends Heartbeat {
    public static final int MAX_UDP_SIZE = 65535;

    /**
     * 启动监听host/port上到来的消息
     * @throws java.io.IOException 监听失败
     */
    public void start() throws java.io.IOException;

    /**
     * 停止监听消息
     */
    public void stop();

    /**
     * 监听的 IPv4 或 IPv6 地址.
     */
    public String getHost();


    /**
     * 返回监听的端口
     */
    public int getPort();

    /**
     * 返回安全监听端口
     * @return port, -1 如果未启用安全监听端口
     */
    public int getSecurePort();

    /**
     * 返回 UDP 端口
     * @return port, -1 如果未启用UDP端口.
     */
    public int getUdpPort();

    /**
     * 设置接收通知的消息监听器
     * @param listener MessageListener
     */
    public void setMessageListener(MessageListener listener);

    /**
     * 返回这个接收器关联的消息监听器
     */
    public MessageListener getMessageListener();

    /**
     * 返回相关的channel
     */
    public Channel getChannel();

    /**
     * 设置相关的channel
     * @param channel The channel
     */
    public void setChannel(Channel channel);

}
