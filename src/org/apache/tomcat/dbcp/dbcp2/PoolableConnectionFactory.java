package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * 创建{@link PoolableConnection}的 {@link PooledObjectFactory}.
 */
public class PoolableConnectionFactory implements PooledObjectFactory<PoolableConnection> {

    private static final Log log =
            LogFactory.getLog(PoolableConnectionFactory.class);

    /**
     * @param connFactory 从中获取的{@link ConnectionFactory}
     * @param dataSourceJmxName JMX名称库{@link Connection}
     */
    public PoolableConnectionFactory(final ConnectionFactory connFactory,
            final ObjectName dataSourceJmxName) {
        _connFactory = connFactory;
        this.dataSourceJmxName = dataSourceJmxName;
    }

    /**
     * 设置用于{@link #validateObject validate} {@link Connection}的查询.
     * 应至少返回一行. 如果没有指定, {@link Connection#isValid(int)} 将用于验证连接.
     *
     * @param validationQuery 用于{@link #validateObject validate} {@link Connection}的查询.
     */
    public void setValidationQuery(final String validationQuery) {
        _validationQuery = validationQuery;
    }

    /**
     * 设置验证查询超时时间, 秒, 执行验证查询时, 连接验证将等待数据库的响应. 使用小于或等于0的值表示没有超时.
     *
     * @param timeout 超时时间, 秒
     */
    public void setValidationQueryTimeout(final int timeout) {
        _validationQueryTimeout = timeout;
    }

    /**
     * 设置用来初始化新创建的{@link Connection}的SQL语句. 使用{@code null}会关闭连接初始化.
     * @param connectionInitSqls 用于初始化{@link Connection}的SQL语句.
     */
    public void setConnectionInitSql(final Collection<String> connectionInitSqls) {
        _connectionInitSqls = connectionInitSqls;
    }

    /**
     * 设置要在其中池化{@link Connection}的{@link ObjectPool}.
     * 
     * @param pool 要使用的{@link ObjectPool}
     */
    public synchronized void setPool(final ObjectPool<PoolableConnection> pool) {
        if(null != _pool && pool != _pool) {
            try {
                _pool.close();
            } catch(final Exception e) {
                // ignored !?!
            }
        }
        _pool = pool;
    }

    /**
     * 返回要在其中池化{@link Connection}的{@link ObjectPool}.
     */
    public synchronized ObjectPool<PoolableConnection> getPool() {
        return _pool;
    }

    /**
     * 为借用的{@link Connection}设置默认的“只读”设置.
     * 
     * @param defaultReadOnly “只读”设置
     */
    public void setDefaultReadOnly(final Boolean defaultReadOnly) {
        _defaultReadOnly = defaultReadOnly;
    }

