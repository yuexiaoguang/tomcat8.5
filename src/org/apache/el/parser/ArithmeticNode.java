package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public abstract class ArithmeticNode extends SimpleNode {

    public ArithmeticNode(int i) {
        super(i);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        return Number.class;
    }
}
