package org.apache.catalina.webresources;

import java.util.jar.JarEntry;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.UriUtil;

/**
 * 表示WAR中一个单独的资源 (文件或目录).
 */
public class WarResource extends AbstractSingleArchiveResource {

    private static final Log log = LogFactory.getLog(WarResource.class);


    public WarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath,
            String baseUrl, JarEntry jarEntry) {
        super(archiveResourceSet, webAppPath, "war:" + baseUrl + UriUtil.getWarSeparator(),
                jarEntry, baseUrl);
    }


    @Override
    protected Log getLog() {
        return log;
    }
}
