package org.apache.catalina.webresources.war;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;

import org.apache.tomcat.util.buf.UriUtil;


public class WarURLConnection extends URLConnection {

    private final URLConnection wrappedJarUrlConnection;
    private boolean connected;

    protected WarURLConnection(URL url) throws IOException {
        super(url);
        URL innerJarUrl = UriUtil.warToJar(url);
        wrappedJarUrlConnection = innerJarUrl.openConnection();
    }


    @Override
    public void connect() throws IOException {
        if (!connected) {
            wrappedJarUrlConnection.connect();
            connected = true;
        }
    }


    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return wrappedJarUrlConnection.getInputStream();
    }


    @Override
    public Permission getPermission() throws IOException {
        return wrappedJarUrlConnection.getPermission();
    }


    @Override
    public long getLastModified() {
        return wrappedJarUrlConnection.getLastModified();
    }
}
