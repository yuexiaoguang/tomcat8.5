package org.apache.el.lang;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

import javax.el.ValueExpression;
import javax.el.VariableMapper;

public class VariableMapperImpl extends VariableMapper implements Externalizable {

    private static final long serialVersionUID = 1L;

    private Map<String, ValueExpression> vars = new HashMap<>();

    public VariableMapperImpl() {
        super();
    }

    @Override
    public ValueExpression resolveVariable(String variable) {
        return this.vars.get(variable);
    }

    @Override
    public ValueExpression setVariable(String variable,
            ValueExpression expression) {
        if (expression == null) {
            return vars.remove(variable);
        } else {
            return vars.put(variable, expression);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.vars = (Map<String, ValueExpression>) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(this.vars);
    }
}
