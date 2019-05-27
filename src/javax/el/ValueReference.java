package javax.el;

import java.io.Serializable;

public class ValueReference implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Object base;
    private final Object property;

    public ValueReference(Object base, Object property) {
        this.base = base;
        this.property = property;
    }

    public Object getBase() {
        return base;
    }

    public Object getProperty() {
        return property;
    }
}
