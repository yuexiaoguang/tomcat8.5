package org.apache.tomcat.dbcp.pool2.impl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;

/**
 * 特定用法实现.
 */
class PoolImplUtils {

    /**
     * 标识对象工厂创建的具体对象类型.
     *
     * @param factory 要检查的工厂
     *
     * @return 工厂创建的对象类型
     */
    @SuppressWarnings("rawtypes")
    static Class<?> getFactoryType(final Class<? extends PooledObjectFactory> factory) {
        return (Class<?>) getGenericType(PooledObjectFactory.class, factory);
    }


    /**
     * 获取使用泛型类型的接口的实现所使用的具体类型.
     *
     * @param type  定义泛型类型的接口
     * @param clazz 实现具体类型接口的类
     * @param <T>   接口类型
     *
     * @return 实现使用的具体类型
     */
    private static <T> Object getGenericType(final Class<T> type,
            final Class<? extends T> clazz) {

        // 查看此类是否实现了通用接口

        // 获取所有接口
        final Type[] interfaces = clazz.getGenericInterfaces();
        for (final Type iface : interfaces) {
            // 只需要检查使用泛型的接口
            if (iface instanceof ParameterizedType) {
                final ParameterizedType pi = (ParameterizedType) iface;
                // 寻找通用接口
                if (pi.getRawType() instanceof Class) {
                    if (type.isAssignableFrom((Class<?>) pi.getRawType())) {
                        return getTypeParameter(
                                clazz, pi.getActualTypeArguments()[0]);
                    }
                }
            }
        }

        // 在此类中未找到接口. 查看父类.
        @SuppressWarnings("unchecked")
        final
        Class<? extends T> superClazz =
                (Class<? extends T>) clazz.getSuperclass();

        final Object result = getGenericType(type, superClazz);
        if (result instanceof Class<?>) {
            // 超类实现接口并为泛型定义显式类型
            return result;
        } else if (result instanceof Integer) {
            // 超类实现接口并为泛型定义未知类型
            // 将未知类型映射到此类中定义的泛型类型
            final ParameterizedType superClassType =
                    (ParameterizedType) clazz.getGenericSuperclass();
            return getTypeParameter(clazz,
                    superClassType.getActualTypeArguments()[
                            ((Integer) result).intValue()]);
        } else {
            // 将在调用堆栈中进一步记录错误
            return null;
        }
    }


    /**
     * 对于通用参数, 返回使用的Class 或类型未知, 类定义中的类型索引
     *
     * @param clazz 定义类
     * @param argType 感兴趣的类型参数
     *
     * @return {@link Class}的实例，表示type参数使用的类型; 或{@link Integer}的实例，表示类定义中的类型的索引
     */
    private static Object getTypeParameter(final Class<?> clazz, final Type argType) {
        if (argType instanceof Class<?>) {
            return argType;
        }
        final TypeVariable<?>[] tvs = clazz.getTypeParameters();
        for (int i = 0; i < tvs.length; i++) {
            if (tvs[i].equals(argType)) {
                return Integer.valueOf(i);
            }
        }
        return null;
    }
}
