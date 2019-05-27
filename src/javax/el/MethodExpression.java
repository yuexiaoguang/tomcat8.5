package javax.el;

public abstract class MethodExpression extends Expression {

    private static final long serialVersionUID = 8163925562047324656L;

    /**
     * @param context 求值的EL 上下文
     *
     * @return 这个表达式解析的方法的信息
     *
     * @throws NullPointerException  如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果属性/变量解析失败，因为找不到匹配或找到不可读的匹配
     * @throws MethodNotFoundException 如果找不到匹配方法
     * @throws ELException 在解析属性时包装任何抛出的异常
     */
    public abstract MethodInfo getMethodInfo(ELContext context);

    /**
     * @param context 求值的EL 上下文
     * @param params  执行方法表达式使用的参数
     *
     * @return 调用此方法表达式的结果
     *
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果属性/变量解析失败，因为找不到匹配或找到不可读的匹配
     * @throws MethodNotFoundException 如果找不到匹配方法
     * @throws ELException  包装任何抛出的异常，如果结果的属性或值强制解析为预期的返回类型失败
     */
    public abstract Object invoke(ELContext context, Object[] params);

    /**
     * @return 默认实现总是返回<code>false</code>
     */
    public boolean isParametersProvided() {
        // 让实现重写
        return false;
    }

    /**
     * Note: 拼写错误是故意的.
     * isParmetersProvided()  - 规范定义
     * isParametersProvided() - 修正的拼写
     *
     * @return Always <code>false</code>
     *
     * @deprecated  Use {@link #isParametersProvided()}
     */
    @Deprecated
    public boolean isParmetersProvided() {
        // 让实现重写
        return false;
    }
}
