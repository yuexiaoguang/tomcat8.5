package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteBufferHolder;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.jsse.JSSESupport;

/**
 * NIO2 端点.
 */
public class Nio2Endpoint extends AbstractJsseEndpoint<Nio2Channel> {


    // -------------------------------------------------------------- Constants


    private static final Log log = LogFactory.getLog(Nio2Endpoint.class);


    // ----------------------------------------------------------------- Fields

    /**
     * 服务器套接字 "pointer".
     */
    private AsynchronousServerSocketChannel serverSock = null;

    /**
     * 检测完成处理程序是否内联完成.
     */
    private static ThreadLocal<Boolean> inlineCompletion = new ThreadLocal<>();

    /**
     * 与服务器套接字关联的线程组.
     */
    private AsynchronousChannelGroup threadGroup = null;

    private volatile boolean allClosed;

    /**
     * Bytebuffer缓存, 每个通道都有一组缓冲区 (two, 除了SSL持有四个)
     */
    private SynchronizedStack<Nio2Channel> nioChannels;


    public Nio2Endpoint() {
        // 覆盖NIO2的默认值
        // 默认情况下，为NIO2禁用maxConnections (see BZ58103)
        setMaxConnections(-1);
    }


    // ------------------------------------------------------------- Properties

    public void setSocketProperties(SocketProperties socketProperties) {
        this.socketProperties = socketProperties;
    }

    /**
     * 是否支持deferAccept?
     */
    @Override
    public boolean getDeferAccept() {
        // Not supported
        return false;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * keep-alive套接字的数量.
     *
     * @return 总是返回 -1.
     */
    public int getKeepAliveCount() {
        // 对于这个连接器, 只有整体连接数是相关的
        return -1;
    }


    // ----------------------------------------------- Public Lifecycle Methods


    /**
     * 初始化端点.
     */
    @Override
    public void bind() throws Exception {

        // 创建工作者集合
        if ( getExecutor() == null ) {
            createExecutor();
        }
        if (getExecutor() instanceof ExecutorService) {
            threadGroup = AsynchronousChannelGroup.withThreadPool((ExecutorService) getExecutor());
        }
        // AsynchronousChannelGroup当前需要对其执行程序服务的独占访问权限
        if (!internalExecutor) {
            log.warn(sm.getString("endpoint.nio2.exclusiveExecutor"));
        }

        serverSock = AsynchronousServerSocketChannel.open(threadGroup);
        socketProperties.setProperties(serverSock);
        InetSocketAddress addr = (getAddress()!=null?new InetSocketAddress(getAddress(),getPort()):new InetSocketAddress(getPort()));
        serverSock.bind(addr,getAcceptCount());

        // 初始化接受器，轮询器的线程计数默认值
        if (acceptorThreadCount != 1) {
            // NIO2不允许任何形式的IO并发
            acceptorThreadCount = 1;
        }

        // 初始化SSL
        initialiseSsl();
    }


    /**
     * 启动NIO2端点, 创建接受者.
     */
    @Override
    public void startInternal() throws Exception {

        if (!running) {
            allClosed = false;
            running = true;
            paused = false;

            processorCache = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getProcessorCache());
            nioChannels = new SynchronizedStack<>(SynchronizedStack.DEFAULT_SIZE,
                    socketProperties.getBufferPool());

            // 创建工作者集合
            if ( getExecutor() == null ) {
                createExecutor();
            }

            initializeConnectionLatch();
            startAcceptorThreads();
        }
    }


