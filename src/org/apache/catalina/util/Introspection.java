package org.apache.catalina.util;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * 它需要了解Tomcat内部结构或仅由Tomcat内部构件使用.
 */
public class Introspection {

    private static final StringManager sm =
            StringManager.getManager("org.apache.catalina.util");


    /**
     * 从setter名称提取 Java Bean 属性名称.
     *
     * Note: 此方法假定已检查方法名称是否正确.
     * 
     * @param setter The setter method
     * @return bean的属性名
     */
    public static String getPropertyName(Method setter) {
        return Introspector.decapitalize(setter.getName().substring(3));
    }


    /**
     * 确定Java Bean setter是否有一个有效的名称和签名.
     *
     * @param method    The method to test
     *
     * @return  <code>true</code> 如果有, 否则<code>false</code>
     */
    public static boolean isValidSetter(Method method) {
        if (method.getName().startsWith("set")
                && method.getName().length() > 3
                && method.getParameterTypes().length == 1
                && method.getReturnType().getName().equals("void")) {
            return true;
        }
        return false;
    }

    /**
     * 确定方法是否是有效的生命周期回调方法.
     *
     * @param method
     *            The method to test
     *
     * @return <code>true</code> 如果是, 否则 <code>false</code>
     */
    public static boolean isValidLifecycleCallback(Method method) {
        if (method.getParameterTypes().length != 0
                || Modifier.isStatic(method.getModifiers())
                || method.getExceptionTypes().length > 0
                || !method.getReturnType().getName().equals("void")) {
            return false;
        }
        return true;
    }

    /**
     * 获取可被配置的任何安全管理器的类的声明的字段.
     * 
     * @param clazz The class to introspect
     * @return 类的字段
     */
    public static Field[] getDeclaredFields(final Class<?> clazz) {
        Field[] fields = null;
        if (Globals.IS_SECURITY_ENABLED) {
            fields = AccessController.doPrivileged(
                    new PrivilegedAction<Field[]>(){
                @Override
                public Field[] run(){
                    return clazz.getDeclaredFields();
                }
            });
        } else {
            fields = clazz.getDeclaredFields();
        }
        return fields;
    }


    /**
     * 获取可被配置的任何安全管理器的类的声明的方法.
     * @param clazz The class to introspect
     * @return 类的方法
     */
    public static Method[] getDeclaredMethods(final Class<?> clazz) {
        Method[] methods = null;
        if (Globals.IS_SECURITY_ENABLED) {
            methods = AccessController.doPrivileged(
                    new PrivilegedAction<Method[]>(){
                @Override
                public Method[] run(){
                    return clazz.getDeclaredMethods();
                }
            });
        } else {
            methods = clazz.getDeclaredMethods();
        }
        return methods;
    }


    /**
     * 使用Container的类加载器尝试加载类.
     * 如果类不能被加载, 一个debug 等级的日志消息将被写入到 Container的日志, 并将返回 null.
     * @param context 此上下文的类加载器将用于尝试加载类
     * @param className 类名
     * @return 加载的类或 <code>null</code>
     */
    public static Class<?> loadClass(Context context, String className) {
        ClassLoader cl = context.getLoader().getClassLoader();
        Log log = context.getLogger();
        Class<?> clazz = null;
        try {
            clazz = cl.loadClass(className);
        } catch (ClassNotFoundException | NoClassDefFoundError | ClassFormatError e) {
            log.debug(sm.getString("introspection.classLoadFailed", className), e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.debug(sm.getString("introspection.classLoadFailed", className), t);
        }
        return clazz;
    }

    /**
     * 转换原始的类型到它对应的封装类型.
     *
     * @param clazz 要转换的Class
     * @return 如果参数是原始的类型，则返回其封装类型; 否则返回相同的 class
     */
    public static Class<?> convertPrimitiveType(Class<?> clazz) {
        if (clazz.equals(char.class)) {
            return Character.class;
        } else if (clazz.equals(int.class)) {
            return Integer.class;
        } else if (clazz.equals(boolean.class)) {
            return Boolean.class;
        } else if (clazz.equals(double.class)) {
            return Double.class;
        } else if (clazz.equals(byte.class)) {
            return Byte.class;
        } else if (clazz.equals(short.class)) {
            return Short.class;
        } else if (clazz.equals(long.class)) {
            return Long.class;
        } else if (clazz.equals(float.class)) {
            return Float.class;
        } else {
            return clazz;
        }
    }
}
