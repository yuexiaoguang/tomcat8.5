package org.apache.naming;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;

/**
 * 处理关联 :
 * <ul>
 * <li>对象和 NamingContext之间的</li>
 * <li>调用线程和NamingContext之间的</li>
 * <li>使用绑定到相同命名上下文的对象调用线程</li>
 * <li>带有NamingContext的线程上下文类加载器</li>
 * <li>线程上下文类加载器，其对象绑定到相同的NamingContext</li>
 * </ul>
 * 对象通常是Catalina Server 或 Context 对象.
 */
public class ContextBindings {

    // -------------------------------------------------------------- Variables

    /**
     * 绑定对象 - 命名上下文. Keyed by object.
     */
    private static final Hashtable<Object,Context> objectBindings = new Hashtable<>();


    /**
     * 绑定线程 - 命名上下文. Keyed by thread.
     */
    private static final Hashtable<Thread,Context> threadBindings = new Hashtable<>();


    /**
     * 绑定线程  - 对象. Keyed by thread.
     */
    private static final Hashtable<Thread,Object> threadObjectBindings = new Hashtable<>();


    /**
     * 绑定类加载器 - 命名上下文. Keyed by class loader.
     */
    private static final Hashtable<ClassLoader,Context> clBindings = new Hashtable<>();


    /**
     * 绑定类加载器 - 对象. Keyed by class loader.
     */
    private static final Hashtable<ClassLoader,Object> clObjectBindings = new Hashtable<>();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ContextBindings.class);


    // --------------------------------------------------------- Public Methods

    /**
     * 绑定对象和命名上下文.
     *
     * @param obj       Object to bind with naming context
     * @param context   Associated naming context instance
     */
    public static void bindContext(Object obj, Context context) {
        bindContext(obj, context, null);
    }


    /**
     * 绑定对象和命名上下文.
     *
     * @param obj       Object to bind with naming context
     * @param context   Associated naming context instance
     * @param token     Security token
     */
    public static void bindContext(Object obj, Context context, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            objectBindings.put(obj, context);
        }
    }


    /**
     * 解绑对象和命名上下文.
     *
     * @param obj   Object to unbind
     * @param token Security token
     */
    public static void unbindContext(Object obj, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            objectBindings.remove(obj);
        }
    }


    /**
     * 检索命名上下文.
     *
     * @param obj   Object bound to the required naming context
     */
    static Context getContext(Object obj) {
        return objectBindings.get(obj);
    }


    /**
     * 绑定命名上下文到一个线程.
     *
     * @param obj   Object bound to the required naming context
     * @param token Security token
     *
     * @throws NamingException 如果没有命名上下文绑定到提供的对象
     */
    public static void bindThread(Object obj, Object token) throws NamingException {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Context context = objectBindings.get(obj);
            if (context == null) {
                throw new NamingException(
                        sm.getString("contextBindings.unknownContext", obj));
            }
            threadBindings.put(Thread.currentThread(), context);
            threadObjectBindings.put(Thread.currentThread(), obj);
        }
    }


    /**
     * 解绑线程和命名上下文.
     *
     * @param obj   Object bound to the required naming context
     * @param token Security token
     */
    public static void unbindThread(Object obj, Object token) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            threadBindings.remove(Thread.currentThread());
            threadObjectBindings.remove(Thread.currentThread());
        }
    }


    /**
     * 检索绑定到当前线程的命名上下文.
     *
     * @return The naming context bound to the current thread.
     *
     * @throws NamingException 如果没有命名上下文绑定到当前线程
     */
    public static Context getThread() throws NamingException {
        Context context = threadBindings.get(Thread.currentThread());
        if (context == null) {
            throw new NamingException
                    (sm.getString("contextBindings.noContextBoundToThread"));
        }
        return context;
    }


    /**
     * 检索绑定到命名上下文和当前线程的对象的名称.
     */
    static String getThreadName() throws NamingException {
        Object obj = threadObjectBindings.get(Thread.currentThread());
        if (obj == null) {
            throw new NamingException
                    (sm.getString("contextBindings.noContextBoundToThread"));
        }
        return obj.toString();
    }


    /**
     * 测试当前线程是否绑定到命名上下文.
     *
     * @return <code>true</code>是, <code>false</code>否
     */
    public static boolean isThreadBound() {
        return (threadBindings.containsKey(Thread.currentThread()));
    }


    /**
     * 绑定命名上下文到类加载器.
     *
     * @param obj           Object bound to the required naming context
     * @param token         Security token
     * @param classLoader   The class loader to bind to the naming context
     *
     * @throws NamingException 如果没有命名上下文绑定到提供的对象
     */
    public static void bindClassLoader(Object obj, Object token,
            ClassLoader classLoader) throws NamingException {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Context context = objectBindings.get(obj);
            if (context == null) {
                throw new NamingException
                        (sm.getString("contextBindings.unknownContext", obj));
            }
            clBindings.put(classLoader, context);
            clObjectBindings.put(classLoader, obj);
        }
    }


    /**
     * 解绑命名上下文和类加载器.
     *
     * @param obj           Object bound to the required naming context
     * @param token         Security token
     * @param classLoader   The class loader bound to the naming context
     */
    public static void unbindClassLoader(Object obj, Object token,
            ClassLoader classLoader) {
        if (ContextAccessController.checkSecurityToken(obj, token)) {
            Object o = clObjectBindings.get(classLoader);
            if (o == null || !o.equals(obj)) {
                return;
            }
            clBindings.remove(classLoader);
            clObjectBindings.remove(classLoader);
        }
    }


    /**
     * 检索绑定到类加载器的命名上下文.
     *
     * @return 绑定到当前类加载器或其父类的命名上下文
     *
     * @throws NamingException 如果没有绑定命名上下文
     */
    public static Context getClassLoader() throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Context context = null;
        do {
            context = clBindings.get(cl);
            if (context != null) {
                return context;
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException(sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * 检索绑定到命名上下文和线程上下文类加载器的对象的名称.
     */
    static String getClassLoaderName() throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object obj = null;
        do {
            obj = clObjectBindings.get(cl);
            if (obj != null) {
                return obj.toString();
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException (sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * 测试线程上下文类加载器是否绑定到上下文.
     *
     * @return <code>true</code>如果线程上下文类加载器或它的父级绑定到命名上下文, 否则<code>false</code>
     */
    public static boolean isClassLoaderBound() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        do {
            if (clBindings.containsKey(cl)) {
                return true;
            }
        } while ((cl = cl.getParent()) != null);
        return false;
    }
}