    /**
     * 停止端点. 这将导致所有处理线程停止.
     */
    @Override
    public void stopInternal() {
        releaseConnectionLatch();
        if (!paused) {
            pause();
        }
        if (running) {
            running = false;
            unlockAccept();
            // 如果出现不良情况，请使用执行程序以避免绑定主线程，并且解除绑定也会等待一段时间才能完成
            getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    // 然后关闭所有活动连接
                    try {
                        for (Nio2Channel channel : getHandler().getOpenSockets()) {
                            closeSocket(channel.getSocket());
                        }
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    } finally {
                        allClosed = true;
                    }
                }
            });
            nioChannels.clear();
            processorCache.clear();
        }
    }


    /**
     * 释放NIO内存池, 并关闭服务器套接字.
     */
    @Override
    public void unbind() throws Exception {
        if (running) {
            stop();
        }
        // 关闭服务器套接字
        serverSock.close();
        serverSock = null;
        destroySsl();
        super.unbind();
        // 不像其他连接器, 线程池绑定到服务器套接字
        shutdownExecutor();
        if (getHandler() != null) {
            getHandler().recycle();
        }
    }


    @Override
    public void shutdownExecutor() {
        if (threadGroup != null && internalExecutor) {
            try {
                long timeout = getExecutorTerminationTimeoutMillis();
                while (timeout > 0 && !allClosed) {
                    timeout -= 100;
                    Thread.sleep(100);
                }
                threadGroup.shutdownNow();
                if (timeout > 0) {
                    threadGroup.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                }
            } catch (IOException e) {
                getLog().warn(sm.getString("endpoint.warn.executorShutdown", getName()), e);
            } catch (InterruptedException e) {
                // Ignore
            }
            if (!threadGroup.isTerminated()) {
                getLog().warn(sm.getString("endpoint.warn.executorShutdown", getName()));
            }
            threadGroup = null;
        }
        // 主要是清理引用
        super.shutdownExecutor();
    }


    // ------------------------------------------------------ Protected Methods


    public int getWriteBufSize() {
        return socketProperties.getTxBufSize();
    }

    public int getReadBufSize() {
        return socketProperties.getRxBufSize();
    }

    @Override
    protected AbstractEndpoint.Acceptor createAcceptor() {
        return new Acceptor();
    }

    /**
     * 处理指定的连接.
     * 
     * @param socket 套接字通道
     * 
     * @return <code>true</code>如果套接字配置正确，处理可能会继续; <code>false</code>如果套接字需要立即关闭
     */
    protected boolean setSocketOptions(AsynchronousSocketChannel socket) {
        try {
            socketProperties.setProperties(socket);
            Nio2Channel channel = nioChannels.pop();
            if (channel == null) {
                SocketBufferHandler bufhandler = new SocketBufferHandler(
                        socketProperties.getAppReadBufSize(),
                        socketProperties.getAppWriteBufSize(),
                        socketProperties.getDirectBuffer());
                if (isSSLEnabled()) {
                    channel = new SecureNio2Channel(bufhandler, this);
                } else {
                    channel = new Nio2Channel(bufhandler);
                }
            }
            Nio2SocketWrapper socketWrapper = new Nio2SocketWrapper(channel, this);
            channel.reset(socket, socketWrapper);
            socketWrapper.setReadTimeout(getSocketProperties().getSoTimeout());
            socketWrapper.setWriteTimeout(getSocketProperties().getSoTimeout());
            socketWrapper.setKeepAliveLeft(Nio2Endpoint.this.getMaxKeepAliveRequests());
            socketWrapper.setSecure(isSSLEnabled());
            socketWrapper.setReadTimeout(getConnectionTimeout());
            socketWrapper.setWriteTimeout(getConnectionTimeout());
            // 继续处理另一个线程
            return processSocket(socketWrapper, SocketEvent.OPEN_READ, true);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error("",t);
        }
        // 告诉关闭套接字
        return false;
    }


    @Override
    protected SocketProcessorBase<Nio2Channel> createSocketProcessor(
            SocketWrapperBase<Nio2Channel> socketWrapper, SocketEvent event) {
        return new SocketProcessor(socketWrapper, event);
    }


    public void closeSocket(SocketWrapperBase<Nio2Channel> socket) {
        if (log.isDebugEnabled()) {
            log.debug("Calling [" + this + "].closeSocket([" + socket + "],[" + socket.getSocket() + "])",
                    new Exception());
        }
        if (socket == null) {
            return;
        }
        try {
            getHandler().release(socket);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
        try {
            synchronized (socket.getSocket()) {
                if (socket.getSocket().isOpen()) {
                    countDownConnection();
                    socket.getSocket().close(true);
                }
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
        try {
            Nio2SocketWrapper nio2Socket = (Nio2SocketWrapper) socket;
            if (nio2Socket.getSendfileData() != null
                    && nio2Socket.getSendfileData().fchannel != null
                    && nio2Socket.getSendfileData().fchannel.isOpen()) {
                nio2Socket.getSendfileData().fchannel.close();
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected NetworkChannel getServerSocket() {
        return serverSock;
    }


    // --------------------------------------------------- Acceptor Inner Class
    /**
     * 使用NIO2, 主接受器线程仅启动初始接受, 但定期检查连接器是否仍在接受(如果没有，它会尝试重新开始).
     */
    protected class Acceptor extends AbstractEndpoint.Acceptor {

        @Override
        public void run() {

            int errorDelay = 0;

            // 循环, 直到收到关机命令
            while (running) {

                // 如果端点暂停，则循环
                while (paused && running) {
                    state = AcceptorState.PAUSED;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (!running) {
                    break;
                }
                state = AcceptorState.RUNNING;

                try {
                    //如果达到了最大连接数, 等待
                    countUpOrAwaitConnection();

                    AsynchronousSocketChannel socket = null;
                    try {
                        // 从服务器套接字接受下一个传入连接
                        socket = serverSock.accept().get();
                    } catch (Exception e) {
                        // 没有得到套接字
                        countDownConnection();
                        if (running) {
                            // 如有必要，请引入延迟
                            errorDelay = handleExceptionWithDelay(errorDelay);
                            // re-throw
                            throw e;
                        } else {
                            break;
                        }
                    }
                    // 成功接受, 重置错误延迟
                    errorDelay = 0;

                    // 配置套接字
                    if (running && !paused) {
                        // 如果成功，setSocketOptions()将把套接字交给适当的处理器
                        if (!setSocketOptions(socket)) {
                            closeSocket(socket);
                       }
                    } else {
                        closeSocket(socket);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }
            }
            state = AcceptorState.ENDED;
        }


        private void closeSocket(AsynchronousSocketChannel socket) {
            countDownConnection();
            try {
                socket.close();
            } catch (IOException ioe) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.err.close"), ioe);
                }
            }
        }
    }


    public static class Nio2SocketWrapper extends SocketWrapperBase<Nio2Channel> {

        private static final ThreadLocal<AtomicInteger> nestedWriteCompletionCount =
                new ThreadLocal<AtomicInteger>() {
            @Override
            protected AtomicInteger initialValue() {
                return new AtomicInteger(0);
            }
        };

        private SendfileData sendfileData = null;

        private final CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> readCompletionHandler;
        private final Semaphore readPending = new Semaphore(1);
        private boolean readInterest = false; // Guarded by readCompletionHandler

        private final CompletionHandler<Integer, ByteBuffer> writeCompletionHandler;
        private final CompletionHandler<Long, ByteBuffer[]> gatheringWriteCompletionHandler;
        private final Semaphore writePending = new Semaphore(1);
        private boolean writeInterest = false; // Guarded by writeCompletionHandler
        private boolean writeNotify = false;

        private CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> awaitBytesHandler
                = new CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>>() {

            @Override
            public void completed(Integer nBytes, SocketWrapperBase<Nio2Channel> attachment) {
                if (nBytes.intValue() < 0) {
                    failed(new ClosedChannelException(), attachment);
                    return;
                }
                getEndpoint().processSocket(attachment, SocketEvent.OPEN_READ, Nio2Endpoint.isInline());
            }

            @Override
            public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
                getEndpoint().processSocket(attachment, SocketEvent.DISCONNECT, true);
            }
        };

        private CompletionHandler<Integer, SendfileData> sendfileHandler
            = new CompletionHandler<Integer, SendfileData>() {

            @Override
            public void completed(Integer nWrite, SendfileData attachment) {
                if (nWrite.intValue() < 0) {
                    failed(new EOFException(), attachment);
                    return;
                }
                attachment.pos += nWrite.intValue();
                ByteBuffer buffer = getSocket().getBufHandler().getWriteBuffer();
                if (!buffer.hasRemaining()) {
                    if (attachment.length <= 0) {
                        // 已经写入了所有数据
                        setSendfileData(null);
                        try {
                            attachment.fchannel.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                        if (isInline()) {
                            attachment.doneInline = true;
                        } else {
                            switch (attachment.keepAliveState) {
                            case NONE: {
                                getEndpoint().processSocket(Nio2SocketWrapper.this,
                                        SocketEvent.DISCONNECT, false);
                                break;
                            }
                            case PIPELINED: {
                                getEndpoint().processSocket(Nio2SocketWrapper.this,
                                        SocketEvent.OPEN_READ, true);
                                break;
                            }
                            case OPEN: {
                                awaitBytes();
                                break;
                            }
                            }
                        }
                        return;
                    } else {
                        getSocket().getBufHandler().configureWriteBufferForWrite();
                        int nRead = -1;
                        try {
                            nRead = attachment.fchannel.read(buffer);
                        } catch (IOException e) {
                            failed(e, attachment);
                            return;
                        }
                        if (nRead > 0) {
                            getSocket().getBufHandler().configureWriteBufferForRead();
                            if (attachment.length < buffer.remaining()) {
                                buffer.limit(buffer.limit() - buffer.remaining() + (int) attachment.length);
                            }
                            attachment.length -= nRead;
                        } else {
                            failed(new EOFException(), attachment);
                            return;
                        }
                    }
                }
                getSocket().write(buffer, getNio2WriteTimeout(), TimeUnit.MILLISECONDS, attachment, this);
            }

            @Override
            public void failed(Throwable exc, SendfileData attachment) {
                try {
                    attachment.fchannel.close();
                } catch (IOException e) {
                    // Ignore
                }
                if (!isInline()) {
                    getEndpoint().processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, false);
                } else {
                    attachment.doneInline = true;
                    attachment.error = true;
                }
            }
        };

        public Nio2SocketWrapper(Nio2Channel channel, final Nio2Endpoint endpoint) {
            super(channel, endpoint);
            socketBufferHandler = channel.getBufHandler();

            this.readCompletionHandler = new CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>>() {
                @Override
                public void completed(Integer nBytes, SocketWrapperBase<Nio2Channel> attachment) {
                    boolean notify = false;
                    if (log.isDebugEnabled()) {
                        log.debug("Socket: [" + attachment + "], Interest: [" + readInterest + "]");
                    }
                    synchronized (readCompletionHandler) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(), attachment);
                        } else {
                            if (readInterest && !Nio2Endpoint.isInline()) {
                                readInterest = false;
                                notify = true;
                            } else {
                                // 这里释放, 因为没有 notify/dispatch 来释放.
                                readPending.release();
                            }
                        }
                    }
                    if (notify) {
                        getEndpoint().processSocket(attachment, SocketEvent.OPEN_READ, false);
                    }
                }
                @Override
                public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
                    IOException ioe;
                    if (exc instanceof IOException) {
                        ioe = (IOException) exc;
                    } else {
                        ioe = new IOException(exc);
                    }
                    setError(ioe);
                    if (exc instanceof AsynchronousCloseException) {
                        // 这里释放, 因为没有 notify/dispatch 来释放.
                        readPending.release();
                        // 如果已经关闭, 不要调用onError并再次关闭
                        return;
                    }
                    getEndpoint().processSocket(attachment, SocketEvent.ERROR, true);
                }
            };

            this.writeCompletionHandler = new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer nBytes, ByteBuffer attachment) {
                    writeNotify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (bufferedWrites.size() > 0) {
                            nestedWriteCompletionCount.get().incrementAndGet();
                            // 继续使用聚集写入来写入数据
                            ArrayList<ByteBuffer> arrayList = new ArrayList<>();
                            if (attachment.hasRemaining()) {
                                arrayList.add(attachment);
                            }
                            for (ByteBufferHolder buffer : bufferedWrites) {
                                buffer.flip();
                                arrayList.add(buffer.getBuf());
                            }
                            bufferedWrites.clear();
                            ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                            getSocket().write(array, 0, array.length,
                                    getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
                                    array, gatheringWriteCompletionHandler);
                            nestedWriteCompletionCount.get().decrementAndGet();
                        } else if (attachment.hasRemaining()) {
                            // Regular write
                            nestedWriteCompletionCount.get().incrementAndGet();
                            getSocket().write(attachment, getNio2WriteTimeout(),
                                    TimeUnit.MILLISECONDS, attachment, writeCompletionHandler);
                            nestedWriteCompletionCount.get().decrementAndGet();
                        } else {
                            // All data has been written
                            if (writeInterest) {
                                writeInterest = false;
                                writeNotify = true;
                            }
                            writePending.release();
                        }
                    }
                    if (writeNotify && nestedWriteCompletionCount.get().get() == 0) {
                        endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_WRITE, Nio2Endpoint.isInline());
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    IOException ioe;
                    if (exc instanceof IOException) {
                        ioe = (IOException) exc;
                    } else {
                        ioe = new IOException(exc);
                    }
                    setError(ioe);
                    writePending.release();
                    endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, true);
                }
            };

            gatheringWriteCompletionHandler = new CompletionHandler<Long, ByteBuffer[]>() {
                @Override
                public void completed(Long nBytes, ByteBuffer[] attachment) {
                    writeNotify = false;
                    synchronized (writeCompletionHandler) {
                        if (nBytes.longValue() < 0) {
                            failed(new EOFException(sm.getString("iob.failedwrite")), attachment);
                        } else if (bufferedWrites.size() > 0 || arrayHasData(attachment)) {
                            // Continue writing data
                            nestedWriteCompletionCount.get().incrementAndGet();
                            ArrayList<ByteBuffer> arrayList = new ArrayList<>();
                            for (ByteBuffer buffer : attachment) {
                                if (buffer.hasRemaining()) {
                                    arrayList.add(buffer);
                                }
                            }
                            for (ByteBufferHolder buffer : bufferedWrites) {
                                buffer.flip();
                                arrayList.add(buffer.getBuf());
                            }
                            bufferedWrites.clear();
                            ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                            getSocket().write(array, 0, array.length,
                                    getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
                                    array, gatheringWriteCompletionHandler);
                            nestedWriteCompletionCount.get().decrementAndGet();
                        } else {
                            // All data has been written
                            if (writeInterest) {
                                writeInterest = false;
                                writeNotify = true;
                            }
                            writePending.release();
                        }
                    }
                    if (writeNotify && nestedWriteCompletionCount.get().get() == 0) {
                        endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.OPEN_WRITE, Nio2Endpoint.isInline());
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer[] attachment) {
                    IOException ioe;
                    if (exc instanceof IOException) {
                        ioe = (IOException) exc;
                    } else {
                        ioe = new IOException(exc);
                    }
                    setError(ioe);
                    writePending.release();
                    endpoint.processSocket(Nio2SocketWrapper.this, SocketEvent.ERROR, true);
               }
            };

        }

        private static boolean arrayHasData(ByteBuffer[] byteBuffers) {
            for (ByteBuffer byteBuffer : byteBuffers) {
                if (byteBuffer.hasRemaining()) {
                    return true;
                }
            }
            return false;
        }


        public void setSendfileData(SendfileData sf) { this.sendfileData = sf; }
        public SendfileData getSendfileData() { return this.sendfileData; }

        @Override
        public boolean isReadyForRead() throws IOException {
            synchronized (readCompletionHandler) {
                if (!readPending.tryAcquire()) {
                    readInterest = true;
                    return false;
                }

                if (!socketBufferHandler.isReadBufferEmpty()) {
                    readPending.release();
                    return true;
                }

                int nRead = fillReadBuffer(false);

                boolean isReady = nRead > 0;

                if (!isReady) {
                    readInterest = true;
                }
                return isReady;
            }
        }


        @Override
        public int read(boolean block, byte[] b, int off, int len) throws IOException {
            checkError();

            if (log.isDebugEnabled()) {
                log.debug("Socket: [" + this + "], block: [" + block + "], length: [" + len + "]");
            }

            if (socketBufferHandler == null) {
                throw new IOException(sm.getString("socket.closed"));
            }

            if (block) {
                try {
                    readPending.acquire();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            } else {
                if (!readPending.tryAcquire()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Socket: [" + this + "], Read in progress. Returning [0]");
                    }
                    return 0;
                }
            }

            int nRead = populateReadBuffer(b, off, len);
            if (nRead > 0) {
                // 这可能足以完成请求，不希望触发另一个读取，因为如果没有更多数据要读取，并且此请求需要一段时间来处理读取, 将超时触发错误.
                readPending.release();
                return nRead;
            }

            synchronized (readCompletionHandler) {
                // 尽可能地填充读缓冲区.
                nRead = fillReadBuffer(block);

                // 使用刚读取的数据, 尽可能多地填充剩余的字节数组
                if (nRead > 0) {
                    socketBufferHandler.configureReadBufferForRead();
                    nRead = Math.min(nRead, len);
                    socketBufferHandler.getReadBuffer().get(b, off, nRead);
                } else if (nRead == 0 && !block) {
                    readInterest = true;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Socket: [" + this + "], Read: [" + nRead + "]");
                }
                return nRead;
            }
        }


        @Override
        public int read(boolean block, ByteBuffer to) throws IOException {
            checkError();

            if (socketBufferHandler == null) {
                throw new IOException(sm.getString("socket.closed"));
            }

            if (block) {
                try {
                    readPending.acquire();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            } else {
                if (!readPending.tryAcquire()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Socket: [" + this + "], Read in progress. Returning [0]");
                    }
                    return 0;
                }
            }

            int nRead = populateReadBuffer(to);
            if (nRead > 0) {
                // 这可能足以完成请求，不希望触发另一个读取，因为如果没有更多数据要读取，并且此请求需要一段时间来处理读取, 将超时触发错误.
                readPending.release();
                return nRead;
            }

            synchronized (readCompletionHandler) {
                // 套接字读缓冲区的容量是socket.appReadBufSize
                int limit = socketBufferHandler.getReadBuffer().capacity();
                if (block && to.remaining() >= limit) {
                    to.limit(to.position() + limit);
                    nRead = fillReadBuffer(block, to);
                } else {
                    // 尽可能地填充读缓冲区.
                    nRead = fillReadBuffer(block);

                    // 使用刚读取的数据, 尽可能多地填充剩余的字节数组
                    if (nRead > 0) {
                        nRead = populateReadBuffer(to);
                    } else if (nRead == 0 && !block) {
                        readInterest = true;
                    }
                }

                return nRead;
            }
        }


        @Override
        public void close() throws IOException {
            getSocket().close();
        }


        @Override
        public boolean isClosed() {
            return !getSocket().isOpen();
        }


        @Override
        public boolean hasAsyncIO() {
            return false;
        }

        /**
         * 用于分散/聚集操作的内部状态跟踪器.
         */
        private static class OperationState<A> {
            private final ByteBuffer[] buffers;
            private final int offset;
            private final int length;
            private final A attachment;
            private final long timeout;
            private final TimeUnit unit;
            private final CompletionCheck check;
            private final CompletionHandler<Long, ? super A> handler;
            private OperationState(ByteBuffer[] buffers, int offset, int length,
                    long timeout, TimeUnit unit, A attachment, CompletionCheck check,
                    CompletionHandler<Long, ? super A> handler) {
                this.buffers = buffers;
                this.offset = offset;
                this.length = length;
                this.timeout = timeout;
                this.unit = unit;
                this.attachment = attachment;
                this.check = check;
                this.handler = handler;
            }
            private volatile long nBytes = 0;
            private volatile CompletionState state = CompletionState.PENDING;
        }

        private class ScatterReadCompletionHandler<A> implements CompletionHandler<Long, OperationState<A>> {
            @Override
            public void completed(Long nBytes, OperationState<A> state) {
                if (nBytes.intValue() < 0) {
                    failed(new EOFException(), state);
                } else {
                    state.nBytes += nBytes.longValue();
                    CompletionState currentState = Nio2Endpoint.isInline() ? CompletionState.INLINE : CompletionState.DONE;
                    boolean complete = true;
                    boolean completion = true;
                    if (state.check != null) {
                        switch (state.check.callHandler(currentState, state.buffers, state.offset, state.length)) {
                        case CONTINUE:
                            complete = false;
                            break;
                        case DONE:
                            break;
                        case NONE:
                            completion = false;
                            break;
                        }
                    }
                    if (complete) {
                        readPending.release();
                        state.state = currentState;
                        if (completion && state.handler != null) {
                            state.handler.completed(Long.valueOf(state.nBytes), state.attachment);
                        }
                    } else {
                        getSocket().read(state.buffers, state.offset, state.length,
                                state.timeout, state.unit, state, this);
                    }
                }
            }
            @Override
            public void failed(Throwable exc, OperationState<A> state) {
                IOException ioe;
                if (exc instanceof IOException) {
                    ioe = (IOException) exc;
                } else {
                    ioe = new IOException(exc);
                }
                setError(ioe);
                readPending.release();
                if (exc instanceof AsynchronousCloseException) {
                    // 如果已经关闭, 不要调用onError并再次关闭
                    return;
                }
                state.state = Nio2Endpoint.isInline() ? CompletionState.ERROR : CompletionState.DONE;
                if (state.handler != null) {
                    state.handler.failed(ioe, state.attachment);
                }
            }
        }

        private class GatherWriteCompletionHandler<A> implements CompletionHandler<Long, OperationState<A>> {
            @Override
            public void completed(Long nBytes, OperationState<A> state) {
                if (nBytes.longValue() < 0) {
                    failed(new EOFException(), state);
                } else {
                    state.nBytes += nBytes.longValue();
                    CompletionState currentState = Nio2Endpoint.isInline() ? CompletionState.INLINE : CompletionState.DONE;
                    boolean complete = true;
                    boolean completion = true;
                    if (state.check != null) {
                        switch (state.check.callHandler(currentState, state.buffers, state.offset, state.length)) {
                        case CONTINUE:
                            complete = false;
                            break;
                        case DONE:
                            break;
                        case NONE:
                            completion = false;
                            break;
                        }
                    }
                    if (complete) {
                        writePending.release();
                        state.state = currentState;
                        if (completion && state.handler != null) {
                            state.handler.completed(Long.valueOf(state.nBytes), state.attachment);
                        }
                    } else {
                        getSocket().write(state.buffers, state.offset, state.length,
                                state.timeout, state.unit, state, this);
                    }
                }
            }
            @Override
            public void failed(Throwable exc, OperationState<A> state) {
                IOException ioe;
                if (exc instanceof IOException) {
                    ioe = (IOException) exc;
                } else {
                    ioe = new IOException(exc);
                }
                setError(ioe);
                writePending.release();
                state.state = Nio2Endpoint.isInline() ? CompletionState.ERROR : CompletionState.DONE;
                if (state.handler != null) {
                    state.handler.failed(ioe, state.attachment);
                }
            }
        }

        @Override
        public <A> CompletionState read(ByteBuffer[] dsts, int offset, int length,
                boolean block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
            OperationState<A> state = new OperationState<>(dsts, offset, length, timeout, unit, attachment, check, handler);
            try {
                if ((!block && readPending.tryAcquire()) || (block && readPending.tryAcquire(timeout, unit))) {
                    Nio2Endpoint.startInline();
                    getSocket().read(dsts, offset, length, timeout, unit, state, new ScatterReadCompletionHandler<A>());
                    Nio2Endpoint.endInline();
                } else {
                    throw new ReadPendingException();
                }
                if (block && state.state == CompletionState.PENDING && readPending.tryAcquire(timeout, unit)) {
                    readPending.release();
                }
            } catch (InterruptedException e) {
                handler.failed(e, attachment);
            }
            return state.state;
        }

        @Override
        public boolean isWritePending() {
            synchronized (writeCompletionHandler) {
                return writePending.availablePermits() == 0;
            }
        }

        @Override
        public <A> CompletionState write(ByteBuffer[] srcs, int offset, int length,
                boolean block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler) {
            OperationState<A> state = new OperationState<>(srcs, offset, length, timeout, unit, attachment, check, handler);
            try {
                if ((!block && writePending.tryAcquire()) || (block && writePending.tryAcquire(timeout, unit))) {
                    Nio2Endpoint.startInline();
                    getSocket().write(srcs, offset, length, timeout, unit, state, new GatherWriteCompletionHandler<A>());
                    Nio2Endpoint.endInline();
                } else {
                    throw new WritePendingException();
                }
                if (block && state.state == CompletionState.PENDING && writePending.tryAcquire(timeout, unit)) {
                    writePending.release();
                }
            } catch (InterruptedException e) {
                handler.failed(e, attachment);
            }
            return state.state;
        }

        /* 这个方法的调用者必须:
         * - 已经获得了readPending信号量
         * - 已经获得了对readCompletionHandler的锁
         *
         * 一旦读取完成，此方法将释放（或安排释放）readPending信号量.
         */
        private int fillReadBuffer(boolean block) throws IOException {
            socketBufferHandler.configureReadBufferForWrite();
            return fillReadBuffer(block, socketBufferHandler.getReadBuffer());
        }

        private int fillReadBuffer(boolean block, ByteBuffer to) throws IOException {
            int nRead = 0;
            Future<Integer> integer = null;
            if (block) {
                try {
                    integer = getSocket().read(to);
                    nRead = integer.get(getNio2ReadTimeout(), TimeUnit.MILLISECONDS).intValue();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    } else {
                        throw new IOException(e);
                    }
                } catch (InterruptedException e) {
                    throw new IOException(e);
                } catch (TimeoutException e) {
                    integer.cancel(true);
                    throw new SocketTimeoutException();
                } finally {
                    // 阻塞读取因此需要在此处释放，因为不会有对完成处理程序的回调.
                    readPending.release();
                }
            } else {
                Nio2Endpoint.startInline();
                getSocket().read(to, getNio2ReadTimeout(), TimeUnit.MILLISECONDS, this,
                        readCompletionHandler);
                Nio2Endpoint.endInline();
                if (readPending.availablePermits() == 1) {
                    nRead = to.position();
                }
            }
            return nRead;
        }


        /**
         * {@inheritDoc}
         * <p>
         * 如果非阻塞写入将数据保留在缓冲区中，则为NIO2重写以启用收集写入, 用于在单个额外的写入中写入所有剩余数据.
         */
        @Override
        protected void writeNonBlocking(byte[] buf, int off, int len) throws IOException {
            // Note: 可能的替代行为:
            // 如果有非阻塞滥用 (例如在测试中, 在单个“非阻塞”写入中写入1MB), 然后阻塞直到完成上一次写入而不是继续缓冲
            // 还允许进行自动阻塞
            // 可以通过与主CoyoteOutputStream协调来“智能”指示写入的结束
            // Uses: if (writePending.tryAcquire(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS))
            synchronized (writeCompletionHandler) {
                if (writePending.tryAcquire()) {
                    // 没有待处理的完成处理程序, 所以写入主缓冲区是可能的
                    socketBufferHandler.configureWriteBufferForWrite();
                    int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
                    len = len - thisTime;
                    off = off + thisTime;
                    if (len > 0) {
                        // 必须缓冲剩余数据
                        addToBuffers(buf, off, len);
                    }
                    flushNonBlocking(true);
                } else {
                    addToBuffers(buf, off, len);
                }
            }
        }


        /**
         * {@inheritDoc}
         * <p>
         * 如果非阻塞写入将数据保留在缓冲区中，则为NIO2重写以启用收集写入, 用于在单个额外的写入中写入所有剩余数据.
         */
        @Override
        protected void writeNonBlocking(ByteBuffer from) throws IOException {
            // Note: 可能的替代行为:
            // 如果有非阻塞滥用 (例如在测试中, 在单个“非阻塞”写入中写入1MB), 然后阻塞直到完成上一次写入而不是继续缓冲
            // 还允许进行自动阻塞
            // 可以通过与主CoyoteOutputStream协调来“智能”指示写入的结束
            // Uses: if (writePending.tryAcquire(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS))
            synchronized (writeCompletionHandler) {
                if (writePending.tryAcquire()) {
                    // 没有待处理的完成处理程序, 所以写入主缓冲区是可能的
                    socketBufferHandler.configureWriteBufferForWrite();
                    transfer(from, socketBufferHandler.getWriteBuffer());
                    if (from.remaining() > 0) {
                        // 必须缓冲剩余数据
                        addToBuffers(from);
                    }
                    flushNonBlocking(true);
                } else {
                    addToBuffers(from);
                }
            }
        }


        /**
         * @param block 忽略，因为此方法仅在阻塞情况下调用
         */
        @Override
        protected void doWrite(boolean block, ByteBuffer from) throws IOException {
            Future<Integer> integer = null;
            try {
                do {
                    integer = getSocket().write(from);
                    if (integer.get(getNio2WriteTimeout(), TimeUnit.MILLISECONDS).intValue() < 0) {
                        throw new EOFException(sm.getString("iob.failedwrite"));
                    }
                } while (from.hasRemaining());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else {
                    throw new IOException(e);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            } catch (TimeoutException e) {
                integer.cancel(true);
                throw new SocketTimeoutException();
            }
        }


        @Override
        protected void flushBlocking() throws IOException {
            checkError();

            // 在做阻塞刷新之前, 确保已完成任何挂起的非阻塞写入.
            try {
                if (writePending.tryAcquire(getNio2WriteTimeout(), TimeUnit.MILLISECONDS)) {
                    writePending.release();
                } else {
                    throw new SocketTimeoutException();
                }
            } catch (InterruptedException e) {
                // Ignore
            }

            super.flushBlocking();
        }

        @Override
        protected boolean flushNonBlocking() throws IOException {
            return flushNonBlocking(false);
        }

        private boolean flushNonBlocking(boolean hasPermit) throws IOException {
            checkError();
            synchronized (writeCompletionHandler) {
                if (hasPermit || writePending.tryAcquire()) {
                    socketBufferHandler.configureWriteBufferForRead();
                    if (bufferedWrites.size() > 0) {
                        // 收集主缓冲区和所有剩余的写入
                        ArrayList<ByteBuffer> arrayList = new ArrayList<>();
                        if (socketBufferHandler.getWriteBuffer().hasRemaining()) {
                            arrayList.add(socketBufferHandler.getWriteBuffer());
                        }
                        for (ByteBufferHolder buffer : bufferedWrites) {
                            buffer.flip();
                            arrayList.add(buffer.getBuf());
                        }
                        bufferedWrites.clear();
                        ByteBuffer[] array = arrayList.toArray(new ByteBuffer[arrayList.size()]);
                        Nio2Endpoint.startInline();
                        getSocket().write(array, 0, array.length, getNio2WriteTimeout(),
                                TimeUnit.MILLISECONDS, array, gatheringWriteCompletionHandler);
                        Nio2Endpoint.endInline();
                    } else if (socketBufferHandler.getWriteBuffer().hasRemaining()) {
                        // Regular write
                        Nio2Endpoint.startInline();
                        getSocket().write(socketBufferHandler.getWriteBuffer(), getNio2WriteTimeout(),
                                TimeUnit.MILLISECONDS, socketBufferHandler.getWriteBuffer(),
                                writeCompletionHandler);
                        Nio2Endpoint.endInline();
                    } else {
                        // Nothing was written
                        if (!hasPermit) {
                            writePending.release();
                        }
                    }
                }
                return hasDataToWrite();
            }
        }


        @Override
        public boolean hasDataToWrite() {
            synchronized (writeCompletionHandler) {
                return !socketBufferHandler.isWriteBufferEmpty() ||
                        bufferedWrites.size() > 0 || getError() != null;
            }
        }


        @Override
        public boolean isReadPending() {
            synchronized (readCompletionHandler) {
                return readPending.availablePermits() == 0;
            }
        }


        @Override
        public boolean awaitReadComplete(long timeout, TimeUnit unit) {
            try {
                if (readPending.tryAcquire(timeout, unit)) {
                    readPending.release();
                }
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }


        @Override
        public boolean awaitWriteComplete(long timeout, TimeUnit unit) {
            try {
                if (writePending.tryAcquire(timeout, unit)) {
                    writePending.release();
                }
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }

        /*
         * 这应该只从当前持有套接字锁的线程调用. 这可以防止正在完成和处理的挂起读取, 与触发新读取的线程之间的竞争条件.
         */
        void releaseReadPending() {
            synchronized (readCompletionHandler) {
                if (readPending.availablePermits() == 0) {
                    readPending.release();
                }
            }
        }


        @Override
        public void registerReadInterest() {
            synchronized (readCompletionHandler) {
                if (readPending.availablePermits() == 0) {
                    readInterest = true;
                } else {
                    // 如果没有待处理的读取, 开始等待数据
                    awaitBytes();
                }
            }
        }


        @Override
        public void registerWriteInterest() {
            synchronized (writeCompletionHandler) {
                if (writePending.availablePermits() == 0) {
                    writeInterest = true;
                } else {
                    // 如果没有写入待处理, 通知
                    getEndpoint().processSocket(this, SocketEvent.OPEN_WRITE, true);
                }
            }
        }


        public void awaitBytes() {
            // NO-OP已经在进行中.
            if (readPending.tryAcquire()) {
                getSocket().getBufHandler().configureReadBufferForWrite();
                Nio2Endpoint.startInline();
                getSocket().read(getSocket().getBufHandler().getReadBuffer(),
                        getNio2ReadTimeout(), TimeUnit.MILLISECONDS, this, awaitBytesHandler);
                Nio2Endpoint.endInline();
            }
        }


        @Override
        public SendfileDataBase createSendfileData(String filename, long pos, long length) {
            return new SendfileData(filename, pos, length);
        }


        @Override
        public SendfileState processSendfile(SendfileDataBase sendfileData) {
            SendfileData data = (SendfileData) sendfileData;
            setSendfileData(data);
            // 配置发送文件数据
            if (data.fchannel == null || !data.fchannel.isOpen()) {
                java.nio.file.Path path = new File(sendfileData.fileName).toPath();
                try {
                    data.fchannel = java.nio.channels.FileChannel
                            .open(path, StandardOpenOption.READ).position(sendfileData.pos);
                } catch (IOException e) {
                    return SendfileState.ERROR;
                }
            }
            getSocket().getBufHandler().configureWriteBufferForWrite();
            ByteBuffer buffer = getSocket().getBufHandler().getWriteBuffer();
            int nRead = -1;
            try {
                nRead = data.fchannel.read(buffer);
            } catch (IOException e1) {
                return SendfileState.ERROR;
            }

            if (nRead >= 0) {
                data.length -= nRead;
                getSocket().getBufHandler().configureWriteBufferForRead();
                Nio2Endpoint.startInline();
                getSocket().write(buffer, getNio2WriteTimeout(), TimeUnit.MILLISECONDS,
                        data, sendfileHandler);
                Nio2Endpoint.endInline();
                if (data.doneInline) {
                    if (data.error) {
                        return SendfileState.ERROR;
                    } else {
                        return SendfileState.DONE;
                    }
                } else {
                    return SendfileState.PENDING;
                }
            } else {
                return SendfileState.ERROR;
            }
        }


        private long getNio2ReadTimeout() {
            long readTimeout = getReadTimeout();
            if (readTimeout > 0) {
                return readTimeout;
            }
            // NIO2无法执行无限超时，因此请使用Long.MAX_VALUE
            return Long.MAX_VALUE;
        }


        private long getNio2WriteTimeout() {
            long writeTimeout = getWriteTimeout();
            if (writeTimeout > 0) {
                return writeTimeout;
            }
            // NIO2无法执行无限超时，因此请使用Long.MAX_VALUE
            return Long.MAX_VALUE;
        }


        @Override
        protected void populateRemoteAddr() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getRemoteAddress();
            } catch (IOException e) {
                // Ignore
            }
            if (socketAddress instanceof InetSocketAddress) {
                remoteAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
            }
        }


        @Override
        protected void populateRemoteHost() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getRemoteAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noRemoteHost", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                remoteHost = ((InetSocketAddress) socketAddress).getAddress().getHostName();
                if (remoteAddr == null) {
                    remoteAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
                }
            }
        }


        @Override
        protected void populateRemotePort() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getRemoteAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noRemotePort", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                remotePort = ((InetSocketAddress) socketAddress).getPort();
            }
        }


        @Override
        protected void populateLocalName() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getLocalAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noLocalName", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                localName = ((InetSocketAddress) socketAddress).getHostName();
            }
        }


        @Override
        protected void populateLocalAddr() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getLocalAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noLocalAddr", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                localAddr = ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
            }
        }


        @Override
        protected void populateLocalPort() {
            SocketAddress socketAddress = null;
            try {
                socketAddress = getSocket().getIOChannel().getLocalAddress();
            } catch (IOException e) {
                log.warn(sm.getString("endpoint.warn.noLocalPort", getSocket()), e);
            }
            if (socketAddress instanceof InetSocketAddress) {
                localPort = ((InetSocketAddress) socketAddress).getPort();
            }
        }


        /**
         * {@inheritDoc}
         * @param clientCertProvider 忽略此实现
         */
        @Override
        public SSLSupport getSslSupport(String clientCertProvider) {
            if (getSocket() instanceof SecureNio2Channel) {
                SecureNio2Channel ch = (SecureNio2Channel) getSocket();
                SSLSession session = ch.getSslEngine().getSession();
                return ((Nio2Endpoint) getEndpoint()).getSslImplementation().getSSLSupport(session);
            } else {
                return null;
            }
        }


        @Override
        public void doClientAuth(SSLSupport sslSupport) throws IOException {
            SecureNio2Channel sslChannel = (SecureNio2Channel) getSocket();
            SSLEngine engine = sslChannel.getSslEngine();
            if (!engine.getNeedClientAuth()) {
                // 需要重新协商SSL连接
                engine.setNeedClientAuth(true);
                sslChannel.rehandshake();
                ((JSSESupport) sslSupport).setSession(engine.getSession());
            }
        }


        @Override
        public void setAppReadBufHandler(ApplicationBufferHandler handler) {
            getSocket().setAppReadBufHandler(handler);
        }
    }


    public static void startInline() {
        inlineCompletion.set(Boolean.TRUE);
    }

    public static void endInline() {
        inlineCompletion.set(Boolean.FALSE);
    }

    public static boolean isInline() {
        Boolean flag = inlineCompletion.get();
        if (flag == null) {
            return false;
        } else {
            return flag.booleanValue();
        }
    }


    // ---------------------------------------------- SocketProcessor Inner Class
    /**
     * 这个类相当于Worker, 但只会在外部Executor线程池中使用.
     */
    protected class SocketProcessor extends SocketProcessorBase<Nio2Channel> {

        public SocketProcessor(SocketWrapperBase<Nio2Channel> socketWrapper, SocketEvent event) {
            super(socketWrapper, event);
        }

        @Override
        protected void doRun() {
            if (SocketEvent.OPEN_WRITE != event) {
                // OPEN_WRITE以外的任何内容都是真正的读取或错误条件，因此所有这些都释放了信号量
                ((Nio2SocketWrapper) socketWrapper).releaseReadPending();
            }
            boolean launch = false;
            try {
                int handshake = -1;

                try {
                    if (socketWrapper.getSocket().isHandshakeComplete()) {
                        // 无需TLS握手. 让处理程序处理此套接字/事件组合.
                        handshake = 0;
                    } else if (event == SocketEvent.STOP || event == SocketEvent.DISCONNECT ||
                            event == SocketEvent.ERROR) {
                        // 无法完成TLS握手. 将其视为握手失败.
                        handshake = -1;
                    } else {
                        handshake = socketWrapper.getSocket().handshake();
                        // 握手过程从/向套接字读/写. 因此，一旦握手完成，状态可以是OPEN_WRITE.
                        // 但是，当套接字打开时会发生握手，因此状态在完成后必须始终为OPEN_READ.
                        // 可以始终设置此值，因为它仅在握手完成时使用.
                        event = SocketEvent.OPEN_READ;
                    }
                } catch (IOException x) {
                    handshake = -1;
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("endpoint.err.handshake"), x);
                    }
                }
                if (handshake == 0) {
                    SocketState state = SocketState.OPEN;
                    // 处理来自此套接字的请求
                    if (event == null) {
                        state = getHandler().process(socketWrapper, SocketEvent.OPEN_READ);
                    } else {
                        state = getHandler().process(socketWrapper, event);
                    }
                    if (state == SocketState.CLOSED) {
                        // 关闭套接字和池
                        closeSocket(socketWrapper);
                        if (running && !paused) {
                            if (!nioChannels.push(socketWrapper.getSocket())) {
                                socketWrapper.getSocket().free();
                            }
                        }
                    } else if (state == SocketState.UPGRADING) {
                        launch = true;
                    }
                } else if (handshake == -1 ) {
                    closeSocket(socketWrapper);
                    if (running && !paused) {
                        if (!nioChannels.push(socketWrapper.getSocket())) {
                            socketWrapper.getSocket().free();
                        }
                    }
                }
            } catch (VirtualMachineError vme) {
                ExceptionUtils.handleThrowable(vme);
            } catch (Throwable t) {
                log.error(sm.getString("endpoint.processing.fail"), t);
                if (socketWrapper != null) {
                    closeSocket(socketWrapper);
                }
            } finally {
                if (launch) {
                    try {
                        getExecutor().execute(new SocketProcessor(socketWrapper, SocketEvent.OPEN_READ));
                    } catch (NullPointerException npe) {
                        if (running) {
                            log.error(sm.getString("endpoint.launch.fail"),
                                    npe);
                        }
                    }
                }
                socketWrapper = null;
                event = null;
                //返回缓存
                if (running && !paused) {
                    processorCache.push(this);
                }
            }
        }
    }

    // ----------------------------------------------- SendfileData Inner Class
    public static class SendfileData extends SendfileDataBase {
        private FileChannel fchannel;
        // 仅供内部使用
        private boolean doneInline = false;
        private boolean error = false;

        public SendfileData(String filename, long pos, long length) {
            super(filename, pos, length);
        }
    }
}
