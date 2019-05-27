package javax.el;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

public abstract class TypeConverter extends ELResolver {

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property,
            Object value) {
        // NO-OP
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return false;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context,
            Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return null;
    }

    @Override
    public abstract Object convertToType(ELContext context, Object obj,
            Class<?> type);
}
