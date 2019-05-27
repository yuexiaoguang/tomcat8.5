package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ResourceSet;
import org.apache.tomcat.util.compat.JreCompat;

public abstract class AbstractArchiveResourceSet extends AbstractResourceSet {

    private URL baseUrl;
    private String baseUrlString;

    private JarFile archive = null;
    protected HashMap<String,JarEntry> archiveEntries = null;
    protected final Object archiveLock = new Object();
    private long archiveUseCount = 0;


    protected final void setBaseUrl(URL baseUrl) {
        this.baseUrl = baseUrl;
        if (baseUrl == null) {
            this.baseUrlString = null;
        } else {
            this.baseUrlString = baseUrl.toString();
        }
    }

    @Override
    public final URL getBaseUrl() {
        return baseUrl;
    }

    protected final String getBaseUrlString() {
        return baseUrlString;
    }


    @Override
    public final String[] list(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ArrayList<String> result = new ArrayList<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar =
                    getInternalPath() + path.substring(webAppMount.length());
            // 剔除开头的 '/' 来获取 JAR 路径
            if (pathInJar.length() > 0 && pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            Iterator<String> entries = getArchiveEntries(false).keySet().iterator();
            while (entries.hasNext()) {
                String name = entries.next();
                if (name.length() > pathInJar.length() &&
                        name.startsWith(pathInJar)) {
                    if (name.charAt(name.length() - 1) == '/') {
                        name = name.substring(
                                pathInJar.length(), name.length() - 1);
                    } else {
                        name = name.substring(pathInJar.length());
                    }
                    if (name.length() == 0) {
                        continue;
                    }
                    if (name.charAt(0) == '/') {
                        name = name.substring(1);
                    }
                    if (name.length() > 0 && name.lastIndexOf('/') == -1) {
                        result.add(name);
                    }
                }
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) {
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    return new String[] {webAppMount.substring(path.length())};
                } else {
                    return new String[] {
                            webAppMount.substring(path.length(), i)};
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public final Set<String> listWebAppPaths(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();

        ResourceSet<String> result = new ResourceSet<>();
        if (path.startsWith(webAppMount)) {
            String pathInJar =
                    getInternalPath() + path.substring(webAppMount.length());
            // 剔除开头的 '/' 来获取 JAR 路径, 并确保以 '/' 结尾
            if (pathInJar.length() > 0) {
                if (pathInJar.charAt(pathInJar.length() - 1) != '/') {
                    pathInJar = pathInJar.substring(1) + '/';
                }
                if (pathInJar.charAt(0) == '/') {
                    pathInJar = pathInJar.substring(1);
                }
            }

            Iterator<String> entries = getArchiveEntries(false).keySet().iterator();
            while (entries.hasNext()) {
                String name = entries.next();
                if (name.length() > pathInJar.length() &&
                        name.startsWith(pathInJar)) {
                    int nextSlash = name.indexOf('/', pathInJar.length());
                    if (nextSlash == -1 || nextSlash == name.length() - 1) {
                        if (name.startsWith(pathInJar)) {
                            result.add(webAppMount + '/' +
                                    name.substring(getInternalPath().length()));
                        }
                    }
                }
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) {
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    result.add(webAppMount + "/");
                } else {
                    result.add(webAppMount.substring(0, i + 1));
                }
            }
        }
        result.setLocked(true);
        return result;
    }


    /**
     * 获取结构中条目的Map. 可能返回 null, 那时应该使用 {@link #getArchiveEntry(String)}.
     *
     * @param single 这个请求是用来支持单个查找的吗? 如果是false, 总是返回 map. 如果是 true, 实现可以使用这一点作为确定最佳响应方式的提示.
     *
     * @return 结构中条目的Map, 或 null 如果应该使用 {@link #getArchiveEntry(String)}.
     */
    protected abstract HashMap<String,JarEntry> getArchiveEntries(boolean single);


    /**
     * 从结构中获取单个条目. 出于性能原因, {@link #getArchiveEntries(boolean)} 应该总是首先被调用, 并在map中查找结构条目.、
     * 只有当它返回  null时, 才会使用这个方法.
     *
     * @param pathInArchive 所需条目结构中的路径
     *
     * @return 指定的结构条目或 null
     */
    protected abstract JarEntry getArchiveEntry(String pathInArchive);

    @Override
    public final boolean mkdir(String path) {
        checkPath(path);

        return false;
    }

    @Override
    public final boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException(
                    sm.getString("dirResourceSet.writeNpe"));
        }

        return false;
    }

