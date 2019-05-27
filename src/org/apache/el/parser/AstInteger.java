package org.apache.el.parser;

import java.math.BigInteger;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstInteger extends SimpleNode {
    public AstInteger(int id) {
        super(id);
    }

    private volatile Number number;

    protected Number getInteger() {
        if (this.number == null) {
            try {
                this.number = Long.valueOf(this.image);
            } catch (ArithmeticException e1) {
                this.number = new BigInteger(this.image);
            }
        }
        return number;
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        return this.getInteger().getClass();
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        return this.getInteger();
    }
}
