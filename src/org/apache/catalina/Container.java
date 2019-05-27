package org.apache.catalina;

import java.beans.PropertyChangeListener;
import java.io.File;

import javax.management.ObjectName;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;


/**
 * <b>Container</b>可以处理从客户端接收到的请求, 以及返回基于这些请求的响应信息. 
 * 一个Container 可以可选地支持按照运行时配置的顺序处理请求的Valves的管道, 通过实现<b>Pipeline</b>接口.
 * <p>
 * Containers 在 Catalina中存在于几个概念层次. 
 * 以下示例代表常见情况:
 * <ul>
 * <li><b>Engine</b> - 整个Catalina servlet引擎, 最有可能包含一个或更多的子容器是主机或上下文实现，或者其他自定义组.
 * <li><b>Host</b> - 包含若干上下文的虚拟主机.
 * <li><b>Context</b> - 单个ServletContext, 通常会包含一个或多个Wrapper来支持servlet.
 * <li><b>Wrapper</b> - 一个单独的servlet定义 
 *     (它可以支持多个servlet实例,如果servlet自身实现SingleThreadModel接口).
 * </ul>
 * Catalina的给定的部署不需要包含所有上述水平的容器.例如, 嵌入在网络设备（例如路由器）中的管理应用程序可能只包含单个上下文和一些包装器,
 * 或者，如果应用程序相对较小，则只使用一个包装器。
 * 因此，容器的实现需要设计，以便在给定的部署中没有父容器时正确地操作它们。
 * <p>
 * 容器也可以与一些支持组件相关联，这些组件提供了可共享的功能（通过将其附加到父容器）或单独定制。 
 * 下列支持组件目前已被确认:
 * <ul>
 * <li><b>Loader</b> - 类装载器用于整合新的java类容器到Catalina 运行的JVM中.
 * <li><b>Logger</b> - <code>ServletContext</code>接口实现类中的<code>log()</code>方法.
 * <li><b>Manager</b> -与此容器相关联的Session池管理器.
 * <li><b>Realm</b> - 安全域名的只读接口, 用于验证用户标识及其相应角色.
 * <li><b>Resources</b> - JNDI 目录支持静态资源访问的上下文,在Catalina被嵌入在一个更大的服务器时，启用自定义连接到现有的服务器组件.
 * </ul>
 */
public interface Container extends Lifecycle {


    // ----------------------------------------------------- Manifest Constants


    /**
     * 当<code>addChild()</code>方法添加子容器的时候, 发送ContainerEvent 事件类型.
     */
    public static final String ADD_CHILD_EVENT = "addChild";


    /**
     * 如果容器支持pipelines，当<code>addValve()</code>方法添加一个valve的时候， ContainerEvent 事件类型发送.
     */
    public static final String ADD_VALVE_EVENT = "addValve";


    /**
     * 当<code>removeChild()</code>方法移除子容器的时候,ContainerEvent 事件类型发送.
     */
    public static final String REMOVE_CHILD_EVENT = "removeChild";


    /**
     * 如果容器支持pipelines，当<code>removeValve()</code>方法移除一个valve的时候， ContainerEvent 事件类型发送.
     */
    public static final String REMOVE_VALVE_EVENT = "removeValve";


    // ------------------------------------------------------------- Properties

    /**
     * 返回当前Container关联的Loader. 如果没有关联的Loader, 返回父Container关联的Loader; 如果还没有, 返回<code>null</code>.
     */
    public Log getLogger();


    /**
     * 返回容器将使用的记录器名称.
     * 
     * @return 用于记录消息的此容器的缩写名称
     */
    public String getLogName();


    /**
     * 获取这个容器的 JMX名字.
     */
    public ObjectName getObjectName();


    /**
     * 获取这个容器注册的JMX 域名.
     *
     * @return JMX 域名
     */
    public String getDomain();


    /**
     * 计算添加到对象的{@link ObjectName}的 key属性表示它与这个容器关联.
     *
     * @return  一种适用于附加到ObjectName的字符串
     *
     */
    public String getMBeanKeyProperties();


    /**
     * 返回管理这个Container关联的Valve的Pipeline对象.
     *
     * @return The Pipeline
     */
    public Pipeline getPipeline();


    /**
     * 获取这个容器的Cluster.
     *
     * @return 这个容器关联的Cluster. 如果没有关联的Cluster, 返回父级容器关联的Cluster; 否则返回<code>null</code>.
     */
    public Cluster getCluster();


    /**
     * 设置这个容器关联的Cluster.
     *
     * @param cluster 这个容器关联的Cluster.
     */
    public void setCluster(Cluster cluster);


    /**
     * 获取这个容器及其子容器的backgroundProcess方法的执行间隔时间.
     * 如果它们的间隔时间不是负值, 则不会调用子容器(这意味着他们在使用自己的线程).将此值设置为正值将导致线程生成.
     * 在等待指定的时间之后, 线程将调用这个容器及其子容器上非正延迟值的{@link #backgroundProcess()}方法.
     */
    public int getBackgroundProcessorDelay();


