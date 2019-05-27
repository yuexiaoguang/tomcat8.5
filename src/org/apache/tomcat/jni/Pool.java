package org.apache.tomcat.jni;

import java.nio.ByteBuffer;

/** Pool
 */
public class Pool {

    /**
     * 创建一个新池.
     * 
     * @param parent 父级池. 如果是 0, 新池是根池. 如果它不为零, 则新池将继承其所有父池的属性, 除了apr_pool_t将是一个子池.
     * 
     * @return 刚刚创建的池.
    */
    public static native long create(long parent);

    /**
     * 清除池中的所有内存并运行所有清理. 这也会销毁所有子池.
     * 
     * @param pool 要清理的池
     * 这实际上并没有释放内存, 它只允许池重新使用此内存进行下一次分配.
     */
    public static native void clear(long pool);

    /**
     * 销毁池. 这与apr_pool_clear()采取类似的操作，然后释放所有内存. 会真正的释放内存
     * 
     * @param pool 要销毁的池
     */
    public static native void destroy(long pool);

    /**
     * 获取指定池的父池.
     * 
     * @param pool 用于检索父池的池.
     * @return 给定池的父级.
     */
    public static native long parentGet(long pool);

    /**
     * 确定池a是否是池b的祖先
     * 
     * @param a 要搜索的池
     * @param b 要搜索的池
     * @return True 如果a是b的祖先, NULL 视为所有池的祖先.
     */
    public static native boolean isAncestor(long a, long b);


    /*
     * Cleanup
     *
     * 清理按照注册的相反顺序执行. 那是: 后进先出. 清理功能可以安全地从正在清理的池中分配内存.
     * 它还可以安全地注册将在当前清理终止后直接运行LIFO的其他清理. 在调用创建子池的函数时，清理必须小心.
     * 清理期间创建的子池不会自动清理.  换句话说，清理工作是在他们自己清理之后.
     */

    /**
     * 注册清除或销毁池时要调用的函数.
     * 
     * @param pool 注册清理的池
     * @param o 清除或销毁池时要调用的对象
     *                      
     * @return 清理处理程序.
     */
    public static native long cleanupRegister(long pool, Object o);

    /**
     * 删除以前注册的清理功能.
     * 
     * @param pool 从中删除清理的池
     * @param data 要从清理中删除的清理处理程序
     */
    public static native void cleanupKill(long pool, long data);

    /**
     * 注册在池死亡时要杀死的进程.
     * 
     * @param a 用于定义进程生存期的池
     * @param proc 要注册的进程
     * @param how 如何杀死进程, 以下其中之一:
     * <PRE>
     * APR_KILL_NEVER         -- 进程永远不会发送任何信号
     * APR_KILL_ALWAYS        -- 进程在apr_pool_t清理时发送SIGKILL
     * APR_KILL_AFTER_TIMEOUT -- SIGTERM, 等3秒, SIGKILL
     * APR_JUST_WAIT          -- 一直等待这个进程完成
     * APR_KILL_ONLY_ONCE     -- 发送SIGTERM然后等待
     * </PRE>
     */
    public static native void noteSubprocess(long a, long proc, int how);

    /**
     * 从池中分配一块内存.
     * 
     * @param p 要分配的池
     * @param size 要分配的内存量
     * 
     * @return 带有分配内存的ByteBuffer
     */
    public static native ByteBuffer alloc(long p, int size);

    /**
     * 从池中分配一块内存并将所有内存设置为0.
     * 
     * @param p 要分配的池
     * @param size 要分配的内存量
     * @return 带有分配的内存的ByteBuffer
     */
    public static native ByteBuffer calloc(long p, int size);

    /*
     * User data management
     */

    /**
     * 设置与当前池关联的数据.
     * 
     * @param data 与池关联的用户数据.
     * @param key 用于关联的Key
     * @param pool 当前池
     * <br><b>Warning :</b>
     * 要附加到池的数据的生命周期应至少与其附加的池一样长. 附加到池的对象将被全局引用，直到清除池或使用空数据调用dataSet.
     * 
     * @return APR 状态码.
     */
     public static native int dataSet(long pool, String key, Object data);

    /**
     * 返回与当前池关联的数据.
     * 
     * @param key 要检索数据的Key
     * @param pool 当前池.
     * 
     * @return 数据
     */
     public static native Object dataGet(long pool, String key);

    /**
     * 运行所有child_cleanup, 这样就可以关闭任何不必要的文件, 因为即将执行一个新程序
     */
    public static native void cleanupForExec();

}
