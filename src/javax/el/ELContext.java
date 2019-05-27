package javax.el;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public abstract class ELContext {

    private Locale locale;

    private Map<Class<?>, Object> map;

    private boolean resolved;

    private ImportHandler importHandler = null;

    private List<EvaluationListener> listeners = new ArrayList<>();

    private Deque<Map<String,Object>> lambdaArguments = new LinkedList<>();

    public ELContext() {
        this.resolved = false;
    }

    public void setPropertyResolved(boolean resolved) {
        this.resolved = resolved;
    }

    /**
     * 标记给定的属性为已解析的并通知任何感兴趣的监听器.
     *
     * @param base     找到属性的基本对象
     * @param property 解析的属性
     */
    public void setPropertyResolved(Object base, Object property) {
        setPropertyResolved(true);
        notifyPropertyResolved(base, property);
    }

    public boolean isPropertyResolved() {
        return this.resolved;
    }

    // 不能使用 Class<?>，因为API 需要符合规范
    /**
     * 在给定的key下向这个EL上下文添加一个对象.
     *
     * @param key           保存对象的key
     * @param contextObject 要添加的对象
     *
     * @throws NullPointerException 如果提供的键或上下文是<code>null</code>
     */
    public void putContext(@SuppressWarnings("rawtypes") Class key, Object contextObject) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(contextObject);

        if (this.map == null) {
            this.map = new HashMap<>();
        }

        this.map.put(key, contextObject);
    }

    // 不能使用 Class<?>，因为API 需要符合规范
    /**
     * 获取给定键的上下文对象.
     *
     * @param key 所需的上下文对象的 key
     *
     * @return 与给定key关联的上下文对象的值
     *
     * @throws NullPointerException 如果提供的键是<code>null</code>
     */
    public Object getContext(@SuppressWarnings("rawtypes") Class key) {
        Objects.requireNonNull(key);
        if (this.map == null) {
            return null;
        }
        return this.map.get(key);
    }

    public abstract ELResolver getELResolver();

    /**
     * 获得这个ELContext的 ImportHandler, 必要时创建一个.
     * 此方法不是线程安全的.
     *
     * @return the ImportHandler for this ELContext.
     */
    public ImportHandler getImportHandler() {
        if (importHandler == null) {
            importHandler = new ImportHandler();
        }
        return importHandler;
    }

    public abstract FunctionMapper getFunctionMapper();

    public Locale getLocale() {
        return this.locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public abstract VariableMapper getVariableMapper();

    /**
     * 注册一个 EvaluationListener.
     *
     * @param listener 要注册的 EvaluationListener
     */
    public void addEvaluationListener(EvaluationListener listener) {
        listeners.add(listener);
    }

    /**
     * 获取所有注册的 EvaluationListeners.
     */
    public List<EvaluationListener> getEvaluationListeners() {
        return listeners;
    }

    /**
     * 通知感兴趣的监听器，将对表达式进行评估.
     *
     * @param expression 将被评估的表达式
     */
    public void notifyBeforeEvaluation(String expression) {
        for (EvaluationListener listener : listeners) {
            try {
                listener.beforeEvaluation(this, expression);
            } catch (Throwable t) {
                Util.handleThrowable(t);
                // Ignore - no option to log
            }
        }
    }

    /**
     * 通知感兴趣的监听器，表达式已经被评估.
     *
     * @param expression 已经被评估的表达式
     */
    public void notifyAfterEvaluation(String expression) {
        for (EvaluationListener listener : listeners) {
            try {
                listener.afterEvaluation(this, expression);
            } catch (Throwable t) {
                Util.handleThrowable(t);
                // Ignore - no option to log
            }
        }
    }

    /**
     * 通知感兴趣的监听器，属性已经被解析.
     *
     * @param base     属性解析的对象
     * @param property 已解析的属性
     */
    public void notifyPropertyResolved(Object base, Object property) {
        for (EvaluationListener listener : listeners) {
            try {
                listener.propertyResolved(this, base, property);
            } catch (Throwable t) {
                Util.handleThrowable(t);
                // Ignore - no option to log
            }
        }
    }

    /**
     * 确定指定的名称是否被识别为lambda参数的名称.
     *
     * @param name lambda参数的名称
     *
     * @return <code>true</code>如果该名称被识别为lambda参数的名称, 否则<code>false</code>
     */
    public boolean isLambdaArgument(String name) {
        for (Map<String,Object> arguments : lambdaArguments) {
            if (arguments.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取给定名称的lambda参数的值.
     *
     * @param name lambda参数的名称
     *
     * @return 指定参数的值
     */
    public Object getLambdaArgument(String name) {
        for (Map<String,Object> arguments : lambdaArguments) {
            Object result = arguments.get(name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * 在开始评估lambda表达式时调用，以便在评估期间参数对EL上下文可用.
     *
     * @param arguments 当前lambda表达式的作用域中的参数.
     */
    public void enterLambdaScope(Map<String,Object> arguments) {
        lambdaArguments.push(arguments);
    }

    /**
     * 在评估lambda表达式后调用，以指示不再需要参数.
     */
    public void exitLambdaScope() {
        lambdaArguments.pop();
    }

    /**
     * 强制提供的对象为所需的类型.
     *
     * @param obj  要被强制的对象
     * @param type 对象应该被强制的类型
     *
     * @return 请求类型的实例.
     *
     * @throws ELException 如果转换失败
     */
    public Object convertToType(Object obj, Class<?> type) {

        boolean originalResolved = isPropertyResolved();
        setPropertyResolved(false);
        try {
            ELResolver resolver = getELResolver();
            if (resolver != null) {
                Object result = resolver.convertToType(this, obj, type);
                if (isPropertyResolved()) {
                    return result;
                }
            }
        } finally {
            setPropertyResolved(originalResolved);
        }

        return ELManager.getExpressionFactory().coerceToType(obj, type);
    }
}
