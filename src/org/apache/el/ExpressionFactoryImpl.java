package org.apache.el;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.apache.el.lang.ELSupport;
import org.apache.el.lang.ExpressionBuilder;
import org.apache.el.stream.StreamELResolverImpl;
import org.apache.el.util.MessageFactory;

public class ExpressionFactoryImpl extends ExpressionFactory {

    public ExpressionFactoryImpl() {
        super();
    }

    @Override
    public Object coerceToType(Object obj, Class<?> type) {
        return ELSupport.coerceToType(null, obj, type);
    }

    @Override
    public MethodExpression createMethodExpression(ELContext context,
            String expression, Class<?> expectedReturnType,
            Class<?>[] expectedParamTypes) {
        ExpressionBuilder builder = new ExpressionBuilder(expression, context);
        return builder.createMethodExpression(expectedReturnType,
                expectedParamTypes);
    }

    @Override
    public ValueExpression createValueExpression(ELContext context,
            String expression, Class<?> expectedType) {
        if (expectedType == null) {
            throw new NullPointerException(MessageFactory
                    .get("error.value.expectedType"));
        }
        ExpressionBuilder builder = new ExpressionBuilder(expression, context);
        return builder.createValueExpression(expectedType);
    }

    @Override
    public ValueExpression createValueExpression(Object instance,
            Class<?> expectedType) {
        if (expectedType == null) {
            throw new NullPointerException(MessageFactory
                    .get("error.value.expectedType"));
        }
        return new ValueExpressionLiteral(instance, expectedType);
    }

    @Override
    public ELResolver getStreamELResolver() {
        return new StreamELResolverImpl();
    }
}
