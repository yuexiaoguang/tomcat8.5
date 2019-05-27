package org.apache.catalina.webresources;

import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

/**
 * 基于一个JAR文件, 表示一个 {@link org.apache.catalina.WebResourceSet}.
 */
public class JarResourceSet extends AbstractSingleArchiveResourceSet {

    public JarResourceSet() {
    }


    /**
     * 基于一个JAR文件, 创建一个新的 {@link org.apache.catalina.WebResourceSet}.
     *
     * @param root          新{@link org.apache.catalina.WebResourceSet}将会被添加到的{@link WebResourceRoot}
     * @param webAppMount   这个{@link org.apache.catalina.WebResourceSet}将会被安装的Web应用程序中的路径.
     * @param base          文件系统上的JAR文件的绝对路径，资源将从其中服务.
     * @param internalPath  新{@link org.apache.catalina.WebResourceSet}中资源的路径. E.g. 对于一个资源 JAR, 将会是 "META-INF/resources"
     *
     * @throws IllegalArgumentException 如果 webAppMount 或 internalPath 无效 (有效的路径必须以 '/' 开头)
     */
    public JarResourceSet(WebResourceRoot root, String webAppMount, String base,
            String internalPath) throws IllegalArgumentException {
        super(root, webAppMount, base, internalPath);
    }


    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest) {
        return new JarResource(this, webAppPath, getBaseUrlString(), jarEntry);
    }
}
