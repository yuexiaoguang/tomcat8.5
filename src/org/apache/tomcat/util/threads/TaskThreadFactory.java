package org.apache.tomcat.util.threads;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.util.security.PrivilegedSetTccl;

/**
 * 用于为执行器实现线程创建的简单任务线程工厂.
 */
public class TaskThreadFactory implements ThreadFactory {

    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    private final boolean daemon;
    private final int threadPriority;

    public TaskThreadFactory(String namePrefix, boolean daemon, int priority) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix;
        this.daemon = daemon;
        this.threadPriority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        TaskThread t = new TaskThread(group, r, namePrefix + threadNumber.getAndIncrement());
        t.setDaemon(daemon);
        t.setPriority(threadPriority);

        // 将新创建的线程的上下文类加载器设置为加载该工厂的类加载器. 这避免了保留对Web应用程序类加载器和类似的引用.
        if (Constants.IS_SECURITY_ENABLED) {
            PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                    t, getClass().getClassLoader());
            AccessController.doPrivileged(pa);
        } else {
            t.setContextClassLoader(getClass().getClassLoader());
        }

        return t;
    }
}
