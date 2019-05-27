package org.apache.catalina;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * 表示Web应用程序的完整资源集.
 * web应用的资源由多个ResourceSet组成, 当查找一个Resource时, ResourceSet按以下顺序处理:
 * <ol>
 * <li>Pre  - web应用的context.xml中&lt;PreResource&gt;元素定义的Resource. 将按照指定的顺序搜索资源.</li>
 * <li>Main - Web应用的主要资源 - 即WAR 或包含解压的WAR的目录</li>
 * <li>JARs - 通过Servlet规范定义的JAR资源. JAR将按照被添加进ResourceRoot的顺序搜索.</li>
 * <li>Post - web应用的context.xml中&lt;PostResource&gt;元素定义的Resource. 将按照指定的顺序搜索资源.</li>
 * </ol>
 * 应注意下列约定:
 * <ul>
 * <li>写操作(包括删除)只适用于主要ResourceSet. 写操作将失败，如果其它ResourceSet中的一个Resource有效地进行操作main ResourceSet 一个 NO-OP.</li>
 * <li>ResourceSet中的一个文件将隐藏在搜索顺序中的ResourceSet中的同名的目录(目录的所有内容).</li>
 * <li>只有main ResourceSet可以定义 META-INF/context.xml，因为文件定义了Pre- 和 Post-Resources.</li>
 * <li>在每个Servlet 规范中, JAR资源中任何META-INF 或 WEB-INF 目录将被忽略.</li>
 * <li>Pre- 和 Post-Resources 可以定义 WEB-INF/lib 和 WEB-INF/classes 以使其他的库和/或类可用于Web应用程序.
 * </ul>
 * 该机制替换并扩展了早期版本中存在的以下特性:
 * <ul>
 * <li>Aliases               - 由Post-Resources替换, 增加了对单个文件, 目录, JAR的支持.</li>
 * <li>VirtualWebappLoader   - 由Pre- 和 Post-Resource替换，映射到WEB-INF/lib 和 WEB-INF/classes</li>
 * <li>VirtualDirContext     - 由Pre- 和 Post-Resource替换</li>
 * <li>External repositories - 由Pre- 和 Post-Resource替换，映射到WEB-INF/lib 和 WEB-INF/classes</li>
 * <li>Resource JARs         - 相同的特性，但使用与所有其他附加资源相同的机制实现.</li>
 * </ul>
 */
public interface WebResourceRoot extends Lifecycle {
    /**
     * 获取表示给定路径上的资源的对象. 注意，该路径上的资源可能不存在.
     * 如果路径不存在, 返回的WebResource 将关联到 main WebResourceSet.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以 '/'开头.
     *
     * @return  表示给定路径上的资源的对象
     */
    WebResource getResource(String path);

    /**
     * 获取表示给定路径上的资源的对象。注意，该路径上的资源可能不存在.
     * 如果路径不存在, 返回的WebResource 将关联到 main WebResourceSet.
     * 这将包括所有匹配，即使资源通常不可访问(例如，它被另一个资源所覆盖)
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以 '/'开头.
     *
     * @return  表示给定路径上的资源的对象
     */
    WebResource[] getResources(String path);

    /**
     * 获取表示给定路径上的类加载器资源的对象.
     * WEB-INF/classes 总是在搜索WEB-INF/lib中的JAR文件之前搜索. JAR文件的搜索顺序在随后调用该方法时将是一致的，直到Web应用程序加载完成.
     * 对于JAR文件的搜索顺序可能没有保证.
     *
     * @param path  类加载器资源的路径, 相对于此Web应用程序的类加载器资源的根路径.
     *
     * @return  表示给定路径上的类加载器资源的对象
     */
    WebResource getClassLoaderResource(String path);

    /**
     * 获取表示给定路径上的类加载器资源的对象. 注意，该路径上的资源可能不存在.
     * 如果路径不存在, 返回的WebResource 将关联到 main WebResourceSet.
     * 这将包括所有匹配，即使资源通常不可访问(例如，它被另一个资源所覆盖)
     *
     * @param path  类加载器资源的路径, 相对于此Web应用程序的类加载器资源的根路径. 必须以 '/'开头.
     *
     * @return  表示给定路径上的类加载器资源的对象
     */
    WebResource[] getClassLoaderResources(String path);

