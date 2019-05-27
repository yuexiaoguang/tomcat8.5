package org.apache.tomcat.dbcp.pool2;

import java.util.NoSuchElementException;

/**
 * <p>
 * 使用示例:
 * <pre style="border:solid thin; padding: 1ex;"
 * > Object obj = <code style="color:#00C">null</code>;
 *
 * <code style="color:#00C">try</code> {
 *     obj = pool.borrowObject();
 *     <code style="color:#00C">try</code> {
 *         <code style="color:#0C0">//...use the object...</code>
 *     } <code style="color:#00C">catch</code>(Exception e) {
 *         <code style="color:#0C0">// invalidate the object</code>
 *         pool.invalidateObject(obj);
 *         <code style="color:#0C0">// do not return the object to the pool twice</code>
 *         obj = <code style="color:#00C">null</code>;
 *     } <code style="color:#00C">finally</code> {
 *         <code style="color:#0C0">// make sure the object is returned to the pool</code>
 *         <code style="color:#00C">if</code>(<code style="color:#00C">null</code> != obj) {
 *             pool.returnObject(obj);
 *        }
 *     }
 * } <code style="color:#00C">catch</code>(Exception e) {
 *       <code style="color:#0C0">// failed to borrow an object</code>
 * }</pre>
 * <p>
 * 查看 {@link org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool}实现.
 *
 * @param <T> 此池中池化的元素类型.
 */
public interface ObjectPool<T> {
    /**
     * 从此池中获取实例.
     * <p>
     * 此方法返回的实例将使用{@link PooledObjectFactory#makeObject}新创建,
     * 或之前{@link PooledObjectFactory#activateObject}激活的空闲对象,
     * 然后使用{@link PooledObjectFactory#validateObject}验证 (可选).
     * <p>
     * 客户端必须使用{@link #returnObject returnObject}, {@link #invalidateObject invalidateObject}, 或实现和子接口中定义的相关方法返回借用的对象.
     * <p>
     * 没有严格指定池耗尽时此方法的行为 (虽然它可能由实现指定).
     *
     * @return 来自这个池中的实例.
     *
     * @throws IllegalStateException 在此池上调用{@link #close close}之后.
     * @throws Exception {@link PooledObjectFactory#makeObject} 抛出异常
     * @throws NoSuchElementException 当池耗尽且不能或不会返回另一个实例时
     */
    T borrowObject() throws Exception, NoSuchElementException,
            IllegalStateException;

    /**
     * 返回实例到池中.
     * 必须已经使用{@link #borrowObject()}或实现和子接口中的相关方法获取了<code>obj</code>.
     *
     * @param obj {@link #borrowObject()}返回的实例.
     *
     * @throws IllegalStateException 如果尝试将对象返回到处于除分配之外的任何状态的池中 (i.e. borrowed).
     *              尝试多次返回对象或尝试返回未从池中借用的对象.
     *
     * @throws Exception 如果实例不能返回到池中
     */
    void returnObject(T obj) throws Exception;

    /**
     * 使池中的对象无效.
     * <p>
     * 必须已经使用{@link #borrowObject}或实现和子接口中的相关方法获取了<code>obj</code>.
     * <p>
     * 当已经借用的对象（由于异常或其他问题）无效时，应该使用此方法.
     *
     * @param obj {@link #borrowObject borrowed}返回的实例.
     *
     * @throws Exception 如果实例无法失效
     */
    void invalidateObject(T obj) throws Exception;

    /**
     * 使用{@link PooledObjectFactory factory}或其他依赖于实现的机制创建对象，对其进行钝化，然后将其放入空闲对象池中.
     * <code>addObject</code>对于使用空闲对象“预加载”池非常有用 (可选操作).
     *
     * @throws Exception 如果{@link PooledObjectFactory#makeObject} 失败.
     * @throws IllegalStateException 在此池上调用{@link #close}之后.
     * @throws UnsupportedOperationException 当此池无法添加新的空闲对象时.
     */
    void addObject() throws Exception, IllegalStateException,
            UnsupportedOperationException;

    /**
     * 返回此池中当前空闲的实例数. 这可以被认为是在不创建任何新实例的情况下可以{@link #borrowObject}的对象数量的近似值.
     * 如果此信息不可用, 则返回负值.
     */
    int getNumIdle();

    /**
     * 返回当前从此池中借用的实例数. 如果此信息不可用, 则返回负值.
     */
    int getNumActive();

    /**
     * 清除在池中所有空闲的对象, 释放所有关联的资源 (可选操作).
     * 清空的空闲对象必须被 {@link PooledObjectFactory#destroyObject(PooledObject)}.
     *
     * @throws UnsupportedOperationException 此实现不支持该操作
     * @throws Exception 如果池无法清空
     */
    void clear() throws Exception, UnsupportedOperationException;

    /**
     * 关闭池, 并释放与之相关的任何资源.
     * <p>
     * 在池上调用此方法后调用{@link #addObject}或{@link #borrowObject}将导致它们抛出{@link IllegalStateException}.
     * <p>
     * 如果不能释放所有资源, 实现应该默默地失败.
     */
    void close();
}