    @Override
    public final WebResource getResource(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();
        WebResourceRoot root = getRoot();

        /*
         * 实现说明
         *
         * 传递到该方法中的路径参数总是以 '/' 开头.
         *
         * 传递到该方法中的路径参数不一定以 '/' 结尾. JarFile.getEntry() 将返回匹配的目录条目, 不论名称是否以 '/' 结尾.
         * 但是, 如果不使用'/'请求条目, 随后调用 JarEntry.isDirectory()将返回 false.
         *
         * JAR中的路径不会以 '/' 开头. 开头的 '/' 需要在调用 JarFile.getEntry() 之前删除.
         */

        // 如果JAR 已安装在Web应用程序根下, 为挂载点以外的请求返回空资源.

        if (path.startsWith(webAppMount)) {
            String pathInJar = getInternalPath() + path.substring(
                    webAppMount.length(), path.length());
            // 总是剔除开头的 '/' 来获取 JAR 路径
            if (pathInJar.length() > 0 && pathInJar.charAt(0) == '/') {
                pathInJar = pathInJar.substring(1);
            }
            if (pathInJar.equals("")) {
                // 特例
                // 这是一个目录资源, 因此路径必须以 / 结尾
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                return new JarResourceRoot(root, new File(getBase()),
                        baseUrlString, path);
            } else {
                JarEntry jarEntry = null;
                if (isMultiRelease()) {
                    // 调用可以多次释放感知的 JarFile.getJarEntry()
                    jarEntry = getArchiveEntry(pathInJar);
                } else {
                    Map<String,JarEntry> jarEntries = getArchiveEntries(true);
                    if (!(pathInJar.charAt(pathInJar.length() - 1) == '/')) {
                        if (jarEntries == null) {
                            jarEntry = getArchiveEntry(pathInJar + '/');
                        } else {
                            jarEntry = jarEntries.get(pathInJar + '/');
                        }
                        if (jarEntry != null) {
                            path = path + '/';
                        }
                    }
                    if (jarEntry == null) {
                        if (jarEntries == null) {
                            jarEntry = getArchiveEntry(pathInJar);
                        } else {
                            jarEntry = jarEntries.get(pathInJar);
                        }
                    }
                }
                if (jarEntry == null) {
                    return new EmptyResource(root, path);
                } else {
                    return createArchiveResource(jarEntry, path, getManifest());
                }
            }
        } else {
            return new EmptyResource(root, path);
        }
    }

    protected abstract boolean isMultiRelease();

    protected abstract WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest);

    @Override
    public final boolean isReadOnly() {
        return true;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        if (readOnly) {
            // This is the hard-coded default - ignore the call
            return;
        }

        throw new IllegalArgumentException(
                sm.getString("abstractArchiveResourceSet.setReadOnlyFalse"));
    }

    protected JarFile openJarFile() throws IOException {
        synchronized (archiveLock) {
            if (archive == null) {
                archive = JreCompat.getInstance().jarFileNewInstance(getBase());
            }
            archiveUseCount++;
            return archive;
        }
    }

    protected void closeJarFile() {
        synchronized (archiveLock) {
            archiveUseCount--;
        }
    }

    @Override
    public void gc() {
        synchronized (archiveLock) {
            if (archive != null && archiveUseCount == 0) {
                try {
                    archive.close();
                } catch (IOException e) {
                    // Log at least WARN
                }
                archive = null;
                archiveEntries = null;
            }
        }
    }
}
