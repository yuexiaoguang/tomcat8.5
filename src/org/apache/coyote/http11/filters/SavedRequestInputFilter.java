package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;

/**
 * 负责重放请求主体的输入过滤器, 当FORM验证后恢复保存的请求时.
 */
public class SavedRequestInputFilter implements InputFilter {

    /**
     * 原始请求主体.
     */
    protected ByteChunk input = null;

    /**
     * @param input 保存的要重放的请求主体.
     */
    public SavedRequestInputFilter(ByteChunk input) {
        this.input = input;
    }

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    @Override
    public int doRead(ByteChunk chunk) throws IOException {
        if(input.getOffset()>= input.getEnd())
            return -1;

        int writeLength = 0;

        if (chunk.getLimit() > 0 && chunk.getLimit() < input.getLength()) {
            writeLength = chunk.getLimit();
        } else {
            writeLength = input.getLength();
        }

        input.substract(chunk.getBuffer(), 0, writeLength);
        chunk.setOffset(0);
        chunk.setEnd(writeLength);

        return writeLength;
    }

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        if(input.getOffset()>= input.getEnd())
            return -1;

        ByteBuffer byteBuffer = handler.getByteBuffer();
        byteBuffer.position(byteBuffer.limit()).limit(byteBuffer.capacity());
        input.substract(byteBuffer);

        return byteBuffer.remaining();
    }

    /**
     * 在请求上设置内容长度.
     */
    @Override
    public void setRequest(org.apache.coyote.Request request) {
        request.setContentLength(input.getLength());
    }

    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        input = null;
    }

    /**
     * 返回关联的编码的名称; 这里是 null.
     */
    @Override
    public ByteChunk getEncodingName() {
        return null;
    }

    /**
     * 在过滤器管道中设置下一个缓冲区 (没有作用).
     */
    @Override
    public void setBuffer(InputBuffer buffer) {
        // NOOP 由于此过滤器将提供请求主体
    }

    /**
     * 缓冲区中仍然可用的字节数.
     */
    @Override
    public int available() {
        return input.getLength();
    }

    /**
     * 结束当前请求 (没有作用).
     */
    @Override
    public long end() throws IOException {
        return 0;
    }

    @Override
    public boolean isFinished() {
        return input.getOffset() >= input.getEnd();
    }
}
