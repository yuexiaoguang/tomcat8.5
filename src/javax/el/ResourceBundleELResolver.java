package javax.el;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

public class ResourceBundleELResolver extends ELResolver {

    public ResourceBundleELResolver() {
        super();
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof ResourceBundle) {
            context.setPropertyResolved(base, property);

            if (property != null) {
                try {
                    return ((ResourceBundle) base).getObject(property
                            .toString());
                } catch (MissingResourceException mre) {
                    return "???" + property.toString() + "???";
                }
            }
        }

        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof ResourceBundle) {
            context.setPropertyResolved(base, property);
        }

        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property,
            Object value) {
        Objects.requireNonNull(context);

        if (base instanceof ResourceBundle) {
            context.setPropertyResolved(base, property);
            throw new PropertyNotWritableException(Util.message(context,
                    "resolverNotWriteable", base.getClass().getName()));
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof ResourceBundle) {
            context.setPropertyResolved(base, property);
            return true;
        }

        return false;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(
            ELContext context, Object base) {
        if (base instanceof ResourceBundle) {
            List<FeatureDescriptor> feats = new ArrayList<>();
            Enumeration<String> e = ((ResourceBundle) base).getKeys();
            FeatureDescriptor feat;
            String key;
            while (e.hasMoreElements()) {
                key = e.nextElement();
                feat = new FeatureDescriptor();
                feat.setDisplayName(key);
                feat.setShortDescription("");
                feat.setExpert(false);
                feat.setHidden(false);
                feat.setName(key);
                feat.setPreferred(true);
                feat.setValue(RESOLVABLE_AT_DESIGN_TIME, Boolean.TRUE);
                feat.setValue(TYPE, String.class);
                feats.add(feat);
            }
            return feats.iterator();
        }
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base instanceof ResourceBundle) {
            return String.class;
        }
        return null;
    }

}
