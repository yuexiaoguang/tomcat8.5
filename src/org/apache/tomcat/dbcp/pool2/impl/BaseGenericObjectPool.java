package org.apache.tomcat.dbcp.pool2.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.tomcat.dbcp.pool2.BaseObject;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener;

/**
 * 为{@link GenericObjectPool}和{@link GenericKeyedObjectPool}提供通用功能的基类.
 * 此类存在的主要原因是减少两个池实现之间的代码重复.
 *
 * @param <T> 池中的元素类型.
 *
 * 此类旨在是线程安全的.
 */
public abstract class BaseGenericObjectPool<T> extends BaseObject {

    // Constants
    /**
     * 用于存储某些属性的历史数据的高速缓存的大小, 以便可以计算滚动方式.
     */
    public static final int MEAN_TIMING_STATS_CACHE_SIZE = 100;

    // 配置属性
    private volatile int maxTotal =
            GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;
    private volatile boolean blockWhenExhausted =
            BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED;
    private volatile long maxWaitMillis =
            BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS;
    private volatile boolean lifo = BaseObjectPoolConfig.DEFAULT_LIFO;
    private final boolean fairness;
    private volatile boolean testOnCreate =
            BaseObjectPoolConfig.DEFAULT_TEST_ON_CREATE;
    private volatile boolean testOnBorrow =
            BaseObjectPoolConfig.DEFAULT_TEST_ON_BORROW;
    private volatile boolean testOnReturn =
            BaseObjectPoolConfig.DEFAULT_TEST_ON_RETURN;
    private volatile boolean testWhileIdle =
            BaseObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;
    private volatile long timeBetweenEvictionRunsMillis =
            BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
    private volatile int numTestsPerEvictionRun =
            BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
    private volatile long minEvictableIdleTimeMillis =
            BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private volatile long softMinEvictableIdleTimeMillis =
            BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private volatile EvictionPolicy<T> evictionPolicy;


    // 内部属性（主要是状态）
    final Object closeLock = new Object();
    volatile boolean closed = false;
    final Object evictionLock = new Object();
    private Evictor evictor = null; // @GuardedBy("evictionLock")
    EvictionIterator evictionIterator = null; // @GuardedBy("evictionLock")
    /*
     * 要使用的evictor线程的类加载器，因为在JavaEE或类似环境中，evictor线程的上下文类加载器可能无法看到正确的工厂.
     * See POOL-161. 如果Pool被丢弃而不是关闭，则使用弱引用来避免潜在的内存泄漏.
     */
    private final WeakReference<ClassLoader> factoryClassLoader;


