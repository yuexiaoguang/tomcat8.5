package org.apache.tomcat.util.threads;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 作为任务队列专门设计为用线程池执行器运行.
 * 优化任务队列以正确地使用线程池执行器中的线程. 如果使用正常队列, 当存在空闲线程时，执行器将生成线程，并且不能将项强制到队列本身.
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = 1L;

    private volatile ThreadPoolExecutor parent = null;

    // 无需 volatile. 这是一个线程写和读的 (当停止上下文并触发监听器时)
    private Integer forcedRemainingCapacity = null;

    public TaskQueue() {
        super();
    }

    public TaskQueue(int capacity) {
        super(capacity);
    }

    public TaskQueue(Collection<? extends Runnable> c) {
        super(c);
    }

    public void setParent(ThreadPoolExecutor tp) {
        parent = tp;
    }

    public boolean force(Runnable o) {
        if ( parent==null || parent.isShutdown() ) throw new RejectedExecutionException("Executor not running, can't force a command into the queue");
        return super.offer(o); // 强制将项放到队列上, 如果任务被拒绝, 则使用
    }

    public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if ( parent==null || parent.isShutdown() ) throw new RejectedExecutionException("Executor not running, can't force a command into the queue");
        return super.offer(o,timeout,unit); // 强制将项放到队列上, 如果任务被拒绝, 则使用
    }

    @Override
    public boolean offer(Runnable o) {
      //不能做任何检查
        if (parent==null) return super.offer(o);
        // 线程都已占用, 将对象添加到队列上
        if (parent.getPoolSize() == parent.getMaximumPoolSize()) return super.offer(o);
        // 有空闲线程, 将对象添加到队列上
        if (parent.getSubmittedCount()<(parent.getPoolSize())) return super.offer(o);
        // 如果线程数比最大线程数少, 强制创建一个新线程
        if (parent.getPoolSize()<parent.getMaximumPoolSize()) return false;
        // 如果到达这里, 将对象添加到队列上
        return super.offer(o);
    }


    @Override
    public Runnable poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        Runnable runnable = super.poll(timeout, unit);
        if (runnable == null && parent != null) {
            // 获取超时了, 它提供了一个机会来停止当前线程，如果需要，以避免内存泄漏.
            parent.stopCurrentThreadIfNeeded();
        }
        return runnable;
    }

    @Override
    public Runnable take() throws InterruptedException {
        if (parent != null && parent.currentThreadShouldBeStopped()) {
            return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
            // yes, 可能返回 null (超时情况下), 通常 take() 不会发生
            // 但是ThreadPoolExecutor 实现允许这样
        }
        return super.take();
    }

    @Override
    public int remainingCapacity() {
        if (forcedRemainingCapacity != null) {
            // ThreadPoolExecutor.setCorePoolSize 检查 remainingCapacity==0 允许中断空闲线程
            // 我不明白为什么, 但是这个黑客允许遵守这个 "requirement"
            return forcedRemainingCapacity.intValue();
        }
        return super.remainingCapacity();
    }

    public void setForcedRemainingCapacity(Integer forcedRemainingCapacity) {
        this.forcedRemainingCapacity = forcedRemainingCapacity;
    }

}
