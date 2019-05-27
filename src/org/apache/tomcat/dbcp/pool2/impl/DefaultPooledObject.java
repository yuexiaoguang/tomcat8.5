package org.apache.tomcat.dbcp.pool2.impl;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;

import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectState;
import org.apache.tomcat.dbcp.pool2.TrackedUse;

/**
 * 此包装器用于跟踪池中对象的其他信息，例如状态.
 * <p>
 * 此类旨在是线程安全的.
 *
 * @param <T> 池中对象的类型
 */
public class DefaultPooledObject<T> implements PooledObject<T> {

    private final T object;
    private PooledObjectState state = PooledObjectState.IDLE; // @GuardedBy("this") to ensure transitions are valid
    private final long createTime = System.currentTimeMillis();
    private volatile long lastBorrowTime = createTime;
    private volatile long lastUseTime = createTime;
    private volatile long lastReturnTime = createTime;
    private volatile boolean logAbandoned = false;
    private volatile Exception borrowedBy = null;
    private volatile Exception usedBy = null;
    private volatile long borrowedCount = 0;

    /**
     * @param object 要封装的对象
     */
    public DefaultPooledObject(final T object) {
        this.object = object;
    }

    @Override
    public T getObject() {
        return object;
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public long getActiveTimeMillis() {
        // 复制以避免线程问题
        final long rTime = lastReturnTime;
        final long bTime = lastBorrowTime;

        if (rTime > bTime) {
            return rTime - bTime;
        }
        return System.currentTimeMillis() - bTime;
    }

    @Override
    public long getIdleTimeMillis() {
        final long elapsed = System.currentTimeMillis() - lastReturnTime;
     // elapsed 可能是负数, 如果:
     // - 另一个线程在计算窗口期间更新lastReturnTime
     // - System.currentTimeMillis() 不是单调的 (e.g. 系统时间被设置回来)
     return elapsed >= 0 ? elapsed : 0;
    }

    @Override
    public long getLastBorrowTime() {
        return lastBorrowTime;
    }

    @Override
    public long getLastReturnTime() {
        return lastReturnTime;
    }

    /**
     * 获取此对象借用的次数.
     */
    public long getBorrowedCount() {
        return borrowedCount;
    }

    /**
     * 返回上次使用此对象的大概时间.
     * 如果池中的对象实现了 {@link TrackedUse}, 返回的是{@link TrackedUse＃getLastUsed()}和{@link #getLastBorrowTime()}的最大值;
     * 否则此方法提供与{@link #getLastBorrowTime()}相同的值.
     */
    @Override
    public long getLastUsedTime() {
        if (object instanceof TrackedUse) {
            return Math.max(((TrackedUse) object).getLastUsed(), lastUseTime);
        }
        return lastUseTime;
    }

    @Override
    public int compareTo(final PooledObject<T> other) {
        final long lastActiveDiff = this.getLastReturnTime() - other.getLastReturnTime();
        if (lastActiveDiff == 0) {
            // 确保自然排序与equals大致一致，但如果不同的对象具有相同的标识哈希码，这将会中断.
            // see java.lang.Comparable Javadocs
            return System.identityHashCode(this) - System.identityHashCode(other);
        }
        // handle int overflow
        return (int)Math.min(Math.max(lastActiveDiff, Integer.MIN_VALUE), Integer.MAX_VALUE);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("Object: ");
        result.append(object.toString());
        result.append(", State: ");
        synchronized (this) {
            result.append(state.toString());
        }
        return result.toString();
        // TODO add other attributes
    }

    @Override
    public synchronized boolean startEvictionTest() {
        if (state == PooledObjectState.IDLE) {
            state = PooledObjectState.EVICTION;
            return true;
        }

        return false;
    }

    @Override
    public synchronized boolean endEvictionTest(
            final Deque<PooledObject<T>> idleQueue) {
        if (state == PooledObjectState.EVICTION) {
            state = PooledObjectState.IDLE;
            return true;
        } else if (state == PooledObjectState.EVICTION_RETURN_TO_HEAD) {
            state = PooledObjectState.IDLE;
            if (!idleQueue.offerFirst(this)) {
                // TODO - Should never happen
            }
        }

        return false;
    }

    /**
     * 分配对象.
     *
     * @return {@code true} 如果原始状态是 {@link PooledObjectState#IDLE IDLE}
     */
    @Override
    public synchronized boolean allocate() {
        if (state == PooledObjectState.IDLE) {
            state = PooledObjectState.ALLOCATED;
            lastBorrowTime = System.currentTimeMillis();
            lastUseTime = lastBorrowTime;
            borrowedCount++;
            if (logAbandoned) {
                borrowedBy = new AbandonedObjectCreatedException();
            }
            return true;
        } else if (state == PooledObjectState.EVICTION) {
            // TODO 无论如何分配并忽略驱逐测试
            state = PooledObjectState.EVICTION_RETURN_TO_HEAD;
            return false;
        }
        // TODO 如果验证而且 testOnBorrow == true, 然后预先分配
        return false;
    }

    /**
     * 释放对象并设置它 {@link PooledObjectState#IDLE IDLE}, 如果它当前是 {@link PooledObjectState#ALLOCATED ALLOCATED}.
     *
     * @return {@code true} 如果状态是 {@link PooledObjectState#ALLOCATED ALLOCATED}
     */
    @Override
    public synchronized boolean deallocate() {
        if (state == PooledObjectState.ALLOCATED ||
                state == PooledObjectState.RETURNING) {
            state = PooledObjectState.IDLE;
            lastReturnTime = System.currentTimeMillis();
            borrowedBy = null;
            return true;
        }

        return false;
    }

    /**
     * 设置状态为 {@link PooledObjectState#INVALID INVALID}
     */
    @Override
    public synchronized void invalidate() {
        state = PooledObjectState.INVALID;
    }

    @Override
    public void use() {
        lastUseTime = System.currentTimeMillis();
        usedBy = new Exception("The last code to use this object was:");
    }

    @Override
    public void printStackTrace(final PrintWriter writer) {
        boolean written = false;
        final Exception borrowedByCopy = this.borrowedBy;
        if (borrowedByCopy != null) {
            borrowedByCopy.printStackTrace(writer);
            written = true;
        }
        final Exception usedByCopy = this.usedBy;
        if (usedByCopy != null) {
            usedByCopy.printStackTrace(writer);
            written = true;
        }
        if (written) {
            writer.flush();
        }
    }

    /**
     * 返回此对象的状态.
     */
    @Override
    public synchronized PooledObjectState getState() {
        return state;
    }

    /**
     * 将池中的对象标记为已废弃.
     */
    @Override
    public synchronized void markAbandoned() {
        state = PooledObjectState.ABANDONED;
    }

    /**
     * 将对象标记为已返回到池中.
     */
    @Override
    public synchronized void markReturning() {
        state = PooledObjectState.RETURNING;
    }

    @Override
    public void setLogAbandoned(final boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }

    /**
     * 用于跟踪从池中获取对象的方式 (异常的堆栈跟踪将显示借用该对象的代码)和借用对象的时间.
     */
    static class AbandonedObjectCreatedException extends Exception {

        private static final long serialVersionUID = 7398692158058772916L;

        /** 日期格式 */
        //@GuardedBy("format")
        private static final SimpleDateFormat format = new SimpleDateFormat
            ("'Pooled object created' yyyy-MM-dd HH:mm:ss Z " +
             "'by the following code has not been returned to the pool:'");

        private final long _createdTime;

        public AbandonedObjectCreatedException() {
            super();
            _createdTime = System.currentTimeMillis();
        }

        // 除非实际使用日志消息，否则覆盖getMessage以避免创建对象和格式化日期.
        @Override
        public String getMessage() {
            String msg;
            synchronized(format) {
                msg = format.format(new Date(_createdTime));
            }
            return msg;
        }
    }
}
