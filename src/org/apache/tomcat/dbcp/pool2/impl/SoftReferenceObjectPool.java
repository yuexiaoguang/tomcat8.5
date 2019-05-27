package org.apache.tomcat.dbcp.pool2.impl;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.tomcat.dbcp.pool2.BaseObjectPool;
import org.apache.tomcat.dbcp.pool2.PoolUtils;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;

/**
 * 基于{@link org.apache.tomcat.dbcp.pool2.ObjectPool}的 {@link java.lang.ref.SoftReference SoftReference}.
 * <p>
 * 此类旨在是线程安全的.
 *
 * @param <T> 此池中的元素类型.
 */
public class SoftReferenceObjectPool<T> extends BaseObjectPool<T> {

    /** 创建池中对象的工厂 */
    private final PooledObjectFactory<T> factory;

    /**
     * 可能从<code>_pool</code>中删除的已损坏引用的队列.
     * 这用于帮助{@link #getNumIdle()}以最小的性能开销更准确.
     */
    private final ReferenceQueue<T> refQueue = new ReferenceQueue<>();

    /** 已检出到池客户端的实例数 */
    private int numActive = 0; // @GuardedBy("this")

    /** 已销毁的实例总数 */
    private long destroyCount = 0; // @GuardedBy("this")


    /** 已创建的实例总数 */
    private long createCount = 0; // @GuardedBy("this")

    /** 空闲引用 - 等待被借用 */
    private final LinkedBlockingDeque<PooledSoftReference<T>> idleReferences =
        new LinkedBlockingDeque<>();

    /** 所有引用 - 检出或等待借用. */
    private final ArrayList<PooledSoftReference<T>> allReferences =
        new ArrayList<>();

    /**
     * @param factory 要使用的对象工厂.
     */
    public SoftReferenceObjectPool(final PooledObjectFactory<T> factory) {
        this.factory = factory;
    }

    /**
     * 从池中借用一个对象. 如果池中没有可用的空闲实例, 调用配置的工厂的{@link PooledObjectFactory#makeObject()}方法来创建新实例.
     *
     * @throws NoSuchElementException 如果无法提供有效对象
     * @throws IllegalStateException 如果在{@link #close() closed}池中调用
     * @throws Exception 如果创建新实例时发生异常
     * 
     * @return 一个有效的激活的对象实例
     */
    @SuppressWarnings("null") // ref cannot be null
    @Override
    public synchronized T borrowObject() throws Exception {
        assertOpen();
        T obj = null;
        boolean newlyCreated = false;
        PooledSoftReference<T> ref = null;
        while (null == obj) {
            if (idleReferences.isEmpty()) {
                if (null == factory) {
                    throw new NoSuchElementException();
                }
                newlyCreated = true;
                obj = factory.makeObject().getObject();
                createCount++;
                // 不要注册队列
                ref = new PooledSoftReference<>(new SoftReference<>(obj));
                allReferences.add(ref);
            } else {
                ref = idleReferences.pollFirst();
                obj = ref.getObject();
                // 清除引用, 以便它不会排队, 但换成新的, 非注册引用, 所以仍然可以在allReference中跟踪这个对象
                ref.getReference().clear();
                ref.setReference(new SoftReference<>(obj));
            }
            if (null != factory && null != obj) {
                try {
                    factory.activateObject(ref);
                    if (!factory.validateObject(ref)) {
                        throw new Exception("ValidateObject failed");
                    }
                } catch (final Throwable t) {
                    PoolUtils.checkRethrow(t);
                    try {
                        destroy(ref);
                    } catch (final Throwable t2) {
                        PoolUtils.checkRethrow(t2);
                        // Swallowed
                    } finally {
                        obj = null;
                    }
                    if (newlyCreated) {
                        throw new NoSuchElementException(
                                "Could not create a validated object, cause: " +
                                        t.getMessage());
                    }
                }
            }
        }
        numActive++;
        ref.allocate();
        return obj;
    }

