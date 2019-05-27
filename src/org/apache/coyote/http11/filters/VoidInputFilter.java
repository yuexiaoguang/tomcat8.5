package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.InputFilter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;

/**
 * 无返回输入过滤器, 尝试读取时返回 -1. 和 GET, HEAD, 或一个类似的请求一起使用.
 */
public class VoidInputFilter implements InputFilter {


    // -------------------------------------------------------------- Constants

    protected static final String ENCODING_NAME = "void";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(StandardCharsets.ISO_8859_1),
                0, ENCODING_NAME.length());
    }


    // ---------------------------------------------------- InputBuffer Methods

    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    @Override
    public int doRead(ByteChunk chunk) throws IOException {
        return -1;
    }

    @Override
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        return -1;
    }


    // ---------------------------------------------------- InputFilter Methods

    /**
     * 设置关联的请求.
     */
    @Override
    public void setRequest(Request request) {
        // NOOP: 请求没有被使用, 所以忽略它
    }


    /**
     * 在过滤器管道中设置下一个缓冲区.
     */
    @Override
    public void setBuffer(InputBuffer buffer) {
        // NOOP: No body to read
    }


    /**
     * 使过滤器准备好处理下一个请求.
     */
    @Override
    public void recycle() {
        // NOOP
    }


    /**
     * 返回关联的编码的名称; 这里是"void".
     */
    @Override
    public ByteChunk getEncodingName() {
        return ENCODING;
    }


    /**
     * 结束当前请求. 允许使用buffer.doWrite写入额外的字节, 在该方法的执行过程中.
     *
     * @return 应该返回 0, 除非过滤器做一些内容长度限制, 在这种情况下，是额外字节或丢失字节的数量, 表示错误.
     * Note: 建议额外的字节被过滤器忽略.
     */
    @Override
    public long end() throws IOException {
        return 0;
    }


    @Override
    public int available() {
        return 0;
    }


    @Override
    public boolean isFinished() {
        return true;
    }
}
