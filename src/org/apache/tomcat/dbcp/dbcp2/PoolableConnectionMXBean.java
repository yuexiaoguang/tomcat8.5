package org.apache.tomcat.dbcp.dbcp2;

import java.sql.SQLException;

/**
 * 定义将通过JMX为{@link PoolableConnection}实例公开的属性和方法.
 */
public interface PoolableConnectionMXBean {
    // 只读属性
    boolean isClosed() throws SQLException;
    //SQLWarning getWarnings() throws SQLException;
    String getToString();

    // Read-write properties
    boolean getAutoCommit() throws SQLException;
    void setAutoCommit(boolean autoCommit) throws SQLException;

    boolean getCacheState();
    void setCacheState(boolean cacheState);

    String getCatalog() throws SQLException;
    void setCatalog(String catalog) throws SQLException;

    int getHoldability() throws SQLException;
    void setHoldability(int holdability) throws SQLException;

    boolean isReadOnly() throws SQLException;
    void setReadOnly(boolean readOnly) throws SQLException;

    String getSchema() throws SQLException;
    void setSchema(String schema) throws SQLException;

    int getTransactionIsolation() throws SQLException;
    void setTransactionIsolation(int level) throws SQLException;

    // Methods
    void clearCachedState();
    void clearWarnings() throws SQLException;
    void close() throws SQLException;
    void reallyClose() throws SQLException;
}
