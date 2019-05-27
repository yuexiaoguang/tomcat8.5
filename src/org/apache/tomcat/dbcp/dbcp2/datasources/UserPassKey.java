package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.Serializable;

/**
 * <p>保存用户名, 密码对.  用作支持SharedPoolDataSource的KeyedObjectPool的poolable对象Key.
 * 具有相同用户名的两个实例被视为相等. 这可确保池中的每个用户只有一个Key池. 创建新连接时，KeyedCPDSConnectionFactory使用密码（以及用户名）.</p>
 *
 * <p>{@link InstanceKeyDataSource#getConnection(String, String)} 验证用于创建连接的密码是否与客户端提供的密码匹配.</p>
 */
class UserPassKey implements Serializable {
    private static final long serialVersionUID = 5142970911626584817L;
    private final String password;
    private final String username;

    UserPassKey(final String username, final String password) {
        this.username = username;
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    /**
     * @return <code>true</code> 如果两个对象的用户名字段相同. 具有相同用户名但密码不同的两个实例被视为相等.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (!(obj instanceof UserPassKey)) {
            return false;
        }

        final UserPassKey key = (UserPassKey) obj;

        return this.username == null ?
                key.username == null :
                this.username.equals(key.username);
    }

    @Override
    public int hashCode() {
        return this.username != null ?
                this.username.hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer(50);
        sb.append("UserPassKey(");
        sb.append(username).append(", ").append(password).append(')');
        return sb.toString();
    }
}
