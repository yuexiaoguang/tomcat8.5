package org.apache.tomcat.jni;

/** Mmap
 */
public class Mmap {
    /** 用于读取的MMap */
    public static final int APR_MMAP_READ  = 1;
    /** 用于写入的MMap */
    public static final int APR_MMAP_WRITE = 2;


    /**
     * 从现有APR文件创建新的mmap文件.
     * 
     * @param file 要变成mmap的文件.
     * @param offset 文件中启动数据指针的偏移量.
     * @param size 文件的大小
     * @param flag 按位或以下其中之一:
     * <PRE>
     * APR_MMAP_READ       用于读取的MMap
     * APR_MMAP_WRITE      用于写入的MMap
     * </PRE>
     * @param pool 创建mmap时要使用的池.
     * 
     * @return 新创建的mmap文件.
     * @throws Error 创建内存映射时出错
     */
    public static native long create(long file, long offset, long size, int flag, long pool)
        throws Error;

    /**
     * 复制指定的MMAP.
     * 
     * @param mmap 要复制的mmap.
     * @param pool 用于new_mmap的池.
     * 
     * @return 复制的mmap'ed文件.
     * @throws Error 复制内存映射时出错
     */
    public static native long dup(long mmap, long pool)
        throws Error;

    /**
     * 删除mmap.
     * 
     * @param mm mmap的文件.
     * 
     * @return 操作状态
     */
    public static native int delete(long mm);

    /**
     * 将指针移动到mmap的文件中指定的偏移量.
     * 
     * @param mm mmap的文件.
     * @param offset 移动到的偏移量.
     * 
     * @return 指向偏移量的指针.
     * @throws Error 读取文件时出错
     */
    public static native long offset(long mm, long offset)
        throws Error;

}
