package org.apache.tomcat.util.http;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public abstract class CookieProcessorBase implements CookieProcessor {

    private static final String COOKIE_DATE_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss z";

    protected static final ThreadLocal<DateFormat> COOKIE_DATE_FORMAT =
        new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            DateFormat df =
                new SimpleDateFormat(COOKIE_DATE_PATTERN, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            return df;
        }
    };

    protected static final String ANCIENT_DATE;

    static {
        ANCIENT_DATE = COOKIE_DATE_FORMAT.get().format(new Date(10000));
    }
}
