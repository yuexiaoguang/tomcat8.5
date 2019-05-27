package org.apache.catalina.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.catalina.TrackedWebResource;
import org.apache.catalina.WebResourceRoot;

class TrackedInputStream extends InputStream implements TrackedWebResource {

    private final WebResourceRoot root;
    private final String name;
    private final InputStream is;
    private final Exception creation;

    TrackedInputStream(WebResourceRoot root, String name, InputStream is) {
        this.root = root;
        this.name = name;
        this.is = is;
        this.creation = new Exception();

        root.registerTrackedResource(this);
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
        root.deregisterTrackedResource(this);
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

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Exception getCreatedBy() {
        return creation;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        sw.append('[');
        sw.append(name);
        sw.append(']');
        sw.append(System.lineSeparator());
        creation.printStackTrace(pw);
        pw.flush();

        return sw.toString();
    }
}
