package org.apache.catalina.tribes;

import java.util.Iterator;

/**
 * 配置channel组件, 例如 sender, receiver, interceptor等
 */
public interface ManagedChannel extends Channel {

    /**
     * Sets the channel sender
     * @param sender ChannelSender
     */
    public void setChannelSender(ChannelSender sender);

    /**
     * Sets the channel receiver
     * @param receiver ChannelReceiver
     */
    public void setChannelReceiver(ChannelReceiver receiver);

    /**
     * Sets the membership service
     * @param service MembershipService
     */
    public void setMembershipService(MembershipService service);

    /**
     * returns the channel sender
     * @return ChannelSender
     */
    public ChannelSender getChannelSender();

    /**
     * returns the channel receiver
     * @return ChannelReceiver
     */
    public ChannelReceiver getChannelReceiver();

    /**
     * Returns the membership service
     * @return MembershipService
     */
    public MembershipService getMembershipService();

    /**
     * 返回拦截器栈
     * @return Iterator
     */
    public Iterator<ChannelInterceptor> getInterceptors();
}
