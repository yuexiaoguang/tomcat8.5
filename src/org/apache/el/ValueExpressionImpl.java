package org.apache.el;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.FunctionMapper;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;
import javax.el.ValueExpression;
import javax.el.ValueReference;
import javax.el.VariableMapper;

import org.apache.el.lang.EvaluationContext;
import org.apache.el.lang.ExpressionBuilder;
import org.apache.el.parser.AstLiteralExpression;
import org.apache.el.parser.Node;
import org.apache.el.util.ReflectionUtil;


/**
 * 可以获取或设置的<code>Expression</code>.
 *
 * <p>
 * 在之前的 API 中, 表达式只能读取. <code>ValueExpression</code> 对象既可以检索值也可以设置它.
 * </p>
 */
public final class ValueExpressionImpl extends ValueExpression implements
        Externalizable {

    private Class<?> expectedType;

    private String expr;

    private FunctionMapper fnMapper;

    private VariableMapper varMapper;

    private transient Node node;

    public ValueExpressionImpl() {
        super();
    }

    public ValueExpressionImpl(String expr, Node node, FunctionMapper fnMapper,
            VariableMapper varMapper, Class<?> expectedType) {
        this.expr = expr;
        this.node = node;
        this.fnMapper = fnMapper;
        this.varMapper = varMapper;
        this.expectedType = expectedType;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ValueExpressionImpl && obj.hashCode() == this
                .hashCode());
    }

    @Override
    public Class<?> getExpectedType() {
        return this.expectedType;
    }

    /**
     * 返回强制的表达式的结果的类型.
     *
     * @return 传递给创建这个<code>ValueExpression</code>的
     *         <code>ExpressionFactory.createValueExpression</code>方法的<code>expectedType</code>.
     */
    @Override
    public String getExpressionString() {
        return this.expr;
    }

    private Node getNode() throws ELException {
        if (this.node == null) {
            this.node = ExpressionBuilder.createNode(this.expr);
        }
        return this.node;
    }

    @Override
    public Class<?> getType(ELContext context) throws PropertyNotFoundException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        Class<?> result = this.getNode().getType(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public Object getValue(ELContext context) throws PropertyNotFoundException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        Object value = this.getNode().getValue(ctx);
        if (this.expectedType != null) {
            value = context.convertToType(value, this.expectedType);
        }
        context.notifyAfterEvaluation(getExpressionString());
        return value;
    }

    @Override
    public int hashCode() {
        return this.getNode().hashCode();
    }

    @Override
    public boolean isLiteralText() {
        try {
            return this.getNode() instanceof AstLiteralExpression;
        } catch (ELException ele) {
            return false;
        }
    }

    @Override
    public boolean isReadOnly(ELContext context)
            throws PropertyNotFoundException, ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        boolean result = this.getNode().isReadOnly(ctx);
        context.notifyAfterEvaluation(getExpressionString());
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
        this.fnMapper = (FunctionMapper) in.readObject();
        this.varMapper = (VariableMapper) in.readObject();
    }

    @Override
    public void setValue(ELContext context, Object value)
            throws PropertyNotFoundException, PropertyNotWritableException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        this.getNode().setValue(ctx, value);
        context.notifyAfterEvaluation(getExpressionString());
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.expr);
        out.writeUTF((this.expectedType != null) ? this.expectedType.getName()
                : "");
        out.writeObject(this.fnMapper);
        out.writeObject(this.varMapper);
    }

    @Override
    public String toString() {
        return "ValueExpression["+this.expr+"]";
    }

    /**
     * @since EL 2.2
     */
    @Override
    public ValueReference getValueReference(ELContext context) {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        ValueReference result = this.getNode().getValueReference(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }
}
