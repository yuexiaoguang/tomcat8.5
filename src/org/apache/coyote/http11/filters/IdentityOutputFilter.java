package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.OutputFilter;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * 标识输出过滤器.
 */
public class IdentityOutputFilter implements OutputFilter {


    // ----------------------------------------------------- Instance Variables


    /**
     * 内容长度.
     */
    protected long contentLength = -1;


    /**
     * 剩余字节.
     */
    protected long remaining = 0;


    /**
     * 管道中的下一个缓冲区.
     */
    protected OutputBuffer buffer;


    // --------------------------------------------------- OutputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    @Override
    public int doWrite(ByteChunk chunk) throws IOException {

        int result = -1;

        if (contentLength >= 0) {
            if (remaining > 0) {
                result = chunk.getLength();
                if (result > remaining) {
                    // 块比主体中保留的字节数长; 将块长度更改为剩余字节数
                    chunk.setBytes(chunk.getBytes(), chunk.getStart(),
                                   (int) remaining);
                    result = (int) remaining;
                    remaining = 0;
                } else {
                    remaining = remaining - result;
                }
                buffer.doWrite(chunk);
            } else {
                // 没有要写入的字节 : 返回 -1 并清空缓冲区
                chunk.recycle();
                result = -1;
            }
        } else {
            // 如果没有设置内容长度, 只写入字节
            buffer.doWrite(chunk);
            result = chunk.getLength();
        }

        return result;

    }


    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {

        int result = -1;

        if (contentLength >= 0) {
            if (remaining > 0) {
                result = chunk.remaining();
                if (result > remaining) {
                    // 块比主体中保留的字节数长; 将块长度更改为剩余字节数
                    chunk.limit(chunk.position() + (int) remaining);
                    result = (int) remaining;
                    remaining = 0;
                } else {
                    remaining = remaining - result;
                }
                buffer.doWrite(chunk);
            } else {
                // 没有要写入的字节 : 返回 -1 并清空缓冲区
                chunk.position(0);
                chunk.limit(0);
                result = -1;
            }
        } else {
            // 如果没有设置内容长度, 只写入字节
            result = chunk.remaining();
            buffer.doWrite(chunk);
            result -= chunk.remaining();
        }

        return result;

    }


    @Override
    public long getBytesWritten() {
        return buffer.getBytesWritten();
    }


    // --------------------------------------------------- OutputFilter Methods


    /**
     * 一些过滤器需要来自响应的额外的参数. 所有必要的读取都可以在该方法中进行, 由于响应头处理完成后调用此方法.
     */
    @Override
    public void setResponse(Response response) {
        contentLength = response.getContentLengthLong();
        remaining = contentLength;
    }


    /**
     * 在过滤器管道中设置下一个缓冲区 .
     */
    @Override
    public void setBuffer(OutputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * 结束当前请求. 允许使用buffer.doWrite写入额外的字节, 在该方法的执行过程中.
     */
    @Override
    public long end()
        throws IOException {

        if (remaining > 0)
            return remaining;
        return 0;

    }


    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        contentLength = -1;
        remaining = 0;
    }
}
