package org.apache.catalina;

import java.io.File;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Catalina;

/**
 * <code>Server</code>代表整个Catalina servlet容器
 * 它的属性代表servlet容器的整体特性. <code>Server</code>可能包含一个或多个<code>Services</code>, 以及顶级的命名资源集.
 * <p>
 * 通常, 该接口的实现类也将实现<code>Lifecycle</code>, 因此当<code>start()</code>和
 * <code>stop()</code>方法被调用,所有定义的<code>Services</code>也将启动和关闭.
 * <p>
 * 在两者之间，实现必须在<code>port</code>属性指定的端口号上打开服务器套接字。
 * 当连接被接受时,读取第一行，并与指定的关闭命令进行比较.
 * 如果命令匹配，将关闭服务器
 * <p>
 * <strong>NOTE</strong> - 接口的所有实现类应该注册单例的实例到<code>ServerFactory</code>.
 */
public interface Server extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * @return 全局命名资源.
     */
    public NamingResourcesImpl getGlobalNamingResources();


    /**
     * 设置全局命名资源.
     *
     * @param globalNamingResources The new global naming resources
     */
    public void setGlobalNamingResources
        (NamingResourcesImpl globalNamingResources);


    /**
     * @return 全局命名资源上下文.
     */
    public javax.naming.Context getGlobalNamingContext();


    /**
     * @return 为关闭命令监听的端口号.
     */
    public int getPort();


    /**
     * 设置为关闭命令监听的端口号.
     *
     * @param port The new port number
     */
    public void setPort(int port);


    /**
     * @return 用来监听关机命令的地址.
     */
    public String getAddress();


    /**
     * 设置用来监听关机命令的地址.
     *
     * @param address The new address
     */
    public void setAddress(String address);


    /**
     * @return 等待的关闭命令字符串.
     */
    public String getShutdown();


    /**
     * 设置等待的关闭命令字符串.
     *
     * @param shutdown The new shutdown command
     */
    public void setShutdown(String shutdown);


    /**
     * @return 父类加载器. 如果未设置, 返回{@link #getCatalina()} {@link Catalina#getParentClassLoader()}.
     * 如果catalina未设置, 返回系统类加载器.
     */
    public ClassLoader getParentClassLoader();


    /**
     * 为该服务器设置父类加载器.
     *
     * @param parent The new parent class loader
     */
    public void setParentClassLoader(ClassLoader parent);


    /**
     * @return 外部Catalina startup/shutdown组件.
     */
    public Catalina getCatalina();

    /**
     * 设置外部Catalina startup/shutdown组件.
     *
     * @param catalina 外部Catalina组件
     */
    public void setCatalina(Catalina catalina);


    /**
     * @return 配置的基础目录. 注意home 和 base可能是相同的(默认的). 如果未设置将使用{@link #getCatalinaHome()}的返回值.
     */
    public File getCatalinaBase();

    /**
     * 设置配置的base 目录. 注意home 和 base可能是相同的(默认的).
     *
     * @param catalinaBase 配置的base 目录
     */
    public void setCatalinaBase(File catalinaBase);


    /**
     * @return 配置的home目录. 注意home 和 base可能是相同的(默认的).
     */
    public File getCatalinaHome();

    /**
     * 设置配置的home 目录. 注意home 和 base可能是相同的(默认的).
     *
     * @param catalinaHome 配置的home 目录
     */
    public void setCatalinaHome(File catalinaHome);


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个Service.
     *
     * @param service The Service to be added
     */
    public void addService(Service service);


    /**
     * 等待接收到正确的关闭命令，然后返回.
     */
    public void await();


    /**
     * 查找指定的Service
     *
     * @param name Service的名称
     * @return 指定的Service, 或<code>null</code>.
     */
    public Service findService(String name);


    /**
     * @return the set of Services defined within this Server.
     */
    public Service[] findServices();


    /**
     * Remove the specified Service from the set associated from this
     * Server.
     *
     * @param service The Service to be removed
     */
    public void removeService(Service service);


    /**
     * @return 在关联的JNDI命名上下文中操作必需的token.
     */
    public Object getNamingToken();
}
