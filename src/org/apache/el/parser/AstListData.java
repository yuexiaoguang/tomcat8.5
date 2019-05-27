package org.apache.el.parser;

import java.util.ArrayList;
import java.util.List;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public class AstListData extends SimpleNode {
    public AstListData(int id) {
        super(id);
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        List<Object> result = new ArrayList<>();

        if (children != null) {
            for (Node child : children) {
                result.add(child.getValue(ctx));
            }
        }

        return result;
    }

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        return List.class;
    }
}
/* JavaCC - OriginalChecksum=7f2694086a9ba64558ee39d1cd719db1 (do not edit this line) */
