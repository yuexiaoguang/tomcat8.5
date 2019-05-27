package org.apache.catalina;

import java.util.EventListener;


/**
 * 定义重要Session生成事件监听器.
 */
public interface SessionListener extends EventListener {


    /**
     * 确认指定事件的发生.
     *
     * @param event 发生的SessionEvent
     */
    public void sessionEvent(SessionEvent event);


}
