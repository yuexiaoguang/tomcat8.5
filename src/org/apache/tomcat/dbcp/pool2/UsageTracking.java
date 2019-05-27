package org.apache.tomcat.dbcp.pool2;

/**
 * 该接口可以由对象池实现以启用客户端 (主要是那些封装池以提供池的扩展功能的客户端), 使用允许的更明智的决策和关于被废弃对象的报告, 向池提供与对象有关的附加信息.
 *
 * @param <T> 池提供的对象类型.
 */
public interface UsageTracking<T> {

    /**
     * 每次使用池化对象使池更好地跟踪借用的对象时, 都会调用此方法.
     *
     * @param pooledObject  使用的对象
     */
    void use(T pooledObject);
}