    /**
     * 为借用的{@link Connection}设置默认的“自动提交”设置.
     * 
     * @param defaultAutoCommit “自动提交”设置
     */
    public void setDefaultAutoCommit(final Boolean defaultAutoCommit) {
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * 为借用的{@link Connection}设置默认的“事务隔离”设置.
     * 
     * @param defaultTransactionIsolation “事务隔离”设置
     */
    public void setDefaultTransactionIsolation(final int defaultTransactionIsolation) {
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * 设置借用的{@link Connection}的默认“目录”设置.
     * 
     * @param defaultCatalog “目录”设置
     */
    public void setDefaultCatalog(final String defaultCatalog) {
        _defaultCatalog = defaultCatalog;
    }

    public void setCacheState(final boolean cacheState) {
        this._cacheState = cacheState;
    }

    public void setPoolStatements(final boolean poolStatements) {
        this.poolStatements = poolStatements;
    }

    public void setMaxOpenPrepatedStatements(final int maxOpenPreparedStatements) {
        this.maxOpenPreparedStatements = maxOpenPreparedStatements;
    }

    /**
     * 设置连接的最长生存期（以毫秒为单位）, 之后连接将始终无法激活、钝化和验证.
     * 零或更小的值表示无限寿命. 默认值是 -1.
     * 
     * @param maxConnLifetimeMillis 最大连接寿命
     */
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }


    public boolean isEnableAutoCommitOnReturn() {
        return enableAutoCommitOnReturn;
    }

    public void setEnableAutoCommitOnReturn(final boolean enableAutoCommitOnReturn) {
        this.enableAutoCommitOnReturn = enableAutoCommitOnReturn;
    }


    public boolean isRollbackOnReturn() {
        return rollbackOnReturn;
    }

    public void setRollbackOnReturn(final boolean rollbackOnReturn) {
        this.rollbackOnReturn = rollbackOnReturn;
    }

    public Integer getDefaultQueryTimeout() {
        return defaultQueryTimeout;
    }

    public void setDefaultQueryTimeout(final Integer defaultQueryTimeout) {
        this.defaultQueryTimeout = defaultQueryTimeout;
    }

    /**
     * SQL_STATE代码被认为是致命的信号.
     * <p>
     * 重写{@link Utils#DISCONNECTION_SQL_CODES}中默认的 (加上任何以{@link Utils#DISCONNECTION_SQL_CODE_PREFIX}开头的内容).
     * 如果这个属性是非 null, 而且 {@link #isFastFailValidation()} 是 {@code true}, 每当此工厂创建的连接在此列表中生成带有SQL_STATE代码的异常时,
     * 它们将被标记为“致命地断开连接”, 随后的验证将很快失败 (不会尝试isValid或验证查询).</p>
     * <p>
     * 如果 {@link #isFastFailValidation()} 是 {@code false}, 设置此属性无效.</p>
     *
     * @return SQL_STATE代码覆盖默认值
     */
    public Collection<String> getDisconnectionSqlCodes() {
        return _disconnectionSqlCodes;
    }

    /**
     * @param disconnectionSqlCodes 断开连接代码
     */
    public void setDisconnectionSqlCodes(final Collection<String> disconnectionSqlCodes) {
        _disconnectionSqlCodes = disconnectionSqlCodes;
    }

    /**
     * True 表示对于之前使用SQL_STATE抛出SQLExceptions指示致命断开连接错误的连接，验证将立即失败.
     *
     * @return true 如果此工厂创建的连接将快速验证失败.
     */
    public boolean isFastFailValidation() {
        return _fastFailValidation;
    }

    /**
     * @param fastFailValidation true 表示此工厂创建的连接将快速验证失败
     */
    public void setFastFailValidation(final boolean fastFailValidation) {
        _fastFailValidation = fastFailValidation;
    }

    @Override
    public PooledObject<PoolableConnection> makeObject() throws Exception {
        Connection conn = _connFactory.createConnection();
        if (conn == null) {
            throw new IllegalStateException("Connection factory returned null from createConnection");
        }
        try {
            initializeConnection(conn);
        } catch (final SQLException sqle) {
            // 确保连接已关闭
            try {
                conn.close();
            } catch (final SQLException ignore) {
                // ignore
            }
            // 重新抛出原始异常，因此调用者可以看到它
            throw sqle;
        }

        final long connIndex = connectionIndex.getAndIncrement();

        if(poolStatements) {
            conn = new PoolingConnection(conn);
            final GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
            config.setMaxTotalPerKey(-1);
            config.setBlockWhenExhausted(false);
            config.setMaxWaitMillis(0);
            config.setMaxIdlePerKey(1);
            config.setMaxTotal(maxOpenPreparedStatements);
            if (dataSourceJmxName != null) {
                final StringBuilder base = new StringBuilder(dataSourceJmxName.toString());
                base.append(Constants.JMX_CONNECTION_BASE_EXT);
                base.append(Long.toString(connIndex));
                config.setJmxNameBase(base.toString());
                config.setJmxNamePrefix(Constants.JMX_STATEMENT_POOL_PREFIX);
            } else {
                config.setJmxEnabled(false);
            }
            final KeyedObjectPool<PStmtKey,DelegatingPreparedStatement> stmtPool =
                    new GenericKeyedObjectPool<>((PoolingConnection)conn, config);
            ((PoolingConnection)conn).setStatementPool(stmtPool);
            ((PoolingConnection) conn).setCacheState(_cacheState);
        }

        // 使用JMX注册此连接
        ObjectName connJmxName;
        if (dataSourceJmxName == null) {
            connJmxName = null;
        } else {
            connJmxName = new ObjectName(dataSourceJmxName.toString() +
                    Constants.JMX_CONNECTION_BASE_EXT + connIndex);
        }

        final PoolableConnection pc = new PoolableConnection(conn, _pool, connJmxName,
                                      _disconnectionSqlCodes, _fastFailValidation);
        pc.setCacheState(_cacheState);

        return new DefaultPooledObject<>(pc);
    }

    protected void initializeConnection(final Connection conn) throws SQLException {
        final Collection<String> sqls = _connectionInitSqls;
        if(conn.isClosed()) {
            throw new SQLException("initializeConnection: connection closed");
        }
        if(null != sqls) {
            try (Statement stmt = conn.createStatement();) {
                for (final String sql : sqls) {
                    if (sql == null) {
                        throw new NullPointerException(
                                "null connectionInitSqls element");
                    }
                    stmt.execute(sql);
                }
            }
        }
    }

    @Override
    public void destroyObject(final PooledObject<PoolableConnection> p)
            throws Exception {
        p.getObject().reallyClose();
    }

    @Override
    public boolean validateObject(final PooledObject<PoolableConnection> p) {
        try {
            validateLifetime(p);

            validateConnection(p.getObject());
            return true;
        } catch (final Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(Utils.getMessage(
                        "poolableConnectionFactory.validateObject.fail"), e);
            }
            return false;
        }
    }

