package org.apache.catalina.security;


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Globals;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 这个工具类关联了一个<code>Subject</code> 到当前<code>AccessControlContext</code>.
 * 当使用<code>SecurityManager</code>的时候, * 容器将总是将所调用的线程与一个只包含请求的Servlet/Filter主体的AccessControlContext关联 *.
 *
 * 这个类使用反射来调用方法.
 */
public final class SecurityUtil{

    // Note that indexes overlap.
    // A Servlet uses "init", "service", "event", "destroy".
    // A Filter uses "doFilter", "doFilterEvent", "destroy".
    private static final int INIT= 0;
    private static final int SERVICE = 1;
    private static final int DOFILTER = 1;
    private static final int EVENT = 2;
    private static final int DOFILTEREVENT = 2;
    private static final int DESTROY = 3;

    private static final String INIT_METHOD = "init";
    private static final String DOFILTER_METHOD = "doFilter";
    private static final String SERVICE_METHOD = "service";
    private static final String EVENT_METHOD = "event";
    private static final String DOFILTEREVENT_METHOD = "doFilterEvent";
    private static final String DESTROY_METHOD = "destroy";

    /**
     * 缓存每个正在创建的方法的类.
     */
    private static final Map<Class<?>,Method[]> classCache = new ConcurrentHashMap<>();

    private static final Log log = LogFactory.getLog(SecurityUtil.class);

    private static final boolean packageDefinitionEnabled =
         (System.getProperty("package.definition") == null &&
           System.getProperty("package.access")  == null) ? false : true;

    /**
     * The string resources for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE);


    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Servlet</code>
     * 
     * @throws Exception an execution error occurred
     */
    public static void doAsPrivilege(final String methodName,
                                     final Servlet targetObject) throws Exception {
         doAsPrivilege(methodName, targetObject, null, null, null);
    }


    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Servlet</code>
     * @param targetType <code>Class</code>数组用于初始化一个<code>Method</code>对象.
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     * 
     * @throws Exception an execution error occurred
     */
    public static void doAsPrivilege(final String methodName,
                                     final Servlet targetObject,
                                     final Class<?>[] targetType,
                                     final Object[] targetArguments)
        throws Exception {

         doAsPrivilege(methodName,
                       targetObject,
                       targetType,
                       targetArguments,
                       null);
    }


    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Servlet</code>
     * @param targetType <code>Class</code>数组用于初始化一个<code>Method</code>对象.
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     * @param principal 安全特权应用的<code>Principal</code>
     * 
     * @throws Exception an execution error occurred
     */
    public static void doAsPrivilege(final String methodName,
                                     final Servlet targetObject,
                                     final Class<?>[] targetParameterTypes,
                                     final Object[] targetArguments,
                                     Principal principal)
        throws Exception {

        Method method = null;
        Method[] methodsCache = classCache.get(Servlet.class);
        if(methodsCache == null) {
            method = createMethodAndCacheIt(methodsCache,
                                            Servlet.class,
                                            methodName,
                                            targetParameterTypes);
        } else {
            method = findMethod(methodsCache, methodName);
            if (method == null) {
                method = createMethodAndCacheIt(methodsCache,
                                                Servlet.class,
                                                methodName,
                                                targetParameterTypes);
            }
        }

        execute(method, targetObject, targetArguments, principal);
    }


    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     * 
     * @throws Exception an execution error occurred
     */
    public static void doAsPrivilege(final String methodName,
                                     final Filter targetObject)
        throws Exception {

         doAsPrivilege(methodName, targetObject, null, null);
    }


    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     * @param targetType <code>Class</code>数组用于初始化一个<code>Method</code>对象.
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     * 
     * @throws Exception an execution error occurred
     */
    public static void doAsPrivilege(final String methodName,
                                     final Filter targetObject,
                                     final Class<?>[] targetType,
                                     final Object[] targetArguments)
        throws Exception {

        doAsPrivilege(
                methodName, targetObject, targetType, targetArguments, null);
    }

    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject.
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     * @param targetParameterTypes 用于实例化<code>Method</code>对象的<code>Class</code>数组.
     * @param targetParameterValues 包含运行时参数实例的<code>Object</code>数组.
     * @param principal 安全特权应用的<code>Principal</code>
     * 
     * @throws Exception an execution error occurred
     */
    public static void doAsPrivilege(final String methodName,
                                     final Filter targetObject,
                                     final Class<?>[] targetParameterTypes,
                                     final Object[] targetParameterValues,
                                     Principal principal)
        throws Exception {

        Method method = null;
        Method[] methodsCache = classCache.get(Filter.class);
        if(methodsCache == null) {
            method = createMethodAndCacheIt(methodsCache,
                                            Filter.class,
                                            methodName,
                                            targetParameterTypes);
        } else {
            method = findMethod(methodsCache, methodName);
            if (method == null) {
                method = createMethodAndCacheIt(methodsCache,
                                                Filter.class,
                                                methodName,
                                                targetParameterTypes);
            }
        }

        execute(method, targetObject, targetParameterValues, principal);
    }


    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject.
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     * @param principal 安全特权应用的<code>Principal</code>
     * 
     * @throws Exception an execution error occurred
     */
    private static void execute(final Method method,
                                final Object targetObject,
                                final Object[] targetArguments,
                                Principal principal)
        throws Exception {

        try{
            Subject subject = null;
            PrivilegedExceptionAction<Void> pea =
                new PrivilegedExceptionAction<Void>(){
                    @Override
                    public Void run() throws Exception{
                       method.invoke(targetObject, targetArguments);
                       return null;
                    }
            };

            // The first argument is always the request object
            if (targetArguments != null
                    && targetArguments[0] instanceof HttpServletRequest){
                HttpServletRequest request =
                    (HttpServletRequest)targetArguments[0];

                boolean hasSubject = false;
                HttpSession session = request.getSession(false);
                if (session != null){
                    subject =
                        (Subject)session.getAttribute(Globals.SUBJECT_ATTR);
                    hasSubject = (subject != null);
                }

                if (subject == null){
                    subject = new Subject();

                    if (principal != null){
                        subject.getPrincipals().add(principal);
                    }
                }

                if (session != null && !hasSubject) {
                    session.setAttribute(Globals.SUBJECT_ATTR, subject);
                }
            }

            Subject.doAsPrivileged(subject, pea, null);
        } catch( PrivilegedActionException pe) {
            Throwable e;
            if (pe.getException() instanceof InvocationTargetException) {
                e = pe.getException().getCause();
                ExceptionUtils.handleThrowable(e);
            } else {
                e = pe;
            }

            if (log.isDebugEnabled()){
                log.debug(sm.getString("SecurityUtil.doAsPrivilege"), e);
            }

            if (e instanceof UnavailableException)
                throw (UnavailableException) e;
            else if (e instanceof ServletException)
                throw (ServletException) e;
            else if (e instanceof IOException)
                throw (IOException) e;
            else if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            else
                throw new ServletException(e.getMessage(), e);
        }
    }


