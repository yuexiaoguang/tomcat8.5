package org.apache.catalina.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.UriUtil;

/**
 * 表示WAR文件中的JAR中一个单独的资源 (文件或目录).
 */
public class JarWarResource extends AbstractArchiveResource {

    private static final Log log = LogFactory.getLog(JarWarResource.class);

    private final String archivePath;

    public JarWarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath,
            String baseUrl, JarEntry jarEntry, String archivePath) {

        super(archiveResourceSet, webAppPath,
                "jar:war:" + baseUrl + UriUtil.getWarSeparator() + archivePath + "!/",
                jarEntry, "war:" + baseUrl + UriUtil.getWarSeparator() + archivePath);
        this.archivePath = archivePath;
    }

    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        JarFile warFile = null;
        JarInputStream jarIs = null;
        JarEntry entry = null;
        try {
            warFile = getArchiveResourceSet().openJarFile();
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream isInWar = warFile.getInputStream(jarFileInWar);

            jarIs = new JarInputStream(isInWar);
            entry = jarIs.getNextJarEntry();
            while (entry != null &&
                    !entry.getName().equals(getResource().getName())) {
                entry = jarIs.getNextJarEntry();
            }

            if (entry == null) {
                return null;
            }

            return new JarInputStreamWrapper(entry, jarIs);
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jarResource.getInputStreamFail",
                        getResource().getName(), getBaseUrl()), e);
            }
            return null;
        } finally {
            if (entry == null) {
                if (jarIs != null) {
                    try {
                        jarIs.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
                if (warFile != null) {
                    getArchiveResourceSet().closeJarFile();
                }
            }
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }
}
