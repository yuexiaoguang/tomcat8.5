package org.apache.tomcat.util.descriptor.tld;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

import org.apache.tomcat.Jar;
import org.apache.tomcat.util.scan.JarFactory;

/**
 * JSP 7.3.2中定义的TLD资源路径.
 * <p>
 * 这封装了对可以位于不同位置的标记库描述符的引用:
 * <ul>
 * <li>作为应用程序内的资源</li>
 * <li>作为应用程序中包含的JAR文件中的条目</li>
 * <li>作为容器提供的资源</li>
 * </ul>
 * 配置从众所周知的URI到TLD的映射时, 允许用户指定在<code>META-INF/taglib.tld</code>中隐式包含TLD的JAR文件的名称.
 * 使用此实现时，必须将此类映射显式转换为URL和entryName.
 */
public class TldResourcePath {
    private final URL url;
    private final String webappPath;
    private final String entryName;

    /**
     * @param url        TLD的位置
     * @param webappPath TLD的Web应用程序路径
     */
    public TldResourcePath(URL url, String webappPath) {
        this(url, webappPath, null);
    }

    /**
     * @param url        JAR的位置
     * @param webappPath JAR的Web应用程序路径
     * @param entryName  JAR中条目的名称
     */
    public TldResourcePath(URL url, String webappPath, String entryName) {
        this.url = url;
        this.webappPath = webappPath;
        this.entryName = entryName;
    }

    /**
     * 返回TLD或包含TLD的JAR的URL.
     *
     * @return TLD的URL
     */
    public URL getUrl() {
        return url;
    }

    /**
     * 返回Web应用程序中获取{@link #getUrl()}返回的资源的路径.
     *
     * @return Web应用程序路径; 或 @null 如果资源不在Web应用程序中
     */
    public String getWebappPath() {
        return webappPath;
    }

    /**
     * 返回包含TLD的JAR条目的名称.
     * 可以为null，表示URL直接指向TLD本身.
     *
     * @return 包含TLD的JAR条目的名称
     */
    public String getEntryName() {
        return entryName;
    }

    /**
     * 返回代表此TLD的URL的外部形式.
     * 这可以用作TLD本身的规范位置, 例如, 作为解析其XML时使用的systemId.
     *
     * @return 表示此TLD的URL的外部形式
     */
    public String toExternalForm() {
        if (entryName == null) {
            return url.toExternalForm();
        } else {
            return "jar:" + url.toExternalForm() + "!/" + entryName;
        }
    }

    /**
     * 打开流以访问TLD.
     *
     * @return 包含TLD内容的流
     * @throws IOException 如果打开流有问题
     */
    public InputStream openStream() throws IOException {
        if (entryName == null) {
            return url.openStream();
        } else {
            URL entryUrl = JarFactory.getJarEntryURL(url, entryName);
            return entryUrl.openStream();
        }
    }

    public Jar openJar() throws IOException {
        if (entryName == null) {
            return null;
        } else {
            return JarFactory.newInstance(url);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TldResourcePath other = (TldResourcePath) o;

        return url.equals(other.url) &&
                Objects.equals(webappPath, other.webappPath) &&
                Objects.equals(entryName, other.entryName);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = result * 31 + Objects.hashCode(webappPath);
        result = result * 31 + Objects.hashCode(entryName);
        return result;
    }
}
