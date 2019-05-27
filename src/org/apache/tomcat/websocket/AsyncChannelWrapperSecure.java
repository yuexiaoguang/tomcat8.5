package org.apache.tomcat.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 使用SSL/TLS包装 {@link AsynchronousSocketChannel}. 这需要更多的测试才能被认为是稳健的.
 */
public class AsyncChannelWrapperSecure implements AsyncChannelWrapper {

    private static final Log log =
            LogFactory.getLog(AsyncChannelWrapperSecure.class);
    private static final StringManager sm =
            StringManager.getManager(AsyncChannelWrapperSecure.class);

    private static final ByteBuffer DUMMY = ByteBuffer.allocate(16921);
    private final AsynchronousSocketChannel socketChannel;
    private final SSLEngine sslEngine;
    private final ByteBuffer socketReadBuffer;
    private final ByteBuffer socketWriteBuffer;
    // 一个线程用于读取, 一个用于写入
    private final ExecutorService executor =
            Executors.newFixedThreadPool(2, new SecureIOThreadFactory());
    private AtomicBoolean writing = new AtomicBoolean(false);
    private AtomicBoolean reading = new AtomicBoolean(false);

    public AsyncChannelWrapperSecure(AsynchronousSocketChannel socketChannel,
            SSLEngine sslEngine) {
        this.socketChannel = socketChannel;
        this.sslEngine = sslEngine;

        int socketBufferSize = sslEngine.getSession().getPacketBufferSize();
        socketReadBuffer = ByteBuffer.allocateDirect(socketBufferSize);
        socketWriteBuffer = ByteBuffer.allocateDirect(socketBufferSize);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        WrapperFuture<Integer,Void> future = new WrapperFuture<>();

        if (!reading.compareAndSet(false, true)) {
            throw new IllegalStateException(sm.getString(
                    "asyncChannelWrapperSecure.concurrentRead"));
        }

        ReadTask readTask = new ReadTask(dst, future);

        executor.execute(readTask);

        return future;
    }

