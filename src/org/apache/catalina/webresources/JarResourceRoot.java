package org.apache.catalina.webresources;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class JarResourceRoot extends AbstractResource {

    private static final Log log = LogFactory.getLog(JarResourceRoot.class);

    private final File base;
    private final String baseUrl;
    private final String name;

    public JarResourceRoot(WebResourceRoot root, File base, String baseUrl,
            String webAppPath) {
        super(root, webAppPath);
        // 先验证 webAppPath
        if (!webAppPath.endsWith("/")) {
            throw new IllegalArgumentException(sm.getString(
                    "jarResourceRoot.invalidWebAppPath", webAppPath));
        }
        this.base = base;
        this.baseUrl = "jar:" + baseUrl;
        // 从 webAppPath 提取名称
        // 去掉最后的 '/' 字符
        String resourceName = webAppPath.substring(0, webAppPath.length() - 1);
        int i = resourceName.lastIndexOf('/');
        if (i > -1) {
            resourceName = resourceName.substring(i + 1);
        }
        name = resourceName;
    }

    @Override
    public long getLastModified() {
        return base.lastModified();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public String getCanonicalPath() {
        return null;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    protected InputStream doGetInputStream() {
        return null;
    }

    @Override
    public byte[] getContent() {
        return null;
    }

    @Override
    public long getCreation() {
        return base.lastModified();
    }

    @Override
    public URL getURL() {
        String url = baseUrl + "!/";
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("fileResource.getUrlFail", url), e);
            }
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        try {
            return new URL(baseUrl);
        } catch (MalformedURLException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("fileResource.getUrlFail", baseUrl), e);
            }
            return null;
        }
    }
    @Override
    protected Log getLog() {
        return log;
    }

    @Override
    public Certificate[] getCertificates() {
        return null;
    }

    @Override
    public Manifest getManifest() {
        return null;
    }
}
