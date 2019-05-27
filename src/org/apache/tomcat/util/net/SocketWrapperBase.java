package org.apache.tomcat.util.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteBufferHolder;
import org.apache.tomcat.util.res.StringManager;

public abstract class SocketWrapperBase<E> {

    private static final Log log = LogFactory.getLog(SocketWrapperBase.class);

    protected static final StringManager sm = StringManager.getManager(SocketWrapperBase.class);

    private final E socket;
    private final AbstractEndpoint<E> endpoint;

    // 易失性，因为I/O和设置超时值发生在与检查超时的线程不同的线程上.
    private volatile long readTimeout = -1;
    private volatile long writeTimeout = -1;

    private volatile int keepAliveLeft = 100;
    private volatile boolean upgraded = false;
    private boolean secure = false;
    private String negotiatedProtocol = null;
    /*
     * 以下缓存, 用于速度/减少GC
     */
    protected String localAddr = null;
    protected String localName = null;
    protected int localPort = -1;
    protected String remoteAddr = null;
    protected String remoteHost = null;
    protected int remotePort = -1;
    /*
     * 如果在套接字级别设置了阻塞/非阻塞，则使用此选项. 客户端负责通过提供的锁对该字段进行线程安全使用.
     */
    private volatile boolean blockingStatus = true;
    private final Lock blockingStatusReadLock;
    private final WriteLock blockingStatusWriteLock;
    /*
     * 用于记录在非阻塞读/写期间发生的第一个IOException，它无法在堆栈中有效传播，因为堆栈中没有用户代码或适当的容器代码来处理它.
     */
    private volatile IOException error = null;

    /**
     * 用于与套接字通信的缓冲区.
     */
    protected volatile SocketBufferHandler socketBufferHandler = null;

    /**
     * 对于“非阻塞”写入，使用外部缓冲区集.
     * 虽然API一次只允许一次非阻塞写入, 由于缓冲和可能需要写入HTTP标头, OutputBuffer可能有多个写入.
     */
    protected final LinkedBlockingDeque<ByteBufferHolder> bufferedWrites = new LinkedBlockingDeque<>();

    /**
     * 缓冲的写入缓冲区的最大大小
     */
    protected int bufferedWriteSize = 64 * 1024; // 64k default write buffer

    public SocketWrapperBase(E socket, AbstractEndpoint<E> endpoint) {
        this.socket = socket;
        this.endpoint = endpoint;
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        this.blockingStatusReadLock = lock.readLock();
        this.blockingStatusWriteLock = lock.writeLock();
    }

    public E getSocket() {
        return socket;
    }

    public AbstractEndpoint<E> getEndpoint() {
        return endpoint;
    }

    public IOException getError() { return error; }
    public void setError(IOException error) {
        // 不是完全线程安全, 但足够好. 只需要确保一旦this.error为非null, 它永远不会为 null.
        if (this.error != null) {
            return;
        }
        this.error = error;
    }
    public void checkError() throws IOException {
        if (error != null) {
            throw error;
        }
    }

