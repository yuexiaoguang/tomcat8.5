package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionPoolDataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.dbcp2.SwallowedExceptionLogger;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

/**
 * <p>适合在J2EE环境中部署的池<code>DataSource</code>. 有许多配置选项, 其中大部分是在父类中定义的.
 * 此数据源使用每个用户的单个池, 并且可以为给定用户专门设置一些属性, 如果部署环境可以支持映射属性的初始化.
 * 所以举个例子, 一个admin或write-access连接池可以保证一定数量的连接, 与具有只读连接的用户的最大集合分开.</p>
 *
 * <p>无需重新初始化数据源即可更改用户密码.
 * 当<code>getConnection(username, password)</code>请求的密码不同于<code>username</code>关联的池中创建连接的密码,
 * 尝试使用提供的密码创建新连接, 如果成功, 使用旧密码创建的空闲连接将被销毁, 并使用新密码创建新连接.</p>
 */
public class PerUserPoolDataSource extends InstanceKeyDataSource {

    private static final long serialVersionUID = 7872747993848065028L;

    private static final Log log =
            LogFactory.getLog(PerUserPoolDataSource.class);

    // 用户池属性
    private Map<String,Boolean> perUserBlockWhenExhausted = null;
    private Map<String,String> perUserEvictionPolicyClassName = null;
    private Map<String,Boolean> perUserLifo = null;
    private Map<String,Integer> perUserMaxIdle = null;
    private Map<String,Integer> perUserMaxTotal = null;
    private Map<String,Long> perUserMaxWaitMillis = null;
    private Map<String,Long> perUserMinEvictableIdleTimeMillis = null;
    private Map<String,Integer> perUserMinIdle = null;
    private Map<String,Integer> perUserNumTestsPerEvictionRun = null;
    private Map<String,Long> perUserSoftMinEvictableIdleTimeMillis = null;
    private Map<String,Boolean> perUserTestOnCreate = null;
    private Map<String,Boolean> perUserTestOnBorrow = null;
    private Map<String,Boolean> perUserTestOnReturn = null;
    private Map<String,Boolean> perUserTestWhileIdle = null;
    private Map<String,Long> perUserTimeBetweenEvictionRunsMillis = null;

    // 连接属性
    private Map<String,Boolean> perUserDefaultAutoCommit = null;
    private Map<String,Integer> perUserDefaultTransactionIsolation = null;
    private Map<String,Boolean> perUserDefaultReadOnly = null;

    /**
     * 跟踪给定用户的池
     */
    private transient Map<PoolKey, PooledConnectionManager> managers =
            new HashMap<>();

    public PerUserPoolDataSource() {
    }

    /**
     * 由此数据源维护的关闭池.
     */
    @Override
    public void close() {
        for (final PooledConnectionManager manager : managers.values()) {
            try {
              ((CPDSConnectionFactory) manager).getPool().close();
            } catch (final Exception closePoolException) {
                    //ignore and try to close others.
            }
        }
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }

    // -------------------------------------------------------------------
    // Properties

    /**
     * 获取指定用户池的{@link GenericObjectPool#getBlockWhenExhausted()}的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return <code>true</code>阻塞
     */
    public boolean getPerUserBlockWhenExhausted(final String key) {
        Boolean value = null;
        if (perUserBlockWhenExhausted != null) {
            value = perUserBlockWhenExhausted.get(key);
        }
        if (value == null) {
            return getDefaultBlockWhenExhausted();
        }
        return value.booleanValue();
    }

