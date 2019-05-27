package org.apache.catalina;


/**
 * 接口定义了一个监听器，监听实现了Lifecycle接口的组件的重大事件(包括 "组件启动"和 "组件停止").
 * 监听器在关联状态更改发生后将被触发.
 */
public interface LifecycleListener {


    /**
     * 确认指定事件的发生.
     *
     * @param event LifecycleEvent that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event);


}
