package org.apache.juli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * 提供与默认日志格式相同的信息，但在单行上更容易grep日志. 唯一的例外是堆栈跟踪，它总是以空格开头，以便跳过它们.
 */
public class OneLineFormatter extends Formatter {

    private static final String ST_SEP = System.lineSeparator() + " ";
    private static final String UNKNOWN_THREAD_NAME = "Unknown thread with ID ";
    private static final Object threadMxBeanLock = new Object();
    private static volatile ThreadMXBean threadMxBean = null;
    private static final int THREAD_NAME_CACHE_SIZE = 10000;
    private static ThreadLocal<LinkedHashMap<Integer,String>> threadNameCache =
            new ThreadLocal<LinkedHashMap<Integer,String>>() {

        @Override
        protected LinkedHashMap<Integer,String> initialValue() {
            return new LinkedHashMap<Integer,String>() {

                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(
                        Entry<Integer, String> eldest) {
                    return (size() > THREAD_NAME_CACHE_SIZE);
                }
            };
        }
    };

    /* Timestamp format */
    private static final String DEFAULT_TIME_FORMAT = "dd-MMM-yyyy HH:mm:ss";

    /**
     * 全局日期格式缓存的大小
     */
    private static final int globalCacheSize = 30;

    /**
     * 线程本地日期格式缓存的大小
     */
    private static final int localCacheSize = 5;

    /**
     * 线程本地日期格式缓存.
     */
    private ThreadLocal<DateFormatCache> localDateCache;


    public OneLineFormatter() {
        String timeFormat = LogManager.getLogManager().getProperty(
                OneLineFormatter.class.getName() + ".timeFormat");
        if (timeFormat == null) {
            timeFormat = DEFAULT_TIME_FORMAT;
        }
        setTimeFormat(timeFormat);
    }


    /**
     * 指定用于日志消息中的时间戳的时间格式.
     *
     * @param timeFormat 格式使用{@link java.text.SimpleDateFormat} 语法
     */
    public void setTimeFormat(final String timeFormat) {
        final DateFormatCache globalDateCache =
                new DateFormatCache(globalCacheSize, timeFormat, null);
        localDateCache = new ThreadLocal<DateFormatCache>() {
            @Override
            protected DateFormatCache initialValue() {
                return new DateFormatCache(localCacheSize, timeFormat, globalDateCache);
            }
        };
    }


    /**
     * 获取当前用于日志消息中的时间戳的格式.
     *
     * @return 格式使用{@link java.text.SimpleDateFormat} 语法
     */
    public String getTimeFormat() {
        return localDateCache.get().getTimeFormat();
    }


    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        // Timestamp
        addTimestamp(sb, record.getMillis());

        // Severity
        sb.append(' ');
        sb.append(record.getLevel().getLocalizedName());

        // Thread
        sb.append(' ');
        sb.append('[');
        if (Thread.currentThread() instanceof AsyncFileHandler.LoggerThread) {
            // 如果使用异步的处理程序无法从当前线程获取线程名称.
            sb.append(getThreadName(record.getThreadID()));
        } else {
            sb.append(Thread.currentThread().getName());
        }
        sb.append(']');

        // Source
        sb.append(' ');
        sb.append(record.getSourceClassName());
        sb.append('.');
        sb.append(record.getSourceMethodName());

        // Message
        sb.append(' ');
        sb.append(formatMessage(record));

        // Stack trace
        if (record.getThrown() != null) {
            sb.append(ST_SEP);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.close();
            sb.append(sw.getBuffer());
        }

        // New line for next record
        sb.append(System.lineSeparator());

        return sb.toString();
    }

    protected void addTimestamp(StringBuilder buf, long timestamp) {
        buf.append(localDateCache.get().getFormat(timestamp));
        long frac = timestamp % 1000;
        buf.append('.');
        if (frac < 100) {
            if (frac < 10) {
                buf.append('0');
                buf.append('0');
            } else {
                buf.append('0');
            }
        }
        buf.append(frac);
    }


    /**
     * LogRecord有threadID但没有线程名称.
     * LogRecord使用int作为线程ID，但线程ID是long.
     * 如果是真正的线程ID > (Integer.MAXVALUE / 2) LogRecord使用它自己的ID来避免因溢出而发生冲突.
     */
    private static String getThreadName(int logRecordThreadId) {
        Map<Integer,String> cache = threadNameCache.get();
        String result = null;

        if (logRecordThreadId > (Integer.MAX_VALUE / 2)) {
            result = cache.get(Integer.valueOf(logRecordThreadId));
        }

        if (result != null) {
            return result;
        }

        if (logRecordThreadId > Integer.MAX_VALUE / 2) {
            result = UNKNOWN_THREAD_NAME + logRecordThreadId;
        } else {
            // 双重校验锁，因为threadMxBean是 volatile
            if (threadMxBean == null) {
                synchronized (threadMxBeanLock) {
                    if (threadMxBean == null) {
                        threadMxBean = ManagementFactory.getThreadMXBean();
                    }
                }
            }
            ThreadInfo threadInfo =
                    threadMxBean.getThreadInfo(logRecordThreadId);
            if (threadInfo == null) {
                return Long.toString(logRecordThreadId);
            }
            result = threadInfo.getThreadName();
        }

        cache.put(Integer.valueOf(logRecordThreadId), result);

        return result;
    }
}
