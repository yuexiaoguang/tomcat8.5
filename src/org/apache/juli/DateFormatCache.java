package org.apache.juli;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * <p>SimpleDateFormat格式化时间戳的缓存结构, 基于秒.</p>
 *
 * <p>不支持使用S进行毫秒格式化. 应该在返回第二个格式后添加毫秒信息.</p>
 *
 * <p>缓存由连续范围的秒组成. 范围的长度是可配置的. 它基于循环缓冲区实现. 新条目改变了范围.</p>
 *
 * <p>缓存不是线程安全的. 它可以通过线程本地实例在不同步的情况下使用, 或同步作为全局缓存.</p>
 *
 * <p>可以使用父缓存创建缓存以构建缓存层次结构. 访问父缓存是线程安全的.</p>
 */
public class DateFormatCache {

    private static final String msecPattern = "#";

    /* Timestamp format */
    private final String format;

    /* 缓存条目的数量 */
    private final int cacheSize;

    private final Cache cache;

    /**
     * 用一些虚拟字符替换毫秒格式字符'S'，以使得生成的格式化时间戳可缓存.
     * 消费者可能会选择用实际毫秒替换虚拟字符，因为它相对便宜.
     */
    private String tidyFormat(String format) {
        boolean escape = false;
        StringBuilder result = new StringBuilder();
        int len = format.length();
        char x;
        for (int i = 0; i < len; i++) {
            x = format.charAt(i);
            if (escape || x != 'S') {
                result.append(x);
            } else {
                result.append(msecPattern);
            }
            if (x == '\'') {
                escape = !escape;
            }
        }
        return result.toString();
    }

    public DateFormatCache(int size, String format, DateFormatCache parent) {
        cacheSize = size;
        this.format = tidyFormat(format);
        Cache parentCache = null;
        if (parent != null) {
            synchronized(parent) {
                parentCache = parent.cache;
            }
        }
        cache = new Cache(parentCache);
    }

    public String getFormat(long time) {
        return cache.getFormat(time);
    }

    public String getTimeFormat() {
        return format;
    }

    private class Cache {

        /* 在最近的调用中格式化的第二个 */
        private long previousSeconds = Long.MIN_VALUE;
        /* 在最近的调用中生成的格式化时间戳 */
        private String previousFormat = "";

        /* 第一秒包含在缓存中 */
        private long first = Long.MIN_VALUE;
        /* 最后一秒包含在缓存中 */
        private long last = Long.MIN_VALUE;
        /* 循环缓存中的“first”索引 */
        private int offset = 0;
        /* 为了能够调用 SimpleDateFormat.format(). */
        private final Date currentDate = new Date();

        private String cache[];
        private SimpleDateFormat formatter;

        private Cache parent = null;

        private Cache(Cache parent) {
            cache = new String[cacheSize];
            formatter = new SimpleDateFormat(format, Locale.US);
            formatter.setTimeZone(TimeZone.getDefault());
            this.parent = parent;
        }

        private String getFormat(long time) {

            long seconds = time / 1000;

            /* First step: 如果在上一次调用中看到过这个时间戳, 返回先前的值. */
            if (seconds == previousSeconds) {
                return previousFormat;
            }

            /* Second step: 尝试在缓存中找到 */
            previousSeconds = seconds;
            int index = (offset + (int)(seconds - first)) % cacheSize;
            if (index < 0) {
                index += cacheSize;
            }
            if (seconds >= first && seconds <= last) {
                if (cache[index] != null) {
                    /* 找到了，所以请记住下一个调用并返回.*/
                    previousFormat = cache[index];
                    return previousFormat;
                }

            /* Third step: 在缓存中找不到，调整缓存并添加项目 */
            } else if (seconds >= last + cacheSize || seconds <= first - cacheSize) {
                first = seconds;
                last = first + cacheSize - 1;
                index = 0;
                offset = 0;
                for (int i = 1; i < cacheSize; i++) {
                    cache[i] = null;
                }
            } else if (seconds > last) {
                for (int i = 1; i < seconds - last; i++) {
                    cache[(index + cacheSize - i) % cacheSize] = null;
                }
                first = seconds - (cacheSize - 1);
                last = seconds;
                offset = (index + 1) % cacheSize;
            } else if (seconds < first) {
                for (int i = 1; i < first - seconds; i++) {
                    cache[(index + i) % cacheSize] = null;
                }
                first = seconds;
                last = seconds + (cacheSize - 1);
                offset = index;
            }

            /* Last step:使用父缓存或本地缓存格式化新时间戳. */
            if (parent != null) {
                synchronized(parent) {
                    previousFormat = parent.getFormat(time);
                }
            } else {
                currentDate.setTime(time);
                previousFormat = formatter.format(currentDate);
            }
            cache[index] = previousFormat;
            return previousFormat;
        }
    }
}
