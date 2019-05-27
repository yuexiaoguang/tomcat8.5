package org.apache.tomcat.dbcp.pool2.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PoolUtils;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObjectState;
import org.apache.tomcat.dbcp.pool2.UsageTracking;

/**
 * 可配置的{@link ObjectPool}实现.
 * <p>
 * 与适当的{@link PooledObjectFactory}结合使用时,
 * <code>GenericObjectPool</code> 为任意对象提供强大的池功能.</p>
 * <p>
 * 可选地，可以将池配置为在池中空闲时检查并可能逐出对象，并确保最小数量的空闲对象可用. 这是由“空闲对象驱逐”线程执行的, 它以异步方式运行.
 * 配置此可选功能时应小心. 驱逐线程与客户端线程竞争访问池中的对象, 因此如果它们运行得太频繁, 可能会导致性能问题.</p>
 * <p>
 * 池还可以配置为检测和删除“废弃”的对象,
 * 即已经从池中检出但在配置的{@link AbandonedConfig#getRemoveAbandonedTimeout() removeAbandonedTimeout}之前既未使用也未返回的对象.
 * 可以将删除废弃的对象配置为, 当调用 <code>borrowObject</code> 且池接近饥饿时执行, 或者可以由空闲对象逐出器执行，或者两者都执行.
 * 如果池中的对象实现了 {@link org.apache.tomcat.dbcp.pool2.TrackedUse} 接口, 将使用该接口上的<code>getLastUsed</code>方法查询它们的最后一次使用时间;
 * 否则是否废弃取决于从池中检出对象的时间.</p>
 * <p>
 * 实现注意: 防止可能的死锁, 已经注意确保在同步块内不会发生对工厂方法的调用. See POOL-125 and DBCP-44 for more information.</p>
 * <p>
 * 此类旨在是线程安全的.</p>
 *
 * @param <T> 此池中的元素类型.
 */
