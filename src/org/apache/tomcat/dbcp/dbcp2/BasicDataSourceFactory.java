package org.apache.tomcat.dbcp.dbcp2;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPoolConfig;

/**
 * <p>JNDI 对象工厂创建基于指定的<code>Reference</code>的<code>RefAddr</code>值配置的<code>BasicDataSource</code>实例,
 * 其必须匹配<code>BasicDataSource</code> bean 属性的名称和数据类型, 可能抛出以下异常:</p>
 * <ul>
 * <li><code>connectionInitSqls</code> 必须作为单个String传递给此工厂，使用分号分隔语句，而<code>BasicDataSource</code>需要一组字符串.</li>
 * </ul>
 */
public class BasicDataSourceFactory implements ObjectFactory {

    private static final Log log = LogFactory.getLog(BasicDataSourceFactory.class);

    private static final String PROP_DEFAULTAUTOCOMMIT = "defaultAutoCommit";
    private static final String PROP_DEFAULTREADONLY = "defaultReadOnly";
    private static final String PROP_DEFAULTTRANSACTIONISOLATION = "defaultTransactionIsolation";
    private static final String PROP_DEFAULTCATALOG = "defaultCatalog";
    private static final String PROP_CACHESTATE ="cacheState";
    private static final String PROP_DRIVERCLASSNAME = "driverClassName";
    private static final String PROP_LIFO = "lifo";
    private static final String PROP_MAXTOTAL = "maxTotal";
    private static final String PROP_MAXIDLE = "maxIdle";
    private static final String PROP_MINIDLE = "minIdle";
    private static final String PROP_INITIALSIZE = "initialSize";
    private static final String PROP_MAXWAITMILLIS = "maxWaitMillis";
    private static final String PROP_TESTONCREATE = "testOnCreate";
    private static final String PROP_TESTONBORROW = "testOnBorrow";
    private static final String PROP_TESTONRETURN = "testOnReturn";
    private static final String PROP_TIMEBETWEENEVICTIONRUNSMILLIS = "timeBetweenEvictionRunsMillis";
    private static final String PROP_NUMTESTSPEREVICTIONRUN = "numTestsPerEvictionRun";
    private static final String PROP_MINEVICTABLEIDLETIMEMILLIS = "minEvictableIdleTimeMillis";
    private static final String PROP_SOFTMINEVICTABLEIDLETIMEMILLIS = "softMinEvictableIdleTimeMillis";
    private static final String PROP_EVICTIONPOLICYCLASSNAME = "evictionPolicyClassName";
    private static final String PROP_TESTWHILEIDLE = "testWhileIdle";
    private static final String PROP_PASSWORD = "password";
    private static final String PROP_URL = "url";
    private static final String PROP_USERNAME = "username";
    private static final String PROP_VALIDATIONQUERY = "validationQuery";
    private static final String PROP_VALIDATIONQUERY_TIMEOUT = "validationQueryTimeout";
    private static final String PROP_JMX_NAME = "jmxName";

    /**
     * connectionInitSqls 的属性名.
     * 关联值String的格式必须是 [query;]*
     */
    private static final String PROP_CONNECTIONINITSQLS = "connectionInitSqls";
    private static final String PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED = "accessToUnderlyingConnectionAllowed";
    private static final String PROP_REMOVEABANDONEDONBORROW = "removeAbandonedOnBorrow";
    private static final String PROP_REMOVEABANDONEDONMAINTENANCE = "removeAbandonedOnMaintenance";
    private static final String PROP_REMOVEABANDONEDTIMEOUT = "removeAbandonedTimeout";
    private static final String PROP_LOGABANDONED = "logAbandoned";
    private static final String PROP_ABANDONEDUSAGETRACKING = "abandonedUsageTracking";
    private static final String PROP_POOLPREPAREDSTATEMENTS = "poolPreparedStatements";
    private static final String PROP_MAXOPENPREPAREDSTATEMENTS = "maxOpenPreparedStatements";
    private static final String PROP_CONNECTIONPROPERTIES = "connectionProperties";
    private static final String PROP_MAXCONNLIFETIMEMILLIS = "maxConnLifetimeMillis";
    private static final String PROP_LOGEXPIREDCONNECTIONS = "logExpiredConnections";
    private static final String PROP_ROLLBACK_ON_RETURN = "rollbackOnReturn";
    private static final String PROP_ENABLE_AUTOCOMMIT_ON_RETURN = "enableAutoCommitOnReturn";
    private static final String PROP_DEFAULT_QUERYTIMEOUT = "defaultQueryTimeout";
    private static final String PROP_FASTFAIL_VALIDATION = "fastFailValidation";

