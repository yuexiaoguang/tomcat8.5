package org.apache.catalina.webresources;

import java.io.InputStream;
import java.util.Date;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ConcurrentDateFormat;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractResource implements WebResource {

    protected static final StringManager sm = StringManager.getManager(AbstractResource.class);

    private final WebResourceRoot root;
    private final String webAppPath;

    private String mimeType = null;
    private volatile String weakETag;


    protected AbstractResource(WebResourceRoot root, String webAppPath) {
        this.root = root;
        this.webAppPath = webAppPath;
    }


    @Override
    public final WebResourceRoot getWebResourceRoot() {
        return root;
    }


    @Override
    public final String getWebappPath() {
        return webAppPath;
    }


    @Override
    public final String getLastModifiedHttp() {
        return ConcurrentDateFormat.formatRfc1123(new Date(getLastModified()));
    }

    @Override
    public final String getETag() {
        if (weakETag == null) {
            synchronized (this) {
                if (weakETag == null) {
                    long contentLength = getContentLength();
                    long lastModified = getLastModified();
                    if ((contentLength >= 0) || (lastModified >= 0)) {
                        weakETag = "W/\"" + contentLength + "-" +
                                   lastModified + "\"";
                    }
                }
            }
        }
        return weakETag;
    }

    @Override
    public final void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }


    @Override
    public final String getMimeType() {
        return mimeType;
    }


    @Override
    public final InputStream getInputStream() {
        InputStream is = doGetInputStream();

        if (is == null || !root.getTrackLockedFiles()) {
            return is;
        }

        return new TrackedInputStream(root, getName(), is);
    }

    protected abstract InputStream doGetInputStream();


    protected abstract Log getLog();
}
