package org.apache.el;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.MethodInfo;
import javax.el.MethodNotFoundException;
import javax.el.PropertyNotFoundException;
import javax.el.VariableMapper;

import org.apache.el.lang.EvaluationContext;
import org.apache.el.lang.ExpressionBuilder;
import org.apache.el.parser.Node;
import org.apache.el.util.ReflectionUtil;


/**
 * 引用一个对象上的方法的<code>Expression</code>.
 */
public final class MethodExpressionImpl extends MethodExpression implements
        Externalizable {

    private Class<?> expectedType;

    private String expr;

    private FunctionMapper fnMapper;

    private VariableMapper varMapper;

    private transient Node node;

    private Class<?>[] paramTypes;

    public MethodExpressionImpl() {
        super();
    }

    public MethodExpressionImpl(String expr, Node node,
            FunctionMapper fnMapper, VariableMapper varMapper,
            Class<?> expectedType, Class<?>[] paramTypes) {
        super();
        this.expr = expr;
        this.node = node;
        this.fnMapper = fnMapper;
        this.varMapper = varMapper;
        this.expectedType = expectedType;
        this.paramTypes = paramTypes;
    }

    /**
     * <p>
     * 注意两个表达式可以相等, 即便它们的表达式字符串不同.
     * 例如, <code>${fn1:foo()}</code>和<code>${fn2:foo()}</code>相等,
     * 如果它们对应的<code>FunctionMapper</code>映射<code>fn1:foo</code>和<code>fn2:foo</code>到相同的方法.
     * </p>
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MethodExpressionImpl && obj.hashCode() == this
                .hashCode());
    }

    /**
     * 返回用于创建这个<code>Expression</code>的原始字符串.
     *
     * <p>
     * 这用于调试目的，也用于比较目的 (e.g. 确保在配置文件中的表达式没有变化).
     * </p>
     *
     * <p>
     * 这种方法并没有提供足够的信息来重新创建一个表达式. 两个不同的表达式可以具有完全相同的表达式字符串，但函数映射不同.
     * 序列化应用于保存和恢复<code>Expression</code>的状态.
     * </p>
     *
     * @return 原始的表达式字符串.
     */
    @Override
    public String getExpressionString() {
        return this.expr;
    }

    /**
     * 表达式求值相对于提供的上下文, 并返回有关实际引用的方法的信息.
     *
     * @param context 上下文
     * 
     * @return 包含计算的方法的信息的<code>MethodInfo</code>实例.
     * 
     * @throws NullPointerException 如果context 是 <code>null</code>或最后计算的基础对象是<code>null</code>.
     * @throws PropertyNotFoundException 如果指定的变量或属性不存在或不可读.
     * @throws MethodNotFoundException 方法未找到.
     * @throws ELException 如果计算属性或变量过程中抛出异常. 抛出的异常必须包含此异常的原因.
     */
    @Override
    public MethodInfo getMethodInfo(ELContext context)
            throws PropertyNotFoundException, MethodNotFoundException,
            ELException {
        Node n = this.getNode();
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        ctx.notifyBeforeEvaluation(getExpressionString());
        MethodInfo result = n.getMethodInfo(ctx, this.paramTypes);
        ctx.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    private Node getNode() throws ELException {
        if (this.node == null) {
            this.node = ExpressionBuilder.createNode(this.expr);
        }
        return this.node;
    }

    @Override
    public int hashCode() {
        return this.expr.hashCode();
    }

    /**
     * 基于提供的上下文计算表达式, 执行使用应用的参数找到的方法, 并返回执行的结果.
     *
     * @param context 计算的上下文.
     * @param params 传递给方法的参数, 或 <code>null</code>.
     * 
     * @return 方法执行的结果 (<code>null</code> 如果方法的返回类型是<code>void</code>).
     * @throws NullPointerException  如果context 是 <code>null</code>或最后计算的基础对象是<code>null</code>.
     * @throws PropertyNotFoundException 如果指定的变量或属性不存在或不可读.
     * @throws MethodNotFoundException 方法未找到.
     * @throws ELException 如果计算属性或变量过程中抛出异常. 抛出的异常必须包含此异常的原因.
     *             如果抛出的异常是一个 <code>InvocationTargetException</code>, 提取它的<code>cause</code>并将其传递到<code>ELException</code>构造方法.
     */
    @Override
    public Object invoke(ELContext context, Object[] params)
            throws PropertyNotFoundException, MethodNotFoundException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        ctx.notifyBeforeEvaluation(getExpressionString());
        Object result = this.getNode().invoke(ctx, this.paramTypes, params);
        ctx.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.expr = in.readUTF();
        String type = in.readUTF();
        if (!"".equals(type)) {
            this.expectedType = ReflectionUtil.forName(type);
        }
        this.paramTypes = ReflectionUtil.toTypeArray(((String[]) in
                .readObject()));
        this.fnMapper = (FunctionMapper) in.readObject();
        this.varMapper = (VariableMapper) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.expr);
        out.writeUTF((this.expectedType != null) ? this.expectedType.getName()
                : "");
        out.writeObject(ReflectionUtil.toTypeNameArray(this.paramTypes));
        out.writeObject(this.fnMapper);
        out.writeObject(this.varMapper);
    }

    @Override
    public boolean isLiteralText() {
        return false;
    }


    /**
     * @since EL 3.0
     */
    @Override
    public boolean isParametersProvided() {
        return this.getNode().isParametersProvided();
    }

    /**
     * @since EL 2.2
     * Note: 拼写错误是故意的.
     * isParmetersProvided()  - 规范定义
     * isParametersProvided() - 正确的拼写
     */
    @Override
    public boolean isParmetersProvided() {
        return this.getNode().isParametersProvided();
    }

}
