package org.apache.catalina;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;

/**
 * <strong>Service</strong>是一组或多组<strong>Connectors</strong>共享单个<strong>Container</strong>来处理它们各自的请求.
 * 例如，这种安排允许一个非SSL和SSL连接器共享相同数量的Web应用程序
 * <p>
 * 一个JVM可以包含多个Service 实例; 然而，他们是完全独立的，只共享基本JVM设备和类文件.
 */
public interface Service extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * @return 处理请求的<code>Engine</code>.
     */
    public Engine getContainer();

    /**
     * 设置处理请求的<code>Engine</code>.
     *
     * @param engine The new Engine
     */
    public void setContainer(Engine engine);

    /**
     * @return the name of this Service.
     */
    public String getName();

    /**
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    public void setName(String name);

    /**
     * @return 关联的<code>Server</code>.
     */
    public Server getServer();

    /**
     * 设置关联的<code>Server</code>.
     *
     * @param server 拥有此服务的服务器
     */
    public void setServer(Server server);

    /**
     * @return 父类加载器. 如果未设置, 返回{@link #getServer()} {@link Server#getParentClassLoader()}.
     * 如果Server未设置, 返回系统类加载器.
     */
    public ClassLoader getParentClassLoader();

    /**
     * 设置父类加载器.
     *
     * @param parent The new parent class loader
     */
    public void setParentClassLoader(ClassLoader parent);

    /**
     * @return 这个容器将被注册的域名.
     */
    public String getDomain();


    // --------------------------------------------------------- Public Methods

    /**
     * 添加一个新的Connector, 并将它与Service的 Container关联.
     *
     * @param connector The Connector to be added
     */
    public void addConnector(Connector connector);

    /**
     * 查找并返回关联的Connector.
     *
     * @return the set of associated Connectors
     */
    public Connector[] findConnectors();

    /**
     * 移除指定的Connector. 移除的Connector也将不再和Container关联.
     *
     * @param connector 要删除的Connector
     */
    public void removeConnector(Connector connector);

    /**
     * 将命名的执行器添加到服务中
     * 
     * @param ex Executor
     */
    public void addExecutor(Executor ex);

    /**
     * 检索所有的执行器
     */
    public Executor[] findExecutors();

    /**
     * 检索指定名称的执行器, 或null
     * 
     * @param name String
     */
    public Executor getExecutor(String name);

    /**
     * 删除一个执行器
     * 
     * @param ex Executor
     */
    public void removeExecutor(Executor ex);

    Mapper getMapper();
}
