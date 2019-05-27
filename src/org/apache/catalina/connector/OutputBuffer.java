package org.apache.catalina.connector;

import java.io.IOException;
import java.io.Writer;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.C2BConverter;
import org.apache.tomcat.util.res.StringManager;

/**
 * Tomcat响应所使用的缓冲区. 这是Tomcat 3.3 OutputBuffer的扩展, 随着一些状态处理的删除 (在Coyote中主要是 Processor的责任).
 */
public class OutputBuffer extends Writer {

    private static final StringManager sm = StringManager.getManager(OutputBuffer.class);

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    /**
     * 编码器缓存.
     */
    private final Map<Charset, C2BConverter> encoders = new HashMap<>();


    // ----------------------------------------------------- Instance Variables

    /**
     * 字节缓冲区.
     */
    private ByteBuffer bb;


    /**
     * 块缓冲区.
     */
    private final CharBuffer cb;


    /**
     * 输出缓冲器的状态.
     */
    private boolean initial = true;


    /**
     * 写入字节数.
     */
    private long bytesWritten = 0;


    /**
     * 写入字符数.
     */
    private long charsWritten = 0;


    /**
     * 指示输出缓冲区是否关闭.
     */
    private volatile boolean closed = false;


    /**
     * 下一次操作刷新.
     */
    private boolean doFlush = false;


    /**
     * 使用的编码.
     */
    private String enc;


    /**
     * 当前字符到字节转换器.
     */
    protected C2BConverter conv;


    /**
     * 关联的Coyote 响应.
     */
    private Response coyoteResponse;


    /**
     * 暂停标志. 如果是true，所有输出字节都将被吞噬.
     */
    private volatile boolean suspended = false;


    // ----------------------------------------------------------- Constructors


    public OutputBuffer() {
        this(DEFAULT_BUFFER_SIZE);
    }


    /**
     * @param size 缓冲区大小
     */
    public OutputBuffer(int size) {

        bb = ByteBuffer.allocate(size);
        clear(bb);
        cb = CharBuffer.allocate(size);
        clear(cb);

    }


    // ------------------------------------------------------------- Properties


    /**
     * 关联的Coyote 响应.
     *
     * @param coyoteResponse Associated Coyote response
     */
    public void setResponse(Response coyoteResponse) {
        this.coyoteResponse = coyoteResponse;
    }


    /**
     * 响应输出暂停吗 ?
     *
     * @return suspended flag value
     */
    public boolean isSuspended() {
        return this.suspended;
    }


    /**
     * 设置响应输出暂停.
     *
     * @param suspended New suspended flag value
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }


    /**
     * 响应输出是否关闭?
     */
    public boolean isClosed() {
        return this.closed;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 回收输出缓冲区.
     */
    public void recycle() {

        initial = true;
        bytesWritten = 0;
        charsWritten = 0;

        clear(bb);
        clear(cb);
        closed = false;
        suspended = false;
        doFlush = false;

        if (conv != null) {
            conv.recycle();
            conv = null;
        }

        enc = null;
    }


    /**
     * 关闭输出缓冲区. 如果未提交响应，则试图计算响应大小.
     *
     * @throws IOException 底层的IOException
     */
    @Override
    public void close() throws IOException {

        if (closed) {
            return;
        }
        if (suspended) {
            return;
        }

        // 如果有字符, 当字节用于计算内容长度时，将它们全部刷新到字节缓冲区(如果一切都适合字节缓冲区).
        if (cb.remaining() > 0) {
            flushCharBuffer();
        }

        if ((!coyoteResponse.isCommitted()) && (coyoteResponse.getContentLengthLong() == -1)
                && !coyoteResponse.getRequest().method().equals("HEAD")) {
            // 如果这没有引起响应的提交, 最终内容长度可以计算出来. Only do this if this is not a HEAD
            // 只有在不是HEAD请求时才会做这些，因为HEAD请求没有请求主体写入, 在这里设置0的值将导致在响应上设置零的显式内容长度.
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.setContentLength(bb.remaining());
            }
        }

        if (coyoteResponse.getStatus() == HttpServletResponse.SC_SWITCHING_PROTOCOLS) {
            doFlush(true);
        } else {
            doFlush(false);
        }
        closed = true;

        // 该请求应该在响应关闭时完全读取. 输入 a) 是无意义的，而且 b) 会让AJP感到困惑(bug 50189)因此关闭输入缓冲区以防止它们发生.
        Request req = (Request) coyoteResponse.getRequest().getNote(CoyoteAdapter.ADAPTER_NOTES);
        req.inputBuffer.close();

        coyoteResponse.action(ActionCode.CLOSE, null);
    }