    /**
     * 获取位于指定目录中的所有文件和目录的名称的列表.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以 '/'开头.
     *
     * @return  资源列表. 如果路径不引用目录，则返回零长度数组.
     */
    String[] list(String path);

    /**
     * 获取位于指定目录中的所有文件和目录的Web应用程序路径名的集合.
     * 表示目录的路径将以 '/' 结尾.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以 '/'开头.
     *
     * @return  资源集合. 如果路径不引用目录，返回null.
     */
    Set<String> listWebAppPaths(String path);

    /**
     * 获取指定目录中所有WebResource的列表.
     *
     * @param path  资源相对于Web应用程序根的路径. 必须以 '/'开头.
     *
     * @return  资源集合. 如果路径不引用目录，则返回零长度数组.
     */
    WebResource[] listResources(String path);

    /**
     * 在给定路径上创建一个新目录.
     *
     * @param path  新资源相对于Web应用程序根创建的路径. 必须以 '/'开头.
     *
     * @return  <code>true</code>如果目录创建成功, 否则<code>false</code>
     */
    boolean mkdir(String path);

    /**
     * 使用提供的InputStream在请求路径中创建新资源.
     *
     * @param path      新资源使用的路径. 相对于Web应用的根路径，并以'/'开头.
     * @param is        作为新资源内容的InputStream.
     * @param overwrite 如果是<code>true</code>，将覆盖资源. 如果是<code>false</code>，已经存在的资源将写入失败.
     *
     * @return  <code>true</code> 当且仅当资源是可写的
     */
    boolean write(String path, InputStream is, boolean overwrite);

    /**
     * 基于提供的参数，为这个{@link WebResourceRoot}创建一个新的{@link WebResourceSet}.
     *
     * @param type          要创建的{@link WebResourceSet}的类型
     * @param webAppMount   资源应在Web应用程序中发布的路径. 必须以'/'开头.
     * @param url           资源的URL (必须定位于一个 JAR, 文件或目录)
     * @param internalPath  要查找的内容在资源内的路径. 必须以 '/'开头.
     */
    void createWebResourceSet(ResourceSetType type, String webAppMount, URL url,
            String internalPath);

    /**
     * 基于提供的参数，为这个{@link WebResourceRoot}创建一个新的{@link WebResourceSet}.
     *
     * @param type          要创建的{@link WebResourceSet}的类型
     * @param webAppMount   资源应在Web应用程序中发布的路径. 必须以'/'开头.
     * @param base          资源的位置
     * @param archivePath   要查找的内容在资源内的存档路径. 如果没有归档，应该是<code>null</code>.
     * @param internalPath  要查找的内容在资源内的存档路径(或资源路径，如果 archivePath 是<code>null</code>). 必须以 '/'开头.
     */
    void createWebResourceSet(ResourceSetType type, String webAppMount,
            String base, String archivePath, String internalPath);


    /**
     * 将提供的WebResourceSet 添加到这个Web应用的 'Pre'资源.
     *
     * @param webResourceSet 要使用的资源集
     */
    void addPreResources(WebResourceSet webResourceSet);

    /**
     * @return 配置给这个Web应用的 'Pre'资源的WebResourceSet集合.
     */
    WebResourceSet[] getPreResources();

    /**
     * 将提供的WebResourceSet 添加到这个Web应用的'Jar'资源.
     *
     * @param webResourceSet 要使用的资源集
     */
    void addJarResources(WebResourceSet webResourceSet);

    /**
     * @return 配置给这个Web应用的'Jar'资源的WebResourceSet集合.
     */
    WebResourceSet[] getJarResources();

    /**
     * 将提供的WebResourceSet 添加到这个Web应用的 'Post'资源.
     *
     * @param webResourceSet 要使用的资源集
     */
    void addPostResources(WebResourceSet webResourceSet);

    /**
     * @return 配置给这个Web应用的'Post'资源的WebResourceSet集合.
     */
    WebResourceSet[] getPostResources();