    /**
     * 设置指定用户池的{@link GenericObjectPool#getBlockWhenExhausted()}的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserBlockWhenExhausted(final String username,
            final Boolean value) {
        assertInitializationAllowed();
        if (perUserBlockWhenExhausted == null) {
            perUserBlockWhenExhausted = new HashMap<>();
        }
        perUserBlockWhenExhausted.put(username, value);
    }

    void setPerUserBlockWhenExhausted(
            final Map<String,Boolean> userDefaultBlockWhenExhausted) {
        assertInitializationAllowed();
        if (perUserBlockWhenExhausted == null) {
            perUserBlockWhenExhausted = new HashMap<>();
        } else {
            perUserBlockWhenExhausted.clear();
        }
        perUserBlockWhenExhausted.putAll(userDefaultBlockWhenExhausted);
    }


    /**
     * 获取指定用户池的{@link GenericObjectPool#getEvictionPolicyClassName()}的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 策略类名
     */
    public String getPerUserEvictionPolicyClassName(final String key) {
        String value = null;
        if (perUserEvictionPolicyClassName != null) {
            value = perUserEvictionPolicyClassName.get(key);
        }
        if (value == null) {
            return getDefaultEvictionPolicyClassName();
        }
        return value;
    }

    /**
     * 设置指定用户池的{@link GenericObjectPool#getEvictionPolicyClassName()}的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserEvictionPolicyClassName(final String username,
            final String value) {
        assertInitializationAllowed();
        if (perUserEvictionPolicyClassName == null) {
            perUserEvictionPolicyClassName = new HashMap<>();
        }
        perUserEvictionPolicyClassName.put(username, value);
    }

    void setPerUserEvictionPolicyClassName(
            final Map<String,String> userDefaultEvictionPolicyClassName) {
        assertInitializationAllowed();
        if (perUserEvictionPolicyClassName == null) {
            perUserEvictionPolicyClassName = new HashMap<>();
        } else {
            perUserEvictionPolicyClassName.clear();
        }
        perUserEvictionPolicyClassName.putAll(userDefaultEvictionPolicyClassName);
    }


    /**
     * 获取指定用户池的{@link GenericObjectPool#getLifo()}的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return <code>true</code> to use LIFO
     */
    public boolean getPerUserLifo(final String key) {
        Boolean value = null;
        if (perUserLifo != null) {
            value = perUserLifo.get(key);
        }
        if (value == null) {
            return getDefaultLifo();
        }
        return value.booleanValue();
    }

