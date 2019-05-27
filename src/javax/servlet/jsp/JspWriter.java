package javax.servlet.jsp;

import java.io.IOException;

/**
 * <p>
 * JSP页面中的操作和模板数据使用JspWriter对象写入，JspWriter对象由隐式变量引用，在PageContext对象中自动初始化.
 * <p>
 * <B>缓冲</B>
 * <p>
 * 最初，JspWriter对象和ServletResponse的PrintWriter对象关联，在某种程度上取决于页面是否被缓冲.
 * 如果页面没有缓冲, 输出写入到这个 JspWriter对象将直接通过PrintWriter写入, 如果有必要将通过响应对象的getWriter()方法创建一个.
 * 如果页面缓冲, PrintWriter对象将不会被创建，直到缓冲区刷新和像setContentType()的操作合法之后.
 * 由于这种灵活性大大简化了编程, 缓冲是jsp页面的默认值.
 * <p>
 * 缓冲区引发了当缓冲区超出时该怎么做的问题. 可以采取两种方法:
 * <ul>
 * <li>超出缓冲区不是致命错误; 超过缓冲区时, 刷新到输出.
 * <li>超出缓冲区是一个致命错误; 超过缓冲区时, 抛出一个异常.
 * </ul>
 * <p>
 * 这两种方法都是有效的, 因此，这两种技术都在JSP技术中得到支持.
 * 页面的行为是autoFlush属性控制的, 默认是true. 总的来说，JSP页面，需要确保正确和完整的数据被发送到他们的客户可能要设置autoFlush为false，
 * 结合典型案例，其中客户端是一个应用程序本身. 另一方面，即使在部分构建时发送有意义的数据的JSP页面也可能要设置 autoFlush为 true;
 * 例如，当数据通过浏览器发送到即时显示时. 每个应用程序都需要考虑它们的特定需求.
 * <p>
 * 另一种方法是使缓冲区大小不受限制; 但是，这有一个缺点，即失控的计算将消耗无限量的资源.
 * <p>
 * JSP实现类的"out"隐式变量是这种类型.
 * 如果页面指令选择 autoflush="true"，那么这个类上的所有的I/O 操作将自动刷新缓冲区的内容， 如果当前操作没有刷新，则会出现溢出条件.
 * 如果autoflush="false"， 那么这个类上的所有的I/O 操作将抛出一个IOException，如果执行当前操作将导致缓冲区溢出.
 */
public abstract class JspWriter extends java.io.Writer {

    /**
     * Writer不会缓冲输出.
     */
    public static final int NO_BUFFER = 0;

    /**
     * Writer是缓冲的，并使用实现默认缓冲区大小.
     */
    public static final int DEFAULT_BUFFER = -1;

    /**
     * Writer是缓冲的，是无界的; 在BodyContent中使用.
     */
    public static final int UNBOUNDED_BUFFER = -2;

    /**
     * @param bufferSize JspWriter使用的缓冲区大小
     * @param autoFlush JspWriter是否自动刷新
     */
    protected JspWriter(int bufferSize, boolean autoFlush) {
        this.bufferSize = bufferSize;
        this.autoFlush = autoFlush;
    }

    /**
     * 另起一行.
     * 行分隔符字符串使用系统属性<tt>line.separator</tt>定义, 不一定是('\n')字符.
     *
     * @exception IOException 如果出现I/O错误
     */
    public abstract void newLine() throws IOException;

    public abstract void print(boolean b) throws IOException;

    public abstract void print(char c) throws IOException;

    public abstract void print(int i) throws IOException;

    public abstract void print(long l) throws IOException;

    public abstract void print(float f) throws IOException;

    public abstract void print(double d) throws IOException;

    public abstract void print(char s[]) throws IOException;

    public abstract void print(String s) throws IOException;

    public abstract void print(Object obj) throws IOException;

    public abstract void println() throws IOException;

    public abstract void println(boolean x) throws IOException;

    public abstract void println(char x) throws IOException;

    public abstract void println(int x) throws IOException;

    public abstract void println(long x) throws IOException;

    public abstract void println(float x) throws IOException;

    public abstract void println(double x) throws IOException;

    public abstract void println(char x[]) throws IOException;

    public abstract void println(String x) throws IOException;

    public abstract void println(Object x) throws IOException;

    /**
     * 清除缓冲区的内容.
     * 如果缓冲区已经被刷新，将抛出IOException, 表示某些数据已经不可撤销地写入客户端的响应流中.
     *
     * @throws IOException 如果出现I/O错误
     */
    public abstract void clear() throws IOException;

    /**
     * 清除缓冲区的当前内容.
     * 和clear()不一样, 这个方法不会抛出IOException，如果缓冲区已被刷新. 它只清除缓冲区的当前内容并返回.
     *
     * @throws IOException 如果出现I/O错误
     */
    public abstract void clearBuffer() throws IOException;

    /**
     * 刷新流.
     * 如果流已经保存来自各种write()方法的字符到缓冲区, 立即把它们写到目的地. 然后，如果该目的地是另一个字符或字节流，则刷新它.
     * 因此，一个 flush()调用将会刷新在Writer和OutputStream链中的所有的缓冲区.
     * <p>
     * 如果超出缓冲区大小，则可以间接调用该方法.
     * <p>
     * 一旦流关闭, write()或 flush()调用将抛出IOException异常.
     *
     * @exception IOException 如果出现I/O错误
     */
    @Override
    public abstract void flush() throws IOException;

    /**
     * 关闭流, 先刷新它.
     * <p>
     * 此方法不需要显式调用， JSP容器生成的代码将自动包含一个close()调用.
     * <p>
     * 关闭先前关闭的流, 和flush()不一样, 没有效果.
     *
     * @exception IOException 如果出现I/O错误
     */
    @Override
    public abstract void close() throws IOException;

    /**
     * 此方法返回缓冲区的大小.
     *
     * @return 缓冲区的大小为字节, 或 0.
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * 此方法返回缓冲区中未使用的字节数.
     *
     * @return 缓冲区中未使用的字节数
     */
    public abstract int getRemaining();

    /**
     * JspWriter是否自动刷新.
     *
     * @return JspWriter自动刷新还是抛出IOException，缓冲区溢出时
     */
    public boolean isAutoFlush() {
        return autoFlush;
    }

    /*
     * fields
     */

    /**
     * 缓冲区的大小.
     */
    protected int bufferSize;

    /**
     * JspWriter是否自动刷新.
     */
    protected boolean autoFlush;
}
