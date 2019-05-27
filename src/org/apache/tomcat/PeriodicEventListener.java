package org.apache.tomcat;

public interface PeriodicEventListener {
    /**
     * 执行一个周期任务, 例如重新加载, 等.
     */
    public void periodicEvent();
}
