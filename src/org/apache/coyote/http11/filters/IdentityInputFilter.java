package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * 表示输入过滤器.
 */
public class IdentityInputFilter implements InputFilter, ApplicationBufferHandler {

    private static final StringManager sm = StringManager.getManager(
            IdentityInputFilter.class.getPackage().getName());


    // -------------------------------------------------------------- Constants


    protected static final String ENCODING_NAME = "identity";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer


    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(StandardCharsets.ISO_8859_1),
                0, ENCODING_NAME.length());
    }


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
    protected InputBuffer buffer;


    /**
     * 用于读取剩余字节的ByteBuffer.
     */
    protected ByteBuffer tempRead;


    private final int maxSwallowSize;


    public IdentityInputFilter(int maxSwallowSize) {
        this.maxSwallowSize = maxSwallowSize;
    }


    // ---------------------------------------------------- InputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    @Override
    public int doRead(ByteChunk chunk) throws IOException {

        int result = -1;

        if (contentLength >= 0) {
            if (remaining > 0) {
                int nRead = buffer.doRead(chunk);
                if (nRead > remaining) {
                    // 块比主体中保留的字节数长; 将块长度更改为剩余字节数
                    chunk.setBytes(chunk.getBytes(), chunk.getStart(),
                                   (int) remaining);
                    result = (int) remaining;
                } else {
                    result = nRead;
                }
                if (nRead > 0) {
                    remaining = remaining - nRead;
                }
            } else {
                // 没有要写入的字节 : 返回 -1 并清空缓冲区
                chunk.recycle();
                result = -1;
            }
        }

        return result;

    }

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {

        int result = -1;

        if (contentLength >= 0) {
            if (remaining > 0) {
                int nRead = buffer.doRead(handler);
                if (nRead > remaining) {
                    // 块比主体中保留的字节数长; 将块长度更改为剩余字节数
                    handler.getByteBuffer().limit(handler.getByteBuffer().position() + (int) remaining);
                    result = (int) remaining;
                } else {
                    result = nRead;
                }
                if (nRead > 0) {
                    remaining = remaining - nRead;
                }
            } else {
                // 没有要写入的字节 : 返回 -1 并清空缓冲区
                if (handler.getByteBuffer() != null) {
                    handler.getByteBuffer().position(0).limit(0);
                }
                result = -1;
            }
        }

        return result;

    }


    // ---------------------------------------------------- InputFilter Methods


    /**
     * 从请求中读取内容长度.
     */
    @Override
    public void setRequest(Request request) {
        contentLength = request.getContentLengthLong();
        remaining = contentLength;
    }


    @Override
    public long end() throws IOException {

        final boolean maxSwallowSizeExceeded = (maxSwallowSize > -1 && remaining > maxSwallowSize);
        long swallowed = 0;

        // 消耗额外的字节.
        while (remaining > 0) {

            int nread = buffer.doRead(this);
            tempRead = null;
            if (nread > 0 ) {
                swallowed += nread;
                remaining = remaining - nread;
                if (maxSwallowSizeExceeded && swallowed > maxSwallowSize) {
                    // Note: 不会过早失败，因此客户端有机会在连接关闭之前读取响应. See:
                    // http://httpd.apache.org/docs/2.0/misc/fin_wait_2.html#appendix
                    throw new IOException(sm.getString("inputFilter.maxSwallow"));
                }
            } else { // 错误处理得更高.
                remaining = 0;
            }
        }

        // 如果读取的字节太多, 返回数量.
        return -remaining;

    }


    /**
     * 缓冲区中仍然可用的字节数.
     */
    @Override
    public int available() {
        return 0;
    }


    /**
     * 在过滤器管道中设置下一个缓冲区.
     */
    @Override
    public void setBuffer(InputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        contentLength = -1;
        remaining = 0;
    }


    /**
     * 返回关联的编码的名称; 这里是 "identity".
     */
    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    @Override
    public boolean isFinished() {
        // 仅在定义内容长度且没有剩余数据时完成
        return contentLength > -1 && remaining <= 0;
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        tempRead = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return tempRead;
    }


    @Override
    public void expand(int size) {
        // no-op
    }
}
