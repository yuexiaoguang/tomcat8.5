package javax.servlet.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * 这个类表示上传到服务器的一部分，作为<code>multipart/form-data</code>请求主体的一部分.
 * 该部分可以表示上传的文件或表单数据.
 */
public interface Part {

    /**
     * 获取一个<code>InputStream</code>用于获取文件内容.
     *
     * @return 文件内容的InputStream
     *
     * @throws IOException 如果在获取流时发生I/O异常
     */
    public InputStream getInputStream() throws IOException;

    /**
     * 获取浏览器传递的内容类型.
     *
     * @return 浏览器传递的内容类型，或 <code>null</code>.
     */
    public String getContentType();

    /**
     * 获取对应于这部分的multipart表单的字段的名称.
     */
    public String getName();

    /**
     * 如果此部分表示上传文件, 获取上传中提交的文件名.
     * 返回{@code null}，如果没有文件名可用，或者这部分不是文件上传.
     *
     * @return 提交的文件名或{@code null}.
     *
     * @since Servlet 3.1
     */
    public String getSubmittedFileName();

    /**
     * 获取这部分的大小.
     */
    public long getSize();

    /**
     * 将上传的部分写入磁盘的一种便捷方法.
     * 客户端代码不关心该部分是否存储在内存中, 或在磁盘上的临时位置. 他们只是想把上传的部分写入文件.
     *
     *  如果对同一部分调用不止一次，则该方法不能保证成功. 这允许特定的实现在可能的情况下使用文件重命名，而不是复制所有底层数据，从而获得显著的性能效益.
     *
     * @param fileName  上传部分应该存储的位置. 相对位置相对于{@link javax.servlet.MultipartConfigElement#getLocation()}
     *
     * @throws IOException 如果在尝试写入部分时发生I/O错误
     */
    public void write(String fileName) throws IOException;

    /**
     * 删除部分的底层存储，包括删除任何相关联的临时磁盘文件.
     * 虽然容器将自动删除此存储，但此方法可用于确保在较早时完成此操作，从而保留系统资源.
     * <p>
     * 仅当部分实例为垃圾收集时，容器才需要删除相关的存储. 当相关的请求完成处理后，Apache Tomcat 将删除相关的存储.
     * 其他容器的行为可能不同.
     *
     * @throws IOException 如果在删除部分时发生I/O异常
     */
    public void delete() throws IOException;

    /**
     * 将指定的部件标头的值作为字符串获取. 如果有多个同名的标头，则此方法返回该部分中的第一个标头. 头名称不区分大小写.
     *
     * @param name  Header name
     * @return      header值或<code>null</code>
     */
    public String getHeader(String name);

    /**
     * 获取指定部分标头的所有值.
     * @param name header名称. 头名称不区分大小写.
     * @return 指定part header的名称. 或者空集合.
     */
    public Collection<String> getHeaders(String name);

    /**
     * 获取此part提供的标头名称.
     */
    public Collection<String> getHeaderNames();
}
