package org.apache.el;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.MethodExpression;
import javax.el.MethodInfo;

import org.apache.el.util.ReflectionUtil;


public class MethodExpressionLiteral extends MethodExpression implements Externalizable {

    private Class<?> expectedType;

    private String expr;

    private Class<?>[] paramTypes;

    public MethodExpressionLiteral() {
        // do nothing
    }

    public MethodExpressionLiteral(String expr, Class<?> expectedType,
            Class<?>[] paramTypes) {
        this.expr = expr;
        this.expectedType = expectedType;
        this.paramTypes = paramTypes;
    }

    @Override
    public MethodInfo getMethodInfo(ELContext context) throws ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        MethodInfo result =
                new MethodInfo(this.expr, this.expectedType, this.paramTypes);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public Object invoke(ELContext context, Object[] params) throws ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        Object result;
        if (this.expectedType != null) {
            result = context.convertToType(this.expr, this.expectedType);
        } else {
            result = this.expr;
        }
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public String getExpressionString() {
        return this.expr;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MethodExpressionLiteral && this.hashCode() == obj.hashCode());
    }

    @Override
    public int hashCode() {
        return this.expr.hashCode();
    }

    @Override
    public boolean isLiteralText() {
        return true;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.expr = in.readUTF();
        String type = in.readUTF();
        if (!"".equals(type)) {
            this.expectedType = ReflectionUtil.forName(type);
        }
        this.paramTypes = ReflectionUtil.toTypeArray(((String[]) in
                .readObject()));
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.expr);
        out.writeUTF((this.expectedType != null) ? this.expectedType.getName()
                : "");
        out.writeObject(ReflectionUtil.toTypeNameArray(this.paramTypes));
    }
}
