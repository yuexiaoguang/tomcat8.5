package org.apache.tomcat.util.scan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.tomcat.Jar;
import org.apache.tomcat.util.compat.JreCompat;

/**
 * {@link Jar}的实现, 对基于文件的jar URL进行优化，这些URL直接指向JAR文件 (e.g jar:file: ... .jar!/ 或 file:... .jar 格式的URL).
 */
public class JarFileUrlJar implements Jar {

    private final JarFile jarFile;
    private final URL jarFileURL;
    private final boolean multiRelease;
    private Enumeration<JarEntry> entries;
    private Set<String> entryNamesSeen;
    private JarEntry entry = null;

    public JarFileUrlJar(URL url, boolean startsWithJar) throws IOException {
        if (startsWithJar) {
            // jar:file:...
            JarURLConnection jarConn = (JarURLConnection) url.openConnection();
            jarConn.setUseCaches(false);
            jarFile = jarConn.getJarFile();
            jarFileURL = jarConn.getJarFileURL();
        } else {
            // file:...
            File f;
            try {
                f = new File(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
            jarFile = JreCompat.getInstance().jarFileNewInstance(f);
            jarFileURL = url;
        }
        multiRelease = JreCompat.getInstance().jarFileIsMultiRelease(jarFile);
    }


    @Override
    public URL getJarFileURL() {
        return jarFileURL;
    }


    @Override
    @Deprecated
    public boolean entryExists(String name) {
        return false;
    }

    @Override
    public InputStream getInputStream(String name) throws IOException {
        // JarFile#getEntry() is multi-release aware
        ZipEntry entry = jarFile.getEntry(name);
        if (entry == null) {
            return null;
        } else {
            return jarFile.getInputStream(entry);
        }
    }

    @Override
    public long getLastModified(String name) throws IOException {
        // JarFile#getEntry() is multi-release aware
        ZipEntry entry = jarFile.getEntry(name);
        if (entry == null) {
            return -1;
        } else {
            return entry.getTime();
        }
    }

    @Override
    public String getURL(String entry) {
        StringBuilder result = new StringBuilder("jar:");
        result.append(getJarFileURL().toExternalForm());
        result.append("!/");
        result.append(entry);

        return result.toString();
    }

    @Override
    public void close() {
        if (jarFile != null) {
            try {
                jarFile.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Override
    public void nextEntry() {
        // JarFile#entries() is NOT multi-release aware
        if (entries == null) {
            entries = jarFile.entries();
            if (multiRelease) {
                entryNamesSeen = new HashSet<>();
            }
        }

        if (multiRelease) {
            // 需要确保:
            // - 第一, 在多个版本可用的情况下返回正确的条目
            // - jar中的条目顺序不能阻止正确的条目返回
            // - 一个条目出现在版本位置但不在基位置中的情况被正确处理

            // 枚举条目，直到到达一个表示以前未被看到的条目为止.
            String name = null;
            while (true) {
                if (entries.hasMoreElements()) {
                    entry = entries.nextElement();
                    name = entry.getName();
                    // Get 'base' name
                    if (name.startsWith("META-INF/versions/")) {
                        int i = name.indexOf('/', 18);
                        if (i == -1) {
                            continue;
                        }
                        name = name.substring(i + 1);
                    }
                    if (name.length() == 0 || entryNamesSeen.contains(name)) {
                        continue;
                    }

                    entryNamesSeen.add(name);

                    // JarFile.getJarEntry is version aware so use it
                    entry = jarFile.getJarEntry(entry.getName());
                    break;
                } else {
                    entry = null;
                    break;
                }
            }
        } else {
            if (entries.hasMoreElements()) {
                entry = entries.nextElement();
            } else {
                entry = null;
            }
        }
    }

    @Override
    public String getEntryName() {
        if (entry == null) {
            return null;
        } else {
            return entry.getName();
        }
    }

    @Override
    public InputStream getEntryInputStream() throws IOException {
        if (entry == null) {
            return null;
        } else {
            return jarFile.getInputStream(entry);
        }
    }

    @Override
    public Manifest getManifest() throws IOException {
        return jarFile.getManifest();
    }


    @Override
    public void reset() throws IOException {
        entries = null;
        entryNamesSeen = null;
        entry = null;
    }
}
