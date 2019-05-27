package org.apache.catalina.tribes.group;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.catalina.tribes.Member;

/**
 * <p>一个简单的, 令人满意和有效的排序会员的方式</p>
 * <p>
 *    排序成员可以作为选择负责人或协调工作的基础.<br>
 *    它基于<code>Member</code> 接口和以下格式的排序成员:
 * </p>
 * <ol>
 *     <li>IP 比较 - 一个字节一个字节的, 更低的字节更高的等级</li>
 *     <li>IPv4 比较等级高于 IPv6, 即字节数较少, 等级更高</li>
 *     <li>端口比较 - 更低的端口, 更高的等级</li>
 *     <li>惟一的ID比较- 一个字节一个字节的, 更低的字节更高的等级</li>
 * </ol>
 */
public class AbsoluteOrder {
    public static final AbsoluteComparator comp = new AbsoluteComparator();

    protected AbsoluteOrder() {
        super();
    }


    public static void absoluteOrder(Member[] members) {
        if ( members == null || members.length <= 1 ) return;
        Arrays.sort(members,comp);
    }

    public static void absoluteOrder(List<Member> members) {
        if ( members == null || members.size() <= 1 ) return;
        java.util.Collections.sort(members, comp);
    }

    public static class AbsoluteComparator implements Comparator<Member>,
            Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public int compare(Member m1, Member m2) {
            int result = compareIps(m1,m2);
            if ( result == 0 ) result = comparePorts(m1,m2);
            if ( result == 0 ) result = compareIds(m1,m2);
            return result;
        }

        public int compareIps(Member m1, Member m2) {
            return compareBytes(m1.getHost(),m2.getHost());
        }

        public int comparePorts(Member m1, Member m2) {
            return compareInts(m1.getPort(),m2.getPort());
        }

        public int compareIds(Member m1, Member m2) {
            return compareBytes(m1.getUniqueId(),m2.getUniqueId());
        }

        protected int compareBytes(byte[] d1, byte[] d2) {
            int result = 0;
            if ( d1.length == d2.length ) {
                for (int i=0; (result==0) && (i<d1.length); i++) {
                    result = compareBytes(d1[i],d2[i]);
                }
            } else if ( d1.length < d2.length) {
                result = -1;
            } else {
                result = 1;
            }
            return result;
        }

        protected int compareBytes(byte b1, byte b2) {
            return compareInts(b1,b2);
        }

        protected int compareInts(int b1, int b2) {
            int result = 0;
            if ( b1 == b2 ) {

            } else if ( b1 < b2) {
                result = -1;
            } else {
                result = 1;
            }
            return result;
        }
    }
}
