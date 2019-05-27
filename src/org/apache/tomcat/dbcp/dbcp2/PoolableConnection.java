package org.apache.tomcat.dbcp.dbcp2;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.tomcat.dbcp.pool2.ObjectPool;

/**
 * 委托的连接, 而不是封闭的底层连接, 关闭时返回它自己到一个 {@link ObjectPool}.
 */
public class PoolableConnection extends DelegatingConnection<Connection>
        implements PoolableConnectionMXBean {

    private static MBeanServer MBEAN_SERVER = null;

    static {
        try {
            MBEAN_SERVER = ManagementFactory.getPlatformMBeanServer();
        } catch (NoClassDefFoundError | Exception ex) {
            // ignore - JMX not available
        }
    }

    /** 应该返回的池. */
    private final ObjectPool<PoolableConnection> _pool;

    private final ObjectName _jmxName;

    // 为验证使用一个预处理的statement, 保留上次使用的SQL以检查验证查询是否已更改.
    private PreparedStatement validationPreparedStatement = null;
    private String lastValidationSql = null;

    /**
     *  指示使用此连接时抛出了不可恢复的SQLException.
     *  这种连接应被视为已破坏，未来不会通过验证.
     */
    private boolean _fatalSqlExceptionThrown = false;

    /**
     * SQL_STATE代码被认为是致命的信号.
     * 重写 {@link Utils#DISCONNECTION_SQL_CODES} 中默认的 (加上任何以{@link Utils#DISCONNECTION_SQL_CODE_PREFIX}开头的内容).
     */
    private final Collection<String> _disconnectionSqlCodes;

    /** 是否在致命连接错误后快速验证失败 */
    private final boolean _fastFailValidation;

    /**
     * @param conn 底层连接
     * @param pool 关闭时应该返回的池
     * @param jmxName JMX 名称
     * @param disconnectSqlCodes SQL_STATE代码被视为致命的断开连接错误
     * @param fastFailValidation true 意味着致命的断开连接错误会导致后续验证立即失败 (不会尝试运行查询或isValid)
     */
    public PoolableConnection(final Connection conn,
            final ObjectPool<PoolableConnection> pool, final ObjectName jmxName, final Collection<String> disconnectSqlCodes,
            final boolean fastFailValidation) {
        super(conn);
        _pool = pool;
        _jmxName = jmxName;
        _disconnectionSqlCodes = disconnectSqlCodes;
        _fastFailValidation = fastFailValidation;

        if (jmxName != null) {
            try {
                MBEAN_SERVER.registerMBean(this, jmxName);
            } catch (InstanceAlreadyExistsException |
                    MBeanRegistrationException | NotCompliantMBeanException e) {
                // For now, simply skip registration
            }
        }
    }

   /**
    * @param conn 底层连接
    * @param pool 关闭时应该返回的池
    * @param jmxName JMX name
    */
   public PoolableConnection(final Connection conn,
           final ObjectPool<PoolableConnection> pool, final ObjectName jmxName) {
       this(conn, pool, jmxName, null, false);
   }


    @Override
    protected void passivate() throws SQLException {
        super.passivate();
        setClosedInternal(true);
    }


    /**
     * {@inheritDoc}
     * <p>
     * 客户端不应使用此方法来确定连接是否应返回到连接池 (通过调用{@link #close()}). 一旦不再需要，客户端应始终尝试将连接返回到池.
     */
    @Override
    public boolean isClosed() throws SQLException {
        if (isClosedInternal()) {
            return true;
        }

        if (getDelegateInternal().isClosed()) {
            // 出错了. 已关闭底层连接，但未将连接返回到池. 返回它.
            close();
            return true;
        }

        return false;
    }


    /**
     * 将当前对象返回到池.
     */
     @Override
    public synchronized void close() throws SQLException {
        if (isClosedInternal()) {
            // already closed
            return;
        }

        boolean isUnderlyingConectionClosed;
        try {
            isUnderlyingConectionClosed = getDelegateInternal().isClosed();
        } catch (final SQLException e) {
            try {
                _pool.invalidateObject(this);
            } catch(final IllegalStateException ise) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch (final Exception ie) {
                // DO NOTHING the original exception will be rethrown
            }
            throw new SQLException("Cannot close connection (isClosed check failed)", e);
        }

        /* 无法在此代码块之前设置关闭，因为在验证运行时需要打开连接.
         * 在此代码块之后无法设置关闭，因为此时连接将返回到池并且可能已被另一个线程借用.
         * 因此, 在passivate()中设置 close 标志.
         */
        if (isUnderlyingConectionClosed) {
            // 异常关闭: 底层连接意外关闭, 所以必须销毁这个代理
            try {
                _pool.invalidateObject(this);
            } catch(final IllegalStateException e) {
                // 池关闭, 因此关闭连接
                passivate();
                getInnermostDelegate().close();
            } catch (final Exception e) {
                throw new SQLException("Cannot close connection (invalidating pooled object failed)", e);
            }
        } else {
            // 正常关闭: 底层连接仍然是打开的, 所以只需要将此代理返回到池中
            try {
                _pool.returnObject(this);
            } catch(final IllegalStateException e) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch(final SQLException e) {
                throw e;
            } catch(final RuntimeException e) {
                throw e;
            } catch(final Exception e) {
                throw new SQLException("Cannot close connection (return to pool failed)", e);
            }
        }
    }

    /**
     * 关闭底层 {@link Connection}.
     */
    @Override
    public void reallyClose() throws SQLException {
        if (_jmxName != null) {
            try {
                MBEAN_SERVER.unregisterMBean(_jmxName);
            } catch (MBeanRegistrationException | InstanceNotFoundException e) {
                // Ignore
            }
        }


        if (validationPreparedStatement != null) {
            try {
                validationPreparedStatement.close();
            } catch (final SQLException sqle) {
                // Ignore
            }
        }

        super.closeInternal();
    }


    /**
     * 通过bean getter公开{@link #toString()}方法，以便通过JMX将其作为属性读取.
     */
    @Override
    public String getToString() {
        return toString();
    }

    /**
     * 验证连接, 使用以下算法:
     * <ol>
     *   <li>如果 {@code fastFailValidation} (构造参数) 是 {@code true},此连接以前抛出致命的断开连接异常,
     *       抛出{@code SQLException}. </li>
     *   <li>如果 {@code sql} 是 null, 调用驱动程序的 #{@link Connection#isValid(int) isValid(timeout)}.
     *       如果它返回 {@code false}, 抛出{@code SQLException}; 否则, 这个方法成功返回.</li>
     *   <li>如果 {@code sql} 不是 null, 它作为查询执行, 如果生成的{@code ResultSet}包含至少一行, 这个方法成功返回.
     *       如果不是, 抛出{@code SQLException}.</li>
     * </ol>
     * @param sql 验证查询
     * @param timeout 验证超时时间
     * @throws SQLException 如果验证失败或验证期间发生SQLException
     */
    public void validate(final String sql, int timeout) throws SQLException {
        if (_fastFailValidation && _fatalSqlExceptionThrown) {
            throw new SQLException(Utils.getMessage("poolableConnection.validate.fastFail"));
        }

        if (sql == null || sql.length() == 0) {
            if (timeout < 0) {
                timeout = 0;
            }
            if (!isValid(timeout)) {
                throw new SQLException("isValid() returned false");
            }
            return;
        }

        if (!sql.equals(lastValidationSql)) {
            lastValidationSql = sql;
            // 必须是最里面的委托，否则当池化连接被钝化时，预处理语句将被关闭.
            validationPreparedStatement =
                    getInnermostDelegateInternal().prepareStatement(sql);
        }

        if (timeout > 0) {
            validationPreparedStatement.setQueryTimeout(timeout);
        }

        try (ResultSet rs = validationPreparedStatement.executeQuery()) {
            if(!rs.next()) {
                throw new SQLException("validationQuery didn't return a row");
            }
        } catch (final SQLException sqle) {
            throw sqle;
        }
    }

    /**
     * 检查输入异常的SQLState以及它包装的任何嵌套的SQLException.
     * <p>
     * 如果已经设置 {@link #getDisconnectSqlCodes() disconnectSQLCodes}, 将sql状态与配置的致命异常代码列表中的状态进行比较.
     * 如果未设置此属性, 代码与#{@ link Utils.DISCONNECTION_SQL_CODES}中的默认代码进行比较，在这种情况下, 
     * 任何以#{link Utils.DISCONNECTION_SQL_CODE_PREFIX}开头的内容都被视为断开连接.</p>
     *
     * @param e 要检查的SQLException
     * @return true 如果异常表示断开连接
     */
    private boolean isDisconnectionSqlException(final SQLException e) {
        boolean fatalException = false;
        final String sqlState = e.getSQLState();
        if (sqlState != null) {
            fatalException = _disconnectionSqlCodes == null ? sqlState.startsWith(Utils.DISCONNECTION_SQL_CODE_PREFIX)
                    || Utils.DISCONNECTION_SQL_CODES.contains(sqlState) : _disconnectionSqlCodes.contains(sqlState);
            if (!fatalException) {
                if (e.getNextException() != null) {
                    fatalException = isDisconnectionSqlException(e.getNextException());
                }
            }
        }
        return fatalException;
    }

    @Override
    protected void handleException(final SQLException e) throws SQLException {
        _fatalSqlExceptionThrown |= isDisconnectionSqlException(e);
        super.handleException(e);
    }
}

