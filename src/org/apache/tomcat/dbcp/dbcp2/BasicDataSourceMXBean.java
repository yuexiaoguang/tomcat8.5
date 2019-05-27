package org.apache.tomcat.dbcp.dbcp2;

/**
 * 定义将通过JMX提供的方法.
 */
public interface BasicDataSourceMXBean {

    boolean getAbandonedUsageTracking();

    Boolean getDefaultAutoCommit();

    Boolean getDefaultReadOnly();

    int getDefaultTransactionIsolation();

    String getDefaultCatalog();

    boolean getCacheState();

    String getDriverClassName();

    boolean getLifo();

    int getMaxTotal();

    int getMaxIdle();

    int getMinIdle();

    int getInitialSize();

    long getMaxWaitMillis();

    boolean isPoolPreparedStatements();

    int getMaxOpenPreparedStatements();

    boolean getTestOnCreate();

    boolean getTestOnBorrow();

    long getTimeBetweenEvictionRunsMillis();

    int getNumTestsPerEvictionRun();

    long getMinEvictableIdleTimeMillis();

    long getSoftMinEvictableIdleTimeMillis();

    boolean getTestWhileIdle();

    int getNumActive();

    int getNumIdle();

    String getPassword();

    String getUrl();

    String getUsername();

    String getValidationQuery();

    int getValidationQueryTimeout();

    String[] getConnectionInitSqlsAsArray();

    boolean isAccessToUnderlyingConnectionAllowed();

    long getMaxConnLifetimeMillis();

    boolean getLogExpiredConnections();

    boolean getRemoveAbandonedOnBorrow();

    boolean getRemoveAbandonedOnMaintenance();

    int getRemoveAbandonedTimeout();

    boolean getLogAbandoned();

    boolean isClosed();

    boolean getFastFailValidation();

    String[] getDisconnectionSqlCodesAsArray();
}
