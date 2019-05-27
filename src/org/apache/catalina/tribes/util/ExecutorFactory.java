package org.apache.catalina.tribes.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorFactory {
    protected static final StringManager sm = StringManager.getManager(ExecutorFactory.class);

    public static ExecutorService newThreadPool(int minThreads, int maxThreads, long maxIdleTime, TimeUnit unit) {
        TaskQueue taskqueue = new TaskQueue();
        ThreadPoolExecutor service = new TribesThreadPoolExecutor(minThreads, maxThreads, maxIdleTime, unit,taskqueue);
        taskqueue.setParent(service);
        return service;
    }

    public static ExecutorService newThreadPool(int minThreads, int maxThreads, long maxIdleTime, TimeUnit unit, ThreadFactory threadFactory) {
        TaskQueue taskqueue = new TaskQueue();
        ThreadPoolExecutor service = new TribesThreadPoolExecutor(minThreads, maxThreads, maxIdleTime, unit,taskqueue, threadFactory);
        taskqueue.setParent(service);
        return service;
    }

    // ---------------------------------------------- TribesThreadPoolExecutor Inner Class
    private static class TribesThreadPoolExecutor extends ThreadPoolExecutor {
        public TribesThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        }

        public TribesThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        public TribesThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        public TribesThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        public void execute(Runnable command) {
            try {
                super.execute(command);
            } catch (RejectedExecutionException rx) {
                if (super.getQueue() instanceof TaskQueue) {
                    TaskQueue queue = (TaskQueue)super.getQueue();
                    if (!queue.force(command)) {
                        throw new RejectedExecutionException(sm.getString("executorFactory.queue.full"));
                    }
                }
            }
        }
    }

     // ---------------------------------------------- TaskQueue Inner Class
    private static class TaskQueue extends LinkedBlockingQueue<Runnable> {
        private static final long serialVersionUID = 1L;

        ThreadPoolExecutor parent = null;

        public TaskQueue() {
            super();
        }

        public void setParent(ThreadPoolExecutor tp) {
            parent = tp;
        }

        public boolean force(Runnable o) {
            if ( parent.isShutdown() ) throw new RejectedExecutionException(sm.getString("executorFactory.not.running"));
            return super.offer(o); //强制项目进入队列, 如果任务被拒绝, 则使用
        }

        @Override
        public boolean offer(Runnable o) {
            // 不能做任何检查
            if (parent==null) return super.offer(o);
            // 当多于线程, 简单地对对象排队
            if (parent.getPoolSize() == parent.getMaximumPoolSize()) return super.offer(o);
            // 有空闲线程, 只需将其添加到队列中
            // 这是一个近似值, 所以它可以使用一些调整
            if (parent.getActiveCount()<(parent.getPoolSize())) return super.offer(o);
            // 如果线程小于最大值, 强制创建一个新线程
            if (parent.getPoolSize()<parent.getMaximumPoolSize()) return false;
            // 如果到这里, 需要添加到队列中
            return super.offer(o);
        }
    }
}
