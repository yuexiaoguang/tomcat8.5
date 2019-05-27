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
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * 创建{@link org.apache.tomcat.dbcp.dbcp2.PoolableConnection PoolableConnection}的{@link PooledObjectFactory}.
 */
class CPDSConnectionFactory
        implements PooledObjectFactory<PooledConnectionAndInfo>,
        ConnectionEventListener, PooledConnectionManager {

    private static final String NO_KEY_MESSAGE
            = "close() was called on a Connection, but "
            + "I have no record of the underlying PooledConnection.";

    private final ConnectionPoolDataSource _cpds;
    private final String _validationQuery;
    private final int _validationQueryTimeout;
    private final boolean _rollbackAfterValidation;
    private ObjectPool<PooledConnectionAndInfo> _pool;
    private final String _username;
    private String _password = null;
    private long maxConnLifetimeMillis = -1;


    /**
     * 要忽略关闭事件的 PooledConnection.
     */
    private final Set<PooledConnection> validatingSet =
            Collections.newSetFromMap(new ConcurrentHashMap<PooledConnection,Boolean>());

    /**
     * PooledConnectionAndInfo 实例
     */
    private final Map<PooledConnection, PooledConnectionAndInfo> pcMap = new ConcurrentHashMap<>();

    /**
     * @param cpds 从中获取PooledConnection的ConnectionPoolDataSource
     * @param validationQuery 用于{@link #validateObject validate} {@link Connection}的查询. 应至少返回一行.
     * 可能是{@code null}，在这种情况下{@link Connection#isValid(int)}将用于验证连接.
     * @param validationQueryTimeout 验证失败前的超时时间, 秒数
     * @param rollbackAfterValidation 是否应在{@link #validateObject validating} {@link Connection}之后发出回滚.
     * @param username 用于创建连接的用户名
     * @param password 用于创建连接的密码
     */
    public CPDSConnectionFactory(final ConnectionPoolDataSource cpds,
                                 final String validationQuery,
                                 final int validationQueryTimeout,
                                 final boolean rollbackAfterValidation,
                                 final String username,
                                 final String password) {
        _cpds = cpds;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _username = username;
        _password = password;
        _rollbackAfterValidation = rollbackAfterValidation;
    }

    /**
     * 返回用于池化此工厂创建的连接的对象池.
     *
     * @return 管理池化连接的ObjectPool
     */
    public ObjectPool<PooledConnectionAndInfo> getPool() {
        return _pool;
    }

    /**
     * @param pool 用于池化{@link Connection}的{@link ObjectPool}
     */
    public void setPool(final ObjectPool<PooledConnectionAndInfo> pool) {
        this._pool = pool;
    }

    @Override
    public synchronized PooledObject<PooledConnectionAndInfo> makeObject() {
        PooledConnectionAndInfo pci;
        try {
            PooledConnection pc = null;
            if (_username == null) {
                pc = _cpds.getPooledConnection();
            } else {
                pc = _cpds.getPooledConnection(_username, _password);
            }

            if (pc == null) {
                throw new IllegalStateException("Connection pool data source returned null from getPooledConnection");
            }

            // 应该将此对象添加为监听器或池.
            // 在决策中考虑validateObject方法
            pc.addConnectionEventListener(this);
            pci = new PooledConnectionAndInfo(pc, _username, _password);
            pcMap.put(pc, pci);
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
        return new DefaultPooledObject<>(pci);
    }

    /**
     * 关闭PooledConnection并停止监听来自它的事件.
     */
    @Override
    public void destroyObject(final PooledObject<PooledConnectionAndInfo> p) throws Exception {
        doDestroyObject(p.getObject());
    }

    private void doDestroyObject(final PooledConnectionAndInfo pci) throws Exception{
        final PooledConnection pc = pci.getPooledConnection();
        pc.removeConnectionEventListener(this);
        pcMap.remove(pc);
        pc.close();
    }

    @Override
    public boolean validateObject(final PooledObject<PooledConnectionAndInfo> p) {
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
            // 来自PooledConnection的逻辑连接必须先关闭才能请求另一个连接, 关闭它将生成一个事件.
            // 保持跟踪, 以便我们知道不要返回PooledConnection
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
            } catch (final Exception e) {
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
    public void passivateObject(final PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
        validateLifetime(p);
    }

    @Override
    public void activateObject(final PooledObject<PooledConnectionAndInfo> p)
            throws Exception {
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
        final PooledConnection pc = (PooledConnection) event.getSource();
        // 如果发生此事件, 是因为我们正在验证, 忽略它
        // 否则返回连接到池中.
        if (!validatingSet.contains(pc)) {
            final PooledConnectionAndInfo pci = pcMap.get(pc);
            if (pci == null) {
                throw new IllegalStateException(NO_KEY_MESSAGE);
            }

            try {
                _pool.returnObject(pci);
            } catch (final Exception e) {
                System.err.println("CLOSING DOWN CONNECTION AS IT COULD "
                        + "NOT BE RETURNED TO THE POOL");
                pc.removeConnectionEventListener(this);
                try {
                    doDestroyObject(pci);
                } catch (final Exception e2) {
                    System.err.println("EXCEPTION WHILE DESTROYING OBJECT "
                            + pci);
                    e2.printStackTrace();
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
            System.err.println(
                    "CLOSING DOWN CONNECTION DUE TO INTERNAL ERROR ("
                    + event.getSQLException() + ")");
        }
        pc.removeConnectionEventListener(this);

        final PooledConnectionAndInfo pci = pcMap.get(pc);
        if (pci == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(pci);
        } catch (final Exception e) {
            System.err.println("EXCEPTION WHILE DESTROYING OBJECT " + pci);
            e.printStackTrace();
        }
    }

    // ***********************************************************************
    // PooledConnectionManager implementation
    // ***********************************************************************

    /**
     * 使池中的PooledConnection无效.  CPDSConnectionFactory关闭连接, 池计数器已正确更新.
     * 也关闭池. 这可确保关闭所有空闲连接, 并在返回时关闭已检出的连接.
     */
    @Override
    public void invalidate(final PooledConnection pc) throws SQLException {
        final PooledConnectionAndInfo pci = pcMap.get(pc);
        if (pci == null) {
            throw new IllegalStateException(NO_KEY_MESSAGE);
        }
        try {
            _pool.invalidateObject(pci);  // 销毁实例并更新池计数器
            _pool.close();  // 清除此池中的任何其他实例, 并在其返回时杀死其他实例
        } catch (final Exception ex) {
            throw new SQLException("Error invalidating connection", ex);
        }
    }

    /**
     * 设置创建新连接时使用的数据库密码.
     *
     * @param password new password
     */
    @Override
    public synchronized void setPassword(final String password) {
        _password = password;
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
     * 验证用户名是否与此工厂管理其连接的用户匹配, 如果是这种情况, 则关闭池; 否则什么都不做.
     */
    @Override
    public void closePool(final String username) throws SQLException {
        synchronized (this) {
            if (username == null || !username.equals(_username)) {
                return;
            }
        }
        try {
            _pool.close();
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
