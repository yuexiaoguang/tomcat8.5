package org.apache.catalina.webresources;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

/**
 * 用来包装一个 'raw' WebResource 并为昂贵的操作提供缓存. 廉价的操作可以传递到底层资源.
 */
public class CachedResource implements WebResource {

    // 基于分析器数据的不包括内容的平均大小.
    private static final long CACHE_ENTRY_SIZE = 500;

    private final Cache cache;
    private final StandardRoot root;
    private final String webAppPath;
    private final long ttl;
    private final int objectMaxSizeBytes;

    private volatile WebResource webResource;
    private volatile WebResource[] webResources;
    private volatile long nextCheck;

    private volatile Long cachedLastModified = null;
    private volatile String cachedLastModifiedHttp = null;
    private volatile byte[] cachedContent = null;
    private volatile Boolean cachedIsFile = null;
    private volatile Boolean cachedIsDirectory = null;
    private volatile Boolean cachedExists = null;
    private volatile Boolean cachedIsVirtual = null;
    private volatile Long cachedContentLength = null;


    public CachedResource(Cache cache, StandardRoot root, String path, long ttl,
            int objectMaxSizeBytes) {
        this.cache = cache;
        this.root = root;
        this.webAppPath = path;
        this.ttl = ttl;
        this.objectMaxSizeBytes = objectMaxSizeBytes;
    }

    protected boolean validateResource(boolean useClassLoaderResources) {
        long now = System.currentTimeMillis();

        if (webResource == null) {
            synchronized (this) {
                if (webResource == null) {
                    webResource = root.getResourceInternal(
                            webAppPath, useClassLoaderResources);
                    getLastModified();
                    getContentLength();
                    nextCheck = ttl + now;
                    // exists() 是一个相对昂贵的文件检查, 所以使用这个
                    if (webResource instanceof EmptyResource) {
                        cachedExists = Boolean.FALSE;
                    } else {
                        cachedExists = Boolean.TRUE;
                    }
                    return true;
                }
            }
        }

        if (now < nextCheck) {
            return true;
        }

        // 假设WAR中的资源不会改变
        if (!root.isPackedWarFile()) {
            WebResource webResourceInternal = root.getResourceInternal(
                    webAppPath, useClassLoaderResources);
            if (!webResource.exists() && webResourceInternal.exists()) {
                return false;
            }

            // 如果更改日期或长度更改 - 资源已更改/删除等.
            if (webResource.getLastModified() != getLastModified() ||
                    webResource.getContentLength() != getContentLength()) {
                return false;
            }

            // 在不同的资源集中插入/删除资源
            if (webResource.getLastModified() != webResourceInternal.getLastModified() ||
                    webResource.getContentLength() != webResourceInternal.getContentLength()) {
                return false;
            }
        }

        nextCheck = ttl + now;
        return true;
    }

    protected boolean validateResources(boolean useClassLoaderResources) {
        long now = System.currentTimeMillis();

        if (webResources == null) {
            synchronized (this) {
                if (webResources == null) {
                    webResources = root.getResourcesInternal(
                            webAppPath, useClassLoaderResources);
                    nextCheck = ttl + now;
                    return true;
                }
            }
        }

        if (now < nextCheck) {
            return true;
        }

        // 假设WAR中的资源不会改变
        if (root.isPackedWarFile()) {
            nextCheck = ttl + now;
            return true;
        } else {
            // 在这一点上, 总是将条目过期并重新填充它可能会像验证它一样昂贵.
            return false;
        }
    }

    protected long getNextCheck() {
        return nextCheck;
    }

    @Override
    public long getLastModified() {
        Long cachedLastModified = this.cachedLastModified;
        if (cachedLastModified == null) {
            cachedLastModified =
                    Long.valueOf(webResource.getLastModified());
            this.cachedLastModified = cachedLastModified;
        }
        return cachedLastModified.longValue();
    }

    @Override
    public String getLastModifiedHttp() {
        String cachedLastModifiedHttp = this.cachedLastModifiedHttp;
        if (cachedLastModifiedHttp == null) {
            cachedLastModifiedHttp = webResource.getLastModifiedHttp();
            this.cachedLastModifiedHttp = cachedLastModifiedHttp;
        }
        return cachedLastModifiedHttp;
    }

