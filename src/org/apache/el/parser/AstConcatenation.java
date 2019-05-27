package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public class AstConcatenation extends SimpleNode {

    public AstConcatenation(int id) {
        super(id);
    }


    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        // 强迫两个子节点为字符串，然后再连接
        String s1 = coerceToString(ctx, children[0].getValue(ctx));
        String s2 = coerceToString(ctx, children[1].getValue(ctx));
        return s1 + s2;
    }


    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        return String.class;
    }
}
/* JavaCC - OriginalChecksum=a95de353974c2c05fa5c7d695a1d50fd (do not edit this line) */
