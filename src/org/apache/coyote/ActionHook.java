package org.apache.coyote;


/**
 * 动作钩子. 动作表示coyote servlet容器使用的回调机制，以请求coyote连接器上的操作.
 * 一些标准的动作定义在 ActionCode中, 但是允许自定义动作.
 *
 * 接口通常由 ProtocolHandler实现, 而且 param通常是一个 Request 或 Response 对象.
 */
public interface ActionHook {

    /**
     * 向连接器发送一个动作.
     *
     * @param actionCode 动作类型
     * @param param 可用于传递和返回与动作相关的信息
     */
    public void action(ActionCode actionCode, Object param);
}
