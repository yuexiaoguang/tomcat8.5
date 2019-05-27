package org.apache.catalina.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ObjectName;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;


/**
 * <b>Container</b>接口的抽象实现类,提供几乎所有实现所需的公共功能. 
 * 继承这个类的类必须实现<code>getInfo()</code>方法, 并可能实现覆盖<code>invoke()</code>方法.
 * <p>
 * 这个抽象基类的所有子类将包括对Pipeline对象的支持，该Pipeline对象定义要接收的每个请求执行的处理 通过这个类的<code>invoke()</code>方法, 
 * 运用“责任链”设计模式. 
 * 子类应该将自己的处理功能封装为<code>Valve</code>, 并通过调用<code>setBasic()</code>将此Valve配置到Pipeline中.
 * <p>
 * 此实现类触发属性更改事件, 根据JavaBeans设计模式, 对于单属性的更改. 
 * 此外，它触发以下<code>ContainerEvent</code>事件，监听使用<code>addContainerListener()</code>注册自己的实例:
 * <table border=1>
 *   <caption>ContainerEvents fired by this implementation</caption>
 *   <tr>
 *     <th>Type</th>
 *     <th>Data</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td align=center><code>addChild</code></td>
 *     <td align=center><code>Container</code></td>
 *     <td>Child container added to this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>{@link #getPipeline() pipeline}.addValve</code></td>
 *     <td align=center><code>Valve</code></td>
 *     <td>Valve added to this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>removeChild</code></td>
 *     <td align=center><code>Container</code></td>
 *     <td>Child container removed from this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>{@link #getPipeline() pipeline}.removeValve</code></td>
 *     <td align=center><code>Valve</code></td>
 *     <td>Valve removed from this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>start</code></td>
 *     <td align=center><code>null</code></td>
 *     <td>Container was started.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>stop</code></td>
 *     <td align=center><code>null</code></td>
 *     <td>Container was stopped.</td>
 *   </tr>
 * </table>
 * 引发其他事件的子类应该在实现类的类注释中记录它们.
 */
