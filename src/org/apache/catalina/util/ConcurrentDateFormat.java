package org.apache.catalina.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 一个线程安全的{@link SimpleDateFormat}包装器, 不使用 ThreadLocal, 创建足够多的 SimpleDateFormat 对象来满足当前的需求.
 */
public class ConcurrentDateFormat {

    private final String format;
    private final Locale locale;
    private final TimeZone timezone;
    private final Queue<SimpleDateFormat> queue = new ConcurrentLinkedQueue<>();

    public static final String RFC1123_DATE = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    private static final ConcurrentDateFormat FORMAT_RFC1123;

    static {
        FORMAT_RFC1123 = new ConcurrentDateFormat(RFC1123_DATE, Locale.US, GMT);
    }

    public static String formatRfc1123(Date date) {
        return FORMAT_RFC1123.format(date);
    }

    public ConcurrentDateFormat(String format, Locale locale,
            TimeZone timezone) {
        this.format = format;
        this.locale = locale;
        this.timezone = timezone;
        SimpleDateFormat initial = createInstance();
        queue.add(initial);
    }

    public String format(Date date) {
        SimpleDateFormat sdf = queue.poll();
        if (sdf == null) {
            sdf = createInstance();
        }
        String result = sdf.format(date);
        queue.add(sdf);
        return result;
    }

    private SimpleDateFormat createInstance() {
        SimpleDateFormat sdf = new SimpleDateFormat(format, locale);
        sdf.setTimeZone(timezone);
        return sdf;
    }
}
