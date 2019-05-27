package org.apache.tomcat.dbcp.pool2;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * 此类仅包含对ObjectPool或KeyedObjectPool相关接口进行操作或返回的静态方法.
 */
public final class PoolUtils {

    /**
     * 用于定期检查池空闲对象计数. 因为一个{@link Timer} 创建一个 {@link Thread}, 使用IODH.
     */
    static class TimerHolder {
        static final Timer MIN_IDLE_TIMER = new Timer(true);
    }

    /**
     * 应该这样使用: PoolUtils.adapt(aPool);.
     * 此构造函数是public的，以允许需要JavaBean实例的工具进行操作.
     */
    public PoolUtils() {
    }

    /**
     * 是否应该重新抛出所提供的Throwable (例如，如果它是一个永远不应该忽略Throwable的实例).
     * 池错误处理用于抛出通常需要忽略的异常的操作.
     *
     * @param t 要检查的Throwable
     * @throws ThreadDeath
     * @throws VirtualMachineError
     */
    public static void checkRethrow(final Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // 所有其它的Throwable将被忽略
    }

    /**
     * 定期检查池的空闲对象计数. 每个周期最多添加一个空闲对象.
     * 如果在调用{@link ObjectPool #addObject()}时出现异常，则不再执行检查.
     *
     * @param pool 要定期检查的池.
     * @param minIdle 如果{@link ObjectPool#getNumIdle()}小于此值，则添加一个空闲对象.
     * @param period 检查池中空闲对象数的频率, see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <T> 池中对象的类型
     * 
     * @return 将定期检查池空闲对象数的{@link TimerTask}.
     * @throws IllegalArgumentException <code>pool</code>为<code>null</code>, 或<code>minIdle</code>为负数, 或<code>period</code>无效
     */
    public static <T> TimerTask checkMinIdle(final ObjectPool<T> pool,
            final int minIdle, final long period)
            throws IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException("minIdle must be non-negative.");
        }
        final TimerTask task = new ObjectPoolMinIdleTimerTask<>(pool, minIdle);
        getMinIdleTimer().schedule(task, 0L, period);
        return task;
    }

    /**
     * 定期检查keyedPool中 Key 的空闲对象数.
     * 每个周期最多添加一个空闲对象. 如果在调用{@link KeyedObjectPool#addObject(Object)}时出现异常，则不再检查该Key.
     *
     * @param keyedPool 要定期检查的 keyedPool.
     * @param key 检查空闲数的Key.
     * @param minIdle 最小空闲对象数量
     * @param period 检查空闲对象数的频率
     * @param <K> 池 key的类型
     * @param <V> 池条目的类型
     * 
     * @return 将周期性检查池空闲对象数量的 {@link TimerTask}
     * @throws IllegalArgumentException 
     * 				<code>keyedPool</code>,<code>pool</code>为<code>null</code>, 或<code>minIdle</code>为负数, 或<code>period</code>无效
     */
    public static <K, V> TimerTask checkMinIdle(
            final KeyedObjectPool<K, V> keyedPool, final K key,
            final int minIdle, final long period)
            throws IllegalArgumentException {
        if (keyedPool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null.");
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException("minIdle must be non-negative.");
        }
        final TimerTask task = new KeyedObjectPoolMinIdleTimerTask<>(
                keyedPool, key, minIdle);
        getMinIdleTimer().schedule(task, 0L, period);
        return task;
    }

    /**
     * 定期检查keyedPool中<code>Collection</code><code>keys</code>中每个键的空闲对象计数.
     * 每个周期最多添加一个空闲对象.
     *
     * @param keyedPool  要定期检查的keyedPool.
     * @param keys 用于检查空闲对象计数的Key集合.
     * @param minIdle 如果{@link KeyedObjectPool#getNumIdle(Object)}小于此值, 则添加一个空闲对象.
     * @param period 检查keyedPool中空闲对象数的频率, see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <K> 池key的类型
     * @param <V> 池条目的类型
     * 
     * @return Key和{@link TimerTask}组成的{@link Map}将定期检查池空闲对象数.
     * @throws IllegalArgumentException 
     *             当<code>keyedPool</code>, <code>keys</code>, 或集合中的任何值是 <code>null</code>,
     *             或<code>minIdle</code>是负数, 或<code>period</code>无效.
     */
    public static <K, V> Map<K, TimerTask> checkMinIdle(
            final KeyedObjectPool<K, V> keyedPool, final Collection<K> keys,
            final int minIdle, final long period)
            throws IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null.");
        }
        final Map<K, TimerTask> tasks = new HashMap<>(keys.size());
        final Iterator<K> iter = keys.iterator();
        while (iter.hasNext()) {
            final K key = iter.next();
            final TimerTask task = checkMinIdle(keyedPool, key, minIdle, period);
            tasks.put(key, task);
        }
        return tasks;
    }

    /**
     * 调用<code>pool</code>上的<code>addObject()</code>添加空闲对象.
     *
     * @param pool 要填充的池.
     * @param count 要添加的空闲对象的数量.
     * @param <T> 池中对象的类型
     * 
     * @throws Exception 当 {@link ObjectPool#addObject()} 失败.
     * @throws IllegalArgumentException 当<code>pool</code>是<code>null</code>.
     */
    public static <T> void prefill(final ObjectPool<T> pool, final int count)
            throws Exception, IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null.");
        }
        for (int i = 0; i < count; i++) {
            pool.addObject();
        }
    }

    /**
     * 调用<code>keyedPool</code>上的<code>addObject(Object)</code>填充空闲对象.
     *
     * @param keyedPool 要填充的keyedPool.
     * @param key 要添加对象的 key.
     * @param count 要添加到<code>key</code>的空闲对象的数量.
     * @param <K> 池的 key的类型
     * @param <V> 池的条目的类型
     * @throws Exception 当{@link KeyedObjectPool#addObject(Object)}失败.
     * @throws IllegalArgumentException 如果<code>keyedPool</code>或<code>key</code>是<code>null</code>.
     */
    public static <K, V> void prefill(final KeyedObjectPool<K, V> keyedPool,
            final K key, final int count) throws Exception,
            IllegalArgumentException {
        if (keyedPool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null.");
        }
        for (int i = 0; i < count; i++) {
            keyedPool.addObject(key);
        }
    }

    /**
     * 调用<code>keyedPool</code>上的<code>addObject(Object)</code>为<code>keys</code>中的每个Key填充空闲对象.
     * 和{@link #prefill(KeyedObjectPool, Object, int)}功能类似.
     *
     * @param keyedPool 要填充的 keyedPool.
     * @param keys 要添加对象的keys {@link Collection}.
     * @param count 要为每个<code>key</code>添加的空闲对象的数量.
     * @param <K> 池的 key的类型
     * @param <V> 池的条目的类型
     * 
     * @throws Exception 当{@link KeyedObjectPool#addObject(Object)} 失败.
     * @throws IllegalArgumentException 当 <code>keyedPool</code>, <code>keys</code>, 或<code>keys</code>中的任何值是<code>null</code>.
     */
    public static <K, V> void prefill(final KeyedObjectPool<K, V> keyedPool,
            final Collection<K> keys, final int count) throws Exception,
            IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null.");
        }
        final Iterator<K> iter = keys.iterator();
        while (iter.hasNext()) {
            prefill(keyedPool, iter.next(), count);
        }
    }

    /**
     * 返回由指定的ObjectPool支持的同步（线程安全）ObjectPool.
     * <p>
     * <b>Note:</b> 这不应该用于已经提供适当同步的池实现, 例如Commons Pool库中提供的池.
     * 在允许另一个使用同步的另一层借用之前, 封装一个{@link #wait() waits}池中的对象返回的池, 将导致存活问题或死锁.
     * </p>
     *
     * @param pool 在一个同步的ObjectPool中封装的 ObjectPool.
     * @param <T> 池中对象的类型
     * 
     * @return 指定的ObjectPool的同步的视图.
     */
    public static <T> ObjectPool<T> synchronizedPool(final ObjectPool<T> pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null.");
        }
        /*
         * assert !(pool instanceof GenericObjectPool) :
         * "GenericObjectPool is already thread-safe"; assert !(pool instanceof
         * SoftReferenceObjectPool) :
         * "SoftReferenceObjectPool is already thread-safe"; assert !(pool
         * instanceof StackObjectPool) :
         * "StackObjectPool is already thread-safe"; assert
         * !"org.apache.commons.pool.composite.CompositeObjectPool"
         * .equals(pool.getClass().getName()) :
         * "CompositeObjectPools are already thread-safe";
         */
        return new SynchronizedObjectPool<>(pool);
    }

    /**
     * 返回由指定的KeyedObjectPool支持的同步（线程安全）KeyedObjectPool.
     * <p>
     * <b>Note:</b> 这不应该用于已经提供适当同步的池实现, 例如Commons Pool库中提供的池.
     * 在允许另一个使用同步的另一层借用之前, 封装一个{@link #wait() waits}池中的对象返回的池, 将导致存活问题或死锁.
     * </p>
     *
     * @param keyedPool 在一个同步的KeyedObjectPool中封装的 KeyedObjectPool.
     * @param <K> 池的key的类型
     * @param <V> 池的条目的类型
     * @return 指定的KeyedObjectPool的同步的视图.
     */
    public static <K, V> KeyedObjectPool<K, V> synchronizedPool(
            final KeyedObjectPool<K, V> keyedPool) {
        /*
         * assert !(keyedPool instanceof GenericKeyedObjectPool) :
         * "GenericKeyedObjectPool is already thread-safe"; assert !(keyedPool
         * instanceof StackKeyedObjectPool) :
         * "StackKeyedObjectPool is already thread-safe"; assert
         * !"org.apache.commons.pool.composite.CompositeKeyedObjectPool"
         * .equals(keyedPool.getClass().getName()) :
         * "CompositeKeyedObjectPools are already thread-safe";
         */
        return new SynchronizedKeyedObjectPool<>(keyedPool);
    }

    /**
     * 返回由指定的PooledObjectFactory支持的同步（线程安全）PooledObjectFactory.
     *
     * @param factory 在一个同步的PooledObjectFactory中封装的 PooledObjectFactory.
     * @param <T> 池中对象的类型
     * @return 指定的PooledObjectFactory的同步的视图.
     */
    public static <T> PooledObjectFactory<T> synchronizedPooledFactory(
            final PooledObjectFactory<T> factory) {
        return new SynchronizedPooledObjectFactory<>(factory);
    }

    /**
     * 返回由指定的KeyedPoolableObjectFactory支持的同步（线程安全）KeyedPooledObjectFactory.
     *
     * @param keyedFactory 在一个同步的KeyedPooledObjectFactory中封装的 KeyedPooledObjectFactory.
     * @param <K> 池的key的类型
     * @param <V> 池的条目的类型
     * @return 指定的KeyedPooledObjectFactory的同步的视图.
     */
    public static <K, V> KeyedPooledObjectFactory<K, V> synchronizedKeyedPooledFactory(
            final KeyedPooledObjectFactory<K, V> keyedFactory) {
        return new SynchronizedKeyedPooledObjectFactory<>(keyedFactory);
    }

    /**
     * 返回一个池, 当不再需要空闲对象时, 该池自适应地减小其大小.
     * 这是一种始终线程安全的替代方法，可以使用许多池实现提供的空闲对象逐出器. 这也是缩小遇到峰值的FIFO有序池的有效方法.
     *
     * @param pool 要装饰的ObjectPool，以便在可能的情况下缩小其空闲计数.
     * @param <T> 池中对象的类型
     * @return 在不再需要空闲对象时自适应地减小其大小的池.
     */
    public static <T> ObjectPool<T> erodingPool(final ObjectPool<T> pool) {
        return erodingPool(pool, 1f);
    }

    /**
     * 返回一个池, 当不再需要空闲对象时, 该池自适应地减小其大小.
     * 这是一种始终线程安全的替代方法，可以使用许多池实现提供的空闲对象逐出器. 这也是缩小遇到峰值的FIFO有序池的有效方法.
     * <p>
     * factor 参数提供了一种机制来调整池缩小其大小的速率. 介于0和1之间的值会导致池尝试更频繁地缩小其大小.
     * 值大于1会导致池不太频繁地尝试缩小其大小.
     * </p>
     *
     * @param pool 要装饰的ObjectPool，以便在可能的情况下缩小其空闲计数.
     * @param factor 一个正值，表示缩放速率，用于池尝试减小其大小. 介于0和1之间，那么池会更积极地收缩. 大于1, 那么池不会那么积极地收缩.
     * @param <T> 池中对象的类型
     * @return 在不再需要空闲对象时自适应地减小其大小的池.
     */
    public static <T> ObjectPool<T> erodingPool(final ObjectPool<T> pool,
            final float factor) {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null.");
        }
        if (factor <= 0f) {
            throw new IllegalArgumentException("factor must be positive.");
        }
        return new ErodingObjectPool<>(pool, factor);
    }

    /**
     * 返回一个池, 当不再需要空闲对象时, 该池自适应地减小其大小.
     * 这是一种始终线程安全的替代方法，可以使用许多池实现提供的空闲对象逐出器. 这也是缩小遇到峰值的FIFO有序池的有效方法.
     *
     * @param keyedPool 要装饰的KeyedObjectPool，以便在可能的情况下缩小其空闲数量.
     * @param <K> 池的 key的类型
     * @param <V> 池的条目的类型
     * @return 在不再需要空闲对象时自适应地减小其大小的池.
     */
    public static <K, V> KeyedObjectPool<K, V> erodingPool(
            final KeyedObjectPool<K, V> keyedPool) {
        return erodingPool(keyedPool, 1f);
    }

    /**
     * 返回一个池, 当不再需要空闲对象时, 该池自适应地减小其大小.
     * 这是一种始终线程安全的替代方法，可以使用许多池实现提供的空闲对象逐出器. 这也是缩小遇到峰值的FIFO有序池的有效方法.
     * <p>
     * factor 参数提供了一种机制来调整池缩小其大小的速率. 介于0和1之间的值会导致池尝试更频繁地缩小其大小.
     * 值大于1会导致池不太频繁地尝试缩小其大小.
     * </p>
     *
     * @param keyedPool 要装饰的KeyedObjectPool，以便在可能的情况下缩小其空闲数量.
     * @param factor 一个正值，表示缩放速率，用于池尝试减小其大小. 介于0和1之间，那么池会更积极地收缩. 大于1, 那么池不会那么积极地收缩.
     * @param <K> 池的 key的类型
     * @param <V> 池的条目的类型
     * @return 在不再需要空闲对象时自适应地减小其大小的池.
     */
    public static <K, V> KeyedObjectPool<K, V> erodingPool(
            final KeyedObjectPool<K, V> keyedPool, final float factor) {
        return erodingPool(keyedPool, factor, false);
    }

    /**
     * 返回一个池, 当不再需要空闲对象时, 该池自适应地减小其大小.
     * 这是一种始终线程安全的替代方法，可以使用许多池实现提供的空闲对象逐出器. 这也是缩小遇到峰值的FIFO有序池的有效方法.
     * <p>
     * factor 参数提供了一种机制来调整池缩小其大小的速率. 介于0和1之间的值会导致池尝试更频繁地缩小其大小.
     * 值大于1会导致池不太频繁地尝试缩小其大小.
     * </p>
     * <p>
     * perKey参数确定池是基于整个池还是基于每个键收缩. 当perKey为false时, Key对池尝试缩小其大小的速率没有影响.
     * 当perKey为true时, 每个 Key 都是独立缩小的.
     * </p>
     *
     * @param keyedPool 要装饰的KeyedObjectPool，以便在可能的情况下缩小其空闲数量.
     * @param factor 一个正值，表示缩放速率，用于池尝试减小其大小. 介于0和1之间，那么池会更积极地收缩. 大于1, 那么池不会那么积极地收缩.
     * @param perKey 当是true时, 每个键都是独立处理的.
     * @param <K> 池的 key的类型
     * @param <V> 池的条目的类型
     * @return 在不再需要空闲对象时自适应地减小其大小的池.
     */
    public static <K, V> KeyedObjectPool<K, V> erodingPool(
            final KeyedObjectPool<K, V> keyedPool, final float factor,
            final boolean perKey) {
        if (keyedPool == null) {
            throw new IllegalArgumentException("keyedPool must not be null.");
        }
        if (factor <= 0f) {
            throw new IllegalArgumentException("factor must be positive.");
        }
        if (perKey) {
            return new ErodingPerKeyKeyedObjectPool<>(keyedPool, factor);
        }
        return new ErodingKeyedObjectPool<>(keyedPool, factor);
    }

    /**
     * 获取检查keyedPool的空闲数的<code>Timer</code>.
     */
    private static Timer getMinIdleTimer() {
        return TimerHolder.MIN_IDLE_TIMER;
    }

    /**
     * 定时器任务，将对象添加到池中，直到空闲实例数达到配置的minIdle.
     * 请注意，这与池的minIdle设置不同.
     */
    private static final class ObjectPoolMinIdleTimerTask<T> extends TimerTask {

        /** 最小空闲实例数. 和 pool.getMinIdle()不一样. */
        private final int minIdle;

        /** Object 池 */
        private final ObjectPool<T> pool;

        /**
         * @param pool 对象池
         * @param minIdle 要维护的空闲实例数
         * @throws IllegalArgumentException 如果 pool 是 null
         */
        ObjectPoolMinIdleTimerTask(final ObjectPool<T> pool, final int minIdle)
                throws IllegalArgumentException {
            if (pool == null) {
                throw new IllegalArgumentException("pool must not be null.");
            }
            this.pool = pool;
            this.minIdle = minIdle;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            boolean success = false;
            try {
                if (pool.getNumIdle() < minIdle) {
                    pool.addObject();
                }
                success = true;

            } catch (final Exception e) {
                cancel();
            } finally {
                // 检测其他类型的Throwable并取消此Timer
                if (!success) {
                    cancel();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("ObjectPoolMinIdleTimerTask");
            sb.append("{minIdle=").append(minIdle);
            sb.append(", pool=").append(pool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 定时器任务，将对象添加到池中，直到给定 Key 的空闲实例数达到配置的minIdle.
     * 请注意，这与池的minIdle设置不同.
     */
    private static final class KeyedObjectPoolMinIdleTimerTask<K, V> extends
            TimerTask {
        /** 最小空闲实例数. 和 pool.getMinIdle()不一样. */
        private final int minIdle;

        /** 要确保minIdle的Key */
        private final K key;

        /** Key对应的对象池 */
        private final KeyedObjectPool<K, V> keyedPool;

        /**
         * @param keyedPool Key对应的对象池
         * @param key 要确保最小空闲实例数的Key
         * @param minIdle 最小空闲实例数
         * @throws IllegalArgumentException 如果 key 是 null
         */
        KeyedObjectPoolMinIdleTimerTask(final KeyedObjectPool<K, V> keyedPool,
                final K key, final int minIdle) throws IllegalArgumentException {
            if (keyedPool == null) {
                throw new IllegalArgumentException(
                        "keyedPool must not be null.");
            }
            this.keyedPool = keyedPool;
            this.key = key;
            this.minIdle = minIdle;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            boolean success = false;
            try {
                if (keyedPool.getNumIdle(key) < minIdle) {
                    keyedPool.addObject(key);
                }
                success = true;

            } catch (final Exception e) {
                cancel();

            } finally {
                // 检测其他类型的Throwable并取消此Timer
                if (!success) {
                    cancel();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("KeyedObjectPoolMinIdleTimerTask");
            sb.append("{minIdle=").append(minIdle);
            sb.append(", key=").append(key);
            sb.append(", keyedPool=").append(keyedPool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 由指定的ObjectPool支持的同步（线程安全）ObjectPool.
     * <p>
     * <b>Note:</b> 这不应该用于已经提供适当同步的池实现, 例如Commons Pool库中提供的池.
     * 在允许另一个使用同步的另一层借用之前, 封装一个{@link #wait() waits}池中的对象返回的池, 将导致存活问题或死锁.
     * </p>
     */
    private static final class SynchronizedObjectPool<T> implements ObjectPool<T> {

        /**
         * 其监视器用于同步封装的池上的方法的对象.
         */
        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        /** 底层对象池 */
        private final ObjectPool<T> pool;

        /**
         * @param pool 要在同步的ObjectPool中封装的ObjectPool.
         * @throws IllegalArgumentException 如果 pool 是 null
         */
        SynchronizedObjectPool(final ObjectPool<T> pool)
                throws IllegalArgumentException {
            if (pool == null) {
                throw new IllegalArgumentException("pool must not be null.");
            }
            this.pool = pool;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T borrowObject() throws Exception, NoSuchElementException,
                IllegalStateException {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                return pool.borrowObject();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void returnObject(final T obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.returnObject(obj);
            } catch (final Exception e) {
                // swallowed as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final T obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.invalidateObject(obj);
            } catch (final Exception e) {
                // swallowed as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject() throws Exception, IllegalStateException,
                UnsupportedOperationException {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.addObject();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return pool.getNumIdle();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return pool.getNumActive();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception, UnsupportedOperationException {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.clear();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.close();
            } catch (final Exception e) {
                // swallowed as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SynchronizedObjectPool");
            sb.append("{pool=").append(pool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 由指定的KeyedObjectPool支持的同步（线程安全）KeyedObjectPool.
     * <p>
     * <b>Note:</b> 这不应该用于已经提供适当同步的池实现, 例如Commons Pool库中提供的池.
     * 在允许另一个使用同步的另一层借用之前, 封装一个{@link #wait() waits}池中的对象返回的池, 将导致存活问题或死锁.
     * </p>
     */
    private static final class SynchronizedKeyedObjectPool<K, V> implements
            KeyedObjectPool<K, V> {

        /**
         * 其监视器用于同步封装池上的方法的对象.
         */
        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        /** 底层对象池 */
        private final KeyedObjectPool<K, V> keyedPool;

        /**
         * @param keyedPool 要封装的 KeyedObjectPool
         * @throws IllegalArgumentException 如果 keyedPool 是 null
         */
        SynchronizedKeyedObjectPool(final KeyedObjectPool<K, V> keyedPool)
                throws IllegalArgumentException {
            if (keyedPool == null) {
                throw new IllegalArgumentException(
                        "keyedPool must not be null.");
            }
            this.keyedPool = keyedPool;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public V borrowObject(final K key) throws Exception,
                NoSuchElementException, IllegalStateException {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                return keyedPool.borrowObject(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void returnObject(final K key, final V obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.returnObject(key, obj);
            } catch (final Exception e) {
                // swallowed
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final K key, final V obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.invalidateObject(key, obj);
            } catch (final Exception e) {
                // swallowed as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject(final K key) throws Exception,
                IllegalStateException, UnsupportedOperationException {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.addObject(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle(final K key) {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumIdle(key);
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive(final K key) {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumActive(key);
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumIdle();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumActive();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception, UnsupportedOperationException {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.clear();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear(final K key) throws Exception,
                UnsupportedOperationException {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.clear(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.close();
            } catch (final Exception e) {
                // swallowed as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SynchronizedKeyedObjectPool");
            sb.append("{keyedPool=").append(keyedPool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 完全同步的PooledObjectFactory, 它封装PooledObjectFactory并同步对封装的工厂方法的访问.
     * <p>
     * <b>Note:</b> 这不应该用于已经提供适当同步的池实现，例如Commons Pool库中提供的池.
     * </p>
     */
    private static final class SynchronizedPooledObjectFactory<T> implements
            PooledObjectFactory<T> {
        /** 同步锁 */
        private final WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

        /** 封装的工厂 */
        private final PooledObjectFactory<T> factory;

        /**
         * @param factory 要封装的底层工厂
         * @throws IllegalArgumentException 如果 factory 是 null
         */
        SynchronizedPooledObjectFactory(final PooledObjectFactory<T> factory)
                throws IllegalArgumentException {
            if (factory == null) {
                throw new IllegalArgumentException("factory must not be null.");
            }
            this.factory = factory;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PooledObject<T> makeObject() throws Exception {
            writeLock.lock();
            try {
                return factory.makeObject();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void destroyObject(final PooledObject<T> p) throws Exception {
            writeLock.lock();
            try {
                factory.destroyObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean validateObject(final PooledObject<T> p) {
            writeLock.lock();
            try {
                return factory.validateObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void activateObject(final PooledObject<T> p) throws Exception {
            writeLock.lock();
            try {
                factory.activateObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void passivateObject(final PooledObject<T> p) throws Exception {
            writeLock.lock();
            try {
                factory.passivateObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SynchronizedPoolableObjectFactory");
            sb.append("{factory=").append(factory);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 完全同步的KeyedPooledObjectFactory, 它封装KeyedPooledObjectFactory并同步对封装的工厂方法的访问
     * <p>
     * <b>Note:</b> 这不应该用于已经提供适当同步的池实现，例如Commons Pool库中提供的池.
     * </p>
     */
    private static final class SynchronizedKeyedPooledObjectFactory<K, V>
            implements KeyedPooledObjectFactory<K, V> {
        /** 同步锁 */
        private final WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

        /** 封装的工厂 */
        private final KeyedPooledObjectFactory<K, V> keyedFactory;

        /**
         * @param keyedFactory 要封装的底层工厂
         * @throws IllegalArgumentException 如果 factory 是 null
         */
        SynchronizedKeyedPooledObjectFactory(
                final KeyedPooledObjectFactory<K, V> keyedFactory)
                throws IllegalArgumentException {
            if (keyedFactory == null) {
                throw new IllegalArgumentException(
                        "keyedFactory must not be null.");
            }
            this.keyedFactory = keyedFactory;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PooledObject<V> makeObject(final K key) throws Exception {
            writeLock.lock();
            try {
                return keyedFactory.makeObject(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void destroyObject(final K key, final PooledObject<V> p) throws Exception {
            writeLock.lock();
            try {
                keyedFactory.destroyObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean validateObject(final K key, final PooledObject<V> p) {
            writeLock.lock();
            try {
                return keyedFactory.validateObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void activateObject(final K key, final PooledObject<V> p) throws Exception {
            writeLock.lock();
            try {
                keyedFactory.activateObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void passivateObject(final K key, final PooledObject<V> p) throws Exception {
            writeLock.lock();
            try {
                keyedFactory.passivateObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SynchronizedKeyedPoolableObjectFactory");
            sb.append("{keyedFactory=").append(keyedFactory);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * 封装下一个可丢弃的池的对象的逻辑.
     * 每次调用 update, 下一次收缩是重新计算的, 基于 factor, 池中的空闲实例数和高水位线.
     * factor假设位于 0 和 1之间. 接近1的值导致较少的侵蚀事件. 侵蚀事件的时间也取决于numIdle.
     * 当这个值比较高时 (接近先前确定的高水位线), 侵蚀更频繁地发生.
     */
    private static final class ErodingFactor {
        /** 确定“侵蚀”事件的频率 */
        private final float factor;

        /** 下一次收缩事件的时间 */
        private transient volatile long nextShrink;

        /** 高水位线 - 遇到的最大数量 */
        private transient volatile int idleHighWaterMark;

        /**
         * @param factor 侵蚀因子
         */
        public ErodingFactor(final float factor) {
            this.factor = factor;
            nextShrink = System.currentTimeMillis() + (long) (900000 * factor); // now
                                                                                // +
                                                                                // 15
                                                                                // min
                                                                                // *
                                                                                // factor
            idleHighWaterMark = 1;
        }

        /**
         * 使用提供的时间和numIdle更新内部状态.
         *
         * @param now 当前时间
         * @param numIdle 池中的空闲元素数
         */
        public void update(final long now, final int numIdle) {
            final int idle = Math.max(0, numIdle);
            idleHighWaterMark = Math.max(idle, idleHighWaterMark);
            final float maxInterval = 15f;
            final float minutes = maxInterval +
                    ((1f - maxInterval) / idleHighWaterMark) * idle;
            nextShrink = now + (long) (minutes * 60000f * factor);
        }

        /**
         * 返回下一个侵蚀事件的时间.
         */
        public long getNextShrink() {
            return nextShrink;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingFactor{" + "factor=" + factor +
                    ", idleHighWaterMark=" + idleHighWaterMark + '}';
        }
    }

    /**
     * 装饰一个对象池, 添加“侵蚀”行为. 基于配置的 {@link #factor erosion factor}, 返回到池的对象可能无效, 而不是被添加到空闲容量.
     */
    private static class ErodingObjectPool<T> implements ObjectPool<T> {
        /** 底层对象池 */
        private final ObjectPool<T> pool;

        /** 侵蚀因子 */
        private final ErodingFactor factor;

        /**
         * @param pool 底层池
         * @param factor 侵蚀因子 - 确定侵蚀事件的频率
         */
        public ErodingObjectPool(final ObjectPool<T> pool, final float factor) {
            this.pool = pool;
            this.factor = new ErodingFactor(factor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T borrowObject() throws Exception, NoSuchElementException,
                IllegalStateException {
            return pool.borrowObject();
        }

        /**
         * 返回 obj 到池中, 除非触发侵蚀, 在这种情况下, obj无效.
         * 当池中存在空闲实例, 且超过上次returnObject激活后的{@link #factor erosion factor}确定的时间时, 将触发侵蚀.
         *
         * @param obj 要返回或无效的对象
         */
        @Override
        public void returnObject(final T obj) {
            boolean discard = false;
            final long now = System.currentTimeMillis();
            synchronized (pool) {
                if (factor.getNextShrink() < now) { // XXX: Pool 3: move test
                                                    // out of sync block
                    final int numIdle = pool.getNumIdle();
                    if (numIdle > 0) {
                        discard = true;
                    }

                    factor.update(now, numIdle);
                }
            }
            try {
                if (discard) {
                    pool.invalidateObject(obj);
                } else {
                    pool.returnObject(obj);
                }
            } catch (final Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final T obj) {
            try {
                pool.invalidateObject(obj);
            } catch (final Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject() throws Exception, IllegalStateException,
                UnsupportedOperationException {
            pool.addObject();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            return pool.getNumIdle();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            return pool.getNumActive();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception, UnsupportedOperationException {
            pool.clear();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            try {
                pool.close();
            } catch (final Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingObjectPool{" + "factor=" + factor + ", pool=" +
                    pool + '}';
        }
    }

    /**
     * 装饰一个Key对应的对象池, 添加“侵蚀”行为.
     * 基于配置的侵蚀因子, 返回到池的对象可能无效, 而不是被添加到空闲容量.
     */
    private static class ErodingKeyedObjectPool<K, V> implements
            KeyedObjectPool<K, V> {
        /** 底层池 */
        private final KeyedObjectPool<K, V> keyedPool;

        /** 侵蚀因子 */
        private final ErodingFactor erodingFactor;

        /**
         * @param keyedPool 底层池
         * @param factor 侵蚀因子 - 确定侵蚀事件的频率
         */
        public ErodingKeyedObjectPool(final KeyedObjectPool<K, V> keyedPool,
                final float factor) {
            this(keyedPool, new ErodingFactor(factor));
        }

        /**
         * @param keyedPool 底层池 - 不能是 null
         * @param erodingFactor 侵蚀因子 - 确定侵蚀事件的频率
         */
        protected ErodingKeyedObjectPool(final KeyedObjectPool<K, V> keyedPool,
                final ErodingFactor erodingFactor) {
            if (keyedPool == null) {
                throw new IllegalArgumentException(
                        "keyedPool must not be null.");
            }
            this.keyedPool = keyedPool;
            this.erodingFactor = erodingFactor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public V borrowObject(final K key) throws Exception,
                NoSuchElementException, IllegalStateException {
            return keyedPool.borrowObject(key);
        }

        /**
         * 返回 obj 到池中, 除非触发侵蚀, 在这种情况下, obj无效.
         *当池中存在空闲实例, 且超过上次returnObject激活后的{@link #factor erosion factor}确定的时间时, 将触发侵蚀.
         *
         * @param obj 要返回或无效的对象
         * @param key key
         */
        @Override
        public void returnObject(final K key, final V obj) throws Exception {
            boolean discard = false;
            final long now = System.currentTimeMillis();
            final ErodingFactor factor = getErodingFactor(key);
            synchronized (keyedPool) {
                if (factor.getNextShrink() < now) {
                    final int numIdle = getNumIdle(key);
                    if (numIdle > 0) {
                        discard = true;
                    }

                    factor.update(now, numIdle);
                }
            }
            try {
                if (discard) {
                    keyedPool.invalidateObject(key, obj);
                } else {
                    keyedPool.returnObject(key, obj);
                }
            } catch (final Exception e) {
                // swallowed
            }
        }

        /**
         * 返回给定 Key 的侵蚀因子
         *
         * @param key key
         * 
         * @return 给定key对应的池的侵蚀因子
         */
        protected ErodingFactor getErodingFactor(final K key) {
            return erodingFactor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final K key, final V obj) {
            try {
                keyedPool.invalidateObject(key, obj);
            } catch (final Exception e) {
                // swallowed
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject(final K key) throws Exception,
                IllegalStateException, UnsupportedOperationException {
            keyedPool.addObject(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            return keyedPool.getNumIdle();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle(final K key) {
            return keyedPool.getNumIdle(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            return keyedPool.getNumActive();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive(final K key) {
            return keyedPool.getNumActive(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception, UnsupportedOperationException {
            keyedPool.clear();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear(final K key) throws Exception,
                UnsupportedOperationException {
            keyedPool.clear(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            try {
                keyedPool.close();
            } catch (final Exception e) {
                // swallowed
            }
        }

        /**
         * 返回底层池
         *
         * @return 这个ErodingKeyedObjectPool封装的Key对应的池
         */
        protected KeyedObjectPool<K, V> getKeyedPool() {
            return keyedPool;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingKeyedObjectPool{" + "factor=" +
                    erodingFactor + ", keyedPool=" + keyedPool + '}';
        }
    }

    /**
     * 扩展ErodingKeyedObjectPool以允许在每个Key的基础上进行侵蚀. 对于单独的Key对应的池, 分别跟踪侵蚀事件的时间.
     */
    private static final class ErodingPerKeyKeyedObjectPool<K, V> extends
            ErodingKeyedObjectPool<K, V> {
        /** 侵蚀因子 - 对于所有池都一样 */
        private final float factor;

        private final Map<K, ErodingFactor> factors = Collections.synchronizedMap(new HashMap<K, ErodingFactor>());

        /**
         * @param keyedPool 底层Key对应的池
         * @param factor 侵蚀因子
         */
        public ErodingPerKeyKeyedObjectPool(
                final KeyedObjectPool<K, V> keyedPool, final float factor) {
            super(keyedPool, null);
            this.factor = factor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected ErodingFactor getErodingFactor(final K key) {
            ErodingFactor eFactor = factors.get(key);
            // 这可能会导致为 Key 创建两个ErodingFactor, 因为它们很小且便宜, 这是可以的.
            if (eFactor == null) {
                eFactor = new ErodingFactor(this.factor);
                factors.put(key, eFactor);
            }
            return eFactor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingPerKeyKeyedObjectPool{" + "factor=" + factor +
                    ", keyedPool=" + getKeyedPool() + '}';
        }
    }
}