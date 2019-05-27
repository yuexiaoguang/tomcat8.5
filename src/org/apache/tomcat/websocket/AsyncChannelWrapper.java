package org.apache.tomcat.websocket;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

/**
 * 这是一个 {@link java.nio.channels.AsynchronousSocketChannel}的包装器, 限制了可用的方法, 从而简化了实现SSL/TLS支持的过程, 因为拦截的方法更少.
 */
public interface AsyncChannelWrapper {

    Future<Integer> read(ByteBuffer dst);

    <B,A extends B> void read(ByteBuffer dst, A attachment,
            CompletionHandler<Integer,B> handler);

    Future<Integer> write(ByteBuffer src);

    <B,A extends B> void write(ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long,B> handler);

    void close();

    Future<Void> handshake() throws SSLException;
}
