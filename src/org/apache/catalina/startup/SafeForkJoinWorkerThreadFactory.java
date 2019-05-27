package org.apache.catalina.startup;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * 提供一个产生{@link ForkJoinWorkerThread}并且不会触发内存泄漏的 {@link ForkJoinWorkerThreadFactory}, 由于对Web应用程序类加载器的保留引用.
 * <p>
 * Note: 这个类必须在bootstrap类路径上可用，为了让它对{@link ForkJoinPool}可见.
 */
public class SafeForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        return new SafeForkJoinWorkerThread(pool);
    }


    private static class SafeForkJoinWorkerThread extends ForkJoinWorkerThread {

        protected SafeForkJoinWorkerThread(ForkJoinPool pool) {
            super(pool);
            setContextClassLoader(ForkJoinPool.class.getClassLoader());
        }
    }
}