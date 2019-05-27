package org.apache.el.parser;

import javax.el.ELClass;
import javax.el.ELException;
import javax.el.MethodExpression;
import javax.el.MethodInfo;
import javax.el.MethodNotFoundException;
import javax.el.PropertyNotFoundException;
import javax.el.ValueExpression;
import javax.el.ValueReference;
import javax.el.VariableMapper;

import org.apache.el.lang.EvaluationContext;
import org.apache.el.util.MessageFactory;
import org.apache.el.util.Validation;

public final class AstIdentifier extends SimpleNode {
    public AstIdentifier(int id) {
        super(id);
    }

    @Override
    public Class<?> getType(EvaluationContext ctx) throws ELException {
        VariableMapper varMapper = ctx.getVariableMapper();
        if (varMapper != null) {
            ValueExpression expr = varMapper.resolveVariable(this.image);
            if (expr != null) {
                return expr.getType(ctx.getELContext());
            }
        }
        ctx.setPropertyResolved(false);
        Class<?> result = ctx.getELResolver().getType(ctx, null, this.image);
        if (!ctx.isPropertyResolved()) {
            throw new PropertyNotFoundException(MessageFactory.get(
                    "error.resolver.unhandled.null", this.image));
        }
        return result;
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {
        // Lambda parameters
        if (ctx.isLambdaArgument(this.image)) {
            return ctx.getLambdaArgument(this.image);
        }

        // Variable mapper
        VariableMapper varMapper = ctx.getVariableMapper();
        if (varMapper != null) {
            ValueExpression expr = varMapper.resolveVariable(this.image);
            if (expr != null) {
                return expr.getValue(ctx.getELContext());
            }
        }

        // EL Resolvers
        ctx.setPropertyResolved(false);
        Object result;
        /* 将 Boolean 放入 ELContext, 是ScopedAttributeELResolver 性能优化的一部分.
         * 当查找 "foo"时, 解析器无法区分 ${ foo } 和 ${ foo.bar }.
         * 这很重要，因为昂贵的类查找只需要在后面的情况下执行. 这个标志告诉解析器是否跳过查找.
         */
        if (parent instanceof AstValue) {
            ctx.putContext(this.getClass(), Boolean.FALSE);
        } else {
            ctx.putContext(this.getClass(), Boolean.TRUE);
        }
        try {
            result = ctx.getELResolver().getValue(ctx, null, this.image);
        } finally {
            // 总是重置标志为 false, 因此，不适当地应用优化
            ctx.putContext(this.getClass(), Boolean.FALSE);
        }

        if (ctx.isPropertyResolved()) {
            return result;
        }

        // Import
        result = ctx.getImportHandler().resolveClass(this.image);
        if (result != null) {
            return new ELClass((Class<?>) result);
        }
        result = ctx.getImportHandler().resolveStatic(this.image);
        if (result != null) {
            try {
                return ((Class<?>) result).getField(this.image).get(null);
            } catch (IllegalArgumentException | IllegalAccessException
                    | NoSuchFieldException | SecurityException e) {
                throw new ELException(e);
            }
        }

        throw new PropertyNotFoundException(MessageFactory.get(
                "error.resolver.unhandled.null", this.image));
    }

    @Override
    public boolean isReadOnly(EvaluationContext ctx) throws ELException {
        VariableMapper varMapper = ctx.getVariableMapper();
        if (varMapper != null) {
            ValueExpression expr = varMapper.resolveVariable(this.image);
            if (expr != null) {
                return expr.isReadOnly(ctx.getELContext());
            }
        }
        ctx.setPropertyResolved(false);
        boolean result = ctx.getELResolver().isReadOnly(ctx, null, this.image);
        if (!ctx.isPropertyResolved()) {
            throw new PropertyNotFoundException(MessageFactory.get(
                    "error.resolver.unhandled.null", this.image));
        }
        return result;
    }

    @Override
    public void setValue(EvaluationContext ctx, Object value)
            throws ELException {
        VariableMapper varMapper = ctx.getVariableMapper();
        if (varMapper != null) {
            ValueExpression expr = varMapper.resolveVariable(this.image);
            if (expr != null) {
                expr.setValue(ctx.getELContext(), value);
                return;
            }
        }
        ctx.setPropertyResolved(false);
        ctx.getELResolver().setValue(ctx, null, this.image, value);
        if (!ctx.isPropertyResolved()) {
            throw new PropertyNotFoundException(MessageFactory.get(
                    "error.resolver.unhandled.null", this.image));
        }
    }

    @Override
    public Object invoke(EvaluationContext ctx, Class<?>[] paramTypes,
            Object[] paramValues) throws ELException {
        return this.getMethodExpression(ctx).invoke(ctx.getELContext(), paramValues);
    }


    @Override
    public MethodInfo getMethodInfo(EvaluationContext ctx,
            Class<?>[] paramTypes) throws ELException {
        return this.getMethodExpression(ctx).getMethodInfo(ctx.getELContext());
    }

    @Override
    public void setImage(String image) {
        if (!Validation.isIdentifier(image)) {
            throw new ELException(MessageFactory.get("error.identifier.notjava",
                    image));
        }
        this.image = image;
    }


    @Override
    public ValueReference getValueReference(EvaluationContext ctx) {
        VariableMapper varMapper = ctx.getVariableMapper();

        if (varMapper == null) {
            return null;
        }

        ValueExpression expr = varMapper.resolveVariable(this.image);

        if (expr == null) {
            return null;
        }

        return expr.getValueReference(ctx);
    }


    private final MethodExpression getMethodExpression(EvaluationContext ctx)
            throws ELException {
        Object obj = null;

        // case A: ValueExpression 存在, getValue 必须是一个 MethodExpression
        VariableMapper varMapper = ctx.getVariableMapper();
        ValueExpression ve = null;
        if (varMapper != null) {
            ve = varMapper.resolveVariable(this.image);
            if (ve != null) {
                obj = ve.getValue(ctx);
            }
        }

        // case B: 再次验证 ELResolver, 必须是一个可以执行的  MethodExpression
        if (ve == null) {
            ctx.setPropertyResolved(false);
            obj = ctx.getELResolver().getValue(ctx, null, this.image);
        }

        // 最后提供有用的提示
        if (obj instanceof MethodExpression) {
            return (MethodExpression) obj;
        } else if (obj == null) {
            throw new MethodNotFoundException("Identity '" + this.image
                    + "' was null and was unable to invoke");
        } else {
            throw new ELException(
                    "Identity '"
                            + this.image
                            + "' does not reference a MethodExpression instance, returned type: "
                            + obj.getClass().getName());
        }
    }
}