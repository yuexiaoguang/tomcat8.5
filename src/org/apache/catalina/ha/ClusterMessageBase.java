package org.apache.catalina.ha;

import org.apache.catalina.tribes.Member;

public abstract class ClusterMessageBase implements ClusterMessage {

    private static final long serialVersionUID = 1L;

    private long timestamp;
    protected transient Member address;

    public ClusterMessageBase() {
        // NO-OP
    }

    @Override
    public Member getAddress() {
        return address;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setAddress(Member member) {
        this.address = member;
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
