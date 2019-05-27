package org.apache.tomcat.util.http.fileupload;

/**
 * 表示 {@link FileItem} 或 {@link FileItemStream}将作为 header 读取的接口.
 */
public interface FileItemHeadersSupport {

    /**
     * 返回此项中本地定义的标头集合.
     *
     * @return 此项的{@link FileItemHeaders}.
     */
    FileItemHeaders getHeaders();

    /**
     * 设置从项目中读取的 Header.
     * {@link FileItem}或{@link FileItemStream}的实现应该实现此接口，以便能够获取 header块中找到的原始 header.
     *
     * @param headers 保留此实例的标头的实例.
     */
    void setHeaders(FileItemHeaders headers);

}
