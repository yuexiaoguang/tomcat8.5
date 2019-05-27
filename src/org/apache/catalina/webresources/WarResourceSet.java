package org.apache.catalina.webresources;

import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

/**
 * 基于一个WAR文件, 表示一个 {@link org.apache.catalina.WebResourceSet}.
 */
public class WarResourceSet extends AbstractSingleArchiveResourceSet {

    public WarResourceSet() {
    }


    /**
     * 基于一个WAR文件, 创建一个新的 {@link org.apache.catalina.WebResourceSet}.
     *
     * @param root          这个WebResourceSet将会被添加到的{@link WebResourceRoot}
     * @param webAppMount   这个{@link WebResourceSet}将会被安装的Web应用程序中的路径.
     * @param base          文件系统上的WAR文件的绝对路径，资源将从其中服务.
     *
     * @throws IllegalArgumentException 如果webAppMount 无效 (有效的路径必须以 '/' 开头)
     */
    public WarResourceSet(WebResourceRoot root, String webAppMount, String base)
            throws IllegalArgumentException {
        super(root, webAppMount, base, "/");
    }


    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest) {
        return new WarResource(this, webAppPath, getBaseUrlString(), jarEntry);
    }
}
