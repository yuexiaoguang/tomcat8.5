package org.apache.tomcat.util.scan;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * {@link org.apache.tomcat.Jar}的实现, 这是针对基于文件的JAR url进行优化的, 这些JAR url指的是嵌套在WAR中的JAR文件
 * (e.g jar:file: ... .war!/ ... .jar 格式的URL).
 */
public class JarFileUrlNestedJar extends AbstractInputStreamJar {

    private final JarFile warFile;
    private final JarEntry jarEntry;

    public JarFileUrlNestedJar(URL url) throws IOException {
        super(url);
        JarURLConnection jarConn = (JarURLConnection) url.openConnection();
        jarConn.setUseCaches(false);
        warFile = jarConn.getJarFile();

        String urlAsString = url.toString();
        int pathStart = urlAsString.indexOf("!/") + 2;
        String jarPath = urlAsString.substring(pathStart);
        jarEntry = warFile.getJarEntry(jarPath);
    }


    @Override
    public void close() {
        closeStream();
        if (warFile != null) {
            try {
                warFile.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }


    @Override
    protected NonClosingJarInputStream createJarInputStream() throws IOException {
        return new NonClosingJarInputStream(warFile.getInputStream(jarEntry));
    }
}
