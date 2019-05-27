package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;

public final class AstLiteralExpression extends SimpleNode {
    public AstLiteralExpression(int id) {
        super(id);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        return String.class;
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        return this.image;
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
            if (c == '\\' && i + 2 < size) {
                char c1 = image.charAt(i + 1);
                char c2 = image.charAt(i + 2);
                if ((c1 == '#' || c1 == '$') && c2 == '{')  {
                    c = c1;
                    i++;
                }
            }
            buf.append(c);
        }
        this.image = buf.toString();
    }
}
