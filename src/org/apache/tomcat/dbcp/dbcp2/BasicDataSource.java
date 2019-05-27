package org.apache.tomcat.dbcp.dbcp2;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.sql.DataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.AbandonedConfig;
import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPoolConfig;

/**
 * <p><code>javax.sql.DataSource</code>的基础实现类, 通过 JavaBeans 属性配置.
 * 这不是组合<em>commons-dbcp2</em> 和 <em>commons-pool2</em> 包的唯一方式, 但为基本要求提供“一站式”解决方案.</p>
 */
public class BasicDataSource implements DataSource, BasicDataSourceMXBean, MBeanRegistration, AutoCloseable {

    private static final Log log = LogFactory.getLog(BasicDataSource.class);

    static {
        // 尝试防止死锁 - see DBCP - 272
        DriverManager.getDrivers();
        try {
            // 现在加载类以防止以后出现AccessControlException
            // 调用getConnection()时会加载许多类, 但是未加载以下类, 因此需要显式加载.
            if (Utils.IS_SECURITY_ENABLED) {
                final ClassLoader loader = BasicDataSource.class.getClassLoader();
                final String dbcpPackageName = BasicDataSource.class.getPackage().getName();
                loader.loadClass(dbcpPackageName + ".BasicDataSource$PaGetConnection");
                loader.loadClass(dbcpPackageName + ".DelegatingCallableStatement");
                loader.loadClass(dbcpPackageName + ".DelegatingDatabaseMetaData");
                loader.loadClass(dbcpPackageName + ".DelegatingPreparedStatement");
                loader.loadClass(dbcpPackageName + ".DelegatingResultSet");
                loader.loadClass(dbcpPackageName + ".PoolableCallableStatement");
                loader.loadClass(dbcpPackageName + ".PoolablePreparedStatement");
                loader.loadClass(dbcpPackageName + ".PoolingConnection$StatementType");
                loader.loadClass(dbcpPackageName + ".PStmtKey");

                final String poolPackageName = PooledObject.class.getPackage().getName();
                loader.loadClass(poolPackageName + ".impl.LinkedBlockingDeque$Node");
                loader.loadClass(poolPackageName + ".impl.GenericKeyedObjectPool$ObjectDeque");
            }
        } catch (final ClassNotFoundException cnfe) {
            throw new IllegalStateException("Unable to pre-load classes", cnfe);
        }
    }

    // ------------------------------------------------------------- Properties

    /**
     * 此池创建的连接的默认自动提交状态.
     */
    private volatile Boolean defaultAutoCommit = null;

    /**
     * 返回默认的自动提交属性.
     *
     * @return true 如果启用自动提交
     */
    @Override
    public Boolean getDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    /**
     * <p>设置此数据源返回的连接的默认自动提交状态.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultAutoCommit 默认自动提交值
     */
    public void setDefaultAutoCommit(final Boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }


    /**
     * 此池创建的连接的默认只读状态.
     */
    private transient Boolean defaultReadOnly = null;

    /**
     * 返回默认的readOnly 属性.
     *
     * @return true 如果连接默认只读
     */
    @Override
    public Boolean getDefaultReadOnly() {
        return defaultReadOnly;
    }

    /**
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultReadOnly 默认的 read-only 值
     */
    public void setDefaultReadOnly(final Boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly;
    }

    /**
     * 此池创建的连接的默认TransactionIsolation状态.
     */
    private volatile int defaultTransactionIsolation =
        PoolableConnectionFactory.UNKNOWN_TRANSACTIONISOLATION;

    /**
     * 返回连接的默认事务隔离状态.
     */
    @Override
    public int getDefaultTransactionIsolation() {
        return this.defaultTransactionIsolation;
    }

    /**
     * <p>设置连接的默认事务隔离状态.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultTransactionIsolation 默认事务隔离状态
     */
    public void setDefaultTransactionIsolation(final int defaultTransactionIsolation) {
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }


    private Integer defaultQueryTimeout = null;

    /**
     * 获取将用于从此连接创建的{@link java.sql.Statement Statement}的默认查询超时时间.
     * <code>null</code> 将使用驱动默认.
     */
    public Integer getDefaultQueryTimeout() {
        return defaultQueryTimeout;
    }


    /**
     * 设置将用于从此连接创建的{@link java.sql.Statement Statement}的默认查询超时时间.
     * <code>null</code> 将使用驱动默认.
     * 
     * @param defaultQueryTimeout 默认超时时间
     */
    public void setDefaultQueryTimeout(final Integer defaultQueryTimeout) {
        this.defaultQueryTimeout = defaultQueryTimeout;
    }


    /**
     * 此池创建的连接的默认 "catalog".
     */
    private volatile String defaultCatalog = null;

    /**
     * 返回默认的 catalog.
     */
    @Override
    public String getDefaultCatalog() {
        return this.defaultCatalog;
    }

    /**
     * <p>设置默认的 catalog.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultCatalog 默认的 catalog
     */
    public void setDefaultCatalog(final String defaultCatalog) {
        if (defaultCatalog != null && defaultCatalog.trim().length() > 0) {
            this.defaultCatalog = defaultCatalog;
        }
        else {
            this.defaultCatalog = null;
        }
    }

    /**
     * 控制池连接是否缓存某些状态, 而不是查询数据库以获取当前状态, 以提高性能的属性.
     */
    private boolean cacheState = true;

    /**
     * 返回状态缓存标志.
     */
    @Override
    public boolean getCacheState() {
        return cacheState;
    }

    /**
     * 设置状态缓存标志.
     *
     * @param cacheState    状态缓存标志
     */
    public void setCacheState(final boolean cacheState) {
        this.cacheState = cacheState;
    }

    /**
     * 要使用的 JDBC Driver.
     */
    private Driver driver = null;

    /**
     * 返回此池配置使用的JDBC驱动程序.
     * <p>
     * Note: 这个 getter 只返回{@link #setDriver(Driver)}设置的最后一个值.
     * 它不会返回可能通过{@link #setDriverClassName(String)}从值集创建的驱动程序实例.
     *
     * @return 此池配置使用的JDBC驱动程序
     */
    public synchronized Driver getDriver() {
        return driver;
    }

    /**
     * 设置此池配置使用的JDBC驱动程序.
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param driver 要使用的驱动程序
     */
    public synchronized void setDriver(final Driver driver) {
        this.driver = driver;
    }

    /**
     * 要使用的JDBC驱动程序的标准Java类名.
     */
    private String driverClassName = null;

    /**
     * 返回JDBC驱动程序类名称.
     * <p>
     * Note: 这个 getter 只返回{@link #setDriverClassName(String)}设置的最后一个值.
     * 它不会返回可能通过{@link #setDriver(Driver)}设置的驱动程序的类名.
     */
    @Override
    public synchronized String getDriverClassName() {
        return this.driverClassName;
    }

    /**
     * <p>设置JDBC驱动程序类名称.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param driverClassName JDBC驱动程序类名
     */
    public synchronized void setDriverClassName(final String driverClassName) {
        if (driverClassName != null && driverClassName.trim().length() > 0) {
            this.driverClassName = driverClassName;
        }
        else {
            this.driverClassName = null;
        }
    }

    /**
     * 用于加载JDBC驱动程序的类加载器实例.
     * 如果未指定, {@link Class#forName(String)}用于加载JDBC驱动程序.
     * 如果指定了, 使用{@link Class#forName(String, boolean, ClassLoader)}.
     */
    private ClassLoader driverClassLoader = null;

