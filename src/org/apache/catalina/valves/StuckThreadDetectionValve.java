package org.apache.catalina.valves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 这个valve 允许检测需要很长时间处理的请求, 这可能表明正在处理的线程被卡住了.
 */
public class StuckThreadDetectionValve extends ValveBase {

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(StuckThreadDetectionValve.class);

    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * 检测到的卡住的线程的计数
     */
    private final AtomicInteger stuckCount = new AtomicInteger(0);

    /**
     * 已中断线程的计数
     */
    private AtomicLong interruptedThreadsCount = new AtomicLong();

    /**
     * 秒. 默认 600 (10 分钟).
     */
    private int threshold = 600;

    /**
     * 秒. 默认 -1 来禁用中断.
     */
    private int interruptThreadThreshold;

    /**
     * 保存的真实运行的线程(在invoke()中自动清空).
     * 那样, 线程可以被回收, 即使 Valve 仍然认为它们被卡住了 (由于很长的监视间隔)
     */
    private final Map<Long, MonitoredThread> activeThreads = new ConcurrentHashMap<>();

    private final Queue<CompletedStuckThread> completedStuckThreadsQueue =
            new ConcurrentLinkedQueue<>();

    /**
     * 指定检查卡住的线程的阈值 (秒).
     * 如果 &lt;=0, 禁用检测. 默认是 600 秒.
     *
     * @param threshold 阈值 (秒)
     */
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /**
     * @return 阈值 (秒)
     */
    public int getThreshold() {
        return threshold;
    }


    public int getInterruptThreadThreshold() {
        return interruptThreadThreshold;
    }

    /**
     * 指定被卡住之前, 线程中断的时间 (秒).
     * 如果 &lt;=0, 禁用中断. 默认是 -1.
     * 如果 &gt;=0, 值必须 &gt;= threshold.
     *
     * @param interruptThreadThreshold 中断的时间 (秒)
     */
    public void setInterruptThreadThreshold(int interruptThreadThreshold) {
        this.interruptThreadThreshold = interruptThreadThreshold;
    }

    /**
     * 需要启用异步支持.
     */
    public StuckThreadDetectionValve() {
        super(true);
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        if (log.isDebugEnabled()) {
            log.debug("Monitoring stuck threads with threshold = "
                    + threshold
                    + " sec");
        }
    }

    private void notifyStuckThreadDetected(MonitoredThread monitoredThread,
        long activeTime, int numStuckThreads) {
        if (log.isWarnEnabled()) {
            String msg = sm.getString(
                "stuckThreadDetectionValve.notifyStuckThreadDetected",
                monitoredThread.getThread().getName(),
                Long.valueOf(activeTime),
                monitoredThread.getStartTime(),
                Integer.valueOf(numStuckThreads),
                monitoredThread.getRequestUri(),
                Integer.valueOf(threshold),
                String.valueOf(monitoredThread.getThread().getId())
                );
            // msg += "\n" + getStackTraceAsString(trace);
            Throwable th = new Throwable();
            th.setStackTrace(monitoredThread.getThread().getStackTrace());
            log.warn(msg, th);
        }
    }

