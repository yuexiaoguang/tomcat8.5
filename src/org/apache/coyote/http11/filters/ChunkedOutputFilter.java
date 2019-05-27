package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.OutputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;

/**
 * 块输出过滤器.
 */
public class ChunkedOutputFilter implements OutputFilter {


    // -------------------------------------------------------------- Constants
    private static final byte[] END_CHUNK_BYTES = {(byte) '0', (byte) '\r', (byte) '\n',
            (byte) '\r', (byte) '\n'};


    // ------------------------------------------------------------ Constructor


    public ChunkedOutputFilter() {
        chunkHeader.put(8, (byte) '\r');
        chunkHeader.put(9, (byte) '\n');
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 管道中的下一个缓冲区.
     */
    protected OutputBuffer buffer;


    /**
     * 块 header.
     */
    protected final ByteBuffer chunkHeader = ByteBuffer.allocate(10);


    /**
     * 结尾块.
     */
    protected final ByteBuffer endChunk = ByteBuffer.wrap(END_CHUNK_BYTES);


    // ------------------------------------------------------------- Properties


    // --------------------------------------------------- OutputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    @Override
    public int doWrite(ByteChunk chunk) throws IOException {

        int result = chunk.getLength();

        if (result <= 0) {
            return 0;
        }

        int pos = calculateChunkHeader(result);

        chunkHeader.position(pos + 1).limit(chunkHeader.position() + 9 - pos);
        buffer.doWrite(chunkHeader);

        buffer.doWrite(chunk);

        chunkHeader.position(8).limit(10);
        buffer.doWrite(chunkHeader);

        return result;

    }


    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {

        int result = chunk.remaining();

        if (result <= 0) {
            return 0;
        }

        int pos = calculateChunkHeader(result);

        chunkHeader.position(pos + 1).limit(chunkHeader.position() + 9 - pos);
        buffer.doWrite(chunkHeader);

        buffer.doWrite(chunk);

        chunkHeader.position(8).limit(10);
        buffer.doWrite(chunkHeader);

        return result;

    }


    private int calculateChunkHeader(int len) {
        // 计算块 header
        int pos = 7;
        int current = len;
        while (current > 0) {
            int digit = current % 16;
            current = current / 16;
            chunkHeader.put(pos--, HexUtils.getHex(digit));
        }
        return pos;
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
        // NOOP: 在这个过滤器中不需要来自响应的参数
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

        // Write end chunk
        buffer.doWrite(endChunk);
        endChunk.position(0).limit(endChunk.capacity());

        return 0;

    }


    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        // NOOP: 无需回收
    }
}
