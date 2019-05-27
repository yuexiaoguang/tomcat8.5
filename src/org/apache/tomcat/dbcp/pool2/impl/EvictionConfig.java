package org.apache.tomcat.dbcp.pool2.impl;

/**
 * 池实现使用此类将配置信息传递给{@link EvictionPolicy}实例. {@link EvictionPolicy}也可能有自己的特定配置属性.
 * <p>
 * 这个类是不可变的和线程安全的.
 */
public class EvictionConfig {

    private final long idleEvictTime;
    private final long idleSoftEvictTime;
    private final int minIdle;

    /**
     * 实例是不可变的.
     *
     * @param poolIdleEvictTime 预计由{@link BaseGenericObjectPool#getMinEvictableIdleTimeMillis()}提供
     * @param poolIdleSoftEvictTime 预计由{@link BaseGenericObjectPool#getSoftMinEvictableIdleTimeMillis()}提供
     * @param minIdle 预计由{@link GenericObjectPool#getMinIdle()}或{@link GenericKeyedObjectPool#getMinIdlePerKey()}提供
     */
    public EvictionConfig(final long poolIdleEvictTime, final long poolIdleSoftEvictTime,
            final int minIdle) {
        if (poolIdleEvictTime > 0) {
            idleEvictTime = poolIdleEvictTime;
        } else {
            idleEvictTime = Long.MAX_VALUE;
        }
        if (poolIdleSoftEvictTime > 0) {
            idleSoftEvictTime = poolIdleSoftEvictTime;
        } else {
            idleSoftEvictTime  = Long.MAX_VALUE;
        }
        this.minIdle = minIdle;
    }

    /**
     * 获取此驱逐配置实例的{@code idleEvictTime}.
     * <p>
     * 基于此值的逐出器的行为将由配置的{@link EvictionPolicy}确定.
     *
     * @return {@code idleEvictTime}以毫秒为单位
     */
    public long getIdleEvictTime() {
        return idleEvictTime;
    }

    /**
     * 获取此驱逐配置实例的{@code idleSoftEvictTime}.
     * <p>
     * 基于此值的逐出器的行为将由配置的{@link EvictionPolicy}确定.
     *
     * @return {@code idleSoftEvictTime}，以毫秒为单位
     */
    public long getIdleSoftEvictTime() {
        return idleSoftEvictTime;
    }

    /**
     * 获取此驱逐配置实例的{@code minIdle}.
     * <p>
     * 基于此值的逐出器的行为将由配置的{@link EvictionPolicy}确定.
     *
     * @return {@code minIdle}
     */
    public int getMinIdle() {
        return minIdle;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("EvictionConfig [idleEvictTime=");
        builder.append(idleEvictTime);
        builder.append(", idleSoftEvictTime=");
        builder.append(idleSoftEvictTime);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append("]");
        return builder.toString();
    }
}
