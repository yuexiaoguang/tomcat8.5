package org.apache.el.stream;

import javax.el.ELException;
import javax.el.LambdaExpression;

import org.apache.el.util.MessageFactory;

public class Optional {

    private final Object obj;

    static final Optional EMPTY = new Optional(null);

    Optional(Object obj) {
        this.obj = obj;
    }


    public Object get() throws ELException {
        if (obj == null) {
            throw new ELException(MessageFactory.get("stream.optional.empty"));
        } else {
            return obj;
        }
    }


    public void ifPresent(LambdaExpression le) {
        if (obj != null) {
            le.invoke(obj);
        }
    }


    public Object orElse(Object other) {
        if (obj == null) {
            return other;
        } else {
            return obj;
        }
    }


    public Object orElseGet(Object le) {
        if (obj == null) {
            // EL 3.0 规范要求参数是 LambdaExpression, 但它可能已经被评估过了.
            // 如果是这种情况, 原始参数将在评估之前检查, 来确保它是一个 LambdaExpression.

            if (le instanceof LambdaExpression) {
                return ((LambdaExpression) le).invoke((Object[]) null);
            } else {
                return le;
            }
        } else {
            return obj;
        }
    }
}
