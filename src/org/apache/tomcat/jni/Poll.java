package org.apache.tomcat.jni;

/** Poll
 */
public class Poll {

    /**
     * 轮询返回值
     */
    /** 可以无阻塞地读 */
    public static final int APR_POLLIN   = 0x001;
    /** 优先数据可用 */
    public static final int APR_POLLPRI  = 0x002;
    /** 可以不阻塞地写 */
    public static final int APR_POLLOUT  = 0x004;
    /** 待定错误 */
    public static final int APR_POLLERR  = 0x010;
    /** 发生了挂断 */
    public static final int APR_POLLHUP  = 0x020;
    /** 描述符无效 */
    public static final int APR_POLLNVAL = 0x040;

    /**
     * Pollset Flags
     */
    /** 添加或删除描述符是线程安全的 */
    public static final int APR_POLLSET_THREADSAFE = 0x001;


    /** 
     * 在apr_pollfd_t中用于确定apr_descriptor是apr_datatype_e枚举的内容
     */
    public static final int APR_NO_DESC       = 0; /** nothing here */
    public static final int APR_POLL_SOCKET   = 1; /** 套接字 */
    public static final int APR_POLL_FILE     = 2; /** 文件 */
    public static final int APR_POLL_LASTDESC = 3; /** 列表中的最后一个 */

    /**
     * 设置一个pollset对象.
     * 如果flags等于APR_POLLSET_THREADSAFE, 然后创建一个pollset, 可以安全地从单独的线程对apr_pollset_add(), apr_pollset_remove()和apr_pollset_poll()进行并发调用.
     * 仅在某些平台上支持此功能; 在不支持APR_ENOTIMPL的平台上, apr_pollset_create（）调用将失败.
     * 
     * @param size 此pollset可容纳的最大描述符数
     * @param p 从中分配pollset的池
     * @param flags 用于修改pollset操作的可选标志.
     * @param ttl 特定套接字的最长生存时间.
     * 
     * @return  用于返回新创建的对象的指针
     * @throws Error Pollset创建失败
     */
    public static native long create(int size, long p, int flags, long ttl)
        throws Error;
    /**
     * 销毁一个pollset对象.
     * 
     * @param pollset 要销毁的pollset
     * @return 操作状态
     */
    public static native int destroy(long pollset);

    /**
     * 使用默认超时将套接字添加到pollset.
     * 
     * @param pollset 要添加套接字的pollset
     * @param sock 要添加的套接字
     * @param reqevents 请求事件
     * 
     * @return 操作状态
     */
    public static native int add(long pollset, long sock,
                                 int reqevents);

    /**
     * 将套接字添加到具有特定超时的pollset.
     * 
     * @param pollset 要添加套接字的pollset
     * @param sock 要添加的套接字
     * @param reqevents 请求事件
     * @param timeout 请求超时时间, 以微秒为单位 (-1 无限制)
     * 
     * @return 操作状态
     */
    public static native int addWithTimeout(long pollset, long sock,
                                            int reqevents, long timeout);

    /**
     * 从pollset中删除描述符.
     * 
     * @param pollset 要从中删除描述符的pollset
     * @param sock 要删除的套接字
     * 
     * @return 操作状态
     */
    public static native int remove(long pollset, long sock);

    /**
     * 阻塞pollset中描述符的活动.
     * 
     * @param pollset 要使用的pollset
     * @param timeout 超时时间, 以微秒为单位
     * @param descriptors 一组信号描述符 (输出参数). 描述符数组的大小必须是pollset的两倍.
     *        并填充如下:
     * <PRE>
     * descriptors[2n + 0] -&gt; 返回的事件
     * descriptors[2n + 1] -&gt; 套接字
     * </PRE>
     * @param remove 从pollset中删除已发信号的描述符
     * 
     * @return 信号描述符的数量 (输出参数), 或负数的APR错误代码.
     */
    public static native int poll(long pollset, long timeout,
                                  long [] descriptors, boolean remove);

    /**
     * 维护一个pollset中的描述符.
     * 
     * @param pollset 要使用的pollset
     * @param descriptors 一组信号描述符 (输出参数). 描述符数组的大小必须是pollset的两倍.
     *        并填充如下:
     * <PRE>
     * descriptors[n] -&gt; 套接字
     * </PRE>
     * 
     * @param remove 从pollset中删除已发信号的描述符
     * @return 信号描述符的数量 (输出参数), 或负数的APR错误代码.
     */
    public static native int maintain(long pollset, long [] descriptors,
                                      boolean remove);

    /**
     * 设置套接字的生存时间.
     * 
     * @param pollset 要使用的pollset
     * @param ttl 超时时间, 以微秒为单位
     */
    public static native void setTtl(long pollset, long ttl);

    /**
     * 获取套接字的生存时间.
     * 
     * @param pollset 要使用的pollset
     * @return 超时时间, 以微秒为单位
     */
    public static native long getTtl(long pollset);

    /**
     * 返回pollset中的所有描述符.
     * 
     * @param pollset 要使用的pollset
     * @param descriptors 一组信号描述符 (输出参数). 描述符数组的大小必须是pollset的两倍.
     *        并填充如下:
     * <PRE>
     * descriptors[2n + 0] -&gt; 返回的事件
     * descriptors[2n + 1] -&gt; 套接字
     * </PRE>
     * 
     * @return 信号描述符的数量 (输出参数), 或负数的APR错误代码.
     */
    public static native int pollset(long pollset, long [] descriptors);

    /**
     * 让 poll() 返回.
     *
     * @param   pollset 要使用的pollset
     * @return  负数的APR错误码
     */
    public static native int interrupt(long pollset);

    /**
     * 检查是否允许 interrupt().
     *
     * @param pollset 要使用的pollset
     * @return  <code>true</code> 如果允许{@link #interrupt(long)}, 否则<code>false</code>
     */
    public static native boolean wakeable(long pollset);
}