    // 监控属性（主要是JMX）
    private final ObjectName oname;
    private final String creationStackTrace;
    private final AtomicLong borrowedCount = new AtomicLong(0);
    private final AtomicLong returnedCount = new AtomicLong(0);
    final AtomicLong createdCount = new AtomicLong(0);
    final AtomicLong destroyedCount = new AtomicLong(0);
    final AtomicLong destroyedByEvictorCount = new AtomicLong(0);
    final AtomicLong destroyedByBorrowValidationCount = new AtomicLong(0);
    private final StatsStore activeTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);
    private final StatsStore idleTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);
    private final StatsStore waitTimes = new StatsStore(MEAN_TIMING_STATS_CACHE_SIZE);
    private final AtomicLong maxBorrowWaitTimeMillis = new AtomicLong(0L);
    private volatile SwallowedExceptionListener swallowedExceptionListener = null;


    /**
     * @param config        池配置
     * @param jmxNameBase   池的默认基本JMX名称, 除非被配置覆盖
     * @param jmxNamePrefix 用于新池的JMX名称的前缀
     */
    public BaseGenericObjectPool(final BaseObjectPoolConfig config,
            final String jmxNameBase, final String jmxNamePrefix) {
        if (config.getJmxEnabled()) {
            this.oname = jmxRegister(config, jmxNameBase, jmxNamePrefix);
        } else {
            this.oname = null;
        }

        // 填充创建堆栈跟踪
        this.creationStackTrace = getStackTrace(new Exception());

        // 保存当前TCCL以供稍后使用的逐出程序线程使用
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            factoryClassLoader = null;
        } else {
            factoryClassLoader = new WeakReference<>(cl);
        }

        fairness = config.getFairness();
    }


    /**
     * 返回给定时间内, 池可以分配的最大对象数（检出到客户端，或空闲等待检出）.
     * 如果为负数，则池一次可以管理的对象数量没有限制.
     */
    public final int getMaxTotal() {
        return maxTotal;
    }

    /**
     * 设置给定时间内, 池可以分配的最大对象数（检出到客户端，或空闲等待检出）.
     * 如果为负数，则池一次可以管理的对象数量没有限制.
     *
     * @param maxTotal  池上管理的对象实例总数的上限.
     */
    public final void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
    }

    /**
     * 返回池耗尽时调用<code>borrowObject()</code>方法, 是否阻塞 (已达到“活动”对象的最大数量).
     *
     * @return <code>true</code> 如果<code>borrowObject()</code>应该阻塞, 当池耗尽时
     */
    public final boolean getBlockWhenExhausted() {
        return blockWhenExhausted;
    }

    /**
     * 设置池耗尽时调用<code>borrowObject()</code>方法, 是否阻塞 (已达到“活动”对象的最大数量).
     *
     * @param blockWhenExhausted    <code>true</code>如果<code>borrowObject()</code>应该阻塞, 当池耗尽时
     */
    public final void setBlockWhenExhausted(final boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
    }

    /**
     * 返回在池耗尽且{@link #getBlockWhenExhausted}为true时, <code> borrowObject()</ code>方法抛出异常之前应阻塞的最长时间（以毫秒为单位）.
     * 如果小于 0, <code>borrowObject()</code>方法可能会无限期地阻塞.
     *
     * @return <code>borrowObject()</code>将阻塞的最大毫秒数.
     */
    public final long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    /**
     * 返回在池耗尽且{@link #getBlockWhenExhausted}为true时, <code> borrowObject()</ code>方法抛出异常之前应阻塞的最长时间（以毫秒为单位）.
     * 如果小于 0, <code>borrowObject()</code>方法可能会无限期地阻塞.
     *
     * @param maxWaitMillis <code>borrowObject()</code>将阻塞的最大毫秒数, 负值一直阻塞.
     */
    public final void setMaxWaitMillis(final long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }

    /**
     * 返回池是否具有与空闲对象相关的LIFO（后进先出）行为 - 始终从池中返回最近使用的对象, 或者作为FIFO（先进先出）队列, 池始终返回空闲对象池中最旧的对象.
     *
     * @return <code>true</code> 如果池配置了LIFO行为
     *         或<code>false</code> 如果池配置了FIFO行为
     */
    public final boolean getLifo() {
        return lifo;
    }

    /**
     * 返回池是否为等待公平借用对象的线程提供服务.
     * True 表示等待线程被服务就像在FIFO队列中等待一样.
     *
     * @return <code>true</code>如果池按到达顺序服务等待线程
     */
    public final boolean getFairness() {
        return fairness;
    }

    /**
     * 设置池是否具有与空闲对象相关的LIFO（后进先出）行为 - 始终从池中返回最近使用的对象, 或者作为FIFO（先进先出）队列, 池始终返回空闲对象池中最旧的对象.
     *
     * @param lifo  <code>true</code> 如果池配置了LIFO行为; 或<code>false</code>如果池配置了FIFO行为
     */
    public final void setLifo(final boolean lifo) {
        this.lifo = lifo;
    }

    /**
     * 返回是否在<code>borrowObject()</code>方法返回之前验证为池创建的对象.
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 如果对象验证失败, 那么<code>borrowObject()</code>将失败.
     *
     * @return <code>true</code>如果在从<code>borrowObject()</code>方法返回之前验证新创建的对象
     */
    public final boolean getTestOnCreate() {
        return testOnCreate;
    }

    /**
     * 设置是否在<code>borrowObject()</code>方法返回之前验证为池创建的对象.
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 如果对象验证失败, 那么<code>borrowObject()</code>将失败.
     *
     * @param testOnCreate  <code>true</code>如果在<code>borrowObject()</code>方法返回之前验证新创建的对象
     */
    public final void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
    }

    /**
     * 返回从<code>borrowObject()</code>方法返回之前是否将验证从池中借用的对象.
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 如果对象验证失败, 它将被从池中删除并销毁, 并且将尝试从池中借用一个对象.
     *
     * @return <code>true</code> 如果在<code>borrowObject()</code>方法返回之前验证对象
     */
    public final boolean getTestOnBorrow() {
        return testOnBorrow;
    }

    /**
     * 返回从<code>borrowObject()</code>方法返回之前是否将验证从池中借用的对象.
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 如果对象验证失败, 它将被从池中删除并销毁, 并且将尝试从池中借用一个对象.
     *
     * @param testOnBorrow  <code>true</code> 如果在<code>borrowObject()</code>方法返回之前验证对象
     */
    public final void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    /**
     * 返回通过<code>returnObject()</code>方法返回从池中借用的对象到池中时, 是否会被验证.
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 验证失败的返回对象将被销毁，而不是返回池.
     *
     * @return <code>true</code> 如果通过<code>returnObject()</code>方法返回到池中时验证对象
     */
    public final boolean getTestOnReturn() {
        return testOnReturn;
    }

    /**
     * 返回通过<code>returnObject()</code>方法返回从池中借用的对象到池中时, 是否会被验证.
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 验证失败的返回对象将被销毁，而不是返回池.
     *
     * @param testOnReturn <code>true</code> 如果通过<code>returnObject()</code>方法返回到池中时验证对象
     */
    public final void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    /**
     * 返回空闲对象逐出器是否将验证池中空闲的对象是否有效 (see {@link #setTimeBetweenEvictionRunsMillis(long)}).
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 如果对象验证失败, 它将被从池中删除并销毁.
     *
     * @return <code>true</code> 如果对象将由逐出器验证
     */
    public final boolean getTestWhileIdle() {
        return testWhileIdle;
    }

    /**
     * 返回空闲对象逐出器是否将验证池中空闲的对象是否有效 (see {@link #setTimeBetweenEvictionRunsMillis(long)}).
     * 验证由与池关联的工厂的<code>validateObject()</code>方法执行. 如果对象验证失败, 它将被从池中删除并销毁.
     * 请注意，除非通过将<code>timeBetweenEvictionRunsMillis</code>设置为正值来启用空闲对象逐出器，否则设置此属性无效.
     *
     * @param testWhileIdle  <code>true</code>所以对象将由逐出器验证
     */
    public final void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    /**
     * 返回空闲对象逐出器线程运行间隔之间休眠的毫秒数. 如果非正值, 不会运行空闲对象逐出器线程.
     *
     * @return 逐出器线程运行间隔之间休眠的毫秒数
     */
    public final long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    /**
     * 设置空闲对象逐出器线程运行间隔之间休眠的毫秒数. 如果非正值, 不会运行空闲对象逐出器线程.
     *
     * @param timeBetweenEvictionRunsMillis  逐出器线程运行间隔之间休眠的毫秒数
     */
    public final void setTimeBetweenEvictionRunsMillis(
            final long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        startEvictor(timeBetweenEvictionRunsMillis);
    }

    /**
     * 返回每次运行空闲对象逐出器线程时要检查的最大对象数.
     * 如果是正数, 为运行执行的测试数量将是配置值和池中的空闲实例数两者之一的最小值.
     * 如果是负数, 执行的测试次数为<code>ceil({@ link #getNumIdle}/abs({@ link #getNumTestsPerEvictionRun}))</code>,
     * 这意味着当值为<code>-n</code>时, 每次运行将测试一个空闲对象n次.
     *
     * @return 每个逐出器运行期间要检查的最大对象数
     */
    public final int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    /**
     * 设置每次运行空闲对象逐出器线程时要检查的最大对象数.
     * 如果是正数, 为运行执行的测试数量将是配置值和池中的空闲实例数两者之一的最小值.
     * 如果是负数, 执行的测试次数为<code>ceil({@ link #getNumIdle}/abs({@ link #getNumTestsPerEvictionRun}))</code>,
     * 这意味着当值为<code>-n</code>时, 每次运行将测试一个空闲对象n次.
     *
     * @param numTestsPerEvictionRun 每个逐出器运行期间要检查的最大对象数
     */
    public final void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * 返回对象在符合空闲对象逐出器驱逐策略之前可能在池中空闲的最短时间 ( see {@link #setTimeBetweenEvictionRunsMillis(long)}).
     * 如果为负数, 由于空闲时间, 不会从池中驱逐任何对象.
     *
     * @return 在符合驱逐策略之前，对象可能在池中闲置的最短时间
     */
    public final long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    /**
     * 返回对象在符合空闲对象逐出器驱逐策略之前可能在池中空闲的最短时间 ( see {@link #setTimeBetweenEvictionRunsMillis(long)}).
     * 如果为负数, 由于空闲时间, 不会从池中驱逐任何对象.
     *
     * @param minEvictableIdleTimeMillis 在符合驱逐策略之前，对象可能在池中闲置的最短时间
     */
    public final void setMinEvictableIdleTimeMillis(
            final long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * 返回对象在符合空闲对象逐出器驱逐策略之前可能在池中空闲的最短时间 ( see {@link #setTimeBetweenEvictionRunsMillis(long)}),
     * 条件是至少<code>minIdle</code>个对象实例保留在池中.
     * 这个设置由{@link #getMinEvictableIdleTimeMillis}重写 (即, 如果{@link #getMinEvictableIdleTimeMillis}是正数,
     * 那么将忽略 {@link #getSoftMinEvictableIdleTimeMillis}).
     *
     * @return 如果minIdle个实例可用, 则对象在符合驱逐资格之前可以在池中闲置的最短时间
     */
    public final long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleTimeMillis;
    }

    /**
     * 返回对象在符合空闲对象逐出器驱逐策略之前可能在池中空闲的最短时间 ( see {@link #setTimeBetweenEvictionRunsMillis(long)}),
     * 条件是至少<code>minIdle</code>个对象实例保留在池中.
     * 这个设置由{@link #getMinEvictableIdleTimeMillis}重写 (即, 如果{@link #getMinEvictableIdleTimeMillis}是正数,
     * 那么将忽略 {@link #getSoftMinEvictableIdleTimeMillis}).
     *
     * @param softMinEvictableIdleTimeMillis 如果minIdle个实例可用, 则对象在符合驱逐资格之前可以在池中闲置的最短时间
     */
    public final void setSoftMinEvictableIdleTimeMillis(
            final long softMinEvictableIdleTimeMillis) {
        this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }

    /**
     * 返回此池使用的{@link EvictionPolicy}实现的名称.
     *
     * @return {@link EvictionPolicy}的完全限定类名
     */
    public final String getEvictionPolicyClassName() {
        return evictionPolicy.getClass().getName();
    }

    /**
     * 设置此池使用的{@link EvictionPolicy}实现的名称.
     * Pool将尝试使用线程上下文类加载器加载类. 如果失败, Pool将尝试使用加载此类的类加载器加载类.
     *
     * @param evictionPolicyClassName  新驱逐策略的完全限定类名
     */
    public final void setEvictionPolicyClassName(final String evictionPolicyClassName) {
        try {
            Class<?> clazz;
            try {
                clazz = Class.forName(evictionPolicyClassName, true,
                        Thread.currentThread().getContextClassLoader());
            } catch (final ClassNotFoundException e) {
                clazz = Class.forName(evictionPolicyClassName);
            }
            final Object policy = clazz.getConstructor().newInstance();
            if (policy instanceof EvictionPolicy<?>) {
                @SuppressWarnings("unchecked") // safe, because we just checked the class
                final
                EvictionPolicy<T> evicPolicy = (EvictionPolicy<T>) policy;
                this.evictionPolicy = evicPolicy;
            }
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Unable to create EvictionPolicy instance of type " +
                    evictionPolicyClassName, e);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Unable to create EvictionPolicy instance of type " +
                    evictionPolicyClassName, e);
        }
    }


    /**
     * 关闭池, 销毁剩余的空闲对象, 如果注册进了 JMX, 注销它.
     */
    public abstract void close();

    /**
     * 池是否已关闭.
     * @return <code>true</code>池已关闭.
     */
    public final boolean isClosed() {
        return closed;
    }

    /**
     * <p>执行<code>numTests</code>次空闲对象驱逐测试，驱逐符合驱逐标准的被检查对象.
     * 如果<code>testWhileIdle</code>是 true, 检查对象在访问时进行验证 (如果无效将删除);
     * 否则只删除空闲时间超过<code>minEvicableIdleTimeMillis</code>的对象.</p>
     *
     * @throws Exception 当驱逐空闲对象时出现问题.
     */
    public abstract void evict() throws Exception;

    /**
     * 返回为此池定义的{@link EvictionPolicy}.
     */
    protected EvictionPolicy<T> getEvictionPolicy() {
        return evictionPolicy;
    }

    /**
     * 验证池是否已打开.
     * @throws IllegalStateException 如果池关闭.
     */
    final void assertOpen() throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    /**
     * <p>以给定的延迟启动逐出器. 如果在调用此方法时运行了逐出器, 它被停止并替换为具有指定延迟的新逐出器.</p>
     *
     * <p>这个方法需要是 final, 因为它从一个构造器调用. See POOL-195.</p>
     *
     * @param delay 开始前和运行之间的时间（以毫秒为单位）
     */
    final void startEvictor(final long delay) {
        synchronized (evictionLock) {
            if (null != evictor) {
                EvictionTimer.cancel(evictor);
                evictor = null;
                evictionIterator = null;
            }
            if (delay > 0) {
                evictor = new Evictor();
                EvictionTimer.schedule(evictor, delay, delay);
            }
        }
    }

    /**
     * 尝试确保池中可用的已配置的最小空闲实例数.
     * 
     * @throws Exception 如果创建空闲实例时发生错误
     */
    abstract void ensureMinIdle() throws Exception;


    // Monitoring (primarily JMX) related methods

    /**
     * 提供池已在平台MBean服务器上注册的名称; 如果池尚未注册，则为<code>null</code>.
     * 
     * @return JMX 名称
     */
    public final ObjectName getJmxName() {
        return oname;
    }

    /**
     * 为创建此池的调用提供堆栈跟踪.
     * JMX注册可能会触发内存泄漏，因此通过调用{@link #close()}方法在不再使用池时取消注册是很重要的.
     * 提供此方法是为了帮助识别创建但不关闭它的代码，从而产生内存泄漏.
     * 
     * @return 池创建堆栈跟踪
     */
    public final String getCreationStackTrace() {
        return creationStackTrace;
    }

    /**
     * 在池的生命周期内, 从此池成功借用的对象总数.
     */
    public final long getBorrowedCount() {
        return borrowedCount.get();
    }

    /**
     * 在池的生命周期内, 返回到此池的对象总数. 排除了多次返回同一对象.
     */
    public final long getReturnedCount() {
        return returnedCount.get();
    }

    /**
     * 在池的生命周期内, 为此池创建的对象总数.
     */
    public final long getCreatedCount() {
        return createdCount.get();
    }

    /**
     * 在池的生命周期内, 销毁的对象总数.
     */
    public final long getDestroyedCount() {
        return destroyedCount.get();
    }

    /**
     * 在池的生命周期内, 与此池关联的逐出器销毁的对象总数.
     */
    public final long getDestroyedByEvictorCount() {
        return destroyedByEvictorCount.get();
    }

    /**
     * 在池的生命周期内<code>borrowObject()</code>期间, 由于验证失败而导致此池损坏的对象总数.
     */
    public final long getDestroyedByBorrowValidationCount() {
        return destroyedByBorrowValidationCount.get();
    }

    /**
     * @return 在最近返回的对象中从池中检出对象的平均时间
     */
    public final long getMeanActiveTimeMillis() {
        return activeTimes.getMean();
    }

    /**
     * @return 在最近借用的对象中，对象在池中空闲的平均时间
     */
    public final long getMeanIdleTimeMillis() {
        return idleTimes.getMean();
    }

    /**
     * @return 最近服务的线程从池中借用对象必须等待的平均时间（以毫秒为单位）
     */
    public final long getMeanBorrowWaitTimeMillis() {
        return waitTimes.getMean();
    }

    /**
     * 线程从池中借用对象等待的最长时间.
     * @return 自创建池以来的最长等待时间（以毫秒为单位）
     */
    public final long getMaxBorrowWaitTimeMillis() {
        return maxBorrowWaitTimeMillis.get();
    }

    /**
     * 此池中当前空闲的实例数.
     * @return 可从池中检出的实例数
     */
    public abstract int getNumIdle();

    /**
     * 用于接收池忽略的异常的监听器.
     *
     * @return 监听器或<code>null</code>
     */
    public final SwallowedExceptionListener getSwallowedExceptionListener() {
        return swallowedExceptionListener;
    }

    /**
     * 用于接收池忽略的异常的监听器..
     *
     * @param swallowedExceptionListener    监听器或<code>null</code>
     */
    public final void setSwallowedExceptionListener(final SwallowedExceptionListener swallowedExceptionListener) {
        this.swallowedExceptionListener = swallowedExceptionListener;
    }

    /**
     * 忽略异常并通知已配置的监听器忽略的异常队列.
     *
     * @param e 要忽略的异常
     */
    final void swallowException(final Exception e) {
        final SwallowedExceptionListener listener = getSwallowedExceptionListener();

        if (listener == null) {
            return;
        }

        try {
            listener.onSwallowException(e);
        } catch (final OutOfMemoryError oome) {
            throw oome;
        } catch (final VirtualMachineError vme) {
            throw vme;
        } catch (final Throwable t) {
            // Ignore. Enjoy the irony.
        }
    }

    /**
     * 从池中借用对象后更新统计信息.
     * @param p 要从池中借用的对象
     * @param waitTime 借用线程必须等待的时间（以毫秒为单位）
     */
    final void updateStatsBorrow(final PooledObject<T> p, final long waitTime) {
        borrowedCount.incrementAndGet();
        idleTimes.add(p.getIdleTimeMillis());
        waitTimes.add(waitTime);

        // 无锁乐观锁定最大值
        long currentMax;
        do {
            currentMax = maxBorrowWaitTimeMillis.get();
            if (currentMax >= waitTime) {
                break;
            }
        } while (!maxBorrowWaitTimeMillis.compareAndSet(currentMax, waitTime));
    }

    /**
     * 将对象返回到池后更新统计信息.
     * @param activeTime 检出返回对象的时间（以毫秒为单位）
     */
    final void updateStatsReturn(final long activeTime) {
        returnedCount.incrementAndGet();
        activeTimes.add(activeTime);
    }

    /**
     * 注销这个池的 MBean.
     */
    final void jmxUnregister() {
        if (oname != null) {
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(
                        oname);
            } catch (final MBeanRegistrationException e) {
                swallowException(e);
            } catch (final InstanceNotFoundException e) {
                swallowException(e);
            }
        }
    }

    /**
     * 使用平台MBean服务器注册池.
     * 注册的名字将是
     * <code>jmxNameBase + jmxNamePrefix + i</code>, i 是大于或等于1的最小整数，因此名称尚未注册.
     * 忽略 MBeanRegistrationException, NotCompliantMBeanException 返回 null.
     *
     * @param config Pool配置
     * @param jmxNameBase 此池的默认基本JMX名称
     * @param jmxNamePrefix 名称前缀
     * 
     * @return 注册的 ObjectName, 如果注册失败为 null
     */
    private ObjectName jmxRegister(final BaseObjectPoolConfig config,
            final String jmxNameBase, String jmxNamePrefix) {
        ObjectName objectName = null;
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        int i = 1;
        boolean registered = false;
        String base = config.getJmxNameBase();
        if (base == null) {
            base = jmxNameBase;
        }
        while (!registered) {
            try {
                ObjectName objName;
                // 如果只有一个，请跳过第一个池的数字后缀，以便名称更清晰.
                if (i == 1) {
                    objName = new ObjectName(base + jmxNamePrefix);
                } else {
                    objName = new ObjectName(base + jmxNamePrefix + i);
                }
                mbs.registerMBean(this, objName);
                objectName = objName;
                registered = true;
            } catch (final MalformedObjectNameException e) {
                if (BaseObjectPoolConfig.DEFAULT_JMX_NAME_PREFIX.equals(
                        jmxNamePrefix) && jmxNameBase.equals(base)) {
                    // 不应该发生. 如果有，请跳过注册.
                    registered = true;
                } else {
                    // 必须是无效的名称. 请改用默认值.
                    jmxNamePrefix =
                            BaseObjectPoolConfig.DEFAULT_JMX_NAME_PREFIX;
                    base = jmxNameBase;
                }
            } catch (final InstanceAlreadyExistsException e) {
                // 重试
                i++;
            } catch (final MBeanRegistrationException e) {
                // 不应该发生. 如果有，请跳过注册.
                registered = true;
            } catch (final NotCompliantMBeanException e) {
                // 不应该发生. 如果有，请跳过注册.
                registered = true;
            }
        }
        return objectName;
    }

    /**
     * 以字符串形式获取异常的堆栈跟踪.
     * 
     * @param e 跟踪的异常
     * @return exception 跟踪堆栈
     */
    private String getStackTrace(final Exception e) {
        // 需要以字符串形式的异常, 来防止保留对堆栈跟踪中可能触发容器环境中的内存泄漏的类的引用.
        final Writer w = new StringWriter();
        final PrintWriter pw = new PrintWriter(w);
        e.printStackTrace(pw);
        return w.toString();
    }

    // Inner classes

    /**
     * 空闲对象逐出器 {@link TimerTask}.
     */
    class Evictor extends TimerTask {
        /**
         * 运行池维护.  Evict对象符合驱逐条件，然后确保可用的最小空闲实例数.
         * 由于调用Evictors的Timer是为所有池共享的，但池可能存在于不同的类加载器中, Evictor确保所采取的任何操作都在与池关联的工厂的类加载器下.
         */
        @Override
        public void run() {
            final ClassLoader savedClassLoader =
                    Thread.currentThread().getContextClassLoader();
            try {
                if (factoryClassLoader != null) {
                    // 设置工厂的类加载器
                    final ClassLoader cl = factoryClassLoader.get();
                    if (cl == null) {
                        // 该池已被解除引用并且类加载器已被 GC 回收. 取消此计时器，以便池也可以GC.
                        cancel();
                        return;
                    }
                    Thread.currentThread().setContextClassLoader(cl);
                }

                // 从池逐出
                try {
                    evict();
                } catch(final Exception e) {
                    swallowException(e);
                } catch(final OutOfMemoryError oome) {
                    // 记录问题，但是如果错误可以恢复，则给予evictor线程继续的机会
                    oome.printStackTrace(System.err);
                }
                // 重新创建空闲实例.
                try {
                    ensureMinIdle();
                } catch (final Exception e) {
                    swallowException(e);
                }
            } finally {
                // 恢复以前的CCL
                Thread.currentThread().setContextClassLoader(savedClassLoader);
            }
        }
    }

    /**
     * 维护值缓存，并报告缓存值的统计信息.
     */
    private class StatsStore {

        private final AtomicLong values[];
        private final int size;
        private int index;

        /**
         * @param size 要在缓存中维护的值的数量.
         */
        public StatsStore(final int size) {
            this.size = size;
            values = new AtomicLong[size];
            for (int i = 0; i < size; i++) {
                values[i] = new AtomicLong(-1);
            }
        }

        /**
         * 向缓存添加值. 如果缓存满了, 其中一个现有值被新值替换.
         *
         * @param value 要添加到缓存的新值.
         */
        public synchronized void add(final long value) {
            values[index].set(value);
            index++;
            if (index == size) {
                index = 0;
            }
        }

        /**
         * 返回缓存值的平均值.
         */
        public long getMean() {
            double result = 0;
            int counter = 0;
            for (int i = 0; i < size; i++) {
                final long value = values[i].get();
                if (value != -1) {
                    counter++;
                    result = result * ((counter - 1) / (double) counter) +
                            value/(double) counter;
                }
            }
            return (long) result;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("StatsStore [values=");
            builder.append(Arrays.toString(values));
            builder.append(", size=");
            builder.append(size);
            builder.append(", index=");
            builder.append(index);
            builder.append("]");
            return builder.toString();
        }
    }

    /**
     * 空闲对象逐出迭代器. 保存对空闲对象的引用.
     */
    class EvictionIterator implements Iterator<PooledObject<T>> {

        private final Deque<PooledObject<T>> idleObjects;
        private final Iterator<PooledObject<T>> idleObjectIterator;

        /**
         * @param idleObjects 底层的双端队列
         */
        EvictionIterator(final Deque<PooledObject<T>> idleObjects) {
            this.idleObjects = idleObjects;

            if (getLifo()) {
                idleObjectIterator = idleObjects.descendingIterator();
            } else {
                idleObjectIterator = idleObjects.iterator();
            }
        }

        /**
         * 返回此迭代器引用的空闲对象双端队列.
         */
        public Deque<PooledObject<T>> getIdleObjects() {
            return idleObjects;
        }

        /** {@inheritDoc} */
        @Override
        public boolean hasNext() {
            return idleObjectIterator.hasNext();
        }

        /** {@inheritDoc} */
        @Override
        public PooledObject<T> next() {
            return idleObjectIterator.next();
        }

        /** {@inheritDoc} */
        @Override
        public void remove() {
            idleObjectIterator.remove();
        }

    }

    /**
     * 池的管理对象的包装器.
     *
     * GenericObjectPool和GenericKeyedObjectPool使用 Map 维护对管理下的所有对象的引用. 此包装类确保对象可以作为哈希键.
     *
     * @param <T> 池中的对象类型
     */
    static class IdentityWrapper<T> {
        /** 封装的对象 */
        private final T instance;

        /**
         * @param instance 要封装的对象
         */
        public IdentityWrapper(final T instance) {
            this.instance = instance;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(instance);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean equals(final Object other) {
            return  other instanceof IdentityWrapper &&
                    ((IdentityWrapper) other).instance == instance;
        }

        /**
         * @return 封装的对象
         */
        public T getObject() {
            return instance;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("IdentityWrapper [instance=");
            builder.append(instance);
            builder.append("]");
            return builder.toString();
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("maxTotal=");
        builder.append(maxTotal);
        builder.append(", blockWhenExhausted=");
        builder.append(blockWhenExhausted);
        builder.append(", maxWaitMillis=");
        builder.append(maxWaitMillis);
        builder.append(", lifo=");
        builder.append(lifo);
        builder.append(", fairness=");
        builder.append(fairness);
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
        builder.append(", numTestsPerEvictionRun=");
        builder.append(numTestsPerEvictionRun);
        builder.append(", minEvictableIdleTimeMillis=");
        builder.append(minEvictableIdleTimeMillis);
        builder.append(", softMinEvictableIdleTimeMillis=");
        builder.append(softMinEvictableIdleTimeMillis);
        builder.append(", evictionPolicy=");
        builder.append(evictionPolicy);
        builder.append(", closeLock=");
        builder.append(closeLock);
        builder.append(", closed=");
        builder.append(closed);
        builder.append(", evictionLock=");
        builder.append(evictionLock);
        builder.append(", evictor=");
        builder.append(evictor);
        builder.append(", evictionIterator=");
        builder.append(evictionIterator);
        builder.append(", factoryClassLoader=");
        builder.append(factoryClassLoader);
        builder.append(", oname=");
        builder.append(oname);
        builder.append(", creationStackTrace=");
        builder.append(creationStackTrace);
        builder.append(", borrowedCount=");
        builder.append(borrowedCount);
        builder.append(", returnedCount=");
        builder.append(returnedCount);
        builder.append(", createdCount=");
        builder.append(createdCount);
        builder.append(", destroyedCount=");
        builder.append(destroyedCount);
        builder.append(", destroyedByEvictorCount=");
        builder.append(destroyedByEvictorCount);
        builder.append(", destroyedByBorrowValidationCount=");
        builder.append(destroyedByBorrowValidationCount);
        builder.append(", activeTimes=");
        builder.append(activeTimes);
        builder.append(", idleTimes=");
        builder.append(idleTimes);
        builder.append(", waitTimes=");
        builder.append(waitTimes);
        builder.append(", maxBorrowWaitTimeMillis=");
        builder.append(maxBorrowWaitTimeMillis);
        builder.append(", swallowedExceptionListener=");
        builder.append(swallowedExceptionListener);
    }
}
