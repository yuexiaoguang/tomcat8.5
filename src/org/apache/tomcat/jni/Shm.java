package org.apache.tomcat.jni;

import java.nio.ByteBuffer;

/** Shm
 */
public class Shm {

    /**
     * 创建共享内存段并使其可访问.
     * <br>
     * 关于匿名vs的说明. 命名共享内存段:<br>
     *         并非所有平台都支持匿名共享内存段, 但在某些情况下, 它优于其他类型的共享内存实现.
     *         传递一个 NULL 'file' 参数到这个函数将导致子系统使用匿名共享内存段.
     *         如果没有这样的系统, 返回APR_ENOTIMPL.
     * <br>
     * 关于分配大小的说明:<br>
     *         在某些平台上，有必要在实际段中存储关于段的一些元信息. 为了向调用者提供所请求的大小，实现可能需要从子系统请求更大的段长度.
     *         在所有情况下, apr_shm_baseaddr_get() 函数将返回内存的第一个可用字节.
     * @param reqsize 期望的段大小.
     * @param filename 在需要它的平台上用于共享内存的文件.
     * @param pool 从中分配共享内存结构的池.
     * 
     * @return 创建的共享内存结构.
     * @throws Error 发生错误
     */
    public static native long create(long reqsize, String filename, long pool)
        throws Error;

    /**
     * 删除与文件名关联的共享内存段.
     * <br>
     * 仅在支持基于名称的共享内存段的平台上支持此功能, 并且将在没有此类支持的平台上返回APR_ENOTIMPL.
     * 
     * @param filename 与需要删除的共享内存段关联的文件名
     * @param pool 用于文件操作的池
     * @return 操作状态
     */
    public static native int remove(String filename, long pool);

    /**
     * 销毁共享内存段和关联的内存.
     * 
     * @param m 要销毁的共享内存段结构.
     * @return 操作状态
     */
    public static native int destroy(long m);

    /**
     * 附加到由另一个进程创建的共享内存段.
     * 
     * @param filename 用于创建原始段的文件. (这必须匹配原始文件名.)
     * @param pool 从中为此进程分配共享内存结构的池.
     * 
     * @return 创建的共享内存结构.
     * @throws Error 发生错误
     */
    public static native long attach(String filename, long pool)
        throws Error;

    /**
     * 从共享内存段中分离而不销毁它.
     * 
     * @param m 表示要分离的段的共享内存结构.
     * @return 操作状态
     */
    public static native int detach(long m);

    /**
     * 检索共享内存段的基址.
     * NOTE: 该地址仅在调用者地址空间内可用, 因为此API不保证其他附加进程将保持相同的地址映射.
     * 
     * @param m 从中检索基址的共享内存段.
     * @return 地址，由APR_ALIGN_DEFAULT对齐.
     */
    public static native long baseaddr(long m);

    /**
     * 检索共享内存段的长度, 以字节为单位.
     * @param m 检索段长度的共享内存段.
     * @return 段的长度
     */
    public static native long size(long m);

    /**
     * 检索共享内存段的新ByteBuffer基址.
     * NOTE: 该地址仅在调用者地址空间内可用, 因为此API不保证其他附加进程将保持相同的地址映射.
     * 
     * @param m 检索基址的共享内存段.
     * @return 地址，由APR_ALIGN_DEFAULT对齐.
     */
    public static native ByteBuffer buffer(long m);

}
