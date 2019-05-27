package org.apache.juli;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogRecord;
/**
 * {@link FileHandler}的实现, 使用一个日志条目队列.
 *
 * <p>配置属性是继承自 {@link FileHandler} 类.
 * 此类不会为日志记录配置添加自己的配置属性, 但依赖于以下系统属性:</p>
 *
 * <ul>
 *   <li><code>org.apache.juli.AsyncOverflowDropType</code>
 *    默认值: <code>1</code></li>
 *   <li><code>org.apache.juli.AsyncMaxRecordCount</code>
 *    默认值: <code>10000</code></li>
 *   <li><code>org.apache.juli.AsyncLoggerPollInterval</code>
 *    默认值: <code>1000</code></li>
 * </ul>
 */
public class AsyncFileHandler extends FileHandler {

    public static final int OVERFLOW_DROP_LAST    = 1;
    public static final int OVERFLOW_DROP_FIRST   = 2;
    public static final int OVERFLOW_DROP_FLUSH   = 3;
    public static final int OVERFLOW_DROP_CURRENT = 4;

    public static final int DEFAULT_OVERFLOW_DROP_TYPE = 1;
    public static final int DEFAULT_MAX_RECORDS        = 10000;
    public static final int DEFAULT_LOGGER_SLEEP_TIME  = 1000;

    public static final int OVERFLOW_DROP_TYPE = Integer.parseInt(
            System.getProperty("org.apache.juli.AsyncOverflowDropType",
                               Integer.toString(DEFAULT_OVERFLOW_DROP_TYPE)));
    public static final int MAX_RECORDS = Integer.parseInt(
            System.getProperty("org.apache.juli.AsyncMaxRecordCount",
                               Integer.toString(DEFAULT_MAX_RECORDS)));
    public static final int LOGGER_SLEEP_TIME = Integer.parseInt(
            System.getProperty("org.apache.juli.AsyncLoggerPollInterval",
                               Integer.toString(DEFAULT_LOGGER_SLEEP_TIME)));

    protected static final LinkedBlockingDeque<LogEntry> queue =
            new LinkedBlockingDeque<>(MAX_RECORDS);

    protected static final LoggerThread logger = new LoggerThread();

    static {
        logger.start();
    }

    protected volatile boolean closed = false;

    public AsyncFileHandler() {
        this(null, null, null, DEFAULT_MAX_DAYS);
    }

    public AsyncFileHandler(String directory, String prefix, String suffix) {
        this(directory, prefix, suffix, DEFAULT_MAX_DAYS);
    }

    public AsyncFileHandler(String directory, String prefix, String suffix, int maxDays) {
        super(directory, prefix, suffix, maxDays);
        open();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        super.close();
    }

    @Override
    protected void open() {
        if (!closed) {
            return;
        }
        closed = false;
        super.open();
    }


    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        // 填写源条目, 在用另一个类加载器将记录移交给另一个线程之前
        record.getSourceMethodName();
        LogEntry entry = new LogEntry(record, this);
        boolean added = false;
        try {
            while (!added && !queue.offer(entry)) {
                switch (OVERFLOW_DROP_TYPE) {
                    case OVERFLOW_DROP_LAST: {
                        //删除最后添加的元素
                        queue.pollLast();
                        break;
                    }
                    case OVERFLOW_DROP_FIRST: {
                        //删除队列中的第一个元素
                        queue.pollFirst();
                        break;
                    }
                    case OVERFLOW_DROP_FLUSH: {
                        added = queue.offer(entry, 1000, TimeUnit.MILLISECONDS);
                        break;
                    }
                    case OVERFLOW_DROP_CURRENT: {
                        added = true;
                        break;
                    }
                }
            }
        } catch (InterruptedException x) {
            // 允许线程被中断并退出发布操作. 无需采取进一步行动.
        }

    }

    protected void publishInternal(LogRecord record) {
        super.publish(record);
    }

    protected static class LoggerThread extends Thread {
        protected final boolean run = true;
        public LoggerThread() {
            this.setDaemon(true);
            this.setName("AsyncFileHandlerWriter-" + System.identityHashCode(this));
        }

        @Override
        public void run() {
            while (run) {
                try {
                    LogEntry entry = queue.poll(LOGGER_SLEEP_TIME, TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        entry.flush();
                    }
                } catch (InterruptedException x) {
                    // 忽略中断线程的尝试.
                } catch (Exception x) {
                    x.printStackTrace();
                }
            }
        }
    }

    protected static class LogEntry {
        private final LogRecord record;
        private final AsyncFileHandler handler;
        public LogEntry(LogRecord record, AsyncFileHandler handler) {
            super();
            this.record = record;
            this.handler = handler;
        }

        public boolean flush() {
            if (handler.closed) {
                return false;
            } else {
                handler.publishInternal(record);
                return true;
            }
        }
    }
}
