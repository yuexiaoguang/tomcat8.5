package org.apache.catalina.startup;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>构建Catalina的类加载器的帮助类. 工厂方法需要以下参数来构建一个新的类装入器(在所有情况下都有适当的默认值):</p>
 * <ul>
 * <li>包含在类装入器存储库中的解包类（和资源）的一组目录.</li>
 * <li>包含JAR文件中类和资源的一组目录.
 *     在这些目录中发现的每个可读JAR文件将被添加到类装入器的存储库中.</li>
 * <li><code>ClassLoader</code> 实例应该成为新类装入器的父节点.</li>
 * </ul>
 */
public final class ClassLoaderFactory {


    private static final Log log = LogFactory.getLog(ClassLoaderFactory.class);

    // --------------------------------------------------------- Public Methods


    /**
     * 创建并返回一个新的类装入器, 基于配置默认值和指定的目录路径:
     *
     * @param unpacked 打开的目录的路径名数组，应该添加到类装入器的存储库中, 或者<code>null</code> 不考虑未解包的目录
     * @param packed 包含JAR文件应该被添加到类加载器的库目录的路径名的数组, 或者<code>null</code>不考虑JAR文件的目录
     * @param parent 新类装入器的父类装入器, 或者<code>null</code> 对于系统类装入器.
     *
     * @exception Exception 如果发生错误，则生成类加载器
     */
    public static ClassLoader createClassLoader(File unpacked[],
                                                File packed[],
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        Set<URL> set = new LinkedHashSet<>();

        // Add unpacked directories
        if (unpacked != null) {
            for (int i = 0; i < unpacked.length; i++)  {
                File file = unpacked[i];
                if (!file.canRead())
                    continue;
                file = new File(file.getCanonicalPath() + File.separator);
                URL url = file.toURI().toURL();
                if (log.isDebugEnabled())
                    log.debug("  Including directory " + url);
                set.add(url);
            }
        }

        // Add packed directory JAR files
        if (packed != null) {
            for (int i = 0; i < packed.length; i++) {
                File directory = packed[i];
                if (!directory.isDirectory() || !directory.canRead())
                    continue;
                String filenames[] = directory.list();
                if (filenames == null) {
                    continue;
                }
                for (int j = 0; j < filenames.length; j++) {
                    String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                    if (!filename.endsWith(".jar"))
                        continue;
                    File file = new File(directory, filenames[j]);
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + file.getAbsolutePath());
                    URL url = file.toURI().toURL();
                    set.add(url);
                }
            }
        }

