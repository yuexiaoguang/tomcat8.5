package org.apache.catalina;


import java.util.EventObject;

/**
 * 通知容器上重要更改的监听器的一般事件.
 */
public final class ContainerEvent extends EventObject {

    private static final long serialVersionUID = 1L;

    /**
     * 与此事件相关联的事件数据.
     */
    private final Object data;


    /**
     * 此实例表示的事件类型.
     */
    private final String type;


    /**
     * @param container 发生此事件的容器
     * @param type 事件类型
     * @param data 事件数据
     */
    public ContainerEvent(Container container, String type, Object data) {
        super(container);
        this.type = type;
        this.data = data;
    }


    /**
     * 返回此事件的事件数据.
     */
    public Object getData() {
        return this.data;
    }


    /**
     * 返回发生此事件的容器.
     */
    public Container getContainer() {
        return (Container) getSource();
    }


    /**
     * 返回此事件的事件类型.
     */
    public String getType() {
        return this.type;
    }


    @Override
    public String toString() {
        return ("ContainerEvent['" + getContainer() + "','" +
                getType() + "','" + getData() + "']");
    }
}
