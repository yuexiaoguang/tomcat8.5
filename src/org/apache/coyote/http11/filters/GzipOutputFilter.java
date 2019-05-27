package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.OutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Gzip 输出过滤器.
 */
public class GzipOutputFilter implements OutputFilter {


    protected static final Log log = LogFactory.getLog(GzipOutputFilter.class);


    // ----------------------------------------------------- Instance Variables


    /**
     * 管道中的下一个缓冲区.
     */
    protected OutputBuffer buffer;


    /**
     * 压缩输出流.
     */
    protected GZIPOutputStream compressionStream = null;


    /**
     * 假的内部输出流.
     */
    protected final OutputStream fakeOutputStream = new FakeOutputStream();


    // --------------------------------------------------- OutputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doWrite(ByteBuffer)}
     */
    @Deprecated
    @Override
    public int doWrite(ByteChunk chunk) throws IOException {
        if (compressionStream == null) {
            compressionStream = new GZIPOutputStream(fakeOutputStream, true);
        }
        compressionStream.write(chunk.getBytes(), chunk.getStart(),
                                chunk.getLength());
        return chunk.getLength();
    }


    @Override
    public int doWrite(ByteBuffer chunk) throws IOException {
        if (compressionStream == null) {
            compressionStream = new GZIPOutputStream(fakeOutputStream, true);
        }
        int len = chunk.remaining();
        if (chunk.hasArray()) {
            compressionStream.write(chunk.array(), chunk.arrayOffset() + chunk.position(), len);
        } else {
            byte[] bytes = new byte[len];
            chunk.put(bytes);
            compressionStream.write(bytes, 0, len);
        }
        return len;
    }


    @Override
    public long getBytesWritten() {
        return buffer.getBytesWritten();
    }


    // --------------------------------------------------- OutputFilter Methods

    /**
     * Added to allow flushing to happen for the gzip'ed outputstream
     */
    public void flush() {
        if (compressionStream != null) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Flushing the compression stream!");
                }
                compressionStream.flush();
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Ignored exception while flushing gzip filter", e);
                }
            }
        }
    }

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
        if (compressionStream == null) {
            compressionStream = new GZIPOutputStream(fakeOutputStream, true);
        }
        compressionStream.finish();
        compressionStream.close();
        return ((OutputFilter) buffer).end();
    }


    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        // Set compression stream to null
        compressionStream = null;
    }


    // ------------------------------------------- FakeOutputStream Inner Class


    protected class FakeOutputStream
        extends OutputStream {
        protected final ByteBuffer outputChunk = ByteBuffer.allocate(1);
        @Override
        public void write(int b)
            throws IOException {
            // 为了更好的性能不应该被使用, 但用于和Sun JDK 1.4.0兼容
            outputChunk.put(0, (byte) (b & 0xff));
            buffer.doWrite(outputChunk);
        }
        @Override
        public void write(byte[] b, int off, int len)
            throws IOException {
            buffer.doWrite(ByteBuffer.wrap(b, off, len));
        }
        @Override
        public void flush() throws IOException {/*NOOP*/}
        @Override
        public void close() throws IOException {/*NOOP*/}
    }


}
