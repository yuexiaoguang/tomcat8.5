package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

/**
 * 启用多个 {@link WsWebSocketContainer}实例共享一个{@link AsynchronousChannelGroup}, 同时确保该组在不再需要时被销毁.
 */
public class AsyncChannelGroupUtil {

    private static final StringManager sm =
            StringManager.getManager(AsyncChannelGroupUtil.class);

    private static AsynchronousChannelGroup group = null;
    private static int usageCount = 0;
    private static final Object lock = new Object();


    private AsyncChannelGroupUtil() {
        // Hide the default constructor
    }


    public static AsynchronousChannelGroup register() {
        synchronized (lock) {
            if (usageCount == 0) {
                group = createAsynchronousChannelGroup();
            }
            usageCount++;
            return group;
        }
    }


    public static void unregister() {
        synchronized (lock) {
            usageCount--;
            if (usageCount == 0) {
                group.shutdown();
                group = null;
            }
        }
    }


    private static AsynchronousChannelGroup createAsynchronousChannelGroup() {
        // 需要用正确的线程上下文类加载器这样做，否则调用的第一个Web应用程序会引发泄露
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(
                    AsyncIOThreadFactory.class.getClassLoader());

            // 这些设置与默认AsynchronousChannelGroup设置相同
            int initialSize = Runtime.getRuntime().availableProcessors();
            ExecutorService executorService = new ThreadPoolExecutor(
                    0,
                    Integer.MAX_VALUE,
                    Long.MAX_VALUE, TimeUnit.MILLISECONDS,
                    new SynchronousQueue<Runnable>(),
                    new AsyncIOThreadFactory());

            try {
                return AsynchronousChannelGroup.withCachedThreadPool(
                        executorService, initialSize);
            } catch (IOException e) {
                // 没有好的理由.
                throw new IllegalStateException(sm.getString("asyncChannelGroup.createFail"));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }


    private static class AsyncIOThreadFactory implements ThreadFactory {

        static {
            // 加载 NewThreadPrivilegedAction, 因为 newThread() 将无法从一个 InnocuousThread调用.
            // See https://bz.apache.org/bugzilla/show_bug.cgi?id=57490
            NewThreadPrivilegedAction.load();
        }


        @Override
        public Thread newThread(final Runnable r) {
            // 在doPrivileged 块中创建新线程，以确保线程继承当前ProtectionDomain，这对于Java Applet来说是非常重要的.
            // See https://bz.apache.org/bugzilla/show_bug.cgi?id=57091
            return AccessController.doPrivileged(new NewThreadPrivilegedAction(r));
        }

        // 非匿名类，以便AsyncIOThreadFactory可以显式加载它
        private static class NewThreadPrivilegedAction implements PrivilegedAction<Thread> {

            private static AtomicInteger count = new AtomicInteger(0);

            private final Runnable r;

            public NewThreadPrivilegedAction(Runnable r) {
                this.r = r;
            }

            @Override
            public Thread run() {
                Thread t = new Thread(r);
                t.setName("WebSocketClient-AsyncIO-" + count.incrementAndGet());
                t.setContextClassLoader(this.getClass().getClassLoader());
                t.setDaemon(true);
                return t;
            }

            private static void load() {
                // NO-OP. 只提供一个钩子启用类加载
            }
        }
    }
}
