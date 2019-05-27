package org.apache.tomcat.util.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.apache.tomcat.util.net.NioEndpoint.Poller;
import org.apache.tomcat.util.res.StringManager;

/**
 * 端点使用的SocketChannel包装器的基类.
 * 这样, SSL套接字通道的逻辑与非SSL的逻辑保持一致, 确保不需要为任何异常情况编写代码.
 */
public class NioChannel implements ByteChannel {

    protected static final StringManager sm = StringManager.getManager(NioChannel.class);

    protected static final ByteBuffer emptyBuf = ByteBuffer.allocate(0);

    protected SocketChannel sc = null;
    protected SocketWrapperBase<NioChannel> socketWrapper = null;

    protected final SocketBufferHandler bufHandler;

    protected Poller poller;

    public NioChannel(SocketChannel channel, SocketBufferHandler bufHandler) {
        this.sc = channel;
        this.bufHandler = bufHandler;
    }

    /**
     * Reset the channel
     *
     * @throws IOException 如果在重置频道时遇到问题
     */
    public void reset() throws IOException {
        bufHandler.reset();
    }


    void setSocketWrapper(SocketWrapperBase<NioChannel> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }

    /**
     * Free the channel memory
     */
    public void free() {
        bufHandler.free();
    }

    /**
     * 如果网络缓冲区已刷新且为空，则返回true.
     *
     * @param block     Unused. May be used when overridden
     * @param s         Unused. May be used when overridden
     * @param timeout   Unused. May be used when overridden
     * 
     * @return 总是返回<code>true</code>, 因为常规频道中没有网络缓冲区
     *
     * @throws IOException 从不用于非安全通道
     */
    public boolean flush(boolean block, Selector s, long timeout)
            throws IOException {
        return true;
    }


    /**
     * Closes this channel.
     *
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public void close() throws IOException {
        getIOChannel().socket().close();
        getIOChannel().close();
    }

    /**
     * 关闭连接.
     *
     * @param force 是否应该强制关闭底层套接字?
     *
     * @throws IOException 如果关闭安全通道失败.
     */
    public void close(boolean force) throws IOException {
        if (isOpen() || force ) close();
    }

    /**
     * 此频道是否已打开.
     *
     * @return <tt>true</tt> 当且仅当此频道开放时
     */
    @Override
    public boolean isOpen() {
        return sc.isOpen();
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
        return sc.write(src);
    }

    /**
     * 从该通道读取一个字节序列到给定的缓冲区.
     *
     * @param dst 要传输字节的缓冲区
     * 
     * @return 读取的字节数, 可能为零, 如果频道已到达流末尾, 则为<tt>-1</tt>
     * @throws IOException 如果发生其他一些I/O错误
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return sc.read(dst);
    }

    public Object getAttachment() {
        Poller pol = getPoller();
        Selector sel = pol!=null?pol.getSelector():null;
        SelectionKey key = sel!=null?getIOChannel().keyFor(sel):null;
        Object att = key!=null?key.attachment():null;
        return att;
    }

    public SocketBufferHandler getBufHandler() {
        return bufHandler;
    }

    public Poller getPoller() {
        return poller;
    }

    public SocketChannel getIOChannel() {
        return sc;
    }

    public boolean isClosing() {
        return false;
    }

    public boolean isHandshakeComplete() {
        return true;
    }

    /**
     * @param read  Unused in non-secure implementation
     * @param write Unused in non-secure implementation
     * 
     * @return 始终返回零
     * @throws IOException 从不用于非安全通道
     */
    public int handshake(boolean read, boolean write) throws IOException {
        return 0;
    }

    public void setPoller(Poller poller) {
        this.poller = poller;
    }

    public void setIOChannel(SocketChannel IOChannel) {
        this.sc = IOChannel;
    }

    @Override
    public String toString() {
        return super.toString()+":"+this.sc.toString();
    }

    public int getOutboundRemaining() {
        return 0;
    }

    /**
     * 如果缓冲区写入数据，则返回true. 非安全通道的NO-OP.
     *
     * @return 对于非安全通道, 始终返回{@code false}
     *
     * @throws IOException 从不用于非安全通道
     */
    public boolean flushOutbound() throws IOException {
        return false;
    }

    /**
     * 在尝试写入之前，应使用此方法检查中断状态.
     *
     * 如果线程已被中断且中断尚未清除，则尝试写入套接字将失败. 发生这种情况时，套接字将从轮询器中删除，而不会选择套接字.
     * 这导致NIO的连接限制泄漏，因为端点期望即使在错误条件下也可以选择套接字.
     * 
     * @throws IOException 如果当前线程被中断
     */
    protected void checkInterruptStatus() throws IOException {
        if (Thread.interrupted()) {
            throw new IOException(sm.getString("channel.nio.interrupted"));
        }
    }


    private ApplicationBufferHandler appReadBufHandler;
    public void setAppReadBufHandler(ApplicationBufferHandler handler) {
        this.appReadBufHandler = handler;
    }
    protected ApplicationBufferHandler getAppReadBufHandler() {
        return appReadBufHandler;
    }
}
