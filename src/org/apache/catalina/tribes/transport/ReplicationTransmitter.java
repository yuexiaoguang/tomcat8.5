package org.apache.catalina.tribes.transport;

import javax.management.ObjectName;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ChannelSender;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.transport.nio.PooledParallelSender;

/**
 * 向其他集群成员发送消息.
 * 基于replicationMode类型创建实际的发送者
 */
public class ReplicationTransmitter implements ChannelSender {

    private Channel channel;

    private ObjectName oname = null;

    public ReplicationTransmitter() {
    }

    private MultiPointSender transport = new PooledParallelSender();

    public MultiPointSender getTransport() {
        return transport;
    }

    public void setTransport(MultiPointSender transport) {
        this.transport = transport;
    }

    // ------------------------------------------------------------- public

    /**
     * 发送数据给一个成员
     */
    @Override
    public void sendMessage(ChannelMessage message, Member[] destination) throws ChannelException {
        MultiPointSender sender = getTransport();
        sender.sendMessage(destination,message);
    }


    /**
     * 启动发送者并注册传送者 mbean
     */
    @Override
    public void start() throws java.io.IOException {
        getTransport().connect();
        // register jmx
        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
        if (jmxRegistry != null) this.oname = jmxRegistry.registerJmx(",component=Sender", transport);
    }

    /**
     * 停止发送者并注销 mbean (传送者, 发送者)
     */
    @Override
    public synchronized void stop() {
        getTransport().disconnect();
        if (oname != null) {
            JmxRegistry.getRegistry(channel).unregisterJmx(oname);
            oname = null;
        }
        channel = null;
    }

    /**
     * 调用传送者检查发送者 socket 状态
     */
    @Override
    public void heartbeat() {
        if (getTransport()!=null) getTransport().keepalive();
    }

    /**
     * 添加新的集群成员并创建发送者 ( s. replicationMode), 向发送者传输当前属性
     */
    @Override
    public synchronized void add(Member member) {
        getTransport().add(member);
    }

    /**
     * 从传送者删除发送者. ( 注销 mbean 并断开发送者连接 )
     */
    @Override
    public synchronized void remove(Member member) {
        getTransport().remove(member);
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
