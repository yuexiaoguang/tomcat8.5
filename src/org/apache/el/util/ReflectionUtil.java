package org.apache.el.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.el.ELException;
import javax.el.MethodNotFoundException;

import org.apache.el.lang.ELSupport;
import org.apache.el.lang.EvaluationContext;


/**
 * 管理序列化和反射的实用程序
 */
public class ReflectionUtil {

    protected static final String[] PRIMITIVE_NAMES = new String[] { "boolean",
            "byte", "char", "double", "float", "int", "long", "short", "void" };

    protected static final Class<?>[] PRIMITIVES = new Class[] { boolean.class,
            byte.class, char.class, double.class, float.class, int.class,
            long.class, short.class, Void.TYPE };

    private ReflectionUtil() {
        super();
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        if (null == name || "".equals(name)) {
            return null;
        }
        Class<?> c = forNamePrimitive(name);
        if (c == null) {
            if (name.endsWith("[]")) {
                String nc = name.substring(0, name.length() - 2);
                c = Class.forName(nc, true, Thread.currentThread().getContextClassLoader());
                c = Array.newInstance(c, 0).getClass();
            } else {
                c = Class.forName(name, true, Thread.currentThread().getContextClassLoader());
            }
        }
        return c;
    }

    protected static Class<?> forNamePrimitive(String name) {
        if (name.length() <= 8) {
            int p = Arrays.binarySearch(PRIMITIVE_NAMES, name);
            if (p >= 0) {
                return PRIMITIVES[p];
            }
        }
        return null;
    }

    /**
     * 转换一组Class 名称到 Class 类型.
     * @param s  类名数组
     * @return 一个Class实例数组，其中结果中索引i处的元素是类的实例，其名称位于输入中的索引i处
     * @throws ClassNotFoundException 如果找不到给定名称的类
     */
    public static Class<?>[] toTypeArray(String[] s) throws ClassNotFoundException {
        if (s == null)
            return null;
        Class<?>[] c = new Class[s.length];
        for (int i = 0; i < s.length; i++) {
            c[i] = forName(s[i]);
        }
        return c;
    }

    /**
     * 将类类型数组转换为类名.
     * @param c 类实例数组
     * @return 一个类名称数组
     */
    public static String[] toTypeNameArray(Class<?>[] c) {
        if (c == null)
            return null;
        String[] s = new String[c.length];
        for (int i = 0; i < c.length; i++) {
            s[i] = c[i].getName();
        }
        return s;
    }