public abstract class ContainerBase extends LifecycleMBeanBase
        implements Container {

    private static final Log log = LogFactory.getLog(ContainerBase.class);

    /**
     * 随着这个类的权限执行addChild.
     * addChild可以通过堆栈上的XML解析器调用,
     * 这允许XML解析器拥有比Tomcat更少的特权.
     */
    protected class PrivilegedAddChild implements PrivilegedAction<Void> {

        private final Container child;

        PrivilegedAddChild(Container child) {
            this.child = child;
        }

        @Override
        public Void run() {
            addChildInternal(child);
            return null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 属于这个Container的子Container, 使用名称作为key.
     */
    protected final HashMap<String, Container> children = new HashMap<>();


    /**
     * 此组件的处理器延迟.
     */
    protected int backgroundProcessorDelay = -1;


    /**
     * 容器事件监听器.
     * 使用CopyOnWriteArrayList， 因为监听器可能添加/删除自己或其它监听器，可能有触发死锁的ReadWriteLock.
     */
    protected final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();

    protected Log logger = null;


    protected String logName = null;


    protected Cluster cluster = null;
    private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();


    protected String name = null;


    protected Container parent = null;


    /**
     * 在安装Loader时要配置的父类加载程序.
     */
    protected ClassLoader parentClassLoader = null;


    protected final Pipeline pipeline = new StandardPipeline(this);


    private volatile Realm realm = null;


    /**
     * 用于控制访问Realm的Lock.
     */
    private final ReadWriteLock realmLock = new ReentrantReadWriteLock();


    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 添加的子容器是否自动启动.
     */
    protected boolean startChildren = true;

    /**
     * 此组件的属性更改支持.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 后台线程.
     */
    private Thread thread = null;


    /**
     * 后台线程完成信号量.
     */
    private volatile boolean threadDone = false;


    /**
     * 用于此容器通常处理的请求的访问日志.
     */
    protected volatile AccessLog accessLog = null;
    private volatile boolean accessLogScanComplete = false;


    /**
     * 与此容器关联的任何子进程可处理的启动和停止事件的线程数.
     */
    private int startStopThreads = 1;
    protected ThreadPoolExecutor startStopExecutor;


    // ------------------------------------------------------------- Properties

    @Override
    public int getStartStopThreads() {
        return startStopThreads;
    }

    /**
     * 处理特殊值.
     */
    private int getStartStopThreadsInternal() {
        int result = getStartStopThreads();

        // 正值不变
        if (result > 0) {
            return result;
        }

        // Zero == Runtime.getRuntime().availableProcessors()
        // -ve  == Runtime.getRuntime().availableProcessors() + value
        // 两个一样
        result = Runtime.getRuntime().availableProcessors() + result;
        if (result < 1) {
            result = 1;
        }
        return result;
    }

    @Override
    public void setStartStopThreads(int startStopThreads) {
        this.startStopThreads = startStopThreads;

        // 使用本地副本确保线程安全
        ThreadPoolExecutor executor = startStopExecutor;
        if (executor != null) {
            int newThreads = getStartStopThreadsInternal();
            executor.setMaximumPoolSize(newThreads);
            executor.setCorePoolSize(newThreads);
        }
    }


    /**
     * 在这个容器和它的子容器的backgroundProcess 方法的执行间隔.
     * 如果它们的延迟值不是负值, 不会调用子容器 (这意味着他们正在使用自己的线程). 将此值设置为正值将导致线程生成. 在等待指定的时间之后, 
     * 该线程将调用这个容器和它的子容器的executePeriodic 方法.
     */
    @Override
    public int getBackgroundProcessorDelay() {
        return backgroundProcessorDelay;
    }


    /**
     * 设置在这个容器和它的子容器的backgroundProcess 方法的执行间隔.
     *
     * @param delay backgroundProcess 方法的执行间隔, 秒
     */
    @Override
    public void setBackgroundProcessorDelay(int delay) {
        backgroundProcessorDelay = delay;
    }


    @Override
    public Log getLogger() {

        if (logger != null)
            return (logger);
        logger = LogFactory.getLog(getLogName());
        return (logger);

    }


    /**
     * @return 用于记录消息的容器的缩写名称
     */
    @Override
    public String getLogName() {

        if (logName != null) {
            return logName;
        }
        String loggerName = null;
        Container current = this;
        while (current != null) {
            String name = current.getName();
            if ((name == null) || (name.equals(""))) {
                name = "/";
            } else if (name.startsWith("##")) {
                name = "/" + name;
            }
            loggerName = "[" + name + "]"
                + ((loggerName != null) ? ("." + loggerName) : "");
            current = current.getParent();
        }
        logName = ContainerBase.class.getName() + "." + loggerName;
        return logName;

    }


    /**
     * 返回关联的Cluster. 
     * 如果没有关联的Cluster, 返回父级关联的Cluster; 否则返回<code>null</code>.
     */
    @Override
    public Cluster getCluster() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            if (cluster != null)
                return cluster;

            if (parent != null)
                return parent.getCluster();

            return null;
        } finally {
            readLock.unlock();
        }
    }


    /*
     * 提供仅访问连接到该容器的集群组件的访问权限.
     */
    protected Cluster getClusterInternal() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            return cluster;
        } finally {
            readLock.unlock();
        }
    }


    /**
     * 设置关联的Cluster.
     *
     * @param cluster The newly associated Cluster
     */
    @Override
    public void setCluster(Cluster cluster) {

        Cluster oldCluster = null;
        Lock writeLock = clusterLock.writeLock();
        writeLock.lock();
        try {
            // Change components if necessary
            oldCluster = this.cluster;
            if (oldCluster == cluster)
                return;
            this.cluster = cluster;

            // Stop the old component if necessary
            if (getState().isAvailable() && (oldCluster != null) &&
                (oldCluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldCluster).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setCluster: stop: ", e);
                }
            }

            // Start the new component if necessary
            if (cluster != null)
                cluster.setContainer(this);

            if (getState().isAvailable() && (cluster != null) &&
                (cluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) cluster).start();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setCluster: start: ", e);
                }
            }
        } finally {
            writeLock.unlock();
        }

        // 通知相关监听器
        support.firePropertyChange("cluster", oldCluster, cluster);
    }


    /**
     * 返回Container的名称(适合人类使用). 
     * 在属于特定父类的子容器中, Container名称必须唯一.
     */
    @Override
    public String getName() {
        return (name);
    }


    /**
     * 设置Container的名称(适合人类使用). 
     * 在属于特定父类的子容器中, Container名称必须唯一.
     *
     * @param name 容器的名称
     *
     * @exception IllegalStateException 如果这个容器已经添加到父容器的子目录中(此后，名称不得更改)
     */
    @Override
    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }


    /**
     * 添加子容器时是否自动启动子容器.
     *
     * @return <code>true</code>如果启动子容器
     */
    public boolean getStartChildren() {
        return (startChildren);
    }


    /**
     * 添加子容器时是否自动启动子容器.
     *
     * @param startChildren New value of the startChildren flag
     */
    public void setStartChildren(boolean startChildren) {

        boolean oldStartChildren = this.startChildren;
        this.startChildren = startChildren;
        support.firePropertyChange("startChildren", oldStartChildren, this.startChildren);
    }


    /**
     * 返回父级 Container. 如果没有，返回<code>null</code>.
     */
    @Override
    public Container getParent() {
        return (parent);
    }


    /**
     * 设置父级 Container. 
     * 通过抛出异常，这个容器可以拒绝连接到指定的容器.
     *
     * @param container 父级容器
     *
     * @exception IllegalArgumentException 这个容器拒绝连接到指定的容器.
     */
    @Override
    public void setParent(Container container) {

        Container oldParent = this.parent;
        this.parent = container;
        support.firePropertyChange("parent", oldParent, this.parent);

    }


    /**
     * 返回父类加载器.
     * 只有在一个Loader已经配置之后，这个调用才有意义.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (parent != null) {
            return (parent.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());

    }


    /**
     * 设置父类加载器
     * 只有在一个Loader配置之前，这个调用才有意义, 并且指定的值（如果非null）应作为参数传递给类装入器构造函数.
     *
     *
     * @param parent The new parent class loader
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);

    }


    /**
     * 返回管理Valves的Pipeline对象.
     */
    @Override
    public Pipeline getPipeline() {
        return (this.pipeline);
    }


    /**
     * 返回关联的Realm. 
     * 如果没有关联的Realm, 返回父级关联的Realm; 否则返回<code>null</code>.
     */
    @Override
    public Realm getRealm() {

        Lock l = realmLock.readLock();
        l.lock();
        try {
            if (realm != null)
                return (realm);
            if (parent != null)
                return (parent.getRealm());
            return null;
        } finally {
            l.unlock();
        }
    }


    protected Realm getRealmInternal() {
        Lock l = realmLock.readLock();
        l.lock();
        try {
            return realm;
        } finally {
            l.unlock();
        }
    }

    /**
     * 设置关联的Realm.
     *
     * @param realm The newly associated Realm
     */
    @Override
    public void setRealm(Realm realm) {

        Lock l = realmLock.writeLock();
        l.lock();
        try {
            // Change components if necessary
            Realm oldRealm = this.realm;
            if (oldRealm == realm)
                return;
            this.realm = realm;

            // Stop the old component if necessary
            if (getState().isAvailable() && (oldRealm != null) &&
                (oldRealm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldRealm).stop();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: stop: ", e);
                }
            }

            // Start the new component if necessary
            if (realm != null)
                realm.setContainer(this);
            if (getState().isAvailable() && (realm != null) &&
                (realm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) realm).start();
                } catch (LifecycleException e) {
                    log.error("ContainerBase.setRealm: start: ", e);
                }
            }

            // Report this property change to interested listeners
            support.firePropertyChange("realm", oldRealm, this.realm);
        } finally {
            l.unlock();
        }

    }


    // ------------------------------------------------------ Container Methods


    /**
     * 添加一个子级Container如果支持的话.
     * 在将该容器添加到子组之前, 子容器的<code>setParent()</code>方法必须被调用, 将这个Container作为一个参数. 
     * 这个方法可能抛出一个<code>IllegalArgumentException</code>, 如果这个Container选择不附加到指定的容器, 
     * 在这种情况下，它不会被添加
     *
     * @param child New child Container to be added
     *
     * @exception IllegalArgumentException 如果子级Container的<code>setParent()</code>方法抛出异常
     * @exception IllegalArgumentException 如果子容器没有一个唯一名称
     * @exception IllegalStateException 如果这个Container不支持子级Containers
     */
    @Override
    public void addChild(Container child) {
        if (Globals.IS_SECURITY_ENABLED) {
            PrivilegedAction<Void> dp =
                new PrivilegedAddChild(child);
            AccessController.doPrivileged(dp);
        } else {
            addChildInternal(child);
        }
    }

    private void addChildInternal(Container child) {

        if( log.isDebugEnabled() )
            log.debug("Add child " + child + " " + this);
        synchronized(children) {
            if (children.get(child.getName()) != null)
                throw new IllegalArgumentException("addChild:  Child name '" +
                                                   child.getName() +
                                                   "' is not unique");
            child.setParent(this);  // May throw IAE
            children.put(child.getName(), child);
        }

        // Start child
        // 不要在同步块内这样做 - 启动可能是一个缓慢的过程，锁定子对象可能会在别处引起问题
        try {
            if ((getState().isAvailable() ||
                    LifecycleState.STARTING_PREP.equals(getState())) &&
                    startChildren) {
                child.start();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.addChild: start: ", e);
            throw new IllegalStateException("ContainerBase.addChild: start: " + e);
        } finally {
            fireContainerEvent(ADD_CHILD_EVENT, child);
        }
    }


    /**
     * 添加一个容器事件监听器.
     *
     * @param listener The listener to add
     */
    @Override
    public void addContainerListener(ContainerListener listener) {
        listeners.add(listener);
    }


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 返回指定名称的子级Container; 否则返回<code>null</code>
     *
     * @param name 要检索的子容器的名称
     */
    @Override
    public Container findChild(String name) {
        if (name == null) {
            return null;
        }
        synchronized (children) {
            return children.get(name);
        }
    }


    /**
     * 返回子级Container集合.
     * 如果没有子级容器, 将返回一个零长度数组.
     */
    @Override
    public Container[] findChildren() {
        synchronized (children) {
            Container results[] = new Container[children.size()];
            return children.values().toArray(results);
        }
    }


    /**
     * 返回容器监听器集合.
     * 如果没有, 将返回一个零长度数组.
     */
    @Override
    public ContainerListener[] findContainerListeners() {
        ContainerListener[] results =
            new ContainerListener[0];
        return listeners.toArray(results);
    }


    /**
     * 移除子级Container.
     *
     * @param child Existing child Container to be removed
     */
    @Override
    public void removeChild(Container child) {

        if (child == null) {
            return;
        }

        try {
            if (child.getState().isAvailable()) {
                child.stop();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: stop: ", e);
        }

        try {
            // child.destroy()可能已经调用，他也可以触发这个调用. 如果是这种情况, 不需要再销毁子容器.
            if (!LifecycleState.DESTROYING.equals(child.getState())) {
                child.destroy();
            }
        } catch (LifecycleException e) {
            log.error("ContainerBase.removeChild: destroy: ", e);
        }

        synchronized(children) {
            if (children.get(child.getName()) == null)
                return;
            children.remove(child.getName());
        }

        fireContainerEvent(REMOVE_CHILD_EVENT, child);
    }


    /**
     * 移除一个容器事件监听器.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removeContainerListener(ContainerListener listener) {
        listeners.remove(listener);
    }


    /**
     * 移除一个属性修改监听器.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    @Override
    protected void initInternal() throws LifecycleException {
        BlockingQueue<Runnable> startStopQueue = new LinkedBlockingQueue<>();
        startStopExecutor = new ThreadPoolExecutor(
                getStartStopThreadsInternal(),
                getStartStopThreadsInternal(), 10, TimeUnit.SECONDS,
                startStopQueue,
                new StartStopThreadFactory(getName() + "-startStop-"));
        startStopExecutor.allowCoreThreadTimeOut(true);
        super.initInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // 启动下属组件
        logger = null;
        getLogger();
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).start();
        }
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).start();
        }

        // 启动子容器
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StartChild(children[i])));
        }

        boolean fail = false;
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString("containerBase.threadedStartFailed"), e);
                fail = true;
            }

        }
        if (fail) {
            throw new LifecycleException(
                    sm.getString("containerBase.threadedStartFailed"));
        }

        // Start the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle)
            ((Lifecycle) pipeline).start();


        setState(LifecycleState.STARTING);

        // Start our thread
        threadStart();

    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        // Stop our thread
        threadStop();

        setState(LifecycleState.STOPPING);

        // Stop the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle &&
                ((Lifecycle) pipeline).getState().isAvailable()) {
            ((Lifecycle) pipeline).stop();
        }

        // Stop our child containers, if any
        Container children[] = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            results.add(startStopExecutor.submit(new StopChild(children[i])));
        }

        boolean fail = false;
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString("containerBase.threadedStopFailed"), e);
                fail = true;
            }
        }
        if (fail) {
            throw new LifecycleException(
                    sm.getString("containerBase.threadedStopFailed"));
        }

        // Stop our subordinate components, if any
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).stop();
        }
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).stop();
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {

        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).destroy();
        }
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).destroy();
        }

        // Stop the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).destroy();
        }

        // Remove children now this container is being destroyed
        for (Container child : findChildren()) {
            removeChild(child);
        }

        // Required if the child is destroyed directly.
        if (parent != null) {
            parent.removeChild(this);
        }

        // If init fails, this may be null
        if (startStopExecutor != null) {
            startStopExecutor.shutdownNow();
        }

        super.destroyInternal();
    }


    /**
     * 检查此容器是否有访问日志，如果未找到，请查看父节点. 如果没有父级, 使用NoOp访问日志.
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            getAccessLog().log(request, response, time);
            logged = true;
        }

        if (getParent() != null) {
            // 无需使用默认记录器请求/响应一次就记录一次
            getParent().logAccess(request, response, time, (useDefault && !logged));
        }
    }

    @Override
    public AccessLog getAccessLog() {

        if (accessLogScanComplete) {
            return accessLog;
        }

        AccessLogAdapter adapter = null;
        Valve valves[] = getPipeline().getValves();
        for (Valve valve : valves) {
            if (valve instanceof AccessLog) {
                if (adapter == null) {
                    adapter = new AccessLogAdapter((AccessLog) valve);
                } else {
                    adapter.add((AccessLog) valve);
                }
            }
        }
        if (adapter != null) {
            accessLog = adapter;
        }
        accessLogScanComplete = true;
        return accessLog;
    }

    // ------------------------------------------------------- Pipeline Methods


    /**
     * 添加一个新的Valve到管道的末尾. 
     * 在添加Valve之前, Valve的<code>setContainer</code>方法必须调用, 将这个Container作为一个参数.
     * 这个方法可能抛出一个<code>IllegalArgumentException</code>，如果这个Valve不能关联到这个Container;
     * 或者<code>IllegalStateException</code>,如果已经关联到另外一个Container.
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException 如果这个Container拒绝接受指定的Valve
     * @exception IllegalArgumentException 如果指定的Valve拒绝关联到Container
     * @exception IllegalStateException 如果指定的Valve已经关联到另外一个Container
     */
    public synchronized void addValve(Valve valve) {
        pipeline.addValve(valve);
    }


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    @Override
    public void backgroundProcess() {

        if (!getState().isAvailable())
            return;

        Cluster cluster = getClusterInternal();
        if (cluster != null) {
            try {
                cluster.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.cluster",
                        cluster), e);
            }
        }
        Realm realm = getRealmInternal();
        if (realm != null) {
            try {
                realm.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.realm", realm), e);
            }
        }
        Valve current = pipeline.getFirst();
        while (current != null) {
            try {
                current.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.valve", current), e);
            }
            current = current.getNext();
        }
        fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }


    @Override
    public File getCatalinaBase() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaHome();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 通知所有容器事件监听器，该容器已发生特定事件.  默认实现使用调用线程同步执行此通知.
     *
     * @param type 事件类型
     * @param data 事件数据
     */
    @Override
    public void fireContainerEvent(String type, Object data) {

        if (listeners.size() < 1)
            return;

        ContainerEvent event = new ContainerEvent(this, type, data);
        // 注意每个都使用内部的迭代器，所以这是安全的
        for (ContainerListener listener : listeners) {
            listener.containerEvent(event);
        }
    }


    // -------------------- JMX and Registration  --------------------

    @Override
    protected String getDomainInternal() {

        Container p = this.getParent();
        if (p == null) {
            return null;
        } else {
            return p.getDomain();
        }
    }


    @Override
    public String getMBeanKeyProperties() {
        Container c = this;
        StringBuilder keyProperties = new StringBuilder();
        int containerCount = 0;

        // 容器层次结构, 为每个容器的名称添加一个组件
        while (!(c instanceof Engine)) {
            if (c instanceof Wrapper) {
                keyProperties.insert(0, ",servlet=");
                keyProperties.insert(9, c.getName());
            } else if (c instanceof Context) {
                keyProperties.insert(0, ",context=");
                ContextName cn = new ContextName(c.getName(), false);
                keyProperties.insert(9,cn.getDisplayName());
            } else if (c instanceof Host) {
                keyProperties.insert(0, ",host=");
                keyProperties.insert(6, c.getName());
            } else if (c == null) {
                // 可能发生在单元测试和/或一些嵌入场景中
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append("=null");
                break;
            } else {
                // Should never happen...
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append('=');
                keyProperties.append(c.getName());
            }
            c = c.getParent();
        }
        return keyProperties.toString();
    }

    public ObjectName[] getChildren() {
        List<ObjectName> names = new ArrayList<>(children.size());
        Iterator<Container>  it = children.values().iterator();
        while (it.hasNext()) {
            Object next = it.next();
            if (next instanceof ContainerBase) {
                names.add(((ContainerBase)next).getObjectName());
            }
        }
        return names.toArray(new ObjectName[names.size()]);
    }


    // -------------------- Background Thread --------------------

    /**
     * 启动后台线程将定期检查会话超时.
     */
    protected void threadStart() {

        if (thread != null)
            return;
        if (backgroundProcessorDelay <= 0)
            return;

        threadDone = false;
        String threadName = "ContainerBackgroundProcessor[" + toString() + "]";
        thread = new Thread(new ContainerBackgroundProcessor(), threadName);
        thread.setDaemon(true);
        thread.start();

    }


    /**
     * 停止定期检查会话超时的后台线程.
     */
    protected void threadStop() {

        if (thread == null)
            return;

        threadDone = true;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            // Ignore
        }
        thread = null;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Container parent = getParent();
        if (parent != null) {
            sb.append(parent.toString());
            sb.append('.');
        }
        sb.append(this.getClass().getSimpleName());
        sb.append('[');
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }


    // -------------------------------------- ContainerExecuteDelay Inner Class

    /**
     * 执行这个容器和子容器的 backgroundProcess方法，在固定延迟后.
     */
    protected class ContainerBackgroundProcessor implements Runnable {

        @Override
        public void run() {
            Throwable t = null;
            String unexpectedDeathMessage = sm.getString(
                    "containerBase.backgroundProcess.unexpectedThreadDeath",
                    Thread.currentThread().getName());
            try {
                while (!threadDone) {
                    try {
                        Thread.sleep(backgroundProcessorDelay * 1000L);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (!threadDone) {
                        processChildren(ContainerBase.this);
                    }
                }
            } catch (RuntimeException|Error e) {
                t = e;
                throw e;
            } finally {
                if (!threadDone) {
                    log.error(unexpectedDeathMessage, t);
                }
            }
        }

        protected void processChildren(Container container) {
            ClassLoader originalClassLoader = null;

            try {
                if (container instanceof Context) {
                    Loader loader = ((Context) container).getLoader();
                    // Loader将会是 null, 对于FailedContext实例
                    if (loader == null) {
                        return;
                    }

                    // 确保Contexts 和 Wrapper的后台处理是在Web应用类加载器中执行的
                    originalClassLoader = ((Context) container).bind(false, null);
                }
                container.backgroundProcess();
                Container[] children = container.findChildren();
                for (int i = 0; i < children.length; i++) {
                    if (children[i].getBackgroundProcessorDelay() <= 0) {
                        processChildren(children[i]);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error("Exception invoking periodic operation: ", t);
            } finally {
                if (container instanceof Context) {
                    ((Context) container).unbind(false, originalClassLoader);
               }
            }
        }
    }


    // ----------------------------- Inner classes used with start/stop Executor

    private static class StartChild implements Callable<Void> {

        private Container child;

        public StartChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            child.start();
            return null;
        }
    }

    private static class StopChild implements Callable<Void> {

        private Container child;

        public StopChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            if (child.getState().isAvailable()) {
                child.stop();
            }
            return null;
        }
    }

    private static class StartStopThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public StartStopThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
