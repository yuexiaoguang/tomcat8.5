package org.apache.jasper.el;

import java.util.Locale;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

/**
 * Simple ELContextWrapper for runtime evaluation of EL w/ dynamic FunctionMappers
 */
public final class ELContextWrapper extends ELContext {

    private final ELContext target;
    private final FunctionMapper fnMapper;

    public ELContextWrapper(ELContext target, FunctionMapper fnMapper) {
        this.target = target;
        this.fnMapper = fnMapper;
    }

    @Override
    public ELResolver getELResolver() {
        return this.target.getELResolver();
    }

    @Override
    public FunctionMapper getFunctionMapper() {
        if (this.fnMapper != null) return this.fnMapper;
        return this.target.getFunctionMapper();
    }

    @Override
    public VariableMapper getVariableMapper() {
        return this.target.getVariableMapper();
    }

    @Override
    public Object getContext(@SuppressWarnings("rawtypes") Class key) {
        return this.target.getContext(key);
    }

    @Override
    public Locale getLocale() {
        return this.target.getLocale();
    }

    @Override
    public boolean isPropertyResolved() {
        return this.target.isPropertyResolved();
    }

    @Override
    public void putContext(@SuppressWarnings("rawtypes") Class key,
            Object contextObject) throws NullPointerException {
        this.target.putContext(key, contextObject);
    }

    @Override
    public void setLocale(Locale locale) {
        this.target.setLocale(locale);
    }

    @Override
    public void setPropertyResolved(boolean resolved) {
        this.target.setPropertyResolved(resolved);
    }

}
