package org.apache.coyote;

import java.io.IOException;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;

/**
 * 此类仅用于协议实现中的内部使用. 所有从Tomcat (或适配器)的读取应该使用 Request.doRead() 完成.
 */
public interface InputBuffer {

    /**
     * 从输入流读入给定缓冲区.
     * IMPORTANT: 当前模型假设协议将“拥有”缓冲区并返回ByteChunk中指向它的指针 (i.e. the param will
     * have chunk.getBytes()==null before call, and the result after the call).
     *
     * @param chunk 读取数据的缓冲区.
     *
     * @return 已添加到缓冲区的字节数或 -1
     *
     * @throws IOException 如果读取输入流发生I/O错误
     *
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    public int doRead(ByteChunk chunk) throws IOException;

    /**
     * 从输入流读取到ApplicationBufferHandler提供的 ByteBuffer.
     * IMPORTANT: 当前模型假设协议将“拥有”ByteBuffer, 并返回指向它的指针.
     *
     * @param handler 提供读取数据的缓冲区的ApplicationBufferHandler
     *
     * @return 已添加到缓冲区的字节数或 -1
     *
     * @throws IOException 如果读取输入流发生I/O错误
     */
    public int doRead(ApplicationBufferHandler handler) throws IOException;
}
