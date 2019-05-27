package org.apache.tomcat.dbcp.pool2.impl;

import org.apache.tomcat.dbcp.pool2.BaseObject;

/**
 * 提供子类共享的公共属性的实现. 将使用公共常量定义的默认值创建此类的新实例.
 * <p>
 * 这个类不是线程安全的.
 */
public abstract class BaseObjectPoolConfig extends BaseObject implements Cloneable {

    /**
     * {@code lifo}配置属性的默认值.
     */
    public static final boolean DEFAULT_LIFO = true;

    /**
     * {@code fairness}配置属性的默认值.
     */
    public static final boolean DEFAULT_FAIRNESS = false;

    /**
     * {@code maxWait}配置属性的默认值.
     */
    public static final long DEFAULT_MAX_WAIT_MILLIS = -1L;

    /**
     * {@code minEvictableIdleTimeMillis}配置属性的默认值.
     */
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS =
            1000L * 60L * 30L;

    /**
     * {@code softMinEvictableIdleTimeMillis}配置属性的默认值.
     */
    public static final long DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1;

    /**
     * {@code numTestsPerEvictionRun}配置属性的默认值.
     */
    public static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3;

    /**
     * {@code testOnCreate}配置属性的默认值.
     */
    public static final boolean DEFAULT_TEST_ON_CREATE = false;

    /**
     * {@code testOnBorrow}配置属性的默认值.
     */
    public static final boolean DEFAULT_TEST_ON_BORROW = false;

    /**
     * {@code testOnReturn}配置属性的默认值.
     */
    public static final boolean DEFAULT_TEST_ON_RETURN = false;

    /**
     * {@code testWhileIdle}配置属性的默认值.
     */
    public static final boolean DEFAULT_TEST_WHILE_IDLE = false;

    /**
     * {@code timeBetweenEvictionRunsMillis}配置属性的默认值.
     */
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L;

    /**
     * {@code blockWhenExhausted}配置属性的默认值.
     */
    public static final boolean DEFAULT_BLOCK_WHEN_EXHAUSTED = true;

    /**
     * 为使用配置实例创建的池启用JMX的默认值.
     */
    public static final boolean DEFAULT_JMX_ENABLE = true;

    /**
     * 用于命名使用配置实例创建的启用JMX的池的前缀的默认值.
     */
    public static final String DEFAULT_JMX_NAME_PREFIX = "pool";

    /**
     * 用于命名使用配置实例创建的启用JMX的池的基本名称的默认值.
     * 默认是 <code>null</code>, 表示池将提供要使用的基本名称.
     */
    public static final String DEFAULT_JMX_NAME_BASE = null;

    /**
     * {@code evictionPolicyClassName}配置属性的默认值.
     */
    public static final String DEFAULT_EVICTION_POLICY_CLASS_NAME =
            "org.apache.tomcat.dbcp.pool2.impl.DefaultEvictionPolicy";


    private boolean lifo = DEFAULT_LIFO;

    private boolean fairness = DEFAULT_FAIRNESS;

    private long maxWaitMillis = DEFAULT_MAX_WAIT_MILLIS;

    private long minEvictableIdleTimeMillis =
        DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    private long softMinEvictableIdleTimeMillis =
            DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    private int numTestsPerEvictionRun =
        DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    private String evictionPolicyClassName = DEFAULT_EVICTION_POLICY_CLASS_NAME;

    private boolean testOnCreate = DEFAULT_TEST_ON_CREATE;

    private boolean testOnBorrow = DEFAULT_TEST_ON_BORROW;

    private boolean testOnReturn = DEFAULT_TEST_ON_RETURN;

    private boolean testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    private long timeBetweenEvictionRunsMillis =
        DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    private boolean blockWhenExhausted = DEFAULT_BLOCK_WHEN_EXHAUSTED;

    private boolean jmxEnabled = DEFAULT_JMX_ENABLE;

