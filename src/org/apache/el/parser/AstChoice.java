package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstChoice extends SimpleNode {
    public AstChoice(int id) {
        super(id);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        Object val = this.getValue(ctx);
        return (val != null) ? val.getClass() : null;
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        Object obj0 = this.children[0].getValue(ctx);
        Boolean b0 = coerceToBoolean(ctx, obj0, true);
        return this.children[((b0.booleanValue() ? 1 : 2))].getValue(ctx);
    }
}
