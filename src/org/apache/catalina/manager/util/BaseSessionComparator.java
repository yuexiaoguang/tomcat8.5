package org.apache.catalina.manager.util;

import java.util.Comparator;

import org.apache.catalina.Session;

/**
 * 允许在会话内容上进行比较.
 *
 * @param <T> 要比较的会话内容的类型
 */
public abstract class BaseSessionComparator<T> implements Comparator<Session> {

    public BaseSessionComparator() {
        super();
    }

    public abstract Comparable<T> getComparableObject(Session session);

    @SuppressWarnings("unchecked")
    @Override
    public final int compare(Session s1, Session s2) {
        Comparable<T> c1 = getComparableObject(s1);
        Comparable<T> c2 = getComparableObject(s2);
        return c1==null ? (c2==null ? 0 : -1) : (c2==null ? 1 : c1.compareTo((T)c2));
    }
}
