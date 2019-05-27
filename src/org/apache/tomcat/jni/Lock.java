package org.apache.tomcat.jni;

/** Lock
 */
public class Lock {

    /**
     * 枚举APR进程锁方法的潜在类型
     * <br><b>Warning :</b> 检查APR_HAS_foo_SERIALIZE定义以查看平台是否支持APR_LOCK_foo.  只有APR_LOCK_DEFAULT是可移植的.
     */

    public static final int APR_LOCK_FCNTL        = 0; /** fcntl() */
    public static final int APR_LOCK_FLOCK        = 1; /** flock() */
    public static final int APR_LOCK_SYSVSEM      = 2; /** System V Semaphores */
    public static final int APR_LOCK_PROC_PTHREAD = 3; /** POSIX pthread process-based locking */
    public static final int APR_LOCK_POSIXSEM     = 4; /** POSIX semaphore process-based locking */
    public static final int APR_LOCK_DEFAULT      = 5; /** 使用默认的进程锁 */

    /**
     * 创建并初始化可用于同步进程的互斥锁.
     * <br><b>Warning :</b> 检查APR_HAS_foo_SERIALIZE定义以查看平台是否支持APR_LOCK_foo.  只有APR_LOCK_DEFAULT是可移植的.
     * 
     * @param fname 锁机制需要使用的文件名. 这个参数必须提供. 锁代码本身将决定是否应该使用它.
     * @param mech 用于进程间锁的机制; 以下其中之一: 
     * <PRE>
     *            APR_LOCK_FCNTL
     *            APR_LOCK_FLOCK
     *            APR_LOCK_SYSVSEM
     *            APR_LOCK_POSIXSEM
     *            APR_LOCK_PROC_PTHREAD
     *            APR_LOCK_DEFAULT     选择平台的默认机制
     * </PRE>
     * @param pool 从中分配互斥锁的池.
     * @return 新创建的互斥锁.
     * @throws Error 发生错误
     */
    public static native long create(String fname, int mech, long pool)
        throws Error;

    /**
     * 在子进程中重新打开互斥锁.
     * 必须调用此函数才能保持可移植性, 即使底层锁机制不需要它.
     * 
     * @param fname 如果互斥锁机制需要文件名，则使用该文件名. 这个参数必须提供. 锁代码本身将决定是否应该使用它.
     * 				此文件名应与传递给apr_proc_mutex_create()的文件名相同.
     * @param pool 操作的池.
     * 
     * @return 新创建的互斥锁.
     * @throws Error 发生错误
     */
    public static native long childInit(String fname, long pool)
        throws Error;

    /**
     * 获取给定互斥锁的锁. 如果互斥锁已被锁, 当前线程将被置于睡眠状态, 直到锁可用.
     * 
     * @param mutex 获取锁的互斥锁.
     * @return 操作状态
     */
    public static native int lock(long mutex);

    /**
     * 尝试获取给定互斥锁的锁. 如果已经获得了互斥锁, 该调用立即返回APR_EBUSY.
     * Note: 由于可移植性的原因，使用APR_STATUS_IS_EBUSY宏来确定返回值是否为APR_EBUSY非常重要.
     * 
     * @param mutex 获取锁的互斥锁.
     * @return 操作状态
     */
    public static native int trylock(long mutex);

    /**
     * 释放给定互斥锁的锁.
     * 
     * @param mutex 释放给定互斥锁的锁.
     * @return 操作状态
     */
    public static native int unlock(long mutex);

    /**
     * 销毁互斥锁并释放与锁相关的内存.
     * 
     * @param mutex 要销毁的互斥锁.
     * @return 操作状态
     */
    public static native int destroy(long mutex);

    /**
     * 返回互斥锁的锁文件名; 如果互斥锁不使用锁文件，则为NULL
     * @param mutex 互斥锁的名称
     * @return 锁文件的名称
     */
    public static native String lockfile(long mutex);

    /**
     * 显示互斥锁的名称, 因为它与使用的实际方法有关. 这与Apache的AcceptMutex指令的有效选项相匹配.
     * 
     * @param mutex 互斥锁的名称
     * @return 互斥锁的名称
     */
    public static native String name(long mutex);

    /**
     * 显示默认互斥锁的名称: APR_LOCK_DEFAULT
     * @return 默认名称
     */
    public static native String defname();

}
