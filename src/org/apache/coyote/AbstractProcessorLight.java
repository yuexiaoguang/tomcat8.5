package org.apache.coyote;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * 这是一个轻量级的抽象处理器实现，它是所有Processor 实现类从轻量级升级处理器到HTTP/AJP处理器的的基础.
 */
public abstract class AbstractProcessorLight implements Processor {

    private Set<DispatchType> dispatches = new CopyOnWriteArraySet<>();


    @Override
    public SocketState process(SocketWrapperBase<?> socketWrapper, SocketEvent status)
            throws IOException {

        SocketState state = SocketState.CLOSED;
        Iterator<DispatchType> dispatches = null;
        do {
            if (dispatches != null) {
                DispatchType nextDispatch = dispatches.next();
                state = dispatch(nextDispatch.getSocketStatus());
            } else if (status == SocketEvent.DISCONNECT) {
                // Do nothing here, 只是等待它被回收利用
            } else if (isAsync() || isUpgrade() || state == SocketState.ASYNC_END) {
                state = dispatch(status);
                if (state == SocketState.OPEN) {
                    // 可能有管道数据读取. 如果数据现在不被处理, 执行将退出此循环, 并调用 release() 回收处理器 (和输入缓冲区) 删除管道数据. 为了避免这个, 现在处理它.
                    state = service(socketWrapper);
                }
            } else if (status == SocketEvent.OPEN_WRITE) {
                // 异步后可能发生的额外写入事件, ignore
                state = SocketState.LONG;
            } else if (status == SocketEvent.OPEN_READ){
                state = service(socketWrapper);
            } else {
                // 默认关闭 socket, 如果传入的SocketEvent与Processor的当前状态不一致
                state = SocketState.CLOSED;
            }

            if (state != SocketState.CLOSED && isAsync()) {
                state = asyncPostProcess();
            }

            if (getLog().isDebugEnabled()) {
                getLog().debug("Socket: [" + socketWrapper +
                        "], Status in: [" + status +
                        "], State out: [" + state + "]");
            }

            if (dispatches == null || !dispatches.hasNext()) {
                // 只返回非null 的iterator, 如果有调度要处理.
                dispatches = getIteratorAndClearDispatches();
            }
        } while (state == SocketState.ASYNC_END ||
                dispatches != null && state != SocketState.CLOSED);

        return state;
    }


    public void addDispatch(DispatchType dispatchType) {
        synchronized (dispatches) {
            dispatches.add(dispatchType);
        }
    }


    public Iterator<DispatchType> getIteratorAndClearDispatches() {
        // Note: AbstractProtocol中的逻辑取决于这个方法只返回一个非 null 值, 如果 iterator 是非空的. i.e. 永远不应该返回一个空iterator.
        Iterator<DispatchType> result;
        synchronized (dispatches) {
            // 同步iterator的生成, 和需要一个原子操作的分配的清除.
            result = dispatches.iterator();
            if (result.hasNext()) {
                dispatches.clear();
            } else {
                result = null;
            }
        }
        return result;
    }


    protected void clearDispatches() {
        synchronized (dispatches) {
            dispatches.clear();
        }
    }


    /**
     * 服务一个'standard' HTTP 请求. 对于新请求和已经部分读取HTTP请求行或HTTP报头的请求，都需要此方法.
     * 一旦header文件已被完全读取，该方法将不再被调用，直到有新的HTTP请求处理.
     * Note: 在处理过程中请求类型可能会改变，这可能导致一个或多个调用 {@link #dispatch(SocketEvent)}. 请求可能是管道的.
     *
     * @param socketWrapper 要处理的连接
     *
     * @return 调用者应该修改的socket状态
     *
     * @throws IOException 如果在请求处理过程中发生I/O错误
     */
    protected abstract SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException;

    /**
     * 处理不在标准HTTP模式下的正在进行的请求.
     * 当前使用的是Servlet 3.0异步和HTTP升级连接. 未来可进一步使用 future. 这些通常会以HTTP请求开始.
     * 
     * @param status 要处理的事件
     * @return the socket state
     */
    protected abstract SocketState dispatch(SocketEvent status);

    protected abstract SocketState asyncPostProcess();

    protected abstract Log getLog();
}