    /**
     * 值字符串的格式必须是 [STATE_CODE,]*
     */
    private static final String PROP_DISCONNECTION_SQL_CODES = "disconnectionSqlCodes";

    /*
     * 阻止来自DBCP 1.x的过时属性.
     * 警告用户忽略这些并且他们应该使用2.x属性.
     */
    private static final String NUPROP_MAXACTIVE = "maxActive";
    private static final String NUPROP_REMOVEABANDONED = "removeAbandoned";
    private static final String NUPROP_MAXWAIT = "maxWait";

    /*
     * 阻止DataSource中预期的属性.
     * 此属性不会被列为忽略 - 它们可能出现在Resource中，而不是将它们列为忽略.
     */
    private static final String SILENTPROP_FACTORY = "factory";
    private static final String SILENTPROP_SCOPE = "scope";
    private static final String SILENTPROP_SINGLETON = "singleton";
    private static final String SILENTPROP_AUTH = "auth";

    private static final String[] ALL_PROPERTIES = {
        PROP_DEFAULTAUTOCOMMIT,
        PROP_DEFAULTREADONLY,
        PROP_DEFAULTTRANSACTIONISOLATION,
        PROP_DEFAULTCATALOG,
        PROP_CACHESTATE,
        PROP_DRIVERCLASSNAME,
        PROP_LIFO,
        PROP_MAXTOTAL,
        PROP_MAXIDLE,
        PROP_MINIDLE,
        PROP_INITIALSIZE,
        PROP_MAXWAITMILLIS,
        PROP_TESTONCREATE,
        PROP_TESTONBORROW,
        PROP_TESTONRETURN,
        PROP_TIMEBETWEENEVICTIONRUNSMILLIS,
        PROP_NUMTESTSPEREVICTIONRUN,
        PROP_MINEVICTABLEIDLETIMEMILLIS,
        PROP_SOFTMINEVICTABLEIDLETIMEMILLIS,
        PROP_EVICTIONPOLICYCLASSNAME,
        PROP_TESTWHILEIDLE,
        PROP_PASSWORD,
        PROP_URL,
        PROP_USERNAME,
        PROP_VALIDATIONQUERY,
        PROP_VALIDATIONQUERY_TIMEOUT,
        PROP_CONNECTIONINITSQLS,
        PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED,
        PROP_REMOVEABANDONEDONBORROW,
        PROP_REMOVEABANDONEDONMAINTENANCE,
        PROP_REMOVEABANDONEDTIMEOUT,
        PROP_LOGABANDONED,
        PROP_ABANDONEDUSAGETRACKING,
        PROP_POOLPREPAREDSTATEMENTS,
        PROP_MAXOPENPREPAREDSTATEMENTS,
        PROP_CONNECTIONPROPERTIES,
        PROP_MAXCONNLIFETIMEMILLIS,
        PROP_LOGEXPIREDCONNECTIONS,
        PROP_ROLLBACK_ON_RETURN,
        PROP_ENABLE_AUTOCOMMIT_ON_RETURN,
        PROP_DEFAULT_QUERYTIMEOUT,
        PROP_FASTFAIL_VALIDATION,
        PROP_DISCONNECTION_SQL_CODES,
        PROP_JMX_NAME
    };

    /**
     * DBCP 1.x中的过时属性. 警告字符串表示新属性.
     * LinkedHashMap 将保证按照插入Map的顺序输出属性.
     */
    private static final Map<String, String> NUPROP_WARNTEXT = new LinkedHashMap<>();

    static {
        NUPROP_WARNTEXT.put(
                NUPROP_MAXACTIVE,
                "Property " + NUPROP_MAXACTIVE + " is not used in DBCP2, use " + PROP_MAXTOTAL + " instead. "
                        + PROP_MAXTOTAL + " default value is " + GenericObjectPoolConfig.DEFAULT_MAX_TOTAL+".");
        NUPROP_WARNTEXT.put(
                NUPROP_REMOVEABANDONED,
                "Property " + NUPROP_REMOVEABANDONED + " is not used in DBCP2,"
                        + " use one or both of "
                        + PROP_REMOVEABANDONEDONBORROW + " or " + PROP_REMOVEABANDONEDONMAINTENANCE + " instead. "
                        + "Both have default value set to false.");
        NUPROP_WARNTEXT.put(
                NUPROP_MAXWAIT,
                "Property " + NUPROP_MAXWAIT + " is not used in DBCP2"
                        + " , use " + PROP_MAXWAITMILLIS + " instead. "
                        + PROP_MAXWAITMILLIS + " default value is " + BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS+".");
    }

