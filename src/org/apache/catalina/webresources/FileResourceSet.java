package org.apache.catalina.webresources;

import java.io.File;
import java.io.InputStream;
import java.util.Set;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ResourceSet;

/**
 * 基于一个单独的文件, 表示一个{@link org.apache.catalina.WebResourceSet}.
 */
public class FileResourceSet extends AbstractFileResourceSet {

    public FileResourceSet() {
        super("/");
    }

    /**
     * 基于一个单独的文件, 创建一个新的 {@link org.apache.catalina.WebResourceSet}.
     *
     * @param root          这个新{@link org.apache.catalina.WebResourceSet}将会被添加到的 {@link WebResourceRoot}.
     * @param webAppMount   这个{@link org.apache.catalina.WebResourceSet}将会被安装的Web应用中的路径.
     * 						例如, 添加JAR的一个目录到Web应用, 目录将被安装在 "WEB-INF/lib/"
     * @param base          文件系统上的文件的绝对路径，资源将从其中服务.
     * @param internalPath  这个新{@link org.apache.catalina.WebResourceSet}中资源的路径.
     */
    public FileResourceSet(WebResourceRoot root, String webAppMount,
            String base, String internalPath) {
        super(internalPath);
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    @Override
    public WebResource getResource(String path) {
        checkPath(path);

        String webAppMount = getWebAppMount();
        WebResourceRoot root = getRoot();
        if (path.equals(webAppMount)) {
            File f = file("", true);
            if (f == null) {
                return new EmptyResource(root, path);
            }
            return new FileResource(root, path, f, isReadOnly(), null);
        }

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }

        if (webAppMount.startsWith(path)) {
            String name = path.substring(0, path.length() - 1);
            name = name.substring(name.lastIndexOf('/') + 1);
            if (name.length() > 0) {
                return new VirtualResource(root, path, name);
            }
        }
        return new EmptyResource(root, path);
    }

    @Override
    public String[] list(String path) {
        checkPath(path);

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        String webAppMount = getWebAppMount();

        if (webAppMount.startsWith(path)) {
            webAppMount = webAppMount.substring(path.length());
            if (webAppMount.equals(getFileBase().getName())) {
                return new String[] {getFileBase().getName()};
            } else {
                // Virtual directory
                int i = webAppMount.indexOf('/');
                if (i > 0) {
                    return new String[] {webAppMount.substring(0, i)};
                }
            }
        }

        return EMPTY_STRING_ARRAY;
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        checkPath(path);

        ResourceSet<String> result = new ResourceSet<>();

        if (path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        String webAppMount = getWebAppMount();

        if (webAppMount.startsWith(path)) {
            webAppMount = webAppMount.substring(path.length());
            if (webAppMount.equals(getFileBase().getName())) {
                result.add(path + getFileBase().getName());
            } else {
                // Virtual directory
                int i = webAppMount.indexOf('/');
                if (i > 0) {
                    result.add(path + webAppMount.substring(0, i + 1));
                }
            }
        }

        result.setLocked(true);
        return result;
    }

    @Override
    public boolean mkdir(String path) {
        checkPath(path);
        return false;
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);
        return false;
    }

    @Override
    protected void checkType(File file) {
        if (file.isFile() == false) {
            throw new IllegalArgumentException(sm.getString("fileResourceSet.notFile",
                    getBase(), File.separator, getInternalPath()));
        }
    }
}
