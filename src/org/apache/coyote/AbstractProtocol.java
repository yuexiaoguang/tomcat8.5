package org.apache.coyote;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;

import org.apache.juli.logging.Log;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractProtocol<S> implements ProtocolHandler,
        MBeanRegistration {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(AbstractProtocol.class);


    /**
     * 用于使用自动端口绑定为连接器生成唯一的JMX名称的计数器.
     */
    private static final AtomicInteger nameCounter = new AtomicInteger(0);


    /**
     * Global Request Processor的MBean的名称.
     */
    protected ObjectName rgOname = null;


    /**
     * 这个连接器的唯一 ID. 只有在连接器配置使用随机端口的时候使用，因为端口在调用 stop(), start()之后会改变.
     */
    private int nameIndex = 0;


    /**
     * 提供低级网络I/O的端点 - 必须匹配 ProtocolHandler 实现 (ProtocolHandler 使用 NIO, 需要 NIO端点等).
     */
    private final AbstractEndpoint<S> endpoint;


    private Handler<S> handler;


    private final Set<Processor> waitingProcessors =
            Collections.newSetFromMap(new ConcurrentHashMap<Processor, Boolean>());


    /**
     * 异步超时线程.
     */
    private AsyncTimeout asyncTimeout = null;


    public AbstractProtocol(AbstractEndpoint<S> endpoint) {
        this.endpoint = endpoint;
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }


    // ----------------------------------------------- Generic property handling

    /**
     * digester使用的通用属性 setter. 其他代码不应该使用这个. digester将只使用这个方法, 如果它找不到更多的特定 setter.
     * 意味着属性属于端点, ServerSocketFactory 或其他一些较低级别的组件. 此方法确保两者都可见.
     *
     * @param name  要设置的属性的名称
     * @param value 属性的值
     *
     * @return <code>true</code>如果属性设置成功, 否则<code>false</code>
     */
    public boolean setProperty(String name, String value) {
        return endpoint.setProperty(name, value);
    }


    /**
     * digester使用的通用属性 getter. 其他代码不应该使用这个.
     *
     * @param name 属性的名称
     *
     * @return 属性的值
     */
    public String getProperty(String name) {
        return endpoint.getProperty(name);
    }


    // ------------------------------- Properties managed by the ProtocolHandler

    /**
     * 提供 ProtocolHandler 和其它连接器之间的连接的适配器.
     */
    protected Adapter adapter;
    @Override
    public void setAdapter(Adapter adapter) { this.adapter = adapter; }
    @Override
    public Adapter getAdapter() { return adapter; }


    /**
     * 将在缓存中保留并在后续请求中重新使用的空闲处理器的最大数目. 默认是 200. -1表示不限制.
     * 在无限的情况下, 缓存的Processor对象的理论最大数量是 {@link #getMaxConnections()}, 虽然它通常很接近 {@link #getMaxThreads()}.
     */
    protected int processorCache = 200;
    public int getProcessorCache() { return this.processorCache; }
    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }


    /**
     * 当客户端证书信息以表单形式呈现时, 而不是{@link java.security.cert.X509Certificate}实例, 它需要在使用之前转换, 此属性控制用于执行转换的JSSE提供程序.
     * 例如，它与AJP连接器, HTTP APR 连接器和{@link org.apache.catalina.valves.SSLValve}一起使用. 如果未指定, 将使用默认的提供者.
     */
    protected String clientCertProvider = null;
    public String getClientCertProvider() { return clientCertProvider; }
    public void setClientCertProvider(String s) { this.clientCertProvider = s; }


    @Override
    public boolean isAprRequired() {
        return false;
    }


    @Override
    public boolean isSendfileSupported() {
        return endpoint.getUseSendfile();
    }


    public AsyncTimeout getAsyncTimeout() {
        return asyncTimeout;
    }

    /**
     * 指定是否在响应中发送原因短语. 默认不发送.
     *
     * @deprecated This option will be removed in Tomcat 9. Reason phrase will
     *             not be sent.
     */
    @Deprecated
    private boolean sendReasonPhrase = false;
    /**
     * 指定是否在响应中发送原因短语. 默认不发送.
     *
     * @return whether the reason phrase will be sent
     * @deprecated This option will be removed in Tomcat 9. Reason phrase will
     *             not be sent.
     */
    @Deprecated
    public boolean getSendReasonPhrase() {
        return sendReasonPhrase;
    }
    /**
     * 指定是否在响应中发送原因短语. 默认不发送.
     *
     * @param sendReasonPhrase specifies whether the reason phrase will be sent
     * @deprecated This option will be removed in Tomcat 9. Reason phrase will
     *             not be sent.
     */
    @Deprecated
    public void setSendReasonPhrase(boolean sendReasonPhrase) {
        this.sendReasonPhrase = sendReasonPhrase;
    }


    // ---------------------- Properties that are passed through to the EndPoint

    @Override
    public Executor getExecutor() { return endpoint.getExecutor(); }
    public void setExecutor(Executor executor) {
        endpoint.setExecutor(executor);
    }


    public int getMaxThreads() { return endpoint.getMaxThreads(); }
    public void setMaxThreads(int maxThreads) {
        endpoint.setMaxThreads(maxThreads);
    }

    public int getMaxConnections() { return endpoint.getMaxConnections(); }
    public void setMaxConnections(int maxConnections) {
        endpoint.setMaxConnections(maxConnections);
    }


    public int getMinSpareThreads() { return endpoint.getMinSpareThreads(); }
    public void setMinSpareThreads(int minSpareThreads) {
        endpoint.setMinSpareThreads(minSpareThreads);
    }


    public int getThreadPriority() { return endpoint.getThreadPriority(); }
    public void setThreadPriority(int threadPriority) {
        endpoint.setThreadPriority(threadPriority);
    }


    public int getAcceptCount() { return endpoint.getAcceptCount(); }
    public void setAcceptCount(int acceptCount) { endpoint.setAcceptCount(acceptCount); }
    @Deprecated
    public int getBacklog() { return endpoint.getBacklog(); }
    @Deprecated
    public void setBacklog(int backlog) { endpoint.setBacklog(backlog); }


    public boolean getTcpNoDelay() { return endpoint.getTcpNoDelay(); }
    public void setTcpNoDelay(boolean tcpNoDelay) {
        endpoint.setTcpNoDelay(tcpNoDelay);
    }


    public int getConnectionLinger() { return endpoint.getConnectionLinger(); }
    public void setConnectionLinger(int connectionLinger) {
        endpoint.setConnectionLinger(connectionLinger);
    }
    @Deprecated
    public int getSoLinger() { return endpoint.getSoLinger(); }
    @Deprecated
    public void setSoLinger(int soLinger) { endpoint.setSoLinger(soLinger); }


    public int getKeepAliveTimeout() { return endpoint.getKeepAliveTimeout(); }
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        endpoint.setKeepAliveTimeout(keepAliveTimeout);
    }

    public InetAddress getAddress() { return endpoint.getAddress(); }
    public void setAddress(InetAddress ia) {
        endpoint.setAddress(ia);
    }


    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) {
        endpoint.setPort(port);
    }


    public int getLocalPort() { return endpoint.getLocalPort(); }

    /*
     * 当Tomcat期望来自客户端的数据时, 这是Tomcat将在关闭连接之前等待数据到达的时间.
     */
    public int getConnectionTimeout() {
        return endpoint.getConnectionTimeout();
    }
    public void setConnectionTimeout(int timeout) {
        endpoint.setConnectionTimeout(timeout);
    }

    @Deprecated
    public int getSoTimeout() {
        return getConnectionTimeout();
    }
    @Deprecated
    public void setSoTimeout(int timeout) {
        setConnectionTimeout(timeout);
    }

    public int getMaxHeaderCount() {
        return endpoint.getMaxHeaderCount();
    }
    public void setMaxHeaderCount(int maxHeaderCount) {
        endpoint.setMaxHeaderCount(maxHeaderCount);
    }

    public long getConnectionCount() {
        return endpoint.getConnectionCount();
    }

    public void setAcceptorThreadCount(int threadCount) {
        endpoint.setAcceptorThreadCount(threadCount);
    }
    public int getAcceptorThreadCount() {
      return endpoint.getAcceptorThreadCount();
    }

    public void setAcceptorThreadPriority(int threadPriority) {
        endpoint.setAcceptorThreadPriority(threadPriority);
    }
    public int getAcceptorThreadPriority() {
      return endpoint.getAcceptorThreadPriority();
    }


    // ---------------------------------------------------------- Public methods

    public synchronized int getNameIndex() {
        if (nameIndex == 0) {
            nameIndex = nameCounter.incrementAndGet();
        }

        return nameIndex;
    }


    /**
     * 名称将会是 prefix-address-port, 如果地址 non-null; 或者 prefix-port, 如果地址是 null.
     *
     * @return 该协议实例的名称，该名称在ObjectName中被适当引用.
     */
    public String getName() {
        return ObjectName.quote(getNameInternal());
    }


    private String getNameInternal() {
        StringBuilder name = new StringBuilder(getNamePrefix());
        name.append('-');
        if (getAddress() != null) {
            name.append(getAddress().getHostAddress());
            name.append('-');
        }
        int port = getPort();
        if (port == 0) {
            // 使用中自动绑定. 检查是否已知端口
            name.append("auto-");
            name.append(getNameIndex());
            port = getLocalPort();
            if (port != -1) {
                name.append('-');
                name.append(port);
            }
        } else {
            name.append(port);
        }
        return name.toString();
    }


    public void addWaitingProcessor(Processor processor) {
        waitingProcessors.add(processor);
    }


    public void removeWaitingProcessor(Processor processor) {
        waitingProcessors.remove(processor);
    }


    // ----------------------------------------------- Accessors for sub-classes

    protected AbstractEndpoint<S> getEndpoint() {
        return endpoint;
    }


    protected Handler<S> getHandler() {
        return handler;
    }

    protected void setHandler(Handler<S> handler) {
        this.handler = handler;
    }


    // -------------------------------------------------------- Abstract methods

    /**
     * 具体的实现需要提供对抽象类使用的logger的访问.
     * @return the logger
     */
    protected abstract Log getLog();


    /**
     * 获取此协议处理程序的名称时, 要使用的前缀. 名称将是 prefix-address-port.
     * @return the prefix
     */
    protected abstract String getNamePrefix();


    /**
     * 获取协议的名称, (Http, Ajp, etc.). 和 JMX一起使用.
     * @return the protocol name
     */
    protected abstract String getProtocolName();


    /**
     * 为网络层协商的协议找到合适的处理程序.
     * 
     * @param name 所请求的协商协议的名称.
     * @return {@link UpgradeProtocol#getAlpnName()} 匹配请求的协议的实例
     */
    protected abstract UpgradeProtocol getNegotiatedProtocol(String name);


    /**
     * 为协议指定的升级名称查找合适的处理程序. 用于直接连接协议选择.
     * 
     * @param name 所请求的协商协议的名称.
     * @return {@link UpgradeProtocol#getAlpnName()} 匹配请求的协议的实例
     */
    protected abstract UpgradeProtocol getUpgradeProtocol(String name);


    /**
     * 为当前协议实现创建和配置新的Processor实例.
     *
     * @return 准备好使用的完全配置的Processor实例
     */
    protected abstract Processor createProcessor();


    protected abstract Processor createUpgradeProcessor(
            SocketWrapperBase<?> socket,
            UpgradeToken upgradeToken);


    // ----------------------------------------------------- JMX related methods

    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        oname = name;
        mserver = server;
        domain = name.getDomain();
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        // NOOP
    }

    @Override
    public void preDeregister() throws Exception {
        // NOOP
    }

    @Override
    public void postDeregister() {
        // NOOP
    }

    private ObjectName createObjectName() throws MalformedObjectNameException {
        // 使用与连接器相同的域
        domain = getAdapter().getDomain();

        if (domain == null) {
            return null;
        }

        StringBuilder name = new StringBuilder(getDomain());
        name.append(":type=ProtocolHandler,port=");
        int port = getPort();
        if (port > 0) {
            name.append(getPort());
        } else {
            name.append("auto-");
            name.append(getNameIndex());
        }
        InetAddress address = getAddress();
        if (address != null) {
            name.append(",address=");
            name.append(ObjectName.quote(address.getHostAddress()));
        }
        return new ObjectName(name.toString());
    }


    // ------------------------------------------------------- Lifecycle methods

    /*
     * NOTE: 在该类中没有维护状态或检查有效的转换. 期望连接器将保持状态并防止无效状态转换.
     */
    @Override
    public void init() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.init", getName()));
        }

        if (oname == null) {
            // Component not pre-registered so register it
            oname = createObjectName();
            if (oname != null) {
                Registry.getRegistry(null, null).registerComponent(this, oname, null);
            }
        }

        if (this.domain != null) {
            rgOname = new ObjectName(domain + ":type=GlobalRequestProcessor,name=" + getName());
            Registry.getRegistry(null, null).registerComponent(
                    getHandler().getGlobal(), rgOname, null);
        }

        String endpointName = getName();
        endpoint.setName(endpointName.substring(1, endpointName.length()-1));
        endpoint.setDomain(domain);

        endpoint.init();
    }


    @Override
    public void start() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.start", getName()));
        }

        endpoint.start();

        // 启动异步超时线程
        asyncTimeout = new AsyncTimeout();
        Thread timeoutThread = new Thread(asyncTimeout, getNameInternal() + "-AsyncTimeout");
        int priority = endpoint.getThreadPriority();
        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            priority = Thread.NORM_PRIORITY;
        }
        timeoutThread.setPriority(priority);
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }


    @Override
    public void pause() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.pause", getName()));
        }

        endpoint.pause();
    }


    @Override
    public void resume() throws Exception {
        if(getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.resume", getName()));
        }

        endpoint.resume();
    }


    @Override
    public void stop() throws Exception {
        if(getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.stop", getName()));
        }

        if (asyncTimeout != null) {
            asyncTimeout.stop();
        }

        endpoint.stop();
    }


    @Override
    public void destroy() throws Exception {
        if(getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.destroy", getName()));
        }

        try {
            endpoint.destroy();
        } finally {
            if (oname != null) {
                if (mserver == null) {
                    Registry.getRegistry(null, null).unregisterComponent(oname);
                } else {
                    // 可能使用不同的MBeanServer 注册
                    try {
                        mserver.unregisterMBean(oname);
                    } catch (MBeanRegistrationException | InstanceNotFoundException e) {
                        getLog().info(sm.getString("abstractProtocol.mbeanDeregistrationFailed",
                                oname, mserver));
                    }
                }
            }

            if (rgOname != null) {
                Registry.getRegistry(null, null).unregisterComponent(rgOname);
            }
        }
    }


    // ------------------------------------------- Connection handler base class

    protected static class ConnectionHandler<S> implements AbstractEndpoint.Handler<S> {

        private final AbstractProtocol<S> proto;
        private final RequestGroupInfo global = new RequestGroupInfo();
        private final AtomicLong registerCount = new AtomicLong(0);
        private final Map<S,Processor> connections = new ConcurrentHashMap<>();
        private final RecycledProcessors recycledProcessors = new RecycledProcessors(this);

        public ConnectionHandler(AbstractProtocol<S> proto) {
            this.proto = proto;
        }

        protected AbstractProtocol<S> getProtocol() {
            return proto;
        }

        protected Log getLog() {
            return getProtocol().getLog();
        }

        @Override
        public Object getGlobal() {
            return global;
        }

        @Override
        public void recycle() {
            recycledProcessors.clear();
        }


        @Override
        public SocketState process(SocketWrapperBase<S> wrapper, SocketEvent status) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractConnectionHandler.process",
                        wrapper.getSocket(), status));
            }
            if (wrapper == null) {
                // Nothing to do. Socket 已经被关闭.
                return SocketState.CLOSED;
            }

            S socket = wrapper.getSocket();

            Processor processor = connections.get(socket);
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractConnectionHandler.connectionsGet",
                        processor, socket));
            }

            if (processor != null) {
                // 确保异步超时不会触发
                getProtocol().removeWaitingProcessor(processor);
            } else if (status == SocketEvent.DISCONNECT || status == SocketEvent.ERROR) {
                // Nothing to do. 端点请求关闭, 不再有与此套接字关联的处理器.
                return SocketState.CLOSED;
            }

            ContainerThreadMarker.set();

            try {
                if (processor == null) {
                    String negotiatedProtocol = wrapper.getNegotiatedProtocol();
                    if (negotiatedProtocol != null) {
                        UpgradeProtocol upgradeProtocol =
                                getProtocol().getNegotiatedProtocol(negotiatedProtocol);
                        if (upgradeProtocol != null) {
                            processor = upgradeProtocol.getProcessor(
                                    wrapper, getProtocol().getAdapter());
                        } else if (negotiatedProtocol.equals("http/1.1")) {
                            // 明确协商默认协议.
                            // 获得以下处理器.
                        } else {
                            // TODO:
                            // OpenSSL 1.0.2的 ALPN 回调不支持错误的握手失败, 如果没有协议可以协商.
                            // 因此, 需要在这里连接失败. 一旦这是固定的, 用注释块替换下面的代码.
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString(
                                    "abstractConnectionHandler.negotiatedProcessor.fail",
                                    negotiatedProtocol));
                            }
                            return SocketState.CLOSED;
                            /*
                             * 一旦使用OpenSSL 1.1.0来替换上面的代码.
                            // 创建处理器失败. This is a bug.
                            throw new IllegalStateException(sm.getString(
                                    "abstractConnectionHandler.negotiatedProcessor.fail",
                                    negotiatedProtocol));
                            */
                        }
                    }
                }
                if (processor == null) {
                    processor = recycledProcessors.pop();
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(sm.getString("abstractConnectionHandler.processorPop",
                                processor));
                    }
                }
                if (processor == null) {
                    processor = getProtocol().createProcessor();
                    register(processor);
                }

                processor.setSslSupport(
                        wrapper.getSslSupport(getProtocol().getClientCertProvider()));

                // 将处理器与连接关联起来
                connections.put(socket, processor);

                SocketState state = SocketState.CLOSED;
                do {
                    state = processor.process(wrapper, status);

                    if (state == SocketState.UPGRADING) {
                        // 获取 HTTP 升级处理器
                        UpgradeToken upgradeToken = processor.getUpgradeToken();
                        // 检索剩余输入
                        ByteBuffer leftOverInput = processor.getLeftoverInput();
                        if (upgradeToken == null) {
                            // 假设直接HTTP/2连接
                            UpgradeProtocol upgradeProtocol = getProtocol().getUpgradeProtocol("h2c");
                            if (upgradeProtocol != null) {
                                processor = upgradeProtocol.getProcessor(
                                        wrapper, getProtocol().getAdapter());
                                wrapper.unRead(leftOverInput);
                                // 将处理器与连接关联起来
                                connections.put(socket, processor);
                            } else {
                                if (getLog().isDebugEnabled()) {
                                    getLog().debug(sm.getString(
                                        "abstractConnectionHandler.negotiatedProcessor.fail",
                                        "h2c"));
                                }
                                return SocketState.CLOSED;
                            }
                        } else {
                            HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
                            // R释放 Http11 处理器来重用
                            release(processor);
                            // 创建升级处理器
                            processor = getProtocol().createUpgradeProcessor(wrapper, upgradeToken);
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString("abstractConnectionHandler.upgradeCreate",
                                        processor, wrapper));
                            }
                            wrapper.unRead(leftOverInput);
                            // 将连接标记为升级
                            wrapper.setUpgraded(true);
                            // 与处理器关联的连接
                            connections.put(socket, processor);
                            // 初始化升级处理程序 (这可能会触发一些IO使用新协议，这就是为什么上面的行是必需的)
                            // 这中情况应该是安全的. 如果失败，周围的try/catch的错误处理将处理它.
                            if (upgradeToken.getInstanceManager() == null) {
                                httpUpgradeHandler.init((WebConnection) processor);
                            } else {
                                ClassLoader oldCL = upgradeToken.getContextBind().bind(false, null);
                                try {
                                    httpUpgradeHandler.init((WebConnection) processor);
                                } finally {
                                    upgradeToken.getContextBind().unbind(false, oldCL);
                                }
                            }
                        }
                    }
                } while ( state == SocketState.UPGRADING);

                if (state == SocketState.LONG) {
                    // 在处理请求/响应的中间. 保持与处理器相关的socket. 精确的需求取决于long类型
                    longPoll(wrapper, processor);
                    if (processor.isAsync()) {
                        getProtocol().addWaitingProcessor(processor);
                    }
                } else if (state == SocketState.OPEN) {
                    // 如果两个请求是 keep-alive. 循环处理器是OK. 继续轮询下一个请求.
                    connections.remove(socket);
                    release(processor);
                    wrapper.registerReadInterest();
                } else if (state == SocketState.SENDFILE) {
                    // Sendfile 在进行中. 如果失败, 将关闭 socket. 如果它工作, 套接字被添加到查询器（或等效的）以等待更多的数据或处理，如果有任何管道请求剩余.
                } else if (state == SocketState.UPGRADED) {
                    // 如果这是非阻塞写，不要将socket添加到查询器中, 否则，查询器可能触发多个读取事件，这些事件可能导致连接器中的线程饥饿.
                    // write() 方法将添加这个 socket 到查询器.
                    if (status != SocketEvent.OPEN_WRITE) {
                        longPoll(wrapper, processor);
                    }
                } else if (state == SocketState.SUSPENDED) {
                    // 不要添加 socket到 poller.
                    // resumeProcessing() 方法将添加这个 socket 到 poller.
                } else {
                    // 连接关闭. 可以回收处理器. 升级处理器不回收.
                    connections.remove(socket);
                    if (processor.isUpgrade()) {
                        UpgradeToken upgradeToken = processor.getUpgradeToken();
                        HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
                        InstanceManager instanceManager = upgradeToken.getInstanceManager();
                        if (instanceManager == null) {
                            httpUpgradeHandler.destroy();
                        } else {
                            ClassLoader oldCL = upgradeToken.getContextBind().bind(false, null);
                            try {
                                httpUpgradeHandler.destroy();
                            } finally {
                                try {
                                    instanceManager.destroyInstance(httpUpgradeHandler);
                                } catch (Throwable e) {
                                    ExceptionUtils.handleThrowable(e);
                                    getLog().error(sm.getString("abstractConnectionHandler.error"), e);
                                }
                                upgradeToken.getContextBind().unbind(false, oldCL);
                            }
                        }
                    } else {
                        release(processor);
                    }
                }
                return state;
            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.ioexception.debug"), e);
            } catch (ProtocolException e) {
                // 协议异常通常意味着客户端发送了无效或不完整的数据.
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.protocolexception.debug"), e);
            }
            // Future developers: 如果发现其他罕见但非致命的异常, 在这里捕获并记录.
            catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                // 任何其他异常或错误都是可能的. 这里使用"ERROR" 级别, 所以它甚至会出现在不那么冗长的日志上.
                getLog().error(sm.getString("abstractConnectionHandler.error"), e);
            } finally {
                ContainerThreadMarker.clear();
            }

            // 确保 socket/processor 从当前连接中删除
            connections.remove(socket);
            release(processor);
            return SocketState.CLOSED;
        }


        protected void longPoll(SocketWrapperBase<?> socket, Processor processor) {
            if (!processor.isAsync()) {
                // 目前只与HTTP一起使用
                // Either:
                //  - 这是升级的连接
                //  - 请求 line/headers 尚未完全读取
                socket.registerReadInterest();
            }
        }


        @Override
        public Set<S> getOpenSockets() {
            return connections.keySet();
        }


        /**
         * 一旦processor不再需要，handler将使用它.
         *
         * @param processor 将释放Processor (socket关联的)
         */
        private void release(Processor processor) {
            if (processor != null) {
                processor.recycle();
                // 回收后, 只有UpgradeProcessorBase 实例将返回 true, 为了 isUpgrade().
                // UpgradeProcessorBase的实例不应该被添加到 recycledProcessors, 因为这个池只用于 AJP 或 HTTP处理器
                if (!processor.isUpgrade()) {
                    recycledProcessors.push(processor);
                    getLog().debug("Pushed Processor [" + processor + "]");
                }
            }
        }


        /**
         * 将被端点用于释放套接字关闭、错误等的资源.
         */
        @Override
        public void release(SocketWrapperBase<S> socketWrapper) {
            S socket = socketWrapper.getSocket();
            Processor processor = connections.remove(socket);
            release(processor);
        }


        protected void register(Processor processor) {
            if (getProtocol().getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        RequestInfo rp =
                            processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(global);
                        ObjectName rpName = new ObjectName(
                                getProtocol().getDomain() +
                                ":type=RequestProcessor,worker="
                                + getProtocol().getName() +
                                ",name=" + getProtocol().getProtocolName() +
                                "Request" + count);
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Register " + rpName);
                        }
                        Registry.getRegistry(null, null).registerComponent(rp,
                                rpName, null);
                        rp.setRpName(rpName);
                    } catch (Exception e) {
                        getLog().warn("Error registering request");
                    }
                }
            }
        }

        protected void unregister(Processor processor) {
            if (getProtocol().getDomain() != null) {
                synchronized (this) {
                    try {
                        Request r = processor.getRequest();
                        if (r == null) {
                            // Probably an UpgradeProcessor
                            return;
                        }
                        RequestInfo rp = r.getRequestProcessor();
                        rp.setGlobalProcessor(null);
                        ObjectName rpName = rp.getRpName();
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Unregister " + rpName);
                        }
                        Registry.getRegistry(null, null).unregisterComponent(
                                rpName);
                        rp.setRpName(null);
                    } catch (Exception e) {
                        getLog().warn("Error unregistering request", e);
                    }
                }
            }
        }

        @Override
        public final void pause() {
            /*
             * 通知所有与当前连接相关的处理器，即端点被暂停. 最不在乎. 那些处理多路复用的流可能希望采取行动.
             * 例如, HTTP/2 可能希望停止接受新的数据流.
             *
             * 请注意，即使端点已恢复, 目前没有API通知处理器.
             */
            for (Processor processor : connections.values()) {
                processor.pause();
            }
        }
    }

    protected static class RecycledProcessors extends SynchronizedStack<Processor> {

        private final transient ConnectionHandler<?> handler;
        protected final AtomicInteger size = new AtomicInteger(0);

        public RecycledProcessors(ConnectionHandler<?> handler) {
            this.handler = handler;
        }

        @SuppressWarnings("sync-override") // Size may exceed cache size a bit
        @Override
        public boolean push(Processor processor) {
            int cacheSize = handler.getProtocol().getProcessorCache();
            boolean offer = cacheSize == -1 ? true : size.get() < cacheSize;
            //避免过度增长缓存或在停止后添加
            boolean result = false;
            if (offer) {
                result = super.push(processor);
                if (result) {
                    size.incrementAndGet();
                }
            }
            if (!result) handler.unregister(processor);
            return result;
        }

        @SuppressWarnings("sync-override") // OK if size is too big briefly
        @Override
        public Processor pop() {
            Processor result = super.pop();
            if (result != null) {
                size.decrementAndGet();
            }
            return result;
        }

        @Override
        public synchronized void clear() {
            Processor next = pop();
            while (next != null) {
                handler.unregister(next);
                next = pop();
            }
            super.clear();
            size.set(0);
        }
    }


    /**
     * 异步超时线程
     */
    protected class AsyncTimeout implements Runnable {

        private volatile boolean asyncTimeoutRunning = true;

        /**
         * 如果没有活动，后台线程则检查异步请求并触发超时.
         */
        @Override
        public void run() {

            // 循环，直到接收到关闭命令
            while (asyncTimeoutRunning) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                long now = System.currentTimeMillis();
                for (Processor processor : waitingProcessors) {
                   processor.timeoutAsync(now);
                }

                // 循环，直到端点暂停
                while (endpoint.isPaused() && asyncTimeoutRunning) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }
        }


        protected void stop() {
            asyncTimeoutRunning = false;

            // 超时等待的异步请求
            for (Processor processor : waitingProcessors) {
                processor.timeoutAsync(-1);
            }
        }
    }
}
