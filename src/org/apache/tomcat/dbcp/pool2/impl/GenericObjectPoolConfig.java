package org.apache.tomcat.dbcp.pool2.impl;

/**
 * 一个简单的“struct”封装了{@link GenericObjectPool}的配置.
 *
 * <p>
 * 这个类不是线程安全的; 它仅用于提供创建池时使用的属性.
 */
public class GenericObjectPoolConfig extends BaseObjectPoolConfig {

    /**
     * {@code maxTotal}配置属性的默认值.
     */
    public static final int DEFAULT_MAX_TOTAL = 8;

    /**
     * {@code maxIdle}配置属性的默认值.
     */
    public static final int DEFAULT_MAX_IDLE = 8;

    /**
     * {@code minIdle}配置属性的默认值.
     */
    public static final int DEFAULT_MIN_IDLE = 0;


    private int maxTotal = DEFAULT_MAX_TOTAL;

    private int maxIdle = DEFAULT_MAX_IDLE;

    private int minIdle = DEFAULT_MIN_IDLE;

    /**
     * 获取使用此配置实例创建的池的{@code maxTotal}配置属性的值.
     */
    public int getMaxTotal() {
        return maxTotal;
    }

    /**
     * 为使用此配置实例创建的池, 设置{@code maxTotal}配置属性的值.
     *
     * @param maxTotal
     */
    public void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
    }


    /**
     * 获取使用此配置实例创建的池的{@code maxIdle}配置属性的值.
     */
    public int getMaxIdle() {
        return maxIdle;
    }

    /**
     * 设置使用此配置实例创建的池的{@code maxIdle}配置属性的值.
     *
     * @param maxIdle
     */
    public void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
    }


    /**
     * 获取使用此配置实例创建的池的{@code minIdle}配置属性的值.
     */
    public int getMinIdle() {
        return minIdle;
    }

    /**
     * 获取使用此配置实例创建的池的{@code minIdle}配置属性的值.
     *
     * @param minIdle
     */
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    @Override
    public GenericObjectPoolConfig clone() {
        try {
            return (GenericObjectPoolConfig) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(); // Can't happen
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", maxTotal=");
        builder.append(maxTotal);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", minIdle=");
        builder.append(minIdle);
    }
}
