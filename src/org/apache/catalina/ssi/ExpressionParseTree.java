package org.apache.catalina.ssi;


import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 表示已解析表达式.
 */
public class ExpressionParseTree {
    /**
     * 包含当前完成的节点集合. 这是解析器的工作区.
     */
    private final LinkedList<Node> nodeStack = new LinkedList<>();
    /**
     * 包含尚未具有值的运算符节点. 这是解析器的工作区.
     */
    private final LinkedList<OppNode> oppStack = new LinkedList<>();
    /**
     * 表达式后的根节点已被解析.
     */
    private Node root;
    /**
     * 计算公式使用的SSIMediator.
     */
    private final SSIMediator ssiMediator;


    /**
     * @param expr 表达式
     * @param ssiMediator 用于计算表达式
     * 
     * @throws ParseException 出现解析错误
     */
    public ExpressionParseTree(String expr, SSIMediator ssiMediator)
            throws ParseException {
        this.ssiMediator = ssiMediator;
        parseExpression(expr);
    }


    /**
     * 计算树. 指定的SSIMediator用于解析变量引用.
     */
    public boolean evaluateTree() {
        return root.evaluate();
    }


    /**
     * 将新操作符推到OPP堆栈上, 解析现有的opps.
     * 
     * @param node 操作符节点
     */
    private void pushOpp(OppNode node) {
        // 如果节点是 null, 那只是一个组标记
        if (node == null) {
            oppStack.add(0, node);
            return;
        }
        while (true) {
            if (oppStack.size() == 0) break;
            OppNode top = oppStack.get(0);
            // 如果顶部是一个间隔，那么不要弹出任何东西
            if (top == null) break;
            // 如果顶部节点的优先级较低，则让它保持
            if (top.getPrecedence() < node.getPrecedence()) break;
            // Remove the top node
            oppStack.remove(0);
            // Let it fill its branches
            top.popValues(nodeStack);
            // Stick it on the resolved node stack
            nodeStack.add(0, top);
        }
        // Add the new node to the opp stack
        oppStack.add(0, node);
    }


    /**
     * 解决所有待定的节点堆栈上的OPP， 直到下一个组标记到达.
     */
    private void resolveGroup() {
        OppNode top = null;
        while ((top = oppStack.remove(0)) != null) {
            // Let it fill its branches
            top.popValues(nodeStack);
            // Stick it on the resolved node stack
            nodeStack.add(0, top);
        }
    }


    /**
     * 将指定表达式解析为解析节点树.
     * 
     * @param expr 要解析的表达式
     * @throws ParseException 出现解析错误
     */
    private void parseExpression(String expr) throws ParseException {
        StringNode currStringNode = null;
        // We cheat a little and start an artificial
        // group right away. It makes finishing easier.
        pushOpp(null);
        ExpressionTokenizer et = new ExpressionTokenizer(expr);
        while (et.hasMoreTokens()) {
            int token = et.nextToken();
            if (token != ExpressionTokenizer.TOKEN_STRING)
                currStringNode = null;
            switch (token) {
                case ExpressionTokenizer.TOKEN_STRING :
                    if (currStringNode == null) {
                        currStringNode = new StringNode(et.getTokenValue());
                        nodeStack.add(0, currStringNode);
                    } else {
                        // Add to the existing
                        currStringNode.value.append(" ");
                        currStringNode.value.append(et.getTokenValue());
                    }
                    break;
                case ExpressionTokenizer.TOKEN_AND :
                    pushOpp(new AndNode());
                    break;
                case ExpressionTokenizer.TOKEN_OR :
                    pushOpp(new OrNode());
                    break;
                case ExpressionTokenizer.TOKEN_NOT :
                    pushOpp(new NotNode());
                    break;
                case ExpressionTokenizer.TOKEN_EQ :
                    pushOpp(new EqualNode());
                    break;
                case ExpressionTokenizer.TOKEN_NOT_EQ :
                    pushOpp(new NotNode());
                    // Sneak the regular node in. The NOT will
                    // be resolved when the next opp comes along.
                    oppStack.add(0, new EqualNode());
                    break;
                case ExpressionTokenizer.TOKEN_RBRACE :
                    // Closeout the current group
                    resolveGroup();
                    break;
                case ExpressionTokenizer.TOKEN_LBRACE :
                    // Push a group marker
                    pushOpp(null);
                    break;
                case ExpressionTokenizer.TOKEN_GE :
                    pushOpp(new NotNode());
                    // Similar strategy to NOT_EQ above, except this
                    // is NOT less than
                    oppStack.add(0, new LessThanNode());
                    break;
                case ExpressionTokenizer.TOKEN_LE :
                    pushOpp(new NotNode());
                    // Similar strategy to NOT_EQ above, except this
                    // is NOT greater than
                    oppStack.add(0, new GreaterThanNode());
                    break;
                case ExpressionTokenizer.TOKEN_GT :
                    pushOpp(new GreaterThanNode());
                    break;
                case ExpressionTokenizer.TOKEN_LT :
                    pushOpp(new LessThanNode());
                    break;
                case ExpressionTokenizer.TOKEN_END :
                    break;
            }
        }
        // Finish off the rest of the opps
        resolveGroup();
        if (nodeStack.size() == 0) {
            throw new ParseException("No nodes created.", et.getIndex());
        }
        if (nodeStack.size() > 1) {
            throw new ParseException("Extra nodes created.", et.getIndex());
        }
        if (oppStack.size() != 0) {
            throw new ParseException("Unused opp nodes exist.", et.getIndex());
        }
        root = nodeStack.get(0);
    }

