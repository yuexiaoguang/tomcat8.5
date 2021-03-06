package org.apache.tomcat.dbcp.dbcp2.cpdsadapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;
import org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement;

/**
 * 这个类是从<code>PooledConnectionImpl.getConnection()</code>返回的 <code>Connection</code>.
 * 大部分方法封装了 JDBC 1.x <code>Connection</code>. 一些异常包含了 preparedStatement 和 close.
 * 根据JDBC规范, 在调用closed()之后不能使用此Connection.  任何进一步的使用将导致SQLException.
 *
 * ConnectionImpl extends DelegatingConnection 启用对底层连接的访问.
 */
class ConnectionImpl extends DelegatingConnection<Connection> {

    private final boolean accessToUnderlyingConnectionAllowed;

    /** 实例化此对象的对象 */
     private final PooledConnectionImpl pooledConnection;

    /**
     * @param pooledConnection 正在调用ctor的PooledConnection.
     * @param connection 要包装的JDBC 1.x连接.
     * @param accessToUnderlyingConnectionAllowed 如果是 true, 然后允许访问底层连接
     */
    ConnectionImpl(final PooledConnectionImpl pooledConnection,
            final Connection connection,
            final boolean accessToUnderlyingConnectionAllowed) {
        super(connection);
        this.pooledConnection = pooledConnection;
        this.accessToUnderlyingConnectionAllowed =
            accessToUnderlyingConnectionAllowed;
    }

    /**
     * 将Connection标记为已关闭, 并通知池池化的连接可用.
     * 根据JDBC规范, 在调用closed()之后不能使用此Connection.  任何进一步的使用将导致SQLException.
     *
     * @throws SQLException 无法关闭数据库连接.
     */
    @Override
    public void close() throws SQLException {
        if (!isClosedInternal()) {
            try {
                passivate();
            } finally {
                setClosedInternal(true);
                pooledConnection.notifyListeners();
            }
        }
    }

    /**
     * 如果在{@link DriverAdapterCPDS}中打开了<code>PreparedStatement</code>的池,
     * 将返回池化的对象, 否则委托给封装好的JDBC 1.x {@link java.sql.Connection}.
     *
     * @param sql 要预处理的SQL语句
     * @return 预处理的SQL语句
     * @throws SQLException 如果此连接已关闭或在封装的连接中发生错误.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement
                (this, pooledConnection.prepareStatement(sql));
        }
        catch (final SQLException e) {
            handleException(e); // Does not return
            return null;
        }
    }

    /**
     * 如果在{@link DriverAdapterCPDS}中打开了<code>PreparedStatement</code>的池,
     * 将返回池化的对象, 否则委托给封装好的JDBC 1.x {@link java.sql.Connection}.
     *
     * @throws SQLException 如果此连接已关闭或在封装的连接中发生错误.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement
                (this, pooledConnection.prepareStatement
                    (sql,resultSetType,resultSetConcurrency));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency,
                                              final int resultSetHoldability)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, resultSetType,
                            resultSetConcurrency, resultSetHoldability));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, autoGeneratedKeys));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int columnIndexes[])
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, columnIndexes));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String columnNames[])
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, columnNames));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    //
    // 访问委托连接的方法
    //

    /**
     * 如果是 false, getDelegate() 和 getInnermostDelegate() 将返回 null.
     * 
     * @return true 如果允许访问底层连接
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }

    /**
     * 获取委托的连接.
     * 
     * @return 内部连接, 或 null, 如果访问不允许.
     */
    @Override
    public Connection getDelegate() {
        if (isAccessToUnderlyingConnectionAllowed()) {
            return getDelegateInternal();
        }
        return null;
    }

    /**
     * 获取最内层的连接.
     * 
     * @return 最内层的连接, 或 null, 如果访问不允许.
     */
    @Override
    public Connection getInnermostDelegate() {
        if (isAccessToUnderlyingConnectionAllowed()) {
            return super.getInnermostDelegateInternal();
        }
        return null;
    }
}
