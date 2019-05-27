package org.apache.catalina.ha;

import java.io.Serializable;

import org.apache.catalina.tribes.Member;

public interface ClusterMessage extends Serializable {
    public Member getAddress();
    public void setAddress(Member member);
    public String getUniqueId();
    public long getTimestamp();
    public void setTimestamp(long timestamp);
}
