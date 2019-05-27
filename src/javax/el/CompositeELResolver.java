package javax.el;

import java.beans.FeatureDescriptor;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class CompositeELResolver extends ELResolver {

    private static final Class<?> SCOPED_ATTRIBUTE_EL_RESOLVER;
    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("javax.servlet.jsp.el.ScopedAttributeELResolver");
        } catch (ClassNotFoundException e) {
            // 忽略. 如果单独使用EL的话，这是预料之中的
        }
        SCOPED_ATTRIBUTE_EL_RESOLVER = clazz;
    }

    private int size;

    private ELResolver[] resolvers;

    public CompositeELResolver() {
        this.size = 0;
        this.resolvers = new ELResolver[8];
    }

    public void add(ELResolver elResolver) {
        Objects.requireNonNull(elResolver);

        if (this.size >= this.resolvers.length) {
            ELResolver[] nr = new ELResolver[this.size * 2];
            System.arraycopy(this.resolvers, 0, nr, 0, this.size);
            this.resolvers = nr;
        }
        this.resolvers[this.size++] = elResolver;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        context.setPropertyResolved(false);
        int sz = this.size;
        for (int i = 0; i < sz; i++) {
            Object result = this.resolvers[i].getValue(context, base, property);
            if (context.isPropertyResolved()) {
                return result;
            }
        }
        return null;
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method,
            Class<?>[] paramTypes, Object[] params) {
        context.setPropertyResolved(false);
        int sz = this.size;
        for (int i = 0; i < sz; i++) {
            Object obj = this.resolvers[i].invoke(context, base, method, paramTypes, params);
            if (context.isPropertyResolved()) {
                return obj;
            }
        }
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        context.setPropertyResolved(false);
        int sz = this.size;
        for (int i = 0; i < sz; i++) {
            Class<?> type = this.resolvers[i].getType(context, base, property);
            if (context.isPropertyResolved()) {
                if (SCOPED_ATTRIBUTE_EL_RESOLVER != null &&
                        SCOPED_ATTRIBUTE_EL_RESOLVER.isAssignableFrom(resolvers[i].getClass())) {
                    // 特殊情况，由于 javax.servlet.jsp.el.ScopedAttributeELResolver 总是返回 Object.class类型
                    Object value = resolvers[i].getValue(context, base, property);
                    if (value != null) {
                        return value.getClass();
                    }
                }
                return type;
            }
        }
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        context.setPropertyResolved(false);
        int sz = this.size;
        for (int i = 0; i < sz; i++) {
            this.resolvers[i].setValue(context, base, property, value);
            if (context.isPropertyResolved()) {
                return;
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        context.setPropertyResolved(false);
        int sz = this.size;
        for (int i = 0; i < sz; i++) {
            boolean readOnly = this.resolvers[i].isReadOnly(context, base, property);
            if (context.isPropertyResolved()) {
                return readOnly;
            }
        }
        return false;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return new FeatureIterator(context, base, this.resolvers, this.size);
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        Class<?> commonType = null;
        int sz = this.size;
        for (int i = 0; i < sz; i++) {
            Class<?> type = this.resolvers[i].getCommonPropertyType(context, base);
            if (type != null && (commonType == null || commonType.isAssignableFrom(type))) {
                commonType = type;
            }
        }
        return commonType;
    }

    @Override
    public Object convertToType(ELContext context, Object obj, Class<?> type) {
        context.setPropertyResolved(false);
        int sz = this.size;
        for (int i = 0; i < sz; i++) {
            Object result = this.resolvers[i].convertToType(context, obj, type);
            if (context.isPropertyResolved()) {
                return result;
            }
        }
        return null;
    }

    private static final class FeatureIterator implements Iterator<FeatureDescriptor> {

        private final ELContext context;

        private final Object base;

        private final ELResolver[] resolvers;

        private final int size;

        private Iterator<FeatureDescriptor> itr;

        private int idx;

        private FeatureDescriptor next;

        public FeatureIterator(ELContext context, Object base, ELResolver[] resolvers, int size) {
            this.context = context;
            this.base = base;
            this.resolvers = resolvers;
            this.size = size;

            this.idx = 0;
            this.guaranteeIterator();
        }

        private void guaranteeIterator() {
            while (this.itr == null && this.idx < this.size) {
                this.itr = this.resolvers[this.idx].getFeatureDescriptors(this.context, this.base);
                this.idx++;
            }
        }

        @Override
        public boolean hasNext() {
            if (this.next != null)
                return true;
            if (this.itr != null) {
                while (this.next == null && itr.hasNext()) {
                    this.next = itr.next();
                }
            } else {
                return false;
            }
            if (this.next == null) {
                this.itr = null;
                this.guaranteeIterator();
            }
            return hasNext();
        }

        @Override
        public FeatureDescriptor next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            FeatureDescriptor result = this.next;
            this.next = null;
            return result;

        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