    private void notifyStuckThreadCompleted(CompletedStuckThread thread,
            int numStuckThreads) {
        if (log.isWarnEnabled()) {
            String msg = sm.getString(
                "stuckThreadDetectionValve.notifyStuckThreadCompleted",
                thread.getName(),
                Long.valueOf(thread.getTotalActiveTime()),
                Integer.valueOf(numStuckThreads),
                String.valueOf(thread.getId()));
            // 由于"stuck thread notification"是 warn, 这里也应该是 warn
            log.warn(msg);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invoke(Request request, Response response)
            throws IOException, ServletException {

        if (threshold <= 0) {
            // 如果不监测卡住的线程
            getNext().invoke(request, response);
            return;
        }

        // 保存 thread/runnable
        // 这里保存线程对象的引用, 不会阻止垃圾回收, 由于引用在finally子句中从Map中移除

        Long key = Long.valueOf(Thread.currentThread().getId());
        StringBuffer requestUrl = request.getRequestURL();
        if(request.getQueryString()!=null) {
            requestUrl.append("?");
            requestUrl.append(request.getQueryString());
        }
        MonitoredThread monitoredThread = new MonitoredThread(Thread.currentThread(),
            requestUrl.toString(), interruptThreadThreshold > 0);
        activeThreads.put(key, monitoredThread);

        try {
            getNext().invoke(request, response);
        } finally {
            activeThreads.remove(key);
            if (monitoredThread.markAsDone() == MonitoredThreadState.STUCK) {
                if(monitoredThread.wasInterrupted()) {
                    interruptedThreadsCount.incrementAndGet();
                }
                completedStuckThreadsQueue.add(
                        new CompletedStuckThread(monitoredThread.getThread(),
                            monitoredThread.getActiveTimeInMillis()));
            }
        }
    }

    @Override
    public void backgroundProcess() {
        super.backgroundProcess();

        long thresholdInMillis = threshold * 1000L;

        // 检查监视线程, 请注意，在检查时，请求可能已经完成
        for (MonitoredThread monitoredThread : activeThreads.values()) {
            long activeTime = monitoredThread.getActiveTimeInMillis();

            if (activeTime >= thresholdInMillis && monitoredThread.markAsStuckIfStillRunning()) {
                int numStuckThreads = stuckCount.incrementAndGet();
                notifyStuckThreadDetected(monitoredThread, activeTime, numStuckThreads);
            }
            if(interruptThreadThreshold > 0 && activeTime >= interruptThreadThreshold*1000L) {
                monitoredThread.interruptIfStuck(interruptThreadThreshold);
            }
        }
        // 检查以前报告的线程是否被卡住, 或者已经完成.
        for (CompletedStuckThread completedStuckThread = completedStuckThreadsQueue.poll();
            completedStuckThread != null; completedStuckThread = completedStuckThreadsQueue.poll()) {

            int numStuckThreads = stuckCount.decrementAndGet();
            notifyStuckThreadCompleted(completedStuckThread, numStuckThreads);
        }
    }

    public int getStuckThreadCount() {
        return stuckCount.get();
    }

    public long[] getStuckThreadIds() {
        List<Long> idList = new ArrayList<>();
        for (MonitoredThread monitoredThread : activeThreads.values()) {
            if (monitoredThread.isMarkedAsStuck()) {
                idList.add(Long.valueOf(monitoredThread.getThread().getId()));
            }
        }

        long[] result = new long[idList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = idList.get(i).longValue();
        }
        return result;
    }

    public String[] getStuckThreadNames() {
        List<String> nameList = new ArrayList<>();
        for (MonitoredThread monitoredThread : activeThreads.values()) {
            if (monitoredThread.isMarkedAsStuck()) {
                nameList.add(monitoredThread.getThread().getName());
            }
        }
        return nameList.toArray(new String[nameList.size()]);
    }

    public long getInterruptedThreadsCount() {
        return interruptedThreadsCount.get();
    }


    private static class MonitoredThread {

        /**
         * 从后台任务获取堆栈跟踪的线程的引用
         */
        private final Thread thread;
        private final String requestUri;
        private final long start;
        private final AtomicInteger state = new AtomicInteger(
            MonitoredThreadState.RUNNING.ordinal());
        /**
         * 同步线程与后台进程线程的信号量. 如果中断功能不激活, 则不使用它.
         */
        private final Semaphore interruptionSemaphore;
        /**
         * 设置为 true, 在线程中断之后. 不需要声明为 volatile, 因为它在获取信号量之后被访问.
         */
        private boolean interrupted;

        public MonitoredThread(Thread thread, String requestUri,
                boolean interruptible) {
            this.thread = thread;
            this.requestUri = requestUri;
            this.start = System.currentTimeMillis();
            if (interruptible) {
                interruptionSemaphore = new Semaphore(1);
            } else {
                interruptionSemaphore = null;
            }
        }

        public Thread getThread() {
            return this.thread;
        }

        public String getRequestUri() {
            return requestUri;
        }

        public long getActiveTimeInMillis() {
            return System.currentTimeMillis() - start;
        }

        public Date getStartTime() {
            return new Date(start);
        }

        public boolean markAsStuckIfStillRunning() {
            return this.state.compareAndSet(MonitoredThreadState.RUNNING.ordinal(),
                MonitoredThreadState.STUCK.ordinal());
        }

        public MonitoredThreadState markAsDone() {
            int val = this.state.getAndSet(MonitoredThreadState.DONE.ordinal());
            MonitoredThreadState threadState = MonitoredThreadState.values()[val];

            if (threadState == MonitoredThreadState.STUCK
                    && interruptionSemaphore != null) {
                try {
                    // 使用信号量与后台线程同步, 可能会中断当前线程.
                    // 否则, 当前线程可能在从这里退出后被中断, 也许已经服务于一个新的请求
                    this.interruptionSemaphore.acquire();
                } catch (InterruptedException e) {
                    log.debug(
                            "thread interrupted after the request is finished, ignoring",
                            e);
                }
                // 不需要释放信号量, 它将被回收
            }
            // 请求在被标记为卡住之前已经完成, 无需同步信号量
            return threadState;
        }

        boolean isMarkedAsStuck() {
            return this.state.get() == MonitoredThreadState.STUCK.ordinal();
        }

        public boolean interruptIfStuck(long interruptThreadThreshold) {
            if (!isMarkedAsStuck() || interruptionSemaphore == null
                    || !this.interruptionSemaphore.tryAcquire()) {
                // 如果已经获得信号量, 这意味着在我们中断请求线程之前中断了它
                return false;
            }
            try {
                if (log.isWarnEnabled()) {
                    String msg = sm.getString(
                        "stuckThreadDetectionValve.notifyStuckThreadInterrupted",
                        this.getThread().getName(),
                        Long.valueOf(getActiveTimeInMillis()),
                        this.getStartTime(), this.getRequestUri(),
                        Long.valueOf(interruptThreadThreshold),
                        String.valueOf(this.getThread().getId()));
                    Throwable th = new Throwable();
                    th.setStackTrace(this.getThread().getStackTrace());
                    log.warn(msg, th);
                }
                this.thread.interrupt();
            } finally {
                this.interrupted = true;
                this.interruptionSemaphore.release();
            }
            return true;
        }

        public boolean wasInterrupted() {
            return interrupted;
        }
    }

    private static class CompletedStuckThread {

        private final String threadName;
        private final long threadId;
        private final long totalActiveTime;

        public CompletedStuckThread(Thread thread, long totalActiveTime) {
            this.threadName = thread.getName();
            this.threadId = thread.getId();
            this.totalActiveTime = totalActiveTime;
        }

        public String getName() {
            return this.threadName;
        }

        public long getId() {
            return this.threadId;
        }

        public long getTotalActiveTime() {
            return this.totalActiveTime;
        }
    }

    private enum MonitoredThreadState {
        RUNNING, STUCK, DONE;
    }
}
