package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedQueue;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.NioEndpoint.NioSocketWrapper;

public class NioBlockingSelector {

    private static final Log log = LogFactory.getLog(NioBlockingSelector.class);

    private static int threadCounter = 0;

    private final SynchronizedStack<KeyReference> keyReferenceStack =
            new SynchronizedStack<>();

    protected Selector sharedSelector;

    protected BlockPoller poller;
    public NioBlockingSelector() {

    }

    public void open(Selector selector) {
        sharedSelector = selector;
        poller = new BlockPoller();
        poller.selector = sharedSelector;
        poller.setDaemon(true);
        poller.setName("NioBlockingSelector.BlockPoller-"+(++threadCounter));
        poller.start();
    }

    public void close() {
        if (poller!=null) {
            poller.disable();
            poller.interrupt();
            poller = null;
        }
    }

    /**
     * 使用bytebuffer对要写入的数据执行阻塞写入.
     * 如果<code>selector</code>参数是 null, 然后它将执行繁忙的写入, 这可能会占用大量的CPU周期.
     * 
     * @param buf ByteBuffer - 包含数据的缓冲区, 只要<code>(buf.hasRemaining()==true)</code>, 将写入
     * @param socket SocketChannel - 要写入数据的套接字
     * @param writeTimeout long - 写入操作的超时时间, 以毫秒为单位, -1 意味着没有超时
     * 
     * @return int - 返回写入的字节数
     * 
     * @throws EOFException 如果写入返回 -1
     * @throws SocketTimeoutException 如果写入超时
     * @throws IOException 如果底层套接字逻辑中发生IO异常
     */
    public int write(ByteBuffer buf, NioChannel socket, long writeTimeout)
            throws IOException {
        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        if ( key == null ) throw new IOException("Key no longer registered");
        KeyReference reference = keyReferenceStack.pop();
        if (reference == null) {
            reference = new KeyReference();
        }
        NioSocketWrapper att = (NioSocketWrapper) key.attachment();
        int written = 0;
        boolean timedout = false;
        int keycount = 1; //假设可以写入
        long time = System.currentTimeMillis(); //启动超时计时器
        try {
            while ( (!timedout) && buf.hasRemaining()) {
                if (keycount > 0) { //只有在注册了写入时, 才可以写入
                    int cnt = socket.write(buf); //write the data
                    if (cnt == -1)
                        throw new EOFException();
                    written += cnt;
                    if (cnt > 0) {
                        time = System.currentTimeMillis(); //重置超时计时器
                        continue; //成功写入, 没有选择器再试一次
                    }
                }
                try {
                    if ( att.getWriteLatch()==null || att.getWriteLatch().getCount()==0) att.startWriteLatch(1);
                    poller.add(att,SelectionKey.OP_WRITE,reference);
                    if (writeTimeout < 0) {
                        att.awaitWriteLatch(Long.MAX_VALUE,TimeUnit.MILLISECONDS);
                    } else {
                        att.awaitWriteLatch(writeTimeout,TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException ignore) {
                    // Ignore
                }
                if ( att.getWriteLatch()!=null && att.getWriteLatch().getCount()> 0) {
                    //被中断了, 但还没有收到轮询器的通知.
                    keycount = 0;
                }else {
                    //latch countdown has happened
                    keycount = 1;
                    att.resetWriteLatch();
                }

                if (writeTimeout > 0 && (keycount == 0))
                    timedout = (System.currentTimeMillis() - time) >= writeTimeout;
            } //while
            if (timedout)
                throw new SocketTimeoutException();
        } finally {
            poller.remove(att,SelectionKey.OP_WRITE);
            if (timedout && reference.key!=null) {
                poller.cancelKey(reference.key);
            }
            reference.key = null;
            keyReferenceStack.push(reference);
        }
        return written;
    }

    /**
     * 使用bytebuffer对要读取的数据执行阻塞读取.
     * 如果<code>selector</code>参数是 null, 那么它将执行频繁的读取, 这可能会占用大量的CPU周期.
     * 
     * @param buf ByteBuffer - 包含数据的缓冲区, 将会读取, 直到读取至少一个字节或超时
     * @param socket SocketChannel - 要写入数据的套接字
     * @param readTimeout long - 读取操作的超时时间, 以毫秒为单位, -1 意味着没有超时
     * 
     * @return int - 返回读取的字节数
     * 
     * @throws EOFException 如果读取返回 -1
     * @throws SocketTimeoutException 如果读取超时
     * @throws IOException 如果底层套接字逻辑中发生IO异常
     */
    public int read(ByteBuffer buf, NioChannel socket, long readTimeout) throws IOException {
        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        if ( key == null ) throw new IOException("Key no longer registered");
        KeyReference reference = keyReferenceStack.pop();
        if (reference == null) {
            reference = new KeyReference();
        }
        NioSocketWrapper att = (NioSocketWrapper) key.attachment();
        int read = 0;
        boolean timedout = false;
        int keycount = 1; //假设可以读取
        long time = System.currentTimeMillis(); //启动超时计时器
        try {
            while(!timedout) {
                if (keycount > 0) { //只有在注册了读取时, 才可以读取
                    read = socket.read(buf);
                    if (read != 0) {
                        break;
                    }
                }
                try {
                    if ( att.getReadLatch()==null || att.getReadLatch().getCount()==0) att.startReadLatch(1);
                    poller.add(att,SelectionKey.OP_READ, reference);
                    if (readTimeout < 0) {
                        att.awaitReadLatch(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                    } else {
                        att.awaitReadLatch(readTimeout, TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException ignore) {
                    // Ignore
                }
                if ( att.getReadLatch()!=null && att.getReadLatch().getCount()> 0) {
                    //被中断了, 但还没有收到轮询器的通知.
                    keycount = 0;
                }else {
                    //latch countdown has happened
                    keycount = 1;
                    att.resetReadLatch();
                }
                if (readTimeout >= 0 && (keycount == 0))
                    timedout = (System.currentTimeMillis() - time) >= readTimeout;
            }
            if (timedout)
                throw new SocketTimeoutException();
        } finally {
            poller.remove(att,SelectionKey.OP_READ);
            if (timedout && reference.key!=null) {
                poller.cancelKey(reference.key);
            }
            reference.key = null;
            keyReferenceStack.push(reference);
        }
        return read;
    }


    protected static class BlockPoller extends Thread {
        protected volatile boolean run = true;
        protected Selector selector = null;
        protected final SynchronizedQueue<Runnable> events =
                new SynchronizedQueue<>();
        public void disable() { run = false; selector.wakeup();}
        protected final AtomicInteger wakeupCounter = new AtomicInteger(0);
        public void cancelKey(final SelectionKey key) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    key.cancel();
                }
            };
            events.offer(r);
            wakeup();
        }

        public void wakeup() {
            if (wakeupCounter.addAndGet(1)==0) selector.wakeup();
        }

        public void cancel(SelectionKey sk, NioSocketWrapper key, int ops){
            if (sk!=null) {
                sk.cancel();
                sk.attach(null);
                if (SelectionKey.OP_WRITE==(ops&SelectionKey.OP_WRITE)) countDown(key.getWriteLatch());
                if (SelectionKey.OP_READ==(ops&SelectionKey.OP_READ))countDown(key.getReadLatch());
            }
        }

        public void add(final NioSocketWrapper key, final int ops, final KeyReference ref) {
            if ( key == null ) return;
            NioChannel nch = key.getSocket();
            final SocketChannel ch = nch.getIOChannel();
            if ( ch == null ) return;

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    SelectionKey sk = ch.keyFor(selector);
                    try {
                        if (sk == null) {
                            sk = ch.register(selector, ops, key);
                            ref.key = sk;
                        } else if (!sk.isValid()) {
                            cancel(sk,key,ops);
                        } else {
                            sk.interestOps(sk.interestOps() | ops);
                        }
                    }catch (CancelledKeyException cx) {
                        cancel(sk,key,ops);
                    }catch (ClosedChannelException cx) {
                        cancel(sk,key,ops);
                    }
                }
            };
            events.offer(r);
            wakeup();
        }

        public void remove(final NioSocketWrapper key, final int ops) {
            if ( key == null ) return;
            NioChannel nch = key.getSocket();
            final SocketChannel ch = nch.getIOChannel();
            if ( ch == null ) return;

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    SelectionKey sk = ch.keyFor(selector);
                    try {
                        if (sk == null) {
                            if (SelectionKey.OP_WRITE==(ops&SelectionKey.OP_WRITE)) countDown(key.getWriteLatch());
                            if (SelectionKey.OP_READ==(ops&SelectionKey.OP_READ))countDown(key.getReadLatch());
                        } else {
                            if (sk.isValid()) {
                                sk.interestOps(sk.interestOps() & (~ops));
                                if (SelectionKey.OP_WRITE==(ops&SelectionKey.OP_WRITE)) countDown(key.getWriteLatch());
                                if (SelectionKey.OP_READ==(ops&SelectionKey.OP_READ))countDown(key.getReadLatch());
                                if (sk.interestOps()==0) {
                                    sk.cancel();
                                    sk.attach(null);
                                }
                            }else {
                                sk.cancel();
                                sk.attach(null);
                            }
                        }
                    }catch (CancelledKeyException cx) {
                        if (sk!=null) {
                            sk.cancel();
                            sk.attach(null);
                        }
                    }
                }
            };
            events.offer(r);
            wakeup();
        }


        public boolean events() {
            Runnable r = null;

            /* 只在启动此方法时轮询并运行可运行事件. 稍后添加到队列的其他事件将延迟到下一次执行此方法.
             *
             * 这样做, 因为来自事件队列的运行事件可能导致工作线程向队列添加更多事件
             * (例如, 当前一个Runnable Add事件被一个无效的SelectionKey唤醒时，工作线程可能会添加另一个Runnable Add事件).
             * 尝试在增加的队列中消费所有事件, 直到它为空, 将使循环难以终止,
             * 这会耗费很多时间, 并极大地影响了轮询器循环的性能.
             */
            int size = events.size();
            for (int i = 0; i < size && (r = events.poll()) != null; i++) {
                r.run();
            }

            return (size > 0);
        }

        @Override
        public void run() {
            while (run) {
                try {
                    events();
                    int keyCount = 0;
                    try {
                        int i = wakeupCounter.get();
                        if (i>0)
                            keyCount = selector.selectNow();
                        else {
                            wakeupCounter.set(-1);
                            keyCount = selector.select(1000);
                        }
                        wakeupCounter.set(0);
                        if (!run) break;
                    }catch ( NullPointerException x ) {
                        //sun bug 5076772 on windows JDK 1.5
                        if (selector==null) throw x;
                        if ( log.isDebugEnabled() ) log.debug("Possibly encountered sun bug 5076772 on windows JDK 1.5",x);
                        continue;
                    } catch ( CancelledKeyException x ) {
                        //sun bug 5076772 on windows JDK 1.5
                        if ( log.isDebugEnabled() ) log.debug("Possibly encountered sun bug 5076772 on windows JDK 1.5",x);
                        continue;
                    } catch (Throwable x) {
                        ExceptionUtils.handleThrowable(x);
                        log.error("",x);
                        continue;
                    }

                    Iterator<SelectionKey> iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;

                    // 浏览准备好的密钥集合, 并调度任何活动事件.
                    while (run && iterator != null && iterator.hasNext()) {
                        SelectionKey sk = iterator.next();
                        NioSocketWrapper attachment = (NioSocketWrapper)sk.attachment();
                        try {
                            iterator.remove();
                            sk.interestOps(sk.interestOps() & (~sk.readyOps()));
                            if ( sk.isReadable() ) {
                                countDown(attachment.getReadLatch());
                            }
                            if (sk.isWritable()) {
                                countDown(attachment.getWriteLatch());
                            }
                        }catch (CancelledKeyException ckx) {
                            sk.cancel();
                            countDown(attachment.getReadLatch());
                            countDown(attachment.getWriteLatch());
                        }
                    }
                }catch ( Throwable t ) {
                    log.error("",t);
                }
            }
            events.clear();
            // 如果使用共享选择器, NioSelectorPool也会尝试关闭选择器. 尽量避免使用ClosedSelectorException, 但由于涉及多个线程, 因此总是存在异常的可能性.
            if (selector.isOpen()) {
                try {
                    // 取消所有剩余的密钥
                    selector.selectNow();
                }catch( Exception ignore ) {
                    if (log.isDebugEnabled())log.debug("",ignore);
                }
            }
            try {
                selector.close();
            }catch( Exception ignore ) {
                if (log.isDebugEnabled())log.debug("",ignore);
            }
        }

        public void countDown(CountDownLatch latch) {
            if ( latch == null ) return;
            latch.countDown();
        }
    }

    public static class KeyReference {
        SelectionKey key = null;

        @Override
        public void finalize() {
            if (key!=null && key.isValid()) {
                log.warn("Possible key leak, cancelling key in the finalizer.");
                try {key.cancel();}catch (Exception ignore){}
            }
        }
    }
}
