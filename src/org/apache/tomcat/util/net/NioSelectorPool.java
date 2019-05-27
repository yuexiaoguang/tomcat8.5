package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 线程安全非阻塞选择器池
 */
public class NioSelectorPool {

    public NioSelectorPool() {
    }

    private static final Log log = LogFactory.getLog(NioSelectorPool.class);

    protected static final boolean SHARED =
        Boolean.parseBoolean(System.getProperty("org.apache.tomcat.util.net.NioSelectorShared", "true"));

    protected NioBlockingSelector blockingSelector;

    protected volatile Selector SHARED_SELECTOR;

    protected int maxSelectors = 200;
    protected long sharedSelectorTimeout = 30000;
    protected int maxSpareSelectors = -1;
    protected boolean enabled = true;
    protected AtomicInteger active = new AtomicInteger(0);
    protected AtomicInteger spare = new AtomicInteger(0);
    protected ConcurrentLinkedQueue<Selector> selectors =
            new ConcurrentLinkedQueue<>();

    protected Selector getSharedSelector() throws IOException {
        if (SHARED && SHARED_SELECTOR == null) {
            synchronized ( NioSelectorPool.class ) {
                if ( SHARED_SELECTOR == null )  {
                    SHARED_SELECTOR = Selector.open();
                    log.info("Using a shared selector for servlet write/read");
                }
            }
        }
        return  SHARED_SELECTOR;
    }

    public Selector get() throws IOException{
        if ( SHARED ) {
            return getSharedSelector();
        }
        if ( (!enabled) || active.incrementAndGet() >= maxSelectors ) {
            if ( enabled ) active.decrementAndGet();
            return null;
        }
        Selector s = null;
        try {
            s = selectors.size()>0?selectors.poll():null;
            if (s == null) {
                s = Selector.open();
            }
            else spare.decrementAndGet();

        }catch (NoSuchElementException x ) {
            try {
                s = Selector.open();
            } catch (IOException iox) {
            }
        } finally {
            if ( s == null ) active.decrementAndGet();// 无法找到选择器
        }
        return s;
    }



    public void put(Selector s) throws IOException {
        if ( SHARED ) return;
        if ( enabled ) active.decrementAndGet();
        if ( enabled && (maxSpareSelectors==-1 || spare.get() < Math.min(maxSpareSelectors,maxSelectors)) ) {
            spare.incrementAndGet();
            selectors.offer(s);
        }
        else s.close();
    }

    public void close() throws IOException {
        enabled = false;
        Selector s;
        while ( (s = selectors.poll()) != null ) s.close();
        spare.set(0);
        active.set(0);
        if (blockingSelector!=null) {
            blockingSelector.close();
        }
        if ( SHARED && getSharedSelector()!=null ) {
            getSharedSelector().close();
            SHARED_SELECTOR = null;
        }
    }

    public void open() throws IOException {
        enabled = true;
        getSharedSelector();
        if (SHARED) {
            blockingSelector = new NioBlockingSelector();
            blockingSelector.open(getSharedSelector());
        }

    }

    /**
     * 使用bytebuffer执行写入, 以写入要写入的数据, 并执行要阻塞的选择器 (如果需要阻塞).
     * 如果<code>selector</code>参数是 null, 并且需要阻塞, 然后它将执行繁忙的写入, 这可能占用大量的CPU周期.
     * 
     * @param buf           包含数据的缓冲区, 如果<code>(buf.hasRemaining()==true)</code>将写入
     * @param socket        要写入数据的套接字
     * @param selector      用于阻塞的选择器, 如果为null, 则将启动忙写入
     * @param writeTimeout  写操作的超时时间, 以毫秒为单位, -1 意味着不会超时
     * @param block         <code>true</code>执行阻塞写入;
     *                      否则将执行非阻塞写入
     *                      
     * @return int - 返回写入的字节数
     * @throws EOFException 如果write返回-1
     * @throws SocketTimeoutException 如果写入超时
     * @throws IOException 如果底层套接字逻辑中发生IO异常
     */
    public int write(ByteBuffer buf, NioChannel socket, Selector selector,
                     long writeTimeout, boolean block) throws IOException {
        if ( SHARED && block ) {
            return blockingSelector.write(buf,socket,writeTimeout);
        }
        SelectionKey key = null;
        int written = 0;
        boolean timedout = false;
        int keycount = 1; //assume we can write
        long time = System.currentTimeMillis(); //start the timeout timer
        try {
            while ( (!timedout) && buf.hasRemaining() ) {
                int cnt = 0;
                if ( keycount > 0 ) { // 只有在注册了写入时, 才可以写入
                    cnt = socket.write(buf); //write the data
                    if (cnt == -1) throw new EOFException();

                    written += cnt;
                    if (cnt > 0) {
                        time = System.currentTimeMillis(); // reset our timeout timer
                        continue; // 成功写入, 没有选择器再试一次
                    }
                    if (cnt==0 && (!block)) break; //don't block
                }
                if ( selector != null ) {
                    // register OP_WRITE to the selector
                    if (key==null) key = socket.getIOChannel().register(selector, SelectionKey.OP_WRITE);
                    else key.interestOps(SelectionKey.OP_WRITE);
                    if (writeTimeout==0) {
                        timedout = buf.hasRemaining();
                    } else if (writeTimeout<0) {
                        keycount = selector.select();
                    } else {
                        keycount = selector.select(writeTimeout);
                    }
                }
                if (writeTimeout > 0 && (selector == null || keycount == 0) ) timedout = (System.currentTimeMillis()-time)>=writeTimeout;
            }//while
            if ( timedout ) throw new SocketTimeoutException();
        } finally {
            if (key != null) {
                key.cancel();
                if (selector != null) selector.selectNow();//removes the key from this selector
            }
        }
        return written;
    }

