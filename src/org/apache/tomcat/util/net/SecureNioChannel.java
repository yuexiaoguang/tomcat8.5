package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.List;

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
 * 实现安全套接字通道
 */
public class SecureNioChannel extends NioChannel  {

    private static final Log log = LogFactory.getLog(SecureNioChannel.class);
    private static final StringManager sm = StringManager.getManager(SecureNioChannel.class);

    // 通过观察SSL引擎在各种情况下请求的内容来确定值
    private static final int DEFAULT_NET_BUFFER_SIZE = 16921;

    protected ByteBuffer netInBuffer;
    protected ByteBuffer netOutBuffer;

    protected SSLEngine sslEngine;

    protected boolean sniComplete = false;

    protected boolean handshakeComplete = false;
    protected HandshakeStatus handshakeStatus; //gets set by handshake

    protected boolean closed = false;
    protected boolean closing = false;

    protected NioSelectorPool pool;
    private final NioEndpoint endpoint;

    public SecureNioChannel(SocketChannel channel, SocketBufferHandler bufHandler,
            NioSelectorPool pool, NioEndpoint endpoint) {
        super(channel, bufHandler);

        // 创建网络缓冲区 (这些包含加密数据).
        if (endpoint.getSocketProperties().getDirectSslBuffer()) {
            netInBuffer = ByteBuffer.allocateDirect(DEFAULT_NET_BUFFER_SIZE);
            netOutBuffer = ByteBuffer.allocateDirect(DEFAULT_NET_BUFFER_SIZE);
        } else {
            netInBuffer = ByteBuffer.allocate(DEFAULT_NET_BUFFER_SIZE);
            netOutBuffer = ByteBuffer.allocate(DEFAULT_NET_BUFFER_SIZE);
        }

        // 用于阻塞操作的选择器池
        this.pool = pool;
        this.endpoint = endpoint;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
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

//===========================================================================================
//                  NIO SSL METHODS
//===========================================================================================

    /**
     * Flush the channel.
     *
     * @param block     是否应该使用阻塞写入?
     * @param s         用于阻塞的选择器; 如果为null，则将启动忙写入
     * @param timeout   此写操作的超时时间, 以毫秒为单位, -1表示没有超时
     * 
     * @return <code>true</code> 如果网络缓冲区已被刷新并且为空, 否则<code>false</code>
     * @throws IOException If an I/O error occurs during the operation
     */
    @Override
    public boolean flush(boolean block, Selector s, long timeout) throws IOException {
        if (!block) {
            flush(netOutBuffer);
        } else {
            pool.write(netOutBuffer, this, s, timeout, block);
        }
        return !netOutBuffer.hasRemaining();
    }

    /**
     * 将缓冲区刷新到网络, 非阻塞
     * 
     * @param buf ByteBuffer
     * 
     * @return boolean true 如果缓冲区已清空, 否则false
     * @throws IOException 写入数据时发生IO错误
     */
    protected boolean flush(ByteBuffer buf) throws IOException {
        int remaining = buf.remaining();
        if ( remaining > 0 ) {
            int written = sc.write(buf);
            return written >= remaining;
        }else {
            return true;
        }
    }

    /**
     * 执行SSL握手, 非阻塞, 但在同一个线程上执行NEED_TASK.
     * 于是, 你永远不应该使用你的Acceptor线程调用这个方法, 因为会显着减慢你的系统.
     * 如果此方法的返回值为正, 选择键应该是注册的interestOps，由返回值给出.
     *
     * @param read boolean - true 如果底层通道是可读的
     * @param write boolean - true 如果底层通道是可写的
     *
     * @return 0 如果握手完成, -1 如果发生错误 (而不是一个IOException), 否则它返回SelectionKey interestOps值
     *
     * @throws IOException 如果在握手期间发生I/O错误，或者在打包或解包期间握手失败
     */
    @Override
    public int handshake(boolean read, boolean write) throws IOException {
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

        if (!flush(netOutBuffer)) return SelectionKey.OP_WRITE; // 还有数据要写

        SSLEngineResult handshake = null;

        while (!handshakeComplete) {
            switch ( handshakeStatus ) {
                case NOT_HANDSHAKING: {
                    //should never happen
                    throw new IOException(sm.getString("channel.nio.ssl.notHandshaking"));
                }
                case FINISHED: {
                    if (endpoint.hasNegotiableProtocols()) {
                        if (sslEngine instanceof SSLUtil.ProtocolInfo) {
                            socketWrapper.setNegotiatedProtocol(
                                    ((SSLUtil.ProtocolInfo) sslEngine).getNegotiatedProtocol());
                        } else if (JreCompat.isJre9Available()) {
                            socketWrapper.setNegotiatedProtocol(
                                    JreCompat.getInstance().getApplicationProtocol(sslEngine));
                        }
                    }
                    // 如果交付了最后一个包，就完成了
                    handshakeComplete = !netOutBuffer.hasRemaining();
                    // 如果完成则返回0, 否则仍然有数据要写入
                    return handshakeComplete?0:SelectionKey.OP_WRITE;
                }
                case NEED_WRAP: {
                    // 执行包装功能
                    try {
                        handshake = handshakeWrap(write);
                    } catch (SSLException e) {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("channel.nio.ssl.wrapException"), e);
                        }
                        handshake = handshakeWrap(write);
                    }
                    if (handshake.getStatus() == Status.OK) {
                        if (handshakeStatus == HandshakeStatus.NEED_TASK)
                            handshakeStatus = tasks();
                    } else if (handshake.getStatus() == Status.CLOSED) {
                        flush(netOutBuffer);
                        return -1;
                    } else {
                        // 包装应始终使用缓冲区
                        throw new IOException(sm.getString("channel.nio.ssl.unexpectedStatusDuringWrap", handshake.getStatus()));
                    }
                    if ( handshakeStatus != HandshakeStatus.NEED_UNWRAP || (!flush(netOutBuffer)) ) {
                        // 如果有NEED_UNWRAP，应该实际返回OP_READ
                        return SelectionKey.OP_WRITE;
                    }
                    // 在同一个调用上落到NEED_UNWRAP, 如果需要数据, 将导致BUFFER_UNDERFLOW
                }
                //$FALL-THROUGH$
                case NEED_UNWRAP: {
                    // 执行解包功能
                    handshake = handshakeUnwrap(read);
                    if ( handshake.getStatus() == Status.OK ) {
                        if (handshakeStatus == HandshakeStatus.NEED_TASK)
                            handshakeStatus = tasks();
                    } else if ( handshake.getStatus() == Status.BUFFER_UNDERFLOW ){
                        // 读取更多数据, 重新注册OP_READ
                        return SelectionKey.OP_READ;
                    } else if (handshake.getStatus() == Status.BUFFER_OVERFLOW) {
                        getBufHandler().configureReadBufferForWrite();
                    } else {
                        throw new IOException(sm.getString("channel.nio.ssl.unexpectedStatusDuringWrap", handshakeStatus));
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
        // 如果达到这一点，则握手完成
        return 0;
    }


    /*
     * 查看初始网络字节以确定是否存在SNI扩展，如果存在，则确定是否请求了哪个主机名.
     * 基于提供的主机名, 为此连接配置SSLEngine.
     *
     * @return 0 如果SNI处理完成, -1 如果发生错误 (而不是 IOException), 否则它返回SelectionKey interestOps值
     *
     * @throws IOException 如果在SNI处理期间发生I/O错误
     */
    private int processSNI() throws IOException {
        // 将一些数据读入网络输入缓冲区，以便查看它.
        int bytesRead = sc.read(netInBuffer);
        if (bytesRead == -1) {
            // 在处理SNI之前到达流的末尾.
            return -1;
        }
        TLSClientHelloExtractor extractor = new TLSClientHelloExtractor(netInBuffer);

        while (extractor.getResult() == ExtractorResult.UNDERFLOW &&
                netInBuffer.capacity() < endpoint.getSniParseLimit()) {
            // 提取器需要处理更多数据，但netInBuffer已满，因此请扩展缓冲区并读取更多数据.
            int newLimit = Math.min(netInBuffer.capacity() * 2, endpoint.getSniParseLimit());
            log.info(sm.getString("channel.nio.ssl.expandNetInBuffer",
                    Integer.toString(newLimit)));

            netInBuffer = ByteBufferUtils.expand(netInBuffer, newLimit);
            sc.read(netInBuffer);
            extractor = new TLSClientHelloExtractor(netInBuffer);
        }

        String hostName = null;
        List<Cipher> clientRequestedCiphers = null;
        List<String> clientRequestedApplicationProtocols = null;
        switch (extractor.getResult()) {
        case COMPLETE:
            hostName = extractor.getSNIValue();
            clientRequestedApplicationProtocols =
                    extractor.getClientRequestedApplicationProtocols();
            //$FALL-THROUGH$ 设置客户端请求的密码
        case NOT_PRESENT:
            clientRequestedCiphers = extractor.getClientRequestedCiphers();
            break;
        case NEED_READ:
            return SelectionKey.OP_READ;
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
     * 这要求在此调用发生之前清空网络和应用程序缓冲区, 或者抛出IOException.
     * 
     * @param timeout - 每个套接字操作的超时时间, 以毫秒为单位
     * 
     * @throws IOException - 如果发生IO异常, 或应用程序或网络缓冲区包含数据
     * @throws SocketTimeoutException - 如果套接字操作超时
     */
    @SuppressWarnings("null") // key cannot be null
    public void rehandshake(long timeout) throws IOException {
        // 验证网络缓冲区是否为空
        if (netInBuffer.position() > 0 && netInBuffer.position()<netInBuffer.limit()) throw new IOException(sm.getString("channel.nio.ssl.netInputNotEmpty"));
        if (netOutBuffer.position() > 0 && netOutBuffer.position()<netOutBuffer.limit()) throw new IOException(sm.getString("channel.nio.ssl.netOutputNotEmpty"));
        if (!getBufHandler().isReadBufferEmpty()) throw new IOException(sm.getString("channel.nio.ssl.appInputNotEmpty"));
        if (!getBufHandler().isWriteBufferEmpty()) throw new IOException(sm.getString("channel.nio.ssl.appOutputNotEmpty"));
        handshakeComplete = false;
        boolean isReadable = false;
        boolean isWriteable = false;
        boolean handshaking = true;
        Selector selector = null;
        SelectionKey key = null;
        try {
            sslEngine.beginHandshake();
            handshakeStatus = sslEngine.getHandshakeStatus();
            while (handshaking) {
                int hsStatus = this.handshake(isReadable, isWriteable);
                switch (hsStatus) {
                    case -1 : throw new EOFException(sm.getString("channel.nio.ssl.eofDuringHandshake"));
                    case  0 : handshaking = false; break;
                    default : {
                        long now = System.currentTimeMillis();
                        if (selector==null) {
                            selector = Selector.open();
                            key = getIOChannel().register(selector, hsStatus);
                        } else {
                            key.interestOps(hsStatus); // null warning supressed
                        }
                        int keyCount = selector.select(timeout);
                        if (keyCount == 0 && ((System.currentTimeMillis()-now) >= timeout)) {
                            throw new SocketTimeoutException(sm.getString("channel.nio.ssl.timeoutDuringHandshake"));
                        }
                        isReadable = key.isReadable();
                        isWriteable = key.isWritable();
                    }
                }
            }
        } catch (IOException x) {
            closeSilently();
            throw x;
        } catch (Exception cx) {
            closeSilently();
            IOException x = new IOException(cx);
            throw x;
        } finally {
            if (key!=null) try {key.cancel();} catch (Exception ignore) {}
            if (selector!=null) try {selector.close();} catch (Exception ignore) {}
        }
    }



    /**
     * 执行同一线程上所需的所有任务.
     * 
     * @return the status
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
     * @param doWrite boolean
     * 
     * @return the result
     * @throws IOException 发生IO错误
     */
    protected SSLEngineResult handshakeWrap(boolean doWrite) throws IOException {
        //永远不应该使用包含数据的网络缓冲区来调用它，以便在此处清除它.
        netOutBuffer.clear();
        // 执行包装
        getBufHandler().configureWriteBufferForRead();
        SSLEngineResult result = sslEngine.wrap(getBufHandler().getWriteBuffer(), netOutBuffer);
        // 准备要写入的结果
        netOutBuffer.flip();
        // set the status
        handshakeStatus = result.getHandshakeStatus();
        // 优化, 如果有可写入的通道, 现在写入它
        if ( doWrite ) flush(netOutBuffer);
        return result;
    }

    /**
     * 执行握手解包
     * 
     * @param doread boolean
     * 
     * @return the result
     * @throws IOException 发生IO错误
     */
    protected SSLEngineResult handshakeUnwrap(boolean doread) throws IOException {

        if (netInBuffer.position() == netInBuffer.limit()) {
            // 如果已将数据清空，请清除缓冲区
            netInBuffer.clear();
        }
        if ( doread )  {
            // 如果有数据要读取, 读取它
            int read = sc.read(netInBuffer);
            if (read == -1) throw new IOException(sm.getString("channel.nio.ssl.eofDuringHandshake"));
        }
        SSLEngineResult result;
        boolean cont = false;
        // 循环，直到可以执行纯SSLEngine数据
        do {
            // 使用传入数据准备缓冲区
            netInBuffer.flip();
            //call unwrap
            getBufHandler().configureReadBufferForWrite();
            result = sslEngine.unwrap(netInBuffer, getBufHandler().getReadBuffer());
            // 压缩缓冲区, 这是一种可选方法, 想知道如果我们不这样做会发生什么
            netInBuffer.compact();
            // 读取状态
            handshakeStatus = result.getHandshakeStatus();
            if ( result.getStatus() == SSLEngineResult.Status.OK &&
                 result.getHandshakeStatus() == HandshakeStatus.NEED_TASK ) {
                // 如果需要，执行任务
                handshakeStatus = tasks();
            }
            // 执行另一个解包?
            cont = result.getStatus() == SSLEngineResult.Status.OK &&
                   handshakeStatus == HandshakeStatus.NEED_UNWRAP;
        }while ( cont );
        return result;
    }

    /**
     * 发送SSL关闭消息, 这里不会关闭连接.
     * <br>如果需要关闭连接, 可以做以下事情
     * <pre><code>
     *   close();
     *   while (isOpen() &amp;&amp; !myTimeoutFunction()) Thread.sleep(25);
     *   if ( isOpen() ) close(true); //forces a close if you timed out
     * </code></pre>
     * 
     * @throws IOException 如果发生I/O错误
     * @throws IOException 如果传出网络缓冲区上有数据，则无法刷新它
     */
    @Override
    public void close() throws IOException {
        if (closing) return;
        closing = true;
        sslEngine.closeOutbound();

        if (!flush(netOutBuffer)) {
            throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"));
        }
        // 准备关闭消息的缓冲区
        netOutBuffer.clear();
        // 执行关闭, 调用 sslEngine.closeOutbound
        SSLEngineResult handshake = sslEngine.wrap(getEmptyBuf(), netOutBuffer);
        // we should be in a close state
        if (handshake.getStatus() != SSLEngineResult.Status.CLOSED) {
            throw new IOException(sm.getString("channel.nio.ssl.invalidCloseState"));
        }
        // 准备缓冲区进行写入
        netOutBuffer.flip();
        // 如果有要写入的数据
        flush(netOutBuffer);

        // 通道是否关闭?
        closed = (!netOutBuffer.hasRemaining() && (handshake.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
    }


    @Override
    public void close(boolean force) throws IOException {
        try {
            close();
        } finally {
            if (force || closed) {
                closed = true;
                sc.socket().close();
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


    /**
     * 从该通道读取一个字节序列到给定的缓冲区.
     *
     * @param dst 要传输字节的缓冲区
     * 
     * @return 读取的字节数, 可能为零, 或<tt>-1</tt>如果通道已到达流的末尾
     * @throws IOException 如果发生其他一些I/O错误
     * @throws IllegalArgumentException 如果目标缓冲区与getBufHandler().getReadBuffer()不同
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        // 确保只使用ApplicationBufferHandler的缓冲区
        if (dst != getBufHandler().getReadBuffer() && (getAppReadBufHandler() == null
                || dst != getAppReadBufHandler().getByteBuffer())) {
            throw new IllegalArgumentException(sm.getString("channel.nio.ssl.invalidBuffer"));
        }
        // 是正在关闭还是已经关闭?
        if ( closing || closed) return -1;
        // 是否完成握手?
        if (!handshakeComplete) throw new IllegalStateException(sm.getString("channel.nio.ssl.incompleteHandshake"));

        // 从网络上读取
        int netread = sc.read(netInBuffer);
        // 是否到达 EOF? 如果是这样，将EOF发送到一层.
        if (netread == -1) return -1;

        // the data read
        int read = 0;
        // SSL引擎结果
        SSLEngineResult unwrap;
        do {
            // 准备缓冲区
            netInBuffer.flip();
            // 解包数据
            unwrap = sslEngine.unwrap(netInBuffer, dst);
            // 压缩缓冲区
            netInBuffer.compact();

            if (unwrap.getStatus() == Status.OK || unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                // 确实收到了一些数据, 将它添加到总数中
                read += unwrap.bytesProduced();
                // 执行任务
                if (unwrap.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    tasks();
                }
                // 如果需要更多的网络数据, 然后保释出来.
                if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                    break;
                }
            } else if (unwrap.getStatus() == Status.BUFFER_OVERFLOW) {
                if (read > 0) {
                    // 如果读取数据，可能会发生缓冲区溢出. 返回，以便在尝试另一次读取之前清空目标缓冲区
                    break;
                } else {
                    // 自创建缓冲区以来，SSL会话增加了所需的缓冲区大小.
                    if (dst == getBufHandler().getReadBuffer()) {
                        // 这是此代码的正常情况
                        getBufHandler().expand(sslEngine.getSession().getApplicationBufferSize());
                        dst = getBufHandler().getReadBuffer();
                    } else if (dst == getAppReadBufHandler().getByteBuffer()) {
                        getAppReadBufHandler()
                                .expand(sslEngine.getSession().getApplicationBufferSize());
                        dst = getAppReadBufHandler().getByteBuffer();
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
        } while (netInBuffer.position() != 0); // 只要输入缓冲区有东西，就继续解包
        return read;
    }

    /**
     * 从给定缓冲区向该通道写入一个字节序列.
     *
     * @param src 要从中检索字节的缓冲区
     * 
     * @return 写入的字节数, 可能为零
     * @throws IOException 如果发生其他一些I/O错误
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        checkInterruptStatus();
        if (src == this.netOutBuffer) {
            // 可以通过使用NioBlockingSelector进行递归调用
            int written = sc.write(src);
            return written;
        } else {
            // 正在关闭或已经关闭?
            if (closing || closed) {
                throw new IOException(sm.getString("channel.nio.ssl.closing"));
            }

            if (!flush(netOutBuffer)) {
                // 还没有清空缓冲区
                return 0;
            }

            // 数据缓冲区为空, 可以重用整个缓冲区.
            netOutBuffer.clear();

            SSLEngineResult result = sslEngine.wrap(src, netOutBuffer);
            // 写入的字节数
            int written = result.bytesConsumed();
            netOutBuffer.flip();

            if (result.getStatus() == Status.OK) {
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) tasks();
            } else {
                throw new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
            }

            // 强制刷新
            flush(netOutBuffer);

            return written;
        }
    }

    @Override
    public int getOutboundRemaining() {
        return netOutBuffer.remaining();
    }

    @Override
    public boolean flushOutbound() throws IOException {
        int remaining = netOutBuffer.remaining();
        flush(netOutBuffer);
        int remaining2= netOutBuffer.remaining();
        return remaining2 < remaining;
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