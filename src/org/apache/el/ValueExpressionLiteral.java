package org.apache.el;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.el.ELContext;
import javax.el.PropertyNotWritableException;
import javax.el.ValueExpression;

import org.apache.el.util.MessageFactory;
import org.apache.el.util.ReflectionUtil;


public final class ValueExpressionLiteral extends ValueExpression implements
        Externalizable {

    private static final long serialVersionUID = 1L;

    private Object value;
    private String valueString;

    private Class<?> expectedType;

    public ValueExpressionLiteral() {
        super();
    }

    public ValueExpressionLiteral(Object value, Class<?> expectedType) {
        this.value = value;
        this.expectedType = expectedType;
    }

    @Override
    public Object getValue(ELContext context) {
        context.notifyBeforeEvaluation(getExpressionString());
        Object result;
        if (this.expectedType != null) {
            result = context.convertToType(this.value, this.expectedType);
        } else {
            result = this.value;
        }
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public void setValue(ELContext context, Object value) {
        context.notifyBeforeEvaluation(getExpressionString());
        throw new PropertyNotWritableException(MessageFactory.get(
                "error.value.literal.write", this.value));
    }

    @Override
    public boolean isReadOnly(ELContext context) {
        context.notifyBeforeEvaluation(getExpressionString());
        context.notifyAfterEvaluation(getExpressionString());
        return true;
    }

    @Override
    public Class<?> getType(ELContext context) {
        context.notifyBeforeEvaluation(getExpressionString());
        Class<?> result = (this.value != null) ? this.value.getClass() : null;
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public Class<?> getExpectedType() {
        return this.expectedType;
    }

    @Override
    public String getExpressionString() {
        if (this.valueString == null) {
            this.valueString = (this.value != null) ? this.value.toString() : null;
        }
        return this.valueString;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ValueExpressionLiteral && this
                .equals((ValueExpressionLiteral) obj));
    }

    public boolean equals(ValueExpressionLiteral ve) {
        return (ve != null && (this.value != null && ve.value != null && (this.value == ve.value || this.value
                .equals(ve.value))));
    }

    @Override
    public int hashCode() {
        return (this.value != null) ? this.value.hashCode() : 0;
    }

    @Override
    public boolean isLiteralText() {
        return true;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(this.value);
        out.writeUTF((this.expectedType != null) ? this.expectedType.getName()
                : "");
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.value = in.readObject();
        String type = in.readUTF();
        if (!"".equals(type)) {
            this.expectedType = ReflectionUtil.forName(type);
        }
    }
}
