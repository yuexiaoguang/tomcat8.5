package org.apache.tomcat.websocket.server;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.websocket.BackgroundProcess;
import org.apache.tomcat.websocket.BackgroundProcessManager;

/**
 * 为异步Web套接字写入提供超时时间.
 * 在服务器端, 只访问 {@link javax.servlet.ServletOutputStream} 和 {@link javax.servlet.ServletInputStream}, 因此无法设置写入客户端的超时时间.
 */
public class WsWriteTimeout implements BackgroundProcess {

    private final Set<WsRemoteEndpointImplServer> endpoints =
            new ConcurrentSkipListSet<>(new EndpointComparator());
    private final AtomicInteger count = new AtomicInteger(0);
    private int backgroundProcessCount = 0;
    private volatile int processPeriod = 1;

    @Override
    public void backgroundProcess() {
        // 这种方法一秒调用一次.
        backgroundProcessCount ++;

        if (backgroundProcessCount >= processPeriod) {
            backgroundProcessCount = 0;

            long now = System.currentTimeMillis();
            Iterator<WsRemoteEndpointImplServer> iter = endpoints.iterator();
            while (iter.hasNext()) {
                WsRemoteEndpointImplServer endpoint = iter.next();
                if (endpoint.getTimeoutExpiry() < now) {
                    // 后台线程, 不是触发写入的线程, 所以不需要使用分派
                    endpoint.onTimeout(false);
                } else {
                    // 端点按超时过期排序，因此如果达到此点，则不需要检查剩余端点
                    break;
                }
            }
        }
    }


    @Override
    public void setProcessPeriod(int period) {
        this.processPeriod = period;
    }


    /**
     * {@inheritDoc}
     *
     * 默认值是1，这意味着异步写入超时每1秒处理一次.
     */
    @Override
    public int getProcessPeriod() {
        return processPeriod;
    }


    public void register(WsRemoteEndpointImplServer endpoint) {
        boolean result = endpoints.add(endpoint);
        if (result) {
            int newCount = count.incrementAndGet();
            if (newCount == 1) {
                BackgroundProcessManager.getInstance().register(this);
            }
        }
    }


    public void unregister(WsRemoteEndpointImplServer endpoint) {
        boolean result = endpoints.remove(endpoint);
        if (result) {
            int newCount = count.decrementAndGet();
            if (newCount == 0) {
                BackgroundProcessManager.getInstance().unregister(this);
            }
        }
    }


    /**
     * Note: 这个比较器强加了与等式不一致的排序
     */
    private static class EndpointComparator implements
            Comparator<WsRemoteEndpointImplServer> {

        @Override
        public int compare(WsRemoteEndpointImplServer o1,
                WsRemoteEndpointImplServer o2) {

            long t1 = o1.getTimeoutExpiry();
            long t2 = o2.getTimeoutExpiry();

            if (t1 < t2) {
                return -1;
            } else if (t1 == t2) {
                return 0;
            } else {
                return 1;
            }
        }
    }
}
