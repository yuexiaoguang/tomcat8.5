package org.apache.tomcat.jni;

/** Time
 */
public class Time {

    /** 每秒微秒数 */
    public static final long APR_USEC_PER_SEC  = 1000000L;
    /** 每微秒的毫秒数 */
    public static final long APR_MSEC_PER_USEC = 1000L;

    /**
     * @param t 时间
     * @return 秒
     */
    public static long sec(long t)
    {
        return t / APR_USEC_PER_SEC;
    }

    /**
     * @param t 时间
     * @return 毫秒
     */
    public static long msec(long t)
    {
        return t / APR_MSEC_PER_USEC;
    }

    /**
     * 从 00:00:00 January 1, 1970 UTC 以来的毫秒数
     * 
     * @return 当前时间
     */
    public static native long now();

    /**
     * 以有效的方式格式化RFC822格式的日期.
     * 
     * @param t 要转换的时间
     * @return 格式化的日期
     */
    public static native String rfc822(long t);

    /**
     * 以有效的方式格式化 ctime() 格式的日期.
     * 不像 ANSI/ISO C ctime(), apr_ctime() 在字符串结尾不包括一个 \n.
     * 
     * @param t 要转换的时间
     * @return 格式化的日期
     */
    public static native String ctime(long t);

    /**
     * 睡眠指定的微秒数.
     * <br><b>Warning :</b> 睡眠时间可能会超过指定的时间.
     * 
     * @param t 所需的睡眠时间.
     */
    public static native void sleep(long t);

}
