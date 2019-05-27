package javax.el;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MapELResolver extends ELResolver {

    private static final Class<?> UNMODIFIABLE =
            Collections.unmodifiableMap(new HashMap<>()).getClass();

    private final boolean readOnly;

    public MapELResolver() {
        this.readOnly = false;
    }

    public MapELResolver(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?,?>) {
            context.setPropertyResolved(base, property);
            return Object.class;
        }

        return null;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?,?>) {
            context.setPropertyResolved(base, property);
            return ((Map<?,?>) base).get(property);
        }

        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property,
            Object value) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?, ?>) {
            context.setPropertyResolved(base, property);

            if (this.readOnly) {
                throw new PropertyNotWritableException(Util.message(context,
                        "resolverNotWriteable", base.getClass().getName()));
            }

            try {
                @SuppressWarnings("unchecked") // Must be OK
                Map<Object, Object> map = ((Map<Object, Object>) base);
                map.put(property, value);
            } catch (UnsupportedOperationException e) {
                throw new PropertyNotWritableException(e);
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?, ?>) {
            context.setPropertyResolved(base, property);
            return this.readOnly || UNMODIFIABLE.equals(base.getClass());
        }

        return this.readOnly;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        if (base instanceof Map<?, ?>) {
            Iterator<?> itr = ((Map<?, ?>) base).keySet().iterator();
            List<FeatureDescriptor> feats = new ArrayList<>();
            Object key;
            FeatureDescriptor desc;
            while (itr.hasNext()) {
                key = itr.next();
                desc = new FeatureDescriptor();
                desc.setDisplayName(key.toString());
                desc.setShortDescription("");
                desc.setExpert(false);
                desc.setHidden(false);
                desc.setName(key.toString());
                desc.setPreferred(true);
                desc.setValue(RESOLVABLE_AT_DESIGN_TIME, Boolean.TRUE);
                desc.setValue(TYPE, key.getClass());
                feats.add(desc);
            }
            return feats.iterator();
        }
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base instanceof Map<?, ?>) {
            return Object.class;
        }
        return null;
    }

}