    /**
     * 刷新缓冲区中包含的字节或字符.
     *
     * @throws IOException 发生底层IOException
     */
    @Override
    public void flush() throws IOException {
        doFlush(true);
    }


    /**
     * 刷新缓冲区中包含的字节或字符.
     *
     * @param realFlush <code>true</code>如果这也会导致真正的网络刷新
     * 
     * @throws IOException 发生底层IOException
     */
    protected void doFlush(boolean realFlush) throws IOException {

        if (suspended) {
            return;
        }

        try {
            doFlush = true;
            if (initial) {
                coyoteResponse.sendHeaders();
                initial = false;
            }
            if (cb.remaining() > 0) {
                flushCharBuffer();
            }
            if (bb.remaining() > 0) {
                flushByteBuffer();
            }
        } finally {
            doFlush = false;
        }

        if (realFlush) {
            coyoteResponse.action(ActionCode.CLIENT_FLUSH, null);
            // 如果出现异常, 或者发生IOE, 用IOE通知Servlet
            if (coyoteResponse.isExceptionPresent()) {
                throw new ClientAbortException(coyoteResponse.getErrorException());
            }
        }

    }


    // ------------------------------------------------- Bytes Handling Methods

    /**
     * 将缓冲区数据发送到客户端输出, 检查响应状态并调用正确的拦截器.
     *
     * @param buf 将写入响应的ByteBuffer
     *
     * @throws IOException 发生底层IOException
     */
    public void realWriteBytes(ByteBuffer buf) throws IOException {

        if (closed) {
            return;
        }
        if (coyoteResponse == null) {
            return;
        }

        // 如果真的有东西要写入
        if (buf.remaining() > 0) {
            // real write to the adapter
            try {
                coyoteResponse.doWrite(buf);
            } catch (IOException e) {
                // 写入时IOException几乎总是由于远程客户端中止请求. 包装这个，以便通过错误分配器更好地处理它.
                throw new ClientAbortException(e);
            }
        }

    }


    public void write(byte b[], int off, int len) throws IOException {

        if (suspended) {
            return;
        }
        writeBytes(b, off, len);
    }


    public void write(ByteBuffer from) throws IOException {

        if (suspended) {
            return;
        }
        writeBytes(from);
    }


    private void writeBytes(byte b[], int off, int len) throws IOException {

        if (closed) {
            return;
        }

        append(b, off, len);
        bytesWritten += len;

        // 如果调用 flush(), 然后立即刷新剩余字节
        if (doFlush) {
            flushByteBuffer();
        }
    }


    private void writeBytes(ByteBuffer from) throws IOException {

        if (closed) {
            return;
        }

        append(from);
        bytesWritten += from.remaining();

        // 如果调用 flush(), 然后立即刷新剩余字节
        if (doFlush) {
            flushByteBuffer();
        }
    }


    public void writeByte(int b) throws IOException {

        if (suspended) {
            return;
        }

        if (isFull(bb)) {
            flushByteBuffer();
        }

        transfer((byte) b, bb);
        bytesWritten++;
    }


    // ------------------------------------------------- Chars Handling Methods


    /**
     * 将字符转换为字节, 然后将数据发送给客户端.
     *
     * @param from 要写入响应的字符缓冲区
     *
     * @throws IOException 发生底层IOException
     */
    public void realWriteChars(CharBuffer from) throws IOException {

        while (from.remaining() > 0) {
            conv.convert(from, bb);
            if (bb.remaining() == 0) {
                // 如果需要更多字符以产生任何输出，则跳出循环
                break;
            }
            if (from.remaining() > 0) {
                flushByteBuffer();
            }
        }

    }

    @Override
    public void write(int c) throws IOException {

        if (suspended) {
            return;
        }

        if (isFull(cb)) {
            flushCharBuffer();
        }

        transfer((char) c, cb);
        charsWritten++;
    }


