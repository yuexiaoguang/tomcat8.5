package org.apache.catalina;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;


/**
 * <b>Host</b>是一个容器，代表在Catalina servlet引擎的虚拟主机 . 
 * 它在以下类型的场景中很有用:
 * <ul>
 * <li>你想使用拦截器，查看由这个特定虚拟主机处理的每个请求
 * <li>你想运行Catalina在一个独立的HTTP连接器中，但是仍然想支持多个虚拟主机
 * </ul>
 * 通常, 你不会使用Host，当部署 Catalina连接到一个web服务器(例如 Apache), 
 * 因为连接器将利用Web服务器的设施来确定应该使用哪些上下文（甚至哪一个Wrapper）来处理这个请求
 * <p>
 * Host的父容器通常是一个Engine, 也可能是其他一些实现类, 又或者可以省略
 * <p>
 * Host的子容器通常是Context的实现类 (表示单个servlet上下文)
 */
public interface Host extends Container {

    // ----------------------------------------------------- Manifest Constants

    /**
     * 当<code>addAlias()</code>方法添加一个新别名的时候， ContainerEvent事件类型将被发送.
     */
    public static final String ADD_ALIAS_EVENT = "addAlias";


    /**
     * 当<code>removeAlias()</code>方法移除一个旧别名的时候， ContainerEvent事件类型将被发送.
     */
    public static final String REMOVE_ALIAS_EVENT = "removeAlias";


    // ------------------------------------------------------------- Properties


    /**
     * @return 这个Host的 XML根. 这可以是绝对路径，相对路径，或一个URL.
     * 如果是null, 默认为${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; 目录
     */
    public String getXmlBase();

    /**
     * 设置这个Host的 Xml根. 这可以是绝对路径，相对路径，或一个URL.
     * 如果是null, 默认为${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; 目录
     * 
     * @param xmlBase The new XML root
     */
    public void setXmlBase(String xmlBase);

    /**
     * @return 这个Host的默认配置路径. 如果可能的话，该文件将是规范的.
     */
    public File getConfigBaseFile();

    /**
     * 返回应用程序根目录 。可能是相对路径、绝对路径、URL.
     */
    public String getAppBase();


    /**
     * @return 这个Host的appBase的一个绝对的 {@link File}.
     * 如果可能的话，该文件将是规范的. 不保证appBase 存在.
     */
    public File getAppBaseFile();


    /**
     * 设置应用程序根目录 。可能是相对路径、绝对路径、URL.
     *
     * @param appBase The new application root
     */
    public void setAppBase(String appBase);


    /**
     * 是否自动部署. 如果是true，表明该主机的子应用应该被自动发现并部署.
     */
    public boolean getAutoDeploy();


    /**
     * 设置自动部署
     * 
     * @param autoDeploy 自动部署标记
     */
    public void setAutoDeploy(boolean autoDeploy);


    /**
     * 返回Web应用程序新的上下文配置类的java类的名称.
     */
    public String getConfigClass();


    /**
     * 设置Web应用程序新的上下文配置类的java类的名称
     *
     * @param configClass 新的上下文配置类
     */
    public void setConfigClass(String configClass);


    /**
     * 启动时是否自动部署.
     * 如果是true, 这表明该主机的子应用能够自动发现并部署.
     */
    public boolean getDeployOnStartup();


    /**
     * 启动时是否自动部署.
     *
     * @param deployOnStartup The new deploy on startup flag
     */
    public void setDeployOnStartup(boolean deployOnStartup);


    /**
     * @return 正则表达式,定义主机的appBase中的文件和目录在自动部署时将被忽略.
     */
    public String getDeployIgnore();


    /**
     * @return 正则表达式,定义主机的appBase中的文件和目录在自动部署时将被忽略.
     */
    public Pattern getDeployIgnorePattern();


    /**
     * 设置正则表达式,定义主机的appBase中的文件和目录在自动部署时将被忽略.
     *
     * @param deployIgnore 匹配文件名的正则表达式
     */
    public void setDeployIgnore(String deployIgnore);


    /**
     * @return 用于启动和停止上下文的执行器.
     * 这主要是用于部署要以多线程方式实现的上下文的组件.
     */
    public ExecutorService getStartStopExecutor();


    /**
     * 返回<code>true</code>，如果Host 尝试创建appBase 和 xmlBase目录，除非它们已经存在.
     * 
     * @return true 如果主机试图创建目录
     */
    public boolean getCreateDirs();


    /**
     * Host 在启动时是否将尝试创建appBase 和 xmlBase目录.
     *
     * @param createDirs The new value for this flag
     */
    public void setCreateDirs(boolean createDirs);


    /**
     * @return <code>true</code> Host配置用于自动卸载部署的应用的旧版本，使用并行部署.
     * 只有在{@link #getAutoDeploy()}也返回<code>true</code>时才有效.
     */
    public boolean getUndeployOldVersions();


    /**
     * 设置为<code>true</code>，如果Host应该自动卸载部署的应用的旧版本，使用并行部署.
     * 只有在{@link #getAutoDeploy()}也返回<code>true</code>时才有效.
     *
     * @param undeployOldVersions The new value for this flag
     */
    public void setUndeployOldVersions(boolean undeployOldVersions);


    // --------------------------------------------------------- Public Methods

    /**
     * 添加映射到同一主机的别名.
     *
     * @param alias The alias to be added
     */
    public void addAlias(String alias);


    /**
     * 返回别名集合. 如果没有,返回零长度的数组.
     */
    public String[] findAliases();


    /**
     * 移除指定的别名.
     *
     * @param alias Alias name to be removed
     */
    public void removeAlias(String alias);
}
