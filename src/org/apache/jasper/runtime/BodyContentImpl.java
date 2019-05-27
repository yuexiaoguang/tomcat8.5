package org.apache.jasper.runtime;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.BodyContent;

import org.apache.jasper.Constants;

/**
 * 将文本写入字符输出流, 缓冲字符，以便有效地写入单个字符, 数组, 字符串. 
 *
 * 为已缓冲的输出提供丢弃的支持. 
 */
public class BodyContentImpl extends BodyContent {

    private static final boolean LIMIT_BUFFER =
        Boolean.parseBoolean(System.getProperty("org.apache.jasper.runtime.BodyContentImpl.LIMIT_BUFFER", "false"));

    private char[] cb;
    private int nextChar;
    private boolean closed;

    private Writer writer;

    /**
     * @param enclosingWriter The wrapped writer
     */
    public BodyContentImpl(JspWriter enclosingWriter) {
        super(enclosingWriter);
        cb = new char[Constants.DEFAULT_TAG_BUFFER_SIZE];
        bufferSize = cb.length;
        nextChar = 0;
        closed = false;
    }

    /**
     * 写入单个字符.
     * 
     * @param c The char to write
     * 
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void write(int c) throws IOException {
        if (writer != null) {
            writer.write(c);
        } else {
            ensureOpen();
            if (nextChar >= bufferSize) {
                reAllocBuff (1);
            }
            cb[nextChar++] = (char) c;
        }
    }

    /**
     * 写一组字符的一部分.
     *
     * <p> 通常该方法将给定数组中的字符存储到该流的缓冲区中, 根据需要将缓冲区刷新到底层流. 
     * 如果请求的长度至少和缓冲区一样大, 但是, 然后该方法将刷新缓冲区并将字符直接写入底层流.
     * 因此，冗余<code>DiscardableBufferedWriter</code>不会不必要地复制数据.
     *
     * @param cbuf 字符数组
     * @param off 从中开始读取字符的偏移量
     * @param len 要写入的字符数量
     * 
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (writer != null) {
            writer.write(cbuf, off, len);
        } else {
            ensureOpen();

            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                    ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return;
            }

            if (len >= bufferSize - nextChar)
                reAllocBuff (len);

            System.arraycopy(cbuf, off, cb, nextChar, len);
            nextChar+=len;
        }
    }

    /**
     * 写一组字符. 此方法不能从Writer类继承, 因为它必须阻止 I/O 异常.
     * 
     * @param buf Content to write
     * 
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void write(char[] buf) throws IOException {
        if (writer != null) {
            writer.write(buf);
        } else {
            write(buf, 0, buf.length);
        }
    }

    /**
     * 写一个字符串的一部分.
     *
     * @param s 要写入的字符串
     * @param off 从中开始读取字符的偏移量
     * @param len 要写入的字符数
     * 
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void write(String s, int off, int len) throws IOException {
        if (writer != null) {
            writer.write(s, off, len);
        } else {
            ensureOpen();
            if (len >= bufferSize - nextChar)
                reAllocBuff(len);

            s.getChars(off, off + len, cb, nextChar);
            nextChar += len;
        }
    }

    /**
     * 写入一个字符串. 此方法不能从Writer类继承, 因为它必须阻止 I/O 异常.
     * 
     * @param s String to be written
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void write(String s) throws IOException {
        if (writer != null) {
            writer.write(s);
        } else {
            write(s, 0, s.length());
        }
    }

    /**
     * 写入行分隔符. 行分隔符字符串由系统属性<tt>line.separator</tt>定义, 不一定是单一的 newline ('\n') 字符.
     *
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void newLine() throws IOException {
        if (writer != null) {
            writer.write(System.lineSeparator());
        } else {
            write(System.lineSeparator());
        }
    }

    /**
     * 打印一个 boolean 值.
     * 由 <code>{@link java.lang.String#valueOf(boolean)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param b 要打印的<code>boolean</code>
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(boolean b) throws IOException {
        if (writer != null) {
            writer.write(b ? "true" : "false");
        } else {
            write(b ? "true" : "false");
        }
    }

    /**
     * 打印一个字符. 该字符被翻译成一个或多个字节, 根据平台的默认字符编码, 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param c The <code>char</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(char c) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(c));
        } else {
            write(String.valueOf(c));
        }
    }

    /**
     * 打印一个integer.
     * 由 <code>{@link java.lang.String#valueOf(int)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param i The <code>int</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(int i) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(i));
        } else {
            write(String.valueOf(i));
        }
    }

    /**
     * 打印一个long.
     * 由<code>{@link java.lang.String#valueOf(long)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param l The <code>long</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(long l) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(l));
        } else {
            write(String.valueOf(l));
        }
    }

    /**
     * 打印一个float.
     * 由<code>{@link java.lang.String#valueOf(float)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param f The <code>float</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(float f) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(f));
        } else {
            write(String.valueOf(f));
        }
    }

    /**
     * 打印一个double.
     * 由<code>{@link java.lang.String#valueOf(double)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param d The <code>double</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(double d) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(d));
        } else {
            write(String.valueOf(d));
        }
    }

    /**
     * 打印一个char数组.
     * 该字符被翻译成字节, 根据平台的默认字符编码, 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param s The array of chars to be printed
     *
     * @throws NullPointerException If <code>s</code> is <code>null</code>
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(char[] s) throws IOException {
        if (writer != null) {
            writer.write(s);
        } else {
            write(s);
        }
    }

    /**
     * 打印一个字符串. 如果参数是<code>null</code>, 打印<code>"null"</code>.
     * 否则, 字符串被翻译成字节, 根据平台的默认字符编码, 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param s The <code>String</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(String s) throws IOException {
        if (s == null) s = "null";
        if (writer != null) {
            writer.write(s);
        } else {
            write(s);
        }
    }

    /**
     * 打印一个object.
     * 由<code>{@link java.lang.String#valueOf(Object)}</code>产生的字符串被转换成字节, 根据平台的默认字符编码,
     * 这些字节通过<code>{@link #write(int)}</code>方法写入.
     *
     * @param obj The <code>Object</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void print(Object obj) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(obj));
        } else {
            write(String.valueOf(obj));
        }
    }

    /**
     * 通过写入行分隔符字符串终止当前行.
     * 行分隔符字符串通过系统属性<code>line.separator</code>定义, 而不一定是一个换行符(<code>'\n'</code>).
     *
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println() throws IOException {
        newLine();
    }

    /**
     * 打印一个布尔值，然后终止该行. 这个方法调用<code>{@link #print(boolean)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The <code>boolean</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(boolean x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个字符，然后终止该行.这个方法调用<code>{@link #print(char)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The <code>char</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(char x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个integer，然后终止该行.这个方法调用<code>{@link #print(int)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The <code>int</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(int x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个long，然后终止该行.这个方法调用<code>{@link #print(long)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The <code>long</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(long x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个float，然后终止该行. 这个方法调用<code>{@link #print(float)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The <code>float</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(float x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个double，然后终止该行.这个方法调用<code>{@link #print(double)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The <code>double</code> to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(double x) throws IOException{
        print(x);
        println();
    }

    /**
     * 打印一组字符，然后终止该行. 这个方法调用<<code>{@link #print(char[])}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The <code>char</code> array to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(char x[]) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个String，然后终止该行. 这个方法调用<code>{@link #print(String)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The string to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(String x) throws IOException {
        print(x);
        println();
    }

    /**
     * 打印一个Object，然后终止该行. 这个方法调用<code>{@link #print(Object)}</code>然后调用<code>{@link #println()}</code>.
     *
     * @param x The object to be printed
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void println(Object x) throws IOException {
        print(x);
        println();
    }

    /**
     * 清除缓冲区的内容. 如果缓冲区已被刷新，则清除操作将抛出一个 IOException, 表示某些数据已经不可撤销地写入客户端响应流.
     *
     * @throws IOException If there is no wrapped writer
     */
    @Override
    public void clear() throws IOException {
        if (writer != null) {
            throw new IOException();
        } else {
            nextChar = 0;
            if (LIMIT_BUFFER && (cb.length > Constants.DEFAULT_TAG_BUFFER_SIZE)) {
                cb = new char[Constants.DEFAULT_TAG_BUFFER_SIZE];
                bufferSize = cb.length;
            }
        }
    }