    @Override
    public void write(char c[]) throws IOException {

        if (suspended) {
            return;
        }
        write(c, 0, c.length);
    }


    @Override
    public void write(char c[], int off, int len) throws IOException {

        if (suspended) {
            return;
        }
        append(c, off, len);
        charsWritten += len;
    }


    /**
     * 向缓冲区追加字符串
     */
    @Override
    public void write(String s, int off, int len) throws IOException {

        if (suspended) {
            return;
        }

        if (s == null) {
            throw new NullPointerException(sm.getString("outputBuffer.writeNull"));
        }

        int sOff = off;
        int sEnd = off + len;
        while (sOff < sEnd) {
            int n = transfer(s, sOff, sEnd - sOff, cb);
            sOff += n;
            if (isFull(cb)) {
                flushCharBuffer();
            }
        }
        charsWritten += len;
    }


    @Override
    public void write(String s) throws IOException {

        if (suspended) {
            return;
        }

        if (s == null) {
            s = "null";
        }
        write(s, 0, s.length());
    }


    /**
     * @param s     编码值
     *
     * @deprecated This method will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public void setEncoding(String s) {
        enc = s;
    }


    public void checkConverter() throws IOException {
        if (conv != null) {
            return;
        }

        Charset charset = null;

        if (coyoteResponse != null) {
            charset = coyoteResponse.getCharset();
        }

        if (charset == null) {
            if (enc == null) {
                charset = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
            } else {
                charset = getCharset(enc);
            }
        }

        conv = encoders.get(charset);

        if (conv == null) {
            conv = createConverter(charset);
            encoders.put(charset, conv);
        }
    }


    private static Charset getCharset(final String encoding) throws IOException {
        if (Globals.IS_SECURITY_ENABLED) {
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<Charset>() {
                    @Override
                    public Charset run() throws IOException {
                        return B2CConverter.getCharset(encoding);
                    }
                });
            } catch (PrivilegedActionException ex) {
                Exception e = ex.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException(ex);
                }
            }
        } else {
            return B2CConverter.getCharset(encoding);
        }
    }


    private static C2BConverter createConverter(final Charset charset) throws IOException {
        if (Globals.IS_SECURITY_ENABLED) {
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<C2BConverter>() {
                    @Override
                    public C2BConverter run() throws IOException {
                        return new C2BConverter(charset);
                    }
                });
            } catch (PrivilegedActionException ex) {
                Exception e = ex.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException(ex);
                }
            }
        } else {
            return new C2BConverter(charset);
        }
    }


    // --------------------  BufferedOutputStream compatibility

    public long getContentWritten() {
        return bytesWritten + charsWritten;
    }

    /**
     * 该缓冲区是否已全部使用?
     *
     * @return true 如果没有将字符或字节添加到缓冲区中，因为最后调用{@link #recycle()}
     */
    public boolean isNew() {
        return (bytesWritten == 0) && (charsWritten == 0);
    }


    public void setBufferSize(int size) {
        if (size > bb.capacity()) {
            bb = ByteBuffer.allocate(size);
            clear(bb);
        }
    }


    public void reset() {
        reset(false);
    }

    public void reset(boolean resetWriterStreamFlags) {
        clear(bb);
        clear(cb);
        bytesWritten = 0;
        charsWritten = 0;
        if (resetWriterStreamFlags) {
            if (conv != null) {
                conv.recycle();
            }
            conv = null;
            enc = null;
        }
        initial = true;
    }


    public int getBufferSize() {
        return bb.capacity();
    }


    /*
     * 所有的非阻塞写入状态信息都保持在响应中，因此对需要它的所有代码都可见/可访问.
     */
    public boolean isReady() {
        return coyoteResponse.isReady();
    }


    public void setWriteListener(WriteListener listener) {
        coyoteResponse.setWriteListener(listener);
    }


    public boolean isBlocking() {
        return coyoteResponse.getWriteListener() == null;
    }

    public void checkRegisterForWrite() {
        coyoteResponse.checkRegisterForWrite();
    }

