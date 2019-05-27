package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionPoolDataSource;

import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * <p>适合在J2EE环境中部署的池<code>DataSource</code>. 有许多配置选项, 其中大部分是在父类中定义的.
 * 所有用户（基于用户名）在此数据源中共享一个最大连接数.</p>
 *
 * <p>无需重新初始化数据源即可更改用户密码.
 * 当<code>getConnection(username, password)</code>请求的密码不同于<code>username</code>关联的池中创建连接的密码,
 * 尝试使用提供的密码创建新连接, 如果成功, 使用旧密码创建的空闲连接将被销毁, 并使用新密码创建新连接.</p>
 */
public class SharedPoolDataSource extends InstanceKeyDataSource {

    private static final long serialVersionUID = -1458539734480586454L;

    // Pool properties
    private int maxTotal = GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;


    private transient KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> pool = null;
    private transient KeyedCPDSConnectionFactory factory = null;

    public SharedPoolDataSource() {
    }

    /**
     * 由此数据源维护的关闭池.
     */
    @Override
    public void close() throws Exception {
        if (pool != null) {
            pool.close();
        }
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }


    // -------------------------------------------------------------------
    // Properties

    /**
     * @return 这个池的{@link GenericKeyedObjectPool#getMaxTotal()}.
     */
    public int getMaxTotal() {
        return this.maxTotal;
    }

    /**
     * 设置这个池的 {@link GenericKeyedObjectPool#getMaxTotal()}.
     * 
     * @param maxTotal 最大总数
     */
    public void setMaxTotal(final int maxTotal) {
        assertInitializationAllowed();
        this.maxTotal = maxTotal;
    }


    // ----------------------------------------------------------------------
    // Instrumentation Methods

    /**
     * @return 池中的活动连接数.
     */
    public int getNumActive() {
        return pool == null ? 0 : pool.getNumActive();
    }

    /**
     * @return 池中的空闲连接数.
     */
    public int getNumIdle() {
        return pool == null ? 0 : pool.getNumIdle();
    }

    // ----------------------------------------------------------------------
    // Inherited abstract methods

    @Override
    protected PooledConnectionAndInfo
        getPooledConnectionAndInfo(final String username, final String password)
        throws SQLException {

        synchronized(this) {
            if (pool == null) {
                try {
                    registerPool(username, password);
                } catch (final NamingException e) {
                    throw new SQLException("RegisterPool failed", e);
                }
            }
        }

        PooledConnectionAndInfo info = null;

        final UserPassKey key = new UserPassKey(username, password);

        try {
            info = pool.borrowObject(key);
        }
        catch (final Exception e) {
            throw new SQLException(
                    "Could not retrieve connection info from pool", e);
        }
        return info;
    }

    @Override
    protected PooledConnectionManager getConnectionManager(final UserPassKey upkey)  {
        return factory;
    }

    /**
     * @return a <code>SharedPoolDataSource</code> {@link Reference}.
     * @throws NamingException 不应该发生
     */
    @Override
    public Reference getReference() throws NamingException {
        final Reference ref = new Reference(getClass().getName(),
            SharedPoolDataSourceFactory.class.getName(), null);
        ref.add(new StringRefAddr("instanceKey", getInstanceKey()));
        return ref;
    }

    private void registerPool(final String username, final String password)
            throws NamingException, SQLException {

        final ConnectionPoolDataSource cpds = testCPDS(username, password);

        // 创建一个对象池以包含PooledConnection
        factory = new KeyedCPDSConnectionFactory(cpds, getValidationQuery(),
                getValidationQueryTimeout(), isRollbackAfterValidation());
        factory.setMaxConnLifetimeMillis(getMaxConnLifetimeMillis());

        final GenericKeyedObjectPoolConfig config =
                new GenericKeyedObjectPoolConfig();
        config.setBlockWhenExhausted(getDefaultBlockWhenExhausted());
        config.setEvictionPolicyClassName(getDefaultEvictionPolicyClassName());
        config.setLifo(getDefaultLifo());
        config.setMaxIdlePerKey(getDefaultMaxIdle());
        config.setMaxTotal(getMaxTotal());
        config.setMaxTotalPerKey(getDefaultMaxTotal());
        config.setMaxWaitMillis(getDefaultMaxWaitMillis());
        config.setMinEvictableIdleTimeMillis(
                getDefaultMinEvictableIdleTimeMillis());
        config.setMinIdlePerKey(getDefaultMinIdle());
        config.setNumTestsPerEvictionRun(getDefaultNumTestsPerEvictionRun());
        config.setSoftMinEvictableIdleTimeMillis(
                getDefaultSoftMinEvictableIdleTimeMillis());
        config.setTestOnCreate(getDefaultTestOnCreate());
        config.setTestOnBorrow(getDefaultTestOnBorrow());
        config.setTestOnReturn(getDefaultTestOnReturn());
        config.setTestWhileIdle(getDefaultTestWhileIdle());
        config.setTimeBetweenEvictionRunsMillis(
                getDefaultTimeBetweenEvictionRunsMillis());

        final KeyedObjectPool<UserPassKey,PooledConnectionAndInfo> tmpPool =
                new GenericKeyedObjectPool<>(factory, config);
        factory.setPool(tmpPool);
        pool = tmpPool;
    }

    @Override
    protected void setupDefaults(final Connection con, final String username) throws SQLException {
        final Boolean defaultAutoCommit = isDefaultAutoCommit();
        if (defaultAutoCommit != null &&
                con.getAutoCommit() != defaultAutoCommit.booleanValue()) {
            con.setAutoCommit(defaultAutoCommit.booleanValue());
        }

        final int defaultTransactionIsolation = getDefaultTransactionIsolation();
        if (defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION) {
            con.setTransactionIsolation(defaultTransactionIsolation);
        }

        final Boolean defaultReadOnly = isDefaultReadOnly();
        if (defaultReadOnly != null &&
                con.isReadOnly() != defaultReadOnly.booleanValue()) {
            con.setReadOnly(defaultReadOnly.booleanValue());
        }
    }

    /**
     * 支持序列化.
     *
     * @param in a <code>java.io.ObjectInputStream</code> value
     * @throws IOException 如果发生错误
     * @throws ClassNotFoundException 如果发生错误
     */
    private void readObject(final ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        try
        {
            in.defaultReadObject();
            final SharedPoolDataSource oldDS = (SharedPoolDataSource)
                new SharedPoolDataSourceFactory()
                    .getObjectInstance(getReference(), null, null, null);
            this.pool = oldDS.pool;
        }
        catch (final NamingException e)
        {
            throw new IOException("NamingException: " + e);
        }
    }
}

