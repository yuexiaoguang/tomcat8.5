package org.apache.tomcat.dbcp.pool2;

/**
 * <code>PoolableObjectFactory</code>基础实现.
 * <p>
 * 这里定义的所有操作基本上都是无操作的.
 * <p>
 * 这个类是不可变的, 因此线程安全
 *
 * @param <T> 此工厂中管理的元素类型.
 */
public abstract class BasePooledObjectFactory<T> extends BaseObject implements PooledObjectFactory<T> {
    /**
     * 创建一个对象实例, 包装在{@link PooledObject}中.
     * <p>这个方法必须支持并发, 多线程操作.</p>
     *
     * @return 池提供的实例
     *
     * @throws Exception 如果创建新实例时出现问题, 这将传播到请求对象的代码.
     */
    public abstract T create() throws Exception;

    /**
     * 使用{@link PooledObject}的实现封装提供的实例.
     *
     * @param obj 要封装的实例
     *
     * @return 提供的由{@link PooledObject}封装的实例
     */
    public abstract PooledObject<T> wrap(T obj);

    @Override
    public PooledObject<T> makeObject() throws Exception {
        return wrap(create());
    }

    /**
     *  No-op.
     *
     *  @param p ignored
     */
    @Override
    public void destroyObject(final PooledObject<T> p)
        throws Exception  {
    }

    /**
     * 总是返回 {@code true}.
     *
     * @param p ignored
     *
     * @return {@code true}
     */
    @Override
    public boolean validateObject(final PooledObject<T> p) {
        return true;
    }

    /**
     *  No-op.
     *
     *  @param p ignored
     */
    @Override
    public void activateObject(final PooledObject<T> p) throws Exception {
    }

    /**
     *  No-op.
     *
     * @param p ignored
     */
    @Override
    public void passivateObject(final PooledObject<T> p)
        throws Exception {
    }
}
