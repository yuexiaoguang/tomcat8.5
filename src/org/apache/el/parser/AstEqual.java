package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstEqual extends BooleanNode {
    public AstEqual(int id) {
        super(id);
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        Object obj0 = this.children[0].getValue(ctx);
        Object obj1 = this.children[1].getValue(ctx);
        return Boolean.valueOf(equals(ctx, obj0, obj1));
    }
}