public class GenericObjectPool<T> extends BaseGenericObjectPool<T>
        implements ObjectPool<T>, GenericObjectPoolMXBean, UsageTracking<T> {

    /**
     * @param factory 用于创建此池使用的对象实例的对象工厂
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory) {
        this(factory, new GenericObjectPoolConfig());
    }

    /**
     * @param factory   用于创建此池使用的对象实例的对象工厂
     * @param config    用于此池实例的配置. 配置按值使用. 对配置对象的后续更改不会反映在池中.
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory,
            final GenericObjectPoolConfig config) {

        super(config, ONAME_BASE, config.getJmxNamePrefix());

        if (factory == null) {
            jmxUnregister(); // tidy up
            throw new IllegalArgumentException("factory may not be null");
        }
        this.factory = factory;

        idleObjects = new LinkedBlockingDeque<>(config.getFairness());

        setConfig(config);

        startEvictor(getTimeBetweenEvictionRunsMillis());
    }

    /**
     * @param factory   用于创建此池使用的对象实例的对象工厂
     * @param config    用于此池实例的配置. 配置按值使用. 对配置对象的后续更改不会反映在池中.
     * @param abandonedConfig  废弃的对象的识别和删除的配置.  配置按值使用.
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory,
            final GenericObjectPoolConfig config, final AbandonedConfig abandonedConfig) {
        this(factory, config);
        setAbandonedConfig(abandonedConfig);
    }

    /**
     * 返回池中“空闲”实例数量的上限. 如果没有限制，则为负值.
     * 如果在负载很重的系统上将maxIdle设置得太低，则可能会看到对象被销毁，并且几乎立即创建了新对象.
     * 这是活动线程暂时返回对象的速度超过请求它们的速度的结果，导致空闲对象的数量超过maxIdle.
     * 对于负载较重的系统，maxIdle的最佳值会有所不同，但默认值是一个很好的起点.
     */
    @Override
    public int getMaxIdle() {
        return maxIdle;
    }

    /**
     * 设置池中“空闲”实例数量的上限. 如果没有限制，则为负值.
     * 如果在负载很重的系统上将maxIdle设置得太低，则可能会看到对象被销毁，并且几乎立即创建了新对象.
     * 这是活动线程暂时返回对象的速度超过请求它们的速度的结果，导致空闲对象的数量超过maxIdle.
     * 对于负载较重的系统，maxIdle的最佳值会有所不同，但默认值是一个很好的起点.
     *
     * @param maxIdle 池中“空闲”实例数量的上限. 如果没有限制，则为负值.
     */
    public void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
    }

    /**
     * 设置要在池中维护的最小空闲对象数量.
     * 如果此设置为正数, 且{@link #getTimeBetweenEvictionRunsMillis()}大于零，则此设置才起作用.
     * 如果是这样的话, 确保池在空闲对象驱逐运行期间具有所需的最小实例数.
     * <p>
     * 如果minIdle的配置值大于maxIdle的配置值，则将使用maxIdle的值.
     *
     * @param minIdle 对象的最小数量.
     */
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    /**
     * 返回要在池中维护的最小空闲对象数量.
     * 如果此设置为正数, 且{@link #getTimeBetweenEvictionRunsMillis()}大于零，则此设置才起作用.
     * 如果是这样的话, 确保池在空闲对象驱逐运行期间具有所需的最小实例数.
     * <p>
     * 如果minIdle的配置值大于maxIdle的配置值，则将使用maxIdle的值.
     *
     * @return 对象的最小数量.
     */
    @Override
    public int getMinIdle() {
        final int maxIdleSave = getMaxIdle();
        if (this.minIdle > maxIdleSave) {
            return maxIdleSave;
        }
        return minIdle;
    }

    /**
     * 是否为此池配置了删除废弃的对象.
     *
     * @return true 如果此池配置为检测并删除废弃的对象
     */
    @Override
    public boolean isAbandonedConfig() {
        return abandonedConfig != null;
    }

    /**
     * 此池是否会识别并记录任何已废弃的对象?
     *
     * @return {@code true} 如果为此池配置了删除废弃的对象，则要记录删除事件; 否则 {@code false}
     */
    @Override
    public boolean getLogAbandoned() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getLogAbandoned();
    }

    /**
     * 当从该池借用一个对象时，是否会检查废弃的对象?
     *
     * @return {@code true}如果删除废弃的对象配置为由borrowObject激活; 否则 {@code false}
     */
    @Override
    public boolean getRemoveAbandonedOnBorrow() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnBorrow();
    }

    /**
     * 当逐出器运行时，是否会检查废弃的对象?
     *
     * @return {@code true} 如果删除废弃的对象配置为在逐出器运行时激活; 否则 {@code false}
     */
    @Override
    public boolean getRemoveAbandonedOnMaintenance() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnMaintenance();
    }

    /**
     * 获取超时时间，在该超时时间之前，该池将认为该对象被废弃.
     *
     * @return 如果为此池配置了删除废弃的对象，则废弃的对象的超时时间（以秒为单位）; 否则 Integer.MAX_VALUE.
     */
    @Override
    public int getRemoveAbandonedTimeout() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null ? ac.getRemoveAbandonedTimeout() : Integer.MAX_VALUE;
    }


    /**
     * 设置基本池配置.
     *
     * @param conf 要使用的新配置.
     */
    public void setConfig(final GenericObjectPoolConfig conf) {
        setLifo(conf.getLifo());
        setMaxIdle(conf.getMaxIdle());
        setMinIdle(conf.getMinIdle());
        setMaxTotal(conf.getMaxTotal());
        setMaxWaitMillis(conf.getMaxWaitMillis());
        setBlockWhenExhausted(conf.getBlockWhenExhausted());
        setTestOnCreate(conf.getTestOnCreate());
        setTestOnBorrow(conf.getTestOnBorrow());
        setTestOnReturn(conf.getTestOnReturn());
        setTestWhileIdle(conf.getTestWhileIdle());
        setNumTestsPerEvictionRun(conf.getNumTestsPerEvictionRun());
        setMinEvictableIdleTimeMillis(conf.getMinEvictableIdleTimeMillis());
        setTimeBetweenEvictionRunsMillis(
                conf.getTimeBetweenEvictionRunsMillis());
        setSoftMinEvictableIdleTimeMillis(
                conf.getSoftMinEvictableIdleTimeMillis());
        setEvictionPolicyClassName(conf.getEvictionPolicyClassName());
    }

    /**
     * 设置删除废弃的对象配置.
     *
     * @param abandonedConfig 要使用的新配置.
     */
    public void setAbandonedConfig(final AbandonedConfig abandonedConfig) {
        if (abandonedConfig == null) {
            this.abandonedConfig = null;
        } else {
            this.abandonedConfig = new AbandonedConfig();
            this.abandonedConfig.setLogAbandoned(abandonedConfig.getLogAbandoned());
            this.abandonedConfig.setLogWriter(abandonedConfig.getLogWriter());
            this.abandonedConfig.setRemoveAbandonedOnBorrow(abandonedConfig.getRemoveAbandonedOnBorrow());
            this.abandonedConfig.setRemoveAbandonedOnMaintenance(abandonedConfig.getRemoveAbandonedOnMaintenance());
            this.abandonedConfig.setRemoveAbandonedTimeout(abandonedConfig.getRemoveAbandonedTimeout());
            this.abandonedConfig.setUseUsageTracking(abandonedConfig.getUseUsageTracking());
        }
    }

    /**
     * 获取用于创建的工厂的引用, 销毁并验证此池使用的对象.
     */
    public PooledObjectFactory<T> getFactory() {
        return factory;
    }

    /**
     * 等效于 <code>{@link #borrowObject(long) borrowObject}({@link #getMaxWaitMillis()})</code>.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public T borrowObject() throws Exception {
        return borrowObject(getMaxWaitMillis());
    }

    /**
     * 使用特定的等待时间从池中借用对象, 如果 {@link #getBlockWhenExhausted()} 是 true.
     * <p>
     * 如果池中有一个或多个空闲实例可用, 然后将根据{@link #getLifo()}的值选择空闲实例, 激活并返回.
     * 如果激活失败, 或者 {@link #getTestOnBorrow() testOnBorrow} 被设置为 <code>true</code>, 且验证失败, 实例被销毁, 并检查下一个可用实例.
     * 这将继续，直到返回有效实例或没有更多空闲实例可用.
     * <p>
     * 如果池中没有可用的空闲实例, 行为取决于{@link #getMaxTotal() maxTotal}, {@link #getBlockWhenExhausted()}以及传递给<code>borrowMaxWaitMillis</code>参数的值.
     * 如果从池中检出的实例数小于<code>maxTotal</code>, 创建，激活和验证新实例并将其返回给调用者. 如果验证失败, 抛出<code>NoSuchElementException</code>.
     * <p>
     * 如果池用尽了 (没有可用的空闲实例，也没有创建新实例的能力), 这个方法要么阻塞 (如果{@link #getBlockWhenExhausted()} 是 true),
     * 要么抛出 <code>NoSuchElementException</code> (如果 {@link #getBlockWhenExhausted()} 是 false).
     * {@link #getBlockWhenExhausted()}为true时, 此方法阻塞的时间长度由传递进来的<code>borrowMaxWaitMillis</code>参数的值确定.
     * <p>
     * 当池耗尽时，可以同时阻塞多个调用线程，等待实例变为可用. 已经实现了“公平”算法以确保线程按照请求到达的顺序接收可用实例.
     *
     * @param borrowMaxWaitMillis 等待对象变为可用的时间（以毫秒为单位）
     *
     * @return 池中的对象实例
     *
     * @throws NoSuchElementException 如果无法返回实例
     *
     * @throws Exception 如果由于错误而无法返回对象实例
     */
    public T borrowObject(final long borrowMaxWaitMillis) throws Exception {
        assertOpen();

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnBorrow() &&
                (getNumIdle() < 2) &&
                (getNumActive() > getMaxTotal() - 3) ) {
            removeAbandoned(ac);
        }

        PooledObject<T> p = null;

        // 获取当前配置的本地副本, 以使其与整个方法执行保持一致
        final boolean blockWhenExhausted = getBlockWhenExhausted();

        boolean create;
        final long waitTime = System.currentTimeMillis();

        while (p == null) {
            create = false;
            p = idleObjects.pollFirst();
            if (p == null) {
                p = create();
                if (p != null) {
                    create = true;
                }
            }
            if (blockWhenExhausted) {
                if (p == null) {
                    if (borrowMaxWaitMillis < 0) {
                        p = idleObjects.takeFirst();
                    } else {
                        p = idleObjects.pollFirst(borrowMaxWaitMillis,
                                TimeUnit.MILLISECONDS);
                    }
                }
                if (p == null) {
                    throw new NoSuchElementException(
                            "Timeout waiting for idle object");
                }
            } else {
                if (p == null) {
                    throw new NoSuchElementException("Pool exhausted");
                }
            }
            if (!p.allocate()) {
                p = null;
            }

            if (p != null) {
                try {
                    factory.activateObject(p);
                } catch (final Exception e) {
                    try {
                        destroy(p);
                    } catch (final Exception e1) {
                        // Ignore - 激活失败更重要
                    }
                    p = null;
                    if (create) {
                        final NoSuchElementException nsee = new NoSuchElementException(
                                "Unable to activate object");
                        nsee.initCause(e);
                        throw nsee;
                    }
                }
                if (p != null && (getTestOnBorrow() || create && getTestOnCreate())) {
                    boolean validate = false;
                    Throwable validationThrowable = null;
                    try {
                        validate = factory.validateObject(p);
                    } catch (final Throwable t) {
                        PoolUtils.checkRethrow(t);
                        validationThrowable = t;
                    }
                    if (!validate) {
                        try {
                            destroy(p);
                            destroyedByBorrowValidationCount.incrementAndGet();
                        } catch (final Exception e) {
                            // Ignore - 验证失败更重要
                        }
                        p = null;
                        if (create) {
                            final NoSuchElementException nsee = new NoSuchElementException(
                                    "Unable to validate object");
                            nsee.initCause(validationThrowable);
                            throw nsee;
                        }
                    }
                }
            }
        }

        updateStatsBorrow(p, System.currentTimeMillis() - waitTime);

        return p.getObject();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 如果{@link #getMaxIdle() maxIdle}设置为正值且空闲实例数已达到此值, 销毁返回的实例.
     * <p>
     * 如果 {@link #getTestOnReturn() testOnReturn} == true, 返回的实例在返回到空闲实例池之前进行验证.
     * 在这种情况下, 如果验证失败, 销毁实例.
     * <p>
     * 忽略销毁时发生的异常, 但通过一个{@link org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener}通知.
     */
    @Override
    public void returnObject(final T obj) {
        final PooledObject<T> p = allObjects.get(new IdentityWrapper<>(obj));

        if (p == null) {
            if (!isAbandonedConfig()) {
                throw new IllegalStateException(
                        "Returned object not currently part of this pool");
            }
            return; // 对象被废弃并删除
        }

        synchronized(p) {
            final PooledObjectState state = p.getState();
            if (state != PooledObjectState.ALLOCATED) {
                throw new IllegalStateException(
                        "Object has already been returned to this pool or is invalid");
            }
            p.markReturning(); // Keep from being marked abandoned
        }

        final long activeTime = p.getActiveTimeMillis();

        if (getTestOnReturn()) {
            if (!factory.validateObject(p)) {
                try {
                    destroy(p);
                } catch (final Exception e) {
                    swallowException(e);
                }
                try {
                    ensureIdle(1, false);
                } catch (final Exception e) {
                    swallowException(e);
                }
                updateStatsReturn(activeTime);
                return;
            }
        }

        try {
            factory.passivateObject(p);
        } catch (final Exception e1) {
            swallowException(e1);
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }
            updateStatsReturn(activeTime);
            return;
        }

        if (!p.deallocate()) {
            throw new IllegalStateException(
                    "Object has already been returned to this pool or is invalid");
        }

        final int maxIdleSave = getMaxIdle();
        if (isClosed() || maxIdleSave > -1 && maxIdleSave <= idleObjects.size()) {
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }
        } else {
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
            if (isClosed()) {
                // 在将对象添加到空闲对象时关闭池.
                // 确保返回的对象被销毁而不是留在空闲对象池中 (实际上是一个泄漏)
                clear();
            }
        }
        updateStatsReturn(activeTime);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 激活此方法会减少活动的实例的数量并尝试销毁实例.
     *
     * @throws Exception             如果销毁对象时发生异常
     * @throws IllegalStateException 如果 obj 不属于这个池
     */
    @Override
    public void invalidateObject(final T obj) throws Exception {
        final PooledObject<T> p = allObjects.get(new IdentityWrapper<>(obj));
        if (p == null) {
            if (isAbandonedConfig()) {
                return;
            }
            throw new IllegalStateException(
                    "Invalidated object not currently part of this pool");
        }
        synchronized (p) {
            if (p.getState() != PooledObjectState.INVALID) {
                destroy(p);
            }
        }
        ensureIdle(1, false);
    }

    /**
     * 通过从空闲实例池中删除它们来清除池中空闲的对象, 然后在每个空闲实例上调用配置的{@link PooledObjectFactory#destroyObject(PooledObject)}方法.
     * <p>
     * 实现注意:
     * <ul>
     * <li>此方法不会以任何方式销毁或影响在调用池时检出的实例.</li>
     * <li>调用此方法不会阻止将对象返回到空闲实例池, 甚至在执行期间. 在删除已销毁的项目时，可能会返回其他实例.</li>
     * <li>忽略销毁空闲对象期间遇到的异常, 但通过 {@link org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener}通知.</li>
     * </ul>
     */
    @Override
    public void clear() {
        PooledObject<T> p = idleObjects.poll();

        while (p != null) {
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }
            p = idleObjects.poll();
        }
    }

    @Override
    public int getNumActive() {
        return allObjects.size() - idleObjects.size();
    }

    @Override
    public int getNumIdle() {
        return idleObjects.size();
    }

    /**
     * 关闭池.
     * 一旦关闭池, {@link #borrowObject()} 将失败并抛出 IllegalStateException,
     * 但是 {@link #returnObject(Object)} 和 {@link #invalidateObject(Object)} 将继续执行, 并在返回时销毁返回的对象.
     * <p>
     * 通过调用{@link #clear()}来销毁池中的空闲实例.
     */
    @Override
    public void close() {
        if (isClosed()) {
            return;
        }

        synchronized (closeLock) {
            if (isClosed()) {
                return;
            }

            // 在池关闭之前停止逐出器，因为evict()调用assertOpen()
            startEvictor(-1L);

            closed = true;
            // 清空任何空闲对象
            clear();

            jmxUnregister();

            // 释放所有等待对象的线程
            idleObjects.interuptTakeWaiters();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的连续激活按顺序检查对象, 以最古老到最年轻的顺序.
     */
    @Override
    public void evict() throws Exception {
        assertOpen();

        if (idleObjects.size() > 0) {

            PooledObject<T> underTest = null;
            final EvictionPolicy<T> evictionPolicy = getEvictionPolicy();

            synchronized (evictionLock) {
                final EvictionConfig evictionConfig = new EvictionConfig(
                        getMinEvictableIdleTimeMillis(),
                        getSoftMinEvictableIdleTimeMillis(),
                        getMinIdle());

                final boolean testWhileIdle = getTestWhileIdle();

                for (int i = 0, m = getNumTests(); i < m; i++) {
                    if (evictionIterator == null || !evictionIterator.hasNext()) {
                        evictionIterator = new EvictionIterator(idleObjects);
                    }
                    if (!evictionIterator.hasNext()) {
                        // Pool exhausted, nothing to do here
                        return;
                    }

                    try {
                        underTest = evictionIterator.next();
                    } catch (final NoSuchElementException nsee) {
                        // 对象是在另一个线程中借用的
                        // 不要把它算作驱逐测试，所以减少 i;
                        i--;
                        evictionIterator = null;
                        continue;
                    }

                    if (!underTest.startEvictionTest()) {
                        // 对象是在另一个线程中借用的
                        // 不要把它算作驱逐测试，所以减少 i;
                        i--;
                        continue;
                    }

                    // 用户提供的驱逐策略可能会引发各种疯狂的异常. 防止这种异常杀死驱逐线程.
                    boolean evict;
                    try {
                        evict = evictionPolicy.evict(evictionConfig, underTest,
                                idleObjects.size());
                    } catch (final Throwable t) {
                        // SwallowedExceptionListener使用时略微复杂
                        // Exception rather than Throwable
                        PoolUtils.checkRethrow(t);
                        swallowException(new Exception(t));
                        // 不要在错误条件下逐出
                        evict = false;
                    }

                    if (evict) {
                        destroy(underTest);
                        destroyedByEvictorCount.incrementAndGet();
                    } else {
                        if (testWhileIdle) {
                            boolean active = false;
                            try {
                                factory.activateObject(underTest);
                                active = true;
                            } catch (final Exception e) {
                                destroy(underTest);
                                destroyedByEvictorCount.incrementAndGet();
                            }
                            if (active) {
                                if (!factory.validateObject(underTest)) {
                                    destroy(underTest);
                                    destroyedByEvictorCount.incrementAndGet();
                                } else {
                                    try {
                                        factory.passivateObject(underTest);
                                    } catch (final Exception e) {
                                        destroy(underTest);
                                        destroyedByEvictorCount.incrementAndGet();
                                    }
                                }
                            }
                        }
                        if (!underTest.endEvictionTest(idleObjects)) {
                            // TODO - 一旦使用其他状态，可能需要在此处添加代码
                        }
                    }
                }
            }
        }
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnMaintenance()) {
            removeAbandoned(ac);
        }
    }

    /**
     * 尝试确保池中的{@link #getMinIdle()}空闲实例可用.
     *
     * @throws Exception 如果关联的工厂抛出异常
     */
    public void preparePool() throws Exception {
        if (getMinIdle() < 1) {
            return;
        }
        ensureMinIdle();
    }

    /**
     * 尝试创建一个新的封装的池中的对象.
     * <p>
     * 如果{@link #getMaxTotal()}对象已在流通或正在创建过程中, 这个方法返回 null.
     *
     * @return 新的封装的池中的对象
     *
     * @throws Exception 如果对象工厂的 {@code makeObject} 失败
     */
    private PooledObject<T> create() throws Exception {
        int localMaxTotal = getMaxTotal();
        // 这样可以在以后的方法中简化代码
        if (localMaxTotal < 0) {
            localMaxTotal = Integer.MAX_VALUE;
        }

        // 指示create是否应该的标志:
        // - TRUE:  调用工厂来创建一个对象
        // - FALSE: 返回 null
        // - null:  循环并重新测试确定是否调用工厂的条件
        Boolean create = null;
        while (create == null) {
            synchronized (makeObjectCountLock) {
                final long newCreateCount = createCount.incrementAndGet();
                if (newCreateCount > localMaxTotal) {
                    // 该池目前达到了其容量，或者正在创建足够的新对象以使其达到容量.
                    createCount.decrementAndGet();
                    if (makeObjectCount == 0) {
                        // 没有正在进行调用makeObject()，因此池达到了其容量. 不要尝试创建新对象. 返回并等待返回一个对象
                        create = Boolean.FALSE;
                    } else {
                        // 正在进行的makeObject()调用可能会使池达到容量. 这些调用也可能会失败，所以请等待它们完成，然后重新测试池是否达到了其容量.
                        makeObjectCountLock.wait();
                    }
                } else {
                    // 池没有达到容量. 创建一个新对象.
                    makeObjectCount++;
                    create = Boolean.TRUE;
                }
            }
        }

        if (!create.booleanValue()) {
            return null;
        }

        final PooledObject<T> p;
        try {
            p = factory.makeObject();
        } catch (Exception e) {
            createCount.decrementAndGet();
            throw e;
        } finally {
            synchronized (makeObjectCountLock) {
                makeObjectCount--;
                makeObjectCountLock.notifyAll();
            }
        }

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getLogAbandoned()) {
            p.setLogAbandoned(true);
        }

        createdCount.incrementAndGet();
        allObjects.put(new IdentityWrapper<>(p.getObject()), p);
        return p;
    }

    /**
     * 销毁封装的池中的对象.
     *
     * @param toDestroy 要销毁的对象
     *
     * @throws Exception 如果工厂未能彻底销毁池中的对象
     */
    private void destroy(final PooledObject<T> toDestroy) throws Exception {
        toDestroy.invalidate();
        idleObjects.remove(toDestroy);
        allObjects.remove(new IdentityWrapper<>(toDestroy.getObject()));
        try {
            factory.destroyObject(toDestroy);
        } finally {
            destroyedCount.incrementAndGet();
            createCount.decrementAndGet();
        }
    }

    @Override
    void ensureMinIdle() throws Exception {
        ensureIdle(getMinIdle(), true);
    }

    /**
     * 尝试确保池中存在{@code idleCount}个空闲实例.
     * <p>
     * 创建并添加空闲实例, 直到 {@link #getNumIdle()} 到达 {@code idleCount},
     * 或对象(空闲, 检出, 正在被创建的)的总数到达{@link #getMaxTotal()}.
     * 如果 {@code always} 是 false, 除非有线程等待从池中检出实例，否则不会创建任何实例.
     *
     * @param idleCount 所需的空闲实例数
     * @param always  true 表示即使池中没有线程等待也要创建实例
     * 
     * @throws Exception 如果工厂的 makeObject 抛出
     */
    private void ensureIdle(final int idleCount, final boolean always) throws Exception {
        if (idleCount < 1 || isClosed() || (!always && !idleObjects.hasTakeWaiters())) {
            return;
        }

        while (idleObjects.size() < idleCount) {
            final PooledObject<T> p = create();
            if (p == null) {
                // 无法创建对象, 没有理由认为调用另一个创建将起作用. 放弃.
                break;
            }
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
        if (isClosed()) {
            // 在将对象添加到空闲对象时关闭池.
            // 确保返回的对象被销毁而不是留在空闲对象池中 (实际上是一个泄漏)
            clear();
        }
    }

    /**
     * 创建一个对象，并将其放入池中. addObject() 对于使用空闲对象“预加载”池非常有用.
     * <p>
     * 如果没有可用的容量添加到池中，则这是一个无操作 (没有异常, 对池没影响). </p>
     */
    @Override
    public void addObject() throws Exception {
        assertOpen();
        if (factory == null) {
            throw new IllegalStateException(
                    "Cannot add objects without a factory.");
        }
        final PooledObject<T> p = create();
        addIdleObject(p);
    }

    /**
     * 将提供的封装的池中的对象添加到此池的空闲对象集. 该对象必须已经是池的一部分.
     * 如果 {@code p} 是 null, 没有操作 (没有异常, 对池没影响).
     *
     * @param p 使闲置的对象
     *
     * @throws Exception 如果工厂无法钝化对象
     */
    private void addIdleObject(final PooledObject<T> p) throws Exception {
        if (p != null) {
            factory.passivateObject(p);
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
    }

    /**
     * 计算在空闲对象逐出器的运行中要测试的对象数.
     */
    private int getNumTests() {
        final int numTestsPerEvictionRun = getNumTestsPerEvictionRun();
        if (numTestsPerEvictionRun >= 0) {
            return Math.min(numTestsPerEvictionRun, idleObjects.size());
        }
        return (int) (Math.ceil(idleObjects.size() /
                Math.abs((double) numTestsPerEvictionRun)));
    }

    /**
     * 恢复已检出但未使用的废弃对象，因为它比removeAbandonedTimeout长.
     *
     * @param ac 用于标识废弃对象的配置
     */
    private void removeAbandoned(final AbandonedConfig ac) {
        // 生成要删除的废弃对象列表
        final long now = System.currentTimeMillis();
        final long timeout =
                now - (ac.getRemoveAbandonedTimeout() * 1000L);
        final ArrayList<PooledObject<T>> remove = new ArrayList<>();
        final Iterator<PooledObject<T>> it = allObjects.values().iterator();
        while (it.hasNext()) {
            final PooledObject<T> pooledObject = it.next();
            synchronized (pooledObject) {
                if (pooledObject.getState() == PooledObjectState.ALLOCATED &&
                        pooledObject.getLastUsedTime() <= timeout) {
                    pooledObject.markAbandoned();
                    remove.add(pooledObject);
                }
            }
        }

        // 现在删除废弃的对象
        final Iterator<PooledObject<T>> itr = remove.iterator();
        while (itr.hasNext()) {
            final PooledObject<T> pooledObject = itr.next();
            if (ac.getLogAbandoned()) {
                pooledObject.printStackTrace(ac.getLogWriter());
            }
            try {
                invalidateObject(pooledObject.getObject());
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }


    //--- Usage tracking support -----------------------------------------------

    @Override
    public void use(final T pooledObject) {
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getUseUsageTracking()) {
            final PooledObject<T> wrapper = allObjects.get(new IdentityWrapper<>(pooledObject));
            wrapper.use();
        }
    }


    //--- JMX support ----------------------------------------------------------

    private volatile String factoryType = null;

    /**
     * 返回当前阻塞等待池中的对象的线程的大概数量. 这仅用于监视，而不用于同步控制.
     */
    @Override
    public int getNumWaiters() {
        if (getBlockWhenExhausted()) {
            return idleObjects.getTakeQueueLength();
        }
        return 0;
    }

    /**
     * 返回类型 - 包括工厂的特定类型而不是通用类型.
     */
    @Override
    public String getFactoryType() {
        // 不是线程安全的. 接受可能有多个评估.
        if (factoryType == null) {
            final StringBuilder result = new StringBuilder();
            result.append(factory.getClass().getName());
            result.append('<');
            final Class<?> pooledObjectType =
                    PoolImplUtils.getFactoryType(factory.getClass());
            result.append(pooledObjectType.getName());
            result.append('>');
            factoryType = result.toString();
        }
        return factoryType;
    }

    /**
     * 提供有关池中所有对象的信息，包括空闲（等待借用）和活动（当前借用）.
     * <p>
     * Note: 这被命名为listAllObjects，因此它通过JMX呈现为一个操作.
     * 这意味着除非明确请求，否则将不会调用它，而在JConsole等工具中的查看对象属性时将自动请求所有属性.
     *
     * @return 在池中的所有对象上分组的信息
     */
    @Override
    public Set<DefaultPooledObjectInfo> listAllObjects() {
        final Set<DefaultPooledObjectInfo> result =
                new HashSet<>(allObjects.size());
        for (final PooledObject<T> p : allObjects.values()) {
            result.add(new DefaultPooledObjectInfo(p));
        }
        return result;
    }

    // --- configuration attributes --------------------------------------------

    private volatile int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;
    private volatile int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;
    private final PooledObjectFactory<T> factory;


    // --- internal attributes -------------------------------------------------

    /*
     * 当前与此池关联的所有对象处于任何状态. 它排除了被销毁的对象.
     * {@link #allObjects}的大小始终小于或等于{@link #_maxActive}. Map 的 key是池中的对象, 值是在池中内部使用的 PooledObject封装器.
     */
    private final Map<IdentityWrapper<T>, PooledObject<T>> allObjects =
        new ConcurrentHashMap<>();
    /*
     * 当前创建的对象与正在创建的对象的组合数量.
     * 在负载下, 它可能超过{@link #_maxActive}, 如果多个线程尝试同时创建一个新对象,
     * 但{@link #create()}将确保在任何时候创建的对象永远不会超过{@link #_maxActive}.
     */
    private final AtomicLong createCount = new AtomicLong(0);
    private long makeObjectCount = 0;
    private final Object makeObjectCountLock = new Object();
    private final LinkedBlockingDeque<PooledObject<T>> idleObjects;

    // JMX的特定属性
    private static final String ONAME_BASE =
        "org.apache.tomcat.dbcp.pool2:type=GenericObjectPool,name=";

    // 废弃的对象跟踪的其他配置属性
    private volatile AbandonedConfig abandonedConfig = null;

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", factoryType=");
        builder.append(factoryType);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append(", factory=");
        builder.append(factory);
        builder.append(", allObjects=");
        builder.append(allObjects);
        builder.append(", createCount=");
        builder.append(createCount);
        builder.append(", idleObjects=");
        builder.append(idleObjects);
        builder.append(", abandonedConfig=");
        builder.append(abandonedConfig);
    }
}
