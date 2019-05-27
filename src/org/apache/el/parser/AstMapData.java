package org.apache.el.parser;

import java.util.HashMap;
import java.util.Map;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public class AstMapData extends SimpleNode {

    public AstMapData(int id) {
        super(id);
    }


    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        Map<Object,Object> result = new HashMap<>();

        if (children != null) {
            for (Node child : children) {
                AstMapEntry mapEntry = (AstMapEntry) child;
                Object key = mapEntry.children[0].getValue(ctx);
                Object value = mapEntry.children[1].getValue(ctx);
                result.put(key, value);
            }
        }

        return result;
    }


    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        return Map.class;
    }
}
/* JavaCC - OriginalChecksum=a68b5c6f0a0708f478fdf8c0e6e1263e (do not edit this line) */
