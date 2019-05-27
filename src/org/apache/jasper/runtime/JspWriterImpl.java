package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.Writer;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.servlet.ServletResponse;
import javax.servlet.jsp.JspWriter;

import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.security.SecurityUtil;

/**
 * 将文本写入字符输出流, 缓冲字符以提供单个字符, 数组, 字符串高效的写入. 
 *
 * 为已缓冲的输出提供丢弃的支持. 
 * 
 * 当JSP规范中的缓冲问题被修复时，这需要重新考虑 -akv 
 */
public class JspWriterImpl extends JspWriter {

    private Writer out;
    private ServletResponse response;
    private char cb[];
    private int nextChar;
    private boolean flushed = false;
    private boolean closed = false;

    public JspWriterImpl() {
        super( Constants.DEFAULT_BUFFER_SIZE, true );
    }

    /**
     * 创建一个新的缓冲字符输出流，它使用给定大小的输出缓冲区.
     *
     * @param  response A Servlet Response
     * @param  sz   	输出缓冲区的大小
     * @param autoFlush <code>true</code>在缓冲区满时自动刷新, <code>false</code>在缓冲区满时抛出溢出异常
     * 
     * @exception  IllegalArgumentException  如果sz小于= 0
     */
    public JspWriterImpl(ServletResponse response, int sz,
            boolean autoFlush) {
        super(sz, autoFlush);
        if (sz < 0)
            throw new IllegalArgumentException("Buffer size <= 0");
        this.response = response;
        cb = sz == 0 ? null : new char[sz];
        nextChar = 0;
    }

    void init( ServletResponse response, int sz, boolean autoFlush ) {
        this.response= response;
        if( sz > 0 && ( cb == null || sz > cb.length ) )
            cb=new char[sz];
        nextChar = 0;
        this.autoFlush=autoFlush;
        this.bufferSize=sz;
    }

    void recycle() {
        flushed = false;
        closed = false;
        out = null;
        nextChar = 0;
        response = null;
    }

    /**
     * 将输出缓冲区刷新到底层字符流, 不刷新流本身. 可能被PrintStream调用.
     * 
     * @throws IOException Error writing buffered data
     */
    protected final void flushBuffer() throws IOException {
        if (bufferSize == 0)
            return;
        flushed = true;
        ensureOpen();
        if (nextChar == 0)
            return;
        initOut();
        out.write(cb, 0, nextChar);
        nextChar = 0;
    }

    private void initOut() throws IOException {
        if (out == null) {
            out = response.getWriter();
        }
    }

    private String getLocalizeMessage(final String message){
        if (SecurityUtil.isPackageProtectionEnabled()){
            return AccessController.doPrivileged(new PrivilegedAction<String>(){
                @Override
                public String run(){
                    return Localizer.getMessage(message);
                }
            });
        } else {
            return Localizer.getMessage(message);
        }
    }

    /**
     * 丢弃输出缓冲区.
     */
    @Override
    public final void clear() throws IOException {
        if ((bufferSize == 0) && (out != null))
            // clear() 是非法的, 在任何缓冲输出后(JSP.5.5)
            throw new IllegalStateException(
                    getLocalizeMessage("jsp.error.ise_on_clear"));
        if (flushed)
            throw new IOException(
                    getLocalizeMessage("jsp.error.attempt_to_clear_flushed_buffer"));
        ensureOpen();
        nextChar = 0;
    }

    @Override
    public void clearBuffer() throws IOException {
        if (bufferSize == 0)
            throw new IllegalStateException(
                    getLocalizeMessage("jsp.error.ise_on_clear"));
        ensureOpen();
        nextChar = 0;
    }

    private final void bufferOverflow() throws IOException {
        throw new IOException(getLocalizeMessage("jsp.error.overflow"));
    }

    /**
     * 刷新
     */
    @Override
    public void flush()  throws IOException {
        flushBuffer();
        if (out != null) {
            out.flush();
        }
    }

