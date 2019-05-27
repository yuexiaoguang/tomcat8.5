package org.apache.tomcat.util.threads;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tomcat.util.res.StringManager;

/**
 * 和java.util.concurrent.ThreadPoolExecutor一样, 但实现了效率更高的 {@link #getSubmittedCount()} 方法,
 * 用于正确处理工作队列.
 * 如果没有指定RejectedExecutionHandler，则将配置默认处理程序，并且该处理程序将始终抛出RejectedExecutionException
 */
public class ThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager
            .getManager("org.apache.tomcat.util.threads.res");

    /**
     * 提交但尚未完成的任务数. 这包括队列中的任务, 和已经交给辅助线程但后者尚未开始执行的任务.
     * 大于等于 {@link #getActiveCount()}.
     */
    private final AtomicInteger submittedCount = new AtomicInteger(0);
    private final AtomicLong lastContextStoppedTime = new AtomicLong(0L);

    /**
     * 最近一段时间，以毫秒为单位，当一个线程决定杀死自己以避免潜在的内存泄漏. 有助于节制线程的再生率.
     */
    private final AtomicLong lastTimeThreadKilledItself = new AtomicLong(0L);

    /**
     * 更新2个线程之间的延迟时间，以毫秒为单位. 如果为负数, 不更新线程.
     */
    private long threadRenewalDelay = Constants.DEFAULT_THREAD_RENEWAL_DELAY;

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        prestartAllCoreThreads();
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        prestartAllCoreThreads();
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new RejectHandler());
        prestartAllCoreThreads();
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new RejectHandler());
        prestartAllCoreThreads();
    }

    public long getThreadRenewalDelay() {
        return threadRenewalDelay;
    }

    public void setThreadRenewalDelay(long threadRenewalDelay) {
        this.threadRenewalDelay = threadRenewalDelay;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        submittedCount.decrementAndGet();

        if (t == null) {
            stopCurrentThreadIfNeeded();
        }
    }

    /**
     * 如果当前线程在上下文停止之前的最后一次启动时启动, 抛出异常, 使当前线程停止.
     */
    protected void stopCurrentThreadIfNeeded() {
        if (currentThreadShouldBeStopped()) {
            long lastTime = lastTimeThreadKilledItself.longValue();
            if (lastTime + threadRenewalDelay < System.currentTimeMillis()) {
                if (lastTimeThreadKilledItself.compareAndSet(lastTime,
                        System.currentTimeMillis() + 1)) {
                    // OK, 现在是处理这个线程的时候了

                    final String msg = sm.getString(
                                    "threadPoolExecutor.threadStoppedToAvoidPotentialLeak",
                                    Thread.currentThread().getName());

                    throw new StopPooledThreadException(msg);
                }
            }
        }
    }

    protected boolean currentThreadShouldBeStopped() {
        if (threadRenewalDelay >= 0
            && Thread.currentThread() instanceof TaskThread) {
            TaskThread currentTaskThread = (TaskThread) Thread.currentThread();
            if (currentTaskThread.getCreationTime() <
                    this.lastContextStoppedTime.longValue()) {
                return true;
            }
        }
        return false;
    }

    public int getSubmittedCount() {
        return submittedCount.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Runnable command) {
        execute(command,0,TimeUnit.MILLISECONDS);
    }

    /**
     * 在将来某个时间执行给定的命令.
     * 该命令可以在新线程中执行, 在池中的线程, 或在调用的线程, 由<tt>Executor</tt> 实现决定.
     * 如果没有可用的线程, 它将被添加到工作队列中. 如果工作队列已满, 该系统将等待指定的时间, 如果队列仍然是满的, 那么它将引发一个RejectedExecutionException异常.
     *
     * @param command 可执行任务
     * @param timeout 完成任务的超时时间
     * @param unit 超时时间单位
     * 
     * @throws RejectedExecutionException 如果此任务不能被接受执行 - 队列已满
     * @throws NullPointerException 如果command或unit是 null
     */
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        submittedCount.incrementAndGet();
        try {
            super.execute(command);
        } catch (RejectedExecutionException rx) {
            if (super.getQueue() instanceof TaskQueue) {
                final TaskQueue queue = (TaskQueue)super.getQueue();
                try {
                    if (!queue.force(command, timeout, unit)) {
                        submittedCount.decrementAndGet();
                        throw new RejectedExecutionException("Queue capacity is full.");
                    }
                } catch (InterruptedException x) {
                    submittedCount.decrementAndGet();
                    throw new RejectedExecutionException(x);
                }
            } else {
                submittedCount.decrementAndGet();
                throw rx;
            }

        }
    }

    public void contextStopping() {
        this.lastContextStoppedTime.set(System.currentTimeMillis());

        // 保存当前池参数以在以后恢复它们
        int savedCorePoolSize = this.getCorePoolSize();
        TaskQueue taskQueue =
                getQueue() instanceof TaskQueue ? (TaskQueue) getQueue() : null;
        if (taskQueue != null) {
            // note by slaurent : 非常奇怪的 threadPoolExecutor.setCorePoolSize 检查 queue.remainingCapacity()==0.
            // 我不明白为什么, 但是为了获得唤醒空闲线程的预期效果, 暂时伪造了这个条件.
            taskQueue.setForcedRemainingCapacity(Integer.valueOf(0));
        }

        // setCorePoolSize(0) 唤醒空闲线程
        this.setCorePoolSize(0);

        // TaskQueue.take() 注意超时, 因此确信池的所有线程都在有限的时间内更新, 类似 (threadKeepAlive + longest request time)

        if (taskQueue != null) {
            // ok, 恢复队列和池的状态
            taskQueue.setForcedRemainingCapacity(null);
        }
        this.setCorePoolSize(savedCorePoolSize);
    }

    private static class RejectHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r,
                java.util.concurrent.ThreadPoolExecutor executor) {
            throw new RejectedExecutionException();
        }
    }
}
