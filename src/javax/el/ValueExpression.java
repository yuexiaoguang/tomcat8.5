package javax.el;

public abstract class ValueExpression extends Expression {

    private static final long serialVersionUID = 8577809572381654673L;

    /**
     * @param context 这个求值的EL上下文
     *
     * @return 值表达式计算的结果
     *
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果属性/变量解析失败，因为找不到匹配或找到不可读的匹配
     * @throws ELException 在解析属性或变量时包装任何抛出的异常
     */
    public abstract Object getValue(ELContext context);

    /**
     * @param context 这个求值的EL上下文
     * @param value   设置此值表达式引用的属性的值
     *
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果属性/变量解析失败，因为找不到匹配项
     * @throws PropertyNotWritableException 如果属性/变量解析失败，因为找到不可读的匹配
     * @throws ELException 在解析属性或变量时包装任何抛出的异常
     */
    public abstract void setValue(ELContext context, Object value);

    /**
     * @param context 这个求值的EL上下文
     *
     * @return <code>true</code>如果表达式是只读的，否则<code>false</code>
     *
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果属性/变量解析失败，因为找不到匹配或找到不可读的匹配
     * @throws ELException 在解析属性或变量时包装任何抛出的异常
     */
    public abstract boolean isReadOnly(ELContext context);

    /**
     * @param context 这个求值的EL上下文
     *
     * @return 此值表达式结果的类型
     *
     * @throws NullPointerException 如果提供的上下文是<code>null</code>
     * @throws PropertyNotFoundException 如果属性/变量解析失败，因为找不到匹配或找到不可读的匹配
     * @throws ELException 在解析属性或变量时包装任何抛出的异常
     */
    public abstract Class<?> getType(ELContext context);

    public abstract Class<?> getExpectedType();

    /**
     * @param context 这个求值的EL上下文
     *
     * @return 默认实现总是返回<code>null</code>
     */
    public ValueReference getValueReference(ELContext context) {
        // 让实现重写
        context.notifyBeforeEvaluation(getExpressionString());
        context.notifyAfterEvaluation(getExpressionString());
        return null;
    }
}
