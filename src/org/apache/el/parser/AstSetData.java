package org.apache.el.parser;

import java.util.HashSet;
import java.util.Set;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public class AstSetData extends SimpleNode {

    public AstSetData(int id) {
        super(id);
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        Set<Object> result = new HashSet<>();

        if (children != null) {
            for (Node child : children) {
                result.add(child.getValue(ctx));
            }
        }

        return result;
    }

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        return Set.class;
    }
}
/* JavaCC - OriginalChecksum=e1dc4e2011eee313491decfa9e0152fe (do not edit this line) */
