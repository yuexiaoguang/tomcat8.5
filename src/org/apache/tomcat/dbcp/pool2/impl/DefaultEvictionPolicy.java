package org.apache.tomcat.dbcp.pool2.impl;

import org.apache.tomcat.dbcp.pool2.PooledObject;

/**
 * 提供池使用的{@link EvictionPolicy}的默认实现. 如果满足以下条件，对象将被驱逐:
 * <ul>
 * <li>对象的闲置时间大于
 *     {@link GenericObjectPool#getMinEvictableIdleTimeMillis()} /
 *     {@link GenericKeyedObjectPool#getMinEvictableIdleTimeMillis()}</li>
 * <li>池中多于{@link GenericObjectPool#getMinIdle()} /
 *     {@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()} 个空闲对象, 而且对象的空闲时间大于
 *     {@link GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} /
 *     {@link GenericKeyedObjectPool#getSoftMinEvictableIdleTimeMillis()}
 * </ul>
 * 这个类是不可变的和线程安全的.
 *
 * @param <T> 池中对象的类型
 */
public class DefaultEvictionPolicy<T> implements EvictionPolicy<T> {

    @Override
    public boolean evict(final EvictionConfig config, final PooledObject<T> underTest,
            final int idleCount) {

        if ((config.getIdleSoftEvictTime() < underTest.getIdleTimeMillis() &&
                config.getMinIdle() < idleCount) ||
                config.getIdleEvictTime() < underTest.getIdleTimeMillis()) {
            return true;
        }
        return false;
    }
}
