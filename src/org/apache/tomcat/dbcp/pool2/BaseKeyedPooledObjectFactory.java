package org.apache.tomcat.dbcp.pool2;

/**
 * <code>KeyedPooledObjectFactory</code>的基础实现类.
 * <p>
 * 这里定义的所有操作基本上都是无操作的.
 * </p>
 * 这个类是不可变的, 因此线程安全.
 *
 * @param <K> 此工厂管理的Key的类型.
 * @param <V> 此工厂管理的元素的类型.
 */
public abstract class BaseKeyedPooledObjectFactory<K,V> extends BaseObject
        implements KeyedPooledObjectFactory<K,V> {

    /**
     * 创建可由池提供服务的实例.
     *
     * @param key 构造对象时使用的Key
     * @return 池服务的实例
     *
     * @throws Exception 如果创建新实例时出现问题, 则会将其传播到请求对象的代码.
     */
    public abstract V create(K key)
        throws Exception;

    /**
     * 使用{@link PooledObject}的实现封装提供的实例.
     *
     * @param value 要封装的实例
     *
     * @return 提供的由 {@link PooledObject}封装的实例
     */
    public abstract PooledObject<V> wrap(V value);

    @Override
    public PooledObject<V> makeObject(final K key) throws Exception {
        return wrap(create(key));
    }

    /**
     * 销毁池不再需要的实例.
     * <p>
     * 默认实现是无操作.
     *
     * @param key 选择实例时使用的密钥
     * @param p 一个要销毁的{@code PooledObject}封装的实例
     */
    @Override
    public void destroyObject(final K key, final PooledObject<V> p)
        throws Exception {
    }

    /**
     * 确保池可以安全地返回实例.
     * <p>
     * 默认实现总是返回 {@code true}.
     *
     * @param key 选择对象时使用的Key
     * @param p 要验证的{@code PooledObject}封装的实例
     * 
     * @return 默认实现总是返回 <code>true</code>
     */
    @Override
    public boolean validateObject(final K key, final PooledObject<V> p) {
        return true;
    }

    /**
     * 重新初始化池返回的实例.
     * <p>
     * 默认实现无操作.
     *
     * @param key 选择对象时使用的Key
     * @param p 要激活的{@code PooledObject}封装的实例
     */
    @Override
    public void activateObject(final K key, final PooledObject<V> p)
        throws Exception {
    }

    /**
     * 取消初始化要返回到空闲对象池的实例.
     * <p>
     * 默认实现无操作.
     *
     * @param key 选择对象时使用的Key
     * @param p 要钝化的{@code PooledObject}封装的实例
     */
    @Override
    public void passivateObject(final K key, final PooledObject<V> p)
        throws Exception {
    }
}