    /**
     * 返回为加载JDBC驱动程序指定的类加载器. 返回<code>null</code>, 如果没有明确指定类加载器.
     * <p>
     * Note: 这个 getter 只返回 {@link #setDriverClassLoader(ClassLoader)} 设置的最后一个值.
     * 它不返回任何可能通过{@link #setDriver(Driver)}设置的驱动程序的类加载器.
     */
    public synchronized ClassLoader getDriverClassLoader() {
        return this.driverClassLoader;
    }

    /**
     * <p>设置用于加载JDBC驱动程序的类加载器.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param driverClassLoader 用于加载JDBC驱动程序的类加载器
     */
    public synchronized void setDriverClassLoader(
            final ClassLoader driverClassLoader) {
        this.driverClassLoader = driverClassLoader;
    }

    /**
     * True 表示borrowObject返回池中最近使用的连接 (如果有空闲连接可用).
     * False 表示池表现为FIFO队列 - 连接是从空闲实例池中按照它们返回池中的顺序获取的.
     */
    private boolean lifo = BaseObjectPoolConfig.DEFAULT_LIFO;

    /**
     * 返回LIFO属性.
     *
     * @return <code>true</code> 如果连接池表现为LIFO队列.
     */
    @Override
    public synchronized boolean getLifo() {
        return this.lifo;
    }

    /**
     * 设置LIFO属性. True 表示池的行为与LIFO队列相同; false 意味着 FIFO.
     *
     * @param lifo LIFO属性
     */
    public synchronized void setLifo(final boolean lifo) {
        this.lifo = lifo;
        if (connectionPool != null) {
            connectionPool.setLifo(lifo);
        }
    }

    /**
     * 可以同时从此池分配的最大活动连接数, 或负值无限制.
     */
    private int maxTotal = GenericObjectPoolConfig.DEFAULT_MAX_TOTAL;

    /**
     * <p>返回可以同时分配的最大活动连接数. </p>
     * <p>负数表示没有限制.</p>
     *
     * @return 最大活动连接数
     */
    @Override
    public synchronized int getMaxTotal() {
        return this.maxTotal;
    }

