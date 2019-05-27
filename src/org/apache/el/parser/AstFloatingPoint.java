package org.apache.el.parser;

import java.math.BigDecimal;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstFloatingPoint extends SimpleNode {
    public AstFloatingPoint(int id) {
        super(id);
    }

    private volatile Number number;

    public Number getFloatingPoint() {
        if (this.number == null) {
            try {
                this.number = Double.valueOf(this.image);
            } catch (ArithmeticException e0) {
                this.number = new BigDecimal(this.image);
            }
        }
        return this.number;
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        return this.getFloatingPoint();
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        return this.getFloatingPoint().getClass();
    }
}
