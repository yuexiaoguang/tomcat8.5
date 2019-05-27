package org.apache.tomcat.jni;

/** Stdlib
 */
public class Stdlib {

    /**
     * 从普通内存中读取
     * 
     * @param dst 目标字节数组
     * @param src 源内存地址
     * @param sz 要复制的字节数
     * @return <code>true</code> 如果操作成功
     */
    public static native boolean memread(byte [] dst, long src, int sz);

    /**
     * 写入普通内存
     * 
     * @param dst 目标内存地址
     * @param src 源字节数组
     * @param sz 要复制的字节数.
     * @return <code>true</code> 如果操作成功
     */
    public static native boolean memwrite(long dst, byte [] src, int sz);

    /**
     * 将缓冲区设置为指定的字符
     * 
     * @param dst 目标内存地址
     * @param c 要设置的字符.
     * @param sz 字符数.
     * @return <code>true</code> 如果操作成功
     */
    public static native boolean memset(long dst, int c, int sz);

    /**
     * 分配内存块.
     * 
     * @param sz 要分配的字节数.
     * @return 指针
     */
    public static native long malloc(int sz);

    /**
     * 重新分配内存块.
     * 
     * @param mem 指向先前分配的内存块的指针.
     * @param sz 字节的新大小.
     * @return 指针
     */
    public static native long realloc(long mem, int sz);

    /**
     * 在内存中使用初始化为0的元素分配数组.
     * 
     * @param num 元素数量.
     * @param sz 每个元素的字节长度.
     * @return 指针
     */
    public static native long calloc(int num, int sz);

    /**
     * 释放内存块.
     * 
     * @param mem 要释放的以前分配的内存块.
     */
    public static native void free(long mem);

    /**
     * 获取当前进程pid.
     * 
     * @return 当前 pid ; 或错误时 &lt; 1.
     */
    public static native int getpid();

    /**
     * 获取当前进程父级 pid.
     * 
     * @return 父级 pid ; 或错误时 &lt; 1.
     */
    public static native int getppid();

}
