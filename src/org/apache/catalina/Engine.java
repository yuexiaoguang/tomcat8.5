package org.apache.catalina;

/**
 * <b>Engine</b> 是一个容器，代表整个Catalina servlet引擎。
 * 它在以下类型的场景中很有用:
 * <ul>
 * <li>您希望使用拦截器来查看整个引擎处理的每一个请求.
 * <li>你希望运行Catalina在一个独立的HTTP连接器，但是仍然想支持多个虚拟主机.
 * </ul>
 * 一般来说, 你不会使用Engine，当部署 Catalina连接到网络服务器（如Apache）,
 * 因为连接器将使用Web服务器的设施来确定上下文（甚至它的包装）应该用来处理这个请求.
 * <p>
 * 附加到引擎的子容器通常是主机（代表虚拟主机）或上下文（代表单个servlet上下文）的实现，这取决于引擎实现.
 * <p>
 * 如果使用, Engine在Catalina层次中一直是顶层容器 
 * 因此, 实现类的 <code>setParent()</code>方法应该抛出<code>IllegalArgumentException</code>.
 */
public interface Engine extends Container {

    /**
     * 返回这个Engine的默认hostname.
     */
    public String getDefaultHost();


    /**
     * 设置默认的hostname.
     *
     * @param defaultHost The new default host
     */
    public void setDefaultHost(String defaultHost);


    /**
     * 检索JvmRouteId.
     */
    public String getJvmRoute();


    /**
     * 设置JvmRouteId.
     *
     * @param jvmRouteId JVM Route ID. 集群中的每个引擎必须具有唯一的JVM路由ID.
     */
    public void setJvmRoute(String jvmRouteId);


    /**
     * 返回关联的<code>Service</code>.
     */
    public Service getService();


    /**
     * 设置关联的<code>Service</code>.
     *
     * @param service The service that owns this Engine
     */
    public void setService(Service service);
}
