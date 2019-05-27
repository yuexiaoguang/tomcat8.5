package org.apache.tomcat.dbcp.dbcp2.datasources;

import javax.naming.RefAddr;
import javax.naming.Reference;

/**
 * 创建 <code>SharedPoolDataSource</code>的JNDI ObjectFactory
 */
public class SharedPoolDataSourceFactory
    extends InstanceKeyDataSourceFactory
{
    private static final String SHARED_POOL_CLASSNAME =
        SharedPoolDataSource.class.getName();

    @Override
    protected boolean isCorrectClass(final String className) {
        return SHARED_POOL_CLASSNAME.equals(className);
    }

    @Override
    protected InstanceKeyDataSource getNewInstance(final Reference ref) {
        final SharedPoolDataSource spds = new SharedPoolDataSource();
        final RefAddr ra = ref.get("maxTotal");
        if (ra != null && ra.getContent() != null) {
            spds.setMaxTotal(
                Integer.parseInt(ra.getContent().toString()));
        }
        return spds;
    }
}

