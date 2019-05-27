package org.apache.tomcat.dbcp.pool2.impl;

import org.apache.tomcat.dbcp.pool2.PooledObject;

/**
 * 提供池的自定义驱逐策略 (i.e. 默认策略为 DefaultEvictionPolicy}), 用户必须提供此接口的实现, 以提供所需的驱逐策略.
 *
 * @param <T> 池中对象的类型
 */
public interface EvictionPolicy<T> {

    /**
     * 调用此方法以测试是否应该逐出池中的空闲对象.
     *
     * @param config    与驱逐相关的池配置
     * @param underTest 要进行驱逐测试的池中的对象
     * @param idleCount 池中当前的空闲对象数，包括被测对象
     * @return <code>true</code> 如果对象应该被驱逐, 否则<code>false</code>
     */
    boolean evict(EvictionConfig config, PooledObject<T> underTest,
            int idleCount);
}
