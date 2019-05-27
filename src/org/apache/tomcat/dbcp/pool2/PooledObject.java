package org.apache.tomcat.dbcp.pool2;

import java.io.PrintWriter;
import java.util.Deque;

/**
 * 定义用于跟踪池化的对象的附加信息（如状态）的包装器.
 * <p>
 * 此类的实现必须是线程安全的.
 *
 * @param <T> 池中对象的类型
 */
public interface PooledObject<T> extends Comparable<PooledObject<T>> {

    /**
     * 获取由此{@link PooledObject}实例封装的底层对象.
     */
    T getObject();

    /**
     * 获取创建此对象的时间(使用与{@link System#currentTimeMillis()}相同的基础).
     *
     * @return 封装的对象的创建时间
     */
    long getCreateTime();

    /**
     * 获取此对象上次处于活动状态的时间, 以毫秒为单位 (它可能仍然是活动的, 在这种情况下, 后续调用将返回增加的值).
     */
    long getActiveTimeMillis();

    /**
     * 获取此对象上次处于空闲状态的时间, 以毫秒为单位  (它可能仍然是空闲的, 在这种情况下, 后续调用将返回增加的值).
     */
    long getIdleTimeMillis();

    /**
     * 获取封装对象的最后借用的时间.
     */
    long getLastBorrowTime();

    /**
     * 获取上次返回封装对象的时间.
     */
    long getLastReturnTime();

    /**
     * 返回上次使用此对象的大概时间.
     * 如果池化的对象的类实现了{@link TrackedUse}, 则返回的是{@link TrackedUse#getLastUsed()}和{@link #getLastBorrowTime()}的最大值;
     * 否则此方法提供与{@link #getLastBorrowTime()}相同的值.
     */
    long getLastUsedTime();

    /**
     * 根据空闲时间对实例进行排序 - i.e. 自实例返回池后的时间长度. 由GKOP空闲对象 evictor 使用.
     *<p>
     * Note: 如果不同的对象具有相同的标识哈希码, 则此类具有与equals不一致的自然顺序.
     * <p>
     * {@inheritDoc}
     */
    @Override
    int compareTo(PooledObject<T> other);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    /**
     * 提供包装器的String形式以用于调试目的. 格式不固定, 可能随时更改.
     * <p>
     * {@inheritDoc}
     */
    @Override
    String toString();

    /**
     * 尝试将池化的对象置于{@link PooledObjectState#EVICTION}状态.
     *
     * @return <code>true</code> 如果对象变为{@link PooledObjectState#EVICTION}状态, 否则 <code>false</code>
     */
    boolean startEvictionTest();

    /**
     * 通知对象驱逐测试已经结束.
     *
     * @param idleQueue 应返回对象的空闲对象队列
     *
     * @return 当前不可用
     */
    boolean endEvictionTest(Deque<PooledObject<T>> idleQueue);

    /**
     * 分配对象.
     *
     * @return {@code true} 如果原始状态是 {@link PooledObjectState#IDLE IDLE}
     */
    boolean allocate();

    /**
     * 释放对象, 并设置它为 {@link PooledObjectState#IDLE IDLE}, 如果其当前状态是 {@link PooledObjectState#ALLOCATED ALLOCATED}.
     *
     * @return {@code true} 如果状态是 {@link PooledObjectState#ALLOCATED ALLOCATED}
     */
    boolean deallocate();

    /**
     * 设置状态为 {@link PooledObjectState#INVALID INVALID}
     */
    void invalidate();

    /**
     * 是否跟踪了废弃的对象?
     * 如果是 true, 实现将需要记录最后一个调用者的堆栈跟踪以借用该对象.
     *
     * @param   logAbandoned    废弃对象跟踪的配置
     */
    void setLogAbandoned(boolean logAbandoned);

    /**
     * 将当前堆栈跟踪记录为上次使用对象的时间.
     */
    void use();

    /**
     * 打印借用此池化的对象的代码的堆栈跟踪以及最后代码的堆栈跟踪.
     *
     * @param   writer  调试输出的目标
     */
    void printStackTrace(PrintWriter writer);

    /**
     * 返回此对象的状态.
     */
    PooledObjectState getState();

    /**
     * 将池化的对象标记为已废弃.
     */
    void markAbandoned();

    /**
     * 将对象标记为已返回到池中.
     */
    void markReturning();

    // TODO: Uncomment this for version 3 (can't add it to 2.x as it will break
    //       API compatibility)
    ///**
    // * 获取此对象借用的次数.
    // */
    //long getBorrowedCount();
}
