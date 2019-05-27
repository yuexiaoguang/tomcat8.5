package org.apache.coyote;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.tomcat.util.buf.ByteChunk;

/**
 * 输出缓冲区.
 *
 * 协议实现内部使用该类. 来自更高级别代码的所有写入应该通过 Response.doWrite().
 */
public interface OutputBuffer {

    /**
     * 将给定的数据写入响应. 调用者拥有块.
     *
     * @param chunk 要写入的数据
     *
     * @return 写入的字节数可能小于输入块中可用的字节数
     *
     * @throws IOException 发生底层的I/O错误
     *
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    public int doWrite(ByteChunk chunk) throws IOException;


    /**
     * 将给定的数据写入响应. 调用者拥有块.
     *
     * @param chunk 要写入的数据
     *
     * @return 写入的字节数可能小于输入块中可用的字节数
     *
     * @throws IOException 发生底层的I/O错误
     */
    public int doWrite(ByteBuffer chunk) throws IOException;


    /**
     * 写入底层socket的字节. 这包括块、压缩等的影响.
     *
     * @return 写入当前请求的字节
     */
    public long getBytesWritten();
}
