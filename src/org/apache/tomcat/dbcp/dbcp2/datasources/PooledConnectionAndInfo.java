package org.apache.tomcat.dbcp.dbcp2.datasources;

import javax.sql.PooledConnection;

/**
 * 持有PooledConnection的不可变poolable对象, 以及用于创建连接的用户名和密码.
 */
final class PooledConnectionAndInfo {
    private final PooledConnection pooledConnection;
    private final String password;
    private final String username;
    private final UserPassKey upkey;

    PooledConnectionAndInfo(final PooledConnection pc, final String username, final String password) {
        this.pooledConnection = pc;
        this.username = username;
        this.password = password;
        upkey = new UserPassKey(username, password);
    }

    PooledConnection getPooledConnection() {
        return pooledConnection;
    }

    UserPassKey getUserPassKey() {
        return upkey;
    }

    /**
     * 获取密码.
     */
    String getPassword() {
        return password;
    }

    /**
     * 获取用户名.
     */
    String getUsername() {
        return username;
    }
}
