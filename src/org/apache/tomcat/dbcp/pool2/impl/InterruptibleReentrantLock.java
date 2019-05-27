package org.apache.tomcat.dbcp.pool2.impl;

import java.util.Collection;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 创建此子类是为了公开等待的线程, 以便使用此锁的队列的池在关闭时可以中断它们. 此类仅供内部使用.
 * <p>
 * 此类旨在是线程安全的.
 */
class InterruptibleReentrantLock extends ReentrantLock {

    private static final long serialVersionUID = 1L;

    /**
     * @param fairness true 表示线程应该像在FIFO队列中等待一样获取竞争锁
     */
    public InterruptibleReentrantLock(final boolean fairness) {
        super(fairness);
    }

    /**
     * 中断在特定条件下等待的线程
     *
     * @param condition 线程等待的条件.
     */
    public void interruptWaiters(final Condition condition) {
        final Collection<Thread> threads = getWaitingThreads(condition);
        for (final Thread thread : threads) {
            thread.interrupt();
        }
    }
}
