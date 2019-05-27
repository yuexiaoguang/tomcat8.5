package org.apache.el.stream;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

import javax.el.ELContext;
import javax.el.ELResolver;

public class StreamELResolverImpl extends ELResolver {

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
    public Object invoke(ELContext context, Object base, Object method,
            Class<?>[] paramTypes, Object[] params) {

        if ("stream".equals(method) && params.length == 0) {
            if (base.getClass().isArray()) {
                context.setPropertyResolved(true);
                return new Stream(new ArrayIterator(base));
            } else if (base instanceof Collection) {
                context.setPropertyResolved(true);
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>) base;
                return new Stream(collection.iterator());
            }
        }

        // 不是由这个处理器处理
        return null;
    }


    private static class ArrayIterator implements Iterator<Object> {

        private final Object base;
        private final int size;
        private int index = 0;

        public ArrayIterator(Object base) {
            this.base = base;
            size = Array.getLength(base);
        }

        @Override
        public boolean hasNext() {
            return size > index;
        }

        @Override
        public Object next() {
            return Array.get(base, index++);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