    /**
     * 返回基于条件的方法.
     * @param ctx 表达式的上下文
     * @param base 拥有该方法的对象
     * @param property 方法的名称
     * @param paramTypes 要使用的参数类型
     * @param paramValues 参数值
     * 
     * @return 指定的方法
     * @throws MethodNotFoundException 如果找不到与给定条件匹配的方法
     */
    /*
     * 这个类和javax.el.Util中的一样. 修改时需要同步.
     */
    @SuppressWarnings("null")
    public static Method getMethod(EvaluationContext ctx, Object base, Object property,
            Class<?>[] paramTypes, Object[] paramValues)
            throws MethodNotFoundException {
        if (base == null || property == null) {
            throw new MethodNotFoundException(MessageFactory.get(
                    "error.method.notfound", base, property,
                    paramString(paramTypes)));
        }

        String methodName = (property instanceof String) ? (String) property
                : property.toString();

        int paramCount;
        if (paramTypes == null) {
            paramCount = 0;
        } else {
            paramCount = paramTypes.length;
        }

        Method[] methods = base.getClass().getMethods();
        Map<Method,MatchResult> candidates = new HashMap<>();

        for (Method m : methods) {
            if (!m.getName().equals(methodName)) {
                // 方法名称不匹配
                continue;
            }

            Class<?>[] mParamTypes = m.getParameterTypes();
            int mParamCount;
            if (mParamTypes == null) {
                mParamCount = 0;
            } else {
                mParamCount = mParamTypes.length;
            }

            // 检查参数的数量
            if (!(paramCount == mParamCount ||
                    (m.isVarArgs() && paramCount >= mParamCount))) {
                // 方法有错误的参数数量
                continue;
            }

            // 检查参数匹配
            int exactMatch = 0;
            int assignableMatch = 0;
            int coercibleMatch = 0;
            boolean noMatch = false;
            for (int i = 0; i < mParamCount; i++) {
                // Can't be null
                if (mParamTypes[i].equals(paramTypes[i])) {
                    exactMatch++;
                } else if (i == (mParamCount - 1) && m.isVarArgs()) {
                    Class<?> varType = mParamTypes[i].getComponentType();
                    for (int j = i; j < paramCount; j++) {
                        if (isAssignableFrom(paramTypes[j], varType)) {
                            assignableMatch++;
                        } else {
                            if (paramValues == null || j >= paramValues.length) {
                                noMatch = true;
                                break;
                            } else {
                                if (isCoercibleFrom(ctx, paramValues[j], varType)) {
                                    coercibleMatch++;
                                } else {
                                    noMatch = true;
                                    break;
                                }
                            }
                        }
                        // 不要将varArgs匹配视为完全匹配, 当结果不明确时，它可以导致varArgs方法匹配
                    }
                } else if (isAssignableFrom(paramTypes[i], mParamTypes[i])) {
                    assignableMatch++;
                } else {
                    if (paramValues == null || i >= paramValues.length) {
                        noMatch = true;
                        break;
                    } else {
                        if (isCoercibleFrom(ctx, paramValues[i], mParamTypes[i])) {
                            coercibleMatch++;
                        } else {
                            noMatch = true;
                            break;
                        }
                    }
                }
            }
            if (noMatch) {
                continue;
            }

            // 如果找到一个方法，其中每个参数都完全匹配, 返回它
            if (exactMatch == paramCount) {
                return getMethod(base.getClass(), m);
            }

            candidates.put(m, new MatchResult(
                    exactMatch, assignableMatch, coercibleMatch, m.isBridge()));
        }

        // 查找具有最多类型匹配的参数的方法
        MatchResult bestMatch = new MatchResult(0, 0, 0, false);
        Method match = null;
        boolean multiple = false;
        for (Map.Entry<Method, MatchResult> entry : candidates.entrySet()) {
            int cmp = entry.getValue().compareTo(bestMatch);
            if (cmp > 0 || match == null) {
                bestMatch = entry.getValue();
                match = entry.getKey();
                multiple = false;
            } else if (cmp == 0) {
                multiple = true;
            }
        }
        if (multiple) {
            if (bestMatch.getExact() == paramCount - 1) {
                // 只有一个参数不完全匹配 - 尝试使用超类
                match = resolveAmbiguousMethod(candidates.keySet(), paramTypes);
            } else {
                match = null;
            }

            if (match == null) {
                // 如果多个方法具有相同匹配数量的参数，则匹配不明确，因此抛出异常
                throw new MethodNotFoundException(MessageFactory.get(
                        "error.method.ambiguous", base, property,
                        paramString(paramTypes)));
                }
        }

        // 处理完全找不到匹配的案例
        if (match == null) {
            throw new MethodNotFoundException(MessageFactory.get(
                        "error.method.notfound", base, property,
                        paramString(paramTypes)));
        }

        return getMethod(base.getClass(), match);
    }

    /*
     * 此类复制javax.el.Util中的代码. 进行更改时，请保持代码同步.
     */
    private static Method resolveAmbiguousMethod(Set<Method> candidates,
            Class<?>[] paramTypes) {
        // 确定哪个参数不完全匹配
        Method m = candidates.iterator().next();

        int nonMatchIndex = 0;
        Class<?> nonMatchClass = null;

        for (int i = 0; i < paramTypes.length; i++) {
            if (m.getParameterTypes()[i] != paramTypes[i]) {
                nonMatchIndex = i;
                nonMatchClass = paramTypes[i];
                break;
            }
        }

        if (nonMatchClass == null) {
            // Null 永远是模棱两可的
            return null;
        }

        for (Method c : candidates) {
           if (c.getParameterTypes()[nonMatchIndex] ==
                   paramTypes[nonMatchIndex]) {
               // 方法具有不同的不匹配参数
               // 结果含糊不清
               return null;
           }
        }

        // Can't be null
        Class<?> superClass = nonMatchClass.getSuperclass();
        while (superClass != null) {
            for (Method c : candidates) {
                if (c.getParameterTypes()[nonMatchIndex].equals(superClass)) {
                    // Found a match
                    return c;
                }
            }
            superClass = superClass.getSuperclass();
        }

        // Number 实例是一个特殊情况
        Method match = null;
        if (Number.class.isAssignableFrom(nonMatchClass)) {
            for (Method c : candidates) {
                Class<?> candidateType = c.getParameterTypes()[nonMatchIndex];
                if (Number.class.isAssignableFrom(candidateType) ||
                        candidateType.isPrimitive()) {
                    if (match == null) {
                        match = c;
                    } else {
                        // 匹配仍然不确定
                        match = null;
                        break;
                    }
                }
            }
        }

        return match;
    }


