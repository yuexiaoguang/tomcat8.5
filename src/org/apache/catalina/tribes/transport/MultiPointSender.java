package org.apache.catalina.tribes.transport;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;

public interface MultiPointSender extends DataSender
{
    public void sendMessage(Member[] destination, ChannelMessage data) throws ChannelException;
    public void setMaxRetryAttempts(int attempts);
    public void setDirectBuffer(boolean directBuf);
    public void add(Member member);
    public void remove(Member member);
}
