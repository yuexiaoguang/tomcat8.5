package javax.el;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class Util {

    /**
     * 检查提供的Throwable是要重新抛出或忽略.
     * @param t 要检查的Throwable
     */
    static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // 所有其它的Throwable实例将默默忽略
    }


    static String message(ELContext context, String name, Object... props) {
        Locale locale = null;
        if (context != null) {
            locale = context.getLocale();
        }
        if (locale == null) {
            locale = Locale.getDefault();
            if (locale == null) {
                return "";
            }
        }
        ResourceBundle bundle = ResourceBundle.getBundle(
                "javax.el.LocalStrings", locale);
        try {
            String template = bundle.getString(name);
            if (props != null) {
                template = MessageFormat.format(template, props);
            }
            return template;
        } catch (MissingResourceException e) {
            return "Missing Resource: '" + name + "' for Locale " + locale.getDisplayName();
        }
    }


    private static final CacheValue nullTcclFactory = new CacheValue();
    private static final ConcurrentMap<CacheKey, CacheValue> factoryCache =
            new ConcurrentHashMap<>();

    /**
     * 提供ExpressionFactory实例的每个类装入器缓存， 不包括把内存中的任何东西都钉进去，因为那样会触发内存泄漏.
     */
    static ExpressionFactory getExpressionFactory() {

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        CacheValue cacheValue = null;
        ExpressionFactory factory = null;

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
            factory = cacheValue.getExpressionFactory();
        } finally {
            readLock.unlock();
        }

        if (factory == null) {
            final Lock writeLock = cacheValue.getLock().writeLock();
            writeLock.lock();
            try {
                factory = cacheValue.getExpressionFactory();
                if (factory == null) {
                    factory = ExpressionFactory.newInstance();
                    cacheValue.setExpressionFactory(factory);
                }
            } finally {
                writeLock.unlock();
            }
        }

        return factory;
    }


    /**
     * Key used to cache default ExpressionFactory information per class
     * loader. 类加载器永远不会是 {@code null}, 因为 {@code null} tccl 被另外处理.
     */
    private static class CacheKey {
        private final int hash;
        private final WeakReference<ClassLoader> ref;

        public CacheKey(ClassLoader key) {
            hash = key.hashCode();
            ref = new WeakReference<>(key);
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
            ClassLoader thisKey = ref.get();
            if (thisKey == null) {
                return false;
            }
            return thisKey == ((CacheKey) obj).ref.get();
        }
    }

    private static class CacheValue {
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private WeakReference<ExpressionFactory> ref;

        public CacheValue() {
        }

        public ReadWriteLock getLock() {
            return lock;
        }

        public ExpressionFactory getExpressionFactory() {
            return ref != null ? ref.get() : null;
        }

        public void setExpressionFactory(ExpressionFactory factory) {
            ref = new WeakReference<>(factory);
        }
    }


    /*
     * 此方法复制org.apache.el.util.ReflectionUtil 中的代码. 进行更改时，请保持代码同步.
     */
    static Method findMethod(Class<?> clazz, String methodName,
            Class<?>[] paramTypes, Object[] paramValues) {

        if (clazz == null || methodName == null) {
            throw new MethodNotFoundException(
                    message(null, "util.method.notfound", clazz, methodName,
                    paramString(paramTypes)));
        }

        if (paramTypes == null) {
            paramTypes = getTypesFromValues(paramValues);
        }

        Method[] methods = clazz.getMethods();

        List<Wrapper> wrappers = Wrapper.wrap(methods, methodName);

        Wrapper result = findWrapper(
                clazz, wrappers, methodName, paramTypes, paramValues);

        if (result == null) {
            return null;
        }
        return getMethod(clazz, (Method) result.unWrap());
    }

    /*
     * 此方法复制org.apache.el.util.ReflectionUtil 中的代码. 进行更改时，请保持代码同步.
     */
    @SuppressWarnings("null")
    private static Wrapper findWrapper(Class<?> clazz, List<Wrapper> wrappers,
            String name, Class<?>[] paramTypes, Object[] paramValues) {

        Map<Wrapper,MatchResult> candidates = new HashMap<>();

        int paramCount;
        if (paramTypes == null) {
            paramCount = 0;
        } else {
            paramCount = paramTypes.length;
        }

        for (Wrapper w : wrappers) {
            Class<?>[] mParamTypes = w.getParameterTypes();
            int mParamCount;
            if (mParamTypes == null) {
                mParamCount = 0;
            } else {
                mParamCount = mParamTypes.length;
            }

            // 检查参数的个数
            if (!(paramCount == mParamCount ||
                    (w.isVarArgs() && paramCount >= mParamCount))) {
                // Method 的参数个数不正确
                continue;
            }

            // 检查参数匹配
            int exactMatch = 0;
            int assignableMatch = 0;
            int coercibleMatch = 0;
            boolean noMatch = false;
            for (int i = 0; i < mParamCount; i++) {
                // 不能是 null
                if (mParamTypes[i].equals(paramTypes[i])) {
                    exactMatch++;
                } else if (i == (mParamCount - 1) && w.isVarArgs()) {
                    Class<?> varType = mParamTypes[i].getComponentType();
                    for (int j = i; j < paramCount; j++) {
                        if (isAssignableFrom(paramTypes[j], varType)) {
                            assignableMatch++;
                        } else {
                            if (paramValues == null) {
                                noMatch = true;
                                break;
                            } else {
                                if (isCoercibleFrom(paramValues[j], varType)) {
                                    coercibleMatch++;
                                } else {
                                    noMatch = true;
                                    break;
                                }
                            }
                        }
                        // 不要精确匹配一个 varArgs, 它可以导致一个 varArgs 方法匹配，当结果应该是 ambiguous的时候
                    }
                } else if (isAssignableFrom(paramTypes[i], mParamTypes[i])) {
                    assignableMatch++;
                } else {
                    if (paramValues == null) {
                        noMatch = true;
                        break;
                    } else {
                        if (isCoercibleFrom(paramValues[i], mParamTypes[i])) {
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

            // 如果找到每个参数都精确匹配的方法, 返回它
            if (exactMatch == paramCount) {
                return w;
            }

            candidates.put(w, new MatchResult(
                    exactMatch, assignableMatch, coercibleMatch, w.isBridge()));
        }

        // 查找具有类型匹配的参数数量最多的方法
        MatchResult bestMatch = new MatchResult(0, 0, 0, false);
        Wrapper match = null;
        boolean multiple = false;
        for (Map.Entry<Wrapper, MatchResult> entry : candidates.entrySet()) {
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
                // 只有一个参数不是精确匹配的 - 使用父类
                match = resolveAmbiguousWrapper(candidates.keySet(), paramTypes);
            } else {
                match = null;
            }

            if (match == null) {
                // 如果多个方法具有相同的匹配参数数量，则匹配是不明确的，因此抛出异常
                throw new MethodNotFoundException(message(
                        null, "util.method.ambiguous", clazz, name,
                        paramString(paramTypes)));
                }
        }

        // 处理没有匹配的结果的情况
        if (match == null) {
            throw new MethodNotFoundException(message(
                        null, "util.method.notfound", clazz, name,
                        paramString(paramTypes)));
        }

        return match;
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
     * 此方法复制org.apache.el.util.ReflectionUtil 中的代码. 进行更改时，请保持代码同步.
     */
    private static Wrapper resolveAmbiguousWrapper(Set<Wrapper> candidates,
            Class<?>[] paramTypes) {
        // 确定哪个参数不是精确匹配的
        Wrapper w = candidates.iterator().next();

        int nonMatchIndex = 0;
        Class<?> nonMatchClass = null;

        for (int i = 0; i < paramTypes.length; i++) {
            if (w.getParameterTypes()[i] != paramTypes[i]) {
                nonMatchIndex = i;
                nonMatchClass = paramTypes[i];
                break;
            }
        }

        if (nonMatchClass == null) {
            // Null总是模糊的
            return null;
        }

        for (Wrapper c : candidates) {
           if (c.getParameterTypes()[nonMatchIndex] ==
                   paramTypes[nonMatchIndex]) {
               // 方法有不同的非匹配参数，结果是模糊的
               return null;
           }
        }

        // Can't be null
        Class<?> superClass = nonMatchClass.getSuperclass();
        while (superClass != null) {
            for (Wrapper c : candidates) {
                if (c.getParameterTypes()[nonMatchIndex].equals(superClass)) {
                    // Found a match
                    return c;
                }
            }
            superClass = superClass.getSuperclass();
        }

        // 将Number实例作为一个特殊情况
        Wrapper match = null;
        if (Number.class.isAssignableFrom(nonMatchClass)) {
            for (Wrapper c : candidates) {
                Class<?> candidateType = c.getParameterTypes()[nonMatchIndex];
                if (Number.class.isAssignableFrom(candidateType) ||
                        candidateType.isPrimitive()) {
                    if (match == null) {
                        match = c;
                    } else {
                        // 匹配依然模糊
                        match = null;
                        break;
                    }
                }
            }
        }

        return match;
    }


    /*
     * 此方法复制org.apache.el.util.ReflectionUtil 中的代码. 进行更改时，请保持代码同步.
     */
    static boolean isAssignableFrom(Class<?> src, Class<?> target) {
        // src 一直是一个对象
        // Short-cut. null 总是可分配给对象, 在 EL中 null 可以始终强制为一个原始的有效值
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
     * 此方法复制org.apache.el.util.ReflectionUtil 中的代码. 进行更改时，请保持代码同步.
     */
    private static boolean isCoercibleFrom(Object src, Class<?> target) {
        // TODO: 这不漂亮，但很有效. 要避免异常，需要进行重要的重构.
        try {
            getExpressionFactory().coerceToType(src, target);
        } catch (ELException e) {
            return false;
        }
        return true;
    }


    private static Class<?>[] getTypesFromValues(Object[] values) {
        if (values == null) {
            return null;
        }

        Class<?> result[] = new Class<?>[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                result[i] = null;
            } else {
                result[i] = values[i].getClass();
            }
        }
        return result;
    }


    /*
     * 此方法复制org.apache.el.util.ReflectionUtil 中的代码. 进行更改时，请保持代码同步.
     */
    static Method getMethod(Class<?> type, Method m) {
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


    static Constructor<?> findConstructor(Class<?> clazz, Class<?>[] paramTypes,
            Object[] paramValues) {

        String methodName = "<init>";

        if (clazz == null) {
            throw new MethodNotFoundException(
                    message(null, "util.method.notfound", clazz, methodName,
                    paramString(paramTypes)));
        }

        if (paramTypes == null) {
            paramTypes = getTypesFromValues(paramValues);
        }

        Constructor<?>[] constructors = clazz.getConstructors();

        List<Wrapper> wrappers = Wrapper.wrap(constructors);

        Wrapper result = findWrapper(
                clazz, wrappers, methodName, paramTypes, paramValues);

        if (result == null) {
            return null;
        }
        return getConstructor(clazz, (Constructor<?>) result.unWrap());
    }


    static Constructor<?> getConstructor(Class<?> type, Constructor<?> c) {
        if (c == null || Modifier.isPublic(type.getModifiers())) {
            return c;
        }
        Constructor<?> cp = null;
        Class<?> sup = type.getSuperclass();
        if (sup != null) {
            try {
                cp = sup.getConstructor(c.getParameterTypes());
                cp = getConstructor(cp.getDeclaringClass(), cp);
                if (cp != null) {
                    return cp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        return null;
    }


    static Object[] buildParameters(Class<?>[] parameterTypes,
            boolean isVarArgs,Object[] params) {
        ExpressionFactory factory = getExpressionFactory();
        Object[] parameters = null;
        if (parameterTypes.length > 0) {
            parameters = new Object[parameterTypes.length];
            int paramCount = params.length;
            if (isVarArgs) {
                int varArgIndex = parameterTypes.length - 1;
                // 首先 argCount-1 参数是标准的
                for (int i = 0; (i < varArgIndex); i++) {
                    parameters[i] = factory.coerceToType(params[i],
                            parameterTypes[i]);
                }
                // 最后一个参数是 varargs
                Class<?> varArgClass =
                    parameterTypes[varArgIndex].getComponentType();
                final Object varargs = Array.newInstance(
                    varArgClass,
                    (paramCount - varArgIndex));
                for (int i = (varArgIndex); i < paramCount; i++) {
                    Array.set(varargs, i - varArgIndex,
                            factory.coerceToType(params[i], varArgClass));
                }
                parameters[varArgIndex] = varargs;
            } else {
                parameters = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    parameters[i] = factory.coerceToType(params[i],
                            parameterTypes[i]);
                }
            }
        }
        return parameters;
    }


    private abstract static class Wrapper {

        public static List<Wrapper> wrap(Method[] methods, String name) {
            List<Wrapper> result = new ArrayList<>();
            for (Method method : methods) {
                if (method.getName().equals(name)) {
                    result.add(new MethodWrapper(method));
                }
            }
            return result;
        }

        public static List<Wrapper> wrap(Constructor<?>[] constructors) {
            List<Wrapper> result = new ArrayList<>();
            for (Constructor<?> constructor : constructors) {
                result.add(new ConstructorWrapper(constructor));
            }
            return result;
        }

        public abstract Object unWrap();
        public abstract Class<?>[] getParameterTypes();
        public abstract boolean isVarArgs();
        public abstract boolean isBridge();
    }


    private static class MethodWrapper extends Wrapper {
        private final Method m;

        public MethodWrapper(Method m) {
            this.m = m;
        }

        @Override
        public Object unWrap() {
            return m;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return m.getParameterTypes();
        }

        @Override
        public boolean isVarArgs() {
            return m.isVarArgs();
        }

        @Override
        public boolean isBridge() {
            return m.isBridge();
        }
    }

    private static class ConstructorWrapper extends Wrapper {
        private final Constructor<?> c;

        public ConstructorWrapper(Constructor<?> c) {
            this.c = c;
        }

        @Override
        public Object unWrap() {
            return c;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return c.getParameterTypes();
        }

        @Override
        public boolean isVarArgs() {
            return c.isVarArgs();
        }

        @Override
        public boolean isBridge() {
            return false;
        }
    }

    /*
     * 此方法复制org.apache.el.util.ReflectionUtil 中的代码. 进行更改时，请保持代码同步.
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
                        // 桥梁方法的本质就是，只要我们选择一个方法，它实际上并不重要. 即, 无论如何都选择'right'的一个 (the non-bridge one).
                        cmp = Boolean.compare(o.isBridge(), this.isBridge());
                    }
                }
            }
            return cmp;
        }
    }
}
