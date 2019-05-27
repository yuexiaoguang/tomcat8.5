package org.apache.catalina.tribes.transport.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.catalina.tribes.io.ObjectReader;
import org.apache.catalina.tribes.transport.AbstractRxTask;
import org.apache.catalina.tribes.transport.ReceiverBase;
import org.apache.catalina.tribes.transport.RxTaskPool;
import org.apache.catalina.tribes.util.ExceptionUtils;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class NioReceiver extends ReceiverBase implements Runnable, NioReceiverMBean {

    private static final Log log = LogFactory.getLog(NioReceiver.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(NioReceiver.class);

    private volatile boolean running = false;

    private AtomicReference<Selector> selector = new AtomicReference<>();
    private ServerSocketChannel serverChannel = null;
    private DatagramChannel datagramChannel = null;

    protected final Deque<Runnable> events = new ConcurrentLinkedDeque<>();

    public NioReceiver() {
    }

    @Override
    public void stop() {
        this.stopListening();
        super.stop();
    }

    /**
     * 启动集群接收器.
     *
     * @throws IOException 如果接收器启动失败
     */
    @Override
    public void start() throws IOException {
        super.start();
        try {
            setPool(new RxTaskPool(getMaxThreads(),getMinThreads(),this));
        } catch (Exception x) {
            log.fatal(sm.getString("nioReceiver.threadpool.fail"), x);
            if ( x instanceof IOException ) throw (IOException)x;
            else throw new IOException(x.getMessage());
        }
        try {
            getBind();
            bind();
            String channelName = "";
            if (getChannel().getName() != null) channelName = "[" + getChannel().getName() + "]";
            Thread t = new Thread(this, "NioReceiver" + channelName);
            t.setDaemon(true);
            t.start();
        } catch (Exception x) {
            log.fatal(sm.getString("nioReceiver.start.fail"), x);
            if ( x instanceof IOException ) throw (IOException)x;
            else throw new IOException(x.getMessage());
        }
    }

    @Override
    public AbstractRxTask createRxTask() {
        NioReplicationTask thread = new NioReplicationTask(this,this);
        thread.setUseBufferPool(this.getUseBufferPool());
        thread.setRxBufSize(getRxBufSize());
        thread.setOptions(getWorkerThreadOptions());
        return thread;
    }



    protected void bind() throws IOException {
        // 分配一个未绑定的服务器socket channel
        serverChannel = ServerSocketChannel.open();
        // 获取要绑定的关联的 ServerSocket
        ServerSocket serverSocket = serverChannel.socket();
        // 创建一个新的 Selector
        this.selector.set(Selector.open());
        // 设置服务器channel将监听的端口
        //serverSocket.bind(new InetSocketAddress(getBind(), getTcpListenPort()));
        bind(serverSocket,getPort(),getAutoBind());
        // 为监听socket 设置非阻塞模式
        serverChannel.configureBlocking(false);
        // 使用Selector注册 ServerSocketChannel
        serverChannel.register(this.selector.get(), SelectionKey.OP_ACCEPT);

        //设置数据报 channel
        if (this.getUdpPort()>0) {
            datagramChannel = DatagramChannel.open();
            configureDatagraChannel();
            //绑定到地址以避免安全检查
            bindUdp(datagramChannel.socket(),getUdpPort(),getAutoBind());
        }
    }

    private void configureDatagraChannel() throws IOException {
        datagramChannel.configureBlocking(false);
        datagramChannel.socket().setSendBufferSize(getUdpTxBufSize());
        datagramChannel.socket().setReceiveBufferSize(getUdpRxBufSize());
        datagramChannel.socket().setReuseAddress(getSoReuseAddress());
        datagramChannel.socket().setSoTimeout(getTimeout());
        datagramChannel.socket().setTrafficClass(getSoTrafficClass());
    }

    public void addEvent(Runnable event) {
        Selector selector = this.selector.get();
        if (selector != null) {
            events.add(event);
            if (log.isTraceEnabled()) {
                log.trace("Adding event to selector:" + event);
            }
            if (isListening()) {
                selector.wakeup();
            }
        }
    }

    public void events() {
        if (events.isEmpty()) {
            return;
        }
        Runnable r = null;
        while ((r = events.pollFirst()) != null ) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Processing event in selector:" + r);
                }
                r.run();
            } catch (Exception x) {
                log.error("", x);
            }
        }
    }

    public static void cancelledKey(SelectionKey key) {
        ObjectReader reader = (ObjectReader)key.attachment();
        if ( reader != null ) {
            reader.setCancelled(true);
            reader.finish();
        }
        key.cancel();
        key.attach(null);
        if (key.channel() instanceof SocketChannel)
            try { ((SocketChannel)key.channel()).socket().close(); } catch (IOException e) { if (log.isDebugEnabled()) log.debug("", e); }
        if (key.channel() instanceof DatagramChannel)
            try { ((DatagramChannel)key.channel()).socket().close(); } catch (Exception e) { if (log.isDebugEnabled()) log.debug("", e); }
        try { key.channel().close(); } catch (IOException e) { if (log.isDebugEnabled()) log.debug("", e); }

    }
    protected long lastCheck = System.currentTimeMillis();
    protected void socketTimeouts() {
        long now = System.currentTimeMillis();
        if ( (now-lastCheck) < getSelectorTimeout() ) return;
        //timeout
        Selector tmpsel = this.selector.get();
        Set<SelectionKey> keys =  (isListening()&&tmpsel!=null)?tmpsel.keys():null;
        if ( keys == null ) return;
        for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
            SelectionKey key = iter.next();
            try {
//                if (key.interestOps() == SelectionKey.OP_READ) {
//                    //only timeout sockets that we are waiting for a read from
//                    ObjectReader ka = (ObjectReader) key.attachment();
//                    long delta = now - ka.getLastAccess();
//                    if (delta > (long) getTimeout()) {
//                        cancelledKey(key);
//                    }
//                }
//                else
                if ( key.interestOps() == 0 ) {
                    //检查没有进入的key
                    ObjectReader ka = (ObjectReader) key.attachment();
                    if ( ka != null ) {
                        long delta = now - ka.getLastAccess();
                        if (delta > getTimeout() && (!ka.isAccessed())) {
                            if (log.isWarnEnabled())
                                log.warn(sm.getString(
                                        "nioReceiver.threadsExhausted",
                                        Integer.valueOf(getTimeout()),
                                        Boolean.valueOf(ka.isCancelled()),
                                        key,
                                        new java.sql.Timestamp(ka.getLastAccess())));
                            ka.setLastAccess(now);
                            //key.interestOps(SelectionKey.OP_READ);
                        }
                    } else {
                        cancelledKey(key);
                    }
                }
            }catch ( CancelledKeyException ckx ) {
                cancelledKey(key);
            }
        }
        lastCheck = System.currentTimeMillis();
    }


    /**
     * 从channel获取数据, 并将其保存进字节数组, 发送它到集群
     * @throws IOException IO error
     */
    protected void listen() throws Exception {
        if (doListen()) {
            log.warn(sm.getString("nioReceiver.alreadyStarted"));
            return;
        }

        setListen(true);

        // 避免 NPE, 如果选择器被设置为 null 来停止
        Selector selector = this.selector.get();

        if (selector!=null && datagramChannel!=null) {
            ObjectReader oreader = new ObjectReader(MAX_UDP_SIZE); //max size for a datagram packet
            registerChannel(selector,datagramChannel,SelectionKey.OP_READ,oreader);
        }

        while (doListen() && selector != null) {
            // 这可能会阻碍很长一段时间, 返回时，所选集合包含就绪channel的key
            try {
                events();
                socketTimeouts();
                int n = selector.select(getSelectorTimeout());
                if (n == 0) {
                    //TcpReplicationThread 调用选择器的 wakeup().
                    //如果发生, 必须确保线程有足够的时间来调用 interestOps
//                    synchronized (interestOpsMutex) {
                        //如果获得了这个锁, 意味着没有key尝试注册
//                    }
                    continue; // nothing to do
                }
                // get an iterator over the set of selected keys
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                // 查看所选集合中的每个 key
                while (it!=null && it.hasNext()) {
                    SelectionKey key = it.next();
                    // 是否是一个新的连接进来?
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel channel = server.accept();
                        channel.socket().setReceiveBufferSize(getTxBufSize());
                        channel.socket().setSendBufferSize(getTxBufSize());
                        channel.socket().setTcpNoDelay(getTcpNoDelay());
                        channel.socket().setKeepAlive(getSoKeepAlive());
                        channel.socket().setOOBInline(getOoBInline());
                        channel.socket().setReuseAddress(getSoReuseAddress());
                        channel.socket().setSoLinger(getSoLingerOn(),getSoLingerTime());
                        channel.socket().setSoTimeout(getTimeout());
                        Object attach = new ObjectReader(channel);
                        registerChannel(selector,
                                        channel,
                                        SelectionKey.OP_READ,
                                        attach);
                    }
                    // 在这个通道上有数据可读吗?
                    if (key.isReadable()) {
                        readDataFromSocket(key);
                    } else {
                        key.interestOps(key.interestOps() & (~SelectionKey.OP_WRITE));
                    }

                    // 从选择的集合中删除key, 它已经被处理
                    it.remove();
                }
            } catch (java.nio.channels.ClosedSelectorException cse) {
                // 关闭或停止监听socket时, 忽略是正常的
            } catch (java.nio.channels.CancelledKeyException nx) {
                log.warn(sm.getString("nioReceiver.clientDisconnect"));
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("nioReceiver.requestError"), t);
            }

        }
        serverChannel.close();
        if (datagramChannel!=null) {
            try {
                datagramChannel.close();
            }catch (Exception iox) {
                if (log.isDebugEnabled()) log.debug("Unable to close datagram channel.",iox);
            }
            datagramChannel=null;
        }
        closeSelector();
    }



    /**
     * Close Selector.
     */
    protected void stopListening() {
        setListen(false);
        Selector selector = this.selector.get();
        if (selector != null) {
            try {
                // 等待输入过程中阻塞, 解锁线程
                selector.wakeup();
                // 等待接收器线程完成
                int count = 0;
                while (running && count < 50) {
                    Thread.sleep(100);
                    count ++;
                }
                if (running) {
                    log.warn(sm.getString("nioReceiver.stop.threadRunning"));
                }
                closeSelector();
            } catch (Exception x) {
                log.error(sm.getString("nioReceiver.stop.fail"), x);
            } finally {
                this.selector.set(null);
            }
        }
    }

    private void closeSelector() throws IOException {
        Selector selector = this.selector.getAndSet(null);
        if (selector == null) return;
        try {
            Iterator<SelectionKey> it = selector.keys().iterator();
            // 查看所选集合中的每个 key
            while (it.hasNext()) {
                SelectionKey key = it.next();
                key.channel().close();
                key.attach(null);
                key.cancel();
            }
        } catch (IOException ignore){
            if (log.isWarnEnabled()) {
                log.warn(sm.getString("nioReceiver.cleanup.fail"), ignore);
            }
        } catch (ClosedSelectorException ignore){
            // Ignore
        }
        try {
            selector.selectNow();
        } catch (Throwable t){
            ExceptionUtils.handleThrowable(t);
            // Ignore everything else
        }
        selector.close();
    }

    // ----------------------------------------------------------

    /**
     * 使用给定的选择器注册给定的 channel, 为给定的操作
     * 
     * @param selector 要使用的选择器
     * @param channel The channel
     * @param ops 注册操作
     * @param attach Attachment object
     * @throws Exception IO error with channel
     */
    protected void registerChannel(Selector selector,
                                   SelectableChannel channel,
                                   int ops,
                                   Object attach) throws Exception {
        if (channel == null)return; // could happen
        // 设置非阻塞的新 channel
        channel.configureBlocking(false);
        // 使用选择器注册
        channel.register(selector, ops, attach);
    }

    /**
     * 启动线程并监听
     */
    @Override
    public void run() {
        running = true;
        try {
            listen();
        } catch (Exception x) {
            log.error(sm.getString("nioReceiver.run.fail"), x);
        } finally {
            running = false;
        }
    }

    // ----------------------------------------------------------

    /**
     * 数据处理方法, 用于数据已经准备读取的 channel.
     * @param key channel关联的SelectionKey对象, 由选择器确定准备读取的. 如果channel返回EOF条件, 在这里关闭, 自动使关联key无效.
     * 选择器将在下一次选择调用时, 取消信道注册.
     * 
     * @throws Exception channel的IO 错误
     */
    protected void readDataFromSocket(SelectionKey key) throws Exception {
        NioReplicationTask task = (NioReplicationTask) getTaskPool().getRxTask();
        if (task == null) {
            // 没有可用的线程/任务, 选择将循环继续调用此方法, 直到线程可用, 线程池本身具有等待机制, 因此不会在这里等待
            if (log.isDebugEnabled()) log.debug("No TcpReplicationThread available");
        } else {
            // 调用这个唤醒工作者线程, 然后返回 
            // 添加任务到线程池
            task.serviceChannel(key);
            getExecutor().execute(task);
        }
    }
}
