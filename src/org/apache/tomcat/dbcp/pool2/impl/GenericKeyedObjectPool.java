package org.apache.tomcat.dbcp.pool2.impl;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PoolUtils;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectState;

/**
 * 可配置的<code>KeyedObjectPool</code>实现.
 * <p>
 * 与适当的 {@link KeyedPooledObjectFactory}结合使用时,
 * <code>GenericKeyedObjectPool</code>为带键的对象提供强大的池功能.
 * 一个 <code>GenericKeyedObjectPool</code>可以视为子级池的一个 map, (惟一的) key 由
 * {@link #preparePool preparePool}, {@link #addObject addObject}, {@link #borrowObject borrowObject}方法提供.
 * 每次这些方法中的一个提供了一个新的 key, 在新的key下创建一个新的子级池, 通过包含<code>GenericKeyedObjectPool</code>管理.
 * <p>
 * 可选地，可以将池配置为在池中空闲时检查并可能逐出对象，并确保最小数量的空闲对象可用. 这是由“空闲对象驱逐”线程执行的, 它以异步方式运行.
 * 配置此可选功能时应小心. 驱逐线程与客户端线程竞争访问池中的对象, 因此如果它们运行得太频繁, 可能会导致性能问题.</p>
 * <p>
 * 实现注意: 防止可能的死锁, 已经注意确保在同步块内不会发生对工厂方法的调用. See POOL-125 and DBCP-44 for more information.</p>
 * <p>
 * 此类旨在是线程安全的.</p>
 *
 * @param <K> 这个池中维护的Key的类型.
 * @param <T> 这个池中的元素的类型.
 */