    @Override
    public <B,A extends B> void read(ByteBuffer dst, A attachment,
            CompletionHandler<Integer,B> handler) {

        WrapperFuture<Integer,B> future =
                new WrapperFuture<>(handler, attachment);

        if (!reading.compareAndSet(false, true)) {
            throw new IllegalStateException(sm.getString(
                    "asyncChannelWrapperSecure.concurrentRead"));
        }

        ReadTask readTask = new ReadTask(dst, future);

        executor.execute(readTask);
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {

        WrapperFuture<Long,Void> inner = new WrapperFuture<>();

        if (!writing.compareAndSet(false, true)) {
            throw new IllegalStateException(sm.getString(
                    "asyncChannelWrapperSecure.concurrentWrite"));
        }

        WriteTask writeTask =
                new WriteTask(new ByteBuffer[] {src}, 0, 1, inner);

        executor.execute(writeTask);

        Future<Integer> future = new LongToIntegerFuture(inner);
        return future;
    }

    @Override
    public <B,A extends B> void write(ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long,B> handler) {

        WrapperFuture<Long,B> future =
                new WrapperFuture<>(handler, attachment);

        if (!writing.compareAndSet(false, true)) {
            throw new IllegalStateException(sm.getString(
                    "asyncChannelWrapperSecure.concurrentWrite"));
        }

        WriteTask writeTask = new WriteTask(srcs, offset, length, future);

        executor.execute(writeTask);
    }

    @Override
    public void close() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            log.info(sm.getString("asyncChannelWrapperSecure.closeFail"));
        }
        executor.shutdownNow();
    }

    @Override
    public Future<Void> handshake() throws SSLException {

        WrapperFuture<Void,Void> wFuture = new WrapperFuture<>();

        Thread t = new WebSocketSslHandshakeThread(wFuture);
        t.start();

        return wFuture;
    }


    private class WriteTask implements Runnable {

        private final ByteBuffer[] srcs;
        private final int offset;
        private final int length;
        private final WrapperFuture<Long,?> future;

        public WriteTask(ByteBuffer[] srcs, int offset, int length,
                WrapperFuture<Long,?> future) {
            this.srcs = srcs;
            this.future = future;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void run() {
            long written = 0;

            try {
                for (int i = offset; i < offset + length; i++) {
                    ByteBuffer src = srcs[i];
                    while (src.hasRemaining()) {
                        socketWriteBuffer.clear();

                        // 加密数据
                        SSLEngineResult r = sslEngine.wrap(src, socketWriteBuffer);
                        written += r.bytesConsumed();
                        Status s = r.getStatus();

                        if (s == Status.OK || s == Status.BUFFER_OVERFLOW) {
                            // 需要写出字节，并且可能需要再次读取源来清空它
                        } else {
                            // Status.BUFFER_UNDERFLOW - 只发生在解包上
                            // Status.CLOSED - unexpected
                            throw new IllegalStateException(sm.getString(
                                    "asyncChannelWrapperSecure.statusWrap"));
                        }

                        // 检查任务
                        if (r.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                            Runnable runnable = sslEngine.getDelegatedTask();
                            while (runnable != null) {
                                runnable.run();
                                runnable = sslEngine.getDelegatedTask();
                            }
                        }

                        socketWriteBuffer.flip();

                        // Do the write
                        int toWrite = r.bytesProduced();
                        while (toWrite > 0) {
                            Future<Integer> f =
                                    socketChannel.write(socketWriteBuffer);
                            Integer socketWrite = f.get();
                            toWrite -= socketWrite.intValue();
                        }
                    }
                }


                if (writing.compareAndSet(true, false)) {
                    future.complete(Long.valueOf(written));
                } else {
                    future.fail(new IllegalStateException(sm.getString(
                            "asyncChannelWrapperSecure.wrongStateWrite")));
                }
            } catch (Exception e) {
                writing.set(false);
                future.fail(e);
            }
        }
    }


    private class ReadTask implements Runnable {

        private final ByteBuffer dest;
        private final WrapperFuture<Integer,?> future;

        public ReadTask(ByteBuffer dest, WrapperFuture<Integer,?> future) {
            this.dest = dest;
            this.future = future;
        }

        @Override
        public void run() {
            int read = 0;

            boolean forceRead = false;

            try {
                while (read == 0) {
                    socketReadBuffer.compact();

                    if (forceRead) {
                        forceRead = false;
                        Future<Integer> f = socketChannel.read(socketReadBuffer);
                        Integer socketRead = f.get();
                        if (socketRead.intValue() == -1) {
                            throw new EOFException(sm.getString(
                                    "asyncChannelWrapperSecure.eof"));
                        }
                    }

                    socketReadBuffer.flip();

                    if (socketReadBuffer.hasRemaining()) {
                        // 对缓冲区中的数据进行解密
                        SSLEngineResult r =
                                sslEngine.unwrap(socketReadBuffer, dest);
                        read += r.bytesProduced();
                        Status s = r.getStatus();

                        if (s == Status.OK) {
                            // 可用于读取的字节, 以及socketReadBuffer 中可能有足够的数据以支持进一步的读取, 而不必从套接字读取
                        } else if (s == Status.BUFFER_UNDERFLOW) {
                            // 在socketReadBuffer中存在部分数据
                            if (read == 0) {
                                // 在处理部分数据和生成某些输出之前需要更多数据
                                forceRead = true;
                            }
                            // 否则返回所拥有的数据，并处理下一次读取的部分数据
                        } else if (s == Status.BUFFER_OVERFLOW) {
                            // 在目标缓冲区中没有足够的空间来存储所有数据.
                            // 可以使用bufferSizeRequired的字节读取值来表示所需的新缓冲区大小，但是显式异常更清楚.
                            if (reading.compareAndSet(true, false)) {
                                throw new ReadBufferOverflowException(sslEngine.
                                        getSession().getApplicationBufferSize());
                            } else {
                                future.fail(new IllegalStateException(sm.getString(
                                        "asyncChannelWrapperSecure.wrongStateRead")));
                            }
                        } else {
                            // Status.CLOSED - unexpected
                            throw new IllegalStateException(sm.getString(
                                    "asyncChannelWrapperSecure.statusUnwrap"));
                        }

                        // Check for tasks
                        if (r.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                            Runnable runnable = sslEngine.getDelegatedTask();
                            while (runnable != null) {
                                runnable.run();
                                runnable = sslEngine.getDelegatedTask();
                            }
                        }
                    } else {
                        forceRead = true;
                    }
                }


                if (reading.compareAndSet(true, false)) {
                    future.complete(Integer.valueOf(read));
                } else {
                    future.fail(new IllegalStateException(sm.getString(
                            "asyncChannelWrapperSecure.wrongStateRead")));
                }
            } catch (Exception e) {
                reading.set(false);
                future.fail(e);
            }
        }
    }


    private class WebSocketSslHandshakeThread extends Thread {

        private final WrapperFuture<Void,Void> hFuture;

        private HandshakeStatus handshakeStatus;
        private Status resultStatus;

        public WebSocketSslHandshakeThread(WrapperFuture<Void,Void> hFuture) {
            this.hFuture = hFuture;
        }

        @Override
        public void run() {
            try {
                sslEngine.beginHandshake();
                // 所以第一个契约是正确的
                socketReadBuffer.position(socketReadBuffer.limit());

                handshakeStatus = sslEngine.getHandshakeStatus();
                resultStatus = Status.OK;

                boolean handshaking = true;

                while(handshaking) {
                    switch (handshakeStatus) {
                        case NEED_WRAP: {
                            socketWriteBuffer.clear();
                            SSLEngineResult r =
                                    sslEngine.wrap(DUMMY, socketWriteBuffer);
                            checkResult(r, true);
                            socketWriteBuffer.flip();
                            Future<Integer> fWrite =
                                    socketChannel.write(socketWriteBuffer);
                            fWrite.get();
                            break;
                        }
                        case NEED_UNWRAP: {
                            socketReadBuffer.compact();
                            if (socketReadBuffer.position() == 0 ||
                                    resultStatus == Status.BUFFER_UNDERFLOW) {
                                Future<Integer> fRead =
                                        socketChannel.read(socketReadBuffer);
                                fRead.get();
                            }
                            socketReadBuffer.flip();
                            SSLEngineResult r =
                                    sslEngine.unwrap(socketReadBuffer, DUMMY);
                            checkResult(r, false);
                            break;
                        }
                        case NEED_TASK: {
                            Runnable r = null;
                            while ((r = sslEngine.getDelegatedTask()) != null) {
                                r.run();
                            }
                            handshakeStatus = sslEngine.getHandshakeStatus();
                            break;
                        }
                        case FINISHED: {
                            handshaking = false;
                            break;
                        }
                        case NOT_HANDSHAKING: {
                            throw new SSLException(
                                    sm.getString("asyncChannelWrapperSecure.notHandshaking"));
                        }
                    }
                }
            } catch (SSLException | InterruptedException |
                    ExecutionException e) {
                hFuture.fail(e);
            }

            hFuture.complete(null);
        }

        private void checkResult(SSLEngineResult result, boolean wrap)
                throws SSLException {

            handshakeStatus = result.getHandshakeStatus();
            resultStatus = result.getStatus();

            if (resultStatus != Status.OK &&
                    (wrap || resultStatus != Status.BUFFER_UNDERFLOW)) {
                throw new SSLException(
                        sm.getString("asyncChannelWrapperSecure.check.notOk", resultStatus));
            }
            if (wrap && result.bytesConsumed() != 0) {
                throw new SSLException(sm.getString("asyncChannelWrapperSecure.check.wrap"));
            }
            if (!wrap && result.bytesProduced() != 0) {
                throw new SSLException(sm.getString("asyncChannelWrapperSecure.check.unwrap"));
            }
        }
    }


    private static class WrapperFuture<T,A> implements Future<T> {

        private final CompletionHandler<T,A> handler;
        private final A attachment;

        private volatile T result = null;
        private volatile Throwable throwable = null;
        private CountDownLatch completionLatch = new CountDownLatch(1);

        public WrapperFuture() {
            this(null, null);
        }

        public WrapperFuture(CompletionHandler<T,A> handler, A attachment) {
            this.handler = handler;
            this.attachment = attachment;
        }

        public void complete(T result) {
            this.result = result;
            completionLatch.countDown();
            if (handler != null) {
                handler.completed(result, attachment);
            }
        }

        public void fail(Throwable t) {
            throwable = t;
            completionLatch.countDown();
            if (handler != null) {
                handler.failed(throwable, attachment);
            }
        }

        @Override
        public final boolean cancel(boolean mayInterruptIfRunning) {
            // 可以通过关闭连接来支持取消
            return false;
        }

        @Override
        public final boolean isCancelled() {
            // 可以通过关闭连接来支持取消
            return false;
        }

        @Override
        public final boolean isDone() {
            return completionLatch.getCount() > 0;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            completionLatch.await();
            if (throwable != null) {
                throw new ExecutionException(throwable);
            }
            return result;
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            boolean latchResult = completionLatch.await(timeout, unit);
            if (latchResult == false) {
                throw new TimeoutException();
            }
            if (throwable != null) {
                throw new ExecutionException(throwable);
            }
            return result;
        }
    }

    private static final class LongToIntegerFuture implements Future<Integer> {

        private final Future<Long> wrapped;

        public LongToIntegerFuture(Future<Long> wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return wrapped.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return wrapped.isCancelled();
        }

        @Override
        public boolean isDone() {
            return wrapped.isDone();
        }

        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            Long result = wrapped.get();
            if (result.longValue() > Integer.MAX_VALUE) {
                throw new ExecutionException(sm.getString(
                        "asyncChannelWrapperSecure.tooBig", result), null);
            }
            return Integer.valueOf(result.intValue());
        }

        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            Long result = wrapped.get(timeout, unit);
            if (result.longValue() > Integer.MAX_VALUE) {
                throw new ExecutionException(sm.getString(
                        "asyncChannelWrapperSecure.tooBig", result), null);
            }
            return Integer.valueOf(result.intValue());
        }
    }


    private static class SecureIOThreadFactory implements ThreadFactory {

        private AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("WebSocketClient-SecureIO-" + count.incrementAndGet());
            // 不需要设置上下文类加载器. 当连接关闭时，线程将被清理.
            t.setDaemon(true);
            return t;
        }
    }
}
