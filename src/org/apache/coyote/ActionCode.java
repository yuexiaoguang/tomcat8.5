package org.apache.coyote;

/**
 * 表示从servlet 容器到coyote 连接器的回调. 动作由ProtocolHandler实现, 使用ActionHook接口.
 */
public enum ActionCode {
    ACK,
    CLOSE,
    COMMIT,

    /**
     * 发生严重错误，无法安全恢复.
     * 应该忽略进一步尝试写入响应，并且需要尽快关闭连接. 如果在提交响应后发生错误，也可以强制关闭连接.
     */
    CLOSE_NOW,

    /**
     * 来自客户端的 flush() 操作 ( i.e. servlet输出流或writer上的 flush(), 由servlet调用). 争论是 Response.
     */
    CLIENT_FLUSH,

    /**
     * 是否已将处理器置于错误状态? 注意，响应可能没有适当的错误代码集.
     */
    IS_ERROR,

    /**
     * 处理器可能已经被放置到一个错误状态，一些错误状态不允许任何进一步的I/O. 当前是否允许I/O?
     */
    IS_IO_ALLOWED,

    /**
     * 如果应该禁用请求忽略, 将调用钩子.
     * Example: 取消大文件上传.
     *
     */
    DISABLE_SWALLOW_INPUT,

    /**
     * 用于延迟计算的回调 - 提取远程主机名和地址.
     */
    REQ_HOST_ATTRIBUTE,

    /**
     * 用于延迟计算的回调 - 提取远程主机地址.
     */
    REQ_HOST_ADDR_ATTRIBUTE,

    /**
     * 用于延迟计算的回调 - 提取包含客户端证书的SSL相关属性.
     */
    REQ_SSL_ATTRIBUTE,

    /**
     * 强制TLS重新握手，并让得到的客户端证书可用, 且作为请求属性.
     */
    REQ_SSL_CERTIFICATE,

    /**
     * 用于延迟计算的回调 - socket 远程端口.
     */
    REQ_REMOTEPORT_ATTRIBUTE,

    /**
     * 用于延迟计算的回调 - socket 本地端口.
     */
    REQ_LOCALPORT_ATTRIBUTE,

    /**
     * 用于延迟计算的回调 - 本地地址.
     */
    REQ_LOCAL_ADDR_ATTRIBUTE,

    /**
     * 用于延迟计算的回调 - 本地地址.
     */
    REQ_LOCAL_NAME_ATTRIBUTE,

    /**
     * 设置FORM auth body回复的回调
     */
    REQ_SET_BODY_REPLAY,

    /**
     * 获取可用字节数量的回调.
     */
    AVAILABLE,

    /**
     * 异步请求的回调.
     */
    ASYNC_START,

    /**
     * 异步调用{@link javax.servlet.AsyncContext#dispatch()} 的回调.
     */
    ASYNC_DISPATCH,

    /**
     * 指示实际的调度已经启动，异步状态需要改变的回调.
     */
    ASYNC_DISPATCHED,

    /**
     * 异步调用 {@link javax.servlet.AsyncContext#start(Runnable)} 的回调.
     */
    ASYNC_RUN,

    /**
     * 异步调用 {@link javax.servlet.AsyncContext#complete()} 的回调.
     */
    ASYNC_COMPLETE,

    /**
     * 触发异步超时的处理的回调.
     */
    ASYNC_TIMEOUT,

    /**
     * 触发错误处理的回调.
     */
    ASYNC_ERROR,

    /**
     * 异步调用 {@link javax.servlet.AsyncContext#setTimeout(long)} 的回调
     */
    ASYNC_SETTIMEOUT,

    /**
     * 确定异步处理是否在进行中的回调.
     */
    ASYNC_IS_ASYNC,

    /**
     * 确定异步调度是否在进行中的回调.
     */
    ASYNC_IS_STARTED,

    /**
     * 确定异步完成是否在进行中的回调.
     */
    ASYNC_IS_COMPLETING,

    /**
     * 确定异步调度是否在进行中的回调.
     */
    ASYNC_IS_DISPATCHING,

    /**
     * 确定异步是否超时的回调.
     */
    ASYNC_IS_TIMINGOUT,

    /**
    * 确定异步是否出错的回调.
    */
    ASYNC_IS_ERROR,

    /**
     * 触发post 处理的回调. 通常只在错误处理过程中使用.
     */
    ASYNC_POST_PROCESS,

    /**
     * 触发HTTP升级过程的回调.
     */
    UPGRADE,

    /**
     * 当数据可供读取时，将通知servlet的指示符.
     */
    NB_READ_INTEREST,

    /**
     * 与非阻塞写入一起使用，以确定当前是否允许写入 (设置传递的参数为 <code>true</code>或<code>false</code>).
     * 如果不允许写入，则回调将在将来再次写入时触发.
     */
    NB_WRITE_INTEREST,

    /**
     * 指示请求正文是否已被完全读取.
     */
    REQUEST_BODY_FULLY_READ,

    /**
     * 指示容器需要触发调用 onDataAvailable(), 对于已注册的非阻塞读监听器.
     */
    DISPATCH_READ,

    /**
     * 指示容器需要触发调用 onWritePossible(), 对于已注册的非阻塞写监听器.
     */
    DISPATCH_WRITE,

    /**
     * 执行任何已经通过{@link #DISPATCH_READ} 或 {@link #DISPATCH_WRITE}注册的非阻塞调度.
     * 通常在线程上配置了非阻塞监听器时需要, 该线程在socket上的读或写事件没有触发处理.
     */
    DISPATCH_EXECUTE,

    /**
     * 当前请求是否支持并允许服务器推送?
     */
    IS_PUSH_SUPPORTED,

    /**
     * 代表当前请求的客户端, 推送请求.
     */
    PUSH_REQUEST
}
