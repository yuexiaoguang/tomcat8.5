package org.apache.tomcat.dbcp.dbcp2;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.tomcat.dbcp.pool2.TrackedUse;

/**
 * 跟踪数据库连接使用情况, 以恢复和报告已放弃的数据库连接.
 *
 * JDBC Connection, Statement, ResultSet 类继承这个类.
 */
public class AbandonedTrace implements TrackedUse {

    /** 此对象的子级创建的对象列表 */
    private final List<WeakReference<AbandonedTrace>> traceList = new ArrayList<>();
    /** 此连接的最后使用时间 */
    private volatile long lastUsed = 0;

    public AbandonedTrace() {
        init(null);
    }

    /**
     * @param parent AbandonedTrace 父级对象
     */
    public AbandonedTrace(final AbandonedTrace parent) {
        init(parent);
    }

    /**
     * @param parent AbandonedTrace 父级对象
     */
    private void init(final AbandonedTrace parent) {
        if (parent != null) {
            parent.addTrace(this);
        }
    }

    /**
     * 获取最后一次使用此对象的时间, 以ms为单位.
     *
     * @return long time in ms
     */
    @Override
    public long getLastUsed() {
        return lastUsed;
    }

    /**
     * 设置最后一次使用此对象的时间, 以ms为单位.
     */
    protected void setLastUsed() {
        lastUsed = System.currentTimeMillis();
    }

    /**
     * 设置最后一次使用此对象的时间, 以ms为单位.
     *
     * @param time time in ms
     */
    protected void setLastUsed(final long time) {
        lastUsed = time;
    }

    /**
     * 将对象添加到要跟踪的对象列表中.
     *
     * @param trace 要添加的AbandonedTrace
     */
    protected void addTrace(final AbandonedTrace trace) {
        synchronized (this.traceList) {
            this.traceList.add(new WeakReference<>(trace));
        }
        setLastUsed();
    }

    /**
     * 清空此对象跟踪的对象列表.
     */
    protected void clearTrace() {
        synchronized(this.traceList) {
            this.traceList.clear();
        }
    }

    /**
     * 获取此对象正在跟踪的对象列表.
     *
     * @return List of objects
     */
    protected List<AbandonedTrace> getTrace() {
        final int size = traceList.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        final ArrayList<AbandonedTrace> result = new ArrayList<>(size);
        synchronized (this.traceList) {
            final Iterator<WeakReference<AbandonedTrace>> iter = traceList.iterator();
            while (iter.hasNext()) {
                final WeakReference<AbandonedTrace> ref = iter.next();
                if (ref.get() == null) {
                    // Clean-up since we are here anyway
                    iter.remove();
                } else {
                    result.add(ref.get());
                }
            }
        }
        return result;
    }

    /**
     * 删除此对象正在跟踪的子对象.
     *
     * @param trace 要删除的AbandonedTrace对象
     */
    protected void removeTrace(final AbandonedTrace trace) {
        synchronized(this.traceList) {
            final Iterator<WeakReference<AbandonedTrace>> iter = traceList.iterator();
            while (iter.hasNext()) {
                final WeakReference<AbandonedTrace> ref = iter.next();
                if (trace.equals(ref.get())) {
                    iter.remove();
                    break;
                } else if (ref.get() == null) {
                    // Clean-up since we are here anyway
                    iter.remove();
                }
            }
        }
    }
}
