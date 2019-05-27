package org.apache.tomcat.util.http.fileupload.util;

import java.io.IOException;

/**
 * 可能会被关闭的对象实现的接口 .
 */
public interface Closeable {

    /**
     * 关闭对象.
     *
     * @throws IOException 发生I/O错误.
     */
    void close() throws IOException;

    /**
     * 对象是否已经关闭.
     *
     * @return True 对象已关闭, 否则 false.
     * @throws IOException 发生I/O错误.
     */
    boolean isClosed() throws IOException;

}
