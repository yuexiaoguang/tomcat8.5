package org.apache.tomcat.dbcp.dbcp2.cpdsadapter;

import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.dbcp2.PoolablePreparedStatement;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * <p>
 * JDBC驱动程序的适配器, 不包含{@link javax.sql.ConnectionPoolDataSource}的实现, 但仍然包含{@link java.sql.DriverManager}实现.
 * <code>ConnectionPoolDataSource</code>未在常规应用程序中使用.  它们由池化了<code>Connection</code>的<code>DataSource</code>实现使用,
 * 例如{@link org.apache.tomcat.dbcp.dbcp2.datasources.SharedPoolDataSource}.
 * J2EE容器通常会提供一些初始化<code>ConnectionPoolDataSource</code>的方法, 其属性表示为bean getter/setter, 然后通过JNDI部署它.
 * 然后它可用作数据库的物理连接源, 当池<code>DataSource</code>需要创建新的物理连接时.
 * </p>
 *
 * <p>
 * 虽然通常在JNDI环境中使用, DriverAdapterCPDS可以实例化并初始化为任何bean, 然后直接附加到池<code>DataSource</code>.
 * <code>Jdbc2PoolDataSource</code>可以使用<code>ConnectionPoolDataSource</code>, 使用或不使用JNDI.
 * </p>
 *
 * <p>
 * DriverAdapterCPDS还提供<code>PreparedStatement</code>池, 这在jbdc2 <code>ConnectionPoolDataSource</code>实现中通常不可用, 但是在jdbc3规范中解决了.
 * DriverAdapterCPDS中的<code>PreparedStatement</code>池已经在dbcp包中存在了一段时间, 但它没有在这里使用的配置中进行大量测试.
 * 它应该被认为是实验性的, 可以使用poolPreparedStatements属性进行切换.
 * </p>
 */
