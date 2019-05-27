package org.apache.tomcat.util.threads;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 一个线程实现，记录其创建的时间.
 */
public class TaskThread extends Thread {

    private static final Log log = LogFactory.getLog(TaskThread.class);
    private final long creationTime;

    public TaskThread(ThreadGroup group, Runnable target, String name) {
        super(group, new WrappingRunnable(target), name);
        this.creationTime = System.currentTimeMillis();
    }

    public TaskThread(ThreadGroup group, Runnable target, String name,
            long stackSize) {
        super(group, new WrappingRunnable(target), name, stackSize);
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * @return 创建该线程的时间（ms）
     */
    public final long getCreationTime() {
        return creationTime;
    }

    /**
     * 包装一个 {@link Runnable} 忽略所有的 {@link StopPooledThreadException}, 而不是让它继续和用于调试目的的触发一个潜在的中断.
     */
    private static class WrappingRunnable implements Runnable {
        private Runnable wrappedRunnable;
        WrappingRunnable(Runnable wrappedRunnable) {
            this.wrappedRunnable = wrappedRunnable;
        }
        @Override
        public void run() {
            try {
                wrappedRunnable.run();
            } catch(StopPooledThreadException exc) {
                //expected : 只是吞下了异常，以避免像Eclipse那样干扰调试器
                log.debug("Thread exiting on purpose", exc);
            }
        }
    }
}
