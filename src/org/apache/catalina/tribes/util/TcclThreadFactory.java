package org.apache.catalina.tribes.util;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用线程上下文类加载器设置创建线程, 到加载这个工厂的类加载器.
 * 它打算在任务传递给执行者时使用, 当Web应用类加载器被设置为线程上下文类加载器, 例如异步会话复制.
 */
public class TcclThreadFactory implements ThreadFactory {

    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private static final boolean IS_SECURITY_ENABLED =
        (System.getSecurityManager() != null);

    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public TcclThreadFactory() {
        this("pool-" + poolNumber.getAndIncrement() + "-thread-");
    }

    public TcclThreadFactory(String namePrefix) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        final Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());

        if (IS_SECURITY_ENABLED) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    t.setContextClassLoader(this.getClass().getClassLoader());
                    return null;
                }
            });
        } else {
            t.setContextClassLoader(this.getClass().getClassLoader());
        }
        t.setDaemon(true);
        return t;
    }

}