    /**
     * 表达式解析树中的一个节点.
     */
    private abstract class Node {
        /**
         * 如果节点求值为true，返回true.
         */
        public abstract boolean evaluate();
    }
    /**
     * 表示字符串值的节点
     */
    private class StringNode extends Node {
        StringBuilder value;
        String resolved = null;


        public StringNode(String value) {
            this.value = new StringBuilder(value);
        }


        /**
         * 解析任何变量引用并返回值字符串.
         */
        public String getValue() {
            if (resolved == null)
                resolved = ssiMediator.substituteVariables(value.toString());
            return resolved;
        }


        /**
         * 如果字符串不是空的，则返回true.
         */
        @Override
        public boolean evaluate() {
            return !(getValue().length() == 0);
        }


        @Override
        public String toString() {
            return value.toString();
        }
    }

    private static final int PRECEDENCE_NOT = 5;
    private static final int PRECEDENCE_COMPARE = 4;
    private static final int PRECEDENCE_LOGICAL = 1;

    /**
     * 表示操作的节点实现.
     */
    private abstract class OppNode extends Node {
        /**
         * The left branch.
         */
        Node left;
        /**
         * The right branch.
         */
        Node right;


        /**
         * 返回比其他OppNode优先级合适的优先级.
         */
        public abstract int getPrecedence();


        /**
         * 让节点在指定的列表前面弹出自己的分支节点. 默认放两个.
         *
         * @param values 从中弹出值的列表
         */
        public void popValues(List<Node> values) {
            right = values.remove(0);
            left = values.remove(0);
        }
    }
    private final class NotNode extends OppNode {
        @Override
        public boolean evaluate() {
            return !left.evaluate();
        }


        @Override
        public int getPrecedence() {
            return PRECEDENCE_NOT;
        }


        @Override
        public void popValues(List<Node> values) {
            left = values.remove(0);
        }


        @Override
        public String toString() {
            return left + " NOT";
        }
    }
    private final class AndNode extends OppNode {
        @Override
        public boolean evaluate() {
            if (!left.evaluate()) // Short circuit
                return false;
            return right.evaluate();
        }


        @Override
        public int getPrecedence() {
            return PRECEDENCE_LOGICAL;
        }


        @Override
        public String toString() {
            return left + " " + right + " AND";
        }
    }
    private final class OrNode extends OppNode {
        @Override
        public boolean evaluate() {
            if (left.evaluate()) // Short circuit
                return true;
            return right.evaluate();
        }


        @Override
        public int getPrecedence() {
            return PRECEDENCE_LOGICAL;
        }


        @Override
        public String toString() {
            return left + " " + right + " OR";
        }
    }
    private abstract class CompareNode extends OppNode {
        protected int compareBranches() {
            String val1 = ((StringNode)left).getValue();
            String val2 = ((StringNode)right).getValue();

            int val2Len = val2.length();
            if (val2Len > 1 && val2.charAt(0) == '/' &&
                    val2.charAt(val2Len - 1) == '/') {
                // 作为正则表达式处理
                String expr = val2.substring(1, val2Len - 1);
                try {
                    Pattern pattern = Pattern.compile(expr);
                    // 正则表达式只会和EqualNode一起使用, 相等返回零，不相等返回非零
                    if (pattern.matcher(val1).find()) {
                        return 0;
                    } else {
                        return -1;
                    }
                } catch (PatternSyntaxException pse) {
                    ssiMediator.log("Invalid expression: " + expr, pse);
                    return 0;
                }
            }
            return val1.compareTo(val2);
        }
    }
    private final class EqualNode extends CompareNode {
        @Override
        public boolean evaluate() {
            return (compareBranches() == 0);
        }


        @Override
        public int getPrecedence() {
            return PRECEDENCE_COMPARE;
        }


        @Override
        public String toString() {
            return left + " " + right + " EQ";
        }
    }
    private final class GreaterThanNode extends CompareNode {
        @Override
        public boolean evaluate() {
            return (compareBranches() > 0);
        }


        @Override
        public int getPrecedence() {
            return PRECEDENCE_COMPARE;
        }


        @Override
        public String toString() {
            return left + " " + right + " GT";
        }
    }
    private final class LessThanNode extends CompareNode {
        @Override
        public boolean evaluate() {
            return (compareBranches() < 0);
        }


        @Override
        public int getPrecedence() {
            return PRECEDENCE_COMPARE;
        }


        @Override
        public String toString() {
            return left + " " + right + " LT";
        }
    }
}