package org.apache.tomcat.dbcp.dbcp2.cpdsadapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Vector;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;
import org.apache.tomcat.dbcp.dbcp2.PoolablePreparedStatement;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * PooledConnectionDataSource返回的PooledConnection的实现.
 */
class PooledConnectionImpl
        implements PooledConnection, KeyedPooledObjectFactory<PStmtKeyCPDS, PoolablePreparedStatement<PStmtKeyCPDS>> {

    private static final String CLOSED
            = "Attempted to use PooledConnection after closed() was called.";

    /**
     * 表示物理数据库连接的JDBC数据库连接.
     */
    private Connection connection = null;

    /**
     * 用于创建PoolablePreparedStatementStub的DelegatingConnection
     */
    private final DelegatingConnection<?> delegatingConnection;

    /**
     * JDBC数据库逻辑连接.
     */
    private Connection logicalConnection = null;

    private final Vector<ConnectionEventListener> eventListeners;

    private final Vector<StatementEventListener> statementEventListeners =
            new Vector<>();

    /**
     * 设置为 true, 只调用一次 close().
     */
    private boolean isClosed;

    /** {@link PreparedStatement}池. */
    private KeyedObjectPool<PStmtKeyCPDS, PoolablePreparedStatement<PStmtKeyCPDS>> pstmtPool = null;

    /**
     * 控制对底层连接的访问
     */
    private boolean accessToUnderlyingConnectionAllowed = false;

    /**
     * 封装真实的连接.
     * 
     * @param connection 要封装的连接
     */
    PooledConnectionImpl(final Connection connection) {
        this.connection = connection;
        if (connection instanceof DelegatingConnection) {
            this.delegatingConnection = (DelegatingConnection<?>) connection;
        } else {
            this.delegatingConnection = new DelegatingConnection<>(connection);
        }
        eventListeners = new Vector<>();
        isClosed = false;
    }

    public void setStatementPool(
            final KeyedObjectPool<PStmtKeyCPDS, PoolablePreparedStatement<PStmtKeyCPDS>> statementPool) {
        pstmtPool = statementPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public void addStatementEventListener(final StatementEventListener listener) {
        if (!statementEventListeners.contains(listener)) {
            statementEventListeners.add(listener);
        }
    }
    /* JDBC_4_ANT_KEY_END */

    /**
     * 关闭物理连接并标记此<code>PooledConnection</code>, 以便可以不使用它生成更多的逻辑<code>Connection</code>s.
     *
     * @throws SQLException 如果发生错误或连接已关闭
     */
    @Override
    public void close() throws SQLException {
        assertOpen();
        isClosed = true;
        try {
            if (pstmtPool != null) {
                try {
                    pstmtPool.close();
                } finally {
                    pstmtPool = null;
                }
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Cannot close connection (return to pool failed)", e);
        } finally {
            try {
                connection.close();
            } finally {
                connection = null;
            }
        }
    }

    /**
     * 抛出 SQLException, 如果 isClosed 是 true
     */
    private void assertOpen() throws SQLException {
        if (isClosed) {
            throw new SQLException(CLOSED);
        }
    }

    /**
     * 返回一个 JDBC 连接.
     *
     * @return 数据库连接.
     * @throws SQLException 如果连接未打开或先前的逻辑连接仍处于打开状态
     */
    @Override
    public Connection getConnection() throws SQLException {
        assertOpen();
        // 确保最后一个连接标记为已关闭
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            // 应该通知错误池，以便可以删除池连接 !FIXME!
            throw new SQLException("PooledConnection was reused, without "
                    + "its previous Connection being closed.");
        }

        // 规范要求返回一个新的Connection实例.
        logicalConnection = new ConnectionImpl(
                this, connection, isAccessToUnderlyingConnectionAllowed());
        return logicalConnection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionEventListener(
            final ConnectionEventListener listener) {
        eventListeners.remove(listener);
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public void removeStatementEventListener(final StatementEventListener listener) {
        statementEventListeners.remove(listener);
    }
    /* JDBC_4_ANT_KEY_END */

    /**
     * 关闭物理连接并检查逻辑连接是否已关闭.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            connection.close();
        } catch (final Exception ignored) {
        }

        // 确保最后一个连接标记为已关闭
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            throw new SQLException("PooledConnection was gc'ed, without"
                    + "its last Connection being closed.");
        }
    }

    /**
     * 发送一个 connectionClosed 事件.
     */
    void notifyListeners() {
        final ConnectionEvent event = new ConnectionEvent(this);
        final Object[] listeners = eventListeners.toArray();
        for (final Object listener : listeners) {
            ((ConnectionEventListener) listener).connectionClosed(event);
        }
    }

    // -------------------------------------------------------------------
    // The following code implements a PreparedStatement pool

    /**
     * 从池中创建或获取一个 {@link PreparedStatement}.
     * 
     * @param sql SQL 语句
     * @return a {@link PoolablePreparedStatement}
     */
    PreparedStatement prepareStatement(final String sql) throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql);
        }
        try {
            return pstmtPool.borrowObject(createKey(sql));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * 从池中创建或获取一个 {@link PreparedStatement}.
     * 
     * @param sql 要发送到数据库的SQL语句; 参数中可能包含一个或多个 '?'
     * @param resultSetType 结果集类型;
     *         <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *         <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>,
     *         <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     *         
     * @param resultSetConcurrency 并发类型; <code>ResultSet.CONCUR_READ_ONLY</code>, <code>ResultSet.CONCUR_UPDATABLE</code>
     *
     * @return a {@link PoolablePreparedStatement}
     */
    PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                       final int resultSetConcurrency)
            throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }
        try {
            return pstmtPool.borrowObject(
                    createKey(sql,resultSetType,resultSetConcurrency));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * 从池中创建或获取一个 {@link PreparedStatement}.
     * 
     * @param sql 参数位置中包含一个或多个 '?' 的SQL语句
     * @param autoGeneratedKeys 指示是否应返回自动生成的密钥; <code>Statement.RETURN_GENERATED_KEYS</code>, <code>Statement.NO_GENERATED_KEYS</code>
     * 
     * @return a {@link PoolablePreparedStatement}
     */
    PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
            throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, autoGeneratedKeys);
        }
        try {
            return pstmtPool.borrowObject(createKey(sql,autoGeneratedKeys));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    PreparedStatement prepareStatement(final String sql, final int resultSetType,
            final int resultSetConcurrency, final int resultSetHoldability)
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, resultSetType,
                    resultSetConcurrency, resultSetHoldability);
        }
        try {
            return pstmtPool.borrowObject(createKey(sql, resultSetType,
                    resultSetConcurrency, resultSetHoldability));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    PreparedStatement prepareStatement(final String sql, final int columnIndexes[])
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, columnIndexes);
        }
        try {
            return pstmtPool.borrowObject(createKey(sql, columnIndexes));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    PreparedStatement prepareStatement(final String sql, final String columnNames[])
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, columnNames);
        }
        try {
            return pstmtPool.borrowObject(createKey(sql, columnNames));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * 为给定的参数创建{@link PooledConnectionImpl.PStmtKey}.
     */
    protected PStmtKeyCPDS createKey(final String sql, final int autoGeneratedKeys) {
        return new PStmtKeyCPDS(normalizeSQL(sql), autoGeneratedKeys);
    }

    /**
     * 为给定的参数创建{@link PooledConnectionImpl.PStmtKey}.
     */
    protected PStmtKeyCPDS createKey(final String sql, final int resultSetType,
            final int resultSetConcurrency, final int resultSetHoldability) {
        return new PStmtKeyCPDS(normalizeSQL(sql), resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    /**
     * 为给定的参数创建{@link PooledConnectionImpl.PStmtKey}.
     */
    protected PStmtKeyCPDS createKey(final String sql, final int columnIndexes[]) {
        return new PStmtKeyCPDS(normalizeSQL(sql), columnIndexes);
    }

    /**
     * 为给定的参数创建{@link PooledConnectionImpl.PStmtKey}.
     */
    protected PStmtKeyCPDS createKey(final String sql, final String columnNames[]) {
        return new PStmtKeyCPDS(normalizeSQL(sql), columnNames);
    }

    /**
     * 为给定的参数创建{@link PooledConnectionImpl.PStmtKey}.
     */
    protected PStmtKeyCPDS createKey(final String sql, final int resultSetType,
                               final int resultSetConcurrency) {
        return new PStmtKeyCPDS(normalizeSQL(sql), resultSetType,
                            resultSetConcurrency);
    }

    /**
     * 为给定的参数创建{@link PooledConnectionImpl.PStmtKey}.
     */
    protected PStmtKeyCPDS createKey(final String sql) {
        return new PStmtKeyCPDS(normalizeSQL(sql));
    }

    /**
     * 规范化给定的SQL语句, 产生一种在语义上等同于原始形式的规范形式.
     */
    protected String normalizeSQL(final String sql) {
        return sql.trim();
    }

    /**
     * 创建{@link PreparedStatement}的 {@link KeyedPooledObjectFactory} 方法.
     * @param key 要创建的 {@link PreparedStatement}的Key
     */
    @Override
    public PooledObject<PoolablePreparedStatement<PStmtKeyCPDS>> makeObject(final PStmtKeyCPDS key) throws Exception {
        if (null == key) {
            throw new IllegalArgumentException();
        }
        // _openPstmts++;
        if (null == key.getResultSetType()
                && null == key.getResultSetConcurrency()) {
            if (null == key.getAutoGeneratedKeys()) {
                return new DefaultPooledObject<>(new PoolablePreparedStatement<>(
                        connection.prepareStatement(key.getSql()),
                        key, pstmtPool, delegatingConnection));
            }
            return new DefaultPooledObject<>(new PoolablePreparedStatement<>(
                            connection.prepareStatement(key.getSql(),
                                    key.getAutoGeneratedKeys().intValue()),
                            key, pstmtPool, delegatingConnection));
        }
        return new DefaultPooledObject<>(new PoolablePreparedStatement<>(
                connection.prepareStatement(key.getSql(),
                        key.getResultSetType().intValue(),
                        key.getResultSetConcurrency().intValue()),
                        key, pstmtPool, delegatingConnection));
    }

    /**
     * 用于销毁{@link PreparedStatement}的 {@link KeyedPooledObjectFactory}方法.
     * 
     * @param key ignored
     * @param p 要销毁的封装的{@link PreparedStatement}.
     */
    @Override
    public void destroyObject(final PStmtKeyCPDS key,
            final PooledObject<PoolablePreparedStatement<PStmtKeyCPDS>> p)
            throws Exception {
        p.getObject().getInnermostDelegate().close();
    }

    /**
     * 用于验证{@link PreparedStatement}的 {@link KeyedPooledObjectFactory} 方法.
     * 
     * @param key ignored
     * @param p ignored
     * 
     * @return {@code true}
     */
    @Override
    public boolean validateObject(final PStmtKeyCPDS key,
            final PooledObject<PoolablePreparedStatement<PStmtKeyCPDS>> p) {
        return true;
    }

    /**
     * 用于激活{@link PreparedStatement}的 {@link KeyedPooledObjectFactory}方法.
     * @param key ignored
     * @param p ignored
     */
    @Override
    public void activateObject(final PStmtKeyCPDS key,
            final PooledObject<PoolablePreparedStatement<PStmtKeyCPDS>> p)
            throws Exception {
        p.getObject().activate();
    }

    /**
     * 用于钝化{@link PreparedStatement}的 {@link KeyedPooledObjectFactory}方法.  当前调用 {@link PreparedStatement#clearParameters}.
     * 
     * @param key ignored
     * @param p 封装的 {@link PreparedStatement}
     */
    @Override
    public void passivateObject(final PStmtKeyCPDS key,
            final PooledObject<PoolablePreparedStatement<PStmtKeyCPDS>> p)
            throws Exception {
        final PoolablePreparedStatement<PStmtKeyCPDS> ppss = p.getObject();
        ppss.clearParameters();
        ppss.passivate();
    }

    /**
     * 返回accessToUnderlyingConnectionAllowed属性的值.
     *
     * @return true 如果允许访问底层, 否则false.
     */
    public synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * 设置accessToUnderlyingConnectionAllowed属性的值.
     * 它控制PoolGuard是否允许访问底层连接. (默认: false)
     *
     * @param allow
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }
}