    /**
     * Silent Properties.
     * 这些属性不会被列为忽略 - 它们可能出现在JDBC资源引用中，我们不会将它们列为忽略.
     */
    private static final List<String> SILENT_PROPERTIES = new ArrayList<>();

    static {
        SILENT_PROPERTIES.add(SILENTPROP_FACTORY);
        SILENT_PROPERTIES.add(SILENTPROP_SCOPE);
        SILENT_PROPERTIES.add(SILENTPROP_SINGLETON);
        SILENT_PROPERTIES.add(SILENTPROP_AUTH);

    }

    // -------------------------------------------------- ObjectFactory Methods

    /**
     * <p>创建并返回一个<code>BasicDataSource</code> 实例.  如果没有实例可以创建, 返回 <code>null</code>.</p>
     *
     * @param obj 可能为null的对象，包含可用于创建对象的位置或引用信息
     * @param name 相对于<code>nameCtx</code>的此对象的名称
     * @param nameCtx 相对于指定的参数<code>name</code>的上下文; 或<code>null</code>, 如果<code>name</code>是相对于默认的初始化上下文
     * @param environment 创建此对象时可能使用的null环境
     *
     * @throws Exception 如果创建实例时发生异常
     */
    @Override
    public Object getObjectInstance(final Object obj, final Name name, final Context nameCtx, final Hashtable<?,?> environment)
        throws Exception {

        // 只知道如何处理指定类名“javax.sql.DataSource”的<code>javax.naming.Reference</code>
        if (obj == null || !(obj instanceof Reference)) {
            return null;
        }
        final Reference ref = (Reference) obj;
        if (!"javax.sql.DataSource".equals(ref.getClassName())) {
            return null;
        }

        // 检查有关过时和未知属性的属性名称和日志警告
        final List<String> warnings = new ArrayList<>();
        final List<String> infoMessages = new ArrayList<>();
        validatePropertyNames(ref, name, warnings, infoMessages);
        for (final String warning : warnings) {
            log.warn(warning);
        }
        for (final String infoMessage : infoMessages) {
            log.info(infoMessage);
        }

        final Properties properties = new Properties();
        for (final String propertyName : ALL_PROPERTIES) {
            final RefAddr ra = ref.get(propertyName);
            if (ra != null) {
                final String propertyValue = ra.getContent().toString();
                properties.setProperty(propertyName, propertyValue);
            }
        }

        return createDataSource(properties);
    }

    /**
     * 收集警告和信息消息.  设置过时属性时会生成警告.  未知属性会生成信息消息.
     *
     * @param ref 检查属性的引用
     * @param name 用于getObject的名称
     * @param warnings 警告消息的容器
     * @param infoMessages 信息消息的容器
     */
    private void validatePropertyNames(final Reference ref, final Name name, final List<String> warnings,
                                      final List<String> infoMessages) {
        final List<String> allPropsAsList = Arrays.asList(ALL_PROPERTIES);
        final String nameString = name != null ? "Name = " + name.toString() + " " : "";
        if (NUPROP_WARNTEXT!=null && !NUPROP_WARNTEXT.keySet().isEmpty()) {
            for (final String propertyName : NUPROP_WARNTEXT.keySet()) {
                final RefAddr ra = ref.get(propertyName);
                if (ra != null && !allPropsAsList.contains(ra.getType())) {
                    final StringBuilder stringBuilder = new StringBuilder(nameString);
                    final String propertyValue = ra.getContent().toString();
                    stringBuilder.append(NUPROP_WARNTEXT.get(propertyName))
                            .append(" You have set value of \"")
                            .append(propertyValue)
                            .append("\" for \"")
                            .append(propertyName)
                            .append("\" property, which is being ignored.");
                    warnings.add(stringBuilder.toString());
                }
            }
        }

        final Enumeration<RefAddr> allRefAddrs = ref.getAll();
        while (allRefAddrs.hasMoreElements()) {
            final RefAddr ra = allRefAddrs.nextElement();
            final String propertyName = ra.getType();
            // 如果属性名称不在属性列表中, 没有对此发出警告, 而且也没有在"silent"清单中, 告诉用户我们忽略它.
            if (!(allPropsAsList.contains(propertyName)
                    || NUPROP_WARNTEXT.keySet().contains(propertyName)
                    || SILENT_PROPERTIES.contains(propertyName))) {
                final String propertyValue = ra.getContent().toString();
                final StringBuilder stringBuilder = new StringBuilder(nameString);
                stringBuilder.append("Ignoring unknown property: ")
                        .append("value of \"")
                        .append(propertyValue)
                        .append("\" for \"")
                        .append(propertyName)
                        .append("\" property");
                infoMessages.add(stringBuilder.toString());
            }
        }
    }