public class DriverAdapterCPDS
    implements ConnectionPoolDataSource, Referenceable, Serializable,
               ObjectFactory {

    private static final long serialVersionUID = -4820523787212147844L;


    private static final String GET_CONNECTION_CALLED
            = "A PooledConnection was already requested from this source, "
            + "further initialization is not allowed.";

    /** 描述 */
    private String description;
    /** 密码 */
    private String password;
    /** Url 名称 */
    private String url;
    /** 用户名 */
    private String user;
    /** 驱动程序类名 */
    private String driver;

    /** 登录超时时间, 以秒为单位 */
    private int loginTimeout;
    /** 日志流. NOT USED */
    private transient PrintWriter logWriter = null;

    // PreparedStatement 池属性
    private boolean poolPreparedStatements;
    private int maxIdle = 10;
    private long _timeBetweenEvictionRunsMillis =
            BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
    private int _numTestsPerEvictionRun = -1;
    private int _minEvictableIdleTimeMillis = -1;
    private int _maxPreparedStatements = -1;

    /** 是否已调用getConnection */
    private volatile boolean getConnectionCalled = false;

    /** 传递给JDBC驱动程序的连接属性 */
    private Properties connectionProperties = null;

    static {
        // 尝试防止死锁 - see DBCP - 272
        DriverManager.getDrivers();
    }

    /**
     * 控制对底层连接的访问
     */
    private boolean accessToUnderlyingConnectionAllowed = false;

    public DriverAdapterCPDS() {
    }

    /**
     * 尝试使用默认用户和密码建立数据库连接.
     */
    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return getPooledConnection(getUser(), getPassword());
    }

    /**
     * 尝试建立数据库连接.
     * @param username 用于连接的名称
     * @param pass 用于连接的密码
     */
    @Override
    public PooledConnection getPooledConnection(final String username, final String pass)
            throws SQLException {
        getConnectionCalled = true;
        PooledConnectionImpl pci = null;
        // 有缺陷的WebLogic 5.1类加载器的解决方法 - 首次调用时忽略该异常.
        try {
            if (connectionProperties != null) {
                connectionProperties.put("user", username);
                connectionProperties.put("password", pass);
                pci = new PooledConnectionImpl(DriverManager.getConnection(
                        getUrl(), connectionProperties));
            } else {
                pci = new PooledConnectionImpl(DriverManager.getConnection(
                        getUrl(), username, pass));
            }
            pci.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        }
        catch (final ClassCircularityError e)
        {
            if (connectionProperties != null) {
                pci = new PooledConnectionImpl(DriverManager.getConnection(
                        getUrl(), connectionProperties));
            } else {
                pci = new PooledConnectionImpl(DriverManager.getConnection(
                        getUrl(), username, pass));
            }
            pci.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        }
        KeyedObjectPool<PStmtKeyCPDS, PoolablePreparedStatement<PStmtKeyCPDS>> stmtPool = null;
        if (isPoolPreparedStatements()) {
            final GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
            config.setMaxTotalPerKey(Integer.MAX_VALUE);
            config.setBlockWhenExhausted(false);
            config.setMaxWaitMillis(0);
            config.setMaxIdlePerKey(getMaxIdle());
            if (getMaxPreparedStatements() <= 0)
            {
                // 因为没有限制, 使用逐出线程创建一个预处理的语句池
                //  evictor 设置与连接池设置相同.
                config.setTimeBetweenEvictionRunsMillis(getTimeBetweenEvictionRunsMillis());
                config.setNumTestsPerEvictionRun(getNumTestsPerEvictionRun());
                config.setMinEvictableIdleTimeMillis(getMinEvictableIdleTimeMillis());
            }
            else
            {
                // 因为没有限制, 创建一个没有逐出线程的预处理的语句池
                // 池具有LRU功能, 因此达到限制时, 池的15％被清除.
                // see org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool.clearOldest method
                config.setMaxTotal(getMaxPreparedStatements());
                config.setTimeBetweenEvictionRunsMillis(-1);
                config.setNumTestsPerEvictionRun(0);
                config.setMinEvictableIdleTimeMillis(0);
            }
            stmtPool = new GenericKeyedObjectPool<>(pci, config);
            pci.setStatementPool(stmtPool);
        }
        return pci;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    // ----------------------------------------------------------------------
    // Referenceable implementation

    /**
     * <CODE>Referenceable</CODE> 实现.
     */
    @Override
    public Reference getReference() throws NamingException {
        // 这个类实现了自己的工厂
        final String factory = getClass().getName();

        final Reference ref = new Reference(getClass().getName(), factory, null);

        ref.add(new StringRefAddr("description", getDescription()));
        ref.add(new StringRefAddr("driver", getDriver()));
        ref.add(new StringRefAddr("loginTimeout",
                                  String.valueOf(getLoginTimeout())));
        ref.add(new StringRefAddr("password", getPassword()));
        ref.add(new StringRefAddr("user", getUser()));
        ref.add(new StringRefAddr("url", getUrl()));

        ref.add(new StringRefAddr("poolPreparedStatements",
                                  String.valueOf(isPoolPreparedStatements())));
        ref.add(new StringRefAddr("maxIdle",
                                  String.valueOf(getMaxIdle())));
        ref.add(new StringRefAddr("timeBetweenEvictionRunsMillis",
            String.valueOf(getTimeBetweenEvictionRunsMillis())));
        ref.add(new StringRefAddr("numTestsPerEvictionRun",
            String.valueOf(getNumTestsPerEvictionRun())));
        ref.add(new StringRefAddr("minEvictableIdleTimeMillis",
            String.valueOf(getMinEvictableIdleTimeMillis())));
        ref.add(new StringRefAddr("maxPreparedStatements",
            String.valueOf(getMaxPreparedStatements())));

        return ref;
    }


    // ----------------------------------------------------------------------
    // ObjectFactory implementation

    /**
     * 实现ObjectFactory以创建此类的实例
     */
    @Override
    public Object getObjectInstance(final Object refObj, final Name name,
                                    final Context context, final Hashtable<?,?> env)
            throws Exception {
        // 规范说如果不能创建引用的实例，则返回null
        DriverAdapterCPDS cpds = null;
        if (refObj instanceof Reference) {
            final Reference ref = (Reference)refObj;
            if (ref.getClassName().equals(getClass().getName())) {
                RefAddr ra = ref.get("description");
                if (ra != null && ra.getContent() != null) {
                    setDescription(ra.getContent().toString());
                }

                ra = ref.get("driver");
                if (ra != null && ra.getContent() != null) {
                    setDriver(ra.getContent().toString());
                }
                ra = ref.get("url");
                if (ra != null && ra.getContent() != null) {
                    setUrl(ra.getContent().toString());
                }
                ra = ref.get("user");
                if (ra != null && ra.getContent() != null) {
                    setUser(ra.getContent().toString());
                }
                ra = ref.get("password");
                if (ra != null && ra.getContent() != null) {
                    setPassword(ra.getContent().toString());
                }

                ra = ref.get("poolPreparedStatements");
                if (ra != null && ra.getContent() != null) {
                    setPoolPreparedStatements(Boolean.valueOf(
                        ra.getContent().toString()).booleanValue());
                }
                ra = ref.get("maxIdle");
                if (ra != null && ra.getContent() != null) {
                    setMaxIdle(Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("timeBetweenEvictionRunsMillis");
                if (ra != null && ra.getContent() != null) {
                    setTimeBetweenEvictionRunsMillis(
                        Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("numTestsPerEvictionRun");
                if (ra != null && ra.getContent() != null) {
                    setNumTestsPerEvictionRun(
                        Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("minEvictableIdleTimeMillis");
                if (ra != null && ra.getContent() != null) {
                    setMinEvictableIdleTimeMillis(
                        Integer.parseInt(ra.getContent().toString()));
                }
                ra = ref.get("maxPreparedStatements");
                if (ra != null && ra.getContent() != null) {
                    setMaxPreparedStatements(
                        Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("accessToUnderlyingConnectionAllowed");
                if (ra != null && ra.getContent() != null) {
                    setAccessToUnderlyingConnectionAllowed(
                            Boolean.valueOf(ra.getContent().toString()).booleanValue());
                }

                cpds = this;
            }
        }
        return cpds;
    }

    /**
     * 抛出一个 IllegalStateException, 如果已经请求了PooledConnection.
     */
    private void assertInitializationAllowed() throws IllegalStateException {
        if (getConnectionCalled) {
            throw new IllegalStateException(GET_CONNECTION_CALLED);
        }
    }

    // ----------------------------------------------------------------------
    // Properties

    /**
     * 获取传递给JDBC驱动程序的连接属性.
     */
    public Properties getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * <p>设置传递给JDBC驱动程序的连接属性.</p>
     *
     * <p>如果 <code>props</code> 包含 "user" 和 "password"属性, 设置相应的实例属性.
     * 如果这些属性不存在, 使用{@link #getUser()}, {@link #getPassword()}填充它们, 当调用 {@link #getPooledConnection()}时,
     * 或者在调用{@link #getPooledConnection(String，String)}时使用实际参数进行方法调用.
     * 调用 {@link #setUser(String)} 或 {@link #setPassword(String)} 重写这些属性的值, 如果<code>connectionProperties</code>不是 null.</p>
     *
     * @param props 创建新连接时要使用的连接属性.
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setConnectionProperties(final Properties props) {
        assertInitializationAllowed();
        connectionProperties = props;
        if (connectionProperties.containsKey("user")) {
            setUser(connectionProperties.getProperty("user"));
        }
        if (connectionProperties.containsKey("password")) {
            setPassword(connectionProperties.getProperty("password"));
        }
    }

    /**
     * 此属性在此处供将部署此数据源的代码使用.  不是内部使用的.
     *
     * @return 描述, 可能是 null.
     */
    public String getDescription() {
        return description;
    }

    /**
     * 此属性在此处供将部署此数据源的代码使用.  不是内部使用的.
     *
     * @param v 
     */
    public void setDescription(final String  v) {
        this.description = v;
    }

    /**
     * 获取默认用户的密码.
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置默认用户的密码.
     * @param v 
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setPassword(final String v) {
        assertInitializationAllowed();
        this.password = v;
        if (connectionProperties != null) {
            connectionProperties.setProperty("password", v);
        }
    }

    /**
     * 获取用于查找此数据源的数据库的url的值.
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置用于查找此数据源的数据库的URL字符串的值.
     * @param v
     * 
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
    */
    public void setUrl(final String v) {
        assertInitializationAllowed();
        this.url = v;
    }

    /**
     * 获取默认的用户 (login 或 username).
     */
    public String getUser() {
        return user;
    }

    /**
     * 设置默认的用户 (login 或 username).
     * @param v 
     * 
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setUser(final String v) {
        assertInitializationAllowed();
        this.user = v;
        if (connectionProperties != null) {
            connectionProperties.setProperty("user", v);
        }
    }

    /**
     * 获取驱动程序类名.
     */
    public String getDriver() {
        return driver;
    }

    /**
     * 设置驱动程序类名. 设置驱动程序类名会导致驱动程序使用DriverManager注册.
     * @param v 
     * 
     * @throws ClassNotFoundException 找不到驱动程序类
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setDriver(final String v) throws ClassNotFoundException {
        assertInitializationAllowed();
        this.driver = v;
        // 确保驱动程序已注册
        Class.forName(v);
    }

    /**
     * 获取此数据源在尝试连接到数据库时可以等待的最长时间（以秒为单位）. NOT USED.
     */
    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    /**
     * 获取此数据源的日志writer. NOT USED.
     */
    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    /**
     * 设置此数据源在尝试连接到数据库时将等待的最长时间（以秒为单位）. NOT USED.
     */
    @Override
    public void setLoginTimeout(final int seconds) {
        loginTimeout = seconds;
    }

    /**
     * 设置此数据源的日志 writer. NOT USED.
     */
    @Override
    public void setLogWriter(final PrintWriter out) {
        logWriter = out;
    }


    // ------------------------------------------------------------------
    // PreparedStatement pool properties


    /**
     * 用于切换<code>PreparedStatement</code>的池的标志
     */
    public boolean isPoolPreparedStatements() {
        return poolPreparedStatements;
    }

    /**
     * 用于切换<code>PreparedStatement</code>的池的标志
     * @param v  true 到池语句.
     * 
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setPoolPreparedStatements(final boolean v) {
        assertInitializationAllowed();
        this.poolPreparedStatements = v;
    }

    /**
     * 获取池中可以保持空闲的最大语句数, 不包括额外的被释放的; 或负值无限制.
     */
    public int getMaxIdle() {
        return this.maxIdle;
    }

    /**
     * 设置池中可以保持空闲的最大语句数, 不包括额外的被释放的; 或负值无限制.
     *
     * @param maxIdle 可以保持空闲的最大语句数
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setMaxIdle(final int maxIdle) {
        assertInitializationAllowed();
        this.maxIdle = maxIdle;
    }

    /**
     * 获取在空闲对象 evictor 线程的运行之间休眠的毫秒数.
     * 当非正值时, 不会运行空闲对象 evictor 线程.
     */
    public long getTimeBetweenEvictionRunsMillis() {
        return _timeBetweenEvictionRunsMillis;
    }

    /**
     * 设置在空闲对象 evictor 线程的运行之间休眠的毫秒数.
     * 当非正值时, 不会运行空闲对象 evictor 线程.
     * 
     * @param timeBetweenEvictionRunsMillis 时间间隔
     * 
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setTimeBetweenEvictionRunsMillis(
            final long timeBetweenEvictionRunsMillis) {
        assertInitializationAllowed();
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    /**
     * 获取在每次运行空闲对象 evictor 线程期间要检查的语句数.
     */
    public int getNumTestsPerEvictionRun() {
        return _numTestsPerEvictionRun;
    }

    /**
     * 设置在每次运行空闲对象 evictor 线程期间要检查的语句数.
     * <p>
     * 当提供负值时, 将运行<tt>ceil({*link #numIdle})/abs({*link #getNumTestsPerEvictionRun})</tt>.
     * I.e., 当值是<i>-n</i>时, 每次运行将测试大约一个空闲对象.
     *
     * @param numTestsPerEvictionRun 每次运行要检查的语句数
     * 
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        assertInitializationAllowed();
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * 获取语句在符合空闲对象 evictor 驱逐条件之前可能在池中处于空闲状态的最短时间.
     */
    public int getMinEvictableIdleTimeMillis() {
        return _minEvictableIdleTimeMillis;
    }

    /**
     * 设置语句在符合空闲对象 evictor 驱逐条件之前可能在池中处于空闲状态的最短时间.
     * 当非正数时, 由于只有空闲时间, 不会从池中驱逐任何对象.
     * 
     * @param minEvictableIdleTimeMillis 最短时间 (ms)
     * 
     * @throws IllegalStateException 如果已经调用 {@link #getPooledConnection()}
     */
    public void setMinEvictableIdleTimeMillis(final int minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * 返回accessToUnderlyingConnectionAllowed属性的值.
     *
     * @return <code>true</code> 如果允许访问底层连接, 否则<code>false</code>.
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

    /**
     * 获取预处理语句的最大数量.
     */
    public int getMaxPreparedStatements()
    {
        return _maxPreparedStatements;
    }

    /**
     * 设置预处理语句的最大数量.
     * @param maxPreparedStatements 最大数量
     */
    public void setMaxPreparedStatements(final int maxPreparedStatements)
    {
        _maxPreparedStatements = maxPreparedStatements;
    }
}
