package org.apache.catalina.tribes;

/**
 * 作为membership 服务的回调.
 */
public interface MembershipListener {
    /**
     * 添加成员
     * @param member Member - 要被添加的成员
     */
    public void memberAdded(Member member);

    /**
     * 删除成员<br>
     * 如果成员自愿离开, Member.getCommand 将包含 Member.SHUTDOWN_PAYLOAD 数据
     * @param member Member
     */
    public void memberDisappeared(Member member);

}