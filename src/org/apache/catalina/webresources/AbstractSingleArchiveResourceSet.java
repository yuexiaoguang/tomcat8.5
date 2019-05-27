package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.compat.JreCompat;

/**
 * {@link org.apache.catalina.WebResourceSet}基类, 基于单个而不是嵌套的结构.
 */
public abstract class AbstractSingleArchiveResourceSet extends AbstractArchiveResourceSet {

    private volatile Boolean multiRelease;

    public AbstractSingleArchiveResourceSet() {
    }


    public AbstractSingleArchiveResourceSet(WebResourceRoot root, String webAppMount, String base,
            String internalPath) throws IllegalArgumentException {
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);
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
    protected HashMap<String,JarEntry> getArchiveEntries(boolean single) {
        synchronized (archiveLock) {
            if (archiveEntries == null && !single) {
                JarFile jarFile = null;
                archiveEntries = new HashMap<>();
                try {
                    jarFile = openJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        archiveEntries.put(entry.getName(), entry);
                    }
                } catch (IOException ioe) {
                    // Should never happen
                    archiveEntries = null;
                    throw new IllegalStateException(ioe);
                } finally {
                    if (jarFile != null) {
                        closeJarFile();
                    }
                }
            }
            return archiveEntries;
        }
    }


    @Override
    protected JarEntry getArchiveEntry(String pathInArchive) {
        JarFile jarFile = null;
        try {
            jarFile = openJarFile();
            return jarFile.getJarEntry(pathInArchive);
        } catch (IOException ioe) {
            // Should never happen
            throw new IllegalStateException(ioe);
        } finally {
            if (jarFile != null) {
                closeJarFile();
            }
        }
    }


    @Override
    protected boolean isMultiRelease() {
        if (multiRelease == null) {
            synchronized (archiveLock) {
                if (multiRelease == null) {
                    JarFile jarFile = null;
                    try {
                        jarFile = openJarFile();
                        multiRelease = Boolean.valueOf(
                                JreCompat.getInstance().jarFileIsMultiRelease(jarFile));
                    } catch (IOException ioe) {
                        // Should never happen
                        throw new IllegalStateException(ioe);
                    } finally {
                        if (jarFile != null) {
                            closeJarFile();
                        }
                    }
                }
            }
        }

        return multiRelease.booleanValue();
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {

        try (JarFile jarFile = JreCompat.getInstance().jarFileNewInstance(getBase())) {
            setManifest(jarFile.getManifest());
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }

        try {
            setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
