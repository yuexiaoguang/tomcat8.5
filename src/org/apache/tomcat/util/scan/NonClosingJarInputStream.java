package org.apache.tomcat.util.scan;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarInputStream;

/**
 * 与XML解析器一起使用{@link JarInputStream}时, 解析器将关闭流. 如果需要解析来自JAR的多个条目，这将导致问题.
 * 这个实现使{{@link #close()}成为NO-OP, 并添加{@link #reallyClose()}来关闭流.
 */
public class NonClosingJarInputStream extends JarInputStream {

    public NonClosingJarInputStream(InputStream in, boolean verify)
            throws IOException {
        super(in, verify);
    }

    public NonClosingJarInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    public void close() throws IOException {
        // 将此设置为NO-OP，以便从流中读取更多条目
    }

    public void reallyClose() throws IOException {
        super.close();
    }
}
