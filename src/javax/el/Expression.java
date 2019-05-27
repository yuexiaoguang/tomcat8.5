package javax.el;

import java.io.Serializable;

public abstract class Expression implements Serializable {

    private static final long serialVersionUID = -6663767980471823812L;

    public abstract String getExpressionString();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public abstract boolean isLiteralText();

}
