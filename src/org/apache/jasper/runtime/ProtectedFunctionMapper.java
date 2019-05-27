package org.apache.jasper.runtime;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * EL 函数和它们的Java方法相当. 让 Method 对象是 protected, 因此JSP 页面不能间接做反射.
 */
@SuppressWarnings("deprecation") // Have to support old JSP EL API
public final class ProtectedFunctionMapper extends javax.el.FunctionMapper {

    /**
     * 映射"prefix:name"到 java.lang.Method 对象.
     */
    private HashMap<String,Method> fnmap = null;

    /**
     * 如果map中只有一个函数, 就是这个 Method.
     */
    private Method theMethod = null;

    private ProtectedFunctionMapper() {
    }

    /**
     * 生成的Servlet 和 Tag Handler 实现调用这个方法检索 ProtectedFunctionMapper的实例.
     * 这是必要的，因为生成的代码没有访问此包中类的实例的权限.
     *
     * @return 一个新的受保护函数映射器.
     */
    public static ProtectedFunctionMapper getInstance() {
        ProtectedFunctionMapper funcMapper = new ProtectedFunctionMapper();
        funcMapper.fnmap = new HashMap<>();
        return funcMapper;
    }

    /**
     * 映射给定的EL 函数前缀和名称到给定的Java 方法.
     *
     * @param fnQName EL函数名称(包括前缀)
     * @param c 包含java方法的类
     * @param methodName java方法的名称
     * @param args Java 方法的参数
     * @throws RuntimeException 如果没有找到给定签名的方法.
     */
    public void mapFunction(String fnQName, final Class<?> c,
            final String methodName, final Class<?>[] args) {
        // 如果传入null值，则跳过. 表示通过lambda或ImportHandler添加函数;
        if (fnQName == null) {
            return;
        }
        java.lang.reflect.Method method;
        try {
            method = c.getMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "Invalid function mapping - no such method: "
                            + e.getMessage());
        }

        this.fnmap.put(fnQName, method);
    }

    /**
     * 创建这个类的一个实例, 并保存指定EL函数前缀和名称对应的 Method. 此方法用于EL表达式中只有一个函数时的情况.
     *
     * @param fnQName EL函数限定名(包括前缀)
     * @param c 包含java方法的类
     * @param methodName java方法的名称
     * @param args java方法的参数
     * @throws RuntimeException 如果没有找到给定签名的方法.
     */
    public static ProtectedFunctionMapper getMapForFunction(String fnQName,
            final Class<?> c, final String methodName, final Class<?>[] args) {
        java.lang.reflect.Method method = null;
        ProtectedFunctionMapper funcMapper = new ProtectedFunctionMapper();
        // Skip if null values were passed in. They indicate a function
        // added via a lambda or ImportHandler; nether of which need to be
        // placed in the Map.
        if (fnQName != null) {
            try {
                method = c.getMethod(methodName, args);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(
                        "Invalid function mapping - no such method: "
                                + e.getMessage());
            }
        }
        funcMapper.theMethod = method;
        return funcMapper;
    }

    /**
     * 将指定的本地名称和前缀解析为 Java.lang.Method.
     * 返回null, 如果找不到前缀和本地名.
     * 
     * @param prefix 函数的前缀
     * @param localName 函数名称
     * @return 映射的方法. 或Null.
     */
    @Override
    public Method resolveFunction(String prefix, String localName) {
        if (this.fnmap != null) {
            return this.fnmap.get(prefix + ":" + localName);
        }
        return theMethod;
    }
}
