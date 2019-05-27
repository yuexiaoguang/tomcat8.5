package org.apache.tomcat.util.http.fileupload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * <p> 此类表示在<code>multipart/form-data</code> POST请求中接收的文件或表单项.
 *
 * <p> 从{@link org.apache.tomcat.util.http.fileupload.FileUpload FileUpload}实例检索此类的实例后
 * (see {@link org.apache.tomcat.util.http.fileupload.FileUpload#parseRequest(RequestContext)}),
 * 您可以使用{@link #get()}一次请求文件的所有内容，或者使用{@link #getInputStream()}请求{@link java.io.InputStream InputStream}, 
 * 并处理该文件, 而不尝试加载 它进入内存, 大文件可能会派上用场.
 *
 * <p> 虽然此接口本身不扩展<code>javax.activation.DataSource</code> (避免很少使用依赖), 使用与该接口中的方法相同的签名来明确定义了几个已定义的方法.
 * 这允许此接口的实现也实现<code>javax.activation.DataSource</code>, 只需要最少的额外工作.
 */
public interface FileItem extends FileItemHeadersSupport {

    // ------------------------------- Methods from javax.activation.DataSource

    /**
     * 返回可用于检索文件内容的{@link java.io.InputStream InputStream}.
     *
     * @return 可用于检索文件的内容的{@link java.io.InputStream InputStream}.
     *
     * @throws IOException 如果发生错误.
     */
    InputStream getInputStream() throws IOException;

    /**
     * 返回浏览器传递的内容类型; 如果未定义，则返回<code> null </ code>.
     */
    String getContentType();

    /**
     * 返回客户端文件系统中的原始文件名，由浏览器（或其他客户端软件）提供.
     * 在多数情况下, 这将是基本文件名, 没有路径信息. 但是, 在一些客户端中, 例如 Opera 浏览器, 包括路径信息.
     *
     * @return 客户端文件系统中的原始文件名.
     * @throws InvalidFileNameException 包含NUL字符的文件名, 这可能是安全攻击的指标.
     * 		如果打算仍然使用文件名, 捕获异常并使用 InvalidFileNameException#getName().
     */
    String getName();

    // ------------------------------------------------------- FileItem methods

    /**
     * 提供有关是否从内存中读取文件内容的提示.
     *
     * @return <code>true</code> 如果文件内容将从内存中读取;
     *         否则<code>false</code>.
     */
    boolean isInMemory();

    /**
     * 返回文件项的大小.
     *
     * @return 文件项的大小, 以字节为单位.
     */
    long getSize();

    /**
     * 以字节数组的形式返回文件项的内容.
     */
    byte[] get();

    /**
     * 以String形式返回文件项的内容, 使用指定的编码.
     * 此方法使用{@link #get()}来检索项目的内容.
     *
     * @param encoding 要使用的字符编码.
     *
     * @return 文件项的内容, 以String形式.
     *
     * @throws UnsupportedEncodingException 如果请求的字符编码不可用.
     */
    String getString(String encoding) throws UnsupportedEncodingException;

    /**
     * 以String形式返回文件项的内容, 使用默认字符编码.
     * 此方法使用{@link #get()}来检索项目的内容.
     *
     * @return 文件项的内容, 以String形式.
     */
    String getString();

    /**
     * 将上传的文件项写入磁盘的便捷方法.
     * 客户端代码不关心文件项是否存储在内存中, 或在磁盘上的临时位置. 它们只想将上传的文件项写入文件.
     * <p>
     * 如果对同一文件项多次调用此方法，则无法保证此方法成功. 这允许使用特定实现, 
     * 例如, 文件重命名，尽可能，而不是复制所有底层数据，从而获得显着的性能优势.
     *
     * @param file 应存储上传文件项的<code>File</code>.
     *
     * @throws Exception 如果发生错误.
     */
    void write(File file) throws Exception;

    /**
     * 删除文件项的底层存储, 包括删除任何关联的临时磁盘文件.
     * 虽然在<code>FileItem</code>实例被垃圾回收时会自动删除此存储, 此方法可用于确保在较早时间完成此操作, 从而保留系统资源.
     */
    void delete();

    /**
     * 返回与此文件项对应的multipart表单中的字段名称.
     *
     * @return 表单字段的名称.
     */
    String getFieldName();

    /**
     * 设置用于引用此文件项的字段名称.
     *
     * @param name 表单字段的名称.
     */
    void setFieldName(String name);

    /**
     * 确定<code>FileItem</code>实例是否表示简单的表单字段.
     *
     * @return <code>true</code>如果实例表示一个简单的表单字段; <code>false</code>如果它表示上传的文件.
     */
    boolean isFormField();

    /**
     * 指定<code>FileItem</code>实例是否表示简单的表单字段.
     *
     * @param state <code>true</code> 如果实例表示一个简单的表单字段; <code>false</code>如果它表示上传的文件.
     */
    void setFormField(boolean state);

    /**
     * 返回可用于存储文件内容的{@link java.io.OutputStream OutputStream}.
     *
     * @return 可用于存储文件内容的{@link java.io.OutputStream OutputStream}.
     *
     * @throws IOException 如果发生错误.
     */
    OutputStream getOutputStream() throws IOException;

}