    /**
     * @return 这个 WebResourceRoot关联的Web应用.
     */
    Context getContext();

    /**
     * 设置这个 WebResourceRoot关联的Web应用.
     *
     * @param context the associated context
     */
    void setContext(Context context);

    /**
     * 配置此资源是否允许使用符号链接.
     *
     * @param allowLinking  <code>true</code>允许使用符号链接.
     */
    void setAllowLinking(boolean allowLinking);

    /**
     * 确定此资源是否允许使用符号链接.
     *
     * @return  <code>true</code>允许使用符号链接
     */
    boolean getAllowLinking();

    /**
     * 设置是否允许此Web应用程序缓存.
     *
     * @param cachingAllowed    <code>true</code>启用缓存, 否则<code>false</code>
     */
    void setCachingAllowed(boolean cachingAllowed);

    /**
     * @return <code>true</code>如果此Web应用程序允许缓存.
     */
    boolean isCachingAllowed();

    /**
     * 设置缓存的Time-To-Live (TTL生存时间).
     *
     * @param ttl   TTL in milliseconds
     */
    void setCacheTtl(long ttl);

    /**
     * 获取缓存的Time-To-Live (TTL生存时间).
     *
     * @return  TTL in milliseconds
     */
    long getCacheTtl();

    /**
     * 设置缓存的最大大小.
     *
     * @param cacheMaxSize  最大缓存大小, KB
     */
    void setCacheMaxSize(long cacheMaxSize);

    /**
     * 获取缓存的最大允许大小.
     *
     * @return  最大缓存大小, KB
     */
    long getCacheMaxSize();

    /**
     * 为缓存中的单个对象设置最大大小. 注意最大大小，为字节，不能超过{@link Integer#MAX_VALUE}.
     *
     * @param cacheObjectMaxSize    单个缓存对象的最大大小, KB
     */
    void setCacheObjectMaxSize(int cacheObjectMaxSize);

    /**
     * 获取缓存中单个对象的最大大小. 注意最大大小，为字节，不能超过{@link Integer#MAX_VALUE}.
     *
     * @return  单个缓存对象的最大大小, KB
     */
    int getCacheObjectMaxSize();

    /**
     * 是否启用了跟踪锁定文件功能.
     * 如果启用, 对锁定文件并返回对象的方法的调用，需要关闭并释放锁(即{@link WebResource#getInputStream()}将执行多个附加任务).
     * <ul>
     *   <li>被调用的方法将被记录并与返回的对象相关联的地方的堆栈跟踪.</li>
     *   <li>返回的对象将被封装，因此调用close()(或等效的方法)时需要释放资源. 一旦资源被释放，对象的跟踪将停止.</li>
     *   <li>Web应用程序关闭时所有剩余的锁定资源将被记录并关闭.</li>
     * </ul>
     *
     * @param trackLockedFiles {@code true}启用, {@code false}禁用
     */
    void setTrackLockedFiles(boolean trackLockedFiles);

    /**
     * 是否已启用跟踪锁定文件功能?
     *
     * @return {@code true} 启用, {@code false}禁用
     */
    boolean getTrackLockedFiles();

    /**
     * 该方法将定期由上下文调用，并允许实现执行周期性任务的方法，例如清除过期缓存条目.
     */
    void backgroundProcess();

    /**
     * 添加一个指定的资源跟踪，以便以后可以在停止时释放资源.
     * 
     * @param trackedResource 将跟踪的资源
     */
    void registerTrackedResource(TrackedWebResource trackedResource);

    /**
     * 停止跟踪指定资源, 一旦它不再需要资源.
     * 
     * @param trackedResource 被跟踪的资源
     */
    void deregisterTrackedResource(TrackedWebResource trackedResource);

    /**
     * @return 这个root使用的{@link WebResourceSet}的{@link WebResourceSet#getBaseUrl()}的集合.
     */
    List<URL> getBaseUrls();

    /**
     * 实现可以缓存一些信息以提高性能. 此方法触发对这些资源的清理.
     */
    void gc();

    static enum ResourceSetType {
        PRE,
        RESOURCE_JAR,
        POST,
        CLASSES_JAR
    }
}