    /*
     * 此类复制javax.el.Util中的代码. 进行更改时，请保持代码同步.
     */
    private static boolean isAssignableFrom(Class<?> src, Class<?> target) {
        // src 总是一个对象
        // Short-cut. null 始终可分配给对象, 而且在EL 中, null 总是可以强制转换为原始的有效值
        if (src == null) {
            return true;
        }

        Class<?> targetClass;
        if (target.isPrimitive()) {
            if (target == Boolean.TYPE) {
                targetClass = Boolean.class;
            } else if (target == Character.TYPE) {
                targetClass = Character.class;
            } else if (target == Byte.TYPE) {
                targetClass = Byte.class;
            } else if (target == Short.TYPE) {
                targetClass = Short.class;
            } else if (target == Integer.TYPE) {
                targetClass = Integer.class;
            } else if (target == Long.TYPE) {
                targetClass = Long.class;
            } else if (target == Float.TYPE) {
                targetClass = Float.class;
            } else {
                targetClass = Double.class;
            }
        } else {
            targetClass = target;
        }
        return targetClass.isAssignableFrom(src);
    }


    /*
     * 此类复制javax.el.Util中的代码. 进行更改时，请保持代码同步.
     */
    private static boolean isCoercibleFrom(EvaluationContext ctx, Object src, Class<?> target) {
        // TODO: 这不漂亮，但它可以工作. 需要进行大量重构以避免异常.
        try {
            ELSupport.coerceToType(ctx, src, target);
        } catch (ELException e) {
            return false;
        }
        return true;
    }


    /*
     * 此类复制javax.el.Util中的代码. 进行更改时，请保持代码同步.
     */
    private static Method getMethod(Class<?> type, Method m) {
        if (m == null || Modifier.isPublic(type.getModifiers())) {
            return m;
        }
        Class<?>[] inf = type.getInterfaces();
        Method mp = null;
        for (int i = 0; i < inf.length; i++) {
            try {
                mp = inf[i].getMethod(m.getName(), m.getParameterTypes());
                mp = getMethod(mp.getDeclaringClass(), mp);
                if (mp != null) {
                    return mp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        Class<?> sup = type.getSuperclass();
        if (sup != null) {
            try {
                mp = sup.getMethod(m.getName(), m.getParameterTypes());
                mp = getMethod(mp.getDeclaringClass(), mp);
                if (mp != null) {
                    return mp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        return null;
    }


    private static final String paramString(Class<?>[] types) {
        if (types != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == null) {
                    sb.append("null, ");
                } else {
                    sb.append(types[i].getName()).append(", ");
                }
            }
            if (sb.length() > 2) {
                sb.setLength(sb.length() - 2);
            }
            return sb.toString();
        }
        return null;
    }

    /*
     * 此类复制javax.el.Util中的代码. 进行更改时，请保持代码同步.
     */
    private static class MatchResult implements Comparable<MatchResult> {

        private final int exact;
        private final int assignable;
        private final int coercible;
        private final boolean bridge;

        public MatchResult(int exact, int assignable, int coercible, boolean bridge) {
            this.exact = exact;
            this.assignable = assignable;
            this.coercible = coercible;
            this.bridge = bridge;
        }

        public int getExact() {
            return exact;
        }

        public int getAssignable() {
            return assignable;
        }

        public int getCoercible() {
            return coercible;
        }

        public boolean isBridge() {
            return bridge;
        }

        @Override
        public int compareTo(MatchResult o) {
            int cmp = Integer.compare(this.getExact(), o.getExact());
            if (cmp == 0) {
                cmp = Integer.compare(this.getAssignable(), o.getAssignable());
                if (cmp == 0) {
                    cmp = Integer.compare(this.getCoercible(), o.getCoercible());
                    if (cmp == 0) {
                        cmp = Boolean.compare(o.isBridge(), this.isBridge());
                    }
                }
            }
            return cmp;
        }
    }

}
