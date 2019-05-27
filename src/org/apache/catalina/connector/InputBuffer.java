package org.apache.catalina.connector;

import java.io.IOException;
import java.io.Reader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ReadListener;

import org.apache.catalina.security.SecurityUtil;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * Tomcat请求所使用的缓冲区. 这是Tomcat 3.3 OutputBuffer派生, 适合处理输入而不是输出.
 * 这允许完全回收外观对象(ServletInputStream 和 BufferedReader).
 */
public class InputBuffer extends Reader
    implements ByteChunk.ByteInputChannel, ApplicationBufferHandler {

    protected static final StringManager sm = StringManager.getManager(InputBuffer.class);

    private static final Log log = LogFactory.getLog(InputBuffer.class);

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    // 缓冲区可用于 byte[] 和 char[] 读取
    // (需要支持 ServletInputStream 和 BufferedReader )
    public final int INITIAL_STATE = 0;
    public final int CHAR_STATE = 1;
    public final int BYTE_STATE = 2;


    /**
     * Encoder cache.
     */
    private static final ConcurrentMap<Charset, SynchronizedStack<B2CConverter>> encoders = new ConcurrentHashMap<>();

    // ----------------------------------------------------- Instance Variables

    /**
     * 字节缓冲区.
     */
    private ByteBuffer bb;


    /**
     * 块缓冲区.
     */
    private CharBuffer cb;


    /**
     * 输出缓冲区状态.
     */
    private int state = 0;


    /**
     * 指示输入缓冲区是否关闭.
     */
    private boolean closed = false;


    /**
     * 使用的编码.
     */
    private String enc;


    /**
     * 当前字节到char转换器.
     */
    protected B2CConverter conv;


    /**
     * 关联的Coyote 请求.
     */
    private Request coyoteRequest;


    /**
     * 缓冲区的位置.
     */
    private int markPos = -1;


    /**
     * 字符缓冲限制.
     */
    private int readLimit;


    /**
     * 缓冲区大小.
     */
    private final int size;


    // ----------------------------------------------------------- Constructors


    public InputBuffer() {
        this(DEFAULT_BUFFER_SIZE);
    }


    /**
     * @param size 初始缓冲区大小
     */
    public InputBuffer(int size) {
        this.size = size;
        bb = ByteBuffer.allocate(size);
        clear(bb);
        cb = CharBuffer.allocate(size);
        clear(cb);
        readLimit = size;
    }


    // ------------------------------------------------------------- Properties


    /**
     * 关联的Coyote 请求.
     *
     * @param coyoteRequest Associated Coyote request
     */
    public void setRequest(Request coyoteRequest) {
        this.coyoteRequest = coyoteRequest;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 回收输出缓冲区.
     */
    public void recycle() {

        state = INITIAL_STATE;

        // 如果标记使用的缓冲区过大，则重新分配它
        if (cb.capacity() > size) {
            cb = CharBuffer.allocate(size);
            clear(cb);
        } else {
            clear(cb);
        }
        readLimit = size;
        markPos = -1;
        clear(bb);
        closed = false;

        if (conv != null) {
            conv.recycle();
            encoders.get(conv.getCharset()).push(conv);
            conv = null;
        }

        enc = null;
    }


    /**
     * 关闭输入缓冲区.
     *
     * @throws IOException 发生的底层IOException
     */
    @Override
    public void close() throws IOException {
        closed = true;
    }


    public int available() {
        int available = 0;
        if (state == BYTE_STATE) {
            available = bb.remaining();
        } else if (state == CHAR_STATE) {
            available = cb.remaining();
        }
        if (available == 0) {
            coyoteRequest.action(ActionCode.AVAILABLE,
                    Boolean.valueOf(coyoteRequest.getReadListener() != null));
            available = (coyoteRequest.getAvailable() > 0) ? 1 : 0;
        }
        return available;
    }


    public void setReadListener(ReadListener listener) {
        coyoteRequest.setReadListener(listener);

        // 容器负责第一次调用listener.onDataAvailable(). 如果isReady() 返回 true, 容器需要从一个新线程调用listener.onDataAvailable().
        // 如果isReady() 返回 false, 将注册socket进行读取，而且一旦数据到达, 容器将调用listener.onDataAvailable().
        // 必须首先调用isFinished()作为对 isReady()的调用， 如果请求已完成，将注册socket进行读取，而不是必需的.
        if (!coyoteRequest.isFinished() && isReady()) {
            coyoteRequest.action(ActionCode.DISPATCH_READ, null);
            if (!ContainerThreadMarker.isContainerThread()) {
                // 不在容器线程上，所以需要执行调度
                coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
            }
        }
    }


    public boolean isFinished() {
        int available = 0;
        if (state == BYTE_STATE) {
            available = bb.remaining();
        } else if (state == CHAR_STATE) {
            available = cb.remaining();
        }
        if (available > 0) {
            return false;
        } else {
            return coyoteRequest.isFinished();
        }
    }


    public boolean isReady() {
        if (coyoteRequest.getReadListener() == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("inputBuffer.requiresNonBlocking"));
            }
            return false;
        }
        if (isFinished()) {
            // 如果这是非容器线程, 需要触发读取，最终导致通过容器线程调用onAllDataRead().
            if (!ContainerThreadMarker.isContainerThread()) {
                coyoteRequest.action(ActionCode.DISPATCH_READ, null);
                coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
            }
            return false;
        }
        boolean result = available() > 0;
        if (!result) {
            coyoteRequest.action(ActionCode.NB_READ_INTEREST, null);
        }
        return result;
    }


    boolean isBlocking() {
        return coyoteRequest.getReadListener() == null;
    }


    // ------------------------------------------------- Bytes Handling Methods

    /**
     * 读取字节块中的新字节.
     *
     * @throws IOException 发生的底层IOException
     */
    @Override
    public int realReadBytes() throws IOException {
        if (closed) {
            return -1;
        }
        if (coyoteRequest == null) {
            return -1;
        }

        if (state == INITIAL_STATE) {
            state = BYTE_STATE;
        }

        int result = coyoteRequest.doRead(this);

        return result;
    }


    public int readByte() throws IOException {
        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (checkByteBufferEof()) {
            return -1;
        }
        return bb.get() & 0xFF;
    }


    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (checkByteBufferEof()) {
            return -1;
        }
        int n = Math.min(len, bb.remaining());
        bb.get(b, off, n);
        return n;
    }


    /**
     * 将字节从缓冲区转移到指定的ByteBuffer.
     * ByteBuffer的位置将返回给之前的调用, 限制将是由传递字节数递增的位置.
     *
     * @param to 写入的ByteBuffer.
     * @return 指定读取的实际字节数, 或 -1 如果到达流的末端
     * @throws IOException 如果发生输入或输出异常
     */
    public int read(ByteBuffer to) throws IOException {
        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (checkByteBufferEof()) {
            return -1;
        }
        int n = Math.min(to.remaining(), bb.remaining());
        int orgLimit = bb.limit();
        bb.limit(bb.position() + n);
        to.put(bb);
        bb.limit(orgLimit);
        to.limit(to.position()).position(to.position() - n);
        return n;
    }


    // ------------------------------------------------- Chars Handling Methods


    /**
     * @param s    编码值
     *
     * @deprecated This method will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public void setEncoding(String s) {
        enc = s;
    }


    public int realReadChars() throws IOException {
        checkConverter();

        boolean eof = false;

        if (bb.remaining() <= 0) {
            int nRead = realReadBytes();
            if (nRead < 0) {
                eof = true;
            }
        }

        if (markPos == -1) {
            clear(cb);
        } else {
            // 确保在最坏的情况下有足够的空间
            makeSpace(bb.remaining());
            if ((cb.capacity() - cb.limit()) == 0 && bb.remaining() != 0) {
                // 越过了限制
                clear(cb);
                markPos = -1;
            }
        }

        state = CHAR_STATE;
        conv.convert(bb, cb, this, eof);

        if (cb.remaining() == 0 && eof) {
            return -1;
        } else {
            return cb.remaining();
        }
    }


    @Override
    public int read() throws IOException {

        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (checkCharBufferEof()) {
            return -1;
        }
        return cb.get();
    }


    @Override
    public int read(char[] cbuf) throws IOException {

        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        return read(cbuf, 0, cbuf.length);
    }


    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {

        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (checkCharBufferEof()) {
            return -1;
        }
        int n = Math.min(len, cb.remaining());
        cb.get(cbuf, off, n);
        return n;
    }


    @Override
    public long skip(long n) throws IOException {
        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (n < 0) {
            throw new IllegalArgumentException();
        }

        long nRead = 0;
        while (nRead < n) {
            if (cb.remaining() >= n) {
                cb.position(cb.position() + (int) n);
                nRead = n;
            } else {
                nRead += cb.remaining();
                cb.position(cb.limit());
                int nb = realReadChars();
                if (nb < 0) {
                    break;
                }
            }
        }
        return nRead;
    }


    @Override
    public boolean ready() throws IOException {
        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }
        if (state == INITIAL_STATE) {
            state = CHAR_STATE;
        }
        return (available() > 0);
    }


    @Override
    public boolean markSupported() {
        return true;
    }


    @Override
    public void mark(int readAheadLimit) throws IOException {

        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (cb.remaining() <= 0) {
            clear(cb);
        } else {
            if ((cb.capacity() > (2 * size)) && (cb.remaining()) < (cb.position())) {
                cb.compact();
                cb.flip();
            }
        }
        readLimit = cb.position() + readAheadLimit + size;
        markPos = cb.position();
    }


    @Override
    public void reset() throws IOException {

        if (closed) {
            throw new IOException(sm.getString("inputBuffer.streamClosed"));
        }

        if (state == CHAR_STATE) {
            if (markPos < 0) {
                clear(cb);
                markPos = -1;
                throw new IOException();
            } else {
                cb.position(markPos);
            }
        } else {
            clear(bb);
        }
    }


    public void checkConverter() throws IOException {
        if (conv != null) {
            return;
        }

        Charset charset = null;

        if (coyoteRequest != null) {
            charset = coyoteRequest.getCharset();
        }

        if (charset == null) {
            if (enc == null) {
                charset = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
            } else {
                charset = B2CConverter.getCharset(enc);
            }
        }

        SynchronizedStack<B2CConverter> stack = encoders.get(charset);
        if (stack == null) {
            stack = new SynchronizedStack<>();
            encoders.putIfAbsent(charset, stack);
            stack = encoders.get(charset);
        }
        conv = stack.pop();

        if (conv == null) {
            conv = createConverter(charset);
        }
    }


    private static B2CConverter createConverter(final Charset charset) throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<B2CConverter>() {

                    @Override
                    public B2CConverter run() throws IOException {
                        return new B2CConverter(charset);
                    }
                });
            } catch (PrivilegedActionException ex) {
                Exception e = ex.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException(e);
                }
            }
        } else {
            return new B2CConverter(charset);
        }

    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        bb = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return bb;
    }


    @Override
    public void expand(int size) {
        // no-op
    }


    private boolean checkByteBufferEof() throws IOException {
        if (bb.remaining() == 0) {
            int n = realReadBytes();
            if (n < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean checkCharBufferEof() throws IOException {
        if (cb.remaining() == 0) {
            int n = realReadChars();
            if (n < 0) {
                return true;
            }
        }
        return false;
    }

    private void clear(Buffer buffer) {
        buffer.rewind().limit(0);
    }

    private void makeSpace(int count) {
        int desiredSize = cb.limit() + count;
        if(desiredSize > readLimit) {
            desiredSize = readLimit;
        }

        if(desiredSize <= cb.capacity()) {
            return;
        }

        int newSize = 2 * cb.capacity();
        if(desiredSize >= newSize) {
            newSize= 2 * cb.capacity() + count;
        }

        if (newSize > readLimit) {
            newSize = readLimit;
        }

        CharBuffer tmp = CharBuffer.allocate(newSize);
        int oldPosition = cb.position();
        cb.position(0);
        tmp.put(cb);
        tmp.flip();
        tmp.position(oldPosition);
        cb = tmp;
        tmp = null;
    }
}
