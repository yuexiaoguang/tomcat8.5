package org.apache.tomcat.util.http;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于生成HTTP日期的实用程序类.
 */
public final class FastHttpDateFormat {


    // -------------------------------------------------------------- Variables


    private static final int CACHE_SIZE =
        Integer.parseInt(System.getProperty("org.apache.tomcat.util.http.FastHttpDateFormat.CACHE_SIZE", "1000"));


    /**
     * 生成HTTP标头时允许的唯一日期格式.
     */
    public static final String RFC1123_DATE =
            "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final SimpleDateFormat format =
            new SimpleDateFormat(RFC1123_DATE, Locale.US);


    private static final TimeZone gmtZone = TimeZone.getTimeZone("GMT");


    /**
     * GMT timezone - 所有HTTP日期都在GMT上
     */
    static {
        format.setTimeZone(gmtZone);
    }


    /**
     * 生成currentDate对象的Instant.
     */
    private static volatile long currentDateGenerated = 0L;


    /**
     * 当前格式化日期.
     */
    private static String currentDate = null;


    /**
     * Formatter cache.
     */
    private static final Map<Long, String> formatCache = new ConcurrentHashMap<>(CACHE_SIZE);


    /**
     * Parser cache.
     */
    private static final Map<String, Long> parseCache = new ConcurrentHashMap<>(CACHE_SIZE);


    // --------------------------------------------------------- Public Methods


    /**
     * 以HTTP格式获取当前日期.
     */
    public static final String getCurrentDate() {

        long now = System.currentTimeMillis();
        if ((now - currentDateGenerated) > 1000) {
            synchronized (format) {
                if ((now - currentDateGenerated) > 1000) {
                    currentDate = format.format(new Date(now));
                    currentDateGenerated = now;
                }
            }
        }
        return currentDate;

    }


    /**
     * 获取指定日期的HTTP格式.
     * 
     * @param value 日期
     * @param threadLocalformat 本地格式以避免同步
     * 
     * @return the HTTP date
     */
    public static final String formatDate
        (long value, DateFormat threadLocalformat) {

        Long longValue = Long.valueOf(value);
        String cachedDate = formatCache.get(longValue);
        if (cachedDate != null) {
            return cachedDate;
        }

        String newDate = null;
        Date dateValue = new Date(value);
        if (threadLocalformat != null) {
            newDate = threadLocalformat.format(dateValue);
            updateFormatCache(longValue, newDate);
        } else {
            synchronized (format) {
                newDate = format.format(dateValue);
            }
            updateFormatCache(longValue, newDate);
        }
        return newDate;
    }


    /**
     * 尝试将给定日期解析为HTTP日期.
     * 
     * @param value HTTP日期
     * @param threadLocalformats 本地格式以避免同步
     * 
     * @return the date as a long
     */
    public static final long parseDate(String value,
                                       DateFormat[] threadLocalformats) {

        Long cachedDate = parseCache.get(value);
        if (cachedDate != null) {
            return cachedDate.longValue();
        }

        Long date = null;
        if (threadLocalformats != null) {
            date = internalParseDate(value, threadLocalformats);
            updateParseCache(value, date);
        } else {
            throw new IllegalArgumentException();
        }
        if (date == null) {
            return (-1L);
        }

        return date.longValue();
    }


    /**
     * 使用给定的格式化程序解析日期.
     */
    private static final Long internalParseDate(String value, DateFormat[] formats) {
        Date date = null;
        for (int i = 0; (date == null) && (i < formats.length); i++) {
            try {
                date = formats[i].parse(value);
            } catch (ParseException e) {
                // Ignore
            }
        }
        if (date == null) {
            return null;
        }
        return Long.valueOf(date.getTime());
    }


    /**
     * Update cache.
     */
    private static void updateFormatCache(Long key, String value) {
        if (value == null) {
            return;
        }
        if (formatCache.size() > CACHE_SIZE) {
            formatCache.clear();
        }
        formatCache.put(key, value);
    }


    /**
     * Update cache.
     */
    private static void updateParseCache(String key, Long value) {
        if (value == null) {
            return;
        }
        if (parseCache.size() > CACHE_SIZE) {
            parseCache.clear();
        }
        parseCache.put(key, value);
    }
}