    /**
     * 设置这个容器及其子容器的backgroundProcess方法的执行间隔时间.
     *
     * @param delay 间隔时间，秒
     */
    public void setBackgroundProcessorDelay(int delay);


    /**
     * 返回Container的名字。在属于特定父类的子容器中, Container名称必须唯一
     */
    public String getName();


    /**
     *设置Container名称. 在属于特定父类的子容器中, Container名称必须唯一
     *
     * @param name 容器名称
     *
     * @exception IllegalStateException 如果Container已经加入到父容器的子列表中，名称不能再被修改
     */
    public void setName(String name);


    /**
     * 返回父容器. 如果没有，返回<code>null</code>.
     */
    public Container getParent();


    /**
     * 设置父容器. 通过抛出异常，这个容器可以拒绝连接到指定的容器.
     *
     * @param container 父容器
     *
     * @exception IllegalArgumentException 如果这个容器拒绝连接到指定的容器
     */
    public void setParent(Container container);


    /**
     * @return 父类加载器. 如果未设置, 返回{@link #getParent()}.{@link #getParentClassLoader()}.
     * 			如果没有父级容器, 返回系统类加载器.
     */
    public ClassLoader getParentClassLoader();


    /**
     * 设置父类加载器.
     * 仅仅在loader被配置之前，这个调用是有效的, 并且应该将指定值作为参数传递给类加载器构造函数.
     *
     * @param parent 父类加载器
     */
    public void setParentClassLoader(ClassLoader parent);


    /**
     * 返回关联的Realm .如果没有，返回父Container关联的Realm。如果都没有，返回<code>null</code>
     */
    public Realm getRealm();


    /**
     * 设置关联的Realm.
     *
     * @param realm 关联的Realm
     */
    public void setRealm(Realm realm);


    // --------------------------------------------------------- Public Methods


    /**
     * 执行周期任务, 例如重新加载等.该方法将在该容器的类加载上下文中调用. 异常将被捕获和记录.
     */
    public void backgroundProcess();


    /**
     * 添加子容器。 
     * 添加子容器之前,子容器的<code>setParent()</code>方法必须被调用，并将这个容器作为参数.
     *
     * @param child 要添加的子容器
     *
     * @exception IllegalArgumentException 如果子容器的<code>setParent()</code>方法抛出异常 
     * @exception IllegalArgumentException 如果新的子容器没有唯一的名称
     * @exception IllegalStateException 如果容器不支持子容器
     */
    public void addChild(Container child);


    /**
     * 添加一个容器事件监听器.
     *
     * @param listener The listener to add
     */
    public void addContainerListener(ContainerListener listener);


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * 返回子容器;没有返回<code>null</code>
     *
     * @param name 子容器名称
     */
    public Container findChild(String name);


    /**
     * 返回所有的子容器，如果没有，返回0长度的数组
     */
    public Container[] findChildren();


    /**
     * 返回所有的容器监听器，如果没有，返回0长度的数组
     */
    public ContainerListener[] findContainerListeners();


    /**
     * 移除子容器.
     *
     * @param child 要删除的子容器
     */
    public void removeChild(Container child);


    /**
     * 移除容器事件监听器.
     *
     * @param listener 要删除的监听器
     */
    public void removeContainerListener(ContainerListener listener);


    /**
     * 移除属性修改监听器.
     *
     * @param listener 要删除的监听器
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * 通知所有容器事件侦听器，该容器已发生特定事件. 默认实现使用调用线程同步执行此通知.
     *
     * @param type 事件类型
     * @param data 事件数据
     */
    public void fireContainerEvent(String type, Object data);


    /**
     * 记录一个针对这个容器的请求/响应，但是在处理链前面已经处理过了，因此请求/响应仍然出现在正确的访问日志中.
     * 
     * @param request
     * @param response
     * @param time  处理请求/响应的时间，以毫秒为单位(未知使用 0)
     * @param useDefault  请求/响应是否应该记录在引擎的默认访问日志中
     */
    public void logAccess(Request request, Response response, long time,
            boolean useDefault);


    /**
     * 获取 AccessLog，用于记录针对该容器的请求/响应.
     * 当处理链前面的请求/响应被处理（或拒绝）时，通常使用这种方式，以便请求/响应仍然出现在正确的访问日志中.
     */
    public AccessLog getAccessLog();


    /**
     * 获取用于启动和停止与此容器关联的任何子线程的可用线程数. 这允许对并行处理的子线程启动/停止调用.
     *
     * @return 当前用于启动/停止与此容器关联的子线程的配置数
     */
    public int getStartStopThreads();


    /**
     * 设置用于启动和停止与此容器关联的任何子线程的可用线程数. 这允许对并行处理的子线程启动/停止调用.
     * 
     * @param   startStopThreads    使用的线程数
     */
    public void setStartStopThreads(int startStopThreads);


    /**
     * 获取CATALINA_BASE的位置.
     *
     * @return  CATALINA_BASE的位置.
     */
    public File getCatalinaBase();


    /**
     * 获取CATALINA_HOME的位置.
     *
     * @return CATALINA_HOME的位置.
     */
    public File getCatalinaHome();
}
