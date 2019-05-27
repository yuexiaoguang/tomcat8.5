package javax.websocket;

public abstract class Endpoint {

    /**
     * 当新会话启动时触发的事件.
     *
     * @param session   新会话.
     * @param config    配置端点的配置.
     */
    public abstract void onOpen(Session session, EndpointConfig config);

    /**
     * 当会话关闭时触发的事件.
     *
     * @param session       会话
     * @param closeReason   关闭会话的原因
     */
    public void onClose(Session session, CloseReason closeReason) {
        // NO-OP by default
    }

    /**
     * 当发生协议错误时触发的事件.
     *
     * @param session   会话.
     * @param throwable 异常.
     */
    public void onError(Session session, Throwable throwable) {
        // NO-OP by default
    }
}
