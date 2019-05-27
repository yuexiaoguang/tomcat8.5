package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 创建 {@link java.sql.Connection}的工厂类接口.
 */
public interface ConnectionFactory {
    /**
     * 在特定方式的实现中创建一个 {@link java.sql.Connection}.
     *
     * @return a new {@link java.sql.Connection}
     * @throws SQLException 如果创建连接时发生数据库错误
     */
    Connection createConnection() throws SQLException;
}
