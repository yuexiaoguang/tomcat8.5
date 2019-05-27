package org.apache.tomcat.dbcp.pool2;

/**
 * {@link ObjectPool}的基础实现类.
 * 可选操作实现不执行任何操作, 返回一个值表明它不受支持, 或抛出{@link UnsupportedOperationException}.
 * <p>
 * 此类是线程安全的.
 *
 * @param <T> 此池中池化的元素类型.
 */
public abstract class BaseObjectPool<T> extends BaseObject implements ObjectPool<T> {

    @Override
    public abstract T borrowObject() throws Exception;

    @Override
    public abstract void returnObject(T obj) throws Exception;

    @Override
    public abstract void invalidateObject(T obj) throws Exception;

    /**
     * 此基本实现不支持.
     */
    @Override
    public int getNumIdle() {
        return -1;
    }

    /**
     * 此基本实现不支持.
     */
    @Override
    public int getNumActive() {
        return -1;
    }

    /**
     * 此基本实现不支持.
     *
     * @throws UnsupportedOperationException 如果池没有实现此方法
     */
    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * 此基本实现不支持. 子类应该覆盖此行为.
     *
     * @throws UnsupportedOperationException 如果池没有实现此方法
     */
    @Override
    public void addObject() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 影响 <code>isClosed</code> 和 <code>assertOpen</code>的行为.
     */
    @Override
    public void close() {
        closed = true;
    }

    /**
     * 此池实例是否已关闭.
     *
     * @return <code>true</code> 池已经关闭.
     */
    public final boolean isClosed() {
        return closed;
    }

    /**
     * 此池关闭后, 抛出<code>IllegalStateException</code>.
     *
     * @throws IllegalStateException 这个池已经关闭.
     */
    protected final void assertOpen() throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    private volatile boolean closed = false;

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("closed=");
        builder.append(closed);
    }
}