    /**
     * 将数据添加到缓冲区.
     *
     * @param src Bytes array
     * @param off Offset
     * @param len Length
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void append(byte src[], int off, int len) throws IOException {
        if (bb.remaining() == 0) {
            appendByteArray(src, off, len);
        } else {
            int n = transfer(src, off, len, bb);
            len = len - n;
            off = off + n;
            if (isFull(bb)) {
                flushByteBuffer();
                appendByteArray(src, off, len);
            }
        }
    }

    /**
     * 将数据添加到缓冲区.
     * @param src Char array
     * @param off Offset
     * @param len Length
     * @throws IOException 将溢出数据写入输出通道失败
     */
    public void append(char src[], int off, int len) throws IOException {
        // 如果有极限，而且在下面
        if(len <= cb.capacity() - cb.limit()) {
            transfer(src, off, len, cb);
            return;
        }

        // 优化:
        // 如果 len-avail < length (也就是说，在填满缓冲区之后，剩下的部分将放在缓冲区中)
        // 将复制第一部分，刷新，然后复制第二部分 - 1 写入还有更多的空间. 仍然会有2 写入, 但是在第一个写入得更多.
        if(len + cb.limit() < 2 * cb.capacity()) {
            /* 如果请求长度超过输出缓冲器的大小, 刷新输出缓冲区，然后直接写入数据.
             * 无法避免2 写入, 但是可以在第二次写入更多
            */
            int n = transfer(src, off, len, cb);

            flushCharBuffer();

            transfer(src, off + n, len - n, cb);
        } else {
            // long write - 刷新缓冲区并直接从源写入其余部分
            flushCharBuffer();

            realWriteChars(CharBuffer.wrap(src, off, len));
        }
    }


    public void append(ByteBuffer from) throws IOException {
        if (bb.remaining() == 0) {
            appendByteBuffer(from);
        } else {
            transfer(from, bb);
            if (isFull(bb)) {
                flushByteBuffer();
                appendByteBuffer(from);
            }
        }
    }

    private void appendByteArray(byte src[], int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        int limit = bb.capacity();
        while (len >= limit) {
            realWriteBytes(ByteBuffer.wrap(src, off, limit));
            len = len - limit;
            off = off + limit;
        }

        if (len > 0) {
            transfer(src, off, len, bb);
        }
    }

    private void appendByteBuffer(ByteBuffer from) throws IOException {
        if (from.remaining() == 0) {
            return;
        }

        int limit = bb.capacity();
        int fromLimit = from.limit();
        while (from.remaining() >= limit) {
            from.limit(from.position() + limit);
            realWriteBytes(from.slice());
            from.position(from.limit());
            from.limit(fromLimit);
        }

        if (from.remaining() > 0) {
            transfer(from, bb);
        }
    }

    private void flushByteBuffer() throws IOException {
        realWriteBytes(bb.slice());
        clear(bb);
    }

    private void flushCharBuffer() throws IOException {
        realWriteChars(cb.slice());
        clear(cb);
    }

    private void transfer(byte b, ByteBuffer to) {
        toWriteMode(to);
        to.put(b);
        toReadMode(to);
    }

    private void transfer(char b, CharBuffer to) {
        toWriteMode(to);
        to.put(b);
        toReadMode(to);
    }

    private int transfer(byte[] buf, int off, int len, ByteBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(buf, off, max);
        }
        toReadMode(to);
        return max;
    }

    private int transfer(char[] buf, int off, int len, CharBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(buf, off, max);
        }
        toReadMode(to);
        return max;
    }

    private int transfer(String s, int off, int len, CharBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(s, off, off + max);
        }
        toReadMode(to);
        return max;
    }

    private void transfer(ByteBuffer from, ByteBuffer to) {
        toWriteMode(to);
        int max = Math.min(from.remaining(), to.remaining());
        if (max > 0) {
            int fromLimit = from.limit();
            from.limit(from.position() + max);
            to.put(from);
            from.limit(fromLimit);
        }
        toReadMode(to);
    }

    private void clear(Buffer buffer) {
        buffer.rewind().limit(0);
    }

    private boolean isFull(Buffer buffer) {
        return buffer.limit() == buffer.capacity();
    }

    private void toReadMode(Buffer buffer) {
        buffer.limit(buffer.position())
              .reset();
    }

    private void toWriteMode(Buffer buffer) {
        buffer.mark()
              .position(buffer.limit())
              .limit(buffer.capacity());
    }
}