    /**
     * 找到存储在缓存中的方法.
     * 
     * @param methodsCache 用于存储方法实例的缓存
     * @param methodName 应用安全约束的方法
     * @return 方法实例, null
     */
    private static Method findMethod(Method[] methodsCache,
                                     String methodName){
        if (methodName.equals(INIT_METHOD)){
            return methodsCache[INIT];
        } else if (methodName.equals(DESTROY_METHOD)){
            return methodsCache[DESTROY];
        } else if (methodName.equals(SERVICE_METHOD)){
            return methodsCache[SERVICE];
        } else if (methodName.equals(DOFILTER_METHOD)){
            return methodsCache[DOFILTER];
        } else if (methodName.equals(EVENT_METHOD)){
            return methodsCache[EVENT];
        } else if (methodName.equals(DOFILTEREVENT_METHOD)){
            return methodsCache[DOFILTEREVENT];
        }
        return null;
    }


    /**
     * 创建方法并将其缓存以便进一步重用.
     * 
     * @param methodsCache 用于存储方法实例的缓存
     * @param targetType 方法将被调用的类.
     * @param methodName 应用安全约束的方法
     * @param parameterTypes 用于初始化一个<code>Method</code>对象的<code>Class</code>数组.
     * 
     * @return 方法实例.
     * @throws Exception an execution error occurred
     */
    private static Method createMethodAndCacheIt(Method[] methodsCache,
                                                 Class<?> targetType,
                                                 String methodName,
                                                 Class<?>[] parameterTypes)
            throws Exception {

        if (methodsCache == null) {
            methodsCache = new Method[4];
        }

        Method method = targetType.getMethod(methodName, parameterTypes);

        if (methodName.equals(INIT_METHOD)){
            methodsCache[INIT] = method;
        } else if (methodName.equals(DESTROY_METHOD)){
            methodsCache[DESTROY] = method;
        } else if (methodName.equals(SERVICE_METHOD)){
            methodsCache[SERVICE] = method;
        } else if (methodName.equals(DOFILTER_METHOD)){
            methodsCache[DOFILTER] = method;
        } else if (methodName.equals(EVENT_METHOD)){
            methodsCache[EVENT] = method;
        } else if (methodName.equals(DOFILTEREVENT_METHOD)){
            methodsCache[DOFILTEREVENT] = method;
        }

        classCache.put(targetType, methodsCache);

        return method;
    }


    /**
     * 从缓存中删除对象.
     *
     * @param cachedObject 要移除的对象
     */
    public static void remove(Object cachedObject){
        classCache.remove(cachedObject);
    }


    /**
     * 返回<code>SecurityManager</code>是否可用, 只有当Security可用以及包保护机制启用的时候.
     * 
     * @return <code>true</code>如果启用包级保护
     */
    public static boolean isPackageProtectionEnabled(){
        if (packageDefinitionEnabled && Globals.IS_SECURITY_ENABLED){
            return true;
        }
        return false;
    }
}