    /**
     * 使用bytebuffer对要读取的数据执行阻塞读取, 并阻塞选择器.
     * 如果<code>selector</code>参数是 null, 并且需要阻塞, 然后它将执行繁忙的写入, 这可能占用大量的CPU周期.
     * 
     * @param buf ByteBuffer - 包含数据的缓冲区, 将读取直到读取至少一个字节或超时
     * @param socket SocketChannel - 要写入数据的套接字
     * @param selector Selector - 用于阻塞的选择器, 如果为null, 则将启动忙读
     * @param readTimeout long - 读操作的超时时间, 以毫秒为单位, -1 意味着不会超时
     * 
     * @return int - 返回读取的字节数
     * @throws EOFException 如果读取返回-1
     * @throws SocketTimeoutException 如果读取超时
     * @throws IOException 如果底层套接字逻辑中发生IO异常
     */
    public int read(ByteBuffer buf, NioChannel socket, Selector selector, long readTimeout) throws IOException {
        return read(buf,socket,selector,readTimeout,true);
    }
    /**
     * 如果block = true，则使用bytebuffer对要读取的数据执行读取，并使用选择器注册事件.
     * 如果<code>selector</code>参数是 null, 并且需要阻塞, 然后它将执行繁忙的写入, 这可能占用大量的CPU周期.
     * 
     * @param buf ByteBuffer - 包含数据的缓冲区, 将读取直到读取至少一个字节或超时
     * @param socket SocketChannel - 要写入数据的套接字
     * @param selector Selector - 用于阻塞的选择器, 如果为null, 则将启动忙读
     * @param readTimeout long - 读操作的超时时间, 以毫秒为单位, -1 意味着不会超时
     * @param block - true 如果要阻塞, 直到数据可用或达到超时时间
     * 
     * @return int - 返回读取的字节数
     * @throws EOFException 如果读取返回-1
     * @throws SocketTimeoutException 如果读取超时
     * @throws IOException 如果底层套接字逻辑中发生IO异常
     */
    public int read(ByteBuffer buf, NioChannel socket, Selector selector, long readTimeout, boolean block) throws IOException {
        if ( SHARED && block ) {
            return blockingSelector.read(buf,socket,readTimeout);
        }
        SelectionKey key = null;
        int read = 0;
        boolean timedout = false;
        int keycount = 1; //assume we can write
        long time = System.currentTimeMillis(); //start the timeout timer
        try {
            while ( (!timedout) ) {
                int cnt = 0;
                if ( keycount > 0 ) { // 只有在注册了读取时，才可以读取
                    cnt = socket.read(buf);
                    if (cnt == -1) {
                        if (read == 0) {
                            read = -1;
                        }
                        break;
                    }
                    read += cnt;
                    if (cnt > 0) continue; //read some more
                    if (cnt==0 && (read>0 || (!block) ) ) break; //we are done reading
                }
                if ( selector != null ) {//执行阻塞读取
                    // 将OP_WRITE注册到选择器
                    if (key==null) key = socket.getIOChannel().register(selector, SelectionKey.OP_READ);
                    else key.interestOps(SelectionKey.OP_READ);
                    if (readTimeout==0) {
                        timedout = (read==0);
                    } else if (readTimeout<0) {
                        keycount = selector.select();
                    } else {
                        keycount = selector.select(readTimeout);
                    }
                }
                if (readTimeout > 0 && (selector == null || keycount == 0) ) timedout = (System.currentTimeMillis()-time)>=readTimeout;
            }
            if ( timedout ) throw new SocketTimeoutException();
        } finally {
            if (key != null) {
                key.cancel();
                if (selector != null) selector.selectNow();// removes the key from this selector
            }
        }
        return read;
    }

    public void setMaxSelectors(int maxSelectors) {
        this.maxSelectors = maxSelectors;
    }

    public void setMaxSpareSelectors(int maxSpareSelectors) {
        this.maxSpareSelectors = maxSpareSelectors;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSharedSelectorTimeout(long sharedSelectorTimeout) {
        this.sharedSelectorTimeout = sharedSelectorTimeout;
    }

    public int getMaxSelectors() {
        return maxSelectors;
    }

    public int getMaxSpareSelectors() {
        return maxSpareSelectors;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getSharedSelectorTimeout() {
        return sharedSelectorTimeout;
    }

    public ConcurrentLinkedQueue<Selector> getSelectors() {
        return selectors;
    }

    public AtomicInteger getSpare() {
        return spare;
    }
}