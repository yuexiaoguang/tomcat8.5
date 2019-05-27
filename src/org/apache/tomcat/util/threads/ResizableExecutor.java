package org.apache.tomcat.util.threads;

import java.util.concurrent.Executor;

public interface ResizableExecutor extends Executor {

    /**
     * 返回池中当前线程的数目.
     *
     * @return 线程的数目
     */
    public int getPoolSize();

    public int getMaxThreads();

    /**
     * 返回正在执行任务的线程的大致数目.
     *
     * @return 线程的数目
     */
    public int getActiveCount();

    public boolean resizePool(int corePoolSize, int maximumPoolSize);

    public boolean resizeQueue(int capacity);

}