    public void validateConnection(final PoolableConnection conn) throws SQLException {
        if(conn.isClosed()) {
            throw new SQLException("validateConnection: connection closed");
        }
        conn.validate(_validationQuery, _validationQueryTimeout);
    }

    @Override
    public void passivateObject(final PooledObject<PoolableConnection> p)
            throws Exception {

        validateLifetime(p);

        final PoolableConnection conn = p.getObject();
        Boolean connAutoCommit = null;
        if (rollbackOnReturn) {
            connAutoCommit = Boolean.valueOf(conn.getAutoCommit());
            if(!connAutoCommit.booleanValue() && !conn.isReadOnly()) {
                conn.rollback();
            }
        }

        conn.clearWarnings();

        // DBCP-97 / DBCP-399 / DBCP-351 池中的空闲连接应启用autoCommit
        if (enableAutoCommitOnReturn) {
            if (connAutoCommit == null) {
                connAutoCommit = Boolean.valueOf(conn.getAutoCommit());
            }
            if(!connAutoCommit.booleanValue()) {
                conn.setAutoCommit(true);
            }
        }

        conn.passivate();
    }

    @Override
    public void activateObject(final PooledObject<PoolableConnection> p)
            throws Exception {

        validateLifetime(p);

        final PoolableConnection conn = p.getObject();
        conn.activate();

        if (_defaultAutoCommit != null &&
                conn.getAutoCommit() != _defaultAutoCommit.booleanValue()) {
            conn.setAutoCommit(_defaultAutoCommit.booleanValue());
        }
        if (_defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION &&
                conn.getTransactionIsolation() != _defaultTransactionIsolation) {
            conn.setTransactionIsolation(_defaultTransactionIsolation);
        }
        if (_defaultReadOnly != null &&
                conn.isReadOnly() != _defaultReadOnly.booleanValue()) {
            conn.setReadOnly(_defaultReadOnly.booleanValue());
        }
        if (_defaultCatalog != null &&
                !_defaultCatalog.equals(conn.getCatalog())) {
            conn.setCatalog(_defaultCatalog);
        }
        conn.setDefaultQueryTimeout(defaultQueryTimeout);
    }

    private void validateLifetime(final PooledObject<PoolableConnection> p)
            throws Exception {
        if (maxConnLifetimeMillis > 0) {
            final long lifetime = System.currentTimeMillis() - p.getCreateTime();
            if (lifetime > maxConnLifetimeMillis) {
                throw new LifetimeExceededException(Utils.getMessage(
                        "connectionFactory.lifetimeExceeded",
                        Long.valueOf(lifetime),
                        Long.valueOf(maxConnLifetimeMillis)));
            }
        }
    }

    protected ConnectionFactory getConnectionFactory() {
        return _connFactory;
    }

    protected boolean getPoolStatements() {
        return poolStatements;
    }

    protected int getMaxOpenPreparedStatements() {
        return maxOpenPreparedStatements;
    }

    protected boolean getCacheState() {
        return _cacheState;
    }

    protected ObjectName getDataSourceJmxName() {
        return dataSourceJmxName;
    }

    protected AtomicLong getConnectionIndex() {
        return connectionIndex;
    }

    private final ConnectionFactory _connFactory;
    private final ObjectName dataSourceJmxName;
    private volatile String _validationQuery = null;
    private volatile int _validationQueryTimeout = -1;
    private Collection<String> _connectionInitSqls = null;
    private Collection<String> _disconnectionSqlCodes = null;
    private boolean _fastFailValidation = false;
    private volatile ObjectPool<PoolableConnection> _pool = null;
    private Boolean _defaultReadOnly = null;
    private Boolean _defaultAutoCommit = null;
    private boolean enableAutoCommitOnReturn = true;
    private boolean rollbackOnReturn = true;
    private int _defaultTransactionIsolation = UNKNOWN_TRANSACTIONISOLATION;
    private String _defaultCatalog;
    private boolean _cacheState;
    private boolean poolStatements = false;
    private int maxOpenPreparedStatements =
        GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL_PER_KEY;
    private long maxConnLifetimeMillis = -1;
    private final AtomicLong connectionIndex = new AtomicLong(0);
    private Integer defaultQueryTimeout = null;

    /**
     * 内部常量表示未设置级别.
     */
    static final int UNKNOWN_TRANSACTIONISOLATION = -1;
}
