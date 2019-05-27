package org.apache.coyote;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * 用于所有协议的处理器的公共接口.
 */
public interface Processor {

    /**
     * 处理连接. 每当事件发生时调用 (e.g. 更多的数据到达), 允许继续处理当前未处理的连接.
     *
     * @param socketWrapper 要处理的连接
     * @param status 触发额外的处理的连接状态
     *
     * @return 调用者应该修改的 socket 状态
     *
     * @throws IOException 如果在请求处理过程中发生I/O错误
     */
    SocketState process(SocketWrapperBase<?> socketWrapper, SocketEvent status) throws IOException;

    /**
     * 生成一个升级 token.
     *
     * @return 一个升级token封装了处理升级请求所需的信息
     *
     * @throws IllegalStateException 如果在一个 Processor上调用, 不支持升级
     */
    UpgradeToken getUpgradeToken();

    /**
     * @return {@code true} 如果Processor当前正在处理一个升级请求, 否则 {@code false}
     */
    boolean isUpgrade();
    boolean isAsync();

    /**
     * 检查此处理器，以查看异步超时是否已过期，如果出现这种情况，则处理超时.
     *
     * @param now 确定异步超时是否已过期的当前时间 ({@link System#currentTimeMillis()的返回值}. 如果为负数, 超时总是被视为已经过期了.
     */
    void timeoutAsync(long now);

    /**
     * @return 与此处理器关联的请求.
     */
    Request getRequest();

    /**
     * 回收处理器, 准备好下一个请求，该请求可能在同一个连接上或不同的连接上.
     */
    void recycle();

    /**
     * 设置此HTTP连接的SSL信息.
     *
     * @param sslSupport 用于此连接的SSL支持对象
     */
    void setSslSupport(SSLSupport sslSupport);

    /**
     * 允许在升级过程中检索额外的输入.
     *
     * @return leftover bytes
     *
     * @throws IllegalStateException 如果在不支持升级的Processor上调用
     */
    ByteBuffer getLeftoverInput();

    /**
     * 通知处理器底层的I/O层已停止接受新连接. 这主要是为了使用多路复用连接的处理器能够防止更多的“流”被添加到现有的多路复用连接中.
     */
    void pause();
}
