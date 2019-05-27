package javax.el;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ListELResolver extends ELResolver {

    private final boolean readOnly;

    private static final Class<?> UNMODIFIABLE =
        Collections.unmodifiableList(new ArrayList<>()).getClass();

    public ListELResolver() {
        this.readOnly = false;
    }

    public ListELResolver(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            List<?> list = (List<?>) base;
            int idx = coerce(property);
            if (idx < 0 || idx >= list.size()) {
                throw new PropertyNotFoundException(
                        new ArrayIndexOutOfBoundsException(idx).getMessage());
            }
            return Object.class;
        }

        return null;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            List<?> list = (List<?>) base;
            int idx = coerce(property);
            if (idx < 0 || idx >= list.size()) {
                return null;
            }
            return list.get(idx);
        }

        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property,
            Object value) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            @SuppressWarnings("unchecked") // 必须是Object
            List<Object> list = (List<Object>) base;

            if (this.readOnly) {
                throw new PropertyNotWritableException(Util.message(context,
                        "resolverNotWriteable", base.getClass().getName()));
            }

            int idx = coerce(property);
            try {
                list.set(idx, value);
            } catch (UnsupportedOperationException e) {
                throw new PropertyNotWritableException(e);
            } catch (IndexOutOfBoundsException e) {
                throw new PropertyNotFoundException(e);
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            List<?> list = (List<?>) base;
            try {
                int idx = coerce(property);
                if (idx < 0 || idx >= list.size()) {
                    throw new PropertyNotFoundException(
                            new ArrayIndexOutOfBoundsException(idx)
                                    .getMessage());
                }
            } catch (IllegalArgumentException e) {
                // ignore
            }
            return this.readOnly || UNMODIFIABLE.equals(list.getClass());
        }

        return this.readOnly;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base instanceof List<?>) { // implies base != null
            return Integer.class;
        }
        return null;
    }

    private static final int coerce(Object property) {
        if (property instanceof Number) {
            return ((Number) property).intValue();
        }
        if (property instanceof Character) {
            return ((Character) property).charValue();
        }
        if (property instanceof Boolean) {
            return (((Boolean) property).booleanValue() ? 1 : 0);
        }
        if (property instanceof String) {
            return Integer.parseInt((String) property);
        }
        throw new IllegalArgumentException(property != null ?
                property.toString() : "null");
    }
}