        // Construct the class loader itself
        final URL[] array = set.toArray(new URL[set.size()]);
        return AccessController.doPrivileged(
                new PrivilegedAction<URLClassLoader>() {
                    @Override
                    public URLClassLoader run() {
                        if (parent == null)
                            return new URLClassLoader(array);
                        else
                            return new URLClassLoader(array, parent);
                    }
                });
    }


    /**
     * 创建并返回一个新的类装入器, 基于配置默认值和指定的目录路径:
     *
     * @param repositories 类目录、jar文件、jar目录或URL列表, 应该将其添加到类加载器的库中.
     * @param parent 新类装入器的父类装入器, 或者<code>null</code> 对于系统类装入器.
     *
     * @exception Exception 如果发生错误，则生成类加载器
     */
    public static ClassLoader createClassLoader(List<Repository> repositories,
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        Set<URL> set = new LinkedHashSet<>();

        if (repositories != null) {
            for (Repository repository : repositories)  {
                if (repository.getType() == RepositoryType.URL) {
                    URL url = buildClassLoaderUrl(repository.getLocation());
                    if (log.isDebugEnabled())
                        log.debug("  Including URL " + url);
                    set.add(url);
                } else if (repository.getType() == RepositoryType.DIR) {
                    File directory = new File(repository.getLocation());
                    directory = directory.getCanonicalFile();
                    if (!validateFile(directory, RepositoryType.DIR)) {
                        continue;
                    }
                    URL url = buildClassLoaderUrl(directory);
                    if (log.isDebugEnabled())
                        log.debug("  Including directory " + url);
                    set.add(url);
                } else if (repository.getType() == RepositoryType.JAR) {
                    File file=new File(repository.getLocation());
                    file = file.getCanonicalFile();
                    if (!validateFile(file, RepositoryType.JAR)) {
                        continue;
                    }
                    URL url = buildClassLoaderUrl(file);
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + url);
                    set.add(url);
                } else if (repository.getType() == RepositoryType.GLOB) {
                    File directory=new File(repository.getLocation());
                    directory = directory.getCanonicalFile();
                    if (!validateFile(directory, RepositoryType.GLOB)) {
                        continue;
                    }
                    if (log.isDebugEnabled())
                        log.debug("  Including directory glob "
                            + directory.getAbsolutePath());
                    String filenames[] = directory.list();
                    if (filenames == null) {
                        continue;
                    }
                    for (int j = 0; j < filenames.length; j++) {
                        String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                        if (!filename.endsWith(".jar"))
                            continue;
                        File file = new File(directory, filenames[j]);
                        file = file.getCanonicalFile();
                        if (!validateFile(file, RepositoryType.JAR)) {
                            continue;
                        }
                        if (log.isDebugEnabled())
                            log.debug("    Including glob jar file "
                                + file.getAbsolutePath());
                        URL url = buildClassLoaderUrl(file);
                        set.add(url);
                    }
                }
            }
        }

        // Construct the class loader itself
        final URL[] array = set.toArray(new URL[set.size()]);
        if (log.isDebugEnabled())
            for (int i = 0; i < array.length; i++) {
                log.debug("  location " + i + " is " + array[i]);
            }

        return AccessController.doPrivileged(
                new PrivilegedAction<URLClassLoader>() {
                    @Override
                    public URLClassLoader run() {
                        if (parent == null)
                            return new URLClassLoader(array);
                        else
                            return new URLClassLoader(array, parent);
                    }
                });
    }

    private static boolean validateFile(File file,
            RepositoryType type) throws IOException {
        if (RepositoryType.DIR == type || RepositoryType.GLOB == type) {
            if (!file.isDirectory() || !file.canRead()) {
                String msg = "Problem with directory [" + file +
                        "], exists: [" + file.exists() +
                        "], isDirectory: [" + file.isDirectory() +
                        "], canRead: [" + file.canRead() + "]";

                File home = new File (Bootstrap.getCatalinaHome());
                home = home.getCanonicalFile();
                File base = new File (Bootstrap.getCatalinaBase());
                base = base.getCanonicalFile();
                File defaultValue = new File(base, "lib");

                // ${catalina.base}/lib 目录的存在是可选的.
                // 隐藏警告, 如果 Tomcat 运行在分隔的catalina.home和 catalina.base, 那个目录不存在.
                if (!home.getPath().equals(base.getPath())
                        && file.getPath().equals(defaultValue.getPath())
                        && !file.exists()) {
                    log.debug(msg);
                } else {
                    log.warn(msg);
                }
                return false;
            }
        } else if (RepositoryType.JAR == type) {
            if (!file.canRead()) {
                log.warn("Problem with JAR file [" + file +
                        "], exists: [" + file.exists() +
                        "], canRead: [" + file.canRead() + "]");
                return false;
            }
        }
        return true;
    }


    /*
     * 这两种方法最好在工具类org.apache.tomcat.util.buf.UriUtil上, 但是那个类是不可见的, 直到构建了类加载器之后.
     */
    private static URL buildClassLoaderUrl(String urlString) throws MalformedURLException {
        // 传递给类加载器的URL可以指向包含JAR的目录. 如果这些URL用于构建JAR中的资源的URL，则URL将被用作 is.
        // 因此需要确保序列 "!/"不存在于类加载器URL中.
        String result = urlString.replaceAll("!/", "%21/");
        return new URL(result);
    }


    private static URL buildClassLoaderUrl(File file) throws MalformedURLException {
        // Could be a directory or a file
        String fileUrlString = file.toURI().toString();
        fileUrlString = fileUrlString.replaceAll("!/", "%21/");
        return new URL(fileUrlString);
    }


    public static enum RepositoryType {
        DIR,
        GLOB,
        JAR,
        URL
    }

    public static class Repository {
        private final String location;
        private final RepositoryType type;

        public Repository(String location, RepositoryType type) {
            this.location = location;
            this.type = type;
        }

        public String getLocation() {
            return location;
        }

        public RepositoryType getType() {
            return type;
        }
    }
}
