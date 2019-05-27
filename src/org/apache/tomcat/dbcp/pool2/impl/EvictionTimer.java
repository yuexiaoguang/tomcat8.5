package org.apache.tomcat.dbcp.pool2.impl;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 为所有池提供共享空闲对象逐出定时器. 此类封装了标准{@link Timer}并跟踪使用它的池的数量.
 * 如果没有池正在使用定时器，则会取消它. 这可以防止线程继续运行，这在应用程序服务器环境中可能导致内存泄漏和阻止应用程序干净地关闭或重新加载.
 * <p>
 * 此类具有包范围，以防止其包含在池公共API中. 下面的类声明不应更改为public.
 * <p>
 * 此类旨在是线程安全的.
 */
class EvictionTimer {

    /** 定时器实例 */
    private static Timer _timer; //@GuardedBy("EvictionTimer.class")

    /** 静态使用计数跟踪器 */
    private static int _usageCount; //@GuardedBy("EvictionTimer.class")

    private EvictionTimer() {
        // Hide the default constructor
    }

    /**
     * 将指定的逐出任务添加到定时器.
     * 通过调用此方法添加的任务必须调用{@link #cancel(TimerTask)}来取消任务, 以防止应用程序服务器环境中的内存和线程泄漏.
     * 
     * @param task      要安排的任务
     * @param delay     执行任务之前的延迟（以毫秒为单位）
     * @param period    执行之间的间隔（以毫秒为单位）
     */
    static synchronized void schedule(final TimerTask task, final long delay, final long period) {
        if (null == _timer) {
            // 强制创建新的Timer线程，并将上下文类加载器设置为加载此库的类加载器
            final ClassLoader ccl = AccessController.doPrivileged(
                    new PrivilegedGetTccl());
            try {
                AccessController.doPrivileged(new PrivilegedSetTccl(
                        EvictionTimer.class.getClassLoader()));
                _timer = AccessController.doPrivileged(new PrivilegedNewEvictionTimer());
            } finally {
                AccessController.doPrivileged(new PrivilegedSetTccl(ccl));
            }
        }
        _usageCount++;
        _timer.schedule(task, delay, period);
    }

    /**
     * 从定时器中删除指定的逐出任务.
     * @param task      要安排的任务
     */
    static synchronized void cancel(final TimerTask task) {
        task.cancel();
        _usageCount--;
        if (_usageCount == 0) {
            _timer.cancel();
            _timer = null;
        }
    }

    /**
     * {@link PrivilegedAction}用于获取 ContextClassLoader
     */
    private static class PrivilegedGetTccl implements PrivilegedAction<ClassLoader> {

        /**
         * {@inheritDoc}
         */
        @Override
        public ClassLoader run() {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    /**
     * {@link PrivilegedAction} 用于设置 ContextClassLoader
     */
    private static class PrivilegedSetTccl implements PrivilegedAction<Void> {

        private final ClassLoader classLoader;

        /**
         * @param classLoader 要使用的ClassLoader
         */
        PrivilegedSetTccl(final ClassLoader cl) {
            this.classLoader = cl;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Void run() {
            Thread.currentThread().setContextClassLoader(classLoader);
            return null;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("PrivilegedSetTccl [classLoader=");
            builder.append(classLoader);
            builder.append("]");
            return builder.toString();
        }
    }

    /**
     * {@link PrivilegedAction} 用于创建 Timer.
     * 使用特权操作创建计时器, 意味着关联的线程不会继承当前的访问控制上下文. 在容器环境中，继承当前访问控制上下文可能会导致保留对线程上下文类加载器的引用，这将是内存泄漏.
     */
    private static class PrivilegedNewEvictionTimer implements PrivilegedAction<Timer> {

        /**
         * {@inheritDoc}
         */
        @Override
        public Timer run() {
            return new Timer("commons-pool-EvictionTimer", true);
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("EvictionTimer []");
        return builder.toString();
    }
}
