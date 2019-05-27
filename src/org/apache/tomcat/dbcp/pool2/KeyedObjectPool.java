package org.apache.tomcat.dbcp.pool2;

import java.util.NoSuchElementException;

/**
 * <p>
 * 为每个键值维护一个实例池.
 * <p>
 * 使用示例:
 * <pre style="border:solid thin; padding: 1ex;"
 * > Object obj = <code style="color:#00C">null</code>;
 * Object key = <code style="color:#C00">"Key"</code>;
 *
 * <code style="color:#00C">try</code> {
 *     obj = pool.borrowObject(key);
 *     <code style="color:#0C0">//...use the object...</code>
 * } <code style="color:#00C">catch</code>(Exception e) {
 *     <code style="color:#0C0">// invalidate the object</code>
 *     pool.invalidateObject(key, obj);
 *     <code style="color:#0C0">// do not return the object to the pool twice</code>
 *     obj = <code style="color:#00C">null</code>;
 * } <code style="color:#00C">finally</code> {
 *     <code style="color:#0C0">// make sure the object is returned to the pool</code>
 *     <code style="color:#00C">if</code>(<code style="color:#00C">null</code> != obj) {
 *         pool.returnObject(key, obj);
 *     }
 * }</pre>
 * <p>
 * {@link KeyedObjectPool}实现可以选择每个键值最多存储一个实例, 或者可以选择为每个键维护一个实例池 (实质上创建一个{@link ObjectPool pools}的 {@link java.util.Map Map}).
 * <p>
 * 查看 {@link org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool}实现.
 *
 * @param <K> 此池维护的键的类型.
 * @param <V> 此池中池化的元素类型.
 */
public interface KeyedObjectPool<K,V> {
    /**
     * 从此池中获取指定<code>key</ code>的实例.
     * <p>
     * 此方法返回的实例将使用{@link KeyedPooledObjectFactory#makeObject makeObject}新创建,
     * 或之前{@link KeyedPooledObjectFactory#activateObject activateObject}激活的空闲对象,
     * 然后使用{@link KeyedPooledObjectFactory#validateObject validateObject}验证 (可选).
     * <p>
     * 客户端必须使用{@link #returnObject returnObject}, {@link #invalidateObject invalidateObject}, 或实现和子接口中定义的相关方法返回借用的对象,
     * 使用相同的 <code>key</code>.
     * <p>
     * 没有严格指定池耗尽时此方法的行为 (虽然它可能由实现指定).
     *
     * @param key 用于获取对象的键
     *
     * @return 来自这个池中的实例.
     *
     * @throws IllegalStateException 在此池上调用{@link #close close}之后
     * @throws Exception {@link KeyedPooledObjectFactory#makeObject makeObject} 抛出异常
     * @throws NoSuchElementException 当池耗尽且不能或不会返回另一个实例时
     */
    V borrowObject(K key) throws Exception, NoSuchElementException, IllegalStateException;

    /**
     * 返回实例到池中.
     * 必须已经使用{@link #borrowObject borrowObject}或实现和子接口中的相关方法获取了<code>obj</code>,
     * 使用相同的<code>key</code>.
     *
     * @param key 用于获取对象的键
     * @param obj {@link #borrowObject borrowed}返回的实例.
     *
     * @throws IllegalStateException 如果尝试将对象返回到处于除分配之外的任何状态的池中 (i.e. borrowed).
     *              尝试多次返回对象或尝试返回未从池中借用的对象.
     *
     * @throws Exception 如果实例不能返回到池中
     */
    void returnObject(K key, V obj) throws Exception;

    /**
     * 使池中的对象无效.
     * <p>
     * 必须已经使用{@link #borrowObject borrowObject}或实现和子接口中的相关方法获取了<code>obj</code>,
     * 使用相同的<code>key</code>.
     * <p>
     * 当已经借用的对象（由于异常或其他问题）无效时，应该使用此方法.
     *
     * @param key 用于获取对象的键
     * @param obj {@link #borrowObject borrowed}返回的实例.
     *
     * @throws Exception 如果实例无法失效
     */
    void invalidateObject(K key, V obj) throws Exception;

    /**
     * 使用{@link KeyedPooledObjectFactory factory}或其他依赖于实现的机制创建对象，对其进行钝化，然后将其放入空闲对象池中.
     * <code>addObject</code>对于使用空闲对象“预加载”池非常有用 (可选操作).
     *
     * @param key 应该添加一个新实例的Key
     *
     * @throws Exception 如果{@link KeyedPooledObjectFactory#makeObject} 失败.
     * @throws IllegalStateException 在此池上调用{@link #close}之后.
     * @throws UnsupportedOperationException 当此池无法添加新的空闲对象时.
     */
    void addObject(K key) throws Exception, IllegalStateException,
            UnsupportedOperationException;

    /**
     * 返回与此池中当前空闲的给定<code>key</code>对应的实例数. 如果此信息不可用, 则返回负值.
     *
     * @param key 要查询的key
     * @return 池中当前空闲的给定<code>key</ code>对应的实例数.
     */
    int getNumIdle(K key);

    /**
     * 返回当前从中借用但尚未返回到与给定<code>key</code>对应的池的实例数.
     * 如果此信息不可用，则返回负值.
     *
     * @param key 要查询的key
     */
    int getNumActive(K key);

    /**
     * 返回此池中当前空闲的实例总数.
     * 如果此信息不可用，则返回负值.
     */
    int getNumIdle();

    /**
     * 返回从此池借用但尚未返回的当前实例总数. 如果此信息不可用，则返回负值.
     */
    int getNumActive();

    /**
     * 清空池, 删除所有的池化的实例 (可选操作).
     *
     * @throws UnsupportedOperationException 此实现不支持该操作
     * @throws Exception 池无法清空
     */
    void clear() throws Exception, UnsupportedOperationException;

    /**
     * 清空指定的池, 删除与给定<code>key</code>对应的所有池实例 (可选操作).
     *
     * @param key 要清空的key
     *
     * @throws UnsupportedOperationException 此实现不支持该操作
     * @throws Exception key无法清空
     */
    void clear(K key) throws Exception, UnsupportedOperationException;

    /**
     * 关闭池, 并释放与之相关的任何资源.
     * <p>
     * 在池上调用此方法后调用{@link #addObject addObject}或{@link #borrowObject borrowObject}将导致它们抛出{@link IllegalStateException}.
     * <p>
     * 如果不能释放所有资源, 实现应该默默地失败.
     */
    void close();
}
