package org.apache.catalina.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;

/**
 * <code>ObjectInputStream</code>的子类从此Web应用程序的类装入器加载.
 * 只有正确地找到web应用程序, 允许类定义.
 */
public final class CustomObjectInputStream extends ObjectInputStream {

    private static final StringManager sm = StringManager.getManager(CustomObjectInputStream.class);

    private static final WeakHashMap<ClassLoader, Set<String>> reportedClassCache =
            new WeakHashMap<>();

    /**
     * 将用来解析类的类装入器.
     */
    private final ClassLoader classLoader;
    private final Set<String> reportedClasses;
    private final Log log;

    private final Pattern allowedClassNamePattern;
    private final String allowedClassNameFilter;
    private final boolean warnOnFailure;


    /**
     * @param stream 读取的输入流
     * @param classLoader 用于实例化对象的类装入器
     *
     * @exception IOException if an input/output error occurs
     */
    public CustomObjectInputStream(InputStream stream, ClassLoader classLoader) throws IOException {
        this(stream, classLoader, null, null, false);
    }


    /**
     * @param stream 读取的输入流
     * @param classLoader 用于实例化对象的类装入器
     * @param log 用于报告问题的logger. 如果filterMode不需要日志记录，则可能为 null
     * @param allowedClassNamePattern 用于筛选反序列化类的正则表达式. 如果启用过滤，则完全限定的类名必须与反序列化模式相匹配.
     * @param warnOnFailure 是否记录失败?
     *
     * @exception IOException if an input/output error occurs
     */
    public CustomObjectInputStream(InputStream stream, ClassLoader classLoader,
            Log log, Pattern allowedClassNamePattern, boolean warnOnFailure)
            throws IOException {
        super(stream);
        if (log == null && allowedClassNamePattern != null && warnOnFailure) {
            throw new IllegalArgumentException(
                    sm.getString("customObjectInputStream.logRequired"));
        }
        this.classLoader = classLoader;
        this.log = log;
        this.allowedClassNamePattern = allowedClassNamePattern;
        if (allowedClassNamePattern == null) {
            this.allowedClassNameFilter = null;
        } else {
            this.allowedClassNameFilter = allowedClassNamePattern.toString();
        }
        this.warnOnFailure = warnOnFailure;

        Set<String> reportedClasses;
        synchronized (reportedClassCache) {
            reportedClasses = reportedClassCache.get(classLoader);
        }
        if (reportedClasses == null) {
            reportedClasses = Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
            synchronized (reportedClassCache) {
                Set<String> original = reportedClassCache.get(classLoader);
                if (original == null) {
                    reportedClassCache.put(classLoader, reportedClasses);
                } else {
                    // Concurrent attempts to create the new Set. 确保所有线程使用第一个成功添加的Set.
                    reportedClasses = original;
                }
            }
        }
        this.reportedClasses = reportedClasses;
    }


    /**
     * 加载本地类等效于指定的流类描述, 通过使用分配给此上下文的类装入器.
     *
     * @param classDesc 来自输入流的类描述
     *
     * @exception ClassNotFoundException 如果找不到这个类
     * @exception IOException if an input/output error occurs
     */
    @Override
    public Class<?> resolveClass(ObjectStreamClass classDesc)
        throws ClassNotFoundException, IOException {

        String name = classDesc.getName();
        if (allowedClassNamePattern != null) {
            boolean allowed = allowedClassNamePattern.matcher(name).matches();
            if (!allowed) {
                boolean doLog = warnOnFailure && reportedClasses.add(name);
                String msg = sm.getString("customObjectInputStream.nomatch", name, allowedClassNameFilter);
                if (doLog) {
                    log.warn(msg);
                } else if (log.isDebugEnabled()) {
                    log.debug(msg);
                }
                throw new InvalidClassException(msg);
            }
        }

        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            try {
                // 因为原始类型也尝试超类
                return super.resolveClass(classDesc);
            } catch (ClassNotFoundException e2) {
                // 重新抛出原始异常, 因为它有更多的信息，说明为什么没有找到类. BZ 48007
                throw e;
            }
        }
    }


    /**
     * 返回一个代理类，实现代理类描述符中指定的接口. 使用分配给此上下文的类装入器完成此操作.
     */
    @Override
    protected Class<?> resolveProxyClass(String[] interfaces)
            throws IOException, ClassNotFoundException {

        Class<?>[] cinterfaces = new Class[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            cinterfaces[i] = classLoader.loadClass(interfaces[i]);
        }

        try {
            return Proxy.getProxyClass(classLoader, cinterfaces);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(null, e);
        }
    }
}
