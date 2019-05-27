package org.apache.catalina.tribes.transport;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.MessageListener;
import org.apache.catalina.tribes.io.ListenCallback;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.util.ExecutorFactory;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public abstract class ReceiverBase implements ChannelReceiver, ListenCallback, RxTaskPool.TaskCreator {

    public static final int OPTION_DIRECT_BUFFER = 0x0004;

    private static final Log log = LogFactory.getLog(ReceiverBase.class);

    private static final Object bindLock = new Object();

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private MessageListener listener;
    private String host = "auto";
    private InetAddress bind;
    private int port  = 4000;
    private int udpPort = -1;
    private int securePort = -1;
    private int rxBufSize = 43800;
    private int txBufSize = 25188;
    private int udpRxBufSize = 43800;
    private int udpTxBufSize = 25188;

    private volatile boolean listen = false;
    private RxTaskPool pool;
    private boolean direct = true;
    private long tcpSelectorTimeout = 5000;
    // 搜索可用 socket 的次数
    private int autoBind = 100;
    private int maxThreads = 15;
    private int minThreads = 6;
    private int maxTasks = 100;
    private int minTasks = 10;
    private boolean tcpNoDelay = true;
    private boolean soKeepAlive = false;
    private boolean ooBInline = true;
    private boolean soReuseAddress = true;
    private boolean soLingerOn = true;
    private int soLingerTime = 3;
    private int soTrafficClass = 0x04 | 0x08 | 0x010;
    private int timeout = 3000; //3 seconds
    private boolean useBufferPool = true;
    private boolean daemon = true;
    private long maxIdleTime = 60000;

    private ExecutorService executor;
    private Channel channel;

    private ObjectName oname = null;

    public ReceiverBase() {
    }

    @Override
    public void start() throws IOException {
        if ( executor == null ) {
            //executor = new ThreadPoolExecutor(minThreads,maxThreads,60,TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>());
            String channelName = "";
            if (channel.getName() != null) channelName = "[" + channel.getName() + "]";
            TaskThreadFactory tf = new TaskThreadFactory("Tribes-Task-Receiver" + channelName + "-");
            executor = ExecutorFactory.newThreadPool(minThreads, maxThreads, maxIdleTime, TimeUnit.MILLISECONDS, tf);
        }
        // register jmx
        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
        if (jmxRegistry != null) this.oname = jmxRegistry.registerJmx(",component=Receiver", this);
    }

    @Override
    public void stop() {
        if ( executor != null ) executor.shutdownNow();//ignore left overs
        executor = null;
        if (oname != null) {
            JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
            if (jmxRegistry != null) jmxRegistry.unregisterJmx(oname);
            oname = null;
        }
        channel = null;
    }

    @Override
    public MessageListener getMessageListener() {
        return listener;
    }

    @Override
    public int getPort() {
        return port;
    }

    public int getRxBufSize() {
        return rxBufSize;
    }

    public int getTxBufSize() {
        return txBufSize;
    }

    @Override
    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = rxBufSize;
    }

    public void setTxBufSize(int txBufSize) {
        this.txBufSize = txBufSize;
    }

    public InetAddress getBind() {
        if (bind == null) {
            try {
                if ("auto".equals(host)) {
                    host = java.net.InetAddress.getLocalHost().getHostAddress();
                }
                if (log.isDebugEnabled())
                    log.debug("Starting replication listener on address:"+ host);
                bind = java.net.InetAddress.getByName(host);
            } catch (IOException ioe) {
                log.error(sm.getString("receiverBase.bind.failed", host), ioe);
            }
        }
        return bind;
    }

    /**
     * 使用提供的端口尝试绑定, 如果失败, 尝试绑定从portstart 到 (portstart + retries -1)的端口, 直到全部尝试或绑定成功.
     * 要绑定的地址通过调用{link {@link #getBind()}获得.
     * 
     * @param socket        要绑定的socket
     * @param portstart     尝试绑定的开始的端口
     * @param retries       尝试绑定的次数 (在尝试之间递增的端口)
     * 
     * @throws IOException 绑定Socket 错误
     */
    protected void bind(ServerSocket socket, int portstart, int retries) throws IOException {
        synchronized (bindLock) {
            InetSocketAddress addr = null;
            int port = portstart;
            while (retries > 0) {
                try {
                    addr = new InetSocketAddress(getBind(), port);
                    socket.bind(addr);
                    setPort(port);
                    log.info(sm.getString("receiverBase.socket.bind", addr));
                    retries = 0;
                } catch ( IOException x) {
                    retries--;
                    if ( retries <= 0 ) {
                        log.info(sm.getString("receiverBase.unable.bind", addr));
                        throw x;
                    }
                    port++;
                }
            }
        }
    }

    /**
     * 和 bind()一样, 除非它使用 UDP 端口
     * @param socket 要绑定的socket
     * @param portstart     尝试绑定的开始的端口
     * @param retries       尝试绑定的次数 (在尝试之间递增的端口)
     * 
     * @return 重试次数
     * @throws IOException 绑定Socket错误
     */
    protected int bindUdp(DatagramSocket socket, int portstart, int retries) throws IOException {
        InetSocketAddress addr = null;
        while ( retries > 0 ) {
            try {
                addr = new InetSocketAddress(getBind(), portstart);
                socket.bind(addr);
                setUdpPort(portstart);
                log.info(sm.getString("receiverBase.udp.bind", addr));
                return 0;
            }catch ( IOException x) {
                retries--;
                if ( retries <= 0 ) {
                    log.info(sm.getString("receiverBase.unable.bind.udp", addr));
                    throw x;
                }
                portstart++;
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ti) {
                    Thread.currentThread().interrupt();
                }
                retries = bindUdp(socket,portstart,retries);
            }
        }
        return retries;
    }


    @Override
    public void messageDataReceived(ChannelMessage data) {
        if ( this.listener != null ) {
            if ( listener.accept(data) ) listener.messageReceived(data);
        }
    }

    public int getWorkerThreadOptions() {
        int options = 0;
        if ( getDirect() ) options = options | OPTION_DIRECT_BUFFER;
        return options;
    }


    public void setBind(java.net.InetAddress bind) {
        this.bind = bind;
    }

    public boolean getDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }

    public String getAddress() {
        getBind();
        return this.host;
    }

    @Override
    public String getHost() {
        return getAddress();
    }

    public long getSelectorTimeout() {
        return tcpSelectorTimeout;
    }

    public boolean doListen() {
        return listen;
    }

    public MessageListener getListener() {
        return listener;
    }

    public RxTaskPool getTaskPool() {
        return pool;
    }

    public int getAutoBind() {
        return autoBind;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getMinThreads() {
        return minThreads;
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    public boolean getSoKeepAlive() {
        return soKeepAlive;
    }

    public boolean getOoBInline() {
        return ooBInline;
    }

    public boolean getSoLingerOn() {
        return soLingerOn;
    }

    public int getSoLingerTime() {
        return soLingerTime;
    }

    public boolean getSoReuseAddress() {
        return soReuseAddress;
    }

    public int getSoTrafficClass() {
        return soTrafficClass;
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean getUseBufferPool() {
        return useBufferPool;
    }

    @Override
    public int getSecurePort() {
        return securePort;
    }

    public int getMinTasks() {
        return minTasks;
    }

    public int getMaxTasks() {
        return maxTasks;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public boolean isListening() {
        return listen;
    }

    public void setSelectorTimeout(long selTimeout) {
        tcpSelectorTimeout = selTimeout;
    }

    public void setListen(boolean doListen) {
        this.listen = doListen;
    }


    public void setAddress(String host) {
        this.host = host;
    }
    public void setHost(String host) {
        setAddress(host);
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public void setPool(RxTaskPool pool) {
        this.pool = pool;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setAutoBind(int autoBind) {
        this.autoBind = autoBind;
        if ( this.autoBind <= 0 ) this.autoBind = 1;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public void setSoKeepAlive(boolean soKeepAlive) {
        this.soKeepAlive = soKeepAlive;
    }

    public void setOoBInline(boolean ooBInline) {
        this.ooBInline = ooBInline;
    }


    public void setSoLingerOn(boolean soLingerOn) {
        this.soLingerOn = soLingerOn;
    }

    public void setSoLingerTime(int soLingerTime) {
        this.soLingerTime = soLingerTime;
    }

    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = soReuseAddress;
    }

    public void setSoTrafficClass(int soTrafficClass) {
        this.soTrafficClass = soTrafficClass;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setUseBufferPool(boolean useBufferPool) {
        this.useBufferPool = useBufferPool;
    }

    public void setSecurePort(int securePort) {
        this.securePort = securePort;
    }

    public void setMinTasks(int minTasks) {
        this.minTasks = minTasks;
    }

    public void setMaxTasks(int maxTasks) {
        this.maxTasks = maxTasks;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void heartbeat() {
        //empty operation
    }

    @Override
    public int getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public int getUdpRxBufSize() {
        return udpRxBufSize;
    }

    public void setUdpRxBufSize(int udpRxBufSize) {
        this.udpRxBufSize = udpRxBufSize;
    }

    public int getUdpTxBufSize() {
        return udpTxBufSize;
    }

    public void setUdpTxBufSize(int udpTxBufSize) {
        this.udpTxBufSize = udpTxBufSize;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    // ---------------------------------------------- stats of the thread pool
    /**
     * 返回当前由池管理的线程数.
     */
    public int getPoolSize() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getPoolSize();
        } else {
            return -1;
        }
    }

    /**
     * 返回当前正在使用的线程数.
     */
    public int getActiveCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getActiveCount();
        } else {
            return -1;
        }
    }

    /**
     * 返回池中已计划执行的任务的总数.
     */
    public long getTaskCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getTaskCount();
        } else {
            return -1;
        }
    }

    /**
     * 返回池已完成执行的任务总数.
     */
    public long getCompletedTaskCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getCompletedTaskCount();
        } else {
            return -1;
        }
    }

    // ---------------------------------------------- ThreadFactory Inner Class
    class TaskThreadFactory implements ThreadFactory {
        final ThreadGroup group;
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        TaskThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(daemon);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    public boolean isDaemon() {
        return daemon;
    }

    public long getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

}