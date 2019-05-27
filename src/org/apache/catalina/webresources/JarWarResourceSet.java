package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.compat.JreCompat;

/**
 * 基于一个嵌套进打包的WAR文件中的JAR文件, 表示一个{@link org.apache.catalina.WebResourceSet}.
 * 这仅用于Tomcat中的内部使用，因此不能通过配置创建.
 */
public class JarWarResourceSet extends AbstractArchiveResourceSet {

    private final String archivePath;

    /**
     * 基于一个嵌套进打包的WAR文件中的JAR文件, 创建一个新的{@link org.apache.catalina.WebResourceSet}.
     *
     * @param root          新{@link org.apache.catalina.WebResourceSet}将会被添加到的{@link WebResourceRoot}.
     * @param webAppMount   这个{@link org.apache.catalina.WebResourceSet}将会被安装的Web应用程序中的路径.
     * @param base          文件系统上的WAR文件的绝对路径
     * @param archivePath   JAR文件所在的WAR文件内的路径.
     * @param internalPath  新{@link org.apache.catalina.WebResourceSet}中资源的路径. E.g. 对于一个资源 JAR, 将会是 "META-INF/resources"
     *
     * @throws IllegalArgumentException 如果 webAppMount 或 internalPath 无效 (有效的路径必须以 '/' 开头)
     */
    public JarWarResourceSet(WebResourceRoot root, String webAppMount,
            String base, String archivePath, String internalPath)
            throws IllegalArgumentException {
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);
        this.archivePath = archivePath;
        setInternalPath(internalPath);

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest) {
        return new JarWarResource(this, webAppPath, getBaseUrlString(), jarEntry, archivePath);
    }


    /**
     * {@inheritDoc}
     * <p>
     * JarWar 不能为单一资源优化, 因此总是返回 Map.
     */
    @Override
    protected HashMap<String,JarEntry> getArchiveEntries(boolean single) {
        synchronized (archiveLock) {
            if (archiveEntries == null) {
                JarFile warFile = null;
                InputStream jarFileIs = null;
                archiveEntries = new HashMap<>();
                boolean multiRelease = false;
                try {
                    warFile = openJarFile();
                    JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
                    jarFileIs = warFile.getInputStream(jarFileInWar);

                    try (TomcatJarInputStream jarIs = new TomcatJarInputStream(jarFileIs)) {
                        JarEntry entry = jarIs.getNextJarEntry();
                        while (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                            entry = jarIs.getNextJarEntry();
                        }
                        Manifest m = jarIs.getManifest();
                        setManifest(m);
                        if (m != null && JreCompat.isJre9Available()) {
                            String value = m.getMainAttributes().getValue("Multi-Release");
                            if (value != null) {
                                multiRelease = Boolean.parseBoolean(value);
                            }
                        }
                        // 黑客在JarInputStream工作吞下这些条目.
                        // a) 条目是否存在 
                        // b) 缓存它们，这样就可以在这里访问它们.
                        entry = jarIs.getMetaInfEntry();
                        if (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                        }
                        entry = jarIs.getManifestEntry();
                        if (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                        }
                    }
                    if (multiRelease) {
                        processArchivesEntriesForMultiRelease();
                    }
                } catch (IOException ioe) {
                    // Should never happen
                    archiveEntries = null;
                    throw new IllegalStateException(ioe);
                } finally {
                    if (warFile != null) {
                        closeJarFile();
                    }
                    if (jarFileIs != null) {
                        try {
                            jarFileIs.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }
            }
            return archiveEntries;
        }
    }


    protected void processArchivesEntriesForMultiRelease() {

        int targetVersion = JreCompat.getInstance().jarFileRuntimeMajorVersion();

        Map<String,VersionedJarEntry> versionedEntries = new HashMap<>();
        Iterator<Entry<String,JarEntry>> iter = archiveEntries.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String,JarEntry> entry = iter.next();
            String name = entry.getKey();
            if (name.startsWith("META-INF/versions/")) {
                // 删除多次释放的版本
                iter.remove();

                // 获取此版本条目的基础名称和版本
                int i = name.indexOf('/', 18);
                if (i > 0) {
                    String baseName = name.substring(i + 1);
                    int version = Integer.parseInt(name.substring(18, i));

                    // Ignore any entries targeting for a later version than
                    // the target for this runtime
                    if (version <= targetVersion) {
                        VersionedJarEntry versionedJarEntry = versionedEntries.get(baseName);
                        if (versionedJarEntry == null) {
                            // No versioned entry found for this name. Create
                            // one.
                            versionedEntries.put(baseName,
                                    new VersionedJarEntry(version, entry.getValue()));
                        } else {
                            // Ignore any entry for which we have already found
                            // a later version
                            if (version > versionedJarEntry.getVersion()) {
                                // Replace the entry targeted at an earlier
                                // version
                                versionedEntries.put(baseName,
                                        new VersionedJarEntry(version, entry.getValue()));
                            }
                        }
                    }
                }
            }
        }

        for (Entry<String,VersionedJarEntry> versionedJarEntry : versionedEntries.entrySet()) {
            archiveEntries.put(versionedJarEntry.getKey(),
                    versionedJarEntry.getValue().getJarEntry());
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * 不应该被调用, 因为 {@link #getArchiveEntries(boolean)} 总是返回一个 Map.
     */
    @Override
    protected JarEntry getArchiveEntry(String pathInArchive) {
        throw new IllegalStateException("Coding error");
    }


    @Override
    protected boolean isMultiRelease() {
        // 总是返回 false, 否则父类将调用 #getArchiveEntry(String)
        return false;
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {

        try (JarFile warFile = new JarFile(getBase())) {
            JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
            InputStream jarFileIs = warFile.getInputStream(jarFileInWar);

            try (JarInputStream jarIs = new JarInputStream(jarFileIs)) {
                setManifest(jarIs.getManifest());
            }
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }

        try {
            setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }


    private static final class VersionedJarEntry {
        private final int version;
        private final JarEntry jarEntry;

        public VersionedJarEntry(int version, JarEntry jarEntry) {
            this.version = version;
            this.jarEntry = jarEntry;
        }


        public int getVersion() {
            return version;
        }


        public JarEntry getJarEntry() {
            return jarEntry;
        }
    }
}
