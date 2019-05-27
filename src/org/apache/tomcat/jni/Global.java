package org.apache.tomcat.jni;

/** Global
 */
public class Global {

    /**
     * 创建并初始化可用于同步进程和线程的互斥锁.
     * Note: 如果只需要跨进程或跨线程互斥，则使用此API会产生相当大的开销. 有关更专业的锁定例程，请参阅apr_proc_mutex.h和apr_thread_mutex.h.
     * <br><b>Warning :</b> 检查APR_HAS_foo_SERIALIZE定义, 以查看平台是否支持APR_LOCK_foo. 只有APR_LOCK_DEFAULT是可移植的.
     * 
     * @param fname 锁定机制需要使用的文件名. 这个参数必须提供. 锁码本身将决定是否应该使用它.
     * @param mech 用于进程间锁定的机制; 其中之一
     * <PRE>
     *            APR_LOCK_FCNTL
     *            APR_LOCK_FLOCK
     *            APR_LOCK_SYSVSEM
     *            APR_LOCK_POSIXSEM
     *            APR_LOCK_PROC_PTHREAD
     *            APR_LOCK_DEFAULT     选择平台的默认机制
     * </PRE>
     * 
     * @param pool 从中分配互斥锁的池.
     * @return 新创建的互斥锁.
     * @throws Error 如果发生错误
     */
    public static native long create(String fname, int mech, long pool)
        throws Error;

    /**
     * 在子进程中重新打开互斥锁.
     * 
     * @param fname 如果互斥锁机制需要文件名，则使用该文件名. 这个参数必须提供. 互斥代码本身将确定是否应该使用它.
     * 				此文件名应与传递给apr_proc_mutex_create()的文件名相同.
     * @param pool 要操作的池.
     * 必须调用此函数才能保持可移植性, 即使底层锁机制不需要它.
     *         
     * @return 新打开的互斥锁.
     * @throws Error 如果发生错误
     */
    public static native long childInit(String fname, long pool)
        throws Error;

    /**
     * 获取给定互斥锁的锁. 如果互斥锁已被锁定, 当前线程将被置于睡眠状态, 直到锁可用.
     * 
     * @param mutex 获取锁的互斥锁.
     * @return 操作状态
     */
    public static native int lock(long mutex);

    /**
     * 尝试获取给定互斥锁的锁.
     * 如果已经获得了互斥锁, 该调用立即返回APR_EBUSY.
     * Note: 重要的是，APR_STATUS_IS_EBUSY宏用于确定返回值是否为APR_EBUSY，出于便携性的原因.
     * 
     * @param mutex 从中获取锁的互斥锁.
     * @return 操作状态
     */
    public static native int trylock(long mutex);

    /**
     * 释放给定互斥锁的锁.
     * 
     * @param mutex 从中释放锁的互斥锁.
     * 
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

}
