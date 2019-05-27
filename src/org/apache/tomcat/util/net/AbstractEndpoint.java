package org.apache.tomcat.util.net;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint.Acceptor.AcceptorState;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.LimitLatch;
import org.apache.tomcat.util.threads.ResizableExecutor;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

/**
 * @param <S> 此端点管理的套接字的类型.
 */
public abstract class AbstractEndpoint<S> {

    // -------------------------------------------------------------- Constants

    protected static final StringManager sm = StringManager.getManager(AbstractEndpoint.class);

    public static interface Handler<S> {

        /**
         * 不同类型的套接字状态.
         */
        public enum SocketState {
            // TODO 将新状态添加到AsyncStateMachine并删除ASYNC_END
            OPEN, CLOSED, LONG, ASYNC_END, SENDFILE, UPGRADING, UPGRADED, SUSPENDED
        }


        /**
         * 使用给定的当前状态处理提供的套接字.
         *
         * @param socket 要处理的套接字
         * @param status 当前套接字状态
         *
         * @return 处理后的套接字状态
         */
        public SocketState process(SocketWrapperBase<S> socket,
                SocketEvent status);


        /**
         * 获取与处理程序关联的GlobalRequestProcessor.
         */
        public Object getGlobal();


        /**
         * 获取当前打开的套接字.
         *
         * @return 处理程序正在跟踪当前打开连接的套接字
         */
        public Set<S> getOpenSockets();

        /**
         * 释放与给定SocketWrapper关联的所有资源.
         *
         * @param socketWrapper socketWrapper释放资源
         */
        public void release(SocketWrapperBase<S> socketWrapper);


        /**
         * 通知处理程序端点已停止接受任何新连接.
         * 通常, 之后不久将终止端点, 但是端点可能会被恢复, 因此处理程序不应该假设端点将停止.
         */
        public void pause();


        /**
         * 回收与处理程序关联的资源.
         */
        public void recycle();
    }

    protected enum BindState {
        UNBOUND, BOUND_ON_INIT, BOUND_ON_START
    }

    public abstract static class Acceptor implements Runnable {
        public enum AcceptorState {
            NEW, RUNNING, PAUSED, ENDED
        }

        protected volatile AcceptorState state = AcceptorState.NEW;
        public final AcceptorState getState() {
            return state;
        }

        private String threadName;
        protected final void setThreadName(final String threadName) {
            this.threadName = threadName;
        }
        protected final String getThreadName() {
            return threadName;
        }
    }


    private static final int INITIAL_ERROR_DELAY = 50;
    private static final int MAX_ERROR_DELAY = 1600;


    // ----------------------------------------------------------------- Fields

    /**
     * 端点的运行状态.
     */
    protected volatile boolean running = false;


    /**
     * 每当端点暂停时，将设置为true.
     */
    protected volatile boolean paused = false;

    /**
     * 是否正在使用内部执行器
     */
    protected volatile boolean internalExecutor = true;


    /**
     * 由端点处理的nr连接的计数器
     */
    private volatile LimitLatch connectionLimitLatch = null;

    /**
     * Socket 属性
     */
    protected SocketProperties socketProperties = new SocketProperties();
    public SocketProperties getSocketProperties() {
        return socketProperties;
    }

    /**
     * 用于接受新连接并将它们传递给工作线程的线程.
     */
    protected Acceptor[] acceptors;

    /**
     * SocketProcessor对象的缓存
     */
    protected SynchronizedStack<SocketProcessorBase<S>> processorCache;

    private ObjectName oname = null;

    // ----------------------------------------------------------------- Properties

