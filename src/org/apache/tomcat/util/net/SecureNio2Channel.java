package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.WritePendingException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteBufferUtils;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.TLSClientHelloExtractor.ExtractorResult;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.res.StringManager;

/**
 * 为NIO2实现安全套接字通道.
 */
public class SecureNio2Channel extends Nio2Channel  {

    private static final Log log = LogFactory.getLog(SecureNio2Channel.class);
    private static final StringManager sm = StringManager.getManager(SecureNio2Channel.class);

    // 通过观察SSL引擎在各种情况下请求的内容来确定值
    private static final int DEFAULT_NET_BUFFER_SIZE = 16921;

    protected ByteBuffer netInBuffer;
    protected ByteBuffer netOutBuffer;

    protected SSLEngine sslEngine;
    protected final Nio2Endpoint endpoint;

    protected boolean sniComplete = false;

    private volatile boolean handshakeComplete;
    private volatile HandshakeStatus handshakeStatus; //gets set by handshake

    private volatile boolean unwrapBeforeRead = false;

    protected boolean closed;
    protected boolean closing;

    private final CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> handshakeReadCompletionHandler;
    private final CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> handshakeWriteCompletionHandler;

    public SecureNio2Channel(SocketBufferHandler bufHandler, Nio2Endpoint endpoint) {
        super(bufHandler);
        this.endpoint = endpoint;
        if (endpoint.getSocketProperties().getDirectSslBuffer()) {
            netInBuffer = ByteBuffer.allocateDirect(DEFAULT_NET_BUFFER_SIZE);
            netOutBuffer = ByteBuffer.allocateDirect(DEFAULT_NET_BUFFER_SIZE);
        } else {
            netInBuffer = ByteBuffer.allocate(DEFAULT_NET_BUFFER_SIZE);
            netOutBuffer = ByteBuffer.allocate(DEFAULT_NET_BUFFER_SIZE);
        }
        handshakeReadCompletionHandler = new HandshakeReadCompletionHandler();
        handshakeWriteCompletionHandler = new HandshakeWriteCompletionHandler();
    }


