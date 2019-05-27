package org.apache.catalina.tribes.membership;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.catalina.tribes.Member;

/**
 * 使用多播.
 * 表示一个多播成员.
 * 此类负责维护集群中活动集群节点的列表. 如果一个节点不能发出心跳, 将清除该节点.
 */
public class Membership implements Cloneable {

    protected static final Member[] EMPTY_MEMBERS = new Member[0];

    private final Object membersLock = new Object();

    /**
     * 本地成员.
     */
    protected final Member local;

    /**
     * 集群中所有的成员.
     */
    protected HashMap<Member, MbrEntry> map = new HashMap<>(); // Guarded by membersLock

    /**
     * 集群中所有的成员.
     */
    protected volatile Member[] members = EMPTY_MEMBERS; // Guarded by membersLock

    /**
     * 按生存时间排序成员的比较器.
     */
    protected final Comparator<Member> memberComparator;

    @Override
    public Object clone() {
        synchronized (membersLock) {
            Membership clone = new Membership(local, memberComparator);
            @SuppressWarnings("unchecked")
            final HashMap<Member, MbrEntry> tmpclone = (HashMap<Member, MbrEntry>) map.clone();
            clone.map = tmpclone;
            clone.members = members.clone();
            return clone;
        }
    }

    /**
     * @param local - 必须是本地成员的名字. 用于从集群成员筛选本地成员
     * @param includeLocal - TBA
     */
    public Membership(Member local, boolean includeLocal) {
        this(local, new MemberComparator(), includeLocal);
    }

    public Membership(Member local) {
        this(local, false);
    }

    public Membership(Member local, Comparator<Member> comp) {
        this(local, comp, false);
    }

    public Membership(Member local, Comparator<Member> comp, boolean includeLocal) {
        this.local = local;
        this.memberComparator = comp;
        if (includeLocal) {
            addMember(local);
        }
    }

    /**
     * 重置会员并重新开始. i.e., 删除所有成员并等待它们再次ping并加入该成员.
     */
    public void reset() {
        synchronized (membersLock) {
            map.clear();
            members = EMPTY_MEMBERS ;
        }
    }

    /**
     * 通知会员此会员已经广播自己.
     *
     * @param member - 刚刚 ping我们的成员
     * @return - true 如果成员是新加入集群的, 否则false.<br>
     * - false 如果成员是本地成员或更新.
     */
    public boolean memberAlive(Member member) {
        // Ignore ourselves
        if (member.equals(local)) {
            return false;
        }

        boolean result = false;
        synchronized (membersLock) {
            MbrEntry entry = map.get(member);
            if (entry == null) {
                entry = addMember(member);
                result = true;
            } else {
                // 更新会员活动时间
                Member updateMember = entry.getMember();
                if (updateMember.getMemberAliveTime() != member.getMemberAliveTime()) {
                    // 更新可以更改的字段
                    updateMember.setMemberAliveTime(member.getMemberAliveTime());
                    updateMember.setPayload(member.getPayload());
                    updateMember.setCommand(member.getCommand());
                    // Re-order. 不能在原地分类, 因为调用 getMembers(), 然后可以接收中间结果.
                    Member[] newMembers = members.clone();
                    Arrays.sort(newMembers, memberComparator);
                    members = newMembers;
                }
            }
            entry.accessed();
        }
        return result;
    }

    /**
     * 向该组件添加成员并排序数组, 使用 memberComparator
     *
     * @param member 要添加的成员
     *
     * @return 为新成员创建的成员条目.
     */
    public MbrEntry addMember(Member member) {
        MbrEntry entry = new MbrEntry(member);
        synchronized (membersLock) {
            if (!map.containsKey(member) ) {
                map.put(member, entry);
                Member results[] = new Member[members.length + 1];
                System.arraycopy(members, 0, results, 0, members.length);
                results[members.length] = member;
                Arrays.sort(results, memberComparator);
                members = results;
            }
        }
        return entry;
    }

    /**
     * 从该组件中删除成员.
     *
     * @param member 要删除的成员
     */
    public void removeMember(Member member) {
        synchronized (membersLock) {
            map.remove(member);
            int n = -1;
            for (int i = 0; i < members.length; i++) {
                if (members[i] == member || members[i].equals(member)) {
                    n = i;
                    break;
                }
            }
            if (n < 0) return;
            Member results[] = new Member[members.length - 1];
            int j = 0;
            for (int i = 0; i < members.length; i++) {
                if (i != n) {
                    results[j++] = members[i];
                }
            }
            members = results;
        }
    }

    /**
     * 运行刷新循环并返回已过期的成员列表.
     * 这也删除了成员, 以这样的方式 getMembers() = getMembers() - expire()
     * @param maxtime - 会员在未被视为死亡之前的最大时间.
     * @return 过期成员列表
     */
    public Member[] expire(long maxtime) {
        synchronized (membersLock) {
            if (!hasMembers()) {
               return EMPTY_MEMBERS;
            }

            ArrayList<Member> list = null;
            Iterator<MbrEntry> i = map.values().iterator();
            while (i.hasNext()) {
                MbrEntry entry = i.next();
                if (entry.hasExpired(maxtime)) {
                    if (list == null) {
                        // 当会员过期时只需要一个列表 (smaller gc)
                        list = new java.util.ArrayList<>();
                    }
                    list.add(entry.getMember());
                }
            }

            if (list != null) {
                Member[] result = new Member[list.size()];
                list.toArray(result);
                for (int j=0; j<result.length; j++) {
                    removeMember(result[j]);
                }
                return result;
            } else {
                return EMPTY_MEMBERS ;
            }
        }
    }

    /**
     * 返回该服务是否有成员.
     *
     * @return <code>true</code> 如果有一个或多个成员, 否则<code>false</code>
     */
    public boolean hasMembers() {
        return members.length > 0;
    }


    public Member getMember(Member mbr) {
        Member[] members = this.members;
        if (members.length > 0) {
            for (int i = 0; i < members.length; i++) {
                if (members[i].equals(mbr)) {
                    return members[i];
                }
            }
        }
        return null;
    }

    public boolean contains(Member mbr) {
        return getMember(mbr) != null;
    }

    /**
     * 返回所有成员. 不需要复制: 添加和移除生成新数组.
     */
    public Member[] getMembers() {
        return members;
    }


    // --------------------------------------------- Inner Class

    private static class MemberComparator implements Comparator<Member>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public int compare(Member m1, Member m2) {
            // Longer alive time, means sort first
            long result = m2.getMemberAliveTime() - m1.getMemberAliveTime();
            if (result < 0) {
                return -1;
            } else if (result == 0) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    /**
     * 表示一个成员
     */
    protected static class MbrEntry {

        protected final Member mbr;
        protected long lastHeardFrom;

        public MbrEntry(Member mbr) {
           this.mbr = mbr;
        }

        /**
         * 指示此成员已被访问.
         */
        public void accessed(){
           lastHeardFrom = System.currentTimeMillis();
        }

        /**
         * 获取关联的成员.
         */
        public Member getMember() {
            return mbr;
        }

        /**
         * 检查成员是否过期.
         *
         * @param maxtime The time threshold
         *
         * @return <code>true</code> 如果成员已过期, 否则<code>false</code>
         */
        public boolean hasExpired(long maxtime) {
            long delta = System.currentTimeMillis() - lastHeardFrom;
            return delta > maxtime;
        }
    }
}
