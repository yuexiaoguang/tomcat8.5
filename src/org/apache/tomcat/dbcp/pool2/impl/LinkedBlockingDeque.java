package org.apache.tomcat.dbcp.pool2.impl;

import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * 基于链接节点的可选有界{@linkplain java.util.concurrent.BlockingDeque blocking deque}.
 *
 * <p>可选的容量绑定构造函数参数用作防止过度扩展的方法. 未指定容量, 等于 {@link Integer#MAX_VALUE}.
 * 每次插入时都会动态创建链接节点，除非这会使deque超出容量.
 *
 * <p>大多数操作都是在恒定时间运行 (忽略阻塞的时间).
 *
 * <p>该类及其迭代器实现了{@link Collection}和{@link Iterator}接口的所有<em>可选</ em>方法.
 *
 * @param <E> 此集合中保存的元素类型
 *
 * Note: 这是从Apache Harmony复制并修改，以满足Commons Pool的需求.
 */
class LinkedBlockingDeque<E> extends AbstractQueue<E> implements Deque<E>, Serializable {

    /*
     * 实现为一个简单的双向链表，受单个锁保护并使用条件来管理阻塞.
     *
     * 实现弱一致的迭代器, 需要保持对于所有节点GC都可以从之前出列的节点中到达.
     * 这会导致两个问题:
     * - 允许恶意迭代器导致无限制的内存保留
     * - 导致旧节点与新节点的跨世代链接, 如果一个节点在生存期间被永久保存, 由哪个代的GC处理很难, 导致重复的主要集合.
     * 
     * 但是, 只需要从出列的节点可以访问未删除的节点, 并且可达性不一定必须是GC所理解的那种. 使用将刚刚出列的Node链接到自身的技巧.
     * 这种隐式的自我链接意味着跳转到 "first" (下一个链接)或 "last" (前一个链接).
     */

    private static final long serialVersionUID = -387911632671998426L;

    /** 双链表节点类 */
    private static final class Node<E> {
        /**
         * 条目, 或 null 如果此节点已被删除.
         */
        E item;

        /**
         * 其中之一:
         * - 真正的前任节点
         * - 这个Node, 意味着前任是尾
         * - null, 没有前任
         */
        Node<E> prev;

        /**
         * 其中之一:
         * - 真正的后继节点
         * - 这个Node, 后继是头
         * - null, 没有后继
         */
        Node<E> next;

        /**
         * @param x 当前节点
         * @param p 前任节点
         * @param n 后继节点
         */
        Node(final E x, final Node<E> p, final Node<E> n) {
            item = x;
            prev = p;
            next = n;
        }
    }

    /**
     * 指向第一个节点的指针.
     * Invariant: (first == null && last == null) ||
     *            (first.prev == null && first.item != null)
     */
    private transient Node<E> first; // @GuardedBy("lock")

    /**
     * 指向最后一个节点的指针.
     * Invariant: (first == null && last == null) ||
     *            (last.next == null && last.item != null)
     */
    private transient Node<E> last; // @GuardedBy("lock")

    /** 双端队列中的节点数量 */
    private transient int count; // @GuardedBy("lock")

    /** 双端队列的容量*/
    private final int capacity;

    /** 主锁保护所有访问 */
    private final InterruptibleReentrantLock lock;

    /** 等待取出的条件 */
    private final Condition notEmpty;

    /** 等待放入的条件 */
    private final Condition notFull;

    public LinkedBlockingDeque() {
        this(Integer.MAX_VALUE);
    }

    /**
     * @param fairness true 表示在双端队列上等待的线程应该像在FIFO请求队列中等待一样
     */
    public LinkedBlockingDeque(final boolean fairness) {
        this(Integer.MAX_VALUE, fairness);
    }

    /**
     * @param capacity 容量
     * 
     * @throws IllegalArgumentException 如果{@code capacity}小于 1
     */
    public LinkedBlockingDeque(final int capacity) {
        this(capacity, false);
    }

    /**
     * @param capacity 容量
     * @param fairness true 表示在双端队列上等待的线程应该像在FIFO请求队列中等待一样
     * 
     * @throws IllegalArgumentException 如果{@code capacity}小于 1
     */
    public LinkedBlockingDeque(final int capacity, final boolean fairness) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
        lock = new InterruptibleReentrantLock(fairness);
        notEmpty = lock.newCondition();
        notFull = lock.newCondition();
    }

    /**
     * @param c 最初包含的元素集合
     * 
     * @throws NullPointerException 如果指定的集合或其任何元素为null
     */
    public LinkedBlockingDeque(final Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        lock.lock(); // 永远不要争辩, 但是可见性是必要的
        try {
            for (final E e : c) {
                if (e == null) {
                    throw new NullPointerException();
                }
                if (!linkLast(e)) {
                    throw new IllegalStateException("Deque full");
                }
            }
        } finally {
            lock.unlock();
        }
    }


    // Basic linking and unlinking operations, called only while holding lock

    /**
     * 链接提供的元素作为第一个元素; 如果满了, 返回 false.
     *
     * @param e 要链接为第一个元素的元素.
     *
     * @return {@code true} 成功, 否则 {@code false}
     */
    private boolean linkFirst(final E e) {
        // assert lock.isHeldByCurrentThread();
        if (count >= capacity) {
            return false;
        }
        final Node<E> f = first;
        final Node<E> x = new Node<>(e, null, f);
        first = x;
        if (last == null) {
            last = x;
        } else {
            f.prev = x;
        }
        ++count;
        notEmpty.signal();
        return true;
    }

    /**
     * 链接提供的元素作为最后一个元素; 如果满了, 返回 false.
     *
     * @param e 要链接为最后一个元素的元素.
     *
     * @return {@code true} 成功, 否则 {@code false}
     */
    private boolean linkLast(final E e) {
        // assert lock.isHeldByCurrentThread();
        if (count >= capacity) {
            return false;
        }
        final Node<E> l = last;
        final Node<E> x = new Node<>(e, l, null);
        last = x;
        if (first == null) {
            first = x;
        } else {
            l.next = x;
        }
        ++count;
        notEmpty.signal();
        return true;
    }

    /**
     * 删除并返回第一个元素; 如果是空的返回 null.
     *
     * @return 第一个元素或 {@code null}
     */
    private E unlinkFirst() {
        // assert lock.isHeldByCurrentThread();
        final Node<E> f = first;
        if (f == null) {
            return null;
        }
        final Node<E> n = f.next;
        final E item = f.item;
        f.item = null;
        f.next = f; // help GC
        first = n;
        if (n == null) {
            last = null;
        } else {
            n.prev = null;
        }
        --count;
        notFull.signal();
        return item;
    }

    /**
     * 删除并返回最后一个元素; 如果是空的返回 null.
     *
     * @return 最后一个元素或 {@code null}
     */
    private E unlinkLast() {
        // assert lock.isHeldByCurrentThread();
        final Node<E> l = last;
        if (l == null) {
            return null;
        }
        final Node<E> p = l.prev;
        final E item = l.item;
        l.item = null;
        l.prev = l; // help GC
        last = p;
        if (p == null) {
            first = null;
        } else {
            p.next = null;
        }
        --count;
        notFull.signal();
        return item;
    }

    /**
     * 取消链接提供的节点.
     *
     * @param x 要取消连接的节点
     */
    private void unlink(final Node<E> x) {
        // assert lock.isHeldByCurrentThread();
        final Node<E> p = x.prev;
        final Node<E> n = x.next;
        if (p == null) {
            unlinkFirst();
        } else if (n == null) {
            unlinkLast();
        } else {
            p.next = n;
            n.prev = p;
            x.item = null;
            // 不要乱用x的链接. 它们可能仍在由迭代器使用.
        --count;
            notFull.signal();
        }
    }

    // BlockingDeque methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFirst(final E e) {
        if (!offerFirst(e)) {
            throw new IllegalStateException("Deque full");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addLast(final E e) {
        if (!offerLast(e)) {
            throw new IllegalStateException("Deque full");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offerFirst(final E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        lock.lock();
        try {
            return linkFirst(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offerLast(final E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        lock.lock();
        try {
            return linkLast(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将提供的元素链接为队列中的第一个元素; 如果队列已满, 则等待有空间.
     *
     * @param e 要链接的元素
     *
     * @throws NullPointerException 如果 e 是 null
     * @throws InterruptedException 如果线程在等待空间时被中断
     */
    public void putFirst(final E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        lock.lock();
        try {
            while (!linkFirst(e)) {
                notFull.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将提供的元素链接为队列中的最后一个元素; 如果队列已满, 则等待有空间.
     *
     * @param e 要链接的元素
     *
     * @throws NullPointerException 如果 e 是 null
     * @throws InterruptedException 如果线程在等待空间时被中断
     */
    public void putLast(final E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        lock.lock();
        try {
            while (!linkLast(e)) {
                notFull.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将提供的元素链接为队列中的第一个元素; 如果队列已满, 则等待指定的时间.
     *
     * @param e         要链接的元素
     * @param timeout   等待的时间长度
     * @param unit      时间单位
     *
     * @return {@code true} 成功, 否则 {@code false}
     *
     * @throws NullPointerException 如果 e 是 null
     * @throws InterruptedException 如果线程在等待空间时被中断
     */
    public boolean offerFirst(final E e, final long timeout, final TimeUnit unit)
        throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (!linkFirst(e)) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将提供的元素链接为队列中的最后一个元素; 如果队列已满, 则等待指定的时间.
     *
     * @param e         要链接的元素
     * @param timeout   等待的时间长度
     * @param unit      时间单位
     *
     * @return {@code true} 成功, 否则 {@code false}
     *
     * @throws NullPointerException 如果 e 是 null
     * @throws InterruptedException 如果线程在等待空间时被中断
     */
    public boolean offerLast(final E e, final long timeout, final TimeUnit unit)
        throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (!linkLast(e)) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E removeFirst() {
        final E x = pollFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E removeLast() {
        final E x = pollLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override
    public E pollFirst() {
        lock.lock();
        try {
            return unlinkFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E pollLast() {
        lock.lock();
        try {
            return unlinkLast();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消链接队列中的第一个元素; 如果队列为空, 等待直到有一个要取消链接的元素.
     *
     * @return 取消链接的元素
     * @throws InterruptedException 如果当前线程被中断
     */
    public E takeFirst() throws InterruptedException {
        lock.lock();
        try {
            E x;
            while ( (x = unlinkFirst()) == null) {
                notEmpty.await();
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消链接队列中的最后一个元素; 如果队列为空, 等待直到有一个要取消链接的元素.
     *
     * @return 取消链接的元素
     * @throws InterruptedException 如果当前线程被中断
     */
    public E takeLast() throws InterruptedException {
        lock.lock();
        try {
            E x;
            while ( (x = unlinkLast()) == null) {
                notEmpty.await();
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消链接队列中的第一个元素; 如果队列为空, 等待指定的时间.
     *
     * @param timeout   等待的时间
     * @param unit      时间单位
     *
     * @return 取消链接的元素
     * @throws InterruptedException 如果当前线程被中断
     */
    public E pollFirst(final long timeout, final TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            E x;
            while ( (x = unlinkFirst()) == null) {
                if (nanos <= 0) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消链接队列中的最后一个元素; 如果队列为空, 等待指定的时间.
     *
     * @param timeout   等待的时间
     * @param unit      时间单位
     *
     * @return 取消链接的元素
     * @throws InterruptedException 如果当前线程被中断
     */
    public E pollLast(final long timeout, final TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            E x;
            while ( (x = unlinkLast()) == null) {
                if (nanos <= 0) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E getFirst() {
        final E x = peekFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E getLast() {
        final E x = peekLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override
    public E peekFirst() {
        lock.lock();
        try {
            return first == null ? null : first.item;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E peekLast() {
        lock.lock();
        try {
            return last == null ? null : last.item;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeFirstOccurrence(final Object o) {
        if (o == null) {
            return false;
        }
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next) {
                if (o.equals(p.item)) {
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeLastOccurrence(final Object o) {
        if (o == null) {
            return false;
        }
        lock.lock();
        try {
            for (Node<E> p = last; p != null; p = p.prev) {
                if (o.equals(p.item)) {
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    // BlockingQueue methods

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(final E e) {
        addLast(e);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offer(final E e) {
        return offerLast(e);
    }

    /**
     * 将提供的元素链接为队列中的最后一个元素; 如果队列已满, 则等待有空间.
     *
     * <p>等效于 {@link #putLast(Object)}.
     *
     * @param e 要链接的元素
     *
     * @throws NullPointerException 如果 e 是 null
     * @throws InterruptedException 如果线程在等待空间时被中断
     */
    public void put(final E e) throws InterruptedException {
        putLast(e);
    }

    /**
     * 将提供的元素链接为队列中的最后一个元素; 如果队列已满, 则等待指定的时间.
     * <p>
     * 等效于 {@link #offerLast(Object, long, TimeUnit)}
     *
     * @param e         要链接的元素
     * @param timeout   等待的时间长度
     * @param unit      时间单位
     *
     * @return {@code true} 成功, 否则 {@code false}
     *
     * @throws NullPointerException 如果 e 是 null
     * @throws InterruptedException 如果线程在等待空间时被中断
     */
    public boolean offer(final E e, final long timeout, final TimeUnit unit) throws InterruptedException {
        return offerLast(e, timeout, unit);
    }

    /**
     * 检索并删除此双端队列的头部.
     * 这个方法不同于 {@link #poll poll}, 只有当这个双端队列为空时才抛出异常.
     *
     * <p>等效于 {@link #removeFirst() removeFirst}.
     *
     * @return 此双端队列的头部
     * @throws NoSuchElementException 如果这个双端队列是空的
     */
    @Override
    public E remove() {
        return removeFirst();
    }

    @Override
    public E poll() {
        return pollFirst();
    }

    /**
     * 取消链接队列中的第一个元素; 如果队列为空, 等待直到有一个要取消链接的元素.
     *
     * <p>等效于 {@link #takeFirst()}.
     *
     * @return 取消链接的元素
     * @throws InterruptedException 如果当前线程被中断
     */
    public E take() throws InterruptedException {
        return takeFirst();
    }

    /**
     * 取消链接队列中的第一个元素; 如果队列为空, 等待指定的时间.
     *
     * <p>等效于 {@link #pollFirst(long, TimeUnit)}.
     *
     * @param timeout   等待的时间
     * @param unit      时间单位
     *
     * @return 取消链接的元素
     * @throws InterruptedException 如果当前线程被中断
     */
    public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        return pollFirst(timeout, unit);
    }

    /**
     * 检索但不删除此双端队列的头部.
     * 这个方法不同于 {@link #peek peek}, 只有当这个双端队列为空时才抛出异常.
     *
     * <p>等效于 {@link #getFirst() getFirst}.
     *
     * @return 此双端队列的头部
     * @throws NoSuchElementException 如果这个双端队列是空的
     */
    @Override
    public E element() {
        return getFirst();
    }

    @Override
    public E peek() {
        return peekFirst();
    }

    /**
     * 返回此双端队列理想情况下（在没有内存或资源限制的情况下）可以无阻塞地接受的其他元素的数量.
     * 这总是等于此双端队列的初始容量减去此双端队列的当前{@code size}.
     *
     * <p>请注意，无法通过检查{@code remainingCapacity}来判断插入元素的尝试是否成功，因为可能是另一个线程即将插入或删除元素的情况.
     *
     * @return 队列能够接受的其他元素的数量
     */
    public int remainingCapacity() {
        lock.lock();
        try {
            return capacity - count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 将队列清空到指定的集合.
     *
     * @param c 要添加元素的集合
     *
     * @return 添加到集合中的元素数量
     *
     * @throws UnsupportedOperationException 如果指定的集合不支持添加操作
     * @throws ClassCastException 如果此集合持有的元素类阻止将它们添加到指定的集合中
     * @throws NullPointerException 如果 c 是 null
     * @throws IllegalArgumentException 如果 c 是这个实例
     */
    public int drainTo(final Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    /**
     * 清空不超过指定数量的元素, 从队列到指定的集合.
     *
     * @param c           要添加元素的集合
     * @param maxElements 要从队列中删除的最大元素数
     *
     * @return 添加到集合中的元素数量
     * 
     * @throws UnsupportedOperationException 如果指定的集合不支持添加操作
     * @throws ClassCastException 如果此集合持有的元素类阻止将它们添加到指定的集合中
     * @throws NullPointerException 如果 c 是 null
     * @throws IllegalArgumentException 如果 c 是这个实例
     */
    public int drainTo(final Collection<? super E> c, final int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        lock.lock();
        try {
            final int n = Math.min(maxElements, count);
            for (int i = 0; i < n; i++) {
                c.add(first.item);   // In this order, in case add() throws.
                unlinkFirst();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    // Stack methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void push(final E e) {
        addFirst(e);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E pop() {
        return removeFirst();
    }

    // Collection methods

    /**
     * 从此双端队列中删除第一次出现的指定元素.
     * 如果双端队列不包含该元素，则不会更改. 更正式地，删除第一个元素{@code e}，使用{@code o.equals(e)} (如果存在这样的元素).
     * 返回 {@code true}, 如果此双端队列包含指定的元素 (或者等价的, 如果这个双端队列因为调用改变了).
     *
     * <p>等效于 {@link #removeFirstOccurrence(Object) removeFirstOccurrence}.
     *
     * @param o 要从此双端队列中删除的元素
     * @return {@code true} 如果这个双端队列因为调用改变了
     */
    @Override
    public boolean remove(final Object o) {
        return removeFirstOccurrence(o);
    }

    /**
     * 返回此双端队列中的元素数量.
     */
    @Override
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 如果此双端队列包含指定的元素，则返回{@code true}.
     *
     * @param o 要在此双端队列中检查包含的对象
     * @return {@code true} 如果此双端队列包含指定的元素
     */
    @Override
    public boolean contains(final Object o) {
        if (o == null) {
            return false;
        }
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next) {
                if (o.equals(p.item)) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /*
     * TODO: Add support for more efficient bulk operations.
     *
     * 不想为每次迭代获取锁, 但也希望其他线程有机会与该集合进行交互, 特别是当计数接近容量时.
     */

//     /**
//      * Adds all of the elements in the specified collection to this
//      * queue.  Attempts to addAll of a queue to itself result in
//      * {@code IllegalArgumentException}. Further, the behavior of
//      * this operation is undefined if the specified collection is
//      * modified while the operation is in progress.
//      *
//      * @param c collection containing elements to be added to this queue
//      * @return {@code true} if this queue changed as a result of the call
//      * @throws ClassCastException
//      * @throws NullPointerException
//      * @throws IllegalArgumentException
//      * @throws IllegalStateException
//      * @see #add(Object)
//      */
//     public boolean addAll(Collection<? extends E> c) {
//         if (c == null)
//             throw new NullPointerException();
//         if (c == this)
//             throw new IllegalArgumentException();
//         final ReentrantLock lock = this.lock;
//         lock.lock();
//         try {
//             boolean modified = false;
//             for (E e : c)
//                 if (linkLast(e))
//                     modified = true;
//             return modified;
//         } finally {
//             lock.unlock();
//         }
//     }

    /**
     * 以适当的顺序（从第一个元素到最后一个元素）返回一个包含此双端队列中所有元素的数组.
     *
     * <p>返回的数组将是“安全的”，因为此双端队列不会保留对它的引用. (换句话说，此方法必须分配一个新数组). 因此调用者可以自由修改返回的数组.
     *
     * <p>此方法充当基于阵列和基于集合的API之间的桥梁.
     *
     * @return 包含此双端队列中所有元素的数组
     */
    @Override
    public Object[] toArray() {
        lock.lock();
        try {
            final Object[] a = new Object[count];
            int k = 0;
            for (Node<E> p = first; p != null; p = p.next) {
                a[k++] = p.item;
            }
            return a;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            if (a.length < count) {
                a = (T[])java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), count);
            }
            int k = 0;
            for (Node<E> p = first; p != null; p = p.next) {
                a[k++] = (T)p.item;
            }
            if (a.length > k) {
                a[k] = null;
            }
            return a;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return super.toString();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 原子地从该双端队列中移除所有元素. 此调用返回后，双端队列将为空.
     */
    @Override
    public void clear() {
        lock.lock();
        try {
            for (Node<E> f = first; f != null;) {
                f.item = null;
                final Node<E> n = f.next;
                f.prev = null;
                f.next = null;
                f = n;
            }
            first = last = null;
            count = 0;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 以适当的顺序返回此双端队列中元素的迭代器.
     * 元素将按从头结点到尾节点的顺序返回. 返回的 {@code Iterator} 是一个 "弱一致" 的迭代器,
     * 不会抛出 {@link java.util.ConcurrentModificationException ConcurrentModificationException},
     * 并且保证在构造迭代器时遍历元素, 并且可以（但不保证）反映构造后的任何修改.
     *
     * @return 以适当的顺序在此双端队列中的元素上的迭代器
     */
    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<E> descendingIterator() {
        return new DescendingItr();
    }

    /**
     * LinkedBlockingDeque的迭代器的基类
     */
    private abstract class AbstractItr implements Iterator<E> {
        /**
         * next()中返回的下一个节点
         */
         Node<E> next;

        /**
         * 保存item字段，因为一旦声称hasNext()中存在一个元素, 必须在锁定下返回 item (in advance()), 即使在调用hasNext()时它正在被删除的过程中.
         */
        E nextItem;

        /**
         * 最近一次调用 next 返回的节点. 需要删除.
         * 如果通过调用remove删除此元素，则重置为null.
         */
        private Node<E> lastRet;

        /**
         * 获取迭代器返回的第一个节点.
         */
        abstract Node<E> firstNode();

        /**
         * 对于给定节点，获取迭代器返回的下一个节点.
         *
         * @param n 给定的节点
         */
        abstract Node<E> nextNode(Node<E> n);

        /**
         * 创建一个新的迭代器. 设置初始位置.
         */
        AbstractItr() {
            // 设置为初始位置
            lock.lock();
            try {
                next = firstNode();
                nextItem = next == null ? null : next.item;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 返回给定非null但可能先前已删除的节点的后继节点.
         *
         * @param n 寻求后继节点的节点
         * @return 后继节点
         */
        private Node<E> succ(Node<E> n) {
            // 如果删除了多个内部节点，则可以使用以null或自链接结束已删除节点的链.
            for (;;) {
                final Node<E> s = nextNode(n);
                if (s == null) {
                    return null;
                } else if (s.item != null) {
                    return s;
                } else if (s == n) {
                    return firstNode();
                } else {
                    n = s;
                }
            }
        }

        /**
         * Advances next.
         */
        void advance() {
            lock.lock();
            try {
                // assert next != null;
                next = succ(next);
                nextItem = next == null ? null : next.item;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public E next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            lastRet = next;
            final E x = nextItem;
            advance();
            return x;
        }

        @Override
        public void remove() {
            final Node<E> n = lastRet;
            if (n == null) {
                throw new IllegalStateException();
            }
            lastRet = null;
            lock.lock();
            try {
                if (n.item != null) {
                    unlink(n);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /** 转发迭代器 */
    private class Itr extends AbstractItr {
        @Override
        Node<E> firstNode() { return first; }
        @Override
        Node<E> nextNode(final Node<E> n) { return n.next; }
        }

    /** 降序迭代器 */
    private class DescendingItr extends AbstractItr {
        @Override
        Node<E> firstNode() { return last; }
        @Override
        Node<E> nextNode(final Node<E> n) { return n.prev; }
    }

    /**
     * 将此双端队列的状态保存为流 (序列化它).
     *
     * @serialData The capacity (int), followed by elements (each an
     * {@code Object}) in the proper order, followed by a null
     * @param s the stream
     */
    private void writeObject(final java.io.ObjectOutputStream s)
        throws java.io.IOException {
        lock.lock();
        try {
            // 写出容量和任何隐藏的东西
            s.defaultWriteObject();
            // 按正确的顺序写出所有元素.
            for (Node<E> p = first; p != null; p = p.next) {
                s.writeObject(p.item);
            }
            // 使用结尾null作为 sentinel
            s.writeObject(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从流中重构此双端队列 (反序列化).
     * @param s the stream
     */
    private void readObject(final java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        count = 0;
        first = null;
        last = null;
        // 读入所有元素并放入队列中
        for (;;) {
            @SuppressWarnings("unchecked")
            final
            E item = (E)s.readObject();
            if (item == null) {
                break;
            }
            add(item);
        }
    }

    // Monitoring methods

    /**
     *如果有线程等待从此双端队列中获取实例，则返回true.
     *
     * @return true 如果至少有一个线程等待此双端队列的notEmpty条件.
     */
    public boolean hasTakeWaiters() {
        lock.lock();
        try {
            return lock.hasWaiters(notEmpty);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回等待从此双端队列中获取实例的线程队列的长度.
     *
     * @return 等待此双端队列的notEmpty条件的线程数.
     */
    public int getTakeQueueLength() {
        lock.lock();
        try {
           return lock.getWaitQueueLength(notEmpty);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 中断当前正在等待从池中获取对象的线程.
     */
    public void interuptTakeWaiters() {
        lock.lock();
        try {
           lock.interruptWaiters(notEmpty);
        } finally {
            lock.unlock();
        }
    }
}
