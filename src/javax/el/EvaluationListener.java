package javax.el;

public abstract class EvaluationListener {

    /**
     * 表达式求值前调用.
     *
     * @param context    将要求值的表达式中的EL 上下文
     * @param expression 将要求值的表达式
     */
    public void beforeEvaluation(ELContext context, String expression) {
        // NO-OP
    }

    /**
     * 表达式求值后调用.
     *
     * @param context    将要求值的表达式中的EL 上下文
     * @param expression 将要求值的表达式
     */
    public void afterEvaluation(ELContext context, String expression) {
        // NO-OP
    }

    /**
     * 属性解析出来后调用.
     *
     * @param context  要解析属性的 EL 上下文
     * @param base     要解析属性的基本对象
     * @param property 要解析的属性
     */
    public void propertyResolved(ELContext context, Object base, Object property) {
        // NO-OP
    }
}
