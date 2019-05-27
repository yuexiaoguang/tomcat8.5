package org.apache.catalina.ssi;


import java.io.PrintWriter;
import java.text.ParseException;
/**
 * 处理所有条件指令的SSI命令.
 */
public class SSIConditional implements SSICommand {
    @Override
    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer)
            throws SSIStopProcessingException {
        // Assume anything using conditionals was modified by it
        long lastModified = System.currentTimeMillis();
        // 检索当前状态信息
        SSIConditionalState state = ssiMediator.getConditionalState();
        if ("if".equalsIgnoreCase(commandName)) {
            // 如果在一个错误的分支中嵌套，除了计数它什么也不要做
            if (state.processConditionalCommandsOnly) {
                state.nestingCount++;
                return lastModified;
            }
            state.nestingCount = 0;
            // 计算表达式
            if (evaluateArguments(paramNames, paramValues, ssiMediator)) {
                // 没有更多的分支可以采取这一IF块
                state.branchTaken = true;
            } else {
                // Do not process this branch
                state.processConditionalCommandsOnly = true;
                state.branchTaken = false;
            }
        } else if ("elif".equalsIgnoreCase(commandName)) {
            // 如果嵌套在一个错误的分支中，就不需要执行了
            if (state.nestingCount > 0) return lastModified;
            // 如果在这个if块中已经有一个分支，则禁用输出和返回
            if (state.branchTaken) {
                state.processConditionalCommandsOnly = true;
                return lastModified;
            }
            // Evaluate the expression
            if (evaluateArguments(paramNames, paramValues, ssiMediator)) {
                // 返回输出并标记分支
                state.processConditionalCommandsOnly = false;
                state.branchTaken = true;
            } else {
                // Do not process this branch
                state.processConditionalCommandsOnly = true;
                state.branchTaken = false;
            }
        } else if ("else".equalsIgnoreCase(commandName)) {
            // 如果我们嵌套在一个错误的分支中，就不需要执行了
            if (state.nestingCount > 0) return lastModified;
            // 如果已经采取了另一个分支，然后禁用输出，否则启用它.
            state.processConditionalCommandsOnly = state.branchTaken;
            // 在任何情况下，都可以说已经采取了一个分支
            state.branchTaken = true;
        } else if ("endif".equalsIgnoreCase(commandName)) {
            // If we are nested inside a false branch then pop out
            // one level on the nesting count
            if (state.nestingCount > 0) {
                state.nestingCount--;
                return lastModified;
            }
            // Turn output back on
            state.processConditionalCommandsOnly = false;
            // 为任何外部if块重置分支状态, 很明显, 我们从一开始就得到了一个分支
            state.branchTaken = true;
        } else {
            throw new SSIStopProcessingException();
            //throw new SsiCommandException( "Not a conditional command:" +
            // cmdName );
        }
        return lastModified;
    }


    /**
     * 从检索指定的参数表达式的必要的评估步骤.
     */
    private boolean evaluateArguments(String[] names, String[] values,
            SSIMediator ssiMediator) throws SSIStopProcessingException {
        String expr = getExpression(names, values);
        if (expr == null) {
            throw new SSIStopProcessingException();
            //throw new SsiCommandException( "No expression specified." );
        }
        try {
            ExpressionParseTree tree = new ExpressionParseTree(expr,
                    ssiMediator);
            return tree.evaluateTree();
        } catch (ParseException e) {
            //throw new SsiCommandException( "Error parsing expression." );
            throw new SSIStopProcessingException();
        }
    }


    /**
     * 返回"expr"如果参数的名字是合适的, 否则返回null.
     */
    private String getExpression(String[] paramNames, String[] paramValues) {
        if ("expr".equalsIgnoreCase(paramNames[0])) return paramValues[0];
        return null;
    }
}