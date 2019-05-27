package org.apache.tomcat.jni;

import java.nio.ByteBuffer;

/** Buffer
 */
public class Buffer {

    /**
     * 从内存中分配新的ByteBuffer.
     * 
     * @param size 要分配的内存数量
     * @return 带有分配的内存的ByteBuffer
     */
    public static native ByteBuffer malloc(int size);

    /**
     * 从内存中分配新的ByteBuffer并将所有内存设置为0
     * 
     * @param num 元素数量.
     * @param size 每个元素的字节长度.
     * @return 带有分配的内存的ByteBuffer
     */
    public static native ByteBuffer calloc(int num, int size);

    /**
     * 从池中分配新的ByteBuffer
     * 
     * @param p 要分配的池
     * @param size 要分配的内存量
     * @return 带有分配的内存的ByteBuffer
     */
    public static native ByteBuffer palloc(long p, int size);

    /**
     * 从池中分配新的ByteBuffer并将所有内存设置为0
     * 
     * @param p 要分配的池
     * @param size 要分配的内存量
     * @return 带有分配的内存的ByteBuffer
     */
    public static native ByteBuffer pcalloc(long p, int size);

    /**
     * 从已分配的内存中分配新的ByteBuffer.
     * <br>必须通过调用Stdlib.alloc或Stdlib.calloc方法提供已分配的内存.
     * 
     * @param mem 要使用的内存
     * @param size 要使用的内存量
     * @return 带有分配的内存的ByteBuffer
     */
    public static native ByteBuffer create(long mem, int size);

    /**
     * 释放ByteBuffer使用的内存块.
     * <br><b>Warning :</b> 仅在通过调用Buffer.alloc或Buffer.calloc创建的ByteBuffers上调用此方法.
     * 
     * @param buf 要释放的以前分配的ByteBuffer.
     */
    public static native void free(ByteBuffer buf);

    /**
     * 返回ByteBuffer的内存地址.
     * 
     * @param buf 以前分配的ByteBuffer.
     * @return 内存地址
     */
    public static native long address(ByteBuffer buf);

    /**
     * 返回ByteBuffer的已分配的内存大小.
     * 
     * @param buf 以前分配的ByteBuffer.
     * @return 大小
     */
    public static native long size(ByteBuffer buf);

}
