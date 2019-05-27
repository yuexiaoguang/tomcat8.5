package org.apache.catalina;

/**
 * <b>Cluster</b>作为本地主机的集群客户机/服务器，可以使用不同的集群实现来支持在集群中进行不同方式的通信.
 * 一个Cluster实现类负责在集群中建立通信方式, 也提供"ClientApplications" 使用<code>ClusterSender</code>,
 * 当在Cluster 和<code>ClusterInfo</code>发送信息的时候使用, 当在Cluster接收信息的时候使用.
 */
public interface Cluster {

    // ------------------------------------------------------------- Properties

    /**
     * 返回集群的名称, 此服务器当前配置运行所在的.
     *
     * @return 这个服务器关联的集群名称
     */
    public String getClusterName();

    /**
     * 设置要加入的集群名称, 如果没有此名称的集群，则创建一个.
     *
     * @param clusterName 要加入的集群名称
     */
    public void setClusterName(String clusterName);

    /**
     * 设置关联的Container
     *
     * @param container The Container to use
     */
    public void setContainer(Container container);

    /**
     * 获取关联的Container
     *
     * @return The Container associated with our Cluster
     */
    public Container getContainer();


    // --------------------------------------------------------- Public Methods

    /**
     * 创建一个新的管理器，该管理器将使用此集群复制其会话.
     *
     * @param name 管理器关联的应用名称(key)
     */
    public Manager createManager(String name);

    /**
     * 使用集群注册管理器.
     * 如果集群不负责创建管理器, 然后容器会通知集群这个管理器正在参与集群.
     * 
     * @param manager Manager
     */
    public void registerManager(Manager manager);

    /**
     * 从集群移除管理器
     * 
     * @param manager Manager
     */
    public void removeManager(Manager manager);

    // --------------------------------------------------------- Cluster Wide Deployments


    /**
     * 执行周期性任务，如重新加载等. 该方法将在这个容器的类加载上下文调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess();
}
