package org.apache.tomcat.util.net;

import java.util.Objects;

public abstract class SocketProcessorBase<S> implements Runnable {

    protected SocketWrapperBase<S> socketWrapper;
    protected SocketEvent event;

    public SocketProcessorBase(SocketWrapperBase<S> socketWrapper, SocketEvent event) {
        reset(socketWrapper, event);
    }


    public void reset(SocketWrapperBase<S> socketWrapper, SocketEvent event) {
        Objects.requireNonNull(event);
        this.socketWrapper = socketWrapper;
        this.event = event;
    }


    @Override
    public final void run() {
        synchronized (socketWrapper) {
            // 可能会同时触发处理以进行读取和写入. 上面的同步可确保并行处理不会发生.
            // 下面的测试确保, 如果要处理的第一个事件导致套接字被关闭, 不处理后续事件.
            if (socketWrapper.isClosed()) {
                return;
            }
            doRun();
        }
    }


    protected abstract void doRun();
}