    /**
     * 成功验证和钝化后, 将实例返回到池中.
     * 如果满足以下任何条件，则返回的实例将被销毁:
     * <ul>
     * <li>池被关闭</li>
     * <li>{@link PooledObjectFactory#validateObject(org.apache.tomcat.dbcp.pool2.PooledObject) validation} 失败
     * </li>
     * <li>{@link PooledObjectFactory#passivateObject(org.apache.tomcat.dbcp.pool2.PooledObject) passivation}抛出异常</li>
     * </ul>
     * 忽略钝化或销毁实例的异常. 验证实例的异常会传播到客户端.
     *
     * @param obj 返回到池中的实例
     */
    @Override
    public synchronized void returnObject(final T obj) throws Exception {
        boolean success = !isClosed();
        final PooledSoftReference<T> ref = findReference(obj);
        if (ref == null) {
            throw new IllegalStateException(
                "Returned object not currently part of this pool");
        }
        if (factory != null) {
            if (!factory.validateObject(ref)) {
                success = false;
            } else {
                try {
                    factory.passivateObject(ref);
                } catch (final Exception e) {
                    success = false;
                }
            }
        }

        final boolean shouldDestroy = !success;
        numActive--;
        if (success) {

            // 取消分配并添加到空闲实例池
            ref.deallocate();
            idleReferences.add(ref);
        }
        notifyAll(); // numActive has changed

        if (shouldDestroy && factory != null) {
            try {
                destroy(ref);
            } catch (final Exception e) {
                // ignored
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void invalidateObject(final T obj) throws Exception {
        final PooledSoftReference<T> ref = findReference(obj);
        if (ref == null) {
            throw new IllegalStateException(
                "Object to invalidate is not currently part of this pool");
        }
        if (factory != null) {
            destroy(ref);
        }
        numActive--;
        notifyAll(); // numActive has changed
    }

    /**
     * 创建一个对象，并将其放入池中. addObject()对于使用空闲对象“预加载”池非常有用.
     * <p>
     * 工厂<code>makeObject</code>或<code>passivate</code>生成的异常会传播给调用者.
     * 忽略销毁实例的异常.
     *
     * @throws IllegalStateException 如果在{@link #close() closed}池中调用
     * @throws Exception 当{@link #getFactory() factory}在创建或钝化对象时出现问题
     */
    @Override
    public synchronized void addObject() throws Exception {
        assertOpen();
        if (factory == null) {
            throw new IllegalStateException(
                    "Cannot add objects without a factory.");
        }
        final T obj = factory.makeObject().getObject();
        createCount++;
        // 创建并注册队列
        final PooledSoftReference<T> ref = new PooledSoftReference<>(
                new SoftReference<>(obj, refQueue));
        allReferences.add(ref);

        boolean success = true;
        if (!factory.validateObject(ref)) {
            success = false;
        } else {
            factory.passivateObject(ref);
        }

        final boolean shouldDestroy = !success;
        if (success) {
            idleReferences.add(ref);
            notifyAll(); // numActive has changed
        }

        if (shouldDestroy) {
            try {
                destroy(ref);
            } catch (final Exception e) {
                // ignored
            }
        }
    }

    /**
     * 返回不小于池中空闲实例数的近似值.
     */
    @Override
    public synchronized int getNumIdle() {
        pruneClearedReferences();
        return idleReferences.size();
    }

    /**
     * 返回当前从此池中借用的实例数.
     */
    @Override
    public synchronized int getNumActive() {
        return numActive;
    }

    /**
     * 清除在池中闲置的所有对象.
     */
    @Override
    public synchronized void clear() {
        if (null != factory) {
            final Iterator<PooledSoftReference<T>> iter = idleReferences.iterator();
            while (iter.hasNext()) {
                try {
                    final PooledSoftReference<T> ref = iter.next();
                    if (null != ref.getObject()) {
                        factory.destroyObject(ref);
                    }
                } catch (final Exception e) {
                    // ignore error, keep destroying the rest
                }
            }
        }
        idleReferences.clear();
        pruneClearedReferences();
    }

    /**
     * 关闭此池，并释放与其关联的所有资源. 调用{@link #clear()}来销毁和删除池中的实例.
     * <p>
     * 在池上调用此方法后调用{@link #addObject}或{@link #borrowObject}将导致它们抛出{@link IllegalStateException}.
     */
    @Override
    public void close() {
        super.close();
        clear();
    }

    /**
     * 返回此池使用的{@link PooledObjectFactory}以创建和管理对象实例.
     */
    public synchronized PooledObjectFactory<T> getFactory() {
        return factory;
    }

    /**
     * 如果任何空闲对象被垃圾收集, 从空闲对象池中删除它们的{@link Reference}包装器.
     */
    private void pruneClearedReferences() {
        // 从idle和allReferences列表中删除排队引用的包装器
        removeClearedReferences(idleReferences.iterator());
        removeClearedReferences(allReferences.iterator());
        while (refQueue.poll() != null) {}
    }

    /**
     * 在指向obj的allReferences中找到PooledSoftReference.
     *
     * @param obj 返回对象
     * @return 包含对obj的软引用的PooledSoftReference
     */
    private PooledSoftReference<T> findReference(final T obj) {
        final Iterator<PooledSoftReference<T>> iterator = allReferences.iterator();
        while (iterator.hasNext()) {
            final PooledSoftReference<T> reference = iterator.next();
            if (reference.getObject() != null && reference.getObject().equals(obj)) {
                return reference;
            }
        }
        return null;
    }

    /**
     * 销毁{@code PooledSoftReference}并将其从空闲和所有引用池中删除.
     *
     * @param toDestroy 要销毁的PooledSoftReference
     *
     * @throws Exception 如果在尝试销毁对象时发生错误
     */
    private void destroy(final PooledSoftReference<T> toDestroy) throws Exception {
        toDestroy.invalidate();
        idleReferences.remove(toDestroy);
        allReferences.remove(toDestroy);
        try {
            factory.destroyObject(toDestroy);
        } finally {
            destroyCount++;
            toDestroy.getReference().clear();
        }
    }

    /**
     * 从iterator的集合中清除已清除的引用
     * @param iterator iterator over idle/allReferences
     */
    private void removeClearedReferences(final Iterator<PooledSoftReference<T>> iterator) {
        PooledSoftReference<T> ref;
        while (iterator.hasNext()) {
            ref = iterator.next();
            if (ref.getReference() == null || ref.getReference().isEnqueued()) {
                iterator.remove();
            }
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", factory=");
        builder.append(factory);
        builder.append(", refQueue=");
        builder.append(refQueue);
        builder.append(", numActive=");
        builder.append(numActive);
        builder.append(", destroyCount=");
        builder.append(destroyCount);
        builder.append(", createCount=");
        builder.append(createCount);
        builder.append(", idleReferences=");
        builder.append(idleReferences);
        builder.append(", allReferences=");
        builder.append(allReferences);
    }
}
