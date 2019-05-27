package org.apache.catalina.tribes.group;

import java.io.Serializable;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.ErrorHandler;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.UniqueId;

public interface GroupChannelMBean {

    // Attributes
    public boolean getOptionCheck();

    public boolean getHeartbeat();

    public long getHeartbeatSleeptime();

    // Operations
    public void start(int svc) throws ChannelException;

    public void stop(int svc) throws ChannelException;

    public UniqueId send(Member[] destination, Serializable msg, int options)
            throws ChannelException;

    public UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler)
            throws ChannelException;

    public void addMembershipListener(MembershipListener listener);

    public void addChannelListener(ChannelListener listener);

    public void removeMembershipListener(MembershipListener listener);

    public void removeChannelListener(ChannelListener listener);

    public boolean hasMembers() ;

    public Member[] getMembers() ;

    public Member getLocalMember(boolean incAlive);

}