    /**
     * 清除缓冲区的当前内容. 不像 clear(), 这个方法不会抛出 IOException, 如果缓冲区已被刷新.
     * 它只清除缓冲区的当前内容并返回.
     *
     * @throws IOException Should not happen
     */
    @Override
    public void clearBuffer() throws IOException {
        if (writer == null) {
            this.clear();
        }
    }

    /**
     * 关闭流, 并首先刷新它. 一旦流关闭, 不论 write() 还是 flush() 调用都会抛出IOException. 关闭先前关闭的流, 但是, 没有效果.
     *
     * @throws IOException Error writing to wrapped writer
     */
    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        } else {
            closed = true;
        }
    }

    /**
     * 返回JspWriter使用的缓冲区的大小.
     *
     * @return 缓冲区字节大小, 或 0.
     */
    @Override
    public int getBufferSize() {
        // 根据规范, JspContext.pushBody(java.io.Writer writer)返回的JspWriter必须表现得好像没有缓冲.
        // 即它的getBufferSize() 必须总是返回 0.
        return (writer == null) ? bufferSize : 0;
    }

    /**
     * @return 缓冲区中未使用的字节数
     */
    @Override
    public int getRemaining() {
        return (writer == null) ? bufferSize-nextChar : 0;
    }

    /**
     * 返回这个BodyJspWriter 的值作为一个Reader.
     * Note: 这是经过评估的!!  没有脚本, 在这个流中.
     *
     * @return the value of this BodyJspWriter as a Reader
     */
    @Override
    public Reader getReader() {
        return (writer == null) ? new CharArrayReader (cb, 0, nextChar) : null;
    }

    /**
     * 返回这个BodyJspWriter 的值作为一个 String.
     * Note: 这是经过评估的!!  在这个流中没有脚本.
     *
     * @return the value of the BodyJspWriter as a String
     */
    @Override
    public String getString() {
        return (writer == null) ? new String(cb, 0, nextChar) : null;
    }

    /**
     * 将这个BodyJspWriter中的内容写入 Writer.
     * 子类很可能用实现来做一些有趣的事情，所以有些东西是非常高效的.
     *
     * @param out 要写入的writer
     * @throws IOException Error writing to writer
     */
    @Override
    public void writeOut(Writer out) throws IOException {
        if (writer == null) {
            out.write(cb, 0, nextChar);
            // 未经writer调用的刷新可能是BodyContent,而且它不允许刷新.
        }
    }

    /**
     * 设置所有输出写入的 writer.
     */
    void setWriter(Writer writer) {
        this.writer = writer;
        closed = false;
        if (writer == null) {
            clearBody();
        }
    }

    /**
     * 这个方法将"reset" BodyContentImpl的内部状态, 释放所有的内部引用, 并准备为稍后可能的{@link PageContextImpl#pushBody(Writer)}重用.
     *
     * <p>Note, BodyContentImpl实例通常属于一个PageContextImpl 实例, 并且PageContextImpl 实例将被重用.
     */
    protected void recycle() {
        this.writer = null;
        try {
            this.clear();
        } catch (IOException ex) {
            // ignore
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("Stream closed");
    }

    /**
     * 重新分配缓冲区，由于规范要求它是无限的.
     */
    private void reAllocBuff(int len) {

        if (bufferSize + len <= cb.length) {
            bufferSize = cb.length;
            return;
        }

        if (len < cb.length) {
            len = cb.length;
        }

        char[] tmp = new char[cb.length + len];
        System.arraycopy(cb, 0, tmp, 0, cb.length);
        cb = tmp;
        bufferSize = cb.length;
    }
}
