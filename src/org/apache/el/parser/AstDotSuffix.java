package org.apache.el.parser;

import javax.el.ELException;

import org.apache.el.lang.EvaluationContext;
import org.apache.el.util.MessageFactory;
import org.apache.el.util.Validation;

public final class AstDotSuffix extends SimpleNode {
    public AstDotSuffix(int id) {
        super(id);
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        return this.image;
    }

    @Override
    public void setImage(String image) {
        if (!Validation.isIdentifier(image)) {
            throw new ELException(MessageFactory.get("error.identifier.notjava",
                    image));
        }
        this.image = image;
    }
}
