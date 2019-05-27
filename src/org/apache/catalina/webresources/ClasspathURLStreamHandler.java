package org.apache.catalina.webresources;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.apache.tomcat.util.res.StringManager;

public class ClasspathURLStreamHandler extends URLStreamHandler {

    private static final StringManager sm =
            StringManager.getManager(ClasspathURLStreamHandler.class);


    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        String path = u.getPath();

        // Thread context class loader first
        URL classpathUrl = Thread.currentThread().getContextClassLoader().getResource(path);
        if (classpathUrl == null) {
            // This class's class loader if no joy with the tccl
            classpathUrl = ClasspathURLStreamHandler.class.getResource(path);
        }

        if (classpathUrl == null) {
            throw new FileNotFoundException(sm.getString("classpathUrlStreamHandler.notFound", u));
        }

        return classpathUrl.openConnection();
    }
}
