package org.apache.catalina;


/**
 * 定义重要容器生成事件的侦听器的接口.
 * 注意："container start" 和 "container stop"事件通常是LifecycleEvents, 而不是ContainerEvents.
 */
public interface ContainerListener {


    /**
     * 确认指定事件的发生.
     *
     * @param event 发生的ContainerEvent
     */
    public void containerEvent(ContainerEvent event);


}