    /**
     * 关闭流
     */
    @Override
    public void close() throws IOException {
        if (response == null || closed)
            // 多次调用关闭也是可以的
            return;
        flush();
        if (out != null)
            out.close();
        out = null;
        closed = true;
    }

    /**
     * @return 缓冲区中未使用的字节数
     */
    @Override
    public int getRemaining() {
        return bufferSize - nextChar;
    }

    /** 检查以确保流尚未关闭  */
    private void ensureOpen() throws IOException {
        if (response == null || closed)
            throw new IOException("Stream closed");
    }


    /**
     * 写入单个字符.
     */
    @Override
    public void write(int c) throws IOException {
        ensureOpen();
        if (bufferSize == 0) {
            initOut();
            out.write(c);
        }
        else {
            if (nextChar >= bufferSize)
                if (autoFlush)
                    flushBuffer();
                else
                    bufferOverflow();
            cb[nextChar++] = (char) c;
        }
    }

    /**
     * 最小值方法, 避免加载 java.lang.Math, 如果我们用完了文件描述符并试图打印堆栈跟踪.
     */
    private static int min(int a, int b) {
        if (a < b) return a;
        return b;
    }

    /**
     * 写入一组字符的一部分.
     *
     * <p> 通常该方法将给定数组中的字符存储到该流的缓冲区中, 根据需要将缓冲区刷新到底层流.
     * 如果请求的长度至少和缓冲区一样大, 但是, 然后该方法将刷新缓冲区并将字符直接写入底层流.
     * 因此，冗余<code>DiscardableBufferedWriter</code>不会复制不必要的数据.
     *
     * @param  cbuf  字符数组
     * @param  off   从中开始读取字符的偏移量
     * @param  len   要写入的字符的数量
     */
    @Override
    public void write(char cbuf[], int off, int len) throws IOException {
        ensureOpen();

        if (bufferSize == 0) {
            initOut();
            out.write(cbuf, off, len);
            return;
        }

        if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        if (len >= bufferSize) {
            /* 如果请求长度超过了输出缓冲区的大小, 刷新缓冲区，然后直接写入数据. 这样的缓冲流将无害的关联. */
            if (autoFlush)
                flushBuffer();
            else
                bufferOverflow();
            initOut();
            out.write(cbuf, off, len);
            return;
        }

        int b = off, t = off + len;
        while (b < t) {
            int d = min(bufferSize - nextChar, t - b);
            System.arraycopy(cbuf, b, cb, nextChar, d);
            b += d;
            nextChar += d;
            if (nextChar >= bufferSize)
                if (autoFlush)
                    flushBuffer();
                else
                    bufferOverflow();
        }

    }

    /**
     * 写入一组字符. 此方法不能从Writer类继承, 因为它必须阻止 I/O 异常.
     */
    @Override
    public void write(char buf[]) throws IOException {
        write(buf, 0, buf.length);
    }

    /**
     * 写入一个字符串的一部分.
     *
     * @param  s     要写入的字符串
     * @param  off   从中开始读取字符的偏移量
     * @param  len   要写入的字符数
     */
    @Override
    public void write(String s, int off, int len) throws IOException {
        ensureOpen();
        if (bufferSize == 0) {
            initOut();
            out.write(s, off, len);
            return;
        }
        int b = off, t = off + len;
        while (b < t) {
            int d = min(bufferSize - nextChar, t - b);
            s.getChars(b, b + d, cb, nextChar);
            b += d;
            nextChar += d;
            if (nextChar >= bufferSize)
                if (autoFlush)
                    flushBuffer();
                else
                    bufferOverflow();
        }
    }


    /**
     * 写入一行分隔符. 行分隔符字符串由系统属性<tt>line.separator</tt>定义, 而不一定是一个换行符('\n').
     *
     * @exception  IOException  If an I/O error occurs
     */
    @Override
    public void newLine() throws IOException {
        write(System.lineSeparator());
    }


/* 不终止行的方法 */
    
