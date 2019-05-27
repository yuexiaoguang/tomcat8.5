package org.apache.tomcat.dbcp.dbcp2;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;

/**
 * {@link DelegatingPreparedStatement}和 {@link PoolingConnection}一起实现了 {@link PreparedStatement}的池.
 * <p>
 * {@link #close} 方法将这个对象返回到池中. (See {@link PoolingConnection}.)
 *
 * @param <K> the key type
 */
public class PoolablePreparedStatement<K> extends DelegatingPreparedStatement {
    /**
     * 获取这个对象的 {@link KeyedObjectPool}.
     */
    private final KeyedObjectPool<K, PoolablePreparedStatement<K>> _pool;

    /**
     * {@link KeyedObjectPool} 中这个对象的"key".
     */
    private final K _key;

    private volatile boolean batchAdded = false;

    /**
     * @param stmt 底层{@link PreparedStatement}
     * @param key {@link KeyedObjectPool} 中这个对象的"key"
     * @param pool 获取这个对象的 {@link KeyedObjectPool}
     * @param conn 创建这个对象的{@link java.sql.Connection Connection}
     */
    public PoolablePreparedStatement(final PreparedStatement stmt, final K key,
            final KeyedObjectPool<K, PoolablePreparedStatement<K>> pool,
            final DelegatingConnection<?> conn) {
        super(conn, stmt);
        _pool = pool;
        _key = key;

        // 现在从跟踪中删除，因为activate语句将添加此语句.
        if(getConnectionInternal() != null) {
            getConnectionInternal().removeTrace(this);
        }
    }

    /**
     * Add batch.
     */
    @Override
    public void addBatch() throws SQLException {
        super.addBatch();
        batchAdded = true;
    }

    /**
     * Clear Batch.
     */
    @Override
    public void clearBatch() throws SQLException {
        batchAdded = false;
        super.clearBatch();
    }

    /**
     * 将这个对象返回到池中.
     */
    @Override
    public void close() throws SQLException {
        // 调用两次 close 方法无效
        if (!isClosed()) {
            try {
                _pool.returnObject(_key, this);
            } catch(final SQLException e) {
                throw e;
            } catch(final RuntimeException e) {
                throw e;
            } catch(final Exception e) {
                throw new SQLException("Cannot close preparedstatement (return to pool failed)", e);
            }
        }
    }

    @Override
    public void activate() throws SQLException{
        setClosedInternal(false);
        if(getConnectionInternal() != null) {
            getConnectionInternal().addTrace(this);
        }
        super.activate();
    }

    @Override
    public void passivate() throws SQLException {
        // DBCP-372. 如果在连接标记为已关闭时调用，则clearBatch 将抛出异常.
        if (batchAdded) {
            clearBatch();
        }
        setClosedInternal(true);
        if(getConnectionInternal() != null) {
            getConnectionInternal().removeTrace(this);
        }

        // JDBC规范要求statement 在关闭时关闭任何打开的ResultSet.
        // FIXME 正在包装的PreparedStatement应该处理这个问题.
        // See bug 17301 for what could happen when ResultSets are closed twice.
        final List<AbandonedTrace> resultSets = getTrace();
        if( resultSets != null) {
            final ResultSet[] set = resultSets.toArray(new ResultSet[resultSets.size()]);
            for (final ResultSet element : set) {
                element.close();
            }
            clearTrace();
        }

        super.passivate();
    }
}
