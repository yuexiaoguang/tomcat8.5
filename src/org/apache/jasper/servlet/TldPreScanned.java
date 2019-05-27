package org.apache.jasper.servlet;

import java.net.URL;
import java.util.Collection;

import javax.servlet.ServletContext;

import org.apache.tomcat.util.descriptor.tld.TldResourcePath;

public class TldPreScanned extends TldScanner {

    private final Collection<URL> preScannedURLs;

    public TldPreScanned (ServletContext context, boolean namespaceAware, boolean validation,
            boolean blockExternal, Collection<URL> preScannedTlds) {
        super(context, namespaceAware, validation, blockExternal);
        preScannedURLs = preScannedTlds;
    }

    @Override
    public void scanJars() {
        for (URL url : preScannedURLs){
            String str = url.toExternalForm();
            int a = str.indexOf("jar:");
            int b = str.indexOf("!/");
            if (a >= 0 && b> 0) {
                String fileUrl = str.substring(a + 4, b);
                String path = str.substring(b + 2);
                try {
                    parseTld(new TldResourcePath(new URL(fileUrl), null, path));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else {
                throw new IllegalStateException("Bad tld url: "+str);
            }
        }
    }
}