package org.apache.catalina.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

public abstract class AbstractArchiveResource extends AbstractResource {

    private final AbstractArchiveResourceSet archiveResourceSet;
    private final String baseUrl;
    private final JarEntry resource;
    private final String codeBaseUrl;
    private final String name;
    private boolean readCerts = false;
    private Certificate[] certificates;

    protected AbstractArchiveResource(AbstractArchiveResourceSet archiveResourceSet,
            String webAppPath, String baseUrl, JarEntry jarEntry, String codeBaseUrl) {
        super(archiveResourceSet.getRoot(), webAppPath);
        this.archiveResourceSet = archiveResourceSet;
        this.baseUrl = baseUrl;
        this.resource = jarEntry;
        this.codeBaseUrl = codeBaseUrl;

        String resourceName = resource.getName();
        if (resourceName.charAt(resourceName.length() - 1) == '/') {
            resourceName = resourceName.substring(0, resourceName.length() - 1);
        }
        String internalPath = archiveResourceSet.getInternalPath();
        if (internalPath.length() > 0 && resourceName.equals(
                internalPath.subSequence(1, internalPath.length()))) {
            name = "";
        } else {
            int index = resourceName.lastIndexOf('/');
            if (index == -1) {
                name = resourceName;
            } else {
                name = resourceName.substring(index + 1);
            }
        }
    }

    protected AbstractArchiveResourceSet getArchiveResourceSet() {
        return archiveResourceSet;
    }

    protected final String getBase() {
        return archiveResourceSet.getBase();
    }

    protected final String getBaseUrl() {
        return baseUrl;
    }

    protected final JarEntry getResource() {
        return resource;
    }

    @Override
    public long getLastModified() {
        return resource.getTime();
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
        return resource.isDirectory();
    }

    @Override
    public boolean isFile() {
        return !resource.isDirectory();
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
        if (isDirectory()) {
            return -1;
        }
        return resource.getSize();
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
    public long getCreation() {
        return resource.getTime();
    }

    @Override
    public URL getURL() {
        String url = baseUrl + resource.getName();
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("fileResource.getUrlFail", url), e);
            }
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        try {
            return new URL(codeBaseUrl);
        } catch (MalformedURLException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("fileResource.getUrlFail", codeBaseUrl), e);
            }
            return null;
        }
    }

    @Override
    public final byte[] getContent() {
        long len = getContentLength();

        if (len > Integer.MAX_VALUE) {
            // 不能创建一个大数组
            throw new ArrayIndexOutOfBoundsException(sm.getString(
                    "abstractResource.getContentTooLarge", getWebappPath(),
                    Long.valueOf(len)));
        }

        if (len < 0) {
            // 内容不适用于此 (e.g. 是一个目录)
            return null;
        }

        int size = (int) len;
        byte[] result = new byte[size];

        int pos = 0;
        try (JarInputStreamWrapper jisw = getJarInputStreamWrapper()) {
            if (jisw == null) {
                // 发生错误, 不要返回损坏的内容
                return null;
            }
            while (pos < size) {
                int n = jisw.read(result, pos, size - pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
            // 一旦读取流, 读取证书
            certificates = jisw.getCertificates();
            readCerts = true;
        } catch (IOException ioe) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractResource.getContentFail",
                        getWebappPath()), ioe);
            }
            // 不要返回损坏的内容
            return null;
        }

        return result;
    }


    @Override
    public Certificate[] getCertificates() {
        if (!readCerts) {
            // TODO - get content first
            throw new IllegalStateException();
        }
        return certificates;
    }

    @Override
    public Manifest getManifest() {
        return archiveResourceSet.getManifest();
    }

    @Override
    protected final InputStream doGetInputStream() {
        if (isDirectory()) {
            return null;
        }
        return getJarInputStreamWrapper();
    }

    protected abstract JarInputStreamWrapper getJarInputStreamWrapper();

    /**
     * 这个封装假设 InputStream 是从 JarFile创建的, JarFile是通过调用 getArchiveResourceSet().openJarFile()获取的.
     * 如果不是这种情况, 那么在 AbstractArchiveResourceSet 中的使用计数将中断, 而且 JarFile 将会被关闭.
     */
    protected class JarInputStreamWrapper extends InputStream {

        private final JarEntry jarEntry;
        private final InputStream is;
        private final AtomicBoolean closed = new AtomicBoolean(false);


        public JarInputStreamWrapper(JarEntry jarEntry, InputStream is) {
            this.jarEntry = jarEntry;
            this.is = is;
        }


        @Override
        public int read() throws IOException {
            return is.read();
        }


        @Override
        public int read(byte[] b) throws IOException {
            return is.read(b);
        }


        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }


        @Override
        public long skip(long n) throws IOException {
            return is.skip(n);
        }


        @Override
        public int available() throws IOException {
            return is.available();
        }


        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                // 只需调用此一次，使用计数就会中断
                archiveResourceSet.closeJarFile();
            }
            is.close();
        }


        @Override
        public synchronized void mark(int readlimit) {
            is.mark(readlimit);
        }


        @Override
        public synchronized void reset() throws IOException {
            is.reset();
        }


        @Override
        public boolean markSupported() {
            return is.markSupported();
        }

        public Certificate[] getCertificates() {
            return jarEntry.getCertificates();
        }
    }
}