    /**
     * 设置可以同时处于活动状态的最大空闲和借用连接总数. 负数表示没有限制.
     *
     * @param maxTotal
     */
    public synchronized void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        if (connectionPool != null) {
            connectionPool.setMaxTotal(maxTotal);
        }
    }

    /**
     * 池中可以保持空闲的最大连接数, 没有额外的连接被销毁, 或者没有限制.
     * 如果在负载很重的系统上将maxIdle设置得太低，您可能会看到连接被关闭，几乎立即打开新连接.
     * 这是因为活动线程暂时关闭连接的速度比打开它们的速度快，导致空闲连接数超过maxIdle.
     * 对于负载较重的系统，maxIdle的最佳值会有所不同，但默认值是一个很好的起点.
     */
    private int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;

    /**
     * <p>返回可在池中保持空闲的最大连接数. 过多的空闲连接在返回池时会被销毁.
     * </p>
     * <p>负值表示没有限制</p>
     *
     * @return the maximum number of idle connections
     */
    @Override
    public synchronized int getMaxIdle() {
        return this.maxIdle;
    }

    /**
     * 设置可在池中保持空闲的最大连接数. 过多的空闲连接在返回池时会被销毁.
     *
     * @param maxIdle
     */
    public synchronized void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
        if (connectionPool != null) {
            connectionPool.setMaxIdle(maxIdle);
        }
    }

    /**
     * 池中可以保持空闲的最小活动连接数，在evictor运行时不创建额外的活动连接，或者不创建.
     * 池在空闲对象evictor运行时尝试确保minIdle连接可用. 除非{@link #timeBetweenEvictionRunsMillis}具有正值，否则此属性的值无效.
     */
    private int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;

    /**
     * 返回池中的最小空闲连接数. 池在空闲对象evictor运行时尝试确保minIdle连接可用.
     * 除非{@link #timeBetweenEvictionRunsMillis}具有正值，否则此属性的值无效.
     */
    @Override
    public synchronized int getMinIdle() {
        return this.minIdle;
    }

    /**
     * 设置池中的最小空闲连接数. 池在空闲对象evictor运行时尝试确保minIdle连接可用.
     * 除非{@link #timeBetweenEvictionRunsMillis}具有正值，否则此属性的值无效.
     *
     * @param minIdle
     */
    public synchronized void setMinIdle(final int minIdle) {
       this.minIdle = minIdle;
       if (connectionPool != null) {
           connectionPool.setMinIdle(minIdle);
       }
    }

    /**
     * 池启动时创建的初始连接数.
     */
    private int initialSize = 0;

    /**
     * 返回连接池的初始大小.
     */
    @Override
    public synchronized int getInitialSize() {
        return this.initialSize;
    }

    /**
     * <p>设置连接池的初始大小.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param initialSize 池初始化时创建的连接数
     */
    public synchronized void setInitialSize(final int initialSize) {
        this.initialSize = initialSize;
    }

    /**
     * 在抛出异常之前，池将等待（没有可用连接时）连接的最大毫秒数，或<= 0无限期等待.
     */
    private long maxWaitMillis =
            BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS;

    /**
     * 返回池在抛出异常之前等待连接返回的最大毫秒数. 小于或等于零的值表示池设置为无限期等待.
     */
    @Override
    public synchronized long getMaxWaitMillis() {
        return this.maxWaitMillis;
    }

    /**
     * 设置 MaxWaitMillis 属性. 使用 -1 让池无限期等待.
     *
     * @param maxWaitMillis
     */
    public synchronized void setMaxWaitMillis(final long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
        if (connectionPool != null) {
            connectionPool.setMaxWaitMillis(maxWaitMillis);
        }
    }

    /**
     * <code>true</code> PreparedStatements 和 CallableStatements 都使用池.
     */
    private boolean poolPreparedStatements = false;

    /**
     * @return <code>true</code> 如果预处理和回调语句都使用池
     */
    @Override
    public synchronized boolean isPoolPreparedStatements() {
        return this.poolPreparedStatements;
    }

    /**
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param poolingStatements
     */
    public synchronized void setPoolPreparedStatements(final boolean poolingStatements) {
        this.poolPreparedStatements = poolingStatements;
    }

    /**
     * <p>可以同时从statement池分配的最大打开statement的数量, 或负值不限制.
     * 由于连接通常一次只使用一个或两个statement，因此主要用于帮助检测资源泄漏.</p>
     *
     * <p>Note: 从版本1.3开始, CallableStatements (由 {@link Connection#prepareCall}生成的)
     * 和 PreparedStatements (由 {@link Connection#prepareStatement}生成的) 和  <code>maxOpenPreparedStatements</code>一起
     * 限制在给定时间使用的预处理的或回调的 statement 的总数.</p>
     */
    private int maxOpenPreparedStatements =
        GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;

    /**
     * 获取<code>maxOpenPreparedStatements</code> 属性的值.
     */
    @Override
    public synchronized int getMaxOpenPreparedStatements() {
        return this.maxOpenPreparedStatements;
    }

    /**
     * <p>设置<code>maxOpenPreparedStatements</code>属性的值.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param maxOpenStatements 预处理的 statement的最大数量
     */
    public synchronized void setMaxOpenPreparedStatements(final int maxOpenStatements) {
        this.maxOpenPreparedStatements = maxOpenStatements;
    }

    /**
     * 指示对象是否在池创建后立即验证. 如果对象无法验证, 触发创建的借用操作将失败.
     */
    private boolean testOnCreate = false;

    /**
     * 返回 {@link #testOnCreate} 属性.
     *
     * @return <code>true</code> 对象是否在池创建后立即验证
     */
    @Override
    public synchronized boolean getTestOnCreate() {
        return this.testOnCreate;
    }

    /**
     * 设置 {@link #testOnCreate} 属性. 此属性确定是否在池创建对象后立即验证对象
     *
     * @param testOnCreate
     */
    public synchronized void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
        if (connectionPool != null) {
            connectionPool.setTestOnCreate(testOnCreate);
        }
    }

    /**
     * 指示在从池中借用对象之前是否验证对象.  如果对象验证失败, 它将从池中删除, 然后借用另一个.
     */
    private boolean testOnBorrow = true;

    /**
     * 返回 {@link #testOnBorrow} 属性.
     *
     * @return <code>true</code> 如果在从池中借用对象之前验证对象
     */
    @Override
    public synchronized boolean getTestOnBorrow() {
        return this.testOnBorrow;
    }

    /**
     * 设置{@link #testOnBorrow} 属性. 此属性确定从池中借用之前是否将验证对象.
     *
     * @param testOnBorrow
     */
    public synchronized void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        if (connectionPool != null) {
            connectionPool.setTestOnBorrow(testOnBorrow);
        }
    }

    /**
     * 是否在返回池之前验证对象.
     */
    private boolean testOnReturn = false;

    /**
     * @return <code>true</code> 如果在返回池之前验证对象
     */
    public synchronized boolean getTestOnReturn() {
        return this.testOnReturn;
    }

    /**
     * 是否在返回池之前验证对象.
     *
     * @param testOnReturn
     */
    public synchronized void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        if (connectionPool != null) {
            connectionPool.setTestOnReturn(testOnReturn);
        }
    }

    /**
     * 在空闲对象 evictor 线程的运行之间休眠的毫秒数.  当非正数时，将不运行空闲对象 evictor 线程.
     */
    private long timeBetweenEvictionRunsMillis =
        BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * @return evictor 线程的运行之间休眠的毫秒数
     */
    @Override
    public synchronized long getTimeBetweenEvictionRunsMillis() {
        return this.timeBetweenEvictionRunsMillis;
    }

    /**
     * 线程的运行之间休眠的毫秒数
     * 
     * @param timeBetweenEvictionRunsMillis
     */
    public synchronized void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        if (connectionPool != null) {
            connectionPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        }
    }

    /**
     * 在每次运行空闲对象 evictor 线程期间要检查的对象数.
     */
    private int numTestsPerEvictionRun =
        BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    /**
     * @return 每次运行空闲对象 evictor 线程期间要检查的对象数
     */
    @Override
    public synchronized int getNumTestsPerEvictionRun() {
        return this.numTestsPerEvictionRun;
    }

    /**
     * 每次运行空闲对象 evictor 线程期间要检查的对象数.
     *
     * @param numTestsPerEvictionRun
     */
    public synchronized void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
        if (connectionPool != null) {
            connectionPool.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        }
    }

    /**
     * 对象在空闲对象 evictor 驱逐之前可能在池中闲置的最短时间.
     */
    private long minEvictableIdleTimeMillis =
        BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * @return 对象在空闲对象 evictor 驱逐之前可能在池中闲置的最短时间.
     */
    @Override
    public synchronized long getMinEvictableIdleTimeMillis() {
        return this.minEvictableIdleTimeMillis;
    }

    /**
     * 对象在空闲对象 evictor 驱逐之前可能在池中闲置的最短时间.
     *
     * @param minEvictableIdleTimeMillis 对象在池中闲置的最短时间
     */
    public synchronized void setMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        if (connectionPool != null) {
            connectionPool.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        }
    }

    /**
     * 连接在空闲对象 evictor 驱逐之前在池中处于空闲状态的最短时间，其中额外条件是至少“minIdle”数量的连接保留在池中.
     * 注意, {@code minEvictableIdleTimeMillis} 优先于此参数.
     */
    private long softMinEvictableIdleTimeMillis =
        BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * 连接在空闲对象 evictor 驱逐之前在池中处于空闲状态的最短时间，其中额外条件是至少“minIdle”数量的连接保留在池中.
     *
     * @param softMinEvictableIdleTimeMillis 处于空闲状态的最短时间, 假设有 minIdle 数量的空闲连接在池中.
     */
    public synchronized void setSoftMinEvictableIdleTimeMillis(final long softMinEvictableIdleTimeMillis) {
        this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
        if (connectionPool != null) {
            connectionPool.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
        }
    }

    /**
     * <p>连接在空闲对象 evictor 驱逐之前在池中处于空闲状态的最短时间，其中额外条件是至少“minIdle”数量的连接保留在池中.</p>
     *
     * <p>当{@link #getMinEvictableIdleTimeMillis() minEvictableIdleTimeMillis} 设置为正数值,
     * minEvictableIdleTimeMillis首先由空闲连接 evictor 检查 - 即，当 evictor 访问空闲连接时, 空闲时间首先跟{@code minEvictableIdleTimeMillis}比较
     * (不考虑池中的空闲连接数), 然后和 {@code softMinEvictableIdleTimeMillis}比较, 包括 {@code minIdle}.</p>
     */
    @Override
    public synchronized long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleTimeMillis;
    }

    private String evictionPolicyClassName =
            BaseObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME;

    /**
     * 获取连接池使用的 EvictionPolicy 实现.
     * @return 驱逐策略
     */
    public synchronized String getEvictionPolicyClassName() {
        return evictionPolicyClassName;
    }

    /**
     * 设置连接池使用的 EvictionPolicy 实现.
     *
     * @param evictionPolicyClassName   EvictionPolicy 实现的完全限定的类名
     */
    public synchronized void setEvictionPolicyClassName(
            final String evictionPolicyClassName) {
        if (connectionPool != null) {
            connectionPool.setEvictionPolicyClassName(evictionPolicyClassName);
        }
        this.evictionPolicyClassName = evictionPolicyClassName;
    }

    /**
     * 对象是否将由空闲对象 evictor 验证.  如果对象验证失败, 将从池中删除.
     */
    private boolean testWhileIdle = false;

    /**
     * @return <code>true</code> 如果空闲对象 evictor 验证对象
     */
    @Override
    public synchronized boolean getTestWhileIdle() {
        return this.testWhileIdle;
    }

    /**
     * 空闲对象 evictor 是否验证连接.
     *
     * @param testWhileIdle
     */
    public synchronized void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
        if (connectionPool != null) {
            connectionPool.setTestWhileIdle(testWhileIdle);
        }
    }

    /**
     * [Read Only] 从此数据源分配的当前活动连接数.
     *
     * @return 当前活动连接数
     */
    @Override
    public int getNumActive() {
        // 如果在null检查后发生关闭，则复制引用以避免NPE
        final GenericObjectPool<PoolableConnection> pool = connectionPool;
        if (pool != null) {
            return pool.getNumActive();
        }
        return 0;
    }


    /**
     * [Read Only] 等待从此数据源分配的当前空闲连接数.
     */
    @Override
    public int getNumIdle() {
        // 如果在null检查后发生关闭，则复制引用以避免NPE
        final GenericObjectPool<PoolableConnection> pool = connectionPool;
        if (pool != null) {
            return pool.getNumIdle();
        }
        return 0;
    }

    /**
     * 要传递给JDBC驱动程序以建立连接的连接密码.
     */
    private volatile String password = null;

    /**
     * 返回传递给JDBC驱动程序以建立连接的密码.
     */
    @Override
    public String getPassword() {
        return this.password;
    }

    /**
     * <p>设置 {@link #password}.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param password
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * 要传递给JDBC驱动程序以建立连接的连接URL.
     */
    private String url = null;

    /**
     * 返回 JDBC 连接 {@link #url} 属性.
     *
     * @return 传递给JDBC驱动程序的{@link #url}以建立连接
     */
    @Override
    public synchronized String getUrl() {
        return this.url;
    }

    /**
     * <p>设置 {@link #url}.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param url
     */
    public synchronized void setUrl(final String url) {
        this.url = url;
    }

    /**
     * 要传递给JDBC驱动程序以建立连接的连接用户名.
     */
    private String username = null;

    /**
     * 返回 JDBC 连接 {@link #username} 属性.
     *
     * @return 传递给JDBC驱动程序的{@link #username}以建立连接
     */
    @Override
    public String getUsername() {
        return this.username;
    }

    /**
     * <p>设置 {@link #username}.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param username
     */
    public void setUsername(final String username) {
        this.username = username;
    }

    /**
     * 在将它们返回给调用者之前, 将用于验证来自此池的连接的SQL查询.
     * 如果指定了, 查询必须是一个至少返回一行的 SQL SELECT 语句.
     * 如果未指定, {@link Connection#isValid(int)} 将用于验证连接.
     */
    private volatile String validationQuery = null;

    /**
     * 用于在返回连接之前验证连接的验证查询.
     *
     * @return SQL 验证查询
     */
    @Override
    public String getValidationQuery() {
        return this.validationQuery;
    }

    /**
     * <p>设置 {@link #validationQuery}.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param validationQuery
     */
    public void setValidationQuery(final String validationQuery) {
        if (validationQuery != null && validationQuery.trim().length() > 0) {
            this.validationQuery = validationQuery;
        } else {
            this.validationQuery = null;
        }
    }

    /**
     * 连接验证查询失败前的超时时间（以秒为单位）.
     */
    private volatile int validationQueryTimeout = -1;

    /**
     * 返回验证查询超时时间（以秒为单位）.
     */
    @Override
    public int getValidationQueryTimeout() {
        return validationQueryTimeout;
    }

    /**
     * 设置验证查询超时时间，连接验证在执行验证查询时等待数据库响应的时间（以秒为单位）.  使用小于或等于0的值表示没有超时.
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param timeout
     */
    public void setValidationQueryTimeout(final int timeout) {
        this.validationQueryTimeout = timeout;
    }

    /**
     * 创建Connection后，这些SQL语句将运行一次.
     * <p>
     * 例如，此属性可用于在创建连接后仅在Oracle数据库中运行ALTER SESSION SET NLS_SORT = XCYECH.
     * </p>
     */
    private volatile List<String> connectionInitSqls;

    /**
     * 返回首次创建物理连接时执行的SQL语句列表. 如果没有配置初始化语句，则返回空列表.
     */
    public List<String> getConnectionInitSqls() {
        final List<String> result = connectionInitSqls;
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }

    /**
     * 提供和 {@link #getConnectionInitSqls()} 相同的数据, 但是在一个数组中，所以可以通过JMX访问它.
     */
    @Override
    public String[] getConnectionInitSqlsAsArray() {
        final Collection<String> result = getConnectionInitSqls();
        return result.toArray(new String[result.size()]);
    }

    /**
     * 设置首次创建物理连接时要执行的SQL语句列表.
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param connectionInitSqls 要在连接创建时执行的SQL语句的集合
     */
    public void setConnectionInitSqls(final Collection<String> connectionInitSqls) {
        if (connectionInitSqls != null && connectionInitSqls.size() > 0) {
            ArrayList<String> newVal = null;
            for (final String s : connectionInitSqls) {
            if (s != null && s.trim().length() > 0) {
                    if (newVal == null) {
                        newVal = new ArrayList<>();
                    }
                    newVal.add(s);
                }
            }
            this.connectionInitSqls = newVal;
        } else {
            this.connectionInitSqls = null;
        }
    }


    /**
     * 控制对底层连接的访问.
     */
    private boolean accessToUnderlyingConnectionAllowed = false;

    /**
     * @return true 如果允许访问底层连接, 否则false
     */
    @Override
    public synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * <p>
     * 控制PoolGuard是否允许访问底层连接. (Default: false)</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param allow 如果为true，则授予对底层连接的访问权限.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }


    private long maxConnLifetimeMillis = -1;

    /**
     * 返回连接的最大允许生存期（以毫秒为单位）. 零或更小的值表示无限寿命.
     */
    @Override
    public long getMaxConnLifetimeMillis() {
        return maxConnLifetimeMillis;
    }

    private boolean logExpiredConnections = true;

    /**
     * 当{@link #getMaxConnLifetimeMillis()}设置为限制连接生存期时, 此属性确定当池由于超出最大生命周期而关闭连接时, 是否生成日志消息.
     */
    @Override
    public boolean getLogExpiredConnections() {
        return logExpiredConnections;
    }

    /**
     * <p>设置连接的最大允许生存期（以毫秒为单位）. 零或更小的值表示无限寿命.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     * 
     * @param maxConnLifetimeMillis 连接最大生命周期
     */
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }

    /**
     * 当{@link #getMaxConnLifetimeMillis()}设置为限制连接生存期时, 此属性确定当池由于超出最大生命周期而关闭连接时, 是否生成日志消息.
     * 将此属性设置为false, 可在连接过期时禁止日志消息.
     * 
     * @param logExpiredConnections <code>true</code> 记录过期的连接
     */
    public void setLogExpiredConnections(final boolean logExpiredConnections) {
        this.logExpiredConnections = logExpiredConnections;
    }

    private String jmxName = null;

    /**
     * @return 已为此DataSource请求的JMX名称. 如果请求的名称无效, 可以选择替代方案.
     */
    public String getJmxName() {
        return jmxName;
    }

    /**
     * 设置为此DataSource请求的JMX名称.
     * 如果请求的名称无效, 可以选择替代方案. 此DataSource将尝试使用此名称注册自己.
     * 如果另一个组件使用JMX注册此DataSource并且此名称有效, 则此名称将优先于其他组件指定的名称使用.
     * 
     * @param jmxName The JMX name
     */
    public void setJmxName(final String jmxName) {
        this.jmxName = jmxName;
    }


    private boolean enableAutoCommitOnReturn = true;

    /**
     * 如果在返回连接时自动提交设置为{@code false}，则控制是否将使用{@link Connection#setAutoCommit(boolean)}检查和配置返回到池的连接.
     * 默认是<code>true</code>.
     * 
     * @return <code>true</code>自动提交
     */
    public boolean getEnableAutoCommitOnReturn() {
        return enableAutoCommitOnReturn;
    }

    /**
     * 如果在返回连接时自动提交设置为{@code false}，则控制是否将使用{@link Connection#setAutoCommit(boolean)}检查和配置返回到池的连接.
     * 默认是<code>true</code>.
     * 
     * @param enableAutoCommitOnReturn The new value
     */
    public void setEnableAutoCommitOnReturn(final boolean enableAutoCommitOnReturn) {
        this.enableAutoCommitOnReturn = enableAutoCommitOnReturn;
    }

    private boolean rollbackOnReturn = true;

    /**
     * 如果在未启用自动提交且连接不是只读的情况下将连接返回到池中时，控制是否回滚连接.
     * 
     * @return <code>true</code>回滚未提交的连接
     */
    public boolean getRollbackOnReturn() {
        return rollbackOnReturn;
    }

    /**
     * 如果在未启用自动提交且连接不是只读的情况下将连接返回到池中时，控制是否回滚连接.
     * 
     * @param rollbackOnReturn The new value
     */
    public void setRollbackOnReturn(final boolean rollbackOnReturn) {
        this.rollbackOnReturn = rollbackOnReturn;
    }

    private volatile Set<String> disconnectionSqlCodes;

    /**
     * 返回被视为发出致命条件信号的SQL_STATE代码集.
     */
    public Set<String> getDisconnectionSqlCodes() {
        final Set<String> result = disconnectionSqlCodes;
        if (result == null) {
            return Collections.emptySet();
        }
        return result;
    }

    /**
     * 提供和{@link #getDisconnectionSqlCodes}相同的数据, 但是在一个数组中, 所以可以通过JMX访问它.
     * 
     * @return 致命的断开状态代码
     */
    @Override
    public String[] getDisconnectionSqlCodesAsArray() {
        final Collection<String> result = getDisconnectionSqlCodes();
        return result.toArray(new String[result.size()]);
    }

    /**
     * 设置被视为发出致命条件信号的SQL_STATE代码集.
     * <p>
     * 覆盖{@link Utils #DISCONNECTION_SQL_CODES}中的默认值 (加上任何以{@link Utils #DISCONNECTION_SQL_CODE_PREFIX}开头的内容).
     * 如果此属性为非null且{@link #getFastFailValidation()}为{@code true}, 每当此数据源创建的连接在此列表中生成带有SQL_STATE代码的异常时,
     * 它们将被标记为“致命断开连接”，后续验证将快速失败(不尝试isValid或验证查询).</p>
     * <p>
     * 如果{@link #getFastFailValidation()} 是 {@code false}, 设置这个属性无效.</p>
     * <p>
     * Note: 池初始化后，此方法无效.
     * 第一次调用以下方法之一时初始化池: <code>getConnection, setLogwriter, setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param disconnectionSqlCodes SQL_STATE代码被认为是致命的信号
     */
    public void setDisconnectionSqlCodes(final Collection<String> disconnectionSqlCodes) {
        if (disconnectionSqlCodes != null && disconnectionSqlCodes.size() > 0) {
            HashSet<String> newVal = null;
            for (final String s : disconnectionSqlCodes) {
            if (s != null && s.trim().length() > 0) {
                    if (newVal == null) {
                        newVal = new HashSet<>();
                    }
                    newVal.add(s);
                }
            }
            this.disconnectionSqlCodes = newVal;
        } else {
            this.disconnectionSqlCodes = null;
        }
    }

    private boolean fastFailValidation;

    /**
     * True表示对于之前使用SQL_STATE抛出SQLExceptions(致命断开连接错误)的连接，验证将立即失败.
     *
     * @return true 如果此数据源创建的连接将快速验证失败.
     */
    @Override
    public boolean getFastFailValidation() {
        return fastFailValidation;
    }

    /**
     * @param fastFailValidation true表示此工厂创建的连接将快速验证失败
     */
    public void setFastFailValidation(final boolean fastFailValidation) {
        this.fastFailValidation = fastFailValidation;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * 内部管理连接的对象池.
     */
    private volatile GenericObjectPool<PoolableConnection> connectionPool = null;

    protected GenericObjectPool<PoolableConnection> getConnectionPool() {
        return connectionPool;
    }

    /**
     * 建立新连接时将发送到JDBC驱动程序的连接属性.
     * <strong>NOTE</strong> - "user" 和 "password" 属性将被显式的传递, 所以它们不需要包含在这里.
     */
    private Properties connectionProperties = new Properties();

    // For unit testing
    Properties getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * 将用于管理连接的数据源.  只应通过调用<code>createDataSource()</code>方法获取此对象.
     */
    private volatile DataSource dataSource = null;

    /**
     * 应将日志消息定向到的PrintWriter.
     */
    private volatile PrintWriter logWriter = new PrintWriter(new OutputStreamWriter(
            System.out, StandardCharsets.UTF_8));


    // ----------------------------------------------------- DataSource Methods


    /**
     * 创建并返回与数据库的连接.
     *
     * @throws SQLException 如果发生数据库访问错误
     * @return a database connection
     */
    @Override
    public Connection getConnection() throws SQLException {
        if (Utils.IS_SECURITY_ENABLED) {
            final PrivilegedExceptionAction<Connection> action = new PaGetConnection();
            try {
                return AccessController.doPrivileged(action);
            } catch (final PrivilegedActionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof SQLException) {
                    throw (SQLException) cause;
                }
                throw new SQLException(e);
            }
        }
        return createDataSource().getConnection();
    }


    /**
     * <strong>BasicDataSource不支持此方法. </strong>
     *
     * @param user 代表Connection进行连接的数据库用户
     * @param pass 数据库用户的密码
     *
     * @throws UnsupportedOperationException 如果不支持
     * @throws SQLException 如果发生数据库访问错误
     * @return nothing - always throws UnsupportedOperationException
     */
    @Override
    public Connection getConnection(final String user, final String pass) throws SQLException {
        // createDataSource返回的PoolingDataSource不支持此方法
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
    }


    /**
     * <strong>BasicDataSource不支持此方法. </strong>
     *
     * <p>返回连接数据库的登录超时时间（以秒为单位）.
     * </p>
     * <p>调用 {@link #createDataSource()}, 所以有初始化连接池的副作用.</p>
     *
     * @throws SQLException 如果发生数据库访问错误
     * @throws UnsupportedOperationException 如果不支持.
     * @return 登录超时时间（秒）
     */
    @Override
    public int getLoginTimeout() throws SQLException {
        // createDataSource返回的PoolingDataSource不支持此方法
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
    }


    /**
     * <p>返回此数据源使用的日志writer.</p>
     * <p>
     * 调用 {@link #createDataSource()}, 所以有初始化连接池的副作用.</p>
     *
     * @throws SQLException 如果发生数据库访问错误
     * @return log writer in use
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return createDataSource().getLogWriter();
    }


    /**
     * <strong>BasicDataSource不支持此方法. </strong>
     *
     * <p>设置连接数据库的登录超时时间（以秒为单位）.</p>
     * <p>
     * 调用 {@link #createDataSource()}, 所以有初始化连接池的副作用.</p>
     *
     * @param loginTimeout 登录超时时间, 或零不超时
     * @throws UnsupportedOperationException 如果不支持.
     * @throws SQLException 如果发生数据库访问错误
     */
    @Override
    public void setLoginTimeout(final int loginTimeout) throws SQLException {
        // createDataSource返回的PoolingDataSource不支持此方法
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
    }


    /**
     * <p>设置此数据源使用的日志 writer.</p>
     * <p>
     * 调用 {@link #createDataSource()}, 所以有初始化连接池的副作用.</p>
     *
     * @param logWriter The new log writer
     * @throws SQLException 如果发生数据库访问错误
     */
    @Override
    public void setLogWriter(final PrintWriter logWriter) throws SQLException {
        createDataSource().setLogWriter(logWriter);
        this.logWriter = logWriter;
    }

    private AbandonedConfig abandonedConfig;

    /**
     * <p>如果在调用borrowObject时超过removeAbandonedTimeout，是否删除已放弃的连接.</p>
     *
     * <p>默认为 false.</p>
     *
     * <p>如果设置为true，则认为连接已被放弃，并且如果连接的使用时间超过{@link #getRemoveAbandonedTimeout() removeAbandonedTimeout}秒，则可以将其删除.</p>
     *
     * <p>在调用{@link #getConnection()}并且满足以下所有条件时，将标识并删除放弃的连接:
     * </p>
     * <ul><li>{@link #getRemoveAbandonedOnBorrow()} </li>
     *     <li>{@link #getNumActive()} &gt; {@link #getMaxTotal()} - 3 </li>
     *     <li>{@link #getNumIdle()} &lt; 2 </li></ul>
     */
    @Override
    public boolean getRemoveAbandonedOnBorrow() {
        if (abandonedConfig != null) {
            return abandonedConfig.getRemoveAbandonedOnBorrow();
        }
        return false;
    }

    /**
     * @param removeAbandonedOnMaintenance true 表示在池维护时可以删除已放弃的连接.
     */
    public void setRemoveAbandonedOnMaintenance(
            final boolean removeAbandonedOnMaintenance) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandonedOnMaintenance(
                removeAbandonedOnMaintenance);
    }

    /**
     * <p>如果在池维护期间超过removeAbandonedTimeout，则删除已放弃的连接.</p>
     *
     * <p>默认为 false.</p>
     *
     * <p>如果设置为true，则认为连接已被放弃，并且如果连接的使用时间超过{@link #getRemoveAbandonedTimeout()}秒，则可以将其删除.</p>
     */
    @Override
    public boolean getRemoveAbandonedOnMaintenance() {
        if (abandonedConfig != null) {
            return abandonedConfig.getRemoveAbandonedOnMaintenance();
        }
        return false;
    }

    /**
     * @param removeAbandonedOnBorrow true表示从池中借用连接时, 可能会删除已放弃的连接.
     */
    public void setRemoveAbandonedOnBorrow(final boolean removeAbandonedOnBorrow) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandonedOnBorrow(removeAbandonedOnBorrow);
    }

    /**
     * <p>删除废弃的连接之前的超时时间（以秒为单位）.</p>
     *
     * <p>创建Statement，PreparedStatement或CallableStatement或使用其中一个执行查询(使用其中一个execute方法)重置父连接的lastUsed属性.</p>
     *
     * <p>废弃的连接清理时发生:</p>
     * <ul>
     * <li>{@link #getRemoveAbandonedOnBorrow()} or
     *     {@link #getRemoveAbandonedOnMaintenance()} = true</li>
     * <li>{@link #getNumIdle() numIdle} &lt; 2</li>
     * <li>{@link #getNumActive() numActive} &gt; {@link #getMaxTotal() maxTotal} - 3</li>
     * </ul>
     *
     * <p>默认是 300 秒.</p>
     */
    @Override
    public int getRemoveAbandonedTimeout() {
        if (abandonedConfig != null) {
            return abandonedConfig.getRemoveAbandonedTimeout();
        }
        return 300;
    }

    /**
     * <p>设置可以删除已废弃的连接之前的超时时间（以秒为单位）.</p>
     *
     * <p>设置这个属性不起作用, 如果 {@link #getRemoveAbandonedOnBorrow()} 和
     * {@link #getRemoveAbandonedOnMaintenance()} 是 false.</p>
     *
     * @param removeAbandonedTimeout 废弃的超时时间, 秒
     */
    public void setRemoveAbandonedTimeout(final int removeAbandonedTimeout) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    }

    /**
     * <p>记录废弃的Statement或Connection的应用程序代码的日志堆栈跟踪.
     * </p>
     * <p>默认 false.
     * </p>
     * <p>记录废弃的 Statement 和 Connection, 会增加每个Connection 打开或创建 Statement的开销，因为必须生成堆栈跟踪. </p>
     */
    @Override
    public boolean getLogAbandoned() {
        if (abandonedConfig != null) {
            return abandonedConfig.getLogAbandoned();
        }
        return false;
    }

    /**
     * @param logAbandoned
     */
    public void setLogAbandoned(final boolean logAbandoned) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setLogAbandoned(logAbandoned);
    }

    /**
     * 获取此配置用于记录废弃的对象信息的日志writer.
     */
    public PrintWriter getAbandonedLogWriter() {
        if (abandonedConfig != null) {
            return abandonedConfig.getLogWriter();
        }
        return null;
    }

    /**
     * 设置此配置用于记录废弃的对象信息的日志writer.
     *
     * @param logWriter The new log writer
     */
    public void setAbandonedLogWriter(final PrintWriter logWriter) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setLogWriter(logWriter);
    }

    /**
     * 如果连接池实现了 {@link org.apache.tomcat.dbcp.pool2.UsageTracking UsageTracking},
     * 每次在池化连接上调用方法时, 连接池是否应记录堆栈跟踪, 并保留最新的堆栈跟踪以帮助调试已废弃的连接?
     *
     * @return <code>true</code>如果启用了使用情况跟踪
     */
    @Override
    public boolean getAbandonedUsageTracking() {
        if (abandonedConfig != null) {
            return abandonedConfig.getUseUsageTracking();
        }
        return false;
    }

    /**
     * 如果连接池实现了 {@link org.apache.tomcat.dbcp.pool2.UsageTracking UsageTracking},
     * 每次在池化连接上调用方法时, 连接池是否应记录堆栈跟踪, 并保留最新的堆栈跟踪以帮助调试已废弃的连接?
     *
     * @param   usageTracking    <code>true</code> 将在每次使用池连接时启用堆栈跟踪记录
     */
    public void setAbandonedUsageTracking(final boolean usageTracking) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setUseUsageTracking(usageTracking);
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 将自定义连接属性添加到将传递给JDBC驱动程序的集合中.
     * 必须在检索第一个连接之前调用它(以及所有其他配置属性 setter). 初始化连接池后调用此方法无效.
     *
     * @param name 自定义连接属性的名称
     * @param value 自定义连接属性的值
     */
    public void addConnectionProperty(final String name, final String value) {
        connectionProperties.put(name, value);
    }

    /**
     * 删除自定义连接属性.
     *
     * @param name 要删除的自定义连接属性的名称
     */
    public void removeConnectionProperty(final String name) {
        connectionProperties.remove(name);
    }

    /**
     * 设置传递到 driver.connect(...)的连接属性.
     *
     * 字符串格式必须是 [propertyName=property;]*
     *
     * NOTE - "user" 和 "password" 属性将被显示添加, 所以他们不需要包含在这里.
     *
     * @param connectionProperties 用于创建新连接的连接属性
     */
    public void setConnectionProperties(final String connectionProperties) {
        if (connectionProperties == null) {
            throw new NullPointerException("connectionProperties is null");
        }

        final String[] entries = connectionProperties.split(";");
        final Properties properties = new Properties();
        for (final String entry : entries) {
            if (entry.length() > 0) {
                final int index = entry.indexOf('=');
                if (index > 0) {
                    final String name = entry.substring(0, index);
                    final String value = entry.substring(index + 1);
                    properties.setProperty(name, value);
                } else {
                    // 没有值是空字符串，这是java.util.Properties的工作方式
                    properties.setProperty(entry, "");
                }
            }
        }
        this.connectionProperties = properties;
    }

    private boolean closed;

    /**
     * <p>关闭并释放当前存储在与此数据源关联的连接池中的所有空闲连接.</p>
     *
     * <p>调用此方法时检出到客户端的连接不受影响.
     * 当客户端应用程序随后调用{@link Connection#close()}以将这些连接返回到池时, 底层JDBC连接关闭.</p>
     *
     * <p>在调用此方法后尝试使用{@link #getConnection()}获取连接会导致SQLExceptions.</p>
     *
     * <p>这种方法是幂等的 - i.e., 关闭已关闭的BasicDataSource不起作用, 也不会生成异常.</p>
     *
     * @throws SQLException if an error occurs closing idle connections
     */
    @Override
    public synchronized void close() throws SQLException {
        if (registeredJmxName != null) {
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            try {
                mbs.unregisterMBean(registeredJmxName);
            } catch (final JMException e) {
                log.warn("Failed to unregister the JMX name: " + registeredJmxName, e);
            } finally {
                registeredJmxName = null;
            }
        }
        closed = true;
        final GenericObjectPool<?> oldpool = connectionPool;
        connectionPool = null;
        dataSource = null;
        try {
            if (oldpool != null) {
                oldpool.close();
            }
        } catch(final RuntimeException e) {
            throw e;
        } catch(final Exception e) {
            throw new SQLException(Utils.getMessage("pool.close.fail"), e);
        }
    }

    /**
     * 如果是 true, 此数据源已关闭，无法从此数据源检索更多连接.
     * @return true, 如果数据源已关闭; 否则 false
     */
    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("BasicDataSource is not a wrapper.");
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * 手动使连接无效, 有效地请求池试图关闭它, 将其从池中删除并回收池容量.
     * 
     * @param connection 要关闭的连接
     * @throws IllegalStateException 如果连接失效.
     */
    public void invalidateConnection(final Connection connection) throws IllegalStateException {
        if (connection == null) {
            return;
        }
        if (connectionPool == null) {
            throw new IllegalStateException("Cannot invalidate connection: ConnectionPool is null.");
        }

        final PoolableConnection poolableConnection;
        try {
            poolableConnection = connection.unwrap(PoolableConnection.class);
            if (poolableConnection == null) {
                throw new IllegalStateException(
                        "Cannot invalidate connection: Connection is not a poolable connection.");
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("Cannot invalidate connection: Unwrapping poolable connection failed.", e);
        }

        try {
            connectionPool.invalidateObject(poolableConnection);
        } catch (final Exception e) {
            throw new IllegalStateException("Invalidating connection threw unexpected exception", e);
        }
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * <p>创建并返回用于管理连接的内部数据源.</p>
     * @return the data source
     * @throws SQLException 如果无法创建对象池.
     */
    protected DataSource createDataSource()
        throws SQLException {
        if (closed) {
            throw new SQLException("Data source is closed");
        }

        // 如果已经创建了池，则返回池
        // 双重校验锁. 这是安全的, 因为 dataSource 是 volatile, 并且代码的目标是Java 5以上.
        if (dataSource != null) {
            return dataSource;
        }
        synchronized (this) {
            if (dataSource != null) {
                return dataSource;
            }

            jmxRegister();

            // 创建返回原始物理连接的工厂
            final ConnectionFactory driverConnectionFactory = createConnectionFactory();

            // 设置poolable连接工厂
            boolean success = false;
            PoolableConnectionFactory poolableConnectionFactory;
            try {
                poolableConnectionFactory = createPoolableConnectionFactory(
                        driverConnectionFactory);
                poolableConnectionFactory.setPoolStatements(
                        poolPreparedStatements);
                poolableConnectionFactory.setMaxOpenPrepatedStatements(
                        maxOpenPreparedStatements);
                success = true;
            } catch (final SQLException se) {
                throw se;
            } catch (final RuntimeException rte) {
                throw rte;
            } catch (final Exception ex) {
                throw new SQLException("Error creating connection factory", ex);
            }

            if (success) {
                // 为连接创建一个池
                createConnectionPool(poolableConnectionFactory);
            }

            // 创建池化数据源以管理连接
            DataSource newDataSource;
            success = false;
            try {
                newDataSource = createDataSourceInstance();
                newDataSource.setLogWriter(logWriter);
                success = true;
            } catch (final SQLException se) {
                throw se;
            } catch (final RuntimeException rte) {
                throw rte;
            } catch (final Exception ex) {
                throw new SQLException("Error creating datasource", ex);
            } finally {
                if (!success) {
                    closeConnectionPool();
                }
            }

            // 如果 initialSize > 0, 预加载池
            try {
                for (int i = 0 ; i < initialSize ; i++) {
                    connectionPool.addObject();
                }
            } catch (final Exception e) {
                closeConnectionPool();
                throw new SQLException("Error preloading the connection pool", e);
            }

            // 如果 timeBetweenEvictionRunsMillis > 0, 启动池的evictor任务
            startPoolMaintenance();

            dataSource = newDataSource;
            return dataSource;
        }
    }

    /**
     * 为此数据源创建JDBC连接工厂. 使用以下算法加载JDBC驱动程序:
     * <ol>
     * <li>如果已通过{@link #setDriver(Driver)}指定了Driver实例，请使用它</li>
     * <li>如果未指定 Driver 实例, 而且 {@link #driverClassName} 指定了使用这个类的 {@link ClassLoader} 加载的类,
     * 或者设置了 {@link #driverClassLoader}, 使用指定的{@link ClassLoader}加载{@link #driverClassName}.</li>
     * <li>如果指定了 {@link #driverClassName}, 而且之前的尝试失败, 使用当前线程的上下文类加载器加载该类.</li>
     * <li>如果仍未加载驱动程序, 则使用指定的{@link #url}通过{@link DriverManager}加载一个驱动程序.
     * </ol>
     * 存在此方法，因此子类可以替换实现类.
     * 
     * @return 连接工厂
     * @throws SQLException 创建连接工厂时出错
     */
    protected ConnectionFactory createConnectionFactory() throws SQLException {
        // 加载JDBC驱动程序类
        Driver driverToUse = this.driver;

        if (driverToUse == null) {
            Class<?> driverFromCCL = null;
            if (driverClassName != null) {
                try {
                    try {
                        if (driverClassLoader == null) {
                            driverFromCCL = Class.forName(driverClassName);
                        } else {
                            driverFromCCL = Class.forName(
                                    driverClassName, true, driverClassLoader);
                        }
                    } catch (final ClassNotFoundException cnfe) {
                        driverFromCCL = Thread.currentThread(
                                ).getContextClassLoader().loadClass(
                                        driverClassName);
                    }
                } catch (final Exception t) {
                    final String message = "Cannot load JDBC driver class '" +
                        driverClassName + "'";
                    logWriter.println(message);
                    t.printStackTrace(logWriter);
                    throw new SQLException(message, t);
                }
            }

            try {
                if (driverFromCCL == null) {
                    driverToUse = DriverManager.getDriver(url);
                } else {
                    // 无法使用DriverManager, 因为它不尊重ContextClassLoader
                    // N.B. 此强制转换可能会导致ClassCastException，这将在下面处理
                    driverToUse = (Driver) driverFromCCL.getConstructor().newInstance();
                    if (!driverToUse.acceptsURL(url)) {
                        throw new SQLException("No suitable driver", "08001");
                    }
                }
            } catch (final Exception t) {
                final String message = "Cannot create JDBC driver of class '" +
                    (driverClassName != null ? driverClassName : "") +
                    "' for connect URL '" + url + "'";
                logWriter.println(message);
                t.printStackTrace(logWriter);
                throw new SQLException(message, t);
            }
        }

        // 设置将使用的驱动程序连接工厂
        final String user = username;
        if (user != null) {
            connectionProperties.put("user", user);
        } else {
            log("DBCP DataSource configured without a 'username'");
        }

        final String pwd = password;
        if (pwd != null) {
            connectionProperties.put("password", pwd);
        } else {
            log("DBCP DataSource configured without a 'password'");
        }

        final ConnectionFactory driverConnectionFactory =
                new DriverConnectionFactory(driverToUse, url, connectionProperties);
        return driverConnectionFactory;
    }

    /**
     * 为此数据源创建连接池.  此方法仅存在，因此子类可以替换实现类.
     *
     * 此实现配置除timeBetweenEvictionRunsMillis之外的所有池属性.
     * 将该属性设置为{@link #startPoolMaintenance()}, 因为将timeBetweenEvictionRunsMillis设置为正值会导致{@link GenericObjectPool}的驱逐计时器启动.
     * @param factory 连接工厂
     */
    protected void createConnectionPool(final PoolableConnectionFactory factory) {
        // 创建一个对象池以包含活动连接
        final GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        updateJmxName(config);
        config.setJmxEnabled(registeredJmxName != null);  // 如果未注册DS，则禁用底层池上的JMX.
        GenericObjectPool<PoolableConnection> gop;
        if (abandonedConfig != null &&
                (abandonedConfig.getRemoveAbandonedOnBorrow() ||
                 abandonedConfig.getRemoveAbandonedOnMaintenance())) {
            gop = new GenericObjectPool<>(factory, config, abandonedConfig);
        }
        else {
            gop = new GenericObjectPool<>(factory, config);
        }
        gop.setMaxTotal(maxTotal);
        gop.setMaxIdle(maxIdle);
        gop.setMinIdle(minIdle);
        gop.setMaxWaitMillis(maxWaitMillis);
        gop.setTestOnCreate(testOnCreate);
        gop.setTestOnBorrow(testOnBorrow);
        gop.setTestOnReturn(testOnReturn);
        gop.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        gop.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        gop.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
        gop.setTestWhileIdle(testWhileIdle);
        gop.setLifo(lifo);
        gop.setSwallowedExceptionListener(new SwallowedExceptionLogger(log, logExpiredConnections));
        gop.setEvictionPolicyClassName(evictionPolicyClassName);
        factory.setPool(gop);
        connectionPool = gop;
    }

    /**
     * 关闭连接池, 忽略发生的任何异常.
     */
    private void closeConnectionPool() {
        final GenericObjectPool<?> oldpool = connectionPool;
        connectionPool = null;
        try {
            if (oldpool != null) {
                oldpool.close();
            }
        } catch(final Exception e) {
            /* Ignore */
        }
    }

    /**
     * 启动连接池维护任务, 如果配置了.
     */
    protected void startPoolMaintenance() {
        if (connectionPool != null && timeBetweenEvictionRunsMillis > 0) {
            connectionPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        }
    }

    /**
     * 创建实际的数据源实例.  此方法存在，以便子类可以替换实现类.
     * @return 数据源
     * @throws SQLException 如果无法创建数据源实例
     */
    protected DataSource createDataSourceInstance() throws SQLException {
        final PoolingDataSource<PoolableConnection> pds = new PoolingDataSource<>(connectionPool);
        pds.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        return pds;
    }

    /**
     * 创建PoolableConnectionFactory并将其附加到连接池.  此方法存在，方便子类可以替换默认实现.
     *
     * @param driverConnectionFactory JDBC 连接工厂
     * @return 连接工厂
     * @throws SQLException 如果创建PoolableConnectionFactory时发生错误
     */
    protected PoolableConnectionFactory createPoolableConnectionFactory(
            final ConnectionFactory driverConnectionFactory) throws SQLException {
        PoolableConnectionFactory connectionFactory = null;
        try {
            connectionFactory = new PoolableConnectionFactory(driverConnectionFactory, registeredJmxName);
            connectionFactory.setValidationQuery(validationQuery);
            connectionFactory.setValidationQueryTimeout(validationQueryTimeout);
            connectionFactory.setConnectionInitSql(connectionInitSqls);
            connectionFactory.setDefaultReadOnly(defaultReadOnly);
            connectionFactory.setDefaultAutoCommit(defaultAutoCommit);
            connectionFactory.setDefaultTransactionIsolation(defaultTransactionIsolation);
            connectionFactory.setDefaultCatalog(defaultCatalog);
            connectionFactory.setCacheState(cacheState);
            connectionFactory.setPoolStatements(poolPreparedStatements);
            connectionFactory.setMaxOpenPrepatedStatements(maxOpenPreparedStatements);
            connectionFactory.setMaxConnLifetimeMillis(maxConnLifetimeMillis);
            connectionFactory.setRollbackOnReturn(getRollbackOnReturn());
            connectionFactory.setEnableAutoCommitOnReturn(getEnableAutoCommitOnReturn());
            connectionFactory.setDefaultQueryTimeout(getDefaultQueryTimeout());
            connectionFactory.setFastFailValidation(fastFailValidation);
            connectionFactory.setDisconnectionSqlCodes(disconnectionSqlCodes);
            validateConnectionFactory(connectionFactory);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Cannot create PoolableConnectionFactory (" + e.getMessage() + ")", e);
        }
        return connectionFactory;
    }

    protected static void validateConnectionFactory(
            final PoolableConnectionFactory connectionFactory) throws Exception {
        PoolableConnection conn = null;
        PooledObject<PoolableConnection> p = null;
        try {
            p = connectionFactory.makeObject();
            conn = p.getObject();
            connectionFactory.activateObject(p);
            connectionFactory.validateConnection(conn);
            connectionFactory.passivateObject(p);
        }
        finally {
            if (p != null) {
                connectionFactory.destroyObject(p);
            }
        }
    }

    protected void log(final String message) {
        if (logWriter != null) {
            logWriter.println(message);
        }
    }

    /**
     * 此组件已注册的实际名称.
     */
    private ObjectName registeredJmxName = null;

    private void jmxRegister() {
        // 如果此DataSource已经注册，请立即返回
        if (registeredJmxName != null) {
            return;
        }
        // 如果未指定JMX名称，则立即返回
        final String requestedName = getJmxName();
        if (requestedName == null) {
            return;
        }
        ObjectName oname;
        try {
             oname = new ObjectName(requestedName);
        } catch (final MalformedObjectNameException e) {
            log.warn("The requested JMX name [" + requestedName +
                    "] was not valid and will be ignored.");
            return;
        }

        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            mbs.registerMBean(this, oname);
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException
                | NotCompliantMBeanException e) {
            log.warn("Failed to complete JMX registration", e);
        }
    }

    @Override
    public ObjectName preRegister(final MBeanServer server, final ObjectName name) {
        final String requestedName = getJmxName();
        if (requestedName != null) {
            try {
                registeredJmxName = new ObjectName(requestedName);
            } catch (final MalformedObjectNameException e) {
                log.warn("The requested JMX name [" + requestedName +
                        "] was not valid and will be ignored.");
            }
        }
        if (registeredJmxName == null) {
            registeredJmxName = name;
        }
        return registeredJmxName;
    }

    @Override
    public void postRegister(final Boolean registrationDone) {
        // NO-OP
    }

    @Override
    public void preDeregister() throws Exception {
        // NO-OP
    }

    @Override
    public void postDeregister() {
        // NO-OP
    }

    private void updateJmxName(final GenericObjectPoolConfig config) {
        if (registeredJmxName == null) {
            return;
        }
        final StringBuilder base = new StringBuilder(registeredJmxName.toString());
        base.append(Constants.JMX_CONNECTION_POOL_BASE_EXT);
        config.setJmxNameBase(base.toString());
        config.setJmxNamePrefix(Constants.JMX_CONNECTION_POOL_PREFIX);
    }

    protected ObjectName getRegisteredJmxName() {
        return registeredJmxName;
    }

    private class PaGetConnection implements PrivilegedExceptionAction<Connection> {

        @Override
        public Connection run() throws SQLException {
            return createDataSource().getConnection();
        }
    }
}