    private class HandshakeReadCompletionHandler
            implements CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> {
        @Override
        public void completed(Integer result, SocketWrapperBase<Nio2Channel> attachment) {
            if (result.intValue() < 0) {
                failed(new EOFException(), attachment);
            } else {
                endpoint.processSocket(attachment, SocketEvent.OPEN_READ, false);
            }
        }
        @Override
        public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
            endpoint.processSocket(attachment, SocketEvent.ERROR, false);
        }
    }


    private class HandshakeWriteCompletionHandler
            implements CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> {
        @Override
        public void completed(Integer result, SocketWrapperBase<Nio2Channel> attachment) {
            if (result.intValue() < 0) {
                failed(new EOFException(), attachment);
            } else {
                endpoint.processSocket(attachment, SocketEvent.OPEN_WRITE, false);
            }
        }
        @Override
        public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
            endpoint.processSocket(attachment, SocketEvent.ERROR, false);
        }
    }


    @Override
    public void reset(AsynchronousSocketChannel channel, SocketWrapperBase<Nio2Channel> socket)
            throws IOException {
        super.reset(channel, socket);
        sslEngine = null;
        sniComplete = false;
        handshakeComplete = false;
        closed = false;
        closing = false;
        netInBuffer.clear();
    }

    @Override
    public void free() {
        super.free();
        if (endpoint.getSocketProperties().getDirectSslBuffer()) {
            ByteBufferUtils.cleanDirectBuffer(netInBuffer);
            ByteBufferUtils.cleanDirectBuffer(netOutBuffer);
        }
    }

    private class FutureFlush implements Future<Boolean> {
        private Future<Integer> integer;
        protected FutureFlush() {
            integer = sc.write(netOutBuffer);
        }
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return integer.cancel(mayInterruptIfRunning);
        }
        @Override
        public boolean isCancelled() {
            return integer.isCancelled();
        }
        @Override
        public boolean isDone() {
            return integer.isDone();
        }
        @Override
        public Boolean get() throws InterruptedException,
                ExecutionException {
            return Boolean.valueOf(integer.get().intValue() >= 0);
        }
        @Override
        public Boolean get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            return Boolean.valueOf(integer.get(timeout, unit).intValue() >= 0);
        }
    }

    /**
     * Flush the channel.
     *
     * @return <code>true</code> 如果网络缓冲区已被刷新并且为空; 否则<code>false</code>
     */
    @Override
    public Future<Boolean> flush() {
        return new FutureFlush();
    }

    /**
     * 执行SSL握手, 非阻塞, 但在同一个线程上执行NEED_TASK.
     * 于是, 永远不应该使用Acceptor线程调用这个方法, 因为会减慢系统.
     * <p>
     * 如果握手完成，则此操作的返回值为0; 如果未完成，则返回正值. 如果有正值回来, 已经使用适当的CompletionHandler调用了适当的读/写.
     *
     * @return 如果握手完成，则为0; 如果套接字需要关闭则为负数; 如果握手不完整则为正数
     *
     * @throws IOException 如果在握手期间发生错误
     */
    @Override
    public int handshake() throws IOException {
        return handshakeInternal(true);
    }

    protected int handshakeInternal(boolean async) throws IOException {
        if (handshakeComplete) {
            return 0; //已经完成了初步握手
        }

        if (!sniComplete) {
            int sniResult = processSNI();
            if (sniResult == 0) {
                sniComplete = true;
            } else {
                return sniResult;
            }
        }

        SSLEngineResult handshake = null;

        while (!handshakeComplete) {
            switch (handshakeStatus) {
                case NOT_HANDSHAKING: {
                    //should never happen
                    throw new IOException(sm.getString("channel.nio.ssl.notHandshaking"));
                }
                case FINISHED: {
                    if (endpoint.hasNegotiableProtocols()) {
                        if (sslEngine instanceof SSLUtil.ProtocolInfo) {
                            socket.setNegotiatedProtocol(
                                    ((SSLUtil.ProtocolInfo) sslEngine).getNegotiatedProtocol());
                        } else if (JreCompat.isJre9Available()) {
                            socket.setNegotiatedProtocol(
                                    JreCompat.getInstance().getApplicationProtocol(sslEngine));
                        }
                    }
                    //如果交付了最后一个包，就完成了
                    handshakeComplete = !netOutBuffer.hasRemaining();
                    //如果完成，则返回0; 否则仍然有数据要写
                    if (handshakeComplete) {
                        return 0;
                    } else {
                        if (async) {
                            sc.write(netOutBuffer, socket, handshakeWriteCompletionHandler);
                        } else {
                            try {
                                sc.write(netOutBuffer).get(endpoint.getConnectionTimeout(), TimeUnit.MILLISECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                throw new IOException(sm.getString("channel.nio.ssl.handshakeError"));
                            }
                        }
                        return 1;
                    }
                }
                case NEED_WRAP: {
                    // 执行包装功能
                    try {
                        handshake = handshakeWrap();
                    } catch (SSLException e) {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("channel.nio.ssl.wrapException"), e);
                        }
                        handshake = handshakeWrap();
                    }
                    if (handshake.getStatus() == Status.OK) {
                        if (handshakeStatus == HandshakeStatus.NEED_TASK)
                            handshakeStatus = tasks();
                    } else if (handshake.getStatus() == Status.CLOSED) {
                        return -1;
                    } else {
                        // 包装应始终使用我们的缓冲区
                        throw new IOException(sm.getString("channel.nio.ssl.unexpectedStatusDuringWrap", handshake.getStatus()));
                    }
                    if (handshakeStatus != HandshakeStatus.NEED_UNWRAP || netOutBuffer.remaining() > 0) {
                        // 如果有NEED_UNWRAP，应该实际返回OP_READ
                        if (async) {
                            sc.write(netOutBuffer, socket, handshakeWriteCompletionHandler);
                        } else {
                            try {
                                sc.write(netOutBuffer).get(endpoint.getConnectionTimeout(), TimeUnit.MILLISECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                throw new IOException(sm.getString("channel.nio.ssl.handshakeError"));
                            }
                        }
                        return 1;
                    }
                    // 在同一个调用上落到NEED_UNWRAP; 如果需要数据，将导致BUFFER_UNDERFLOW
                }
                //$FALL-THROUGH$
                case NEED_UNWRAP: {
                    // 执行解包装功能
                    handshake = handshakeUnwrap();
                    if (handshake.getStatus() == Status.OK) {
                        if (handshakeStatus == HandshakeStatus.NEED_TASK)
                            handshakeStatus = tasks();
                    } else if (handshake.getStatus() == Status.BUFFER_UNDERFLOW) {
                        // 读取更多数据，重新注册OP_READ
                        if (async) {
                            sc.read(netInBuffer, socket, handshakeReadCompletionHandler);
                        } else {
                            try {
                                int read = sc.read(netInBuffer).get(endpoint.getConnectionTimeout(),
                                        TimeUnit.MILLISECONDS).intValue();
                                if (read == -1) {
                                    throw new EOFException();
                                }
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                throw new IOException(sm.getString("channel.nio.ssl.handshakeError"));
                            }
                        }
                        return 1;
                    } else {
                        throw new IOException(sm.getString("channel.nio.ssl.unexpectedStatusDuringUnwrap", handshakeStatus));
                    }
                    break;
                }
                case NEED_TASK: {
                    handshakeStatus = tasks();
                    break;
                }
                default: throw new IllegalStateException(sm.getString("channel.nio.ssl.invalidStatus", handshakeStatus));
            }
        }
        //如果完成则返回0, 否则递归处理任务
        return handshakeComplete ? 0 : handshakeInternal(async);
    }


    /*
     * 查看初始网络字节以确定是否存在SNI扩展，如果存在，则确定请求了哪个主机名.
     * 基于提供的主机名, 为此连接配置SSLEngine.
     */
    private int processSNI() throws IOException {
        // 如果没有要处理的数据, 立即触发读取. 这是典型情况的优化, 因此不创建SNIExtractor, 只发现没有要处理的数据
        if (netInBuffer.position() == 0) {
            sc.read(netInBuffer, socket, handshakeReadCompletionHandler);
            return 1;
        }

        TLSClientHelloExtractor extractor = new TLSClientHelloExtractor(netInBuffer);

        if (extractor.getResult() == ExtractorResult.UNDERFLOW &&
                netInBuffer.capacity() < endpoint.getSniParseLimit()) {
            // 提取器需要处理更多数据, 但netInBuffer已满, 因此扩展缓冲区, 并读取更多数据.
            int newLimit = Math.min(netInBuffer.capacity() * 2, endpoint.getSniParseLimit());
            log.info(sm.getString("channel.nio.ssl.expandNetInBuffer",
                    Integer.toString(newLimit)));

            netInBuffer = ByteBufferUtils.expand(netInBuffer, newLimit);
            sc.read(netInBuffer, socket, handshakeReadCompletionHandler);
            return 1;
        }

        String hostName = null;
        List<Cipher> clientRequestedCiphers = null;
        List<String> clientRequestedApplicationProtocols = null;
        switch (extractor.getResult()) {
        case COMPLETE:
            hostName = extractor.getSNIValue();
            clientRequestedApplicationProtocols =
                    extractor.getClientRequestedApplicationProtocols();
            //$FALL-THROUGH$ to set the client requested ciphers
        case NOT_PRESENT:
            clientRequestedCiphers = extractor.getClientRequestedCiphers();
            break;
        case NEED_READ:
            sc.read(netInBuffer, socket, handshakeReadCompletionHandler);
            return 1;
        case UNDERFLOW:
            // 无法缓冲足够的数据来读取SNI扩展数据
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("channel.nio.ssl.sniDefault"));
            }
            hostName = endpoint.getDefaultSSLHostConfigName();
            clientRequestedCiphers = Collections.emptyList();
            break;
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("channel.nio.ssl.sniHostName", hostName));
        }

        sslEngine = endpoint.createSSLEngine(hostName, clientRequestedCiphers,
                clientRequestedApplicationProtocols);

        // 确保应用程序缓冲区（必须先前创建）足够大.
        getBufHandler().expand(sslEngine.getSession().getApplicationBufferSize());
        if (netOutBuffer.capacity() < sslEngine.getSession().getApplicationBufferSize()) {
            // 现在的信息，因为可能需要增加DEFAULT_NET_BUFFER_SIZE
            log.info(sm.getString("channel.nio.ssl.expandNetOutBuffer",
                    Integer.toString(sslEngine.getSession().getApplicationBufferSize())));
        }
        netInBuffer = ByteBufferUtils.expand(netInBuffer, sslEngine.getSession().getPacketBufferSize());
        netOutBuffer = ByteBufferUtils.expand(netOutBuffer, sslEngine.getSession().getPacketBufferSize());

        // 将限制和位置设置为预期值
        netOutBuffer.position(0);
        netOutBuffer.limit(0);

        // 发起握手
        sslEngine.beginHandshake();
        handshakeStatus = sslEngine.getHandshakeStatus();

        return 0;
    }


    /**
     * 强制为此密钥执行阻塞握手.
     * 这要求在此调用发生之前清空网络和应用程序缓冲区，否则将抛出IOException.
     * 
     * @throws IOException - 如果发生IO异常, 或应用程序或网络缓冲区包含数据
     * @throws java.net.SocketTimeoutException - 如果套接字操作超时
     */
    public void rehandshake() throws IOException {
        // 验证网络缓冲区是否为空
        if (netInBuffer.position() > 0 && netInBuffer.position() < netInBuffer.limit()) throw new IOException(sm.getString("channel.nio.ssl.netInputNotEmpty"));
        if (netOutBuffer.position() > 0 && netOutBuffer.position() < netOutBuffer.limit()) throw new IOException(sm.getString("channel.nio.ssl.netOutputNotEmpty"));
        if (!getBufHandler().isReadBufferEmpty()) throw new IOException(sm.getString("channel.nio.ssl.appInputNotEmpty"));
        if (!getBufHandler().isWriteBufferEmpty()) throw new IOException(sm.getString("channel.nio.ssl.appOutputNotEmpty"));

        netOutBuffer.position(0);
        netOutBuffer.limit(0);
        netInBuffer.position(0);
        netInBuffer.limit(0);
        getBufHandler().reset();

        handshakeComplete = false;
        //initiate handshake
        sslEngine.beginHandshake();
        handshakeStatus = sslEngine.getHandshakeStatus();

        boolean handshaking = true;
        try {
            while (handshaking) {
                int hsStatus = handshakeInternal(false);
                switch (hsStatus) {
                    case -1 : throw new EOFException(sm.getString("channel.nio.ssl.eofDuringHandshake"));
                    case  0 : handshaking = false; break;
                    default : // 发生了一些阻塞IO, 迭代
                }
            }
        } catch (IOException x) {
            closeSilently();
            throw x;
        } catch (Exception cx) {
            closeSilently();
            IOException x = new IOException(cx);
            throw x;
        }
    }


    /**
     * 执行同一线程上所需的所有任务.
     * 
     * @return 状态
     */
    protected SSLEngineResult.HandshakeStatus tasks() {
        Runnable r = null;
        while ( (r = sslEngine.getDelegatedTask()) != null) {
            r.run();
        }
        return sslEngine.getHandshakeStatus();
    }

    /**
     * 执行WRAP功能
     * 
     * @return the result
     * @throws IOException 发生IO错误
     */
    protected SSLEngineResult handshakeWrap() throws IOException {
        // 永远不应该使用包含数据的网络缓冲区来调用它，以便我们在此处清除它.
        netOutBuffer.clear();
        // 执行包装
        getBufHandler().configureWriteBufferForRead();
        SSLEngineResult result = sslEngine.wrap(getBufHandler().getWriteBuffer(), netOutBuffer);
        // 准备要写入的结果
        netOutBuffer.flip();
        // 设置状态
        handshakeStatus = result.getHandshakeStatus();
        return result;
    }

    /**
     * 执行握手解包
     * 
     * @return the result
     * @throws IOException 发生IO错误
     */
    protected SSLEngineResult handshakeUnwrap() throws IOException {
        if (netInBuffer.position() == netInBuffer.limit()) {
            // 如果已将数据清空，请清除缓冲区
            netInBuffer.clear();
        }
        SSLEngineResult result;
        boolean cont = false;
        //循环，而我们可以执行纯SSLEngine数据
        do {
            // 使用传入数据准备缓冲区
            netInBuffer.flip();
            // 调用解包装
            getBufHandler().configureReadBufferForWrite();
            result = sslEngine.unwrap(netInBuffer, getBufHandler().getReadBuffer());
            // 压缩缓冲区，这是一个可选的方法，想知道如果我们不这样做会发生什么
            netInBuffer.compact();
            // 读入状态
            handshakeStatus = result.getHandshakeStatus();
            if (result.getStatus() == SSLEngineResult.Status.OK &&
                 result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                // 如果需要，执行任务
                handshakeStatus = tasks();
            }
            // 执行另一个解包装?
            cont = result.getStatus() == SSLEngineResult.Status.OK &&
                   handshakeStatus == HandshakeStatus.NEED_UNWRAP;
        } while (cont);
        return result;
    }

    /**
     * 发送SSL关闭消息, 这里不会关闭连接.<br>
     * 要关闭连接, 可以这样
     * <pre><code>
     *   close();
     *   while (isOpen() &amp;&amp; !myTimeoutFunction()) Thread.sleep(25);
     *   if ( isOpen() ) close(true); //forces a close if you timed out
     * </code></pre>
     * 
     * @throws IOException 如果发生I/O错误
     * @throws IOException 如果传出网络缓冲区上有数据，无法刷新它
     */
    @Override
    public void close() throws IOException {
        if (closing) return;
        closing = true;
        sslEngine.closeOutbound();

        try {
            if (!flush().get(endpoint.getConnectionTimeout(), TimeUnit.MILLISECONDS).booleanValue()) {
                throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"), e);
        } catch (WritePendingException e) {
            throw new IOException(sm.getString("channel.nio.ssl.pendingWriteDuringClose"), e);
        }
        // 准备关闭消息的缓冲区
        netOutBuffer.clear();
        // 执行关闭, 因为调用 sslEngine.closeOutbound
        SSLEngineResult handshake = sslEngine.wrap(getEmptyBuf(), netOutBuffer);
        // 应该处于封闭状态
        if (handshake.getStatus() != SSLEngineResult.Status.CLOSED) {
            throw new IOException(sm.getString("channel.nio.ssl.invalidCloseState"));
        }
        // 准备缓冲区进行写入
        netOutBuffer.flip();
        // 如果有要写的数据
        try {
            if (!flush().get(endpoint.getConnectionTimeout(), TimeUnit.MILLISECONDS).booleanValue()) {
                throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"), e);
        } catch (WritePendingException e) {
            throw new IOException(sm.getString("channel.nio.ssl.pendingWriteDuringClose"), e);
        }

        // is the channel closed?
        closed = (!netOutBuffer.hasRemaining() && (handshake.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
    }


    @Override
    public void close(boolean force) throws IOException {
        try {
            close();
        } finally {
            if (force || closed) {
                closed = true;
                sc.close();
            }
        }
    }


    private void closeSilently() {
        try {
            close(true);
        } catch (IOException ioe) {
            // 这是预料之中的 - 吞下异常就是这种方法存在的原因. 如果有人感兴趣，请记录调试.
            log.debug(sm.getString("channel.nio.ssl.closeSilentError"), ioe);
        }
    }


    private class FutureRead implements Future<Integer> {
        private ByteBuffer dst;
        private Future<Integer> integer;
        private FutureRead(ByteBuffer dst) {
            this.dst = dst;
            if (unwrapBeforeRead || netInBuffer.position() > 0) {
                this.integer = null;
            } else {
                this.integer = sc.read(netInBuffer);
            }
        }
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return (integer == null) ? false : integer.cancel(mayInterruptIfRunning);
        }
        @Override
        public boolean isCancelled() {
            return (integer == null) ? false : integer.isCancelled();
        }
        @Override
        public boolean isDone() {
            return (integer == null) ? true : integer.isDone();
        }
        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            try {
                return (integer == null) ? unwrap(netInBuffer.position(), -1, TimeUnit.MILLISECONDS) : unwrap(integer.get().intValue(), -1, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // Cannot happen: no timeout
                throw new ExecutionException(e);
            }
        }
        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            return (integer == null) ? unwrap(netInBuffer.position(), timeout, unit) : unwrap(integer.get(timeout, unit).intValue(), timeout, unit);
        }
        private Integer unwrap(int nRead, long timeout, TimeUnit unit) throws ExecutionException, TimeoutException, InterruptedException {
            //are we in the middle of closing or closed?
            if (closing || closed)
                return Integer.valueOf(-1);
            //did we reach EOF? if so send EOF up one layer.
            if (nRead < 0)
                return Integer.valueOf(-1);
            //the data read
            int read = 0;
            //the SSL engine result
            SSLEngineResult unwrap;
            do {
                //prepare the buffer
                netInBuffer.flip();
                //unwrap the data
                try {
                    unwrap = sslEngine.unwrap(netInBuffer, dst);
                } catch (SSLException e) {
                    throw new ExecutionException(e);
                }
                // 压缩缓冲区
                netInBuffer.compact();
                if (unwrap.getStatus() == Status.OK || unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                    //确实收到了一些数据, 将它添加到总数中
                    read += unwrap.bytesProduced();
                    // 执行任务
                    if (unwrap.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        tasks();
                    }
                    // 如果需要更多的网络数据, 然后保释出来.
                    if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                        if (read == 0) {
                            integer = sc.read(netInBuffer);
                            if (timeout > 0) {
                                return unwrap(integer.get(timeout, unit).intValue(), timeout, unit);
                            } else {
                                return unwrap(integer.get().intValue(), -1, TimeUnit.MILLISECONDS);
                            }
                        } else {
                            break;
                        }
                    }
                } else if (unwrap.getStatus() == Status.BUFFER_OVERFLOW) {
                    if (read > 0) {
                        // 如果读取数据，可能会发生缓冲区溢出. 返回，以便在尝试另一次读取之前清空目标缓冲区
                        break;
                    } else {
                        // 自创建缓冲区以来，SSL会话增加了所需的缓冲区大小.
                        if (dst == getBufHandler().getReadBuffer()) {
                            // 这是此代码的正常情况
                            getBufHandler()
                                    .expand(sslEngine.getSession().getApplicationBufferSize());
                            dst = getBufHandler().getReadBuffer();
                        } else if (dst == getAppReadBufHandler().getByteBuffer()) {
                            getAppReadBufHandler()
                                    .expand(sslEngine.getSession().getApplicationBufferSize());
                            dst = getAppReadBufHandler().getByteBuffer();
                        } else {
                            // 无法扩展缓冲区，因为无法向调用方发出已更换缓冲区的信号.
                            throw new ExecutionException(new IOException(sm.getString("channel.nio.ssl.unwrapFailResize", unwrap.getStatus())));
                        }
                    }
                } else {
                    // Something else went wrong
                    throw new ExecutionException(new IOException(sm.getString("channel.nio.ssl.unwrapFail", unwrap.getStatus())));
                }
            } while (netInBuffer.position() != 0); // 只要输入缓冲区有东西，就继续解包
            if (!dst.hasRemaining()) {
                unwrapBeforeRead = true;
            } else {
                unwrapBeforeRead = false;
            }
            return Integer.valueOf(read);
        }
    }

    /**
     * 从该通道读取一个字节序列到给定的缓冲区.
     *
     * @param dst 要传输字节的缓冲区
     * 
     * @return 读取的字节数, 可能为零, 或<tt>-1</tt> 如果频道已到达流末尾
     * @throws IllegalStateException 如果握手没有完成
     */
    @Override
    public Future<Integer> read(ByteBuffer dst) {
        if (!handshakeComplete) {
            throw new IllegalStateException(sm.getString("channel.nio.ssl.incompleteHandshake"));
        }
        return new FutureRead(dst);
    }

    private class FutureWrite implements Future<Integer> {
        private final ByteBuffer src;
        private Future<Integer> integer = null;
        private int written = 0;
        private Throwable t = null;
        private FutureWrite(ByteBuffer src) {
            this.src = src;
            //are we closing or closed?
            if (closing || closed) {
                t = new IOException(sm.getString("channel.nio.ssl.closing"));
            } else {
                wrap();
            }
        }
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return integer.cancel(mayInterruptIfRunning);
        }
        @Override
        public boolean isCancelled() {
            return integer.isCancelled();
        }
        @Override
        public boolean isDone() {
            return integer.isDone();
        }
        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            if (t != null) {
                throw new ExecutionException(t);
            }
            if (integer.get().intValue() > 0 && written == 0) {
                wrap();
                return get();
            } else if (netOutBuffer.hasRemaining()) {
                integer = sc.write(netOutBuffer);
                return get();
            } else {
                return Integer.valueOf(written);
            }
        }
        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            if (t != null) {
                throw new ExecutionException(t);
            }
            if (integer.get(timeout, unit).intValue() > 0 && written == 0) {
                wrap();
                return get(timeout, unit);
            } else if (netOutBuffer.hasRemaining()) {
                integer = sc.write(netOutBuffer);
                return get(timeout, unit);
            } else {
                return Integer.valueOf(written);
            }
        }
        protected void wrap() {
            try {
                if (!netOutBuffer.hasRemaining()) {
                    netOutBuffer.clear();
                    SSLEngineResult result = sslEngine.wrap(src, netOutBuffer);
                    written = result.bytesConsumed();
                    netOutBuffer.flip();
                    if (result.getStatus() == Status.OK) {
                        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                            tasks();
                    } else {
                        t = new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
                    }
                }
                integer = sc.write(netOutBuffer);
            } catch (SSLException e) {
                t = e;
            }
        }
    }

    /**
     * 从给定缓冲区向该通道写入一个字节序列.
     *
     * @param src 要从中检索字节的缓冲区
     * @return 写入的字节数, 可能为零
     */
    @Override
    public Future<Integer> write(ByteBuffer src) {
        return new FutureWrite(src);
    }

    @Override
    public <A> void read(final ByteBuffer dst,
            final long timeout, final TimeUnit unit, final A attachment,
            final CompletionHandler<Integer, ? super A> handler) {
        // Check state
        if (closing || closed) {
            handler.completed(Integer.valueOf(-1), attachment);
            return;
        }
        if (!handshakeComplete) {
            throw new IllegalStateException(sm.getString("channel.nio.ssl.incompleteHandshake"));
        }
        CompletionHandler<Integer, A> readCompletionHandler = new CompletionHandler<Integer, A>() {
            @Override
            public void completed(Integer nBytes, A attach) {
                if (nBytes.intValue() < 0) {
                    failed(new EOFException(), attach);
                } else {
                    try {
                        ByteBuffer dst2 = dst;
                        //the data read
                        int read = 0;
                        //the SSL engine result
                        SSLEngineResult unwrap;
                        do {
                            // 准备缓冲区
                            netInBuffer.flip();
                            // 打开数据
                            unwrap = sslEngine.unwrap(netInBuffer, dst2);
                            // 压缩缓冲区
                            netInBuffer.compact();
                            if (unwrap.getStatus() == Status.OK || unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                                // 确实收到了一些数据, 将它添加到总数中
                                read += unwrap.bytesProduced();
                                // 执行任务
                                if (unwrap.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                                    tasks();
                                // 如果需要更多的网络数据, 然后保释出来.
                                if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                                    if (read == 0) {
                                        sc.read(netInBuffer, timeout, unit, attachment, this);
                                        return;
                                    } else {
                                        break;
                                    }
                                }
                            } else if (unwrap.getStatus() == Status.BUFFER_OVERFLOW) {
                                if (read > 0) {
                                    // 如果读取数据，可能会发生缓冲区溢出. 返回，以便在尝试另一次读取之前清空目标缓冲区
                                    break;
                                } else {
                                    // 自创建缓冲区以来，SSL会话增加了所需的缓冲区大小.
                                    if (dst2 == getBufHandler().getReadBuffer()) {
                                        // 这是此代码的正常情况
                                        getBufHandler().expand(
                                                sslEngine.getSession().getApplicationBufferSize());
                                        dst2 = getBufHandler().getReadBuffer();
                                    } else {
                                        // 无法扩展缓冲区，因为无法向调用方发出已更换缓冲区的信号.
                                        throw new IOException(
                                                sm.getString("channel.nio.ssl.unwrapFailResize", unwrap.getStatus()));
                                    }
                                }
                            } else {
                                // Something else went wrong
                                throw new IOException(sm.getString("channel.nio.ssl.unwrapFail", unwrap.getStatus()));
                            }
                        // 只要输入缓冲区有东西，就继续解封装
                        } while (netInBuffer.position() != 0);
                        if (!dst2.hasRemaining()) {
                            unwrapBeforeRead = true;
                        } else {
                            unwrapBeforeRead = false;
                        }
                        // If everything is OK, so complete
                        handler.completed(Integer.valueOf(read), attach);
                    } catch (Exception e) {
                        failed(e, attach);
                    }
                }
            }
            @Override
            public void failed(Throwable exc, A attach) {
                handler.failed(exc, attach);
            }
        };
        if (unwrapBeforeRead || netInBuffer.position() > 0) {
            readCompletionHandler.completed(Integer.valueOf(netInBuffer.position()), attachment);
        } else {
            sc.read(netInBuffer, timeout, unit, attachment, readCompletionHandler);
        }
    }

    @Override
    public <A> void read(final ByteBuffer[] dsts, final int offset, final int length,
            final long timeout, final TimeUnit unit, final A attachment,
            final CompletionHandler<Long, ? super A> handler) {
        if (offset < 0 || dsts == null || (offset + length) > dsts.length) {
            throw new IllegalArgumentException();
        }
        if (closing || closed) {
            handler.completed(Long.valueOf(-1), attachment);
            return;
        }
        if (!handshakeComplete) {
            throw new IllegalStateException(sm.getString("channel.nio.ssl.incompleteHandshake"));
        }
        CompletionHandler<Integer, A> readCompletionHandler = new CompletionHandler<Integer, A>() {
            @Override
            public void completed(Integer nBytes, A attach) {
                if (nBytes.intValue() < 0) {
                    failed(new EOFException(), attach);
                } else {
                    try {
                        //the data read
                        long read = 0;
                        //the SSL engine result
                        SSLEngineResult unwrap;
                        do {
                            // 准备缓冲区
                            netInBuffer.flip();
                            // 打开数据
                            unwrap = sslEngine.unwrap(netInBuffer, dsts, offset, length);
                            // 压缩缓冲区
                            netInBuffer.compact();
                            if (unwrap.getStatus() == Status.OK || unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                                // 确实收到了一些数据, 将它添加到总数中
                                read += unwrap.bytesProduced();
                                // 执行任务
                                if (unwrap.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                                    tasks();
                                // 如果需要更多的网络数据, 然后保释出来.
                                if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                                    if (read == 0) {
                                        sc.read(netInBuffer, timeout, unit, attachment, this);
                                        return;
                                    } else {
                                        break;
                                    }
                                }
                            } else if (unwrap.getStatus() == Status.BUFFER_OVERFLOW && read > 0) {
                                // 缓冲区溢出可能发生, 如果有读取数据, 然后在再次读取之前清空dst缓冲区
                                break;
                            } else {
                                // 这里我们应该捕获BUFFER_OVERFLOW, 并调用缓冲区的扩展, 抛出异常, 因为在构造函数中初始化了缓冲区
                                throw new IOException(sm.getString("channel.nio.ssl.unwrapFail", unwrap.getStatus()));
                            }
                        } while (netInBuffer.position() != 0); // 只要输入缓冲区有东西，就继续解包
                        int capacity = 0;
                        final int endOffset = offset + length;
                        for (int i = offset; i < endOffset; i++) {
                            capacity += dsts[i].remaining();
                        }
                        if (capacity == 0) {
                            unwrapBeforeRead = true;
                        } else {
                            unwrapBeforeRead = false;
                        }
                        // If everything is OK, so complete
                        handler.completed(Long.valueOf(read), attach);
                    } catch (Exception e) {
                        failed(e, attach);
                    }
                }
            }
            @Override
            public void failed(Throwable exc, A attach) {
                handler.failed(exc, attach);
            }
        };
        if (unwrapBeforeRead || netInBuffer.position() > 0) {
            readCompletionHandler.completed(Integer.valueOf(netInBuffer.position()), attachment);
        } else {
            sc.read(netInBuffer, timeout, unit, attachment, readCompletionHandler);
        }
    }

    @Override
    public <A> void write(final ByteBuffer src, final long timeout, final TimeUnit unit,
            final A attachment, final CompletionHandler<Integer, ? super A> handler) {
        // Check state
        if (closing || closed) {
            handler.failed(new IOException(sm.getString("channel.nio.ssl.closing")), attachment);
            return;
        }
        try {
            // 准备输出缓冲区
            netOutBuffer.clear();
            // 将源数据包装到内部缓冲区中
            SSLEngineResult result = sslEngine.wrap(src, netOutBuffer);
            final int written = result.bytesConsumed();
            netOutBuffer.flip();
            if (result.getStatus() == Status.OK) {
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    tasks();
                }
                // 将数据写入通道
                sc.write(netOutBuffer, timeout, unit, attachment,
                        new CompletionHandler<Integer, A>() {
                    @Override
                    public void completed(Integer nBytes, A attach) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(), attach);
                        } else if (netOutBuffer.hasRemaining()) {
                            sc.write(netOutBuffer, timeout, unit, attachment, this);
                        } else if (written == 0) {
                            // 特例, 重新开始以避免代码重复
                            write(src, timeout, unit, attachment, handler);
                        } else {
                            // 使用消耗的字节数, 调用处理程序已完成的方法
                            handler.completed(Integer.valueOf(written), attach);
                        }
                    }
                    @Override
                    public void failed(Throwable exc, A attach) {
                        handler.failed(exc, attach);
                    }
                });
            } else {
                throw new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
            }
        } catch (Exception e) {
            handler.failed(e, attachment);
        }
    }

    @Override
    public <A> void write(final ByteBuffer[] srcs, final int offset, final int length,
            final long timeout, final TimeUnit unit, final A attachment,
            final CompletionHandler<Long, ? super A> handler) {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length)) {
            throw new IndexOutOfBoundsException();
        }
        // Check state
        if (closing || closed) {
            handler.failed(new IOException(sm.getString("channel.nio.ssl.closing")), attachment);
            return;
        }
        try {
             // 准备输出缓冲区
            netOutBuffer.clear();
            // 将源数据包装到内部缓冲区中
            SSLEngineResult result = sslEngine.wrap(srcs, offset, length, netOutBuffer);
            final int written = result.bytesConsumed();
            netOutBuffer.flip();
            if (result.getStatus() == Status.OK) {
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    tasks();
                }
                // 将数据写入通道
                sc.write(netOutBuffer, timeout, unit, attachment, new CompletionHandler<Integer, A>() {
                    @Override
                    public void completed(Integer nBytes, A attach) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(), attach);
                        } else if (netOutBuffer.hasRemaining()) {
                            sc.write(netOutBuffer, timeout, unit, attachment, this);
                        } else if (written == 0) {
                            // 特例, 重新开始以避免代码重复
                            write(srcs, offset, length, timeout, unit, attachment, handler);
                        } else {
                            // 使用消耗的字节数, 调用处理程序已完成的方法
                            handler.completed(Long.valueOf(written), attach);
                        }
                    }
                    @Override
                    public void failed(Throwable exc, A attach) {
                        handler.failed(exc, attach);
                    }
                });
            } else {
                throw new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
            }
        } catch (Exception e) {
            handler.failed(e, attachment);
        }
   }

    @Override
    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    @Override
    public boolean isClosing() {
        return closing;
    }

    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    public ByteBuffer getEmptyBuf() {
        return emptyBuf;
    }
}