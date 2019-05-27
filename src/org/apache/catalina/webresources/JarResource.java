package org.apache.catalina.webresources;

import java.util.jar.JarEntry;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 表示JAR中一个 单独的 (文件或目录).
 */
public class JarResource extends AbstractSingleArchiveResource {

    private static final Log log = LogFactory.getLog(JarResource.class);


    public JarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath,
            String baseUrl, JarEntry jarEntry) {
        super(archiveResourceSet, webAppPath, "jar:" + baseUrl + "!/", jarEntry, baseUrl);
    }


    @Override
    protected Log getLog() {
        return log;
    }
}
