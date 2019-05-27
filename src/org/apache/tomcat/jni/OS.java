package org.apache.tomcat.jni;

/** OS
 */
public class OS {

    /* OS Enums */
    private static final int UNIX      = 1;
    private static final int NETWARE   = 2;
    private static final int WIN32     = 3;
    private static final int WIN64     = 4;
    private static final int LINUX     = 5;
    private static final int SOLARIS   = 6;
    private static final int BSD       = 7;
    private static final int MACOSX    = 8;

    public static final int LOG_EMERG  = 1;
    public static final int LOG_ERROR  = 2;
    public static final int LOG_NOTICE = 3;
    public static final int LOG_WARN   = 4;
    public static final int LOG_INFO   = 5;
    public static final int LOG_DEBUG  = 6;

    /**
     * 检查操作系统类型.
     * 
     * @param type 要测试的OS类型.
     */
    private static native boolean is(int type);

    public static final boolean IS_UNIX    = is(UNIX);
    public static final boolean IS_NETWARE = is(NETWARE);
    public static final boolean IS_WIN32   = is(WIN32);
    public static final boolean IS_WIN64   = is(WIN64);
    public static final boolean IS_LINUX   = is(LINUX);
    public static final boolean IS_SOLARIS = is(SOLARIS);
    public static final boolean IS_BSD     = is(BSD);
    public static final boolean IS_MACOSX  = is(MACOSX);

    /**
     * 获取系统默认字符集的名称.
     * 
     * @param pool 从中分配名称的池
     * @return 编码
     */
    public static native String defaultEncoding(long pool);

    /**
     * 获取当前区域设置字符集的名称.
     * 如果在此系统上无法检索当前区域设置的数据，则推迟到apr_os_default_encoding.
     * 
     * @param pool 从中分配名称的池
     * @return 编码
     */
    public static native String localeEncoding(long pool);

    /**
     * 生成随机字节.
     * 
     * @param buf 要填充随机字节的缓冲区
     * @param len 缓冲区长度，以字节为单位
     * 
     * @return 操作状态
     */
    public static native int random(byte [] buf, int len);

    /**
     * 收集系统信息.
     * <PRE>
     * 在退出时，inf数组将被填充:
     * inf[0]  - 总可用主内存大小
     * inf[1]  - 可用内存大小
     * inf[2]  - 总页面文件/交换空间大小
     * inf[3]  - 页面文件/交换空间仍然可用
     * inf[4]  - 共享内存量
     * inf[5]  - 缓冲区使用的内存
     * inf[6]  - 内存负载
     *
     * inf[7]  - 空闲时间，以微秒为单位
     * inf[8]  - 内核时间，以微秒为单位
     * inf[9]  - 用户时间，以微秒为单位
     *
     * inf[10] - 进程创建时间 (apr_time_t)
     * inf[11] - 进程内核时间，以微秒为单位
     * inf[12] - 进程用户时间, 以微秒为单位
     *
     * inf[13] - 当前工作集大小.
     * inf[14] - 峰值工作集大小.
     * inf[15] - 页面错误数.
     * </PRE>
     * @param inf 将填充系统信息的数组. 数组长度必须至少为16.
     * @return 操作状态
     */
    public static native int info(long [] inf);

    /**
     * 扩展环境变量.
     * @param str 要扩展的字符串
     * @return 带有替换环境变量的扩展字符串.
     */
    public static native String expand(String str);

    /**
     * 初始化系统日志记录.
     * @param domain 将添加到每条消息的字符串
     */
    public static native void sysloginit(String domain);

    /**
     * 记录消息.
     * 
     * @param level 记录消息严重性. See LOG_XXX enums.
     * @param message 要记录的消息
     */
    public static native void syslog(int level, String message);

}
