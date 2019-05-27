package org.apache.catalina;

import java.util.concurrent.TimeUnit;

public interface Executor extends java.util.concurrent.Executor, Lifecycle {

    public String getName();

    /**
     * 在将来的某个时候执行给定的命令.
     * 命令可以在新线程中执行, 在共享的线程中, 或在调用的线程中, 在<tt>Executor</tt> 实现类的指令中.
     * 如果没有可用的线程，它将被添加到工作队列中. 如果工作队列已满, 系统将等待指定的时间, 直到它抛出RejectedExecutionException
     *
     * @param command 线程
     * @param timeout 等待任务完成的时间长度
     * @param unit    表示超时的单位
     *
     * @throws java.util.concurrent.RejectedExecutionException 如果无法接受此任务执行——队列已满
     * @throws NullPointerException 如果命令或单位为null
     */
    void execute(Runnable command, long timeout, TimeUnit unit);
}