public class GenericKeyedObjectPool<K,T> extends BaseGenericObjectPool<T>
        implements KeyedObjectPool<K,T>, GenericKeyedObjectPoolMXBean<K> {

    /**
     * @param factory 用于创建条目的对象工厂
     */
    public GenericKeyedObjectPool(final KeyedPooledObjectFactory<K,T> factory) {
        this(factory, new GenericKeyedObjectPoolConfig());
    }

    /**
     * @param factory 用于创建条目的对象工厂
     * @param config    用于此池实例的配置. 配置按值使用. 对配置对象的后续更改不会反映在池中.
     */
    public GenericKeyedObjectPool(final KeyedPooledObjectFactory<K,T> factory,
            final GenericKeyedObjectPoolConfig config) {

        super(config, ONAME_BASE, config.getJmxNamePrefix());

        if (factory == null) {
            jmxUnregister(); // tidy up
            throw new IllegalArgumentException("factory may not be null");
        }
        this.factory = factory;
        this.fairness = config.getFairness();

        setConfig(config);

        startEvictor(getTimeBetweenEvictionRunsMillis());
    }

    /**
     * 返回池分配的对象实例数的限制 (检出的或空闲的), 每个 key. 达到限制时, 子池已经用尽.
     * 负值表示没有限制.
     */
    @Override
    public int getMaxTotalPerKey() {
        return maxTotalPerKey;
    }

    /**
     * 设置池分配的对象实例数的限制 (检出的或空闲的), 每个 key. 达到限制时, 子池已经用尽.
     * 负值表示没有限制.
     *
     * @param maxTotalPerKey 每个key的活动实例数限制
     */
    public void setMaxTotalPerKey(final int maxTotalPerKey) {
        this.maxTotalPerKey = maxTotalPerKey;
    }


    /**
     * 返回池中每个key的“空闲”实例数量的上限. 如果没有限制，则为负值.
     * 如果在负载很重的系统上将maxIdlePerKey设置得太低，则可能会看到对象被销毁，并且几乎立即创建了新对象.
     * 这是活动线程暂时返回对象的速度超过请求它们的速度的结果，导致空闲对象的数量超过maxIdlePerKey.
     * 对于负载较重的系统，maxIdlePerKey的最佳值会有所不同，但默认值是一个很好的起点.
     */
    @Override
    public int getMaxIdlePerKey() {
        return maxIdlePerKey;
    }

    /**
     * 设置池中每个key的“空闲”实例数量的上限. 如果没有限制，则为负值.
     * 如果在负载很重的系统上将maxIdlePerKey设置得太低，则可能会看到对象被销毁，并且几乎立即创建了新对象.
     * 这是活动线程暂时返回对象的速度超过请求它们的速度的结果，导致空闲对象的数量超过maxIdlePerKey.
     * 对于负载较重的系统，maxIdlePerKey的最佳值会有所不同，但默认值是一个很好的起点.
     *
     * @param maxIdlePerKey 池中每个key的“空闲”实例数量的上限. 如果没有限制，则为负值.
     */
    public void setMaxIdlePerKey(final int maxIdlePerKey) {
        this.maxIdlePerKey = maxIdlePerKey;
    }

    /**
     * 设置要在每个Key的子级池中维护的最小空闲对象数量.
     * 如果此设置为正数, 且{@link #getTimeBetweenEvictionRunsMillis()}大于零，则此设置才起作用.
     * 如果是这样的话, 确保池在空闲对象驱逐运行期间具有所需的最小实例数.
     * <p>
     * 如果minIdlePerKey的配置值大于maxIdlePerKey的配置值，则将使用maxIdlePerKey的值.
     *
     * @param minIdlePerKey 每个Key的子级池中对象的最小数量
     */
    public void setMinIdlePerKey(final int minIdlePerKey) {
        this.minIdlePerKey = minIdlePerKey;
    }

    /**
     * 返回要在每个Key的子级池中维护的最小空闲对象数量.
     * 如果此设置为正数, 且{@link #getTimeBetweenEvictionRunsMillis()}大于零，则此设置才起作用.
     * 如果是这样的话, 确保池在空闲对象驱逐运行期间具有所需的最小实例数.
     * <p>
     * 如果minIdlePerKey的配置值大于maxIdlePerKey的配置值，则将使用maxIdlePerKey的值.
     */
    @Override
    public int getMinIdlePerKey() {
        final int maxIdlePerKeySave = getMaxIdlePerKey();
        if (this.minIdlePerKey > maxIdlePerKeySave) {
            return maxIdlePerKeySave;
        }
        return minIdlePerKey;
    }

    /**
     * 设置配置.
     *
     * @param conf 要使用的新配置.
     */
    public void setConfig(final GenericKeyedObjectPoolConfig conf) {
        setLifo(conf.getLifo());
        setMaxIdlePerKey(conf.getMaxIdlePerKey());
        setMaxTotalPerKey(conf.getMaxTotalPerKey());
        setMaxTotal(conf.getMaxTotal());
        setMinIdlePerKey(conf.getMinIdlePerKey());
        setMaxWaitMillis(conf.getMaxWaitMillis());
        setBlockWhenExhausted(conf.getBlockWhenExhausted());
        setTestOnCreate(conf.getTestOnCreate());
        setTestOnBorrow(conf.getTestOnBorrow());
        setTestOnReturn(conf.getTestOnReturn());
        setTestWhileIdle(conf.getTestWhileIdle());
        setNumTestsPerEvictionRun(conf.getNumTestsPerEvictionRun());
        setMinEvictableIdleTimeMillis(conf.getMinEvictableIdleTimeMillis());
        setSoftMinEvictableIdleTimeMillis(
                conf.getSoftMinEvictableIdleTimeMillis());
        setTimeBetweenEvictionRunsMillis(
                conf.getTimeBetweenEvictionRunsMillis());
        setEvictionPolicyClassName(conf.getEvictionPolicyClassName());
    }

    /**
     * 获取用于创建的工厂的引用, 销毁并验证此池使用的对象.
     */
    public KeyedPooledObjectFactory<K, T> getFactory() {
        return factory;
    }

    /**
     * 等效于 <code>{@link #borrowObject(Object, long) borrowObject}(key, {@link #getMaxWaitMillis()})</code>.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public T borrowObject(final K key) throws Exception {
        return borrowObject(key, getMaxWaitMillis());
    }

    /**
     * 使用特定的等待时间从给定Key关联的子级池中借用对象, 如果 {@link #getBlockWhenExhausted()} 是 true.
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
     * 当<code>maxTotal</code>设置为正值时，如果在限制下调用此方法，并且在请求的密钥下没有可用的空闲实例,
     * 尝试通过清除给定的Key对应的子池中最旧的15%的元素来创建空间.
     * <p>
     * 当池耗尽时，可以同时阻塞多个调用线程，等待实例变为可用. 已经实现了“公平”算法以确保线程按照请求到达的顺序接收可用实例.
     *
     * @param key 池的 key
     * @param borrowMaxWaitMillis 等待对象变为可用的时间（以毫秒为单位）
     *
     * @return 池中的对象实例
     *
     * @throws NoSuchElementException 如果无法返回实例
     *
     * @throws Exception 如果由于错误而无法返回对象实例
     */
    public T borrowObject(final K key, final long borrowMaxWaitMillis) throws Exception {
        assertOpen();

        PooledObject<T> p = null;

        // 获取当前配置的本地副本, 以使其与整个方法执行保持一致
        final boolean blockWhenExhausted = getBlockWhenExhausted();

        boolean create;
        final long waitTime = System.currentTimeMillis();
        final ObjectDeque<T> objectDeque = register(key);

        try {
            while (p == null) {
                create = false;
                p = objectDeque.getIdleObjects().pollFirst();
                if (p == null) {
                    p = create(key);
                    if (p != null) {
                        create = true;
                    }
                }
                if (blockWhenExhausted) {
                    if (p == null) {
                        if (borrowMaxWaitMillis < 0) {
                            p = objectDeque.getIdleObjects().takeFirst();
                        } else {
                            p = objectDeque.getIdleObjects().pollFirst(
                                    borrowMaxWaitMillis, TimeUnit.MILLISECONDS);
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
                        factory.activateObject(key, p);
                    } catch (final Exception e) {
                        try {
                            destroy(key, p, true);
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
                            validate = factory.validateObject(key, p);
                        } catch (final Throwable t) {
                            PoolUtils.checkRethrow(t);
                            validationThrowable = t;
                        }
                        if (!validate) {
                            try {
                                destroy(key, p, true);
                                destroyedByBorrowValidationCount.incrementAndGet();
                            } catch (final Exception e) {
                                // Ignore - 激活失败更重要
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
        } finally {
            deregister(key);
        }

        updateStatsBorrow(p, System.currentTimeMillis() - waitTime);

        return p.getObject();
    }


    /**
     * 返回一个对象到指定的Key对应的子级池中.
     * <p>
     * 如果{@link #getMaxIdle() maxIdle}设置为正值且空闲实例数已达到此值, 销毁返回的实例.
     * <p>
     * 如果 {@link #getTestOnReturn() testOnReturn} == true, 返回的实例在返回到空闲实例池之前进行验证.
     * 在这种情况下, 如果验证失败, 销毁实例.
     * <p>
     * 忽略销毁时发生的异常, 但通过一个{@link org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener}通知.
     *
     * @param key 池的 key
     * @param obj 要返回的实例
     *
     * @throws IllegalStateException 如果一个对象返回到错误的池，或者一个对象多次返回池中
     */
    @Override
    public void returnObject(final K key, final T obj) {

        final ObjectDeque<T> objectDeque = poolMap.get(key);

        final PooledObject<T> p = objectDeque.getAllObjects().get(new IdentityWrapper<>(obj));

        if (p == null) {
            throw new IllegalStateException(
                    "Returned object not currently part of this pool");
        }

        synchronized(p) {
            final PooledObjectState state = p.getState();
            if (state != PooledObjectState.ALLOCATED) {
                throw new IllegalStateException(
                        "Object has already been returned to this pool or is invalid");
            }
            p.markReturning(); // 不要被标记为废弃 (一旦GKOP这样做了)
        }

        final long activeTime = p.getActiveTimeMillis();

        try {
            if (getTestOnReturn()) {
                if (!factory.validateObject(key, p)) {
                    try {
                        destroy(key, p, true);
                    } catch (final Exception e) {
                        swallowException(e);
                    }
                    if (objectDeque.idleObjects.hasTakeWaiters()) {
                        try {
                            addObject(key);
                        } catch (final Exception e) {
                            swallowException(e);
                        }
                    }
                    return;
                }
            }

            try {
                factory.passivateObject(key, p);
            } catch (final Exception e1) {
                swallowException(e1);
                try {
                    destroy(key, p, true);
                } catch (final Exception e) {
                    swallowException(e);
                }
                if (objectDeque.idleObjects.hasTakeWaiters()) {
                    try {
                        addObject(key);
                    } catch (final Exception e) {
                        swallowException(e);
                    }
                }
                return;
            }

            if (!p.deallocate()) {
                throw new IllegalStateException(
                        "Object has already been returned to this pool");
            }

            final int maxIdle = getMaxIdlePerKey();
            final LinkedBlockingDeque<PooledObject<T>> idleObjects =
                objectDeque.getIdleObjects();

            if (isClosed() || maxIdle > -1 && maxIdle <= idleObjects.size()) {
                try {
                    destroy(key, p, true);
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
                    clear(key);
                }
            }
        } finally {
            if (hasBorrowWaiters()) {
                reuseCapacity();
            }
            updateStatsReturn(activeTime);
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * 激活此方法会减少给定的Key对应的池关联的活动的实例的数量并尝试销毁<code>obj</code>
     *
     * @param key 池的 key
     * @param obj 要使其无效的实例
     *
     * @throws Exception             如果销毁对象时发生异常
     * @throws IllegalStateException 如果 obj 不属于这个池
     */
    @Override
    public void invalidateObject(final K key, final T obj) throws Exception {

        final ObjectDeque<T> objectDeque = poolMap.get(key);

        final PooledObject<T> p = objectDeque.getAllObjects().get(new IdentityWrapper<>(obj));
        if (p == null) {
            throw new IllegalStateException(
                    "Object not currently part of this pool");
        }
        synchronized (p) {
            if (p.getState() != PooledObjectState.INVALID) {
                destroy(key, p, true);
            }
        }
        if (objectDeque.idleObjects.hasTakeWaiters()) {
            addObject(key);
        }
    }


    /**
     * 通过从空闲实例池中删除它们来清除池中空闲的对象, 然后在每个空闲实例上调用配置的{@link KeyedPooledObjectFactory#destroyObject(Object, PooledObject)}方法.
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
        final Iterator<K> iter = poolMap.keySet().iterator();

        while (iter.hasNext()) {
            clear(iter.next());
        }
    }


    /**
     * 清空指定的子池, 删除与给定的<code>key</ code>对应的所有池中的实例.
     * 忽略销毁空闲对象期间遇到的异常, 但通过 {@link org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener}通知.
     *
     * @param key 要清空的key
     */
    @Override
    public void clear(final K key) {

        final ObjectDeque<T> objectDeque = register(key);

        try {
            final LinkedBlockingDeque<PooledObject<T>> idleObjects =
                    objectDeque.getIdleObjects();

            PooledObject<T> p = idleObjects.poll();

            while (p != null) {
                try {
                    destroy(key, p, true);
                } catch (final Exception e) {
                    swallowException(e);
                }
                p = idleObjects.poll();
            }
        } finally {
            deregister(key);
        }
    }


    @Override
    public int getNumActive() {
        return numTotal.get() - getNumIdle();
    }


    @Override
    public int getNumIdle() {
        final Iterator<ObjectDeque<T>> iter = poolMap.values().iterator();
        int result = 0;

        while (iter.hasNext()) {
            result += iter.next().getIdleObjects().size();
        }

        return result;
    }


    @Override
    public int getNumActive(final K key) {
        final ObjectDeque<T> objectDeque = poolMap.get(key);
        if (objectDeque != null) {
            return objectDeque.getAllObjects().size() -
                    objectDeque.getIdleObjects().size();
        }
        return 0;
    }


    @Override
    public int getNumIdle(final K key) {
        final ObjectDeque<T> objectDeque = poolMap.get(key);
        return objectDeque != null ? objectDeque.getIdleObjects().size() : 0;
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
            final Iterator<ObjectDeque<T>> iter = poolMap.values().iterator();
            while (iter.hasNext()) {
                iter.next().getIdleObjects().interuptTakeWaiters();
            }
            // 清除了现在任何等待线程被中断的Key
            clear();
        }
    }


    /**
     * 清除池中最旧的15%的对象.  该方法将对象排序为TreeMap，然后迭代前15%以进行删除.
     */
    public void clearOldest() {

        // 构建空闲对象的有序映射
        final Map<PooledObject<T>, K> map = new TreeMap<>();

        for (Map.Entry<K, ObjectDeque<T>> entry : poolMap.entrySet()) {
            final K k = entry.getKey();
            final ObjectDeque<T> deque = entry.getValue();
            // 如果在另一个线程中删除了Key，则防止可能的NPE. 在此循环完成时不值得锁定Key.
            if (deque != null) {
                final LinkedBlockingDeque<PooledObject<T>> idleObjects =
                        deque.getIdleObjects();
                for (final PooledObject<T> p : idleObjects) {
                    // 使用PooledObject对象作为Key将每个项目放入 map 中. 然后根据空闲时间对其进行排序
                    map.put(p, k);
                }
            }
        }

        // 现在迭代创建的 map, 并杀死前 15%
        int itemsToRemove = ((int) (map.size() * 0.15)) + 1;
        final Iterator<Map.Entry<PooledObject<T>, K>> iter =
            map.entrySet().iterator();

        while (iter.hasNext() && itemsToRemove > 0) {
            final Map.Entry<PooledObject<T>, K> entry = iter.next();
            // 在命名上有点倒退.  在 map 中, 每个 key 是PooledObject, 因为它具有带时间戳值的排序. key引用的每个值都是它所属列表的key.
            final K key = entry.getValue();
            final PooledObject<T> p = entry.getKey();
            // 假设销毁成功
            boolean destroyed = true;
            try {
                destroyed = destroy(key, p, false);
            } catch (final Exception e) {
                swallowException(e);
            }
            if (destroyed) {
                itemsToRemove--;
            }
        }
    }

    /**
     * 尝试创建一个新实例，以便可以从添加新实例负载最重的池中提供服务.
     *
     * 当线程等待并且随后在所请求的Key下创建实例的容量变得可用时，存在该方法以确保池中的活跃性.
     *
     * 此方法无法保证创建实例, 并且其对可以创建实例的最多负载池的选择可能并不总是正确的, 因为它不会锁定池并且可能会通过其他线程创建,
     * 借用, 返回, 销毁实例, 在它执行期间.
     */
    private void reuseCapacity() {
        final int maxTotalPerKeySave = getMaxTotalPerKey();

        // 找到可以获取新实例的最多负载的池
        int maxQueueLength = 0;
        LinkedBlockingDeque<PooledObject<T>> mostLoaded = null;
        K loadedKey = null;
        for (Map.Entry<K, ObjectDeque<T>> entry : poolMap.entrySet()) {
            final K k = entry.getKey();
            final ObjectDeque<T> deque = entry.getValue();
            if (deque != null) {
                final LinkedBlockingDeque<PooledObject<T>> pool = deque.getIdleObjects();
                final int queueLength = pool.getTakeQueueLength();
                if (getNumActive(k) < maxTotalPerKeySave && queueLength > maxQueueLength) {
                    maxQueueLength = queueLength;
                    mostLoaded = pool;
                    loadedKey = k;
                }
            }
        }

        // 尝试将实例添加到最多负载的池中
        if (mostLoaded != null) {
            register(loadedKey);
            try {
                final PooledObject<T> p = create(loadedKey);
                if (p != null) {
                    addIdleObject(loadedKey, p);
                }
            } catch (final Exception e) {
                swallowException(e);
            } finally {
                deregister(loadedKey);
            }
        }
    }

    /**
     * 检查是否有线程当前正在等待借用对象, 但被阻塞, 等待更多对象变为可用.
     *
     * @return {@code true} 如果至少有一个线程在等待; 否则 {@code false}
     */
    private boolean hasBorrowWaiters() {
        for (Map.Entry<K, ObjectDeque<T>> entry : poolMap.entrySet()) {
            final ObjectDeque<T> deque = entry.getValue();
            if (deque != null) {
                final LinkedBlockingDeque<PooledObject<T>> pool =
                    deque.getIdleObjects();
                if(pool.hasTakeWaiters()) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * {@inheritDoc}
     * <p>
     * 此方法的连续激活按顺序检查对象, 以最古老到最年轻的顺序.
     */
    @Override
    public void evict() throws Exception {
        assertOpen();

        if (getNumIdle() == 0) {
            return;
        }

        PooledObject<T> underTest = null;
        final EvictionPolicy<T> evictionPolicy = getEvictionPolicy();

        synchronized (evictionLock) {
            final EvictionConfig evictionConfig = new EvictionConfig(
                    getMinEvictableIdleTimeMillis(),
                    getSoftMinEvictableIdleTimeMillis(),
                    getMinIdlePerKey());

            final boolean testWhileIdle = getTestWhileIdle();

            for (int i = 0, m = getNumTests(); i < m; i++) {
                if(evictionIterator == null || !evictionIterator.hasNext()) {
                    if (evictionKeyIterator == null ||
                            !evictionKeyIterator.hasNext()) {
                        final List<K> keyCopy = new ArrayList<>();
                        final Lock readLock = keyLock.readLock();
                        readLock.lock();
                        try {
                            keyCopy.addAll(poolKeyList);
                        } finally {
                            readLock.unlock();
                        }
                        evictionKeyIterator = keyCopy.iterator();
                    }
                    while (evictionKeyIterator.hasNext()) {
                        evictionKey = evictionKeyIterator.next();
                        final ObjectDeque<T> objectDeque = poolMap.get(evictionKey);
                        if (objectDeque == null) {
                            continue;
                        }

                        final Deque<PooledObject<T>> idleObjects = objectDeque.getIdleObjects();
                        evictionIterator = new EvictionIterator(idleObjects);
                        if (evictionIterator.hasNext()) {
                            break;
                        }
                        evictionIterator = null;
                    }
                }
                if (evictionIterator == null) {
                    // 池耗尽
                    return;
                }
                final Deque<PooledObject<T>> idleObjects;
                try {
                    underTest = evictionIterator.next();
                    idleObjects = evictionIterator.getIdleObjects();
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
                            poolMap.get(evictionKey).getIdleObjects().size());
                } catch (final Throwable t) {
                    // SwallowedExceptionListener使用时略微复杂
                    // Exception rather than Throwable
                    PoolUtils.checkRethrow(t);
                    swallowException(new Exception(t));
                    // 不要在错误条件下逐出
                    evict = false;
                }

                if (evict) {
                    destroy(evictionKey, underTest, true);
                    destroyedByEvictorCount.incrementAndGet();
                } else {
                    if (testWhileIdle) {
                        boolean active = false;
                        try {
                            factory.activateObject(evictionKey, underTest);
                            active = true;
                        } catch (final Exception e) {
                            destroy(evictionKey, underTest, true);
                            destroyedByEvictorCount.incrementAndGet();
                        }
                        if (active) {
                            if (!factory.validateObject(evictionKey, underTest)) {
                                destroy(evictionKey, underTest, true);
                                destroyedByEvictorCount.incrementAndGet();
                            } else {
                                try {
                                    factory.passivateObject(evictionKey, underTest);
                                } catch (final Exception e) {
                                    destroy(evictionKey, underTest, true);
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

    /**
     * 创建一个新的池中的对象.
     *
     * @param key 新创建的池中的对象关联的Key
     *
     * @return 新创建的池中的对象
     *
     * @throws Exception 如果创建对象失败
     */
    private PooledObject<T> create(final K key) throws Exception {
        int maxTotalPerKeySave = getMaxTotalPerKey(); // Per key
        if (maxTotalPerKeySave < 0) {
            maxTotalPerKeySave = Integer.MAX_VALUE;
        }
        final int maxTotal = getMaxTotal();   // All keys

        final ObjectDeque<T> objectDeque = poolMap.get(key);

        // 检查总体限制
        boolean loop = true;

        while (loop) {
            final int newNumTotal = numTotal.incrementAndGet();
            if (maxTotal > -1 && newNumTotal > maxTotal) {
                numTotal.decrementAndGet();
                if (getNumIdle() == 0) {
                    return null;
                }
                clearOldest();
            } else {
                loop = false;
            }
        }

        // 指示create是否应该的标志:
        // - TRUE:  调用工厂来创建一个对象
        // - FALSE: 返回 null
        // - null:  循环并重新测试确定是否调用工厂的条件
        Boolean create = null;
        while (create == null) {
            synchronized (objectDeque.makeObjectCountLock) {
                final long newCreateCount = objectDeque.getCreateCount().incrementAndGet();
                // 再次检查每个 key 的限制
                if (newCreateCount > maxTotalPerKeySave) {
                    // Key目前处于容量或正在创建足够的新对象以使其具有容量.
                    objectDeque.getCreateCount().decrementAndGet();
                    if (objectDeque.makeObjectCount == 0) {
                        // 此key没有正在进行的makeObject()调用，因此 key 处于容量状态.
                        // 不要尝试创建新对象. 返回并等待返回一个对象.
                        create = Boolean.FALSE;
                    } else {
                        // 正在进行的makeObject()调用可能会使池达到容量.
                        // 这些调用也可能会失败, 所以请等待它们完成, 然后重新测试池是否处于容量状态.
                        objectDeque.makeObjectCountLock.wait();
                    }
                } else {
                    // 池没有达到容量. 创建一个新对象.
                    objectDeque.makeObjectCount++;
                    create = Boolean.TRUE;
                }
            }
        }

        if (!create.booleanValue()) {
            numTotal.decrementAndGet();
            return null;
        }

        PooledObject<T> p = null;
        try {
            p = factory.makeObject(key);
        } catch (final Exception e) {
            numTotal.decrementAndGet();
            objectDeque.getCreateCount().decrementAndGet();
            throw e;
        } finally {
            synchronized (objectDeque.makeObjectCountLock) {
                objectDeque.makeObjectCount--;
                objectDeque.makeObjectCountLock.notifyAll();
            }
        }

        createdCount.incrementAndGet();
        objectDeque.getAllObjects().put(new IdentityWrapper<>(p.getObject()), p);
        return p;
    }

    /**
     * 销毁封装的池中的对象.
     *
     * @param key 要销毁的对象关联的 key.
     * @param toDestroy 要销毁的封装的对象
     * @param always 是否销毁对象，即使它当前不在给定Key的空闲对象集中
     * 
     * @return {@code true} 销毁对象, 否则 {@code false}
     * @throws Exception 如果对象销毁失败
     */
    private boolean destroy(final K key, final PooledObject<T> toDestroy, final boolean always)
            throws Exception {

        final ObjectDeque<T> objectDeque = register(key);

        try {
            final boolean isIdle = objectDeque.getIdleObjects().remove(toDestroy);

            if (isIdle || always) {
                objectDeque.getAllObjects().remove(new IdentityWrapper<>(toDestroy.getObject()));
                toDestroy.invalidate();

                try {
                    factory.destroyObject(key, toDestroy);
                } finally {
                    objectDeque.getCreateCount().decrementAndGet();
                    destroyedCount.incrementAndGet();
                    numTotal.decrementAndGet();
                }
                return true;
            }
            return false;
        } finally {
            deregister(key);
        }
    }


    /**
     * 注册一个 key.
     * <p>
     * register() 和 deregister() 必须成对使用.
     *
     * @param k 要注册的 key
     *
     * @return 当前与给定 key 关联的对象. 如果此方法返回而不抛出异常, 那么它永远不会返回 null.
     */
    private ObjectDeque<T> register(final K k) {
        Lock lock = keyLock.readLock();
        ObjectDeque<T> objectDeque = null;
        try {
            lock.lock();
            objectDeque = poolMap.get(k);
            if (objectDeque == null) {
                // 升级到写锁
                lock.unlock();
                lock = keyLock.writeLock();
                lock.lock();
                objectDeque = poolMap.get(k);
                if (objectDeque == null) {
                    objectDeque = new ObjectDeque<>(fairness);
                    objectDeque.getNumInterested().incrementAndGet();
                    // NOTE: 必须同时添加Key到 poolMap 和 poolKeyList, 在 keyLock.writeLock() 保护期间
                    poolMap.put(k, objectDeque);
                    poolKeyList.add(k);
                } else {
                    objectDeque.getNumInterested().incrementAndGet();
                }
            } else {
                objectDeque.getNumInterested().incrementAndGet();
            }
        } finally {
            lock.unlock();
        }
        return objectDeque;
    }

    /**
     * 注销一个 key.
     * <p>
     * register() 和 deregister() 必须成对使用.
     *
     * @param k 要注销的 key
     */
    private void deregister(final K k) {
        ObjectDeque<T> objectDeque;

        objectDeque = poolMap.get(k);
        final long numInterested = objectDeque.getNumInterested().decrementAndGet();
        if (numInterested == 0 && objectDeque.getCreateCount().get() == 0) {
            // 删除密钥的可能性
            final Lock writeLock = keyLock.writeLock();
            writeLock.lock();
            try {
                if (objectDeque.getCreateCount().get() == 0 &&
                        objectDeque.getNumInterested().get() == 0) {
                    // NOTE: 必须同时添加Key到 poolMap 和 poolKeyList, 在 keyLock.writeLock() 保护期间
                    poolMap.remove(k);
                    poolKeyList.remove(k);
                }
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    void ensureMinIdle() throws Exception {
        final int minIdlePerKeySave = getMinIdlePerKey();
        if (minIdlePerKeySave < 1) {
            return;
        }

        for (final K k : poolMap.keySet()) {
            ensureMinIdle(k);
        }
    }

    /**
     * 确保池中为给定key提供了已配置的最小空闲对象数.
     *
     * @param key 要检查空闲对象的key
     *
     * @throws Exception 如果需要新对象且无法创建
     */
    private void ensureMinIdle(final K key) throws Exception {
        // 计算当前池对象
        final ObjectDeque<T> objectDeque = poolMap.get(key);

        // objectDeque == null is OK here. 它由以下两种方法正确处理.

        // 这个方法不同步，所以calculateDeficit在开始时作为循环限制完成，第二次在循环内完成，当另一个线程已经返回所需的对象时停止
        final int deficit = calculateDeficit(objectDeque);

        for (int i = 0; i < deficit && calculateDeficit(objectDeque) > 0; i++) {
            addObject(key);
        }
    }

    /**
     * 使用{@link KeyedPooledObjectFactory#makeObject factory}创建一个对象，将其钝化，然后将其放在空闲对象池中.
     * <code>addObject</code> 对于使用空闲对象“预加载”池非常有用.
     *
     * @param key 应该添加新实例的 key
     *
     * @throws Exception 当 {@link KeyedPooledObjectFactory#makeObject}失败.
     */
    @Override
    public void addObject(final K key) throws Exception {
        assertOpen();
        register(key);
        try {
            final PooledObject<T> p = create(key);
            addIdleObject(key, p);
        } finally {
            deregister(key);
        }
    }

    /**
     * 将对象添加到给定key的空闲对象集.
     *
     * @param key 与空闲对象关联的 key
     * @param p 要添加的封装的对象.
     *
     * @throws Exception 如果关联的工厂无法钝化该对象
     */
    private void addIdleObject(final K key, final PooledObject<T> p) throws Exception {

        if (p != null) {
            factory.passivateObject(key, p);
            final LinkedBlockingDeque<PooledObject<T>> idleObjects =
                    poolMap.get(key).getIdleObjects();
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
    }

    /**
     * 注册池控制的Key, 并确保创建{@link #getMinIdlePerKey()}空闲实例.
     *
     * @param key - 注册池控制的key
     *
     * @throws Exception 如果关联的工厂抛出异常
     */
    public void preparePool(final K key) throws Exception {
        final int minIdlePerKeySave = getMinIdlePerKey();
        if (minIdlePerKeySave < 1) {
            return;
        }
        ensureMinIdle(key);
    }

    /**
     * 计算在空闲对象逐出器的运行中要测试的对象数.
     */
    private int getNumTests() {
        final int totalIdle = getNumIdle();
        final int numTests = getNumTestsPerEvictionRun();
        if (numTests >= 0) {
            return Math.min(numTests, totalIdle);
        }
        return(int)(Math.ceil(totalIdle/Math.abs((double)numTests)));
    }

    /**
     * 计算需要创建的对象数，以尝试保持最小空闲对象数，同时不超过每个Key或总体的最大对象数限制.
     *
     * @param objectDeque   要检查的对象集
     *
     * @return 要创建的对象的数量
     */
    private int calculateDeficit(final ObjectDeque<T> objectDeque) {

        if (objectDeque == null) {
            return getMinIdlePerKey();
        }

        // 多次使用，因此请保留本地副本，以使值保持一致
        final int maxTotal = getMaxTotal();
        final int maxTotalPerKeySave = getMaxTotalPerKey();

        int objectDefecit = 0;

        // 计算不需要创建的对象, 为了池中对象的数量 < maxTotalPerKey();
        objectDefecit = getMinIdlePerKey() - objectDeque.getIdleObjects().size();
        if (maxTotalPerKeySave > 0) {
            final int growLimit = Math.max(0,
                    maxTotalPerKeySave - objectDeque.getIdleObjects().size());
            objectDefecit = Math.min(objectDefecit, growLimit);
        }

        // 考虑maxTotal限制
        if (maxTotal > 0) {
            final int growLimit = Math.max(0, maxTotal - getNumActive() - getNumIdle());
            objectDefecit = Math.min(objectDefecit, growLimit);
        }

        return objectDefecit;
    }


    //--- JMX support ----------------------------------------------------------

    @Override
    public Map<String,Integer> getNumActivePerKey() {
        final HashMap<String,Integer> result = new HashMap<>();

        final Iterator<Entry<K,ObjectDeque<T>>> iter = poolMap.entrySet().iterator();
        while (iter.hasNext()) {
            final Entry<K,ObjectDeque<T>> entry = iter.next();
            if (entry != null) {
                final K key = entry.getKey();
                final ObjectDeque<T> objectDequeue = entry.getValue();
                if (key != null && objectDequeue != null) {
                    result.put(key.toString(), Integer.valueOf(
                            objectDequeue.getAllObjects().size() -
                            objectDequeue.getIdleObjects().size()));
                }
            }
        }
        return result;
    }

    /**
     * 返回当前阻塞等待池中的对象的线程的大概数量. 这仅用于监视，而不用于同步控制.
     */
    @Override
    public int getNumWaiters() {
        int result = 0;

        if (getBlockWhenExhausted()) {
            final Iterator<ObjectDeque<T>> iter = poolMap.values().iterator();

            while (iter.hasNext()) {
                // Assume no overflow
                result += iter.next().getIdleObjects().getTakeQueueLength();
            }
        }

        return result;
    }

    /**
     * 返回每个key当前阻塞等待池中的对象的线程的大概数量. 这仅用于监视，而不用于同步控制.
     */
    @Override
    public Map<String,Integer> getNumWaitersByKey() {
        final Map<String,Integer> result = new HashMap<>();

        for (Map.Entry<K, ObjectDeque<T>> entry : poolMap.entrySet()) {
            final K k = entry.getKey();
            final ObjectDeque<T> deque = entry.getValue();
            if (deque != null) {
                if (getBlockWhenExhausted()) {
                    result.put(k.toString(), Integer.valueOf(
                            deque.getIdleObjects().getTakeQueueLength()));
                } else {
                    result.put(k.toString(), Integer.valueOf(0));
                }
            }
        }
        return result;
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
    public Map<String,List<DefaultPooledObjectInfo>> listAllObjects() {
        final Map<String,List<DefaultPooledObjectInfo>> result =
                new HashMap<>();

        for (Map.Entry<K, ObjectDeque<T>> entry : poolMap.entrySet()) {
            final K k = entry.getKey();
            final ObjectDeque<T> deque = entry.getValue();
            if (deque != null) {
                final List<DefaultPooledObjectInfo> list =
                        new ArrayList<>();
                result.put(k.toString(), list);
                for (final PooledObject<T> p : deque.getAllObjects().values()) {
                    list.add(new DefaultPooledObjectInfo(p));
                }
            }
        }
        return result;
    }


    //--- inner classes ----------------------------------------------

    /**
     * 维护给定Key的每个Key队列的信息.
     */
    private class ObjectDeque<S> {

        private final LinkedBlockingDeque<PooledObject<S>> idleObjects;

        /*
         * 创建的实例数量 - 销毁的数量.
         * Invariant: createCount <= maxTotalPerKey
         */
        private final AtomicInteger createCount = new AtomicInteger(0);

        private long makeObjectCount = 0;
        private final Object makeObjectCountLock = new Object();

        /*
         * 池中的实例
         */
        private final Map<IdentityWrapper<S>, PooledObject<S>> allObjects =
                new ConcurrentHashMap<>();

        /*
         * 在此Key中注册的线程数.
         * register(K) 增加这个计数器, deRegister(K) 减少它.
         * Invariant: 空的池不会删除, 除非 numInterested 是 0.
         */
        private final AtomicLong numInterested = new AtomicLong(0);

        /**
         * @param fairness true 表示等待借用/返回实例的客户端线程将被视为在FIFO队列中等待.
         */
        public ObjectDeque(final boolean fairness) {
            idleObjects = new LinkedBlockingDeque<>(fairness);
        }

        /**
         * 获取当前Key的空闲对象.
         */
        public LinkedBlockingDeque<PooledObject<S>> getIdleObjects() {
            return idleObjects;
        }

        /**
         * 获取为当前Key创建的对象数量.
         */
        public AtomicInteger getCreateCount() {
            return createCount;
        }

        /**
         * 获取在此Key中注册的线程数.
         */
        public AtomicLong getNumInterested() {
            return numInterested;
        }

        /**
         * 获取当前Key的所有对象.
         */
        public Map<IdentityWrapper<S>, PooledObject<S>> getAllObjects() {
            return allObjects;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("ObjectDeque [idleObjects=");
            builder.append(idleObjects);
            builder.append(", createCount=");
            builder.append(createCount);
            builder.append(", allObjects=");
            builder.append(allObjects);
            builder.append(", numInterested=");
            builder.append(numInterested);
            builder.append("]");
            return builder.toString();
        }

    }

    //--- configuration attributes ---------------------------------------------
    private volatile int maxIdlePerKey =
            GenericKeyedObjectPoolConfig.DEFAULT_MAX_IDLE_PER_KEY;
    private volatile int minIdlePerKey =
        GenericKeyedObjectPoolConfig.DEFAULT_MIN_IDLE_PER_KEY;
    private volatile int maxTotalPerKey =
        GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL_PER_KEY;
    private final KeyedPooledObjectFactory<K,T> factory;
    private final boolean fairness;


    //--- internal attributes --------------------------------------------------

    /*
     * 子级池 (ObjectQueue).
     * 必须使用{@link #keyLock}将Key列表与{@link #poolKeyList}保持一致，以确保以线程安全的方式对当前Key列表进行任何更改.
     */
    private final Map<K,ObjectDeque<T>> poolMap =
            new ConcurrentHashMap<>(); // @GuardedBy("keyLock") for write access (and some read access)
    /*
     * 池Key的列表 - 用于控制逐出顺序.
     * 必须使用{@link #keyLock}将Key列表与{@link #poolMap}保持一致，以确保以线程安全的方式对当前Key列表进行任何更改.
     */
    private final List<K> poolKeyList = new ArrayList<>(); // @GuardedBy("keyLock")
    private final ReadWriteLock keyLock = new ReentrantReadWriteLock(true);
    /*
     * 所有Key的当前活动对象和正在创建的Key的组合数量.
     * 在负载下, 它可能会超过{@link #maxTotal}，但任何时候都不会超过{@link #maxTotal}.
     */
    private final AtomicInteger numTotal = new AtomicInteger(0);
    private Iterator<K> evictionKeyIterator = null; // @GuardedBy("evictionLock")
    private K evictionKey = null; // @GuardedBy("evictionLock")

    // JMX的特定属性
    private static final String ONAME_BASE =
        "org.apache.tomcat.dbcp.pool2:type=GenericKeyedObjectPool,name=";

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", maxIdlePerKey=");
        builder.append(maxIdlePerKey);
        builder.append(", minIdlePerKey=");
        builder.append(minIdlePerKey);
        builder.append(", maxTotalPerKey=");
        builder.append(maxTotalPerKey);
        builder.append(", factory=");
        builder.append(factory);
        builder.append(", fairness=");
        builder.append(fairness);
        builder.append(", poolMap=");
        builder.append(poolMap);
        builder.append(", poolKeyList=");
        builder.append(poolKeyList);
        builder.append(", keyLock=");
        builder.append(keyLock);
        builder.append(", numTotal=");
        builder.append(numTotal);
        builder.append(", evictionKeyIterator=");
        builder.append(evictionKeyIterator);
        builder.append(", evictionKey=");
        builder.append(evictionKey);
    }
}
