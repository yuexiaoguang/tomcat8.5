package org.apache.catalina;

import java.util.EventObject;

/**
 * 一般事件，通知监听器实现了Lifecycle接口的组件发生修改。
 */
public final class LifecycleEvent extends EventObject {

    private static final long serialVersionUID = 1L;


    /**
     * @param lifecycle 发生事件的Lifecycle接口实现类
     * @param type 事件类型(必需)
     * @param data 事件数据
     */
    public LifecycleEvent(Lifecycle lifecycle, String type, Object data) {
        super(lifecycle);
        this.type = type;
        this.data = data;
    }


    private final Object data;


    private final String type;


    public Object getData() {
        return data;
    }

    public Lifecycle getLifecycle() {
        return (Lifecycle) getSource();
    }

    public String getType() {
        return this.type;
    }
}
