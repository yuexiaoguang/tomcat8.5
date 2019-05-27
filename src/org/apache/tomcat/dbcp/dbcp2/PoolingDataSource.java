package org.apache.tomcat.dbcp.dbcp2;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

/**
 * 从指定的{@link ObjectPool}获取{@link Connection}的 {@link DataSource} 实现.
 *
 * @param <C> 连接类型
 */
public class PoolingDataSource<C extends Connection> implements DataSource, AutoCloseable {

    private static final Log log = LogFactory.getLog(PoolingDataSource.class);

    /** 控制对底层连接的访问 */
    private boolean accessToUnderlyingConnectionAllowed = false;

    public PoolingDataSource(final ObjectPool<C> pool) {
        if (null == pool) {
            throw new NullPointerException("Pool must not be null.");
        }
        _pool = pool;
        // 验证_pool的工厂是否引用它.  如果不是, 记录警告并尝试修复.
        if (_pool instanceof GenericObjectPool<?>) {
            final PoolableConnectionFactory pcf = (PoolableConnectionFactory) ((GenericObjectPool<?>) _pool).getFactory();
            if (pcf == null) {
                throw new NullPointerException("PoolableConnectionFactory must not be null.");
            }
            if (pcf.getPool() != _pool) {
                log.warn(Utils.getMessage("poolingDataSource.factoryConfig"));
                @SuppressWarnings("unchecked") // PCF必须拥有一组PC
                final
                ObjectPool<PoolableConnection> p = (ObjectPool<PoolableConnection>) _pool;
                pcf.setPool(p);
            }
        }
    }

    /**
     * 关闭并释放池中的所有{@link Connection}.
     */
    @Override
    public void close() throws Exception {
        try {
            _pool.close();
        } catch(final RuntimeException rte) {
            throw new RuntimeException(Utils.getMessage("pool.close.fail"), rte);
        } catch(final Exception e) {
            throw new SQLException(Utils.getMessage("pool.close.fail"), e);
        }
    }

    /**
     * 返回accessToUnderlyingConnectionAllowed属性的值.
     *
     * @return true 如果允许访问底层{@link Connection}, 否则false.
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * 设置accessToUnderlyingConnectionAllowed属性的值.
     * 它控制PoolGuard是否允许访问底层连接. (Default: false)
     *
     * @param allow true，允许访问底层连接.
     */
    public void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("PoolingDataSource is not a wrapper.");
    }
    /* JDBC_4_ANT_KEY_END */

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    //--- DataSource methods -----------------------------------------

    /**
     * 从池中返回一个{@link java.sql.Connection}, 根据{@link ObjectPool#borrowObject}指定的合同.
     */
    @Override
    public Connection getConnection() throws SQLException {
        try {
            final C conn = _pool.borrowObject();
            if (conn == null) {
                return null;
            }
            return new PoolGuardConnectionWrapper<>(conn);
        } catch(final SQLException e) {
            throw e;
        } catch(final NoSuchElementException e) {
            throw new SQLException("Cannot get a connection, pool error " + e.getMessage(), e);
        } catch(final RuntimeException e) {
            throw e;
        } catch(final Exception e) {
            throw new SQLException("Cannot get a connection, general error", e);
        }
    }

    /**
     * @throws UnsupportedOperationException 不支持
     */
    @Override
    public Connection getConnection(final String uname, final String passwd) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PrintWriter getLogWriter() {
        return _logWriter;
    }

    /**
     * @throws UnsupportedOperationException 不支持.
     */
    @Override
    public int getLoginTimeout() {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    /**
     * @throws UnsupportedOperationException 不支持.
     */
    @Override
    public void setLoginTimeout(final int seconds) {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    @Override
    public void setLogWriter(final PrintWriter out) {
        _logWriter = out;
    }

    /** My log writer. */
    private PrintWriter _logWriter = null;

    private final ObjectPool<C> _pool;

    protected ObjectPool<C> getPool() {
        return _pool;
    }

    /**
     * PoolGuardConnectionWrapper是一个Connection包装器, 可确保不再使用已关闭的连接.
     */
    private class PoolGuardConnectionWrapper<D extends Connection>
            extends DelegatingConnection<D> {

        PoolGuardConnectionWrapper(final D delegate) {
            super(delegate);
        }

        @Override
        public D getDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getDelegate();
            }
            return null;
        }

        @Override
        public Connection getInnermostDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getInnermostDelegate();
            }
            return null;
        }

        @Override
        public void close() throws SQLException {
            if (getDelegateInternal() != null) {
                super.close();
                super.setDelegate(null);
            }
        }

        @Override
        public boolean isClosed() throws SQLException {
            if (getDelegateInternal() == null) {
                return true;
            }
            return super.isClosed();
        }
    }
}
