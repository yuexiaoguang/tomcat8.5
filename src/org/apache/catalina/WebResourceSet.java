package org.apache.catalina;

import java.io.InputStream;
import java.net.URL;
import java.util.Set;

/**
 * 表示作为Web应用程序的一部分的资源集合. 示例包括目录结构, JAR资源, WAR文件.
 */
public interface WebResourceSet extends Lifecycle {
    /**
     * 获取表示给定路径上的资源的对象. 注意该路径上的资源可能不存在.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以'/'开头.
     *
     * @return  表示给定路径上的资源的对象
     */
    WebResource getResource(String path);

    /**
     * 获取位于指定目录中的所有文件和目录的名称的列表.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以'/'开头.
     *
     * @return  资源列表. 如果路径不引用目录，则返回零长度数组.
     */
    String[] list(String path);

    /**
     * 获取位于指定目录的所有的文件和目录的Web应用程序的路径. 表示目录的路径必须以"/"结尾.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以'/'开头.
     *
     * @return  资源列表. 如果路径不引用目录，则返回零长度数组.
     */
    Set<String> listWebAppPaths(String path);

    /**
     * 在给定路径创建一个新目录.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以'/'开头.
     *
     * @return  <code>true</code>如果目录创建成功, 否则<code>false</code>
     */
    boolean mkdir(String path);

    /**
     * 使用提供的InputStream在请求路径中创建新资源.
     *
     * @param path      新资源使用的路径. 资源相对于Web应用程序根的路径. 必须以'/'开头.
     * @param is        提供新资源内容的InputStream .
     * @param overwrite 如果<code>true</code>资源已经存在，将被覆盖.
     * 					如果<code>false</code>资源已经存在，写入失败.
     *
     * @return  <code>true</code>当且仅当资源写入
     */
    boolean write(String path, InputStream is, boolean overwrite);

    void setRoot(WebResourceRoot root);

    /**
     * 当显式查找类加载器资源时， 此资源集返回的资源是否应该包含在任何结果中.
     * 也就是说，这些资源是否应该排除在显式的静态资源查找之外.
     *
     * @return <code>true</code>如果这些资源只能用于类加载器资源的查找, 否则<code>false</code>
     */
    boolean getClassLoaderOnly();

    void setClassLoaderOnly(boolean classLoaderOnly);

    /**
     * 当显式查找静态资源时， 此资源集返回的资源是否应该包含在任何结果中.
     * 也就是说，这些资源是否应该排除在显式的静态资源查找之外.
     *
     * @return <code>true</code>如果这些资源只能用于静态资源的查找, 否则<code>false</code>
     */
    boolean getStaticOnly();

    void setStaticOnly(boolean staticOnly);

    /**
     * 获取此资源集的基本URL. 这其中的一个用途是在安全管理器下运行时授予资源的读权限.
     *
     * @return 这组资源的基本URL
     */
    URL getBaseUrl();

    /**
     * 配置此资源集是否为只读资源.
     *
     * @param readOnly <code>true</code>如果这组资源应该配置为只读的
     *
     * @throws IllegalArgumentException 如果尝试配置只读的{@link WebResourceSet}为可写的
     */
    void setReadOnly(boolean readOnly);

    /**
     * 获取此资源集的只读设置的当前值.
     *
     * @return <code>true</code>如果这组资源应该配置为只读的, 否则<code>false</code>
     */
    boolean isReadOnly();

    /**
     * 实现可以缓存一些信息以提高性能. 此方法触发对这些资源的清理.
     */
    void gc();
}