    private String defaultSSLHostConfigName = SSLHostConfig.DEFAULT_SSL_HOST_NAME;
    public String getDefaultSSLHostConfigName() {
        return defaultSSLHostConfigName;
    }
    public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
        this.defaultSSLHostConfigName = defaultSSLHostConfigName;
    }


    protected ConcurrentMap<String,SSLHostConfig> sslHostConfigs = new ConcurrentHashMap<>();
    /**
     * 添加给定的SSL主机配置.
     *
     * @param sslHostConfig 要添加的配置
     *
     * @throws IllegalArgumentException 如果主机名无效或已为该主机提供了配置
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig) throws IllegalArgumentException {
        addSslHostConfig(sslHostConfig, false);
    }
    /**
     * 添加给定的SSL主机配置, 可选地替换给定主机的现有配置.
     *
     * @param sslHostConfig 要添加的配置
     * @param replace       如果是 {@code true}, 允许替换现有配置; 否则任何此类尝试的替换都将触发异常
     *
     * @throws IllegalArgumentException 如果主机名无效或者已为该主机提供了配置，则不允许进行替换
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig, boolean replace) throws IllegalArgumentException {
        String key = sslHostConfig.getHostName();
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException(sm.getString("endpoint.noSslHostName"));
        }
        if (bindState != BindState.UNBOUND && isSSLEnabled()) {
            sslHostConfig.setConfigType(getSslConfigType());
            try {
                createSSLContext(sslHostConfig);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (replace) {
            SSLHostConfig previous = sslHostConfigs.put(key, sslHostConfig);
            if (previous != null) {
                unregisterJmx(sslHostConfig);
            }
            registerJmx(sslHostConfig);

            // 不要释放与替换的SSLHostConfig关联的任何SSLContexts. 它们可能仍然被现有连接使用，释放它们最多会破坏连接.
            // 让GC处理清理.
        } else {
            SSLHostConfig duplicate = sslHostConfigs.putIfAbsent(key, sslHostConfig);
            if (duplicate != null) {
                releaseSSLContext(sslHostConfig);
                throw new IllegalArgumentException(sm.getString("endpoint.duplicateSslHostName", key));
            }
            registerJmx(sslHostConfig);
        }
    }
    /**
     * 删除给定主机名的SSL主机配置, 如果存在这样的配置.
     *
     * @param hostName  与要删除的SSL主机配置关联的主机名
     *
     * @return 已删除的SSL主机配置
     */
    public SSLHostConfig removeSslHostConfig(String hostName) {
        // 主机名不区分大小写
        if (hostName != null && hostName.equalsIgnoreCase(getDefaultSSLHostConfigName())) {
            throw new IllegalArgumentException(
                    sm.getString("endpoint.removeDefaultSslHostConfig", hostName));
        }
        SSLHostConfig sslHostConfig = sslHostConfigs.remove(hostName);
        unregisterJmx(sslHostConfig);
        return sslHostConfig;
    }
    /**
     * 重新读取SSL主机的配置文件, 并使用更新的设置替换现有的SSL配置.
     * 请注意，即使设置保持不变，也会发生此替换.
     *
     * @param hostName 应重新加载配置的SSL主机. 这必须与当前的SSL主机匹配
     */
    public void reloadSslHostConfig(String hostName) {
        SSLHostConfig sslHostConfig = sslHostConfigs.get(hostName);
        if (sslHostConfig == null) {
            throw new IllegalArgumentException(
                    sm.getString("endpoint.unknownSslHostName", hostName));
        }
        addSslHostConfig(sslHostConfig, true);
    }
    /**
     * 重新读取所有SSL主机的配置文件，并使用更新的设置替换现有的SSL配置.
     * 请注意，即使设置保持不变，也会发生此替换.
     */
    public void reloadSslHostConfigs() {
        for (String hostName : sslHostConfigs.keySet()) {
            reloadSslHostConfig(hostName);
        }
    }
    public SSLHostConfig[] findSslHostConfigs() {
        return sslHostConfigs.values().toArray(new SSLHostConfig[0]);
    }

    protected abstract SSLHostConfig.Type getSslConfigType();

    /**
     * 为给定的SSLHostConfig创建SSLContext.
     *
     * @param sslHostConfig 应为其创建SSLContext的SSLHostConfig
     * 
     * @throws Exception 如果无法为给定的SSLHostConfig创建SSLContext
     */
    protected abstract void createSSLContext(SSLHostConfig sslHostConfig) throws Exception;

    /**
     * 释放与SSLHostConfig关联的SSLContext.
     *
     * @param sslHostConfig 应该释放SSLContext的SSLHostConfig
     */
    protected abstract void releaseSSLContext(SSLHostConfig sslHostConfig);

    protected SSLHostConfig getSSLHostConfig(String sniHostName) {
        SSLHostConfig result = null;

        if (sniHostName != null) {
            // First choice - 直接匹配
            result = sslHostConfigs.get(sniHostName);
            if (result != null) {
                return result;
            }
            // Second choice, 通配符
            int indexOfDot = sniHostName.indexOf('.');
            if (indexOfDot > -1) {
                result = sslHostConfigs.get("*" + sniHostName.substring(indexOfDot));
            }
        }

        // Fall-back. Use the default
        if (result == null) {
            result = sslHostConfigs.get(getDefaultSSLHostConfigName());
        }
        if (result == null) {
            // Should never happen.
            throw new IllegalStateException();
        }
        return result;
    }


    /**
     * 用户是否已请求尽可能使用发送文件?
     */
    private boolean useSendfile = true;
    public boolean getUseSendfile() {
        return useSendfile;
    }
    public void setUseSendfile(boolean useSendfile) {
        this.useSendfile = useSendfile;
    }


    /**
     * 在端点停止时, 等待内部执行程序终止的时间, 以毫秒为单位. 默认 5000 (5 seconds).
     */
    private long executorTerminationTimeoutMillis = 5000;

    public long getExecutorTerminationTimeoutMillis() {
        return executorTerminationTimeoutMillis;
    }

    public void setExecutorTerminationTimeoutMillis(
            long executorTerminationTimeoutMillis) {
        this.executorTerminationTimeoutMillis = executorTerminationTimeoutMillis;
    }


    /**
     * 接受者线程数.
     */
    protected int acceptorThreadCount = 1;

    public void setAcceptorThreadCount(int acceptorThreadCount) {
        this.acceptorThreadCount = acceptorThreadCount;
    }
    public int getAcceptorThreadCount() { return acceptorThreadCount; }


    /**
     * 接受者线程的优先级.
     */
    protected int acceptorThreadPriority = Thread.NORM_PRIORITY;
    public void setAcceptorThreadPriority(int acceptorThreadPriority) {
        this.acceptorThreadPriority = acceptorThreadPriority;
    }
    public int getAcceptorThreadPriority() { return acceptorThreadPriority; }


    private int maxConnections = 10000;
    public void setMaxConnections(int maxCon) {
        this.maxConnections = maxCon;
        LimitLatch latch = this.connectionLimitLatch;
        if (latch != null) {
            // 更新强制执行此操作的锁存器
            if (maxCon == -1) {
                releaseConnectionLatch();
            } else {
                latch.setLimit(maxCon);
            }
        } else if (maxCon > 0) {
            initializeConnectionLatch();
        }
    }

    public int  getMaxConnections() { return this.maxConnections; }

    /**
     * 返回此端点处理的当前连接数, 如果计算连接数 (当最大连接数有限时会发生这种情况), 或<code>-1</code>如果不计算.
     * 此属性将添加到此处，以便可以通过JMX检查此值. 它在“ThreadPool” MBean上可见.
     *
     * <p>在尝试接受新连接之前，计数会由Acceptor递增. 直到达到限制, 计数不能递增, 此值比实际的服务连接数多1（接受者数）.
     *
     * @return The count
     */
    public long getConnectionCount() {
        LimitLatch latch = connectionLimitLatch;
        if (latch != null) {
            return latch.getCount();
        }
        return -1;
    }

    /**
     * 基于线程池的外部执行器.
     */
    private Executor executor = null;
    public void setExecutor(Executor executor) {
        this.executor = executor;
        this.internalExecutor = (executor == null);
    }
    public Executor getExecutor() { return executor; }


    /**
     * 服务器套接字端口.
     */
    private int port;
    public int getPort() { return port; }
    public void setPort(int port ) { this.port=port; }


    public final int getLocalPort() {
        try {
            InetSocketAddress localAddress = getLocalAddress();
            if (localAddress == null) {
                return -1;
            }
            return localAddress.getPort();
        } catch (IOException ioe) {
            return -1;
        }
    }


    /**
     * 服务器套接字的地址.
     */
    private InetAddress address;
    public InetAddress getAddress() { return address; }
    public void setAddress(InetAddress address) { this.address = address; }


    /**
     * 获取服务器套接字绑定的网络地址. 这主要是为了在解锁服务器套接字时, 能够使用正确的地址, 因为如果没有专门设置地址, 它会删除所涉及的猜测工作.
     *
     * @return 服务器套接字正在监听的网络地址; 如果服务器套接字当前未绑定，则为null.
     *
     * @throws IOException 如果确定当前绑定的套接字有问题
     */
    protected abstract InetSocketAddress getLocalAddress() throws IOException;


    /**
     * 允许服务器开发人员指定应该用于服务器套接字的acceptCount (backlog). 默认 100.
     */
    private int acceptCount = 100;
    public void setAcceptCount(int acceptCount) { if (acceptCount > 0) this.acceptCount = acceptCount; }
    public int getAcceptCount() { return acceptCount; }
    @Deprecated
    public void setBacklog(int backlog) { setAcceptCount(backlog); }
    @Deprecated
    public int getBacklog() { return getAcceptCount(); }

    /**
     * 控制端点绑定端口的时间. <code>true</code>, 默认在 {@link #init()}上绑定端口; 在 {@link #destroy()}上解绑.
     * 如果设置为<code>false</code>, 在 {@link #start()}上绑定端口; 在 {@link #stop()}上解绑.
     */
    private boolean bindOnInit = true;
    public boolean getBindOnInit() { return bindOnInit; }
    public void setBindOnInit(boolean b) { this.bindOnInit = b; }
    private volatile BindState bindState = BindState.UNBOUND;

    /**
     * Keepalive超时, 如果没有设置, 则使用soTimeout.
     */
    private Integer keepAliveTimeout = null;
    public int getKeepAliveTimeout() {
        if (keepAliveTimeout == null) {
            return getSoTimeout();
        } else {
            return keepAliveTimeout.intValue();
        }
    }
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        this.keepAliveTimeout = Integer.valueOf(keepAliveTimeout);
    }


    /**
     * 套接字TCP没有延迟.
     *
     * @return 此端点创建的套接字的当前TCP无延迟设置
     */
    public boolean getTcpNoDelay() { return socketProperties.getTcpNoDelay();}
    public void setTcpNoDelay(boolean tcpNoDelay) { socketProperties.setTcpNoDelay(tcpNoDelay); }


    /**
     * Socket linger.
     *
     * @return 此端点创建的套接字的当前套接字延迟时间
     */
    public int getConnectionLinger() { return socketProperties.getSoLingerTime(); }
    public void setConnectionLinger(int connectionLinger) {
        socketProperties.setSoLingerTime(connectionLinger);
        socketProperties.setSoLingerOn(connectionLinger>=0);
    }
    @Deprecated
    public int getSoLinger() { return getConnectionLinger(); }
    @Deprecated
    public void setSoLinger(int soLinger) { setConnectionLinger(soLinger);}


    /**
     * Socket timeout.
     *
     * @return 此端点创建的套接字的当前套接字超时时间
     */
    public int getConnectionTimeout() { return socketProperties.getSoTimeout(); }
    public void setConnectionTimeout(int soTimeout) { socketProperties.setSoTimeout(soTimeout); }
    @Deprecated
    public int getSoTimeout() { return getConnectionTimeout(); }
    @Deprecated
    public void setSoTimeout(int soTimeout) { setConnectionTimeout(soTimeout); }

    /**
     * SSL engine.
     */
    private boolean SSLEnabled = false;
    public boolean isSSLEnabled() { return SSLEnabled; }
    public void setSSLEnabled(boolean SSLEnabled) { this.SSLEnabled = SSLEnabled; }

    /**
     * 标识端点是否支持ALPN.
     * 返回<code>true</code>表示 {@link #isSSLEnabled()}也将返回<code>true</code>.
     *
     * @return <code>true</code>如果端点在其当前配置中支持ALPN, 否则<code>false</code>.
     */
    public abstract boolean isAlpnSupported();

    private int minSpareThreads = 10;
    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
        Executor executor = this.executor;
        if (internalExecutor && executor instanceof java.util.concurrent.ThreadPoolExecutor) {
            // 内部执行程序应始终是j.u.c.ThreadPoolExecutor的实例，但如果端点未运行，则它可能为null.
            // 此检查还可以避免各种线程问题.
            ((java.util.concurrent.ThreadPoolExecutor) executor).setCorePoolSize(minSpareThreads);
        }
    }
    public int getMinSpareThreads() {
        return Math.min(getMinSpareThreadsInternal(), getMaxThreads());
    }
    private int getMinSpareThreadsInternal() {
        if (internalExecutor) {
            return minSpareThreads;
        } else {
            return -1;
        }
    }


    /**
     * 最大工作线程数.
     */
    private int maxThreads = 200;
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        Executor executor = this.executor;
        if (internalExecutor && executor instanceof java.util.concurrent.ThreadPoolExecutor) {
            // 内部执行程序应始终是j.u.c.ThreadPoolExecutor的实例，但如果端点未运行，则它可能为null.
            // 此检查还可以避免各种线程问题.
            ((java.util.concurrent.ThreadPoolExecutor) executor).setMaximumPoolSize(maxThreads);
        }
    }
    public int getMaxThreads() {
        if (internalExecutor) {
            return maxThreads;
        } else {
            return -1;
        }
    }


    /**
     * 工作线程的优先级.
     */
    protected int threadPriority = Thread.NORM_PRIORITY;
    public void setThreadPriority(int threadPriority) {
        // 执行程序启动后无法更改此设置
        this.threadPriority = threadPriority;
    }
    public int getThreadPriority() {
        if (internalExecutor) {
            return threadPriority;
        } else {
            return -1;
        }
    }


    /**
     * Max keep alive requests
     */
    private int maxKeepAliveRequests=100; // as in Apache HTTPD server
    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
    }
    public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
        this.maxKeepAliveRequests = maxKeepAliveRequests;
    }

    /**
     * 请求中允许的最大标头数.默认 100. 值小于0表示没有限制.
     */
    private int maxHeaderCount = 100; // as in Apache HTTPD server
    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }
    public void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }

    /**
     * 线程池的名称, 它将用于命名子线程.
     */
    private String name = "TP";
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }


    /**
     * 用于JMX注册的域名.
     */
    private String domain;
    public void setDomain(String domain) { this.domain = domain; }
    public String getDomain() { return domain; }


    /**
     * 默认是 true - 创建的线程将处于守护进程模式.、
     * 如果设置为false, 控制线程不是守护进程 - 并将使这个进程保持存活.
     */
    private boolean daemon = true;
    public void setDaemon(boolean b) { daemon = b; }
    public boolean getDaemon() { return daemon; }


    protected abstract boolean getDeferAccept();


    protected final List<String> negotiableProtocols = new ArrayList<>();
    public void addNegotiatedProtocol(String negotiableProtocol) {
        negotiableProtocols.add(negotiableProtocol);
    }
    public boolean hasNegotiableProtocols() {
        return (negotiableProtocols.size() > 0);
    }


    /**
     * 处理接受的套接字.
     */
    private Handler<S> handler = null;
    public void setHandler(Handler<S> handler ) { this.handler = handler; }
    public Handler<S> getHandler() { return handler; }


    /**
     * 属性提供了一种将配置传递给子组件的方法，而{@link org.apache.coyote.ProtocolHandler}不知道这些子组件上可用的属性.
     */
    protected HashMap<String, Object> attributes = new HashMap<>();

    /**
     * 当在{@link org.apache.coyote.ProtocolHandler}中存在特定setter的属性需要让子组件可用时，调用属性setter
     * 特定的setter将调用此方法来填充属性.
     *
     * @param name  要设置的属性的名称
     * @param value 属性的值
     */
    public void setAttribute(String name, Object value) {
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("endpoint.setAttribute", name, value));
        }
        attributes.put(name, value);
    }
    /**
     * 由子组件用于检索配置信息.
     *
     * @param key 应检索其值的属性的名称
     *
     * @return 指定属性的值
     */
    public Object getAttribute(String key) {
        Object value = attributes.get(key);
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("endpoint.getAttribute", key, value));
        }
        return value;
    }



    public boolean setProperty(String name, String value) {
        setAttribute(name, value);
        final String socketName = "socket.";
        try {
            if (name.startsWith(socketName)) {
                return IntrospectionUtils.setProperty(socketProperties, name.substring(socketName.length()), value);
            } else {
                return IntrospectionUtils.setProperty(this,name,value,false);
            }
        }catch ( Exception x ) {
            getLog().error("Unable to set attribute \""+name+"\" to \""+value+"\"",x);
            return false;
        }
    }
    public String getProperty(String name) {
        String value = (String) getAttribute(name);
        final String socketName = "socket.";
        if (value == null && name.startsWith(socketName)) {
            Object result = IntrospectionUtils.getProperty(socketProperties, name.substring(socketName.length()));
            if (result != null) {
                value = result.toString();
            }
        }
        return value;
    }

    /**
     * 返回池管理的线程数.
     */
    public int getCurrentThreadCount() {
        Executor executor = this.executor;
        if (executor != null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) executor).getPoolSize();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor) executor).getPoolSize();
            } else {
                return -1;
            }
        } else {
            return -2;
        }
    }

    /**
     * 返回正在使用的线程数
     */
    public int getCurrentThreadsBusy() {
        Executor executor = this.executor;
        if (executor != null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) executor).getActiveCount();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor) executor).getActiveCount();
            } else {
                return -1;
            }
        } else {
            return -2;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }


    public void createExecutor() {
        internalExecutor = true;
        TaskQueue taskqueue = new TaskQueue();
        TaskThreadFactory tf = new TaskThreadFactory(getName() + "-exec-", daemon, getThreadPriority());
        executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), 60, TimeUnit.SECONDS,taskqueue, tf);
        taskqueue.setParent( (ThreadPoolExecutor) executor);
    }

    public void shutdownExecutor() {
        Executor executor = this.executor;
        if (executor != null && internalExecutor) {
            this.executor = null;
            if (executor instanceof ThreadPoolExecutor) {
                // 这是内部的一个, 所以需要关闭它
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                tpe.shutdownNow();
                long timeout = getExecutorTerminationTimeoutMillis();
                if (timeout > 0) {
                    try {
                        tpe.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (tpe.isTerminating()) {
                        getLog().warn(sm.getString("endpoint.warn.executorShutdown", getName()));
                    }
                }
                TaskQueue queue = (TaskQueue) tpe.getQueue();
                queue.setParent(null);
            }
        }
    }

    /**
     * 接受使用虚假连接解锁服务器套接字.
     */
    protected void unlockAccept() {
        // Only try to unlock the acceptor if it is necessary
        int unlocksRequired = 0;
        for (Acceptor acceptor : acceptors) {
            if (acceptor.getState() == AcceptorState.RUNNING) {
                unlocksRequired++;
            }
        }
        if (unlocksRequired == 0) {
            return;
        }

        InetSocketAddress unlockAddress = null;
        InetSocketAddress localAddress = null;
        try {
            localAddress = getLocalAddress();
        } catch (IOException ioe) {
            getLog().debug(sm.getString("endpoint.debug.unlock.localFail", getName()), ioe);
        }
        if (localAddress == null) {
            getLog().warn(sm.getString("endpoint.debug.unlock.localNone", getName()));
            return;
        }

        try {
            unlockAddress = getUnlockAddress(localAddress);

            for (int i = 0; i < unlocksRequired; i++) {
                try (java.net.Socket s = new java.net.Socket()) {
                    int stmo = 2 * 1000;
                    int utmo = 2 * 1000;
                    if (getSocketProperties().getSoTimeout() > stmo)
                        stmo = getSocketProperties().getSoTimeout();
                    if (getSocketProperties().getUnlockTimeout() > utmo)
                        utmo = getSocketProperties().getUnlockTimeout();
                    s.setSoTimeout(stmo);
                    s.setSoLinger(getSocketProperties().getSoLingerOn(),getSocketProperties().getSoLingerTime());
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("About to unlock socket for:" + unlockAddress);
                    }
                    s.connect(unlockAddress,utmo);
                    if (getDeferAccept()) {
                        /*
                         * 在延迟接受过滤器的情况下，我们需要发送数据以唤醒接受.
                         * 发送OPTIONS *以绕过甚至BSD接受过滤器. Acceptor将丢弃它.
                         */
                        OutputStreamWriter sw;

                        sw = new OutputStreamWriter(s.getOutputStream(), "ISO-8859-1");
                        sw.write("OPTIONS * HTTP/1.0\r\n" +
                                 "User-Agent: Tomcat wakeup connection\r\n\r\n");
                        sw.flush();
                    }
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Socket unlock completed for:" + unlockAddress);
                    }
                }
            }
            // 等待接受器线程解锁, 最多1000毫秒
            long waitLeft = 1000;
            for (Acceptor acceptor : acceptors) {
                while (waitLeft > 0 &&
                        acceptor.getState() == AcceptorState.RUNNING) {
                    Thread.sleep(50);
                    waitLeft -= 50;
                }
            }
        } catch(Exception e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("endpoint.debug.unlock.fail", "" + getPort()), e);
            }
        }
    }


    private static InetSocketAddress getUnlockAddress(InetSocketAddress localAddress) throws SocketException {
        if (localAddress.getAddress().isAnyLocalAddress()) {
            // 需要与配置的绑定地址相同类型的本地地址（IPv4或IPV6），因为连接器可能配置为不在类型之间映射.
            InetAddress loopbackUnlockAddress = null;
            InetAddress linkLocalUnlockAddress = null;

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (localAddress.getAddress().getClass().isAssignableFrom(inetAddress.getClass())) {
                        if (inetAddress.isLoopbackAddress()) {
                            if (loopbackUnlockAddress == null) {
                                loopbackUnlockAddress = inetAddress;
                            }
                        } else if (inetAddress.isLinkLocalAddress()) {
                            if (linkLocalUnlockAddress == null) {
                                linkLocalUnlockAddress = inetAddress;
                            }
                        } else {
                            // 使用非本地链接, 默认情况下为非循环返回地址
                            return new InetSocketAddress(inetAddress, localAddress.getPort());
                        }
                    }
                }
            }
            // 首选循环本地链路，因为在某些平台（例如OSX）上，当监听所有本地地址时，不包括某些链路本地地址.
            if (loopbackUnlockAddress != null) {
                return new InetSocketAddress(loopbackUnlockAddress, localAddress.getPort());
            }
            if (linkLocalUnlockAddress != null) {
                return new InetSocketAddress(linkLocalUnlockAddress, localAddress.getPort());
            }
            // Fallback
            return new InetSocketAddress("localhost", localAddress.getPort());
        } else {
            return localAddress;
        }
    }


    // ---------------------------------------------- Request processing methods

    /**
     * 使用给定状态处理给定的SocketWrapper.
     * 用于触发处理，就好像Poller（对于那些有端点的端点）选择套接字一样.
     *
     * @param socketWrapper 要处理的套接字包装器
     * @param event         要处理的套接字事件
     * @param dispatch      是否应在新容器线程上执行处理
     *
     * @return 如果处理成功触发
     */
    public boolean processSocket(SocketWrapperBase<S> socketWrapper,
            SocketEvent event, boolean dispatch) {
        try {
            if (socketWrapper == null) {
                return false;
            }
            SocketProcessorBase<S> sc = processorCache.pop();
            if (sc == null) {
                sc = createSocketProcessor(socketWrapper, event);
            } else {
                sc.reset(socketWrapper, event);
            }
            Executor executor = getExecutor();
            if (dispatch && executor != null) {
                executor.execute(sc);
            } else {
                sc.run();
            }
        } catch (RejectedExecutionException ree) {
            getLog().warn(sm.getString("endpoint.executor.fail", socketWrapper) , ree);
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // 这意味着我们有一个OOM或类似的创建一个线程, 或者池及其队列已满
            getLog().error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }


    protected abstract SocketProcessorBase<S> createSocketProcessor(
            SocketWrapperBase<S> socketWrapper, SocketEvent event);


    // ------------------------------------------------------- Lifecycle methods

    /*
     * NOTE: 除了确保在正确的位置调用bind / unbind之外，不会维护状态或检查此类中的有效转换.
     * 期望调用代码将保持状态并防止无效的状态转换.
     */

    public abstract void bind() throws Exception;
    public abstract void unbind() throws Exception;
    public abstract void startInternal() throws Exception;
    public abstract void stopInternal() throws Exception;

    public void init() throws Exception {
        if (bindOnInit) {
            bind();
            bindState = BindState.BOUND_ON_INIT;
        }
        if (this.domain != null) {
            // 注册端点 (as ThreadPool - historical name)
            oname = new ObjectName(domain + ":type=ThreadPool,name=\"" + getName() + "\"");
            Registry.getRegistry(null, null).registerComponent(this, oname, null);

            for (SSLHostConfig sslHostConfig : findSslHostConfigs()) {
                registerJmx(sslHostConfig);
            }
        }
    }


    private void registerJmx(SSLHostConfig sslHostConfig) {
        ObjectName sslOname = null;
        try {
            sslOname = new ObjectName(domain + ":type=SSLHostConfig,ThreadPool=" +
                    getName() + ",name=" + ObjectName.quote(sslHostConfig.getHostName()));
            sslHostConfig.setObjectName(sslOname);
            try {
                Registry.getRegistry(null, null).registerComponent(sslHostConfig, sslOname, null);
            } catch (Exception e) {
                getLog().warn(sm.getString("endpoint.jmxRegistrationFailed", sslOname), e);
            }
        } catch (MalformedObjectNameException e) {
            getLog().warn(sm.getString("endpoint.invalidJmxNameSslHost",
                    sslHostConfig.getHostName()), e);
        }

        for (SSLHostConfigCertificate sslHostConfigCert : sslHostConfig.getCertificates()) {
            ObjectName sslCertOname = null;
            try {
                sslCertOname = new ObjectName(domain +
                        ":type=SSLHostConfigCertificate,ThreadPool=" + getName() +
                        ",Host=" + ObjectName.quote(sslHostConfig.getHostName()) +
                        ",name=" + sslHostConfigCert.getType());
                sslHostConfigCert.setObjectName(sslCertOname);
                try {
                    Registry.getRegistry(null, null).registerComponent(
                            sslHostConfigCert, sslCertOname, null);
                } catch (Exception e) {
                    getLog().warn(sm.getString("endpoint.jmxRegistrationFailed", sslCertOname), e);
                }
            } catch (MalformedObjectNameException e) {
                getLog().warn(sm.getString("endpoint.invalidJmxNameSslHostCert",
                        sslHostConfig.getHostName(), sslHostConfigCert.getType()), e);
            }
        }
    }


    private void unregisterJmx(SSLHostConfig sslHostConfig) {
        Registry registry = Registry.getRegistry(null, null);
        registry.unregisterComponent(sslHostConfig.getObjectName());
        for (SSLHostConfigCertificate sslHostConfigCert : sslHostConfig.getCertificates()) {
            registry.unregisterComponent(sslHostConfigCert.getObjectName());
        }
    }


    public final void start() throws Exception {
        if (bindState == BindState.UNBOUND) {
            bind();
            bindState = BindState.BOUND_ON_START;
        }
        startInternal();
    }

    protected final void startAcceptorThreads() {
        int count = getAcceptorThreadCount();
        acceptors = new Acceptor[count];

        for (int i = 0; i < count; i++) {
            acceptors[i] = createAcceptor();
            String threadName = getName() + "-Acceptor-" + i;
            acceptors[i].setThreadName(threadName);
            Thread t = new Thread(acceptors[i], threadName);
            t.setPriority(getAcceptorThreadPriority());
            t.setDaemon(getDaemon());
            t.start();
        }
    }


    /**
     * 允许端点提供特定的Acceptor实现的钩子.
     */
    protected abstract Acceptor createAcceptor();


    /**
     * 暂停端点, 这将阻止它接受新的连接.
     */
    public void pause() {
        if (running && !paused) {
            paused = true;
            unlockAccept();
            getHandler().pause();
        }
    }

    /**
     * 恢复端点, 这将使它开始再次接受新的连接.
     */
    public void resume() {
        if (running) {
            paused = false;
        }
    }

    public final void stop() throws Exception {
        stopInternal();
        if (bindState == BindState.BOUND_ON_START) {
            unbind();
            bindState = BindState.UNBOUND;
        }
    }

    public final void destroy() throws Exception {
        if (bindState == BindState.BOUND_ON_INIT) {
            unbind();
            bindState = BindState.UNBOUND;
        }
        Registry registry = Registry.getRegistry(null, null);
        registry.unregisterComponent(oname);
        for (SSLHostConfig sslHostConfig : findSslHostConfigs()) {
            unregisterJmx(sslHostConfig);
        }
    }


    protected abstract Log getLog();

    protected LimitLatch initializeConnectionLatch() {
        if (maxConnections==-1) return null;
        if (connectionLimitLatch==null) {
            connectionLimitLatch = new LimitLatch(getMaxConnections());
        }
        return connectionLimitLatch;
    }

    protected void releaseConnectionLatch() {
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) latch.releaseAll();
        connectionLimitLatch = null;
    }

    protected void countUpOrAwaitConnection() throws InterruptedException {
        if (maxConnections==-1) return;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) latch.countUpOrAwait();
    }

    protected long countDownConnection() {
        if (maxConnections==-1) return -1;
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) {
            long result = latch.countDown();
            if (result<0) {
                getLog().warn(sm.getString("endpoint.warn.incorrectConnectionCount"));
            }
            return result;
        } else return -1;
    }

    /**
     * 为子类提供了一种通用方法来处理需要延迟的异常，以防止线程进入紧密循环，从而消耗CPU并且还可能触发大量日志记录.
     * 例如, 如果达到打开文件的ulimit，则可能会发生Acceptor线程.
     *
     * @param currentErrorDelay 当前延迟在失败时应用
     * @return 下一次失败时的延迟时间
     */
    protected int handleExceptionWithDelay(int currentErrorDelay) {
        // 不要延迟第一个异常
        if (currentErrorDelay > 0) {
            try {
                Thread.sleep(currentErrorDelay);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // 在随后的异常, 在50ms开始延迟, 将每个后续异常的延迟加倍, 直到延迟达到1.6秒.
        if (currentErrorDelay == 0) {
            return INITIAL_ERROR_DELAY;
        } else if (currentErrorDelay < MAX_ERROR_DELAY) {
            return currentErrorDelay * 2;
        } else {
            return MAX_ERROR_DELAY;
        }
    }
}

