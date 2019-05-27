package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstGreaterThan extends BooleanNode {
    public AstGreaterThan(int id) {
        super(id);
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        Object obj0 = this.children[0].getValue(ctx);
        if (obj0 == null) {
            return Boolean.FALSE;
        }
        Object obj1 = this.children[1].getValue(ctx);
        if (obj1 == null) {
            return Boolean.FALSE;
        }
        return (compare(ctx, obj0, obj1) > 0) ? Boolean.TRUE : Boolean.FALSE;
    }
}
