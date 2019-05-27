package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstString extends SimpleNode {
    public AstString(int id) {
        super(id);
    }

    private volatile String string;

    public String getString() {
        if (this.string == null) {
            this.string = this.image.substring(1, this.image.length() - 1);
        }
        return this.string;
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        return String.class;
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        return this.getString();
    }

    @Override
    public void setImage(String image) {
        if (image.indexOf('\\') == -1) {
            this.image = image;
            return;
        }
        int size = image.length();
        StringBuilder buf = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            char c = image.charAt(i);
            if (c == '\\' && i + 1 < size) {
                char c1 = image.charAt(i + 1);
                if (c1 == '\\' || c1 == '"' || c1 == '\'') {
                    c = c1;
                    i++;
                }
            }
            buf.append(c);
        }
        this.image = buf.toString();
    }
}
