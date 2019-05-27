package org.apache.tomcat.util.scan;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

import org.apache.tomcat.Jar;
import org.apache.tomcat.util.compat.JreCompat;

/**
 * Jar的基本实现，用于使用JarInputStream访问Jar文件的实现.
 */
public abstract class AbstractInputStreamJar implements Jar {

    private final URL jarFileURL;

    private NonClosingJarInputStream jarInputStream = null;
    private JarEntry entry = null;
    private Boolean multiRelease = null;
    private Map<String,String> mrMap = null;

    public AbstractInputStreamJar(URL jarFileUrl) {
        this.jarFileURL = jarFileUrl;
    }


    @Override
    public URL getJarFileURL() {
        return jarFileURL;
    }


    @Override
    public void nextEntry() {
        if (jarInputStream == null) {
            try {
                reset();
            } catch (IOException e) {
                entry = null;
                return;
            }
        }
        try {
            entry = jarInputStream.getNextJarEntry();
            if (multiRelease.booleanValue()) {
                // 跳过有多发行版条目的基本条目
                // 跳过未使用的多发行版条目
                while (entry != null &&
                        (mrMap.keySet().contains(entry.getName()) ||
                                entry.getName().startsWith("META-INF/versions/") &&
                                !mrMap.values().contains(entry.getName()))) {
                    entry = jarInputStream.getNextJarEntry();
                }
            } else {
                // 跳过multi-release条目
                while (entry != null && entry.getName().startsWith("META-INF/versions/")) {
                    entry = jarInputStream.getNextJarEntry();
                }
            }
        } catch (IOException ioe) {
            entry = null;
        }
    }


    @Override
    public String getEntryName() {
        // 给定条目名称的使用方式, 不需要将多版本条目的名称转换为相应的基本名称.
        if (entry == null) {
            return null;
        } else {
            return entry.getName();
        }
    }


    @Override
    public InputStream getEntryInputStream() throws IOException {
        return jarInputStream;
    }


    @Override
    @Deprecated
    public boolean entryExists(String name) throws IOException {
        return false;
    }


    @Override
    public InputStream getInputStream(String name) throws IOException {
        gotoEntry(name);
        if (entry == null) {
            return null;
        } else {
            // 清除条目，因此对同一条目多次调用该方法将导致每次调用都产生新的InputStream
            // (BZ 60798)
            entry = null;
            return jarInputStream;
        }
    }


    @Override
    public long getLastModified(String name) throws IOException {
        gotoEntry(name);
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
    public Manifest getManifest() throws IOException {
        reset();
        return jarInputStream.getManifest();
    }


    @Override
    public void reset() throws IOException {
        closeStream();
        entry = null;
        jarInputStream = createJarInputStream();
        // 只在第一次访问时执行多版本处理
        if (multiRelease == null) {
            if (JreCompat.isJre9Available()) {
                Manifest manifest = jarInputStream.getManifest();
                if (manifest == null) {
                    multiRelease = Boolean.FALSE;
                } else {
                    String mrValue = manifest.getMainAttributes().getValue("Multi-Release");
                    if (mrValue == null) {
                        multiRelease = Boolean.FALSE;
                    } else {
                        multiRelease = Boolean.valueOf(mrValue);
                    }
                }
            } else {
                multiRelease = Boolean.FALSE;
            }
            if (multiRelease.booleanValue()) {
                if (mrMap == null) {
                    populateMrMap();
                }
            }
        }
    }


    protected void closeStream() {
        if (jarInputStream != null) {
            try {
                jarInputStream.reallyClose();
            } catch (IOException ioe) {
                // Ignore
            }
        }
    }


    protected abstract NonClosingJarInputStream createJarInputStream() throws IOException;


    private void gotoEntry(String name) throws IOException {
        boolean needsReset = true;
        if (multiRelease == null) {
            reset();
            needsReset = false;
        }

        // 需要将请求的名称转换为多版本名称 (如果存在)
        if (multiRelease.booleanValue()) {
            String mrName = mrMap.get(name);
            if (mrName != null) {
                name = mrName;
            }
        } else if (name.startsWith("META-INF/versions/")) {
            entry = null;
            return;
        }

        if (entry != null && name.equals(entry.getName())) {
            return;
        }
        if (needsReset) {
            reset();
        }

        JarEntry jarEntry = jarInputStream.getNextJarEntry();
        while (jarEntry != null) {
            if (name.equals(jarEntry.getName())) {
                entry = jarEntry;
                break;
            }
            jarEntry = jarInputStream.getNextJarEntry();
        }
    }


    private void populateMrMap() throws IOException {
        int targetVersion = JreCompat.getInstance().jarFileRuntimeMajorVersion();

        Map<String,Integer> mrVersions = new HashMap<>();

        JarEntry jarEntry = jarInputStream.getNextJarEntry();

        // 跟踪基名称和找到的最新有效版本就足以创建所需的重命名映射
        while (jarEntry != null) {
            String name = jarEntry.getName();
            if (name.startsWith("META-INF/versions/") && name.endsWith(".class")) {

                // 获取这个版本条目的基本名称和版本
                int i = name.indexOf('/', 18);
                if (i > 0) {
                    String baseName = name.substring(i + 1);
                    int version = Integer.parseInt(name.substring(18, i));

                    // 忽略针对此运行时的目标版本后面版本的任何条目
                    if (version <= targetVersion) {
                        Integer mappedVersion = mrVersions.get(baseName);
                        if (mappedVersion == null) {
                            // 没有找到这个名称的版本. Create one.
                            mrVersions.put(baseName, Integer.valueOf(version));
                        } else {
                            // 忽略任何已经找到的后续版本的条目
                            if (version > mappedVersion.intValue()) {
                                // 替换早期版本
                                mrVersions.put(baseName, Integer.valueOf(version));
                            }
                        }
                    }
                }
            }
            jarEntry = jarInputStream.getNextJarEntry();
        }

        mrMap = new HashMap<>();

        for (Entry<String,Integer> mrVersion : mrVersions.entrySet()) {
            mrMap.put(mrVersion.getKey() , "META-INF/versions/" + mrVersion.getValue().toString() +
                    "/" +  mrVersion.getKey());
        }

        // 重置流, 回到JAR的开始
        closeStream();
        jarInputStream = createJarInputStream();
    }
}
