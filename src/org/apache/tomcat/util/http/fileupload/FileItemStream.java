package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>此接口提供对<code>multipart/form-data</code> POST请求中接收的文件或表单项的访问.
 * 通过调用{@link #openStream()}来检索项目内容.</p>
 * <p>通过访问迭代器来创建此类的实例, 由{@link FileUploadBase#getItemIterator(RequestContext)}返回.</p>
 * <p><em>Note</em>: 迭代器及其相关的{@link FileItemStream}实例之间存在交互:
 * 通过在迭代器上调用{@link java.util.Iterator#hasNext()}, 丢弃所有数据, 这些数据尚未从之前的数据中读取.</p>
 */
public interface FileItemStream extends FileItemHeadersSupport {

    /**
     * 如果尝试从{@link InputStream}读取数据, 则抛出此异常, 该数据已由{@link FileItemStream#openStream()}返回,
     * 在迭代器上调用{@link java.util.Iterator#hasNext()}后.
     */
    public static class ItemSkippedException extends IOException {

        /**
         * 串行化版本UID，在序列化异常实例时使用.
         */
        private static final long serialVersionUID = -7280778431581963740L;

    }

    /**
     * 创建一个 {@link InputStream}, 允许读取项目内容.
     *
     * @return 可以从中读取项目数据的输入流.
     * 
     * @throws IllegalStateException 该方法已在此项目上调用. 无法重新创建数据流.
     * @throws IOException An I/O error occurred.
     */
    InputStream openStream() throws IOException;

    /**
     * 返回浏览器传递的内容类型; 如果未定义, 则返回<code>null</code>.
     *
     * @return 浏览器传递的内容类型; 如果未定义, 则为<code> null </ code>.
     */
    String getContentType();

    /**
     * 返回客户端文件系统中的原始文件名, 由浏览器提供 (或其他客户端软件).
     * 在大多数情况下，这将是基本文件名，没有路径信息. 但是，某些客户端（例如Opera浏览器）确实包含路径信息.
     *
     * @return 客户端文件系统中的原始文件名.
     */
    String getName();

    /**
     * 返回与此文件项对应的multipart表单中的字段名称.
     */
    String getFieldName();

    /**
     * 确定<code>FileItem</code>实例是否表示简单的表单字段.
     *
     * @return <code>true</code> 如果实例表示一个简单的表单字段; <code>false</code>如果它表示上传的文件.
     */
    boolean isFormField();

}