    // TODO Consider changing this to a single property for 3.x
    private String jmxNamePrefix = DEFAULT_JMX_NAME_PREFIX;

    private String jmxNameBase = DEFAULT_JMX_NAME_BASE;


    /**
     * 获取使用此配置实例创建的池的{@code lifo}配置属性的值.
     */
    public boolean getLifo() {
        return lifo;
    }

    /**
     * 获取使用此配置实例创建的池的{@code fairness}配置属性的值.
     */
    public boolean getFairness() {
        return fairness;
    }

    /**
     * 设置使用此配置实例创建的池的{@code lifo}配置属性的值.
     *
     * @param lifo
     */
    public void setLifo(final boolean lifo) {
        this.lifo = lifo;
    }

    /**
     * 设置使用此配置实例创建的池的{@code fairness}配置属性的值.
     *
     * @param fairness
     */
    public void setFairness(final boolean fairness) {
        this.fairness = fairness;
    }

    /**
     * 获取使用此配置实例创建的池的{@code maxWait}配置属性的值.
     */
    public long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    /**
     * 设置使用此配置实例创建的池的{@code maxWait}配置属性的值.
     *
     * @param maxWaitMillis
     */
    public void setMaxWaitMillis(final long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }

    /**
     * 获取使用此配置实例创建的池的{@code minEvictableIdleTimeMillis}配置属性的值.
     */
    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    /**
     * 设置使用此配置实例创建的池的{@code minEvictableIdleTimeMillis}配置属性的值.
     *
     * @param minEvictableIdleTimeMillis
     */
    public void setMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * 获取使用此配置实例创建的池的{@code softMinEvictableIdleTimeMillis}配置属性的值.
     */
    public long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleTimeMillis;
    }

    /**
     * 设置使用此配置实例创建的池的{@code softMinEvictableIdleTimeMillis}配置属性的值.
     *
     * @param softMinEvictableIdleTimeMillis
     */
    public void setSoftMinEvictableIdleTimeMillis(
            final long softMinEvictableIdleTimeMillis) {
        this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }

    /**
     * 获取使用此配置实例创建的池的{@code numTestsPerEvictionRun}配置属性的值.
     */
    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    /**
     * 设置使用此配置实例创建的池的{@code numTestsPerEvictionRun}配置属性的值.
     *
     * @param numTestsPerEvictionRun
     */
    public void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * 获取使用此配置实例创建的池的{@code testOnCreate}配置属性的值.
     */
    public boolean getTestOnCreate() {
        return testOnCreate;
    }

    /**
     * 设置使用此配置实例创建的池的{@code testOnCreate}配置属性的值.
     *
     * @param testOnCreate
     */
    public void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
    }

    /**
     * 获取使用此配置实例创建的池的{@code testOnBorrow}配置属性的值.
     */
    public boolean getTestOnBorrow() {
        return testOnBorrow;
    }

    /**
     * 设置使用此配置实例创建的池的{@code testOnBorrow}配置属性的值.
     *
     * @param testOnBorrow
     */
    public void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    /**
     * 获取使用此配置实例创建的池的{@code testOnReturn}配置属性的值.
     */
    public boolean getTestOnReturn() {
        return testOnReturn;
    }

    /**
     * 设置使用此配置实例创建的池的{@code testOnReturn}配置属性的值.
     *
     * @param testOnReturn
     */
    public void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    /**
     * 获取使用此配置实例创建的池的{@code testWhileIdle}配置属性的值.
     */
    public boolean getTestWhileIdle() {
        return testWhileIdle;
    }

    /**
     * 设置使用此配置实例创建的池的{@code testWhileIdle}配置属性的值.
     *
     * @param testWhileIdle
     */
    public void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    /**
     * 获取使用此配置实例创建的池的{@code timeBetweenEvictionRunsMillis}配置属性的值.
     */
    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    /**
     * 设置使用此配置实例创建的池的{@code timeBetweenEvictionRunsMillis}配置属性的值.
     *
     * @param timeBetweenEvictionRunsMillis
     */
    public void setTimeBetweenEvictionRunsMillis(
            final long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    /**
     * 获取使用此配置实例创建的池的{@code evictionPolicyClassName}配置属性的值.
     */
    public String getEvictionPolicyClassName() {
        return evictionPolicyClassName;
    }

    /**
     * 设置使用此配置实例创建的池的{@code evictionPolicyClassName}配置属性的值.
     *
     * @param evictionPolicyClassName
     */
    public void setEvictionPolicyClassName(final String evictionPolicyClassName) {
        this.evictionPolicyClassName = evictionPolicyClassName;
    }

    /**
     * 获取使用此配置实例创建的池的{@code blockWhenExhausted}配置属性的值.
     */
    public boolean getBlockWhenExhausted() {
        return blockWhenExhausted;
    }

    /**
     * 设置使用此配置实例创建的池的{@code blockWhenExhausted}配置属性的值.
     *
     * @param blockWhenExhausted
     */
    public void setBlockWhenExhausted(final boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
    }

    /**
     * 是否将为使用此配置实例创建的池启用JMX.
     */
    public boolean getJmxEnabled() {
        return jmxEnabled;
    }

    /**
     * 是否将为使用此配置实例创建的池启用JMX.
     *
     * @param jmxEnabled
     */
    public void setJmxEnabled(final boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    /**
     * 获取JMX名称库的值，该名称库将用作分配给使用此配置实例创建的启用JMX的池的名称的一部分.
     * <code>null</code> 表示池将定义JMX名称库.
     */
    public String getJmxNameBase() {
        return jmxNameBase;
    }

    /**
     * 设置JMX名称库的值，该名称库将用作分配给使用此配置实例创建的启用JMX的池的名称的一部分.
     * <code>null</code> 表示池将定义JMX名称库.
     *
     * @param jmxNameBase
     */
    public void setJmxNameBase(final String jmxNameBase) {
        this.jmxNameBase = jmxNameBase;
    }

    /**
     * 获取JMX名称前缀的值，该前缀将用作分配给使用此配置实例创建的启用JMX的池的名称的一部分.
     */
    public String getJmxNamePrefix() {
        return jmxNamePrefix;
    }

    /**
     * 设置JMX名称前缀的值，该前缀将用作分配给使用此配置实例创建的启用JMX的池的名称的一部分.
     *
     * @param jmxNamePrefix
     */
    public void setJmxNamePrefix(final String jmxNamePrefix) {
        this.jmxNamePrefix = jmxNamePrefix;
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("lifo=");
        builder.append(lifo);
        builder.append(", fairness=");
        builder.append(fairness);
        builder.append(", maxWaitMillis=");
        builder.append(maxWaitMillis);
        builder.append(", minEvictableIdleTimeMillis=");
        builder.append(minEvictableIdleTimeMillis);
        builder.append(", softMinEvictableIdleTimeMillis=");
        builder.append(softMinEvictableIdleTimeMillis);
        builder.append(", numTestsPerEvictionRun=");
        builder.append(numTestsPerEvictionRun);
        builder.append(", evictionPolicyClassName=");
        builder.append(evictionPolicyClassName);
        builder.append(", testOnCreate=");
        builder.append(testOnCreate);
        builder.append(", testOnBorrow=");
        builder.append(testOnBorrow);
        builder.append(", testOnReturn=");
        builder.append(testOnReturn);
        builder.append(", testWhileIdle=");
        builder.append(testWhileIdle);
        builder.append(", timeBetweenEvictionRunsMillis=");
        builder.append(timeBetweenEvictionRunsMillis);
        builder.append(", blockWhenExhausted=");
        builder.append(blockWhenExhausted);
        builder.append(", jmxEnabled=");
        builder.append(jmxEnabled);
        builder.append(", jmxNamePrefix=");
        builder.append(jmxNamePrefix);
        builder.append(", jmxNameBase=");
        builder.append(jmxNameBase);
    }
}
