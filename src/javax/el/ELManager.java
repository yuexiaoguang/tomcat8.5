package javax.el;

import java.lang.reflect.Method;
import java.util.Map;

public class ELManager {

    private StandardELContext context = null;

    public static ExpressionFactory getExpressionFactory() {
        return Util.getExpressionFactory();
    }

    public StandardELContext getELContext() {
        if (context == null) {
            context = new StandardELContext(getExpressionFactory());
        }

        return context;
    }

    public ELContext setELContext(ELContext context) {
        StandardELContext oldContext = this.context;
        this.context = new StandardELContext(context);
        return oldContext;
    }

    public void addBeanNameResolver(BeanNameResolver beanNameResolver) {
        getELContext().addELResolver(new BeanNameELResolver(beanNameResolver));
    }

    public void addELResolver(ELResolver resolver) {
        getELContext().addELResolver(resolver);
    }

    public void mapFunction(String prefix, String function, Method method) {
        getELContext().getFunctionMapper().mapFunction(
                prefix, function, method);
    }

    public void setVariable(String variable, ValueExpression expression) {
        getELContext().getVariableMapper().setVariable(variable, expression);
    }

    public void importStatic(String staticMemberName)
            throws javax.el.ELException {
        getELContext().getImportHandler().importStatic(staticMemberName);
    }

    public void importClass(String className) throws javax.el.ELException {
        getELContext().getImportHandler().importClass(className);
    }

    public void importPackage(String packageName) {
        getELContext().getImportHandler().importPackage(packageName);
    }

    public Object defineBean(String name, Object bean) {
        Map<String,Object> localBeans = getELContext().getLocalBeans();

        if (bean == null) {
            return localBeans.remove(name);
        } else {
            return localBeans.put(name, bean);
        }
    }

    public void addEvaluationListener(EvaluationListener listener) {
        getELContext().addEvaluationListener(listener);
    }
}
