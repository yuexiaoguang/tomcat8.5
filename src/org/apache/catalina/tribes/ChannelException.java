package org.apache.catalina.tribes;

import java.util.ArrayList;

/**
 * 当channel中发生内部错误时抛出. <br>
 * 当发生一个全局错误时, 可以使用<code>getCause()</code>检索原因<br><br>
 * 如果应用程序正在发送消息, 一些收件人未能接收到该消息, 应用可以使用<code>getFaultyMembers()</code>方法检索哪个接收者接收失败.
 * 这样, 应用将总是知道消息是否交付成功.
 */
public class ChannelException extends Exception {
    private static final long serialVersionUID = 1L;
    /**
     * 避免重排列表的空列表
     */
    protected static final FaultyMember[] EMPTY_LIST = new FaultyMember[0];
    /**
     * 错误成员列表
     */
    private ArrayList<FaultyMember> faultyMembers=null;

    public ChannelException() {
        super();
    }

    public ChannelException(String message) {
        super(message);
    }

    public ChannelException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChannelException(Throwable cause) {
        super(cause);
    }

    /**
     * 返回异常的消息
     * @return 错误消息
     */
    @Override
    public String getMessage() {
        StringBuilder buf = new StringBuilder(super.getMessage());
        if (faultyMembers==null || faultyMembers.size() == 0 ) {
            buf.append("; No faulty members identified.");
        } else {
            buf.append("; Faulty members:");
            for ( int i=0; i<faultyMembers.size(); i++ ) {
                FaultyMember mbr = faultyMembers.get(i);
                buf.append(mbr.getMember().getName());
                buf.append("; ");
            }
        }
        return buf.toString();
    }

    /**
     * 添加一个错误成员, 和成员失败的原因.
     * @param mbr Member
     * @param x Exception
     * @return <code>true</code>添加成功
     */
    public boolean addFaultyMember(Member mbr, Exception x ) {
        return addFaultyMember(new FaultyMember(mbr,x));
    }

    /**
     * 添加一组错误成员
     * @param mbrs FaultyMember[]
     * @return 添加的成员的数量
     */
    public int addFaultyMember(FaultyMember[] mbrs) {
        int result = 0;
        for (int i=0; mbrs!=null && i<mbrs.length; i++ ) {
            if ( addFaultyMember(mbrs[i]) ) result++;
        }
        return result;
    }

    /**
     * 添加一个错误成员
     * @param mbr FaultyMember
     * @return <code>true</code>添加成功
     */
    public boolean addFaultyMember(FaultyMember mbr) {
        if ( this.faultyMembers==null ) this.faultyMembers = new ArrayList<>();
        if ( !faultyMembers.contains(mbr) ) return faultyMembers.add(mbr);
        else return false;
    }

    /**
     * 返回一组失败的成员及其失败的原因.
     */
    public FaultyMember[] getFaultyMembers() {
        if ( this.faultyMembers==null ) return EMPTY_LIST;
        return faultyMembers.toArray(new FaultyMember[faultyMembers.size()]);
    }

    /**
     * <p>当消息被发送给不止一个成员时，表示特定成员的失败</p>
     */
    public static class FaultyMember {
        protected final Exception cause;
        protected final Member member;
        public FaultyMember(Member mbr, Exception x) {
            this.member = mbr;
            this.cause = x;
        }

        public Member getMember() {
            return member;
        }

        public Exception getCause() {
            return cause;
        }

        @Override
        public String toString() {
            return "FaultyMember:"+member.toString();
        }

        @Override
        public int hashCode() {
            return (member!=null)?member.hashCode():0;
        }

        @Override
        public boolean equals(Object o) {
            if (member==null || (!(o instanceof FaultyMember)) || (((FaultyMember)o).member==null)) return false;
            return member.equals(((FaultyMember)o).member);
        }
    }
}