    /**
     * 基于给定的属性创建并配置一个 {@link BasicDataSource} 实例.
     *
     * @param properties 数据源配置属性
     * @return 数据源实例
     * @throws Exception 如果创建数据源时发生错误
     */
    public static BasicDataSource createDataSource(final Properties properties) throws Exception {
        final BasicDataSource dataSource = new BasicDataSource();
        String value = null;

        value = properties.getProperty(PROP_DEFAULTAUTOCOMMIT);
        if (value != null) {
            dataSource.setDefaultAutoCommit(Boolean.valueOf(value));
        }

        value = properties.getProperty(PROP_DEFAULTREADONLY);
        if (value != null) {
            dataSource.setDefaultReadOnly(Boolean.valueOf(value));
        }

        value = properties.getProperty(PROP_DEFAULTTRANSACTIONISOLATION);
        if (value != null) {
            int level = PoolableConnectionFactory.UNKNOWN_TRANSACTIONISOLATION;
            if ("NONE".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_NONE;
            }
            else if ("READ_COMMITTED".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_READ_COMMITTED;
            }
            else if ("READ_UNCOMMITTED".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_READ_UNCOMMITTED;
            }
            else if ("REPEATABLE_READ".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_REPEATABLE_READ;
            }
            else if ("SERIALIZABLE".equalsIgnoreCase(value)) {
                level = Connection.TRANSACTION_SERIALIZABLE;
            }
            else {
                try {
                    level = Integer.parseInt(value);
                } catch (final NumberFormatException e) {
                    System.err.println("Could not parse defaultTransactionIsolation: " + value);
                    System.err.println("WARNING: defaultTransactionIsolation not set");
                    System.err.println("using default value of database driver");
                    level = PoolableConnectionFactory.UNKNOWN_TRANSACTIONISOLATION;
                }
            }
            dataSource.setDefaultTransactionIsolation(level);
        }

        value = properties.getProperty(PROP_DEFAULTCATALOG);
        if (value != null) {
            dataSource.setDefaultCatalog(value);
        }

        value = properties.getProperty(PROP_CACHESTATE);
        if (value != null) {
            dataSource.setCacheState(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_DRIVERCLASSNAME);
        if (value != null) {
            dataSource.setDriverClassName(value);
        }

        value = properties.getProperty(PROP_LIFO);
        if (value != null) {
            dataSource.setLifo(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_MAXTOTAL);
        if (value != null) {
            dataSource.setMaxTotal(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MAXIDLE);
        if (value != null) {
            dataSource.setMaxIdle(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MINIDLE);
        if (value != null) {
            dataSource.setMinIdle(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_INITIALSIZE);
        if (value != null) {
            dataSource.setInitialSize(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MAXWAITMILLIS);
        if (value != null) {
            dataSource.setMaxWaitMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_TESTONCREATE);
        if (value != null) {
            dataSource.setTestOnCreate(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TESTONBORROW);
        if (value != null) {
            dataSource.setTestOnBorrow(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TESTONRETURN);
        if (value != null) {
            dataSource.setTestOnReturn(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_TIMEBETWEENEVICTIONRUNSMILLIS);
        if (value != null) {
            dataSource.setTimeBetweenEvictionRunsMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_NUMTESTSPEREVICTIONRUN);
        if (value != null) {
            dataSource.setNumTestsPerEvictionRun(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_MINEVICTABLEIDLETIMEMILLIS);
        if (value != null) {
            dataSource.setMinEvictableIdleTimeMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_SOFTMINEVICTABLEIDLETIMEMILLIS);
        if (value != null) {
            dataSource.setSoftMinEvictableIdleTimeMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_EVICTIONPOLICYCLASSNAME);
        if (value != null) {
            dataSource.setEvictionPolicyClassName(value);
        }

        value = properties.getProperty(PROP_TESTWHILEIDLE);
        if (value != null) {
            dataSource.setTestWhileIdle(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_PASSWORD);
        if (value != null) {
            dataSource.setPassword(value);
        }

        value = properties.getProperty(PROP_URL);
        if (value != null) {
            dataSource.setUrl(value);
        }

        value = properties.getProperty(PROP_USERNAME);
        if (value != null) {
            dataSource.setUsername(value);
        }

        value = properties.getProperty(PROP_VALIDATIONQUERY);
        if (value != null) {
            dataSource.setValidationQuery(value);
        }

        value = properties.getProperty(PROP_VALIDATIONQUERY_TIMEOUT);
        if (value != null) {
            dataSource.setValidationQueryTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_ACCESSTOUNDERLYINGCONNECTIONALLOWED);
        if (value != null) {
            dataSource.setAccessToUnderlyingConnectionAllowed(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDONBORROW);
        if (value != null) {
            dataSource.setRemoveAbandonedOnBorrow(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDONMAINTENANCE);
        if (value != null) {
            dataSource.setRemoveAbandonedOnMaintenance(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_REMOVEABANDONEDTIMEOUT);
        if (value != null) {
            dataSource.setRemoveAbandonedTimeout(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_LOGABANDONED);
        if (value != null) {
            dataSource.setLogAbandoned(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_ABANDONEDUSAGETRACKING);
        if (value != null) {
            dataSource.setAbandonedUsageTracking(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_POOLPREPAREDSTATEMENTS);
        if (value != null) {
            dataSource.setPoolPreparedStatements(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_MAXOPENPREPAREDSTATEMENTS);
        if (value != null) {
            dataSource.setMaxOpenPreparedStatements(Integer.parseInt(value));
        }

        value = properties.getProperty(PROP_CONNECTIONINITSQLS);
        if (value != null) {
            dataSource.setConnectionInitSqls(parseList(value, ';'));
        }

        value = properties.getProperty(PROP_CONNECTIONPROPERTIES);
        if (value != null) {
          final Properties p = getProperties(value);
          final Enumeration<?> e = p.propertyNames();
          while (e.hasMoreElements()) {
            final String propertyName = (String) e.nextElement();
            dataSource.addConnectionProperty(propertyName, p.getProperty(propertyName));
          }
        }

        value = properties.getProperty(PROP_MAXCONNLIFETIMEMILLIS);
        if (value != null) {
            dataSource.setMaxConnLifetimeMillis(Long.parseLong(value));
        }

        value = properties.getProperty(PROP_LOGEXPIREDCONNECTIONS);
        if (value != null) {
            dataSource.setLogExpiredConnections(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_JMX_NAME);
        if (value != null) {
            dataSource.setJmxName(value);
        }

        value = properties.getProperty(PROP_ENABLE_AUTOCOMMIT_ON_RETURN);
        if (value != null) {
            dataSource.setEnableAutoCommitOnReturn(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_ROLLBACK_ON_RETURN);
        if (value != null) {
            dataSource.setRollbackOnReturn(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_DEFAULT_QUERYTIMEOUT);
        if (value != null) {
            dataSource.setDefaultQueryTimeout(Integer.valueOf(value));
        }

        value = properties.getProperty(PROP_FASTFAIL_VALIDATION);
        if (value != null) {
            dataSource.setFastFailValidation(Boolean.valueOf(value).booleanValue());
        }

        value = properties.getProperty(PROP_DISCONNECTION_SQL_CODES);
        if (value != null) {
            dataSource.setDisconnectionSqlCodes(parseList(value, ','));
        }

        // DBCP-215
        // 欺骗以确保创建initialSize连接
        if (dataSource.getInitialSize() > 0) {
            dataSource.getLogWriter();
        }

        // 返回配置的DataSource实例
        return dataSource;
    }

    /**
     * <p>从字符串中解析属性. 字符串格式必须是 [propertyName=property;]*<p>
     * @param propText
     * @return Properties
     * @throws Exception
     */
    private static Properties getProperties(final String propText) throws Exception {
      final Properties p = new Properties();
      if (propText != null) {
        p.load(new ByteArrayInputStream(
                propText.replace(';', '\n').getBytes(StandardCharsets.ISO_8859_1)));
      }
      return p;
    }

    /**
     * 从分隔的字符串中解析属性值列表.
     * 
     * @param value 分隔的值列表
     * @param delimiter 用于分隔列表中的值的字符
     * 
     * @return 值集合
     */
    private static Collection<String> parseList(final String value, final char delimiter) {
        final StringTokenizer tokenizer = new StringTokenizer(value, Character.toString(delimiter));
        final Collection<String> tokens = new ArrayList<>(tokenizer.countTokens());
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        return tokens;
    }
}
