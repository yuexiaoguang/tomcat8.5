package org.apache.el.parser;

import java.util.Collection;
import java.util.Map;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstEmpty extends SimpleNode {
    public AstEmpty(int id) {
        super(id);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        return Boolean.class;
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        Object obj = this.children[0].getValue(ctx);
        if (obj == null) {
            return Boolean.TRUE;
        } else if (obj instanceof String) {
            return Boolean.valueOf(((String) obj).length() == 0);
        } else if (obj instanceof Object[]) {
            return Boolean.valueOf(((Object[]) obj).length == 0);
        } else if (obj instanceof Collection<?>) {
            return Boolean.valueOf(((Collection<?>) obj).isEmpty());
        } else if (obj instanceof Map<?,?>) {
            return Boolean.valueOf(((Map<?,?>) obj).isEmpty());
        }
        return Boolean.FALSE;
    }
}
