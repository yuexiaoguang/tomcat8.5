package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 一般来说, 只需直接调用包装的{@link AsynchronousSocketChannel}. 在某些情况下，可以将异常忽略以防止它们被调用代码忽略.
 */
public class AsyncChannelWrapperNonSecure implements AsyncChannelWrapper {

    private static final Future<Void> NOOP_FUTURE = new NoOpFuture();

    private final AsynchronousSocketChannel socketChannel;

    public AsyncChannelWrapperNonSecure(
            AsynchronousSocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        return socketChannel.read(dst);
    }

    @Override
    public <B,A extends B> void read(ByteBuffer dst, A attachment,
            CompletionHandler<Integer,B> handler) {
        socketChannel.read(dst, attachment, handler);
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return socketChannel.write(src);
    }

    @Override
    public <B,A extends B> void write(ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long,B> handler) {
        socketChannel.write(
                srcs, offset, length, timeout, unit, attachment, handler);
    }

    @Override
    public void close() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public Future<Void> handshake() {
        return NOOP_FUTURE;
    }


    private static final class NoOpFuture implements Future<Void> {

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
        public Void get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            return null;
        }
    }
}
