package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstAnd extends BooleanNode {
    public AstAnd(int id) {
        super(id);
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        Object obj = children[0].getValue(ctx);
        Boolean b = coerceToBoolean(ctx, obj, true);
        if (!b.booleanValue()) {
            return b;
        }
        obj = children[1].getValue(ctx);
        b = coerceToBoolean(ctx, obj, true);
        return b;
    }
}
