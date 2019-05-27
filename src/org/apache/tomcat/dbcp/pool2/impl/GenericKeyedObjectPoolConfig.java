package org.apache.tomcat.dbcp.pool2.impl;

/**
 * 一个简单的“struct”封装了{@link GenericKeyedObjectPool}的配置.
 *
 * <p>
 * 这个类不是线程安全的; 它仅用于提供创建池时使用的属性.
 */
public class GenericKeyedObjectPoolConfig extends BaseObjectPoolConfig {

    /**
     * {@code maxTotalPerKey}配置属性的默认值.
     */
    public static final int DEFAULT_MAX_TOTAL_PER_KEY = 8;

    /**
     * {@code maxTotal}配置属性的默认值.
     */
    public static final int DEFAULT_MAX_TOTAL = -1;

    /**
     * {@code minIdlePerKey}配置属性的默认值.
     */
    public static final int DEFAULT_MIN_IDLE_PER_KEY = 0;

    /**
     * {@code maxIdlePerKey}配置属性的默认值.
     */
    public static final int DEFAULT_MAX_IDLE_PER_KEY = 8;


    private int minIdlePerKey = DEFAULT_MIN_IDLE_PER_KEY;

    private int maxIdlePerKey = DEFAULT_MAX_IDLE_PER_KEY;

    private int maxTotalPerKey = DEFAULT_MAX_TOTAL_PER_KEY;

    private int maxTotal = DEFAULT_MAX_TOTAL;

    public GenericKeyedObjectPoolConfig() {
    }

    /**
     * 获取使用此配置实例创建的池的{@code maxTotal}配置属性的值.
     */
    public int getMaxTotal() {
        return maxTotal;
    }

    /**
     * 设置使用此配置实例创建的池的{@code maxTotal}配置属性的值.
     *
     * @param maxTotal
     */
    public void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
    }

    /**
     * 获取使用此配置实例创建的池的{@code maxTotalPerKey}配置属性的值.
     */
    public int getMaxTotalPerKey() {
        return maxTotalPerKey;
    }

    /**
     * 设置使用此配置实例创建的池的{@code maxTotalPerKey}配置属性的值.
     *
     * @param maxTotalPerKey
     */
    public void setMaxTotalPerKey(final int maxTotalPerKey) {
        this.maxTotalPerKey = maxTotalPerKey;
    }

    /**
     * 获取使用此配置实例创建的池的{@code minIdlePerKey}配置属性的值.
     */
    public int getMinIdlePerKey() {
        return minIdlePerKey;
    }

    /**
     * 设置使用此配置实例创建的池的{@code minIdlePerKey}配置属性的值.
     *
     * @param minIdlePerKey
     */
    public void setMinIdlePerKey(final int minIdlePerKey) {
        this.minIdlePerKey = minIdlePerKey;
    }

    /**
     * 获取使用此配置实例创建的池的{@code maxIdlePerKey}配置属性的值.
     */
    public int getMaxIdlePerKey() {
        return maxIdlePerKey;
    }

    /**
     * 设置使用此配置实例创建的池的{@code maxIdlePerKey}配置属性的值.
     *
     * @param maxIdlePerKey
     */
    public void setMaxIdlePerKey(final int maxIdlePerKey) {
        this.maxIdlePerKey = maxIdlePerKey;
    }

    @Override
    public GenericKeyedObjectPoolConfig clone() {
        try {
            return (GenericKeyedObjectPoolConfig) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(); // Can't happen
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", minIdlePerKey=");
        builder.append(minIdlePerKey);
        builder.append(", maxIdlePerKey=");
        builder.append(maxIdlePerKey);
        builder.append(", maxTotalPerKey=");
        builder.append(maxTotalPerKey);
        builder.append(", maxTotal=");
        builder.append(maxTotal);
    }
}
