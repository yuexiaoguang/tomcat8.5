package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.OutputFilter;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * 无返回输出过滤器, 将忽略写入的字节. 使用 204状态 (没有内容) 或一个 HEAD 请求.
 */
public class VoidOutputFilter implements OutputFilter {


    // --------------------------------------------------- OutputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    @Override
    public int doWrite(ByteChunk chunk) throws IOException {
        return chunk.getLength();
    }


    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {
        return chunk.remaining();
    }


    @Override
    public long getBytesWritten() {
        return 0;
    }


    // --------------------------------------------------- OutputFilter Methods


    /**
     * 一些过滤器需要来自响应的额外的参数. 所有必要的读取都可以在该方法中进行, 由于响应头处理完成后调用此方法.
     */
    @Override
    public void setResponse(Response response) {
        // NOOP: 在这个过滤器中不需要来自响应的参数
    }


    /**
     * 在过滤器管道中设置下一个缓冲区.
     */
    @Override
    public void setBuffer(OutputBuffer buffer) {
        // NO-OP
    }


    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        // NOOP: Nothing to recycle
    }


    /**
     * 结束当前请求. 允许使用buffer.doWrite写入额外的字节, 在该方法的执行过程中.
     *
     * @return 应该返回 0, 除非过滤器做一些内容长度限制, 在这种情况下，是额外字节或丢失字节的数量, 表示错误.
     * Note: 建议额外的字节被过滤器忽略.
     */
    @Override
    public long end()
        throws IOException {
        return 0;
    }

}
