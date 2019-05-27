package org.apache.tomcat.dbcp.pool2.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

import org.apache.tomcat.dbcp.pool2.PooledObject;

/**
 * 用于通过JMX提供有关池中的对象的信息的对象的实现.
 */
public class DefaultPooledObjectInfo implements DefaultPooledObjectInfoMBean {

    private final PooledObject<?> pooledObject;

    /**
     * @param pooledObject 此实例将表示的池中的对象
     */
    public DefaultPooledObjectInfo(final PooledObject<?> pooledObject) {
        this.pooledObject = pooledObject;
    }

    @Override
    public long getCreateTime() {
        return pooledObject.getCreateTime();
    }

    @Override
    public String getCreateTimeFormatted() {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        return sdf.format(Long.valueOf(pooledObject.getCreateTime()));
    }

    @Override
    public long getLastBorrowTime() {
        return pooledObject.getLastBorrowTime();
    }

    @Override
    public String getLastBorrowTimeFormatted() {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        return sdf.format(Long.valueOf(pooledObject.getLastBorrowTime()));
    }

    @Override
    public String getLastBorrowTrace() {
        final StringWriter sw = new StringWriter();
        pooledObject.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public long getLastReturnTime() {
        return pooledObject.getLastReturnTime();
    }

    @Override
    public String getLastReturnTimeFormatted() {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        return sdf.format(Long.valueOf(pooledObject.getLastReturnTime()));
    }

    @Override
    public String getPooledObjectType() {
        return pooledObject.getObject().getClass().getName();
    }

    @Override
    public String getPooledObjectToString() {
        return pooledObject.getObject().toString();
    }

    @Override
    public long getBorrowedCount() {
        // TODO Simplify this once getBorrowedCount has been added to PooledObject
        if (pooledObject instanceof DefaultPooledObject) {
            return ((DefaultPooledObject<?>) pooledObject).getBorrowedCount();
        }
        return -1;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("DefaultPooledObjectInfo [pooledObject=");
        builder.append(pooledObject);
        builder.append("]");
        return builder.toString();
    }
}
