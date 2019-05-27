package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.Serializable;

class PoolKey implements Serializable {
    private static final long serialVersionUID = 2252771047542484533L;

    private final String datasourceName;
    private final String username;

    PoolKey(final String datasourceName, final String username) {
        this.datasourceName = datasourceName;
        this.username = username;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof PoolKey) {
            final PoolKey pk = (PoolKey)obj;
            return (null == datasourceName ? null == pk.datasourceName : datasourceName.equals(pk.datasourceName)) &&
                (null == username ? null == pk.username : username.equals(pk.username));
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = 0;
        if (datasourceName != null) {
            h += datasourceName.hashCode();
        }
        if (username != null) {
            h = 29 * h + username.hashCode();
        }
        return h;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer(50);
        sb.append("PoolKey(");
        sb.append(username).append(", ").append(datasourceName);
        sb.append(')');
        return sb.toString();
    }
}