    public boolean isUpgraded() { return upgraded; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public String getNegotiatedProtocol() { return negotiatedProtocol; }
    public void setNegotiatedProtocol(String negotiatedProtocol) {
        this.negotiatedProtocol = negotiatedProtocol;
    }

    /**
     * 设置读取超时. 零或更小的值将更改为-1.
     *
     * @param readTimeout 超时时间, 以毫秒为单位. 值-1表示无限超时.
     */
    public void setReadTimeout(long readTimeout) {
        if (readTimeout > 0) {
            this.readTimeout = readTimeout;
        } else {
            this.readTimeout = -1;
        }
    }

    public long getReadTimeout() {
        return this.readTimeout;
    }

    /**
     * 设置写入超时时间. 零或更小的值将更改为-1.
     *
     * @param writeTimeout 超时时间, 以毫秒为单位. 值为零或更小表示无限超时.
     */
    public void setWriteTimeout(long writeTimeout) {
        if (writeTimeout > 0) {
            this.writeTimeout = writeTimeout;
        } else {
            this.writeTimeout = -1;
        }
    }

    public long getWriteTimeout() {
        return this.writeTimeout;
    }


    public void setKeepAliveLeft(int keepAliveLeft) { this.keepAliveLeft = keepAliveLeft;}
    public int decrementKeepAlive() { return (--keepAliveLeft);}

    public String getRemoteHost() {
        if (remoteHost == null) {
            populateRemoteHost();
        }
        return remoteHost;
    }
    protected abstract void populateRemoteHost();

    public String getRemoteAddr() {
        if (remoteAddr == null) {
            populateRemoteAddr();
        }
        return remoteAddr;
    }
    protected abstract void populateRemoteAddr();

    public int getRemotePort() {
        if (remotePort == -1) {
            populateRemotePort();
        }
        return remotePort;
    }
    protected abstract void populateRemotePort();

    public String getLocalName() {
        if (localName == null) {
            populateLocalName();
        }
        return localName;
    }
    protected abstract void populateLocalName();

    public String getLocalAddr() {
        if (localAddr == null) {
            populateLocalAddr();
        }
        return localAddr;
    }
    protected abstract void populateLocalAddr();

    public int getLocalPort() {
        if (localPort == -1) {
            populateLocalPort();
        }
        return localPort;
    }
    protected abstract void populateLocalPort();

    public boolean getBlockingStatus() { return blockingStatus; }
    public void setBlockingStatus(boolean blockingStatus) {
        this.blockingStatus = blockingStatus;
    }
    public Lock getBlockingStatusReadLock() { return blockingStatusReadLock; }
    public WriteLock getBlockingStatusWriteLock() {
        return blockingStatusWriteLock;
    }
    public SocketBufferHandler getSocketBufferHandler() { return socketBufferHandler; }

    public boolean hasDataToWrite() {
        return !socketBufferHandler.isWriteBufferEmpty() || bufferedWrites.size() > 0;
    }

    /**
     * 检查是否有任何待处理的写入, 以及是否调用{@link #registerWriteInterest()}, 在挂起的写入完成后触发回调.
     * <p>
     * Note: 一旦这个方法返回<code>false</code>, 在挂起的写入完成和触发回调完成之前, 不能再次调用它.
     *       TODO: 编辑{@link #registerWriteInterest()}, 所以上面的限制是在那里强制执行, 而不是依赖于调用者.
     *
     * @return <code>true</code>如果没有待处理的写入，则可以写入数据; 否则<code>false</code>
     */
    public boolean isReadyForWrite() {
        boolean result = canWrite();
        if (!result) {
            registerWriteInterest();
        }
        return result;
    }


    public boolean canWrite() {
        if (socketBufferHandler == null) {
            throw new IllegalStateException(sm.getString("socket.closed"));
        }
        return socketBufferHandler.isWriteBufferWritable() && bufferedWrites.size() == 0;
    }


    /**
     * 为调试而重写. 不保证此消息的格式, 其在点发布之间可能会有很大差异.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return super.toString() + ":" + String.valueOf(socket);
    }


    public abstract int read(boolean block, byte[] b, int off, int len) throws IOException;
    public abstract int read(boolean block, ByteBuffer to) throws IOException;
    public abstract boolean isReadyForRead() throws IOException;
    public abstract void setAppReadBufHandler(ApplicationBufferHandler handler);

    protected int populateReadBuffer(byte[] b, int off, int len) {
        socketBufferHandler.configureReadBufferForRead();
        ByteBuffer readBuffer = socketBufferHandler.getReadBuffer();
        int remaining = readBuffer.remaining();

        // 读取缓冲区中是否有足够的数据来满足此请求?
        // 将读取缓冲区中的数据复制到字节数组中
        if (remaining > 0) {
            remaining = Math.min(remaining, len);
            readBuffer.get(b, off, remaining);

            if (log.isDebugEnabled()) {
                log.debug("Socket: [" + this + "], Read from buffer: [" + remaining + "]");
            }
        }
        return remaining;
    }


    protected int populateReadBuffer(ByteBuffer to) {
        // 读取缓冲区中是否有足够的数据来满足此请求?
        // 将读取缓冲区中的数据复制到字节数组中
        socketBufferHandler.configureReadBufferForRead();
        int nRead = transfer(socketBufferHandler.getReadBuffer(), to);

        if (log.isDebugEnabled()) {
            log.debug("Socket: [" + this + "], Read from buffer: [" + nRead + "]");
        }
        return nRead;
    }


    /**
     * 将已读取的输入返回到输入缓冲区，以便通过正确的组件重新读取.
     * 有时, 组件在将控制权传递给另一个组件之前, 可能会读取比所需数据更多的数据. 其中一个例子是在HTTP升级期间.
     * 如果客户端在HTTP升级完成之前发送与升级的协议关联的数据, HTTP处理程序可以读取它. 此方法提供了一种返回数据的方法，因此可以由正确的组件处理.
     *
     * @param returnedInput 返回到输入缓冲区的输入.
     */
    public void unRead(ByteBuffer returnedInput) {
        if (returnedInput != null) {
            socketBufferHandler.configureReadBufferForWrite();
            socketBufferHandler.getReadBuffer().put(returnedInput);
        }
    }


    public abstract void close() throws IOException;
    public abstract boolean isClosed();

    /**
     * 将提供的数据写入套接字, 如果在非阻塞模式下使用, 则缓冲剩余的数据.
     *
     * @param block <code>true</code>如果应该使用阻塞写入, 否则将使用非阻塞写入
     * @param buf   包含要写入的数据的字节数组
     * @param off   要写入的数据的字节数组内的偏移量
     * @param len   要写入的数据的长度
     *
     * @throws IOException 如果在写入期间发生IO错误
     */
    public final void write(boolean block, byte[] buf, int off, int len) throws IOException {
        if (len == 0 || buf == null) {
            return;
        }

        // 虽然阻塞和非阻塞写入的实现非常相似，但它们已被拆分为单独的方法，以允许子类单独覆盖它们.
        // 例如，NIO2会覆盖非阻塞写入，但不会覆盖阻塞写入.
        if (block) {
            writeBlocking(buf, off, len);
        } else {
            writeNonBlocking(buf, off, len);
        }
    }


    /**
     * 将提供的数据写入套接字, 如果在非阻塞模式下使用, 则缓冲剩余的数据.
     *
     * @param block  <code>true</code>如果应该使用阻塞写入, 否则将使用非阻塞写入
     * @param from   包含要写入的数据的ByteBuffer
     *
     * @throws IOException 如果在写入期间发生IO错误
     */
    public final void write(boolean block, ByteBuffer from) throws IOException {
        if (from == null || from.remaining() == 0) {
            return;
        }

        // 虽然阻塞和非阻塞写入的实现非常相似，但它们已被拆分为单独的方法，以允许子类单独覆盖它们.
        // 例如，NIO2会覆盖非阻塞写入，但不会覆盖阻塞写入.
        if (block) {
            writeBlocking(from);
        } else {
            writeNonBlocking(from);
        }
    }


    /**
     * 将数据传输到套接字写入缓冲区 (如果缓冲区使用阻塞写入填满，则将该数据写入套接字), 直到所有数据都已传输, 并且空间保留在套接字写入缓冲区中.
     *
     * @param buf   包含要写入的数据的字节数组
     * @param off   要写入的数据的字节数组内的偏移量
     * @param len   要写入的数据的长度
     *
     * @throws IOException 如果在写入期间发生IO错误
     */
    protected void writeBlocking(byte[] buf, int off, int len) throws IOException {
        // Note: 有一个实现，假设如果已经从非阻塞切换到阻塞，则在切换发生时刷新任何挂起的非阻塞写入.

        // 继续写入, 直到所有数据都已传输到套接字写入缓冲区, 并且空间保留在该缓冲区中
        socketBufferHandler.configureWriteBufferForWrite();
        int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
        while (socketBufferHandler.getWriteBuffer().remaining() == 0) {
            len = len - thisTime;
            off = off + thisTime;
            doWrite(true);
            socketBufferHandler.configureWriteBufferForWrite();
            thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
        }
    }


    /**
     * 将数据写入套接字 (使用阻塞写入将该数据写入套接字), 直到所有数据都已传输, 并且空间保留在套接字写入缓冲区中.
     * 如果可以直接使用提供的缓冲区，则不要传输到套接字写入缓冲区.
     *
     * @param from 包含要写入的数据的ByteBuffer
     *
     * @throws IOException 如果在写入期间发生IO错误
     */
    protected void writeBlocking(ByteBuffer from) throws IOException {
        // Note: 有一个实现，假设如果已经从非阻塞切换到阻塞，则在切换发生时刷新任何挂起的非阻塞写入.

        // 如果可以直接从提供的缓冲区将数据写入套接字，否则将其传输到套接字写入缓冲区
        if (socketBufferHandler.isWriteBufferEmpty()) {
            writeByteBufferBlocking(from);
        } else {
            socketBufferHandler.configureWriteBufferForWrite();
            transfer(from, socketBufferHandler.getWriteBuffer());
            if (!socketBufferHandler.isWriteBufferWritable()) {
                doWrite(true);
                writeByteBufferBlocking(from);
            }
        }
    }


    protected void writeByteBufferBlocking(ByteBuffer from) throws IOException {
        // 套接字写入缓冲区容量是socket.appWriteBufSize
        int limit = socketBufferHandler.getWriteBuffer().capacity();
        int fromLimit = from.limit();
        while (from.remaining() >= limit) {
            from.limit(from.position() + limit);
            doWrite(true, from);
            from.limit(fromLimit);
        }

        if (from.remaining() > 0) {
            socketBufferHandler.configureWriteBufferForWrite();
            transfer(from, socketBufferHandler.getWriteBuffer());
        }
    }


    /**
     * 将数据传输到套接字写入缓冲区 (如果缓冲区使用非阻塞写入填满，则将该数据写入套接字),
     * 直到所有数据都已传输, 并且空间保留在套接字写入缓冲区中, 或非阻塞写入将数据留在套接字写入缓冲区中.
     *
     * @param buf   包含要写入的数据的字节数组
     * @param off   要写入的数据的字节数组内的偏移量
     * @param len   要写入的数据的长度
     *
     * @throws IOException 如果在写入期间发生IO错误
     */
    protected void writeNonBlocking(byte[] buf, int off, int len) throws IOException {
        if (bufferedWrites.size() == 0 && socketBufferHandler.isWriteBufferWritable()) {
            socketBufferHandler.configureWriteBufferForWrite();
            int thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
            len = len - thisTime;
            while (!socketBufferHandler.isWriteBufferWritable()) {
                off = off + thisTime;
                doWrite(false);
                if (len > 0 && socketBufferHandler.isWriteBufferWritable()) {
                    socketBufferHandler.configureWriteBufferForWrite();
                    thisTime = transfer(buf, off, len, socketBufferHandler.getWriteBuffer());
                } else {
                    // 没有在最后的非阻塞写中写入任何数据.
                    // 因此写入缓冲区仍然是满的. 这里别无其他. 结束循环.
                    break;
                }
                len = len - thisTime;
            }
        }

        if (len > 0) {
            // 必须缓冲剩余数据
            addToBuffers(buf, off, len);
        }
    }


    /**
     * 将数据写入套接字 (使用非阻塞写入将该数据写入套接字),
     * 直到所有数据都已传输, 并且空间保留在套接字写入缓冲区中, 或非阻塞写入将数据留在套接字写入缓冲区中.
     * 如果可以直接使用提供的缓冲区，则不要传输到套接字写入缓冲区.
     *
     * @param from 包含要写入的数据的ByteBuffer
     *
     * @throws IOException 如果在写入期间发生IO错误
     */
    protected void writeNonBlocking(ByteBuffer from) throws IOException {
        if (bufferedWrites.size() == 0 && socketBufferHandler.isWriteBufferWritable()) {
            writeNonBlockingInternal(from);
        }

        if (from.remaining() > 0) {
            // 必须缓冲剩余的数据
            addToBuffers(from);
        }
    }


    private boolean writeNonBlockingInternal(ByteBuffer from) throws IOException {
        if (socketBufferHandler.isWriteBufferEmpty()) {
            return writeByteBufferNonBlocking(from);
        } else {
            socketBufferHandler.configureWriteBufferForWrite();
            transfer(from, socketBufferHandler.getWriteBuffer());
            if (!socketBufferHandler.isWriteBufferWritable()) {
                doWrite(false);
                if (socketBufferHandler.isWriteBufferWritable()) {
                    return writeByteBufferNonBlocking(from);
                }
            }
        }

        return !socketBufferHandler.isWriteBufferWritable();
    }


    protected boolean writeByteBufferNonBlocking(ByteBuffer from) throws IOException {
        // 套接字写入缓冲区容量是socket.appWriteBufSize
        int limit = socketBufferHandler.getWriteBuffer().capacity();
        int fromLimit = from.limit();
        while (from.remaining() >= limit) {
            int newLimit = from.position() + limit;
            from.limit(newLimit);
            doWrite(false, from);
            from.limit(fromLimit);
            if (from.position() != newLimit) {
                // 没有在最后的非阻塞写入中写入全部数据.
                // 结束循环.
                return true;
            }
        }

        if (from.remaining() > 0) {
            socketBufferHandler.configureWriteBufferForWrite();
            transfer(from, socketBufferHandler.getWriteBuffer());
        }

        return false;
    }


    /**
     * 从缓冲区中剩余的数据中, 尽可能多地写入数据.
     *
     * @param block <code>true</code> 如果应该使用阻塞写入,
     *                  否则将使用非阻塞写入
     *
     * @return <code>true</code>如果在此方法完成后仍需要刷新数据, 否则<code>false</code>.
     * 		因此在阻塞模式下, 返回值永远是<code>false</code>
     *
     * @throws IOException 如果在写入期间发生IO错误
     */
    public boolean flush(boolean block) throws IOException {
        boolean result = false;
        if (block) {
            // 阻塞刷新将始终清空缓冲区.
            flushBlocking();
        } else {
            result = flushNonBlocking();
        }

        return result;
    }


    protected void flushBlocking() throws IOException {
        doWrite(true);

        if (bufferedWrites.size() > 0) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                writeBlocking(buffer.getBuf());
                if (buffer.getBuf().remaining() == 0) {
                    bufIter.remove();
                }
            }

            if (!socketBufferHandler.isWriteBufferEmpty()) {
                doWrite(true);
            }
        }

    }


    protected boolean flushNonBlocking() throws IOException {
        boolean dataLeft = !socketBufferHandler.isWriteBufferEmpty();

        // 写入套接字, 如果有什么要写入的
        if (dataLeft) {
            doWrite(false);
            dataLeft = !socketBufferHandler.isWriteBufferEmpty();
        }

        if (!dataLeft && bufferedWrites.size() > 0) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (!dataLeft && bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                dataLeft = writeNonBlockingInternal(buffer.getBuf());
                if (buffer.getBuf().remaining() == 0) {
                    bufIter.remove();
                }
            }

            if (!dataLeft && !socketBufferHandler.isWriteBufferEmpty()) {
                doWrite(false);
                dataLeft = !socketBufferHandler.isWriteBufferEmpty();
            }
        }

        return dataLeft;
    }


    /**
     * 将socketWriteBuffer的内容写入套接字. 对于阻塞写入，则将写入缓冲区的全部内容或将抛出IOException.
     * 不会发生部分阻塞写入.
     *
     * @param block 写入是否应该阻塞?
     *
     * @throws IOException 如果在写入期间发生I/O错误（如超时）
     */
    protected void doWrite(boolean block) throws IOException {
        socketBufferHandler.configureWriteBufferForRead();
        doWrite(block, socketBufferHandler.getWriteBuffer());
    }


    /**
     * 将ByteBuffer的内容写入套接字. 对于阻塞写入，则将写入缓冲区的全部内容或将抛出IOException.
     * 不会发生部分阻塞写入.
     *
     * @param block 写入是否应该阻塞?
     * @param from 包含要写入的数据的ByteBuffer
     *
     * @throws IOException 如果在写入期间发生I/O错误（如超时）
     */
    protected abstract void doWrite(boolean block, ByteBuffer from) throws IOException;


    protected void addToBuffers(byte[] buf, int offset, int length) {
        ByteBufferHolder holder = getByteBufferHolder(length);
        holder.getBuf().put(buf, offset, length);
    }


    protected void addToBuffers(ByteBuffer from) {
        ByteBufferHolder holder = getByteBufferHolder(from.remaining());
        holder.getBuf().put(from);
    }


    private ByteBufferHolder getByteBufferHolder(int capacity) {
        ByteBufferHolder holder = bufferedWrites.peekLast();
        if (holder == null || holder.isFlipped() || holder.getBuf().remaining() < capacity) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(bufferedWriteSize, capacity));
            holder = new ByteBufferHolder(buffer, false);
            bufferedWrites.add(holder);
        }
        return holder;
    }


    public void processSocket(SocketEvent socketStatus, boolean dispatch) {
        endpoint.processSocket(this, socketStatus, dispatch);
    }


    public abstract void registerReadInterest();

    public abstract void registerWriteInterest();

    public abstract SendfileDataBase createSendfileData(String filename, long pos, long length);

    /**
     * 启动sendfile进程.
     * 如果sendfile进程在此调用期间未完成且未报告错误, 则应该是预期的, 调用者不会添加套接字到轮询器 (或等效的).
     * 这是此方法的责任.
     *
     * @param sendfileData 表示要发送的文件的数据
     *
     * @return 第一次写入后, sendfile进程的状态.
     */
    public abstract SendfileState processSendfile(SendfileDataBase sendfileData);

    /**
     * 要求客户端执行CLIENT-CERT身份验证, 如果尚未执行.
     *
     * @param sslSupport 客户端身份验证后, 可能需要更新的连接当前使用的SSL/TLS支持实例
     *
     * @throws IOException 如果需要身份验证，那么客户端将有I/O，如果出现错误，将抛出此异常
     */
    public abstract void doClientAuth(SSLSupport sslSupport) throws IOException;

    public abstract SSLSupport getSslSupport(String clientCertProvider);


    // ------------------------------------------------------- NIO 2 style APIs


    public enum CompletionState {
        /**
         * 操作仍在等待中.
         */
        PENDING,
        /**
         * 该操作在线完成.
         */
        INLINE,
        /**
         * 该操作在线完成, 但失败了.
         */
        ERROR,
        /**
         * 操作完成, 但不是在线的.
         */
        DONE
    }

    public enum CompletionHandlerCall {
        /**
         * 操作应该继续, 不应该调用完成处理程序.
         */
        CONTINUE,
        /**
         * 操作已完成, 但不应调用完成处理程序.
         */
        NONE,
        /**
         * 操作已完成, 但应该调用完成处理程序.
         */
        DONE
    }

    public interface CompletionCheck {
        /**
         * 确定应该对完成处理程序进行什么调用.
         *
         * @param state 操作的状态 (自IO调用完成以来完成或在线完成)
         * @param buffers 已传递给原始IO调用的ByteBuffer[]
         * @param offset 已传递给原始IO调用的偏移量
         * @param length 已传递给原始IO调用的长度
         *
         * @return 要进行完成处理程序的调用
         */
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
                int offset, int length);
    }

    /**
     * 此实用程序CompletionCheck将完全写入所有剩余的数据. 如果操作在线完成, 不会调用完成处理程序.
     */
    public static final CompletionCheck COMPLETE_WRITE = new CompletionCheck() {
        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
                int offset, int length) {
            for (int i = 0; i < offset; i++) {
                if (buffers[i].remaining() > 0) {
                    return CompletionHandlerCall.CONTINUE;
                }
            }
            return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE
                    : CompletionHandlerCall.NONE;
        }
    };

    /**
     * 一旦读取了一些数据，该实用程序CompletionCheck将导致完成处理程序被调用.
     * 如果操作在线完成, 不会调用完成处理程序.
     */
    public static final CompletionCheck READ_DATA = new CompletionCheck() {
        @Override
        public CompletionHandlerCall callHandler(CompletionState state, ByteBuffer[] buffers,
                int offset, int length) {
            return (state == CompletionState.DONE) ? CompletionHandlerCall.DONE
                    : CompletionHandlerCall.NONE;
        }
    };

    /**
     * 仅允许对可以有效支持它的连接器使用NIO2样式的读/写.
     *
     * @return 此默认实现始终返回 {@code false}
     */
    public boolean hasAsyncIO() {
        return false;
    }

    /**
     * 允许检查异步读取操作当前是否挂起.
     * 
     * @return <code>true</code>如果端点支持异步IO, 并且正在异步处理读取操作
     */
    public boolean isReadPending() {
        return false;
    }

    /**
     * 允许检查异步写入操作当前是否处于挂起状态.
     * 
     * @return <code>true</code>如果端点支持异步IO, 并且正在异步处理写入操作
     */
    public boolean isWritePending() {
        return false;
    }

    /**
     * 如果异步读取操作处于挂起状态, 此方法将阻塞, 直到操作完成, 或指定的时间已过.
     * 
     * @param timeout 等待的最长时间
     * @param unit 超时的单位
     * 
     * @return <code>true</code>如果读取操作完成,
     *  <code>false</code>如果操作仍处于挂起状态且已超过指定的超时
     */
    public boolean awaitReadComplete(long timeout, TimeUnit unit) {
        return true;
    }

    /**
     * 如果异步写入操作处于挂起状态, 此方法将阻塞, 直到操作完成, 或指定的时间已过.
     * 
     * @param timeout 等待的最长时间
     * @param unit 超时的单位
     * 
     * @return <code>true</code>如果操作完成,
     *  <code>false</code>如果操作仍处于挂起状态且已超过指定的超时
     */
    public boolean awaitWriteComplete(long timeout, TimeUnit unit) {
        return true;
    }

    /**
     * Scatter read. 一旦读取了一些数据或发生错误，就会调用完成处理程序.
     * 如果已提供CompletionCheck对象, 只有在callHandler方法返回true时才会调用完成处理程序.
     * 如果未提供CompletionCheck对象, 使用默认的NIO2行为: 一旦读取了一些数据, 就会调用完成处理程序, 即使读取已在线完成.
     *
     * @param block true: 阻塞, 直到任何挂起的读取完成, 如果发生超时并且读取仍处于挂起状态, 将抛出ReadPendingException;
     * 				false: 不阻塞, 但任何挂起的读取操作都会导致ReadPendingException
     * @param timeout  读取的超时持续时间
     * @param unit  超时持续时间的单位
     * @param attachment  附加到I/O操作的对象, 用于在调用完成处理程序时使用
     * @param check  IO操作是否完成
     * @param handler  IO完成时调用的处理程序
     * @param dsts  缓冲区
     * @param <A> 附件类型
     * 
     * @return 完成状态 (done, done inline, 或者仍在等待中)
     */
    public final <A> CompletionState read(boolean block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler, ByteBuffer... dsts) {
        if (dsts == null) {
            throw new IllegalArgumentException();
        }
        return read(dsts, 0, dsts.length, block, timeout, unit, attachment, check, handler);
    }

    /**
     * Scatter read. 一旦读取了一些数据或发生错误，就会调用完成处理程序.
     * 如果已提供CompletionCheck对象, 只有在callHandler方法返回true时才会调用完成处理程序.
     * 如果未提供CompletionCheck对象, 使用默认的NIO2行为: 一旦读取了一些数据, 就会调用完成处理程序, 即使读取已在线完成.
     *
     * @param dsts  缓冲区
     * @param offset  缓冲区数组中的偏移量
     * @param length  缓冲区数组的长度
     * @param block true: 阻塞, 直到任何挂起的读取完成, 如果发生超时并且读取仍处于挂起状态, 将抛出ReadPendingException;
     * 				false: 不阻塞, 但任何挂起的读取操作都会导致ReadPendingException
     * @param timeout  读取的超时持续时间
     * @param unit  超时持续时间的单位
     * @param attachment  附加到I/O操作的对象, 用于在调用完成处理程序时使用
     * @param check  IO操作是否完成
     * @param handler  IO完成时调用的处理程序
     * @param <A>  附件类型
     * 
     * @return 完成状态 (done, done inline, 或者仍在等待中)
     */
    public <A> CompletionState read(ByteBuffer[] dsts, int offset, int length, boolean block,
            long timeout, TimeUnit unit, A attachment, CompletionCheck check,
            CompletionHandler<Long, ? super A> handler) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gather write. 一旦写入一些数据或发生错误，将调用完成处理程序.
     * 如果已提供CompletionCheck对象, 只有在callHandler方法返回true时, 才会调用完成处理程序.
     * 如果未提供CompletionCheck对象, 使用默认的NIO2行为: 将调用完成处理程序, 即使写入不完整, 数据仍保留在缓冲区中, 或者如果在线写入完成.
     *
     * @param block true: 阻止, 直到任何挂起的写入完成, 如果发生超时并且写入仍处于挂起状态, 将抛出WritePendingException;
     * 				false: 不阻塞, 但任何挂起的写入操作都会导致WritePendingException
     * @param timeout 写入的超时持续时间
     * @param unit 超时持续时间的单位
     * @param attachment 附加到I/O操作的对象, 用于在调用完成处理程序时使用
     * @param check IO操作是否完成
     * @param handler IO完成时调用的处理程序
     * @param srcs 缓冲区
     * @param <A> 附件类型
     * @return 完成状态 (done, done inline, 或者仍在等待中)
     */
    public final <A> CompletionState write(boolean block, long timeout, TimeUnit unit, A attachment,
            CompletionCheck check, CompletionHandler<Long, ? super A> handler, ByteBuffer... srcs) {
        if (srcs == null) {
            throw new IllegalArgumentException();
        }
        return write(srcs, 0, srcs.length, block, timeout, unit, attachment, check, handler);
    }

    /**
     * Gather write. 一旦写入一些数据或发生错误，将调用完成处理程序.
     * 如果已提供CompletionCheck对象, 只有在callHandler方法返回true时, 才会调用完成处理程序.
     * 如果未提供CompletionCheck对象, 使用默认的NIO2行为: 将调用完成处理程序, 即使写入不完整, 数据仍保留在缓冲区中, 或者如果在线写入完成.
     *
     * @param srcs 缓冲区
     * @param offset 缓冲区数组中的偏移量
     * @param length 缓冲区数组的长度
     * @param block true: 阻止, 直到任何挂起的写入完成, 如果发生超时并且写入仍处于挂起状态, 将抛出WritePendingException;
     * 				false: 不阻塞, 但任何挂起的写入操作都会导致WritePendingException
     * @param timeout 写入的超时持续时间
     * @param unit 超时持续时间的单位
     * @param attachment 附加到I/O操作的对象, 用于在调用完成处理程序时使用
     * @param check IO操作是否完成
     * @param handler IO完成时调用的处理程序
     * @param <A> 附件类型
     * 
     * @return 完成状态 (done, done inline, 或者仍在等待中)
     */
    public <A> CompletionState write(ByteBuffer[] srcs, int offset, int length, boolean block,
            long timeout, TimeUnit unit, A attachment, CompletionCheck check,
            CompletionHandler<Long, ? super A> handler) {
        throw new UnsupportedOperationException();
    }


    // --------------------------------------------------------- Utility methods

    protected static int transfer(byte[] from, int offset, int length, ByteBuffer to) {
        int max = Math.min(length, to.remaining());
        if (max > 0) {
            to.put(from, offset, max);
        }
        return max;
    }

    protected static int transfer(ByteBuffer from, ByteBuffer to) {
        int max = Math.min(from.remaining(), to.remaining());
        if (max > 0) {
            int fromLimit = from.limit();
            from.limit(from.position() + max);
            to.put(from);
            from.limit(fromLimit);
        }
        return max;
    }
}
