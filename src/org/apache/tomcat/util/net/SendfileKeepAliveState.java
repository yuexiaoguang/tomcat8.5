package org.apache.tomcat.util.net;

public enum SendfileKeepAliveState {

    /**
     * 不使用 Keep-alive. 写入响应后，可以关闭套接字.
     */
    NONE,

    /**
     * Keep-alive正在使用中，并且输入缓冲区中有流水线数据，一旦写入当前响应就会被读取.
     */
    PIPELINED,

    /**
     * Keep-alive正在使用中.
     * 应该将套接字添加到轮询器（或等效的），以便在写入当前响应后立即等待更多数据.
     */
    OPEN
}
