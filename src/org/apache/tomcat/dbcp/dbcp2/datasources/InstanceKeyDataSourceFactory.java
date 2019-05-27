package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * 创建<code>SharedPoolDataSource</code>或 <code>PerUserPoolDataSource</code>的JNDI ObjectFactory
 */
abstract class InstanceKeyDataSourceFactory implements ObjectFactory {

    private static final Map<String, InstanceKeyDataSource> instanceMap =
            new ConcurrentHashMap<>();

    static synchronized String registerNewInstance(final InstanceKeyDataSource ds) {
        int max = 0;
        final Iterator<String> i = instanceMap.keySet().iterator();
        while (i.hasNext()) {
            final String s = i.next();
            if (s != null) {
                try {
                    max = Math.max(max, Integer.parseInt(s));
                } catch (final NumberFormatException e) {
                    // no sweat, ignore those keys
                }
            }
        }
        final String instanceKey = String.valueOf(max + 1);
        // 暂时把占位符放在这里, 所以其他实例不会拿我们的 key.  准备好后我们将用池替换.
        instanceMap.put(instanceKey, ds);
        return instanceKey;
    }

    static void removeInstance(final String key) {
        if (key != null) {
            instanceMap.remove(key);
        }
    }

    /**
     * 关闭与此类关联的所有池.
     * 
     * @throws Exception Close exception
     */
    public static void closeAll() throws Exception {
        // 获取迭代器以遍历此数据源的所有实例.
        final Iterator<Entry<String,InstanceKeyDataSource>> instanceIterator =
            instanceMap.entrySet().iterator();
        while (instanceIterator.hasNext()) {
            instanceIterator.next().getValue().close();
        }
        instanceMap.clear();
    }


    /**
     * 实现ObjectFactory以创建SharedPoolDataSource或PerUserPoolDataSource的实例.
     */
    @Override
    public Object getObjectInstance(final Object refObj, final Name name,
                                    final Context context, final Hashtable<?,?> env)
        throws IOException, ClassNotFoundException {
        // 规范说如果我们不能创建引用的实例, 则返回null
        Object obj = null;
        if (refObj instanceof Reference) {
            final Reference ref = (Reference) refObj;
            if (isCorrectClass(ref.getClassName())) {
                final RefAddr ra = ref.get("instanceKey");
                if (ra != null && ra.getContent() != null) {
                    // 对象通过Referenceable api绑定到jndi.
                    obj = instanceMap.get(ra.getContent());
                }
                else
                {
                    // tomcat jndi从server.xml <ResourceParam>配置中创建一个Reference, 并将其传递给server.xml中给出的工厂实例.
                    String key = null;
                    if (name != null)
                    {
                        key = name.toString();
                        obj = instanceMap.get(key);
                    }
                    if (obj == null)
                    {
                        final InstanceKeyDataSource ds = getNewInstance(ref);
                        setCommonProperties(ref, ds);
                        obj = ds;
                        if (key != null)
                        {
                            instanceMap.put(key, ds);
                        }
                    }
                }
            }
        }
        return obj;
    }

    private void setCommonProperties(final Reference ref,
                                     final InstanceKeyDataSource ikds)
        throws IOException, ClassNotFoundException {

        RefAddr ra = ref.get("dataSourceName");
        if (ra != null && ra.getContent() != null) {
            ikds.setDataSourceName(ra.getContent().toString());
        }

        ra = ref.get("description");
        if (ra != null && ra.getContent() != null) {
            ikds.setDescription(ra.getContent().toString());
        }

        ra = ref.get("jndiEnvironment");
        if (ra != null  && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            ikds.setJndiEnvironment((Properties) deserialize(serialized));
        }

        ra = ref.get("loginTimeout");
        if (ra != null && ra.getContent() != null) {
            ikds.setLoginTimeout(
                Integer.parseInt(ra.getContent().toString()));
        }

        // Pool properties
        ra = ref.get("blockWhenExhausted");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultBlockWhenExhausted(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("evictionPolicyClassName");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultEvictionPolicyClassName(ra.getContent().toString());
        }

        // Pool properties
        ra = ref.get("lifo");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultLifo(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("maxIdlePerKey");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMaxIdle(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("maxTotalPerKey");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMaxTotal(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("maxWaitMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMaxWaitMillis(
                Long.parseLong(ra.getContent().toString()));
        }

        ra = ref.get("minEvictableIdleTimeMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMinEvictableIdleTimeMillis(
                Long.parseLong(ra.getContent().toString()));
        }

        ra = ref.get("minIdlePerKey");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMinIdle(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("numTestsPerEvictionRun");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultNumTestsPerEvictionRun(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("softMinEvictableIdleTimeMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultSoftMinEvictableIdleTimeMillis(
                Long.parseLong(ra.getContent().toString()));
        }

        ra = ref.get("testOnCreate");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestOnCreate(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("testOnBorrow");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestOnBorrow(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("testOnReturn");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestOnReturn(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("testWhileIdle");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestWhileIdle(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("timeBetweenEvictionRunsMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTimeBetweenEvictionRunsMillis(
                Long.parseLong(ra.getContent().toString()));
        }


        // 连接工厂属性

        ra = ref.get("validationQuery");
        if (ra != null && ra.getContent() != null) {
            ikds.setValidationQuery(ra.getContent().toString());
        }

        ra = ref.get("validationQueryTimeout");
        if (ra != null && ra.getContent() != null) {
            ikds.setValidationQueryTimeout(Integer.parseInt(
                    ra.getContent().toString()));
        }

        ra = ref.get("rollbackAfterValidation");
        if (ra != null && ra.getContent() != null) {
            ikds.setRollbackAfterValidation(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("maxConnLifetimeMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setMaxConnLifetimeMillis(
                Long.parseLong(ra.getContent().toString()));
        }


        // 连接属性

        ra = ref.get("defaultAutoCommit");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultAutoCommit(Boolean.valueOf(ra.getContent().toString()));
        }

        ra = ref.get("defaultTransactionIsolation");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTransactionIsolation(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("defaultReadOnly");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultReadOnly(Boolean.valueOf(ra.getContent().toString()));
        }
    }


    /**
     * @param className 类名
     * @return true 当且仅当 className 是从 getClass().getName().toString()返回的值
     */
    protected abstract boolean isCorrectClass(String className);

    /**
     * 创建子类的实例并设置Reference中包含的任何属性.
     * 
     * @param ref The reference
     * 
     * @return 数据源
     * @throws IOException IO 错误
     * @throws ClassNotFoundException 无法加载数据源实现
     */
    protected abstract InstanceKeyDataSource getNewInstance(Reference ref)
        throws IOException, ClassNotFoundException;

    /**
     * 用于设置Reference中保存的一些属性.
     * 
     * @param data Object data
     * @return 反序列化的对象
     * @throws IOException Stream 错误
     * @throws ClassNotFoundException 无法加载对象类
     */
    protected static final Object deserialize(final byte[] data)
        throws IOException, ClassNotFoundException {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new ByteArrayInputStream(data));
            return in.readObject();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException ex) {
                }
            }
        }
    }
}

