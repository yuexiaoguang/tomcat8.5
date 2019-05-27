package javax.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * 定义一个对象，以帮助servlet向客户端发送响应.
 * servlet容器创建一个<code>ServletResponse</code>对象并将它作为参数传递给servlet的<code>service</code>方法.
 * <p>
 * 在MIME正文响应中发送二进制数据, 使用{@link #getOutputStream}返回的{@link ServletOutputStream}.
 * 发送字符数据, 使用{@link #getWriter}返回的<code>PrintWriter</code>对象. 混合二进制和文本数据, 例如, 创建一个多部分反应,
 * 使用一个<code>ServletOutputStream</code>并手动管理字符部分.
 * <p>
 * 对于MIME主体响应的字符集可以显式的使用{@link #setCharacterEncoding}和{@link #setContentType}方法指定, 或隐式的使用 {@link #setLocale}方法.
 * 显式规范优先于隐式规范. 如果没有指定字符集, ISO-8859-1将被使用. 
 * <code>setCharacterEncoding</code>,<code>setContentType</code>, <code>setLocale</code>方法必须在<code>getWriter</code>之前，
 * 在提交要使用的字符编码的响应之前调用.
 * <p>
 */
public interface ServletResponse {

    /**
     * 返回发送响应的字符编码名称(MIME 字符集).
     * 字符编码可能已经使用{@link #setCharacterEncoding}或{@link #setContentType}方法显式指定,
     * 或者隐式的使用{@link #setLocale}方法指定. 显式规范优先于隐式规范.
     * 对这些方法的调用，在调用<code>getWriter</code>之后，或响应完成后，对字符编码没有影响.
     * 如果未指定字符, 默认使用<code>ISO-8859-1</code>.
     * <p>
     *
     * @return 字符编码名称, 例如, <code>UTF-8</code>
     */
    public String getCharacterEncoding();

    /**
     * 返回用于在该响应中发送的MIME主体的内容类型.
     * 必须使用{@link #setContentType}指定适当的内容类型，在响应提交之前. 如果没有指定内容类型, 这个方法返回null.
     * 如果指定了内容类型，则显式或隐式指定字符编码, 像{@link #getCharacterEncoding}中描述的那样, charset参数包含在返回的字符串中.
     * 如果没有指定字符编码, charset参数被省略.
     *
     * @return 指定的内容类型, 例如, <code>text/html; charset=UTF-8</code>, 或null
     */
    public String getContentType();

    /**
     * 返回一个{@link ServletOutputStream} 在响应中写入二进制数据. servlet容器不编码二进制数据.
     * <p>
     * 调用ServletOutputStream的 flush()提交响应. 这个方法或者{@link #getWriter}将被调用来写入主体, 但不能两个都调用.
     *
     * @return 写入二进制数据的{@link ServletOutputStream}
     * @exception IllegalStateException 如果这个响应的<code>getWriter</code>方法已经被调用
     * @exception IOException 如果发生输入输出异常
     */
    public ServletOutputStream getOutputStream() throws IOException;

    /**
     * 返回一个可以向客户端发送字符文本的<code>PrintWriter</code>对象.
     * <code>PrintWriter</code>使用{@link #getCharacterEncoding}返回的字符编码.
     * 如果响应的字符编码没有指定，<code>getCharacterEncoding</code>返回的 (i.e., 默认值<code>ISO-8859-1</code>),
     * <code>getWriter</code>更新它为<code>ISO-8859-1</code>.
     * <p>
     * 调用<code>PrintWriter</code>上的flush()提交响应.
     * <p>
     * 这个方法或者{@link #getOutputStream}将被调用来写入主体, 但不能两个都调用.
     *
     * @return 返回字符数据到客户端的<code>PrintWriter</code>对象
     * @exception java.io.UnsupportedEncodingException 如果<code>getCharacterEncoding</code>返回的字符编码不能使用
     * @exception IllegalStateException 如果<code>getOutputStream</code>方法已经被调用
     * @exception IOException 如果发生输入输出异常
     */
    public PrintWriter getWriter() throws IOException;

    /**
     * 设置发送到客户端的响应的字符编码(MIME字符集), 例如, UTF-8.
     * 如果字符编码已经通过{@link #setContentType} 或 {@link #setLocale}设置, 这个方法会覆盖它.
     * 调用{@link #setContentType}设置<code>text/html</code>并调用这个方法设置<code>UTF-8</code>，
     * 和调用<code>setContentType</code> 设置<code>text/html; charset=UTF-8</code>是等效的.
     * <p>
     * 这个方法可以反复调用来改变字符编码.
     * 这个方法在 <code>getWriter</code>调用之后或者响应已经被提交之后调用是无效的.
     * <p>
     * 如果协议提供了一种方法，容器必须将servlet响应所使用的字符编码传递给客户端.
     * 例如HTTP, 字符编码作为<code>Content-Type</code> header的部分对于文本媒体类型.
     * 注意，如果servlet没有指定内容类型，字符编码不能通过HTTP头进行通信; 但是，它仍然用于对通过servlet响应的writer编写的文本进行编码.
     *
     * @param charset 指定字符集由IANA字符集定义
     *            (http://www.iana.org/assignments/character-sets)
     */
    public void setCharacterEncoding(String charset);

    /**
     * 设置HTTP servlet中响应的内容主体的长度, 这个方法设置HTTP Content-Length header.
     *
     * @param len 指定返回给客户端的内容长度; 设置Content-Length header
     */
    public void setContentLength(int len);

    /**
     * 设置HTTP servlet中响应的内容主体的长度, 这个方法设置HTTP Content-Length header.
     *
     * @param length 指定返回给客户端的内容长度; 设置Content-Length header
     */
    public void setContentLengthLong(long length);

    /**
     * 如果响应尚未提交，则设置发送到客户端的响应的内容类型.
     * 给定的内容类型可以包括字符编码规范, 例如, <code>text/html;charset=UTF-8</code>.
     * 响应的字符编码仅从给定的内容类型设置，如果这个方法在<code>getWriter</code>之前调用.
     * <p>
     * 可以反复调用此方法来更改内容类型和字符编码. 如果在响应已提交之后调用此方法，则此方法无效.
     * 它不设置响应的字符编码，如果在<code>getWriter</code>之后或在响应提交之后调用.
     * <p>
     * 如果协议提供了一种方法，容器必须将servlet响应所使用的字符编码传递给客户端.
     * 例如HTTP, 使用<code>Content-Type</code> header.
     *
     * @param type 指定内容的MIME类型
     */
    public void setContentType(String type);

    /**
     * 为响应的主体设置首选缓冲区大小. servlet容器将使用至少与所请求的大小一样大的缓冲区.
     * 使用的真实的缓冲区大小可以使用<code>getBufferSize</code>获取.
     * <p>
     * 较大的缓冲区允许在实际发送任何内容之前写入更多内容, 这样就为servlet提供了更多的时间来设置适当的状态码和头.
     * 较小的缓冲区减少服务器内存负载，并允许客户端更快地接收数据.
     * <p>
     * 必须在任何响应正文内容写入之前调用此方法; 如果内容已写入或响应对象已提交, 抛出<code>IllegalStateException</code>.
     *
     * @param size 首选缓冲区大小
     * @exception IllegalStateException 如果在内容被写入后调用此方法
     */
    public void setBufferSize(int size);

    /**
     * 返回用于响应的实际缓冲区大小. 如果不使用缓冲区, 这个方法返回 0.
     *
     * @return 实际使用的缓冲区大小
     */
    public int getBufferSize();

    /**
     * 强制将缓冲区中的任何内容写入客户端. 对该方法的调用将自动提交响应, 意味着状态码和头将被写入.
     *
     * @throws IOException 如果在响应刷新期间发生I/O
     */
    public void flushBuffer() throws IOException;

    /**
     * 清除响应中的底层缓冲区的内容，而不清除头或状态码.
     * 如果响应被提交, 这个方法抛出一个<code>IllegalStateException</code>.
     */
    public void resetBuffer();

    /**
     * 返回一个布尔值，指示响应是否已提交. 提交的响应已经有状态码和头.
     */
    public boolean isCommitted();

    /**
     * 清除缓冲区中存在的任何数据，以及状态码和头.
     *
     * @exception IllegalStateException 如果响应已提交
     */
    public void reset();

    /**
     * 如果响应尚未提交，则设置响应的区域.
     * 它还为区域设置适当的字符编码, 如果字符编码没有显式的通过{@link #setContentType} 或 {@link #setCharacterEncoding}设置,
     * <code>getWriter</code>还没有被调用, 响应还没有被提交.
     * 如果部署描述符包含一个<code>locale-encoding-mapping-list</code>元素, 该元素为给定的区域设置提供了映射，就使用此映射.
     * 否则, 从区域编码到字符编码的映射是依赖容器的.
     * <p>
     * 可以反复调用此方法来更改区域设置和字符编码. 如果在响应已提交之后调用该方法，则此方法无效.
     * 它不设置响应的字符编码, 如果在{@link #setContentType}之后, 在{@link #setCharacterEncoding}之后,
     * 在<code>getWriter</code>之后, 或在响应提交之后.
     * <p>
     * 如果协议提供了一种方法，容器必须将servlet响应所使用的字符编码传递给客户端.
     * 例如HTTP, 区域通过header的<code>Content-Language</code>表示, 字符集使用<code>Content-Type</code>.
     * 注意，如果servlet没有指定内容类型，字符编码不能通过HTTP头进行通信; 但是, 它仍然用于对通过servlet响应的writer写入的文本进行编码.
     *
     * @param loc 响应的区域
     */
    public void setLocale(Locale loc);

    /**
     * 返回为此响应使用{@link #setLocale}方法指定的区域.
     * 在响应提交之后调用<code>setLocale</code>无效.
     *
     * @return 使用{@link #setLocale}方法指定的区域. 如果未指定区域, 返回容器的默认区域设置.
     */
    public Locale getLocale();

}
