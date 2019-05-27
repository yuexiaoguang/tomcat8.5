package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.ELSupport;
import org.apache.el.lang.EvaluationContext;

public final class AstCompositeExpression extends SimpleNode {

    public AstCompositeExpression(int id) {
        super(id);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        return String.class;
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        StringBuilder sb = new StringBuilder(16);
        Object obj = null;
        if (this.children != null) {
            for (int i = 0; i < this.children.length; i++) {
                obj = this.children[i].getValue(ctx);
                if (obj != null) {
                    sb.append(ELSupport.coerceToString(ctx, obj));
                }
            }
        }
        return sb.toString();
    }
}
