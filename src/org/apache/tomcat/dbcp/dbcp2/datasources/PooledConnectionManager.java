package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.sql.SQLException;

import javax.sql.PooledConnection;

/**
 * 管理PoolableConnection和使用它们的连接池的方法.
 */
interface PooledConnectionManager {
    /**
     * 关闭PooledConnection并将其从其所属的连接池中删除, 调整池计数器.
     *
     * @param pc 无效的PooledConnection
     * @throws SQLException 如果关闭连接时发生SQL错误
     */
    void invalidate(PooledConnection pc) throws SQLException;

    /**
     * 设置创建连接时使用的数据库密码.
     *
     * @param password 验证数据库时使用的密码
     */
    void setPassword(String password);


    /**
     * 关闭与给定用户关联的连接池.
     *
     * @param username 用户名
     * @throws SQLException 如果在池中关闭空闲连接时发生错误
     */
    void closePool(String username) throws SQLException;

}