    /**
     * 打印一个 boolean 值.
     * 由 <code>{@link java.lang.String#valueOf(boolean)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param b 要打印的<code>boolean</code>
     */
    @Override
    public void print(boolean b) throws IOException {
        write(b ? "true" : "false");
    }

    /**
     * 打印一个字符. 该字符被翻译成一个或多个字节, 根据平台的默认字符编码, 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      c   The <code>char</code> to be printed
     */
    @Override
    public void print(char c) throws IOException {
        write(String.valueOf(c));
    }

    /**
     * 打印一个integer.
     * 由 <code>{@link java.lang.String#valueOf(int)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      i   The <code>int</code> to be printed
     */
    @Override
    public void print(int i) throws IOException {
        write(String.valueOf(i));
    }

    /**
     * 打印一个long.
     * 由<code>{@link java.lang.String#valueOf(long)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      l   The <code>long</code> to be printed
     */
    @Override
    public void print(long l) throws IOException {
        write(String.valueOf(l));
    }

    /**
     * 打印一个float.
     * 由<code>{@link java.lang.String#valueOf(float)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      f   The <code>float</code> to be printed
     */
    @Override
    public void print(float f) throws IOException {
        write(String.valueOf(f));
    }

    /**
     * 打印一个double.
     * 由<code>{@link java.lang.String#valueOf(double)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      d   The <code>double</code> to be printed
     */
    @Override
    public void print(double d) throws IOException {
        write(String.valueOf(d));
    }

    /**
     * 打印一个char数组.
     * 该字符被翻译成字节, 根据平台的默认字符编码, 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      s   The array of chars to be printed
     *
     * @throws  NullPointerException  If <code>s</code> is <code>null</code>
     */
    @Override
    public void print(char s[]) throws IOException {
        write(s);
    }

    /**
     * 打印一个字符串. 如果参数是<code>null</code>, 打印<code>"null"</code>.
     * 否则, 字符串被翻译成字节, 根据平台的默认字符编码, 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      s   The <code>String</code> to be printed
     */
    @Override
    public void print(String s) throws IOException {
        if (s == null) {
            s = "null";
        }
        write(s);
    }

    /**
     * 打印一个object.
     * 由<code>{@link java.lang.String#valueOf(Object)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param      obj   The <code>Object</code> to be printed
     */
    @Override
    public void print(Object obj) throws IOException {
        write(String.valueOf(obj));
    }

    /* Methods that do terminate lines */

    /**
     * 通过写入行分隔符字符串终止当前行.
     * 行分隔符字符串通过系统属性<code>line.separator</code>定义, 而不一定是一个换行符(<code>'\n'</code>).
     *
     * 需要修改PrintWriter, 因为默认的println() 直接写入接收器，而不是通过写入方法...  
     */
    @Override
    public void println() throws IOException {
        newLine();
    }

    /**
     * 打印一个布尔值，然后终止该行. 这个方法调用<code>{@link #print(boolean)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(boolean x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个字符，然后终止该行.这个方法调用<code>{@link #print(char)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(char x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个integer，然后终止该行.这个方法调用<code>{@link #print(int)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(int x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个long，然后终止该行.这个方法调用<code>{@link #print(long)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(long x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个float，然后终止该行. 这个方法调用<code>{@link #print(float)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(float x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个double，然后终止该行.这个方法调用<code>{@link #print(double)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(double x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一组字符，然后终止该行. 这个方法调用<<code>{@link #print(char[])}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(char x[]) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个String，然后终止该行. 这个方法调用<code>{@link #print(String)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(String x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个Object，然后终止该行. 这个方法调用<code>{@link #print(Object)}</code>然后调用<code>{@link #println()}</code>.
     */
    @Override
    public void println(Object x) throws IOException {
        print(x);
        println();
    }

}
