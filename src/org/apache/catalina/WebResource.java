package org.apache.catalina;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

/**
 * 表示Web应用程序中的文件或目录. 它大量借用了{@link java.io.File}.
 */
public interface WebResource {
    /**
     * @return {@link java.io.File#lastModified()}.
     */
    long getLastModified();

    /**
     * @return 此资源的最后修改时间， RFC 2616指定的HTTP Last-Modified header的正确格式.
     */
    String getLastModifiedHttp();

    /**
     * @return {@link java.io.File#exists()}.
     */
    boolean exists();

    /**
     * 指示此资源是否需要应用程序正确扫描文件结构，但这在main中并不存在或任何其他{@link WebResourceSet}.
     * 例如, 如果将外部目录映射到在其他空的Web应用程序中的 /WEB-INF/lib 目录, /WEB-INF 将被表示为虚拟资源.
     *
     * @return <code>true</code>虚拟资源
     */
    boolean isVirtual();

    /**
     * @return {@link java.io.File#isDirectory()}.
     */
    boolean isDirectory();

    /**
     * @return {@link java.io.File#isFile()}.
     */
    boolean isFile();

    /**
     * @return {@link java.io.File#delete()}.
     */
    boolean delete();

    /**
     * @return {@link java.io.File#getName()}.
     */
    String getName();

    /**
     * @return {@link java.io.File#length()}.
     */
    long getContentLength();

    /**
     * @return {@link java.io.File#getCanonicalPath()}.
     */
    String getCanonicalPath();

    /**
     * @return {@link java.io.File#canRead()}.
     */
    boolean canRead();

    /**
     * @return 此资源相对于Web应用程序根目录的路径. 如果资源是一个目录, 返回值将以'/'结尾.
     */
    String getWebappPath();

    /**
     * 返回增强的ETag(当前不支持)，或者返回减弱的ETag, 从内容长度和最后修改计算的.
     *
     * @return  这个资源的ETag
     */
    String getETag();

    /**
     * 设置这个资源的MIME 类型.
     *
     * @param mimeType 与资源关联的MIME类型
     */
    void setMimeType(String mimeType);

    /**
     * @return 这个资源的MIME 类型.
     */
    String getMimeType();

    /**
     * 获取这个资源内容的InputStream.
     */
    InputStream getInputStream();

    /**
     * @return 此资源的缓存二进制内容.
     */
    byte[] getContent();

    /**
     * @return 创建文件的时间. 如果无效, 将返回{@link #getLastModified()}的结果.
     */
    long getCreation();

    /**
     * @return 访问资源的URL或<code>null</code>如果没有这样的URL可用，或者如果资源不存在.
     */
    URL getURL();

    /**
     * @return 将使用此资源的代码基础，在查找安全策略文件中的代码库的指定权限时，在安全管理器下运行时.
     */
    URL getCodeBase();

    WebResourceRoot getWebResourceRoot();

    /**
     * @return 用于签署此资源以验证其证书或 @null.
     */
    Certificate[] getCertificates();

    /**
     * @return 与此资源相关联的清单 or @null.
     */
    Manifest getManifest();
}