    /**
     * 设置指定用户池的{@link GenericObjectPool#getLifo()}的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserLifo(final String username, final Boolean value) {
        assertInitializationAllowed();
        if (perUserLifo == null) {
            perUserLifo = new HashMap<>();
        }
        perUserLifo.put(username, value);
    }

    void setPerUserLifo(final Map<String,Boolean> userDefaultLifo) {
        assertInitializationAllowed();
        if (perUserLifo == null) {
            perUserLifo = new HashMap<>();
        } else {
            perUserLifo.clear();
        }
        perUserLifo.putAll(userDefaultLifo);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getMaxIdle()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 最大空闲
     */
    public int getPerUserMaxIdle(final String key) {
        Integer value = null;
        if (perUserMaxIdle != null) {
            value = perUserMaxIdle.get(key);
        }
        if (value == null) {
            return getDefaultMaxIdle();
        }
        return value.intValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getMaxIdle()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserMaxIdle(final String username, final Integer value) {
        assertInitializationAllowed();
        if (perUserMaxIdle == null) {
            perUserMaxIdle = new HashMap<>();
        }
        perUserMaxIdle.put(username, value);
    }

    void setPerUserMaxIdle(final Map<String,Integer> userDefaultMaxIdle) {
        assertInitializationAllowed();
        if (perUserMaxIdle == null) {
            perUserMaxIdle = new HashMap<>();
        } else {
            perUserMaxIdle.clear();
        }
        perUserMaxIdle.putAll(userDefaultMaxIdle);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getMaxTotal()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 最大总数
     */
    public int getPerUserMaxTotal(final String key) {
        Integer value = null;
        if (perUserMaxTotal != null) {
            value = perUserMaxTotal.get(key);
        }
        if (value == null) {
            return getDefaultMaxTotal();
        }
        return value.intValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getMaxTotal()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserMaxTotal(final String username, final Integer value) {
        assertInitializationAllowed();
        if (perUserMaxTotal == null) {
            perUserMaxTotal = new HashMap<>();
        }
        perUserMaxTotal.put(username, value);
    }

    void setPerUserMaxTotal(final Map<String,Integer> userDefaultMaxTotal) {
        assertInitializationAllowed();
        if (perUserMaxTotal == null) {
            perUserMaxTotal = new HashMap<>();
        } else {
            perUserMaxTotal.clear();
        }
        perUserMaxTotal.putAll(userDefaultMaxTotal);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getMaxWaitMillis()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 最长等待时间
     */
    public long getPerUserMaxWaitMillis(final String key) {
        Long value = null;
        if (perUserMaxWaitMillis != null) {
            value = perUserMaxWaitMillis.get(key);
        }
        if (value == null) {
            return getDefaultMaxWaitMillis();
        }
        return value.longValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getMaxWaitMillis()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserMaxWaitMillis(final String username, final Long value) {
        assertInitializationAllowed();
        if (perUserMaxWaitMillis == null) {
            perUserMaxWaitMillis = new HashMap<>();
        }
        perUserMaxWaitMillis.put(username, value);
    }

    void setPerUserMaxWaitMillis(
            final Map<String,Long> userDefaultMaxWaitMillis) {
        assertInitializationAllowed();
        if (perUserMaxWaitMillis == null) {
            perUserMaxWaitMillis = new HashMap<>();
        } else {
            perUserMaxWaitMillis.clear();
        }
        perUserMaxWaitMillis.putAll(userDefaultMaxWaitMillis);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getMinEvictableIdleTimeMillis()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 驱逐的最小空闲时间
     */
    public long getPerUserMinEvictableIdleTimeMillis(final String key) {
        Long value = null;
        if (perUserMinEvictableIdleTimeMillis != null) {
            value = perUserMinEvictableIdleTimeMillis.get(key);
        }
        if (value == null) {
            return getDefaultMinEvictableIdleTimeMillis();
        }
        return value.longValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getMinEvictableIdleTimeMillis()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserMinEvictableIdleTimeMillis(final String username,
            final Long value) {
        assertInitializationAllowed();
        if (perUserMinEvictableIdleTimeMillis == null) {
            perUserMinEvictableIdleTimeMillis = new HashMap<>();
        }
        perUserMinEvictableIdleTimeMillis.put(username, value);
    }

    void setPerUserMinEvictableIdleTimeMillis(
            final Map<String,Long> userDefaultMinEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        if (perUserMinEvictableIdleTimeMillis == null) {
            perUserMinEvictableIdleTimeMillis = new HashMap<>();
        } else {
            perUserMinEvictableIdleTimeMillis.clear();
        }
        perUserMinEvictableIdleTimeMillis.putAll(
                userDefaultMinEvictableIdleTimeMillis);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getMinIdle()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 最小空闲计数
     */
    public int getPerUserMinIdle(final String key) {
        Integer value = null;
        if (perUserMinIdle != null) {
            value = perUserMinIdle.get(key);
        }
        if (value == null) {
            return getDefaultMinIdle();
        }
        return value.intValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getMinIdle()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserMinIdle(final String username, final Integer value) {
        assertInitializationAllowed();
        if (perUserMinIdle == null) {
            perUserMinIdle = new HashMap<>();
        }
        perUserMinIdle.put(username, value);
    }

    void setPerUserMinIdle(final Map<String,Integer> userDefaultMinIdle) {
        assertInitializationAllowed();
        if (perUserMinIdle == null) {
            perUserMinIdle = new HashMap<>();
        } else {
            perUserMinIdle.clear();
        }
        perUserMinIdle.putAll(userDefaultMinIdle);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getNumTestsPerEvictionRun()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return the tests count
     */
    public int getPerUserNumTestsPerEvictionRun(final String key) {
        Integer value = null;
        if (perUserNumTestsPerEvictionRun != null) {
            value = perUserNumTestsPerEvictionRun.get(key);
        }
        if (value == null) {
            return getDefaultNumTestsPerEvictionRun();
        }
        return value.intValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getNumTestsPerEvictionRun()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserNumTestsPerEvictionRun(final String username,
            final Integer value) {
        assertInitializationAllowed();
        if (perUserNumTestsPerEvictionRun == null) {
            perUserNumTestsPerEvictionRun = new HashMap<>();
        }
        perUserNumTestsPerEvictionRun.put(username, value);
    }

    void setPerUserNumTestsPerEvictionRun(
            final Map<String,Integer> userDefaultNumTestsPerEvictionRun) {
        assertInitializationAllowed();
        if (perUserNumTestsPerEvictionRun == null) {
            perUserNumTestsPerEvictionRun = new HashMap<>();
        } else {
            perUserNumTestsPerEvictionRun.clear();
        }
        perUserNumTestsPerEvictionRun.putAll(userDefaultNumTestsPerEvictionRun);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 驱逐的软最小空闲时间
     */
    public long getPerUserSoftMinEvictableIdleTimeMillis(final String key) {
        Long value = null;
        if (perUserSoftMinEvictableIdleTimeMillis != null) {
            value = perUserSoftMinEvictableIdleTimeMillis.get(key);
        }
        if (value == null) {
            return getDefaultSoftMinEvictableIdleTimeMillis();
        }
        return value.longValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserSoftMinEvictableIdleTimeMillis(final String username,
            final Long value) {
        assertInitializationAllowed();
        if (perUserSoftMinEvictableIdleTimeMillis == null) {
            perUserSoftMinEvictableIdleTimeMillis = new HashMap<>();
        }
        perUserSoftMinEvictableIdleTimeMillis.put(username, value);
    }

    void setPerUserSoftMinEvictableIdleTimeMillis(
            final Map<String,Long> userDefaultSoftMinEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        if (perUserSoftMinEvictableIdleTimeMillis == null) {
            perUserSoftMinEvictableIdleTimeMillis = new HashMap<>();
        } else {
            perUserSoftMinEvictableIdleTimeMillis.clear();
        }
        perUserSoftMinEvictableIdleTimeMillis.putAll(userDefaultSoftMinEvictableIdleTimeMillis);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getTestOnCreate()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return <code>true</code> to test on create
     */
    public boolean getPerUserTestOnCreate(final String key) {
        Boolean value = null;
        if (perUserTestOnCreate != null) {
            value = perUserTestOnCreate.get(key);
        }
        if (value == null) {
            return getDefaultTestOnCreate();
        }
        return value.booleanValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getTestOnCreate()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserTestOnCreate(final String username, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnCreate == null) {
            perUserTestOnCreate = new HashMap<>();
        }
        perUserTestOnCreate.put(username, value);
    }

    void setPerUserTestOnCreate(final Map<String,Boolean> userDefaultTestOnCreate) {
        assertInitializationAllowed();
        if (perUserTestOnCreate == null) {
            perUserTestOnCreate = new HashMap<>();
        } else {
            perUserTestOnCreate.clear();
        }
        perUserTestOnCreate.putAll(userDefaultTestOnCreate);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getTestOnBorrow()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return <code>true</code> to test on borrow
     */
    public boolean getPerUserTestOnBorrow(final String key) {
        Boolean value = null;
        if (perUserTestOnBorrow != null) {
            value = perUserTestOnBorrow.get(key);
        }
        if (value == null) {
            return getDefaultTestOnBorrow();
        }
        return value.booleanValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getTestOnBorrow()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserTestOnBorrow(final String username, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnBorrow == null) {
            perUserTestOnBorrow = new HashMap<>();
        }
        perUserTestOnBorrow.put(username, value);
    }

    void setPerUserTestOnBorrow(final Map<String,Boolean> userDefaultTestOnBorrow) {
        assertInitializationAllowed();
        if (perUserTestOnBorrow == null) {
            perUserTestOnBorrow = new HashMap<>();
        } else {
            perUserTestOnBorrow.clear();
        }
        perUserTestOnBorrow.putAll(userDefaultTestOnBorrow);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getTestOnReturn()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return <code>true</code> to test on return
     */
    public boolean getPerUserTestOnReturn(final String key) {
        Boolean value = null;
        if (perUserTestOnReturn != null) {
            value = perUserTestOnReturn.get(key);
        }
        if (value == null) {
            return getDefaultTestOnReturn();
        }
        return value.booleanValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getTestOnReturn()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserTestOnReturn(final String username, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnReturn == null) {
            perUserTestOnReturn = new HashMap<>();
        }
        perUserTestOnReturn.put(username, value);
    }

    void setPerUserTestOnReturn(
            final Map<String,Boolean> userDefaultTestOnReturn) {
        assertInitializationAllowed();
        if (perUserTestOnReturn == null) {
            perUserTestOnReturn = new HashMap<>();
        } else {
            perUserTestOnReturn.clear();
        }
        perUserTestOnReturn.putAll(userDefaultTestOnReturn);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getTestWhileIdle()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return <code>true</code> to test while idle
     */
    public boolean getPerUserTestWhileIdle(final String key) {
        Boolean value = null;
        if (perUserTestWhileIdle != null) {
            value = perUserTestWhileIdle.get(key);
        }
        if (value == null) {
            return getDefaultTestWhileIdle();
        }
        return value.booleanValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getTestWhileIdle()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserTestWhileIdle(final String username, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestWhileIdle == null) {
            perUserTestWhileIdle = new HashMap<>();
        }
        perUserTestWhileIdle.put(username, value);
    }

    void setPerUserTestWhileIdle(
            final Map<String,Boolean> userDefaultTestWhileIdle) {
        assertInitializationAllowed();
        if (perUserTestWhileIdle == null) {
            perUserTestWhileIdle = new HashMap<>();
        } else {
            perUserTestWhileIdle.clear();
        }
        perUserTestWhileIdle.putAll(userDefaultTestWhileIdle);
    }


    /**
     * 获取指定用户池的 {@link GenericObjectPool#getTimeBetweenEvictionRunsMillis()} 的用户特定值; 如果未定义用户特定值, 则为默认值.
     * 
     * @param key The user
     * @return 逐出运行的间隔时间
     */
    public long getPerUserTimeBetweenEvictionRunsMillis(final String key) {
        Long value = null;
        if (perUserTimeBetweenEvictionRunsMillis != null) {
            value = perUserTimeBetweenEvictionRunsMillis.get(key);
        }
        if (value == null) {
            return getDefaultTimeBetweenEvictionRunsMillis();
        }
        return value.longValue();
    }

    /**
     * 设置指定用户池的 {@link GenericObjectPool#getTimeBetweenEvictionRunsMillis ()} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserTimeBetweenEvictionRunsMillis(final String username,
            final Long value) {
        assertInitializationAllowed();
        if (perUserTimeBetweenEvictionRunsMillis == null) {
            perUserTimeBetweenEvictionRunsMillis = new HashMap<>();
        }
        perUserTimeBetweenEvictionRunsMillis.put(username, value);
    }

    void setPerUserTimeBetweenEvictionRunsMillis(
            final Map<String,Long> userDefaultTimeBetweenEvictionRunsMillis ) {
        assertInitializationAllowed();
        if (perUserTimeBetweenEvictionRunsMillis == null) {
            perUserTimeBetweenEvictionRunsMillis = new HashMap<>();
        } else {
            perUserTimeBetweenEvictionRunsMillis.clear();
        }
        perUserTimeBetweenEvictionRunsMillis.putAll(
                userDefaultTimeBetweenEvictionRunsMillis );
    }


    /**
     * 获取指定用户池的 {@link Connection#setAutoCommit(boolean)} 的用户特定值.
     * 
     * @param key The user
     * @return <code>true</code> 自动提交
     */
    public Boolean getPerUserDefaultAutoCommit(final String key) {
        Boolean value = null;
        if (perUserDefaultAutoCommit != null) {
            value = perUserDefaultAutoCommit.get(key);
        }
        return value;
    }

    /**
     * 设置指定用户池的 {@link Connection#setAutoCommit(boolean)} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserDefaultAutoCommit(final String username, final Boolean value) {
        assertInitializationAllowed();
        if (perUserDefaultAutoCommit == null) {
            perUserDefaultAutoCommit = new HashMap<>();
        }
        perUserDefaultAutoCommit.put(username, value);
    }

    void setPerUserDefaultAutoCommit(final Map<String,Boolean> userDefaultAutoCommit) {
        assertInitializationAllowed();
        if (perUserDefaultAutoCommit == null) {
            perUserDefaultAutoCommit = new HashMap<>();
        } else {
            perUserDefaultAutoCommit.clear();
        }
        perUserDefaultAutoCommit.putAll(userDefaultAutoCommit);
    }


    /**
     * 获取指定用户池的 {@link Connection#setReadOnly(boolean)} 的用户特定值.
     * 
     * @param key The user
     * @return <code>true</code> 默认情况下是只读的
     */
    public Boolean getPerUserDefaultReadOnly(final String key) {
        Boolean value = null;
        if (perUserDefaultReadOnly != null) {
            value = perUserDefaultReadOnly.get(key);
        }
        return value;
    }

    /**
     * 设置指定用户池的 {@link Connection#setReadOnly(boolean)} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserDefaultReadOnly(final String username, final Boolean value) {
        assertInitializationAllowed();
        if (perUserDefaultReadOnly == null) {
            perUserDefaultReadOnly = new HashMap<>();
        }
        perUserDefaultReadOnly.put(username, value);
    }

    void setPerUserDefaultReadOnly(final Map<String,Boolean> userDefaultReadOnly) {
        assertInitializationAllowed();
        if (perUserDefaultReadOnly == null) {
            perUserDefaultReadOnly = new HashMap<>();
        } else {
            perUserDefaultReadOnly.clear();
        }
        perUserDefaultReadOnly.putAll(userDefaultReadOnly);
    }


    /**
     * 获取指定用户池的 {@link Connection#setTransactionIsolation(int)} 的用户特定值.
     * 
     * @param key The user
     * @return 默认事务隔离
     */
    public Integer getPerUserDefaultTransactionIsolation(final String key) {
        Integer value = null;
        if (perUserDefaultTransactionIsolation != null) {
            value = perUserDefaultTransactionIsolation.get(key);
        }
        return value;
    }

    /**
     * 设置指定用户池的 {@link Connection#setTransactionIsolation(int)} 的用户特定值.
     * 
     * @param username The user
     * @param value The value
     */
    public void setPerUserDefaultTransactionIsolation(final String username,
            final Integer value) {
        assertInitializationAllowed();
        if (perUserDefaultTransactionIsolation == null) {
            perUserDefaultTransactionIsolation = new HashMap<>();
        }
        perUserDefaultTransactionIsolation.put(username, value);
    }

    void setPerUserDefaultTransactionIsolation(
            final Map<String,Integer> userDefaultTransactionIsolation) {
        assertInitializationAllowed();
        if (perUserDefaultTransactionIsolation == null) {
            perUserDefaultTransactionIsolation = new HashMap<>();
        } else {
            perUserDefaultTransactionIsolation.clear();
        }
        perUserDefaultTransactionIsolation.putAll(userDefaultTransactionIsolation);
    }


    // ----------------------------------------------------------------------
    // Instrumentation Methods

    /**
     * @return 默认池中的活动连接数.
     */
    public int getNumActive() {
        return getNumActive(null);
    }

    /**
     * @param username The user
     * @return 给定用户的池中活动连接数.
     */
    public int getNumActive(final String username) {
        final ObjectPool<PooledConnectionAndInfo> pool =
            getPool(getPoolKey(username));
        return pool == null ? 0 : pool.getNumActive();
    }

    /**
     * @return 默认池中的空闲连接数.
     */
    public int getNumIdle() {
        return getNumIdle(null);
    }

    /**
     * @param username The user
     * @return 给定用户池中的空闲连接数.
     */
    public int getNumIdle(final String username) {
        final ObjectPool<PooledConnectionAndInfo> pool =
            getPool(getPoolKey(username));
        return pool == null ? 0 : pool.getNumIdle();
    }


    // ----------------------------------------------------------------------
    // Inherited abstract methods

    @Override
    protected PooledConnectionAndInfo
        getPooledConnectionAndInfo(final String username, final String password)
        throws SQLException {

        final PoolKey key = getPoolKey(username);
        ObjectPool<PooledConnectionAndInfo> pool;
        PooledConnectionManager manager;
        synchronized(this) {
            manager = managers.get(key);
            if (manager == null) {
                try {
                    registerPool(username, password);
                    manager = managers.get(key);
                } catch (final NamingException e) {
                    throw new SQLException("RegisterPool failed", e);
                }
            }
            pool = ((CPDSConnectionFactory) manager).getPool();
        }

        PooledConnectionAndInfo info = null;
        try {
            info = pool.borrowObject();
        }
        catch (final NoSuchElementException ex) {
            throw new SQLException(
                    "Could not retrieve connection info from pool", ex);
        }
        catch (final Exception e) {
            // 查看是否由于CPDSConnectionFactory身份验证失败而导致失败
            try {
                testCPDS(username, password);
            } catch (final Exception ex) {
                throw new SQLException(
                        "Could not retrieve connection info from pool", ex);
            }
            // 新密码有效, 因此销毁旧的池, 创建一个新的, 并借用
            manager.closePool(username);
            synchronized (this) {
                managers.remove(key);
            }
            try {
                registerPool(username, password);
                pool = getPool(key);
            } catch (final NamingException ne) {
                throw new SQLException("RegisterPool failed", ne);
            }
            try {
                info = pool.borrowObject();
            } catch (final Exception ex) {
                throw new SQLException(
                        "Could not retrieve connection info from pool", ex);
            }
        }
        return info;
    }

    @Override
    protected void setupDefaults(final Connection con, final String username)
        throws SQLException {
        Boolean defaultAutoCommit = isDefaultAutoCommit();
        if (username != null) {
            final Boolean userMax = getPerUserDefaultAutoCommit(username);
            if (userMax != null) {
                defaultAutoCommit = userMax;
            }
        }

        Boolean defaultReadOnly = isDefaultReadOnly();
        if (username != null) {
            final Boolean userMax = getPerUserDefaultReadOnly(username);
            if (userMax != null) {
                defaultReadOnly = userMax;
            }
        }

        int defaultTransactionIsolation = getDefaultTransactionIsolation();
        if (username != null) {
            final Integer userMax = getPerUserDefaultTransactionIsolation(username);
            if (userMax != null) {
                defaultTransactionIsolation = userMax.intValue();
            }
        }

        if (defaultAutoCommit != null &&
                con.getAutoCommit() != defaultAutoCommit.booleanValue()) {
            con.setAutoCommit(defaultAutoCommit.booleanValue());
        }

        if (defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION) {
            con.setTransactionIsolation(defaultTransactionIsolation);
        }

        if (defaultReadOnly != null &&
                con.isReadOnly() != defaultReadOnly.booleanValue()) {
            con.setReadOnly(defaultReadOnly.booleanValue());
        }
    }

    @Override
    protected PooledConnectionManager getConnectionManager(final UserPassKey upkey) {
        return managers.get(getPoolKey(upkey.getUsername()));
    }

    /**
     * @return a <code>PerUserPoolDataSource</code> {@link Reference}.
     * @throws NamingException 不应该发生
     */
    @Override
    public Reference getReference() throws NamingException {
        final Reference ref = new Reference(getClass().getName(),
                PerUserPoolDataSourceFactory.class.getName(), null);
        ref.add(new StringRefAddr("instanceKey", getInstanceKey()));
        return ref;
    }

    /**
     * 从提供的参数创建池Key.
     *
     * @param username  User name
     * @return the pool key
     */
    private PoolKey getPoolKey(final String username) {
        return new PoolKey(getDataSourceName(), username);
    }

    private synchronized void registerPool(final String username, final String password)
            throws NamingException, SQLException {

        final ConnectionPoolDataSource cpds = testCPDS(username, password);

        // 设置我们将使用的工厂 (通过池将工厂与池关联, 所以不必显式地这样做)
        final CPDSConnectionFactory factory = new CPDSConnectionFactory(cpds,
                getValidationQuery(), getValidationQueryTimeout(),
                isRollbackAfterValidation(), username, password);
        factory.setMaxConnLifetimeMillis(getMaxConnLifetimeMillis());

        // 创建一个对象池以包含PooledConnections
        final GenericObjectPool<PooledConnectionAndInfo> pool =
                new GenericObjectPool<>(factory);
        factory.setPool(pool);
        pool.setBlockWhenExhausted(getPerUserBlockWhenExhausted(username));
        pool.setEvictionPolicyClassName(
                getPerUserEvictionPolicyClassName(username));
        pool.setLifo(getPerUserLifo(username));
        pool.setMaxIdle(getPerUserMaxIdle(username));
        pool.setMaxTotal(getPerUserMaxTotal(username));
        pool.setMaxWaitMillis(getPerUserMaxWaitMillis(username));
        pool.setMinEvictableIdleTimeMillis(
                getPerUserMinEvictableIdleTimeMillis(username));
        pool.setMinIdle(getPerUserMinIdle(username));
        pool.setNumTestsPerEvictionRun(
                getPerUserNumTestsPerEvictionRun(username));
        pool.setSoftMinEvictableIdleTimeMillis(
                getPerUserSoftMinEvictableIdleTimeMillis(username));
        pool.setTestOnCreate(getPerUserTestOnCreate(username));
        pool.setTestOnBorrow(getPerUserTestOnBorrow(username));
        pool.setTestOnReturn(getPerUserTestOnReturn(username));
        pool.setTestWhileIdle(getPerUserTestWhileIdle(username));
        pool.setTimeBetweenEvictionRunsMillis(
                getPerUserTimeBetweenEvictionRunsMillis(username));

        pool.setSwallowedExceptionListener(new SwallowedExceptionLogger(log));

        final Object old = managers.put(getPoolKey(username), factory);
        if (old != null) {
            throw new IllegalStateException("Pool already contains an entry for this user/password: " + username);
        }
    }

    /**
     * 支持序列化.
     *
     * @param in a <code>java.io.ObjectInputStream</code> value
     * @throws IOException 发生错误
     * @throws ClassNotFoundException 发生错误
     */
    private void readObject(final ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        try
        {
            in.defaultReadObject();
            final PerUserPoolDataSource oldDS = (PerUserPoolDataSource)
                new PerUserPoolDataSourceFactory()
                    .getObjectInstance(getReference(), null, null, null);
            this.managers = oldDS.managers;
        }
        catch (final NamingException e)
        {
            throw new IOException("NamingException: " + e);
        }
    }

    /**
     * 返回与给定PoolKey关联的对象池.
     *
     * @param key 标识池的PoolKey
     * @return 使用PoolKey指定的用户名和数据源的GenericObjectPool池连接
     */
    private ObjectPool<PooledConnectionAndInfo> getPool(final PoolKey key) {
        final CPDSConnectionFactory mgr = (CPDSConnectionFactory) managers.get(key);
        return mgr == null ? null : mgr.getPool();
    }
}
