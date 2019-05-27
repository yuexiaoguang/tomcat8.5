package org.apache.tomcat.jni;

/** SSL Conf
 */
public final class SSLConf {

    /**
     * 创建一个新的SSL_CONF上下文.
     *
     * @param pool 要使用的池.
     * @param flags 要使用的SSL_CONF标志. 它可以是以下任意组合:
     * <PRE>
     * {@link SSL#SSL_CONF_FLAG_CMDLINE}
     * {@link SSL#SSL_CONF_FLAG_FILE}
     * {@link SSL#SSL_CONF_FLAG_CLIENT}
     * {@link SSL#SSL_CONF_FLAG_SERVER}
     * {@link SSL#SSL_CONF_FLAG_SHOW_ERRORS}
     * {@link SSL#SSL_CONF_FLAG_CERTIFICATE}
     * </PRE>
     *
     * @return 指向新创建的SSL_CONF上下文的指针的Java表示形式
     *
     * @throws Exception 如果无法创建SSL_CONF上下文
     */
    public static native long make(long pool, int flags) throws Exception;

    /**
     * 释放上下文使用的资源
     *
     * @param cctx 要释放的SSL_CONF 上下文.
     */
    public static native void free(long cctx);

    /**
     * 检查具有SSL_CONF上下文的命令.
     *
     * @param cctx 要使用的SSL_CONF上下文.
     * @param name 命令名称.
     * @param value 命令值.
     *
     * @return 基于{@code SSL_CONF_cmd_value_type}调用的检查结果. 未知类型将导致异常, 以及具有无效文件或目录名称的文件和目录类型.
     *
     * @throws Exception 如果检查失败.
     */
    public static native int check(long cctx, String name, String value) throws Exception;

    /**
     * 将SSL上下文分配给SSL_CONF上下文.
     * 以下对{@link #apply(long, String, String)}的所有调用都将应用于此SSL上下文.
     *
     * @param cctx 要使用的SSL_CONF上下文.
     * @param ctx 要分配给给定SSL_CONF上下文的SSL上下文.
     */
    public static native void assign(long cctx, long ctx);

    /**
     * 将命令应用于SSL_CONF上下文.
     *
     * @param cctx 要使用的SSL_CONF上下文.
     * @param name 命令名称.
     * @param value 命令值.
     *
     * @return 本地的{@code SSL_CONF_cmd}调用的结果
     *
     * @throws Exception 如果SSL_CONF上下文是{@code 0}
     */
    public static native int apply(long cctx, String name, String value) throws Exception;

    /**
     * 完成SSL_CONF上下文的命令.
     *
     * @param cctx 要使用的SSL_CONF上下文.
     *
     * @return 本地的{@code SSL_CONF_CTX_finish}调用的结果
     */
    public static native int finish(long cctx);

}
