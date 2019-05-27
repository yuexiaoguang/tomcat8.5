package org.apache.tomcat.util.net;

/**
 * 此枚举列出了请求处理可以触发的不同类型的调度.
 * 在这种情况下, 调度意味着使用给定的套接字状态重新处理此请求.
 */
public enum DispatchType {

    NON_BLOCKING_READ(SocketEvent.OPEN_READ),
    NON_BLOCKING_WRITE(SocketEvent.OPEN_WRITE);

    private final SocketEvent status;

    private DispatchType(SocketEvent status) {
        this.status = status;
    }

    public SocketEvent getSocketStatus() {
        return status;
    }
}
