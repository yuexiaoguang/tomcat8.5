package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * <p><code>SharedPoolDataSource</code>和 <code>PerUserPoolDataSource</code>的基类. 许多配置属性在此处共享和定义.
 * 此类声明为public, 以允许使用commons-beanutils; 要在<em>commons-dbcp2</em>之外直接使用它.
 * </p>
 *
 * <p>
 * J2EE容器通常会提供一些初始化<code>DataSource</code>的方法, 其属性以bean getter/setter的形式呈现, 然后通过JNDI进行部署.
 * 然后，它可作为与数据库的池化逻辑连接的源.  池需要物理连接源.
 * 此源采用<code>ConnectionPoolDataSource</code>的形式, 可以通过{@link #setDataSourceName(String)}指定，用于通过JNDI查找源.
 * </p>
 *
 * <p>
 * 虽然通常在JNDI环境中使用, 但可以将 DataSource 实例化并初始化为任何bean. 在这种情况下, <code>ConnectionPoolDataSource</code>可能会以类似的方式实例化.
 * 此类允许使用{@link #setConnectionPoolDataSource(ConnectionPoolDataSource)}方法将物理连接源直接附加到此池.
 * </p>
 *
 * <p>
 * dbcp包中包含一个适配器, {@link org.apache.tomcat.dbcp.dbcp2.cpdsadapter.DriverAdapterCPDS},
 * 可以用来允许使用基于此类的<code>DataSource</code>, 以及不支持<code>ConnectionPoolDataSource</code>的JDBC驱动程序实现,
 * 但仍然提供 {@link java.sql.Driver} 实现.
 * </p>
 */
public abstract class InstanceKeyDataSource
        implements DataSource, Referenceable, Serializable, AutoCloseable {

    private static final long serialVersionUID = -6819270431752240878L;

    private static final String GET_CONNECTION_CALLED
            = "A Connection was already requested from this source, "
            + "further initialization is not allowed.";
    private static final String BAD_TRANSACTION_ISOLATION
        = "The requested TransactionIsolation level is invalid.";

    /**
    * 内部常量表示未设置级别.
    */
    protected static final int UNKNOWN_TRANSACTIONISOLATION = -1;

    /** 保护属性 setter - 一旦是 true, setter抛出 IllegalStateException */
    private volatile boolean getConnectionCalled = false;

    /** PooledConnection的底层源 */
    private ConnectionPoolDataSource dataSource = null;

    /** 用于查找ConnectionPoolDataSource的DataSource名称 */
    private String dataSourceName = null;

    /** 描述 */
    private String description = null;

    /** 可用于设置jndi初始上下文的环境. */
    private Properties jndiEnvironment = null;

    /** 登录超时时间, 以秒为单位 */
    private int loginTimeout = 0;

    /** 日志流 */
    private PrintWriter logWriter = null;

    /** 实例 key */
    private String instanceKey = null;

    // Pool properties
    private boolean defaultBlockWhenExhausted =
            BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED;
    private String defaultEvictionPolicyClassName =
            BaseObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME;
    private boolean defaultLifo = BaseObjectPoolConfig.DEFAULT_LIFO;
    private int defaultMaxIdle =
            GenericKeyedObjectPoolConfig.DEFAULT_MAX_IDLE_PER_KEY;
    private int defaultMaxTotal =
            GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;
    private long defaultMaxWaitMillis =
            BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS;
    private long defaultMinEvictableIdleTimeMillis =
            BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private int defaultMinIdle =
            GenericKeyedObjectPoolConfig.DEFAULT_MIN_IDLE_PER_KEY;
    private int defaultNumTestsPerEvictionRun =
            BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
    private long defaultSoftMinEvictableIdleTimeMillis =
            BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private boolean defaultTestOnCreate =
            BaseObjectPoolConfig.DEFAULT_TEST_ON_CREATE;
    private boolean defaultTestOnBorrow =
            BaseObjectPoolConfig.DEFAULT_TEST_ON_BORROW;
    private boolean defaultTestOnReturn =
            BaseObjectPoolConfig.DEFAULT_TEST_ON_RETURN;
    private boolean defaultTestWhileIdle =
            BaseObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;
    private long defaultTimeBetweenEvictionRunsMillis =
            BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    // 连接工厂属性
    private String validationQuery = null;
    private int validationQueryTimeout = -1;
    private boolean rollbackAfterValidation = false;
    private long maxConnLifetimeMillis = -1;

    // 连接属性
    private Boolean defaultAutoCommit = null;
    private int defaultTransactionIsolation = UNKNOWN_TRANSACTIONISOLATION;
    private Boolean defaultReadOnly = null;


    public InstanceKeyDataSource() {
    }

    /**
     * 抛出 IllegalStateException, 如果已经请求了PooledConnection.
     */
    protected void assertInitializationAllowed()
        throws IllegalStateException {
        if (getConnectionCalled) {
            throw new IllegalStateException(GET_CONNECTION_CALLED);
        }
    }

    /**
     * 关闭此数据源维护的连接池.
     */
    @Override
    public abstract void close() throws Exception;

    protected abstract PooledConnectionManager getConnectionManager(UserPassKey upkey);

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("InstanceKeyDataSource is not a wrapper.");
    }
    /* JDBC_4_ANT_KEY_END */

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }


    // -------------------------------------------------------------------
    // Properties

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig＃getBlockWhenExhausted()}的默认值.
     */
    public boolean getDefaultBlockWhenExhausted() {
        return this.defaultBlockWhenExhausted;
    }

    /**
     * 设置个用户池的{@link GenericKeyedObjectPoolConfig＃getBlockWhenExhausted()}的默认值.
     * 
     * @param blockWhenExhausted The new value
     */
    public void setDefaultBlockWhenExhausted(final boolean blockWhenExhausted) {
        assertInitializationAllowed();
        this.defaultBlockWhenExhausted = blockWhenExhausted;
    }

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig＃getEvictionPolicyClassName()}的默认值.
     */
    public String getDefaultEvictionPolicyClassName() {
        return this.defaultEvictionPolicyClassName;
    }

    /**
     * 设置每个用户池的{@link GenericKeyedObjectPoolConfig＃getEvictionPolicyClassName()}的默认值.
     * 
     * @param evictionPolicyClassName The new value
     */
    public void setDefaultEvictionPolicyClassName(
            final String evictionPolicyClassName) {
        assertInitializationAllowed();
        this.defaultEvictionPolicyClassName = evictionPolicyClassName;
    }

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig#getLifo()}的默认值.
     */
    public boolean getDefaultLifo() {
        return this.defaultLifo;
    }

    /**
     * 设置每个用户池的{@link GenericKeyedObjectPoolConfig#getLifo()}的默认值.
     * 
     * @param lifo The new value
     */
    public void setDefaultLifo(final boolean lifo) {
        assertInitializationAllowed();
        this.defaultLifo = lifo;
    }

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()}的默认值.
     */
    public int getDefaultMaxIdle() {
        return this.defaultMaxIdle;
    }

    /**
     * 设置每个用户池的{@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()}的默认值.
     * 
     * @param maxIdle The new value
     */
    public void setDefaultMaxIdle(final int maxIdle) {
        assertInitializationAllowed();
        this.defaultMaxIdle = maxIdle;
    }

    /**
     * @return 每个用户池的 {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()}的默认值.
     */
    public int getDefaultMaxTotal() {
        return this.defaultMaxTotal;
    }

    /**
     * 设置每个用户池的 {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()}的默认值.
     * 
     * @param maxTotal The new value
     */
    public void setDefaultMaxTotal(final int maxTotal) {
        assertInitializationAllowed();
        this.defaultMaxTotal = maxTotal;
    }

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()}的默认值.
     */
    public long getDefaultMaxWaitMillis() {
        return this.defaultMaxWaitMillis;
    }

    /**
     * 设置每个用户池的{@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()}的默认值.
     * 
     * @param maxWaitMillis The new value
     */
    public void setDefaultMaxWaitMillis(final long maxWaitMillis) {
        assertInitializationAllowed();
        this.defaultMaxWaitMillis = maxWaitMillis;
    }

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig#getMinEvictableIdleTimeMillis()}的默认值.
     */
    public long getDefaultMinEvictableIdleTimeMillis() {
        return this.defaultMinEvictableIdleTimeMillis;
    }

    /**
     * 设置每个用户池的{@link GenericKeyedObjectPoolConfig#getMinEvictableIdleTimeMillis()}的默认值.
     * 
     * @param minEvictableIdleTimeMillis The new value
     */
    public void setDefaultMinEvictableIdleTimeMillis(
            final long minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.defaultMinEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()}的默认值.
     */
    public int getDefaultMinIdle() {
        return this.defaultMinIdle;
    }

    /**
     * 设置每个用户池的{@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()}的默认值.
     * 
     * @param minIdle The new value
     */
    public void setDefaultMinIdle(final int minIdle) {
        assertInitializationAllowed();
        this.defaultMinIdle = minIdle;
    }

    /**
     * @return 每个用户池的{@link GenericKeyedObjectPoolConfig#getNumTestsPerEvictionRun()}的默认值.
     */
    public int getDefaultNumTestsPerEvictionRun() {
        return this.defaultNumTestsPerEvictionRun;
    }

    /**
     * 设置每个用户池的{@link GenericKeyedObjectPoolConfig#getNumTestsPerEvictionRun()}的默认值.
     * 
     * @param numTestsPerEvictionRun The new value
     */
    public void setDefaultNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        assertInitializationAllowed();
        this.defaultNumTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * @return 每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getSoftMinEvictableIdleTimeMillis()}的默认值.
     */
    public long getDefaultSoftMinEvictableIdleTimeMillis() {
        return this.defaultSoftMinEvictableIdleTimeMillis;
    }

    /**
     * 设置每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getSoftMinEvictableIdleTimeMillis()}的默认值.
     * 
     * @param softMinEvictableIdleTimeMillis The new value
     */
    public void setDefaultSoftMinEvictableIdleTimeMillis(
            final long softMinEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.defaultSoftMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }

    /**
     * @return 每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestOnCreate()}的默认值.
     */
    public boolean getDefaultTestOnCreate() {
        return this.defaultTestOnCreate;
    }

    /**
     * 设置每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestOnCreate()}的默认值.
     * 
     * @param testOnCreate The new value
     */
    public void setDefaultTestOnCreate(final boolean testOnCreate) {
        assertInitializationAllowed();
        this.defaultTestOnCreate = testOnCreate;
    }

    /**
     * @return 每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestOnBorrow()}的默认值.
     */
    public boolean getDefaultTestOnBorrow() {
        return this.defaultTestOnBorrow;
    }

    /**
     * 设置每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestOnBorrow()}的默认值.
     * 
     * @param testOnBorrow The new value
     */
    public void setDefaultTestOnBorrow(final boolean testOnBorrow) {
        assertInitializationAllowed();
        this.defaultTestOnBorrow = testOnBorrow;
    }

    /**
     * @return 每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestOnReturn()}的默认值.
     */
    public boolean getDefaultTestOnReturn() {
        return this.defaultTestOnReturn;
    }

    /**
     * 设置每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestOnReturn()}的默认值.
     * 
     * @param testOnReturn The new value
     */
    public void setDefaultTestOnReturn(final boolean testOnReturn) {
        assertInitializationAllowed();
        this.defaultTestOnReturn = testOnReturn;
    }

    /**
     * @return 每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestWhileIdle()}的默认值.
     */
    public boolean getDefaultTestWhileIdle() {
        return this.defaultTestWhileIdle;
    }

    /**
     * 设置每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTestWhileIdle()}的默认值.
     * 
     * @param testWhileIdle The new value
     */
    public void setDefaultTestWhileIdle(final boolean testWhileIdle) {
        assertInitializationAllowed();
        this.defaultTestWhileIdle = testWhileIdle;
    }

    /**
     * @return 每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTimeBetweenEvictionRunsMillis ()}的默认值.
     */
    public long getDefaultTimeBetweenEvictionRunsMillis () {
        return this.defaultTimeBetweenEvictionRunsMillis ;
    }

    /**
     * 设置每个用户池的{@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool GenericObjectPool#getTimeBetweenEvictionRunsMillis ()}的默认值.
     * 
     * @param timeBetweenEvictionRunsMillis The new value
     */
    public void setDefaultTimeBetweenEvictionRunsMillis (
            final long timeBetweenEvictionRunsMillis ) {
        assertInitializationAllowed();
        this.defaultTimeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis ;
    }

    /**
     * 如果通过jndi访问数据源, 这个方法返回 null.
     */
    public ConnectionPoolDataSource getConnectionPoolDataSource() {
        return dataSource;
    }

    /**
     * 如果通过jndi访问数据源, 这个方法不应该被使用.
     *
     * @param v 
     */
    public void setConnectionPoolDataSource(final ConnectionPoolDataSource v) {
        assertInitializationAllowed();
        if (dataSourceName != null) {
            throw new IllegalStateException(
                "Cannot set the DataSource, if JNDI is used.");
        }
        if (dataSource != null)
        {
            throw new IllegalStateException(
                "The CPDS has already been set. It cannot be altered.");
        }
        dataSource = v;
        instanceKey = InstanceKeyDataSourceFactory.registerNewInstance(this);
    }

    /**
     * 获取支持此池的ConnectionPoolDataSource的名称. 此名称用于从jndi服务提供程序中查找数据源.
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * 设置支持此池的ConnectionPoolDataSource的名称.
     * 此名称用于从jndi服务提供程序中查找数据源.
     *
     * @param v 
     */
    public void setDataSourceName(final String v) {
        assertInitializationAllowed();
        if (dataSource != null) {
            throw new IllegalStateException(
                "Cannot set the JNDI name for the DataSource, if already " +
                "set using setConnectionPoolDataSource.");
        }
        if (dataSourceName != null)
        {
            throw new IllegalStateException(
                "The DataSourceName has already been set. " +
                "It cannot be altered.");
        }
        this.dataSourceName = v;
        instanceKey = InstanceKeyDataSourceFactory.registerNewInstance(this);
    }

    /**
     * 从这个池发出的连接状态.
     * 可以使用Connection.setAutoCommit(boolean)在Connection上修改该值. 默认是 <code>null</code>.
     */
    public Boolean isDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    /**
     * 从这个池发出的连接状态.
     * 可以使用Connection.setAutoCommit(boolean)在Connection上修改该值. 默认是 <code>null</code>.
     *
     * @param v 
     */
    public void setDefaultAutoCommit(final Boolean v) {
        assertInitializationAllowed();
        this.defaultAutoCommit = v;
    }

    /**
     * 从这个池发出的连接状态.
     * 可以使用Connection.setReadOnly(boolean)在Connection上修改该值. 默认是 <code>null</code>.
     *
     * @return value of defaultReadOnly.
     */
    public Boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }

    /**
     * 从这个池发出的连接状态.
     * 可以使用Connection.setReadOnly(boolean)在Connection上修改该值. 默认是 <code>null</code>.
     *
     * @param v 
     */
    public void setDefaultReadOnly(final Boolean v) {
        assertInitializationAllowed();
        this.defaultReadOnly = v;
    }

    /**
     * 从这个池发出的连接状态.
     * 可以使用Connection.setTransactionIsolation(int)在Connection上修改该值.
     * 如果返回 -1, 默认值是JDBC驱动程序相关.
     *
     * @return value of defaultTransactionIsolation.
     */
    public int getDefaultTransactionIsolation() {
        return defaultTransactionIsolation;
    }

    /**
     * 从这个池发出的连接状态.
     * 可以使用Connection.setTransactionIsolation(int)在Connection上修改该值.
     * 默认是JDBC驱动程序相关.
     *
     * @param v 
     */
    public void setDefaultTransactionIsolation(final int v) {
        assertInitializationAllowed();
        switch (v) {
        case Connection.TRANSACTION_NONE:
        case Connection.TRANSACTION_READ_COMMITTED:
        case Connection.TRANSACTION_READ_UNCOMMITTED:
        case Connection.TRANSACTION_REPEATABLE_READ:
        case Connection.TRANSACTION_SERIALIZABLE:
            break;
        default:
            throw new IllegalArgumentException(BAD_TRANSACTION_ISOLATION);
        }
        this.defaultTransactionIsolation = v;
    }

    /**
     * 此属性由JDBC定义, 以便与可能部署数据源的GUI（或其他）工具一起使用. 它没有内部用途.
     */
    public String getDescription() {
        return description;
    }

    /**
     * 此属性由JDBC定义, 以便与可能部署数据源的GUI（或其他）工具一起使用. 它没有内部用途.
     *
     * @param v 
     */
    public void setDescription(final String v) {
        this.description = v;
    }

    protected String getInstanceKey() {
        return instanceKey;
    }

    /**
     * 获取在实例化jndi InitialContext时使用的jndiEnvironment的值. 此InitialContext用于查找ConnectionPoolDataSource.
     * 
     * @param key 环境属性名称
     * @return value of jndiEnvironment.
     */
    public String getJndiEnvironment(final String key) {
        String value = null;
        if (jndiEnvironment != null) {
            value = jndiEnvironment.getProperty(key);
        }
        return value;
    }

    /**
     * 设置实例化JNDI InitialContext时要使用的给定JNDI环境属性的值. 此InitialContext用于查找ConnectionPoolDataSource.
     *
     * @param key 要设置的JNDI环境属性.
     * @param value 分配给指定JNDI环境属性的值.
     */
    public void setJndiEnvironment(final String key, final String value) {
        if (jndiEnvironment == null) {
            jndiEnvironment = new Properties();
        }
        jndiEnvironment.setProperty(key, value);
    }

    /**
     * 设置实例化JNDI InitialContext时要使用的JNDI环境. 此InitialContext用于查找ConnectionPoolDataSource.
     *
     * @param properties 要设置的JNDI环境属性将覆盖任何当前设置
     */
    void setJndiEnvironment(final Properties properties) {
        if (jndiEnvironment == null) {
            jndiEnvironment = new Properties();
        } else {
            jndiEnvironment.clear();
        }
        jndiEnvironment.putAll(properties);
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public void setLoginTimeout(final int v) {
        this.loginTimeout = v;
    }

    @Override
    public PrintWriter getLogWriter() {
        if (logWriter == null) {
            logWriter = new PrintWriter(
                    new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        }
        return logWriter;
    }

    @Override
    public void setLogWriter(final PrintWriter v) {
        this.logWriter = v;
    }

    /**
     * 在将它们返回给调用者之前, 将用于验证来自此池的连接的SQL查询.
     * 如果指定了, 此查询必须是返回至少一行的SQL SELECT语句. 如果未指定, {@link Connection#isValid(int)} 将用于验证连接.
     */
    public String getValidationQuery() {
        return this.validationQuery;
    }

    /**
     * 在将它们返回给调用者之前, 将用于验证来自此池的连接的SQL查询.
     * 如果指定了, 此查询必须是返回至少一行的SQL SELECT语句. 如果未指定, {@link Connection#isValid(int)} 将用于验证连接.
     * 
     * @param validationQuery 验证查询
     */
    public void setValidationQuery(final String validationQuery) {
        assertInitializationAllowed();
        this.validationQuery = validationQuery;
    }

    /**
     * @return 验证查询失败前的超时时间（以秒为单位）.
     */
    public int getValidationQueryTimeout() {
        return validationQueryTimeout;
    }

    /**
     * 验证查询失败前的超时时间（以秒为单位）.
     *
     * @param validationQueryTimeout  超时时间（以秒为单位）
     */
    public void setValidationQueryTimeout(final int validationQueryTimeout) {
        this.validationQueryTimeout = validationQueryTimeout;
    }

    /**
     * 是否在执行SQL查询之后发出回滚, 该查询将用于在将这些连接返回给调用方之前验证来自此池的连接.
     *
     * @return true 如果在执行验证查询后回滚
     */
    public boolean isRollbackAfterValidation() {
        return this.rollbackAfterValidation;
    }

    /**
     * 是否在执行SQL查询之后发出回滚, 该查询将用于在将这些连接返回给调用方之前验证来自此池的连接.
     * 默认行为是不回滚. 只有设置了验证查询, 该设置才会生效.
     *
     * @param rollbackAfterValidation 属性值
     */
    public void setRollbackAfterValidation(final boolean rollbackAfterValidation) {
        assertInitializationAllowed();
        this.rollbackAfterValidation = rollbackAfterValidation;
    }

    /**
     * @return 连接的最大允许生命周期（毫秒）. 零或更小的值表示无限寿命.
     */
    public long getMaxConnLifetimeMillis() {
        return maxConnLifetimeMillis;
    }

    /**
     * <p>设置连接的最大允许生存期（毫秒）. 零或更小的值表示无限寿命.</p>
     * <p>
     * Note: 池初始化后, 此方法当前无效. 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     * 
     * @param maxConnLifetimeMillis 连接的最大寿命
     */
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }

    // ----------------------------------------------------------------------
    // DataSource implementation

    /**
     * 尝试建立数据库连接.
     * @return the connection
     * @throws SQLException 连接失败
     */
    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(null, null);
    }

    /**
     * 尝试使用提供的用户名和密码使用{@link #getPooledConnectionAndInfo(String，String)}检索数据库连接.
     * 将<code>getPooledConnectionAndInfo</code>返回的{@link PooledConnectionAndInfo}实例上的密码与<code>password</code>参数进行比较.
     * 如果比较失败, 尝试使用提供的用户名和密码建立数据库连接. 如果连接尝试失败, 抛出 SQLException, 指示给定的密码与用于创建池连接的密码不匹配.
     * 如果连接尝试成功, 意味着数据库密码已更改. 在这种情况下, 销毁使用旧密码检索的<code>PooledConnectionAndInfo</code>实例, 
     * 并重复调用<code>getPooledConnectionAndInfo</code>, 直到返回带有新密码的<code>PooledConnectionAndInfo</code>实例.
     * 
     * @param username 用于连接的用户名
     * @param password 密码
     * @return the connection
     * @throws SQLException 连接失败
     */
    @Override
    public Connection getConnection(final String username, final String password)
            throws SQLException {
        if (instanceKey == null) {
            throw new SQLException("Must set the ConnectionPoolDataSource "
                    + "through setDataSourceName or setConnectionPoolDataSource"
                    + " before calling getConnection.");
        }
        getConnectionCalled = true;
        PooledConnectionAndInfo info = null;
        try {
            info = getPooledConnectionAndInfo(username, password);
        } catch (final NoSuchElementException e) {
            closeDueToException(info);
            throw new SQLException("Cannot borrow connection from pool", e);
        } catch (final RuntimeException e) {
            closeDueToException(info);
            throw e;
        } catch (final SQLException e) {
            closeDueToException(info);
            throw e;
        } catch (final Exception e) {
            closeDueToException(info);
            throw new SQLException("Cannot borrow connection from pool", e);
        }

        if (!(null == password ? null == info.getPassword()
                : password.equals(info.getPassword()))) {  // PooledConnectionAndInfo上的密码不匹配
            try { // 通过尝试连接查看密码是否已更改
                testCPDS(username, password);
            } catch (final SQLException ex) {
                // 密码没有改变, 所以拒绝客户端, 但返回连接到池中
                closeDueToException(info);
                throw new SQLException("Given password did not match password used"
                                       + " to create the PooledConnection.", ex);
            } catch (final javax.naming.NamingException ne) {
                throw new SQLException(
                        "NamingException encountered connecting to database", ne);
            }
            /*
             * 密码必须已更改 -> 销毁连接并继续重试，直到得到一个新的, 当从池中拉出它们时, 用旧密码销毁任何空闲连接.
             */
            final UserPassKey upkey = info.getUserPassKey();
            final PooledConnectionManager manager = getConnectionManager(upkey);
            manager.invalidate(info.getPooledConnection()); // 从池中销毁并移除
            manager.setPassword(upkey.getPassword()); // 如果使用CPDSConnectionFactory, 请在工厂重置密码
            info = null;
            for (int i = 0; i < 10; i++) { // 限制重试次数 - 只有在坏实例返回时才需要
                try {
                    info = getPooledConnectionAndInfo(username, password);
                } catch (final NoSuchElementException e) {
                    closeDueToException(info);
                    throw new SQLException("Cannot borrow connection from pool", e);
                } catch (final RuntimeException e) {
                    closeDueToException(info);
                    throw e;
                } catch (final SQLException e) {
                    closeDueToException(info);
                    throw e;
                } catch (final Exception e) {
                    closeDueToException(info);
                    throw new SQLException("Cannot borrow connection from pool", e);
                }
                if (info != null && password != null && password.equals(info.getPassword())) {
                    break;
                }
                if (info != null) {
                    manager.invalidate(info.getPooledConnection());
                }
                info = null;
            }
            if (info == null) {
                throw new SQLException("Cannot borrow connection from pool - password change failure.");
            }
        }

        final Connection con = info.getPooledConnection().getConnection();
        try {
            setupDefaults(con, username);
            con.clearWarnings();
            return con;
        } catch (final SQLException ex) {
            try {
                con.close();
            } catch (final Exception exc) {
                getLogWriter().println(
                     "ignoring exception during close: " + exc);
            }
            throw ex;
        }
    }

    protected abstract PooledConnectionAndInfo
        getPooledConnectionAndInfo(String username, String password)
        throws SQLException;

    protected abstract void setupDefaults(Connection con, String username)
        throws SQLException;


    private void closeDueToException(final PooledConnectionAndInfo info) {
        if (info != null) {
            try {
                info.getPooledConnection().getConnection().close();
            } catch (final Exception e) {
                // 不要抛出此异常, 因为正在处理另一个异常. 但记录它, 是因为它可能泄漏池中的连接.
                getLogWriter().println("[ERROR] Could not return connection to "
                    + "pool during exception handling. " + e.getMessage());
            }
        }
    }

    protected ConnectionPoolDataSource
        testCPDS(final String username, final String password)
        throws javax.naming.NamingException, SQLException {
        // 物理数据库连接的源
        ConnectionPoolDataSource cpds = this.dataSource;
        if (cpds == null) {
            Context ctx = null;
            if (jndiEnvironment == null) {
                ctx = new InitialContext();
            } else {
                ctx = new InitialContext(jndiEnvironment);
            }
            final Object ds = ctx.lookup(dataSourceName);
            if (ds instanceof ConnectionPoolDataSource) {
                cpds = (ConnectionPoolDataSource) ds;
            } else {
                throw new SQLException("Illegal configuration: "
                    + "DataSource " + dataSourceName
                    + " (" + ds.getClass().getName() + ")"
                    + " doesn't implement javax.sql.ConnectionPoolDataSource");
            }
        }

        // 尝试使用提供的用户名/密码建立连接
        PooledConnection conn = null;
        try {
            if (username != null) {
                conn = cpds.getPooledConnection(username, password);
            }
            else {
                conn = cpds.getPooledConnection();
            }
            if (conn == null) {
                throw new SQLException(
                    "Cannot connect using the supplied username/password");
            }
        }
        finally {
            if (conn != null) {
                try {
                    conn.close();
                }
                catch (final SQLException e) {
                    // 至少可以连接
                }
            }
        }
        return cpds;
    }
}
