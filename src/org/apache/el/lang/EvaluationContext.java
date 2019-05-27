package org.apache.el.lang;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.EvaluationListener;
import javax.el.FunctionMapper;
import javax.el.ImportHandler;
import javax.el.VariableMapper;

public final class EvaluationContext extends ELContext {

    private final ELContext elContext;

    private final FunctionMapper fnMapper;

    private final VariableMapper varMapper;

    public EvaluationContext(ELContext elContext, FunctionMapper fnMapper,
            VariableMapper varMapper) {
        this.elContext = elContext;
        this.fnMapper = fnMapper;
        this.varMapper = varMapper;
    }

    public ELContext getELContext() {
        return elContext;
    }

    @Override
    public FunctionMapper getFunctionMapper() {
        return fnMapper;
    }

    @Override
    public VariableMapper getVariableMapper() {
        return varMapper;
    }

    @Override
    // 不能使用 Class<?> , 因为API 需要匹配父类的规范
    public Object getContext(@SuppressWarnings("rawtypes") Class key) {
        return elContext.getContext(key);
    }

    @Override
    public ELResolver getELResolver() {
        return elContext.getELResolver();
    }

    @Override
    public boolean isPropertyResolved() {
        return elContext.isPropertyResolved();
    }

    @Override
    // 不能使用 Class<?> , 因为API 需要匹配父类的规范
    public void putContext(@SuppressWarnings("rawtypes") Class key,
            Object contextObject) {
        elContext.putContext(key, contextObject);
    }

    @Override
    public void setPropertyResolved(boolean resolved) {
        elContext.setPropertyResolved(resolved);
    }

    @Override
    public Locale getLocale() {
        return elContext.getLocale();
        }

    @Override
    public void setLocale(Locale locale) {
        elContext.setLocale(locale);
    }

    @Override
    public void setPropertyResolved(Object base, Object property) {
        elContext.setPropertyResolved(base, property);
    }

    @Override
    public ImportHandler getImportHandler() {
        return elContext.getImportHandler();
    }

    @Override
    public void addEvaluationListener(EvaluationListener listener) {
        elContext.addEvaluationListener(listener);
    }

    @Override
    public List<EvaluationListener> getEvaluationListeners() {
        return elContext.getEvaluationListeners();
    }

    @Override
    public void notifyBeforeEvaluation(String expression) {
        elContext.notifyBeforeEvaluation(expression);
    }

    @Override
    public void notifyAfterEvaluation(String expression) {
        elContext.notifyAfterEvaluation(expression);
    }

    @Override
    public void notifyPropertyResolved(Object base, Object property) {
        elContext.notifyPropertyResolved(base, property);
    }

    @Override
    public boolean isLambdaArgument(String name) {
        return elContext.isLambdaArgument(name);
    }

    @Override
    public Object getLambdaArgument(String name) {
        return elContext.getLambdaArgument(name);
    }

    @Override
    public void enterLambdaScope(Map<String, Object> arguments) {
        elContext.enterLambdaScope(arguments);
    }

    @Override
    public void exitLambdaScope() {
        elContext.exitLambdaScope();
    }

    @Override
    public Object convertToType(Object obj, Class<?> type) {
        return elContext.convertToType(obj, type);
    }
}
