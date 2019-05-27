package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.dbcp2.Utils;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * 创建{@link org.apache.tomcat.dbcp.dbcp2.PoolableConnection PoolableConnection}的{@link KeyedPooledObjectFactory}.
 */
class KeyedCPDSConnectionFactory
    implements KeyedPooledObjectFactory<UserPassKey,PooledConnectionAndInfo>,
    ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE
            = "close() was called on a Connection, but "
            + "I have no record of the underlying PooledConnection.";

    private final ConnectionPoolDataSource _cpds;
    private final String _validationQuery;
    private final int _validationQueryTimeout;
    private final boolean _rollbackAfterValidation;
    private KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> _pool;
    private long maxConnLifetimeMillis = -1;

    /**
     * 忽略关闭时间的 PooledConnection.
     */
    private final Set<PooledConnection> validatingSet =
            Collections.newSetFromMap(new ConcurrentHashMap<PooledConnection,Boolean>());

    /**
     * PooledConnectionAndInfo 实例
     */
    private final Map<PooledConnection, PooledConnectionAndInfo> pcMap =
        new ConcurrentHashMap<>();


    /**
     * @param cpds 从中获取PooledConnections的ConnectionPoolDataSource
     * @param validationQuery 用于{@link #validateObject validate} {@link Connection}的查询. 应至少返回一行.
     * 可能是{@code null}，其中case3 {@link Connection#isValid(int)}将用于验证连接.
     * @param validationQueryTimeout 验证查询的超时时间, 秒
     * @param rollbackAfterValidation 是否应在{@link #validateObject validating} {@link Connection}之后发出回滚.
     */
    public KeyedCPDSConnectionFactory(final ConnectionPoolDataSource cpds,
                                      final String validationQuery,
                                      final int validationQueryTimeout,
                                      final boolean rollbackAfterValidation) {
        _cpds = cpds;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _rollbackAfterValidation = rollbackAfterValidation;
    }

    public void setPool(final KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> pool) {
        this._pool = pool;
    }

    /**
     *返回用于池化此工厂创建的连接的Key对象池.
     *
     * @return 管理池化连接的KeyedObjectPool
     */
    public KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> getPool() {
        return _pool;
    }

    /**
     * 从给定的{@link UserPassKey}创建一个新的{@link PooledConnectionAndInfo}.
     *
     * @param upkey 包含用户凭据的{@link UserPassKey}
     * @throws SQLException 如果无法创建连接.
     */
    @Override
    public synchronized PooledObject<PooledConnectionAndInfo> makeObject(final UserPassKey upkey)
            throws Exception {
        PooledConnectionAndInfo pci = null;

        PooledConnection pc = null;
        final String username = upkey.getUsername();
        final String password = upkey.getPassword();
        if (username == null) {
            pc = _cpds.getPooledConnection();
        } else {
            pc = _cpds.getPooledConnection(username, password);
        }

        if (pc == null) {
            throw new IllegalStateException("Connection pool data source returned null from getPooledConnection");
        }

        // 应该将此对象添加为监听器或池.
        // 在决策中考虑validateObject方法
        pc.addConnectionEventListener(this);
        pci = new PooledConnectionAndInfo(pc, username, password);
        pcMap.put(pc, pci);

        return new DefaultPooledObject<>(pci);
    }

    /**
     * 关闭PooledConnection并停止监听来自它的事件.
     */
    @Override
    public void destroyObject(final UserPassKey key, final PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
        final PooledConnection pc = p.getObject().getPooledConnection();
        pc.removeConnectionEventListener(this);
        pcMap.remove(pc);
        pc.close();
    }

    /**
     * 验证池化连接.
     *
     * @param key ignored
     * @param p 包含要验证的连接的{@link PooledConnectionAndInfo}
     * @return true 如果验证成功
     */
    @Override
    public boolean validateObject(final UserPassKey key,
            final PooledObject<PooledConnectionAndInfo> p) {
        try {
            validateLifetime(p);
        } catch (final Exception e) {
            return false;
        }
        boolean valid = false;
        final PooledConnection pconn = p.getObject().getPooledConnection();
        Connection conn = null;
        validatingSet.add(pconn);
        if (null == _validationQuery) {
            int timeout = _validationQueryTimeout;
            if (timeout < 0) {
                timeout = 0;
            }
            try {
                conn = pconn.getConnection();
                valid = conn.isValid(timeout);
            } catch (final SQLException e) {
                valid = false;
            } finally {
                Utils.closeQuietly(conn);
                validatingSet.remove(pconn);
            }
        } else {
            Statement stmt = null;
            ResultSet rset = null;
            // 来自PooledConnection的逻辑连接必须先关闭才能请求另一个连接，关闭它将生成一个事件. 保持跟踪，以便我们知道不要返回PooledConnection
            validatingSet.add(pconn);
            try {
                conn = pconn.getConnection();
                stmt = conn.createStatement();
                rset = stmt.executeQuery(_validationQuery);
                if (rset.next()) {
                    valid = true;
                } else {
                    valid = false;
                }
                if (_rollbackAfterValidation) {
                    conn.rollback();
                }
            } catch(final Exception e) {
                valid = false;
            } finally {
                Utils.closeQuietly(rset);
                Utils.closeQuietly(stmt);
                Utils.closeQuietly(conn);
                validatingSet.remove(pconn);
            }
        }
        return valid;
    }

    @Override
    public void passivateObject(final UserPassKey key,
            final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        validateLifetime(p);
    }

    @Override
    public void activateObject(final UserPassKey key,
            final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        validateLifetime(p);
    }

    // ***********************************************************************
    // java.sql.ConnectionEventListener implementation
    // ***********************************************************************

    /**
     * 如果getConnection方法返回的Connection来自PooledConnection, 则会调用此方法, 并且用户调用此连接对象的close()方法.
     * 需要做的是从池中释放这个PooledConnection...
     */
    @Override
    public void connectionClosed(final ConnectionEvent event) {
        final PooledConnection pc = (PooledConnection)event.getSource();
        // 如果发生此事件, 是因为我们正在验证, 忽略它
        // 否则返回连接到池中.
        if (!validatingSet.contains(pc)) {
            final PooledConnectionAndInfo pci = pcMap.get(pc);
            if (pci == null) {
                throw new IllegalStateException(NO_KEY_MESSAGE);
            }
            try {
                _pool.returnObject(pci.getUserPassKey(), pci);
            } catch (final Exception e) {
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD " +
                "NOT BE RETURNED TO THE POOL");
                pc.removeConnectionEventListener(this);
                try {
                    _pool.invalidateObject(pci.getUserPassKey(), pci);
                } catch (final Exception e3) {
                    System.err.println("EXCEPTION WHILE DESTROYING OBJECT " +
                            pci);
                    e3.printStackTrace();
                }
            }
        }
    }

    /**
     * 如果发生致命错误, 关闭底层物理连接, 以便将来不再返回
     */
    @Override
    public void connectionErrorOccurred(final ConnectionEvent event) {
        final PooledConnection pc = (PooledConnection)event.getSource();
        if (null != event.getSQLException()) {
            System.err
                .println("CLOSING DOWN CONNECTION DUE TO INTERNAL ERROR (" +
                         event.getSQLException() + ")");
        }
        pc.removeConnectionEventListener(this);

        final PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(info.getUserPassKey(), info);
        } catch (final Exception e) {
            System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + info);
            e.printStackTrace();
        }
    }

    // ***********************************************************************
    // PooledConnectionManager implementation
    // ***********************************************************************

    /**
     * 使池中的PooledConnection无效.  CPDSConnectionFactory关闭连接, 池计数器已正确更新.
     * 还清除与用于创建PooledConnection的用户名关联的任何空闲实例.  与此用户关联的连接不受影响, 并且在返回池时不会自动关闭.
     */
    @Override
    public void invalidate(final PooledConnection pc) throws SQLException {
        final PooledConnectionAndInfo info = pcMap.get(pc);
        if (info == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        final UserPassKey key = info.getUserPassKey();
        try {
            _pool.invalidateObject(key, info);  // 销毁实例并更新池计数器
            _pool.clear(key); // 删除此key关联的空闲实例
        } catch (final Exception ex) {
            throw new SQLException("Error invalidating connection", ex);
        }
    }

    /**
     * Does nothing.  此工厂不会缓存用户凭据.
     */
    @Override
    public void setPassword(final String password) {
    }

    /**
     * 设置连接的最长生存期（以毫秒为单位）, 之后连接将始终无法激活、钝化和验证.
     *
     * @param maxConnLifetimeMillis 零或更小的值表示无限寿命. 默认值是 -1.
     */
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }

    /**
     * 此实现不会完全关闭KeyedObjectPool, 因为这会影响所有用户.
     * 相反, 它会清除与给定用户关联的池. 目前尚未使用此方法.
     */
    @Override
    public void closePool(final String username) throws SQLException {
        try {
            _pool.clear(new UserPassKey(username, null));
        } catch (final Exception ex) {
            throw new SQLException("Error closing connection pool", ex);
        }
    }

    private void validateLifetime(final PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
        if (maxConnLifetimeMillis > 0) {
            final long lifetime = System.currentTimeMillis() - p.getCreateTime();
            if (lifetime > maxConnLifetimeMillis) {
                throw new Exception(Utils.getMessage(
                        "connectionFactory.lifetimeExceeded",
                        Long.valueOf(lifetime),
                        Long.valueOf(maxConnLifetimeMillis)));
            }
        }
    }
}
