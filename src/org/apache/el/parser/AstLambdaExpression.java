package org.apache.el.parser;

import java.util.ArrayList;
import java.util.List;

import javax.el.ELException;
import javax.el.LambdaExpression;

import org.apache.el.ValueExpressionImpl;
import org.apache.el.lang.EvaluationContext;
import org.apache.el.util.MessageFactory;

public class AstLambdaExpression extends SimpleNode {

    private NestedState nestedState = null;

    public AstLambdaExpression(int id) {
        super(id);
    }

    @Override
    public Object getValue(EvaluationContext ctx) throws ELException {

        // 正确的计算需要了解整套嵌套表达式, 不只是当前的表达式
        NestedState state = getNestedState();

        // 检查是否有更多的参数.
        int methodParameterSetCount = jjtGetNumChildren() - 2;
        if (methodParameterSetCount > state.getNestingCount()) {
            throw new ELException(MessageFactory.get(
                    "error.lambda.tooManyMethodParameterSets"));
        }

        // 第一个总是参数，即使没有任何参数
        AstLambdaParameters formalParametersNode =
                (AstLambdaParameters) children[0];
        Node[] formalParamNodes = formalParametersNode.children;

        // 第二个是一个值表达式
        ValueExpressionImpl ve = new ValueExpressionImpl("", children[1],
                ctx.getFunctionMapper(), ctx.getVariableMapper(), null);

        // Build a LambdaExpression
        List<String> formalParameters = new ArrayList<>();
        if (formalParamNodes != null) {
            for (Node formalParamNode : formalParamNodes) {
                formalParameters.add(formalParamNode.getImage());
            }
        }
        LambdaExpression le = new LambdaExpression(formalParameters, ve);
        le.setELContext(ctx);

        if (jjtGetNumChildren() == 2) {
            // 无方法参数
            // 只能调用表达式, 如果在嵌套的参数中没有 lambda 表达式
            if (state.getHasFormalParameters()) {
                return le;
            } else {
                return le.invoke(ctx, (Object[]) null);
            }
        }

        /*
         * 这是一个（可能嵌套的）lambda表达式，提供了一个或多个参数.
         *
         * 如果有更多嵌套表达式而不是参数集合，则可以返回 LambdaExpression.
         *
         * 如果有更多的参数集合而不是嵌套表达式, 将在这个方法开始的时候抛出 ELException.
         */

        // 总是要调用最外面的表达式
        int methodParameterIndex = 2;
        Object result = le.invoke(((AstMethodParameters)
                children[methodParameterIndex]).getParameters(ctx));
        methodParameterIndex++;

        while (result instanceof LambdaExpression &&
                methodParameterIndex < jjtGetNumChildren()) {
            result = ((LambdaExpression) result).invoke(((AstMethodParameters)
                    children[methodParameterIndex]).getParameters(ctx));
            methodParameterIndex++;
        }

        return result;
    }


    private NestedState getNestedState() {
        if (nestedState == null) {
            setNestedState(new NestedState());
        }
        return nestedState;
    }


    private void setNestedState(NestedState nestedState) {
        if (this.nestedState != null) {
            // Should never happen
            throw new IllegalStateException("nestedState may only be set once");
        }
        this.nestedState = nestedState;

        // 递增当前表达式的嵌套计数
        nestedState.incrementNestingCount();

        if (jjtGetNumChildren() > 1) {
            Node firstChild = jjtGetChild(0);
            if (firstChild instanceof AstLambdaParameters) {
                if (firstChild.jjtGetNumChildren() > 0) {
                    nestedState.setHasFormalParameters();
                }
            } else {
                // Can't be a lambda expression
                return;
            }
            Node secondChild = jjtGetChild(1);
            if (secondChild instanceof AstLambdaExpression) {
                ((AstLambdaExpression) secondChild).setNestedState(nestedState);
            }
        }
    }


    @Override
    public String toString() {
        // 纯粹用于调试目的.
        StringBuilder result = new StringBuilder();
        for (Node n : children) {
            result.append(n.toString());
        }
        return result.toString();
    }


    private static class NestedState {

        private int nestingCount = 0;
        private boolean hasFormalParameters = false;

        private void incrementNestingCount() {
            nestingCount++;
        }

        private int getNestingCount() {
            return nestingCount;
        }

        private void setHasFormalParameters() {
            hasFormalParameters = true;
        }

        private boolean getHasFormalParameters() {
            return hasFormalParameters;
        }
    }
}
/* JavaCC - OriginalChecksum=071159eff10c8e15ec612c765ae4480a (do not edit this line) */
