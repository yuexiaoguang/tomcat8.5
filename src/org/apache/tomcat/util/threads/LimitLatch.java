package org.apache.tomcat.util.threads;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 允许锁存器被获取有限次数的共享锁存器. 此后，所有后续的获取锁存器的请求将被置于FIFO队列中，直到返回一个共享.
 */
public class LimitLatch {

    private static final Log log = LogFactory.getLog(LimitLatch.class);

    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        public Sync() {
        }

        @Override
        protected int tryAcquireShared(int ignored) {
            long newCount = count.incrementAndGet();
            if (!released && newCount > limit) {
                // Limit exceeded
                count.decrementAndGet();
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            count.decrementAndGet();
            return true;
        }
    }

    private final Sync sync;
    private final AtomicLong count;
    private volatile long limit;
    private volatile boolean released = false;

    /**
     * 用初始极限实例化一个LimitLatch对象.
     * 
     * @param limit - 此锁存器的并发获取的最大数目
     */
    public LimitLatch(long limit) {
        this.limit = limit;
        this.count = new AtomicLong(0);
        this.sync = new Sync();
    }

    /**
     * 返回锁存器的当前计数
     * 
     * @return 锁存器的当前计数
     */
    public long getCount() {
        return count.get();
    }

    /**
     * 获取当前限制.
     */
    public long getLimit() {
        return limit;
    }


    /**
     * 设置限制.
     * 如果限制减少，则可能会有一个周期，将获取大于其限制的共享的锁存器. 在这种情况下, 没有足够的共享锁存器将导致问题, 直到返回足够的锁存器将获取的共享锁存器数量降到限制之下.
     * 如果限制增加, 队列中当前的线程可能不会发出一个新可用的共享，直到对锁存器进行下一个请求.
     *
     * @param limit 限制值
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }


    /**
     * 如果锁存器可用，则获取共享锁存器; 或者如果没有共享锁存器当前可用，则等待.
     * 
     * @throws InterruptedException 如果当前线程中断
     */
    public void countUpOrAwait() throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Counting up["+Thread.currentThread().getName()+"] latch="+getCount());
        }
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 释放共享锁存器, 使它可用于另一个线程使用.
     * 
     * @return 前计数器值
     */
    public long countDown() {
        sync.releaseShared(0);
        long result = getCount();
        if (log.isDebugEnabled()) {
            log.debug("Counting down["+Thread.currentThread().getName()+"] latch="+result);
    }
        return result;
    }

    /**
     * 释放所有等待线程, 并导致 {@link #limit} 被忽略, 直到 {@link #reset()}被调用.
     * 
     * @return <code>true</code> 如果释放完成
     */
    public boolean releaseAll() {
        released = true;
        return sync.releaseShared(0);
    }

    /**
     * 重置锁存器并初始化共享获取计数器为零.
     */
    public void reset() {
        this.count.set(0);
        released = false;
    }

    /**
     * 返回<code>true</code>, 如果至少有一个线程等待获取共享锁, 否则返回<code>false</code>.
     * 
     * @return <code>true</code>如果线程正在等待
     */
    public boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 提供对等待获取此有限共享锁存器的线程列表的访问权限.
     * 
     * @return 线程集合
     */
    public Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }
}
