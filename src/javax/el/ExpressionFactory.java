package javax.el;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class ExpressionFactory {

    private static final boolean IS_SECURITY_ENABLED =
        (System.getSecurityManager() != null);

    private static final String SERVICE_RESOURCE_NAME =
        "META-INF/services/javax.el.ExpressionFactory";

    private static final String PROPERTY_NAME = "javax.el.ExpressionFactory";

    private static final String PROPERTY_FILE;

    private static final CacheValue nullTcclFactory = new CacheValue();
    private static final ConcurrentMap<CacheKey, CacheValue> factoryCache =
            new ConcurrentHashMap<>();

    static {
        if (IS_SECURITY_ENABLED) {
            PROPERTY_FILE = AccessController.doPrivileged(
                    new PrivilegedAction<String>(){
                        @Override
                        public String run() {
                            return System.getProperty("java.home") + File.separator +
                                    "lib" + File.separator + "el.properties";
                        }

                    }
            );
        } else {
            PROPERTY_FILE = System.getProperty("java.home") + File.separator + "lib" +
                    File.separator + "el.properties";
        }
    }

    /**
     * 要使用的类由以下搜索顺序决定:
     * <ol>
     * <li>services API (META-INF/services/javax.el.ExpressionFactory)</li>
     * <li>$JRE_HOME/lib/el.properties - key javax.el.ExpressionFactory</li>
     * <li>javax.el.ExpressionFactory</li>
     * <li>平台默认实现 - org.apache.el.ExpressionFactoryImpl</li>
     * </ol>
     * @return the new ExpressionFactory
     */
    public static ExpressionFactory newInstance() {
        return newInstance(null);
    }

    /**
     * 搜索顺序和{@link #newInstance()}一样.
     *
     * @param properties 要传递给新实例的属性 (可能是 null)
     * @return 新 ExpressionFactory
     */
    public static ExpressionFactory newInstance(Properties properties) {
        ExpressionFactory result = null;

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();

        CacheValue cacheValue;
        Class<?> clazz;

        if (tccl == null) {
            cacheValue = nullTcclFactory;
        } else {
            CacheKey key = new CacheKey(tccl);
            cacheValue = factoryCache.get(key);
            if (cacheValue == null) {
                CacheValue newCacheValue = new CacheValue();
                cacheValue = factoryCache.putIfAbsent(key, newCacheValue);
                if (cacheValue == null) {
                    cacheValue = newCacheValue;
                }
            }
        }

        final Lock readLock = cacheValue.getLock().readLock();
        readLock.lock();
        try {
            clazz = cacheValue.getFactoryClass();
        } finally {
            readLock.unlock();
        }

        if (clazz == null) {
            String className = null;
            try {
                final Lock writeLock = cacheValue.getLock().writeLock();
                writeLock.lock();
                try {
                    className = cacheValue.getFactoryClassName();
                    if (className == null) {
                        className = discoverClassName(tccl);
                        cacheValue.setFactoryClassName(className);
                    }
                    if (tccl == null) {
                        clazz = Class.forName(className);
                    } else {
                        clazz = tccl.loadClass(className);
                    }
                    cacheValue.setFactoryClass(clazz);
                } finally {
                    writeLock.unlock();
                }
            } catch (ClassNotFoundException e) {
                throw new ELException(
                    "Unable to find ExpressionFactory of type: " + className,
                    e);
            }
        }

        try {
            Constructor<?> constructor = null;
            // 需要寻找一个构造函数吗?
            if (properties != null) {
                try {
                    constructor = clazz.getConstructor(Properties.class);
                } catch (SecurityException se) {
                    throw new ELException(se);
                } catch (NoSuchMethodException nsme) {
                    // 可以忽略
                    // 这个构造函数不存在是可以的
                }
            }
            if (constructor == null) {
                result = (ExpressionFactory) clazz.getConstructor().newInstance();
            } else {
                result = (ExpressionFactory) constructor.newInstance(properties);
            }

        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                NoSuchMethodException e) {
            throw new ELException(
                    "Unable to create ExpressionFactory of type: " + clazz.getName(),
                    e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            Util.handleThrowable(cause);
            throw new ELException(
                    "Unable to create ExpressionFactory of type: " + clazz.getName(),
                    e);
        }

        return result;
    }

    /**
     * 创建一个新的值表达式.
     *
     * @param context      这个求值的EL 上下文
     * @param expression   值表达式的String 形式
     * @param expectedType 表达式计算结果的预期类型
     *
     * @return 由输入参数形成的新值表达式
     *
     * @throws NullPointerException 如果预期的类型是<code>null</code>
     * @throws ELException 如果所提供的表达式中存在语法错误
     */
    public abstract ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType);

    public abstract ValueExpression createValueExpression(Object instance, Class<?> expectedType);

    /**
     * 创建一个新的方法表达式实例.
     *
     * @param context            这个求值的EL 上下文
     * @param expression         方法表达式的 String 形式
     * @param expectedReturnType 方法执行的结果的预期类型
     * @param expectedParamTypes 输入参数的期望类型
     *
     * @return 由输入参数形成的一个新方法表达式.
     *
     * @throws NullPointerException 如果预期的参数类型是<code>null</code>
     * @throws ELException 如果所提供的表达式中存在语法错误
     */
    public abstract MethodExpression createMethodExpression(ELContext context,
            String expression, Class<?> expectedReturnType,
            Class<?>[] expectedParamTypes);

    /**
     * 将提供的对象强制转换为请求的类型.
     *
     * @param obj          要强制转换的对象
     * @param expectedType 要转换成的类型
     *
     * @return 请求类型的实例.
     *
     * @throws ELException 如果转换失败
     */
    public abstract Object coerceToType(Object obj, Class<?> expectedType);

    /**
     * @return 默认实现总是返回 null
     */
    public ELResolver getStreamELResolver() {
        return null;
    }

    /**
     * @return 默认实现总是返回 null
     */
    public Map<String,Method> getInitFunctionMap() {
        return null;
    }

    /**
     * Key 用于缓存 ExpressionFactory 每个类装入器的发现信息. 类加载器引用不会是 {@code null}, 因为
     * {@code null} tccl 被另外处理.
     */
    private static class CacheKey {
        private final int hash;
        private final WeakReference<ClassLoader> ref;

        public CacheKey(ClassLoader cl) {
            hash = cl.hashCode();
            ref = new WeakReference<>(cl);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            ClassLoader thisCl = ref.get();
            if (thisCl == null) {
                return false;
            }
            return thisCl == ((CacheKey) obj).ref.get();
        }
    }

    private static class CacheValue {
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private String className;
        private WeakReference<Class<?>> ref;

        public CacheValue() {
        }

        public ReadWriteLock getLock() {
            return lock;
        }

        public String getFactoryClassName() {
            return className;
        }

        public void setFactoryClassName(String className) {
            this.className = className;
        }

        public Class<?> getFactoryClass() {
            return ref != null ? ref.get() : null;
        }

        public void setFactoryClass(Class<?> clazz) {
            ref = new WeakReference<Class<?>>(clazz);
        }
    }

    /**
     * 发现实现了ExpressionFactory的类的名称.
     *
     * @param tccl {@code ClassLoader}
     * @return 类名. 默认的实现总是 {@code null}.
     */
    private static String discoverClassName(ClassLoader tccl) {
        String className = null;

        // 首先服务 API
        className = getClassNameServices(tccl);
        if (className == null) {
            if (IS_SECURITY_ENABLED) {
                className = AccessController.doPrivileged(
                        new PrivilegedAction<String>() {
                            @Override
                            public String run() {
                                return getClassNameJreDir();
                            }
                        }
                );
            } else {
                // 其次 el.properties 文件
                className = getClassNameJreDir();
            }
        }
        if (className == null) {
            if (IS_SECURITY_ENABLED) {
                className = AccessController.doPrivileged(
                        new PrivilegedAction<String>() {
                            @Override
                            public String run() {
                                return getClassNameSysProp();
                            }
                        }
                );
            } else {
                // 第三，系统属性
                className = getClassNameSysProp();
            }
        }
        if (className == null) {
            // 第四 - 默认
            className = "org.apache.el.ExpressionFactoryImpl";
        }
        return className;
    }

    private static String getClassNameServices(ClassLoader tccl) {
        InputStream is = null;

        if (tccl == null) {
            is = ClassLoader.getSystemResourceAsStream(SERVICE_RESOURCE_NAME);
        } else {
            is = tccl.getResourceAsStream(SERVICE_RESOURCE_NAME);
        }

        if (is != null) {
            String line = null;
            try (InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                    BufferedReader br = new BufferedReader(isr)) {
                line = br.readLine();
                if (line != null && line.trim().length() > 0) {
                    return line.trim();
                }
            } catch (UnsupportedEncodingException e) {
                // UTF-8永远不会发生
                // 如果发生了 - 忽略 & 返回 null
            } catch (IOException e) {
                throw new ELException("Failed to read " + SERVICE_RESOURCE_NAME,
                        e);
            } finally {
                try {
                    is.close();
                } catch (IOException ioe) {/*Ignore*/}
            }
        }

        return null;
    }

    private static String getClassNameJreDir() {
        File file = new File(PROPERTY_FILE);
        if (file.canRead()) {
            try (InputStream is = new FileInputStream(file)){
                Properties props = new Properties();
                props.load(is);
                String value = props.getProperty(PROPERTY_NAME);
                if (value != null && value.trim().length() > 0) {
                    return value.trim();
                }
            } catch (FileNotFoundException e) {
                // 不应该发生 - 忽略它
            } catch (IOException e) {
                throw new ELException("Failed to read " + PROPERTY_FILE, e);
            }
        }
        return null;
    }

    private static final String getClassNameSysProp() {
        String value = System.getProperty(PROPERTY_NAME);
        if (value != null && value.trim().length() > 0) {
            return value.trim();
        }
        return null;
    }

}
