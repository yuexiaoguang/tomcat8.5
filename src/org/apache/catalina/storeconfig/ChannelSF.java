package org.apache.catalina.storeconfig;

import java.io.PrintWriter;
import java.util.Iterator;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.ChannelSender;
import org.apache.catalina.tribes.ManagedChannel;
import org.apache.catalina.tribes.MembershipService;

/**
 * 生成Channel 元素
 */
public class ChannelSF extends StoreFactoryBase {

    /**
     * 存储指定的Channel 子级.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 缩进此元素的空格数量
     * @param aChannel 要存储属性的 Channel
     *
     * @exception Exception 存储期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aChannel,
            StoreDescription parentDesc) throws Exception {
        if (aChannel instanceof Channel) {
            Channel channel = (Channel) aChannel;
            if (channel instanceof ManagedChannel) {
                ManagedChannel managedChannel = (ManagedChannel) channel;
                // 存储嵌套的 <Membership> 元素
                MembershipService service = managedChannel.getMembershipService();
                if (service != null) {
                    storeElement(aWriter, indent, service);
                }
                // 存储嵌套的 <Sender> 元素
                ChannelSender sender = managedChannel.getChannelSender();
                if (sender != null) {
                    storeElement(aWriter, indent, sender);
                }
                // 存储嵌套的 <Receiver> 元素
                ChannelReceiver receiver = managedChannel.getChannelReceiver();
                if (receiver != null) {
                    storeElement(aWriter, indent, receiver);
                }
                Iterator<ChannelInterceptor> interceptors = managedChannel.getInterceptors();
                while (interceptors.hasNext()) {
                    ChannelInterceptor interceptor = interceptors.next();
                    storeElement(aWriter, indent, interceptor);
                }
            }
       }
    }
}