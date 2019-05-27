package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;

/**
 * 由{@link FileUploadBase#getItemIterator(RequestContext)}返回的迭代器.
 */
public interface FileItemIterator {

    /**
     * 是否有另一个{@link FileItemStream}实例可用.
     *
     * @throws FileUploadException 解析或处理文件项失败
     * @throws IOException 读取文件项失败.
     * @return True, 如果有一个或多个其他文件可用, 否则 false.
     */
    boolean hasNext() throws FileUploadException, IOException;

    /**
     * 返回下一个可用的 {@link FileItemStream}.
     *
     * @throws java.util.NoSuchElementException 没有更多项目可用. 使用 {@link #hasNext()} 来防止此异常.
     * @throws FileUploadException 解析或处理文件项失败.
     * @throws IOException 读取文件项失败.
     * 
     * @return FileItemStream 实例, 它提供对下一个文件项的访问.
     */
    FileItemStream next() throws FileUploadException, IOException;

}
