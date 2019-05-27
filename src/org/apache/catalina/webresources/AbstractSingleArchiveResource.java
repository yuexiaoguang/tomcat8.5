package org.apache.catalina.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public abstract class AbstractSingleArchiveResource extends AbstractArchiveResource {

    protected AbstractSingleArchiveResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath,
            String baseUrl, JarEntry jarEntry, String codeBaseUrl) {
        super(archiveResourceSet, webAppPath, baseUrl, jarEntry, codeBaseUrl);
    }


    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        JarFile jarFile = null;
        try {
            jarFile = getArchiveResourceSet().openJarFile();
            // 需要创建一个新的 JarEntry, 所以可以读取证书
            JarEntry jarEntry = jarFile.getJarEntry(getResource().getName());
            InputStream is = jarFile.getInputStream(jarEntry);
            return new JarInputStreamWrapper(jarEntry, is);
        } catch (IOException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("jarResource.getInputStreamFail",
                        getResource().getName(), getBaseUrl()), e);
            }
            if (jarFile != null) {
                getArchiveResourceSet().closeJarFile();
            }
            return null;
        }
    }
}
