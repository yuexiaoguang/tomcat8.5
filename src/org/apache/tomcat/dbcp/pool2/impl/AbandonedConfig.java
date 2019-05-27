package org.apache.tomcat.dbcp.pool2.impl;

import java.io.PrintWriter;

/**
 * 废弃对象删除的配置.
 */
public class AbandonedConfig {

    /**
     * borrowObject是否执行废弃的对象删除.
     */
    private boolean removeAbandonedOnBorrow = false;

    /**
     * <p>如果在调用borrowObject时超过了removeAbandonedTimeout，则删除已放弃的对象.</p>
     *
     * <p>默认是 false.</p>
     *
     * <p>如果设置为 true, borrowObject删除废弃的对象, 如果池中可用的空闲对象少于2个, 而且 <code>getNumActive() &gt; getMaxTotal() - 3</code></p>
     *
     * @return true 如果要通过borrowObject删除废弃的对象
     */
    public boolean getRemoveAbandonedOnBorrow() {
        return this.removeAbandonedOnBorrow;
    }

    /**
     * <p>如果在调用borrowObject时超过了removeAbandonedTimeout，则删除已放弃的对象.</p>
     *
     * @param removeAbandonedOnBorrow  true:如果要通过borrowObject删除废弃的对象
     */
    public void setRemoveAbandonedOnBorrow(final boolean removeAbandonedOnBorrow) {
        this.removeAbandonedOnBorrow = removeAbandonedOnBorrow;
    }

    /**
     * 池维护（逐出器）是否删除废弃的对象.
     */
    private boolean removeAbandonedOnMaintenance = false;

    /**
     * <p>如果池维护(evictor)运行时间超过了removeAbandonedTimeout，则删除已废弃的对象.</p>
     *
     * <p>默认值是 false.</p>
     *
     * <p>如果设置为 true, 池维护线程将删除废弃的对象.
     * 只有在{@link GenericObjectPool#getTimeBetweenEvictionRunsMillis() timeBetweenEvictionRunsMillis}是正数的时候有效.</p>
     *
     * @return true 如果evictor将删除废弃的对象
     */
    public boolean getRemoveAbandonedOnMaintenance() {
        return this.removeAbandonedOnMaintenance;
    }

    /**
     * <p>如果池维护(evictor)运行时间超过了removeAbandonedTimeout，则删除已废弃的对象.</p>
     *
     * @param removeAbandonedOnMaintenance 如果设置为 true, 池维护线程将删除废弃的对象.
     */
    public void setRemoveAbandonedOnMaintenance(final boolean removeAbandonedOnMaintenance) {
        this.removeAbandonedOnMaintenance = removeAbandonedOnMaintenance;
    }

    /**
     * 可以删除已废弃的对象之前的超时时间（以秒为单位）.
     */
    private int removeAbandonedTimeout = 300;

    /**
     * <p>可以删除已废弃的对象之前的超时时间（以秒为单位）.</p>
     *
     * <p>最近使用对象的时间是{@link org.apache.tomcat.dbcp.pool2.TrackedUse#getLastUsed()}的最大值（最新）
     * (如果此类实现了TrackedUse) 和从池中借用对象的时间.</p>
     *
     * <p>默认是 300 秒.</p>
     *
     * @return 废弃的对象的超时时间, 以秒为单位
     */
    public int getRemoveAbandonedTimeout() {
        return this.removeAbandonedTimeout;
    }

    /**
     * <p>可以删除已废弃的对象之前的超时时间（以秒为单位）</p>
     *
     * <p>如果{@link #getRemoveAbandonedOnBorrow() removeAbandonedOnBorrow} 和
     * {@link #getRemoveAbandonedOnMaintenance() removeAbandonedOnMaintenance}
     * 都是 false, 设置这个属性无效.</p>
     *
     * @param removeAbandonedTimeout 废弃超时时间, 秒
     */
    public void setRemoveAbandonedTimeout(final int removeAbandonedTimeout) {
        this.removeAbandonedTimeout = removeAbandonedTimeout;
    }

    /**
     * 是否记录废弃对象的应用程序代码的堆栈跟踪.
     */
    private boolean logAbandoned = false;

    /**
     * 是否记录废弃对象的应用程序代码的堆栈跟踪.
     *
     * 默认是 false.
     * 记录废弃的对象会增加创建对象的开销，因为必须生成堆栈跟踪.
     *
     * @return true 启用废弃的对象的堆栈跟踪日志记录
     */
    public boolean getLogAbandoned() {
        return this.logAbandoned;
    }

    /**
     * 是否记录废弃对象的应用程序代码的堆栈跟踪.
     *
     * @param logAbandoned  true 启用废弃的对象的堆栈跟踪日志记录
     */
    public void setLogAbandoned(final boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }

    /**
     * 用于记录废弃对象的信息的PrintWriter.
     * 故意使用默认系统编码.
     */
    private PrintWriter logWriter = new PrintWriter(System.out);

    /**
     * 用于记录废弃对象的信息的PrintWriter.
     * 如果未设置, 使用基于System.out的PrintWriter, 并使用系统默认编码.
     */
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    /**
     * 用于记录废弃对象的信息的PrintWriter.
     *
     * @param logWriter
     */
    public void setLogWriter(final PrintWriter logWriter) {
        this.logWriter = logWriter;
    }

    /**
     * 如果池实现了 {@link UsageTracking}, 每次在池中的对象上调用方法时, 池是否应记录堆栈跟踪, 并保留最新的堆栈跟踪以帮助调试已废弃的对象?
     */
    private boolean useUsageTracking = false;

    /**
     * 如果池实现了{@link org.apache.tomcat.dbcp.pool2.UsageTracking}, 每次在池中的对象上调用方法时, 池是否应记录堆栈跟踪, 并保留最新的堆栈跟踪以帮助调试已废弃的对象?
     *
     * @return <code>true</code>如果启用了使用情况跟踪
     */
    public boolean getUseUsageTracking() {
        return useUsageTracking;
    }

    /**
     * 如果池实现了{@link org.apache.tomcat.dbcp.pool2.UsageTracking}, 每次在池中的对象上调用方法时, 池是否应记录堆栈跟踪, 并保留最新的堆栈跟踪以帮助调试已废弃的对象?
     *
     * @param   useUsageTracking    <code>true</code>如果启用了使用情况跟踪
     */
    public void setUseUsageTracking(final boolean useUsageTracking) {
        this.useUsageTracking = useUsageTracking;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AbandonedConfig [removeAbandonedOnBorrow=");
        builder.append(removeAbandonedOnBorrow);
        builder.append(", removeAbandonedOnMaintenance=");
        builder.append(removeAbandonedOnMaintenance);
        builder.append(", removeAbandonedTimeout=");
        builder.append(removeAbandonedTimeout);
        builder.append(", logAbandoned=");
        builder.append(logAbandoned);
        builder.append(", logWriter=");
        builder.append(logWriter);
        builder.append(", useUsageTracking=");
        builder.append(useUsageTracking);
        builder.append("]");
        return builder.toString();
    }
}