    @Override
    public boolean exists() {
        Boolean cachedExists = this.cachedExists;
        if (cachedExists == null) {
            cachedExists = Boolean.valueOf(webResource.exists());
            this.cachedExists = cachedExists;
        }
        return cachedExists.booleanValue();
    }

    @Override
    public boolean isVirtual() {
        Boolean cachedIsVirtual = this.cachedIsVirtual;
        if (cachedIsVirtual == null) {
            cachedIsVirtual = Boolean.valueOf(webResource.isVirtual());
            this.cachedIsVirtual = cachedIsVirtual;
        }
        return cachedIsVirtual.booleanValue();
    }

    @Override
    public boolean isDirectory() {
        Boolean cachedIsDirectory = this.cachedIsDirectory;
        if (cachedIsDirectory == null) {
            cachedIsDirectory = Boolean.valueOf(webResource.isDirectory());
            this.cachedIsDirectory = cachedIsDirectory;
        }
        return cachedIsDirectory.booleanValue();
    }

    @Override
    public boolean isFile() {
        Boolean cachedIsFile = this.cachedIsFile;
        if (cachedIsFile == null) {
            cachedIsFile = Boolean.valueOf(webResource.isFile());
            this.cachedIsFile = cachedIsFile;
        }
        return cachedIsFile.booleanValue();
    }

    @Override
    public boolean delete() {
        boolean deleteResult = webResource.delete();
        if (deleteResult) {
            cache.removeCacheEntry(webAppPath);
        }
        return deleteResult;
    }

    @Override
    public String getName() {
        return webResource.getName();
    }

    @Override
    public long getContentLength() {
        Long cachedContentLength = this.cachedContentLength;
        if (cachedContentLength == null) {
            long result = 0;
            if (webResource != null) {
                result = webResource.getContentLength();
                cachedContentLength = Long.valueOf(result);
                this.cachedContentLength = cachedContentLength;
            }
            return result;
        }
        return cachedContentLength.longValue();
    }

    @Override
    public String getCanonicalPath() {
        return webResource.getCanonicalPath();
    }

    @Override
    public boolean canRead() {
        return webResource.canRead();
    }

    @Override
    public String getWebappPath() {
        return webAppPath;
    }

    @Override
    public String getETag() {
        return webResource.getETag();
    }

    @Override
    public void setMimeType(String mimeType) {
        webResource.setMimeType(mimeType);
    }

    @Override
    public String getMimeType() {
        return webResource.getMimeType();
    }

    @Override
    public InputStream getInputStream() {
        byte[] content = getContent();
        if (content == null) {
            // 不能缓存 InputStreams
            return webResource.getInputStream();
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public byte[] getContent() {
        byte[] cachedContent = this.cachedContent;
        if (cachedContent == null) {
            if (getContentLength() > objectMaxSizeBytes) {
                return null;
            }
            cachedContent = webResource.getContent();
            this.cachedContent = cachedContent;
        }
        return cachedContent;
    }

    @Override
    public long getCreation() {
        return webResource.getCreation();
    }

    @Override
    public URL getURL() {
        return webResource.getURL();
    }

    @Override
    public URL getCodeBase() {
        return webResource.getCodeBase();
    }

    @Override
    public Certificate[] getCertificates() {
        return webResource.getCertificates();
    }

    @Override
    public Manifest getManifest() {
        return webResource.getManifest();
    }

    @Override
    public WebResourceRoot getWebResourceRoot() {
        return webResource.getWebResourceRoot();
    }

    WebResource getWebResource() {
        return webResource;
    }

    WebResource[] getWebResources() {
        return webResources;
    }

    // 假设缓存条目总是包含内容, 除非资源内容大于 objectMaxSizeBytes. 情况并非总是如此, 但它更容易跟踪当前的缓存大小.
    long getSize() {
        long result = CACHE_ENTRY_SIZE;
        if (getContentLength() <= objectMaxSizeBytes) {
            result += getContentLength();
        }
        return result;
    }
}
