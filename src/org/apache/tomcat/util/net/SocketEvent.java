package org.apache.tomcat.util.net;

/**
 * 定义每个套接字发生的事件，需要容器进一步处理. 通常这些事件由套接字实现触发，但它们也可能由容器触发.
 */
public enum SocketEvent {

    /**
     * 可以读取数据.
     */
    OPEN_READ,

    /**
     * 套接字已准备好写入.
     */
    OPEN_WRITE,

    /**
     * 关联的Connector/Endpoint正在停止，连接/套接字需要干净地关闭.
     */
    STOP,

    /**
     * 发生超时，需要干净地关闭连接.
     * 目前，这仅供Servlet 3.0异步处理使用.
     */
    TIMEOUT,

    /**
     * 客户端已断开连接.
     */
    DISCONNECT,

    /**
     * 非容器线程发生错误，处理需要返回容器进行任何必要的清理.
     * 使用它的例子包括:
     * <ul>
     * <li>由NIO2表示完成处理程序的失败</li>
     * <li>由容器在Servlet 3.0异步处理期间发出非容器线程上的I/O错误信号.</li>
     * </ul>
     */
    ERROR
}
