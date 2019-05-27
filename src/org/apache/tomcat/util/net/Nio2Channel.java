package org.apache.tomcat.util.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 端点使用的SocketChannel包装器的基类.
 * 这样, SSL套接字通道的逻辑与非SSL的逻辑保持一致, 确保不需要为任何异常情况编写代码.
 */
public class Nio2Channel implements AsynchronousByteChannel {

    protected static final ByteBuffer emptyBuf = ByteBuffer.allocate(0);

    protected AsynchronousSocketChannel sc = null;
    protected SocketWrapperBase<Nio2Channel> socket = null;
    protected final SocketBufferHandler bufHandler;

    public Nio2Channel(SocketBufferHandler bufHandler) {
        this.bufHandler = bufHandler;
    }

    /**
     * 重置 channel.
     *
     * @param channel 与此NIO2通道关联的新异步通道
     * @param socket  与此NIO2通道关联的新套接字
     *
     * @throws IOException 如果在重置频道时遇到问题
     */
    public void reset(AsynchronousSocketChannel channel, SocketWrapperBase<Nio2Channel> socket)
            throws IOException {
        this.sc = channel;
        this.socket = socket;
        bufHandler.reset();
    }

    /**
     * 释放频道内存
     */
    public void free() {
        bufHandler.free();
    }

    public SocketWrapperBase<Nio2Channel> getSocket() {
        return socket;
    }


    /**
     * Closes this channel.
     *
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public void close() throws IOException {
        sc.close();
    }


    /**
     * 关闭连接.
     *
     * @param force 是否应该强制关闭底层套接字?
     *
     * @throws IOException 如果关闭安全通道失败.
     */
    public void close(boolean force) throws IOException {
        if (isOpen() || force) {
            close();
        }
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

    public SocketBufferHandler getBufHandler() {
        return bufHandler;
    }

    public AsynchronousSocketChannel getIOChannel() {
        return sc;
    }

    public boolean isClosing() {
        return false;
    }

    public boolean isHandshakeComplete() {
        return true;
    }

    /**
     * @return 始终返回零
     *
     * @throws IOException 从不用于非安全通道
     */
    public int handshake() throws IOException {
        return 0;
    }

    @Override
    public String toString() {
        return super.toString()+":"+this.sc.toString();
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        return sc.read(dst);
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        read(dst, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
    }

    public <A> void read(ByteBuffer dst,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        sc.read(dst, timeout, unit, attachment, handler);
    }

    public <A> void read(ByteBuffer[] dsts,
            int offset, int length, long timeout, TimeUnit unit,
            A attachment, CompletionHandler<Long,? super A> handler) {
        sc.read(dsts, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return sc.write(src);
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        write(src, Integer.MAX_VALUE, TimeUnit.MILLISECONDS, attachment, handler);
    }

    public <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        sc.write(src, timeout, unit, attachment, handler);
    }

    public <A> void write(ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long,? super A> handler) {
        sc.write(srcs, offset, length, timeout, unit, attachment, handler);
    }

    private static final Future<Boolean> DONE = new Future<Boolean>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
        @Override
        public boolean isCancelled() {
            return false;
        }
        @Override
        public boolean isDone() {
            return true;
        }
        @Override
        public Boolean get() throws InterruptedException,
                ExecutionException {
            return Boolean.TRUE;
        }
        @Override
        public Boolean get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            return Boolean.TRUE;
        }
    };

    public Future<Boolean> flush() {
        return DONE;
    }


    private ApplicationBufferHandler appReadBufHandler;
    public void setAppReadBufHandler(ApplicationBufferHandler handler) {
        this.appReadBufHandler = handler;
    }
    protected ApplicationBufferHandler getAppReadBufHandler() {
        return appReadBufHandler;
    }
}
