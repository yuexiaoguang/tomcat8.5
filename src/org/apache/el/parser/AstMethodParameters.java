package org.apache.el.parser;

import java.util.ArrayList;

import org.apache.el.lang.EvaluationContext;

public final class AstMethodParameters extends SimpleNode {
    public AstMethodParameters(int id) {
        super(id);
    }

    public Object[] getParameters(EvaluationContext ctx) {
        ArrayList<Object> params = new ArrayList<>();
        for (int i = 0; i < this.jjtGetNumChildren(); i++) {
            params.add(this.jjtGetChild(i).getValue(ctx));
        }
        return params.toArray(new Object[params.size()]);
    }

    @Override
    public String toString() {
        // 纯粹用于调试目的.
        StringBuilder result = new StringBuilder();
        result.append('(');
        if (children != null) {
            for (Node n : children) {
                result.append(n.toString());
                result.append(',');
            }
        }
        result.append(')');
        return result.toString();
    }
}
