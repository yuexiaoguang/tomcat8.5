package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.jsp.tagext.FunctionInfo;

import org.apache.jasper.JasperException;

/**
 * 这个类定义EL表达式的内部表示
 *
 * 它目前只定义函数. 可以扩展它来定义EL表达式的所有组件, 如果需要.
 */
abstract class ELNode {

    public abstract void accept(Visitor v) throws JasperException;


    /**
     * 表示EL表达式: ${ 和 }中的所有东西.
     */
    public static class Root extends ELNode {

        private final ELNode.Nodes expr;
        private final char type;

        Root(ELNode.Nodes expr, char type) {
            this.expr = expr;
        this.type = type;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public ELNode.Nodes getExpression() {
            return expr;
        }

        public char getType() {
            return type;
        }
    }

    /**
     * 表示EL表达式之外的文本.
     */
    public static class Text extends ELNode {

        private final String text;

        Text(String text) {
            this.text = text;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 表示EL表达式中的任何内容, 其他功能, 包括函数参数等
     */
    public static class ELText extends ELNode {

        private final String text;

        ELText(String text) {
            this.text = text;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 代表一个函数
     * 目前只包含前缀和函数名, 但不是它的参数.
     */
    public static class Function extends ELNode {

        private final String prefix;
        private final String name;
        private final String originalText;
        private String uri;
        private FunctionInfo functionInfo;
        private String methodName;
        private String[] parameters;

        Function(String prefix, String name, String originalText) {
            this.prefix = prefix;
            this.name = name;
            this.originalText = originalText;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        public String getPrefix() {
            return prefix;
        }

        public String getName() {
            return name;
        }

        public String getOriginalText() {
            return originalText;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }

        public void setFunctionInfo(FunctionInfo f) {
            this.functionInfo = f;
        }

        public FunctionInfo getFunctionInfo() {
            return functionInfo;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setParameters(String[] parameters) {
            this.parameters = parameters;
        }

        public String[] getParameters() {
            return parameters;
        }
    }

    /**
     * ELNode的有序列表.
     */
    public static class Nodes {

        /* 用于为EL表达式中的函数创建映射的名称, 和Generator交互.
         */
        private String mapName = null;    // 与EL相关联的功能map
        private final List<ELNode> list;

        public Nodes() {
            list = new ArrayList<>();
        }

        public void add(ELNode en) {
            list.add(en);
        }

        /**
         * 使用提供的访问者访问列表中的节点
         *
         * @param v 使用的访问者
         *
         * @throws JasperException 如果在访问节点时发生错误
         */
        public void visit(Visitor v) throws JasperException {
            Iterator<ELNode> iter = list.iterator();
            while (iter.hasNext()) {
                ELNode n = iter.next();
                n.accept(v);
            }
        }

        public Iterator<ELNode> iterator() {
            return list.iterator();
        }

        public boolean isEmpty() {
            return list.size() == 0;
        }

        /**
         * @return true 如果表达式包含一个 ${...}
         */
        public boolean containsEL() {
            Iterator<ELNode> iter = list.iterator();
            while (iter.hasNext()) {
                ELNode n = iter.next();
                if (n instanceof Root) {
                    return true;
                }
            }
            return false;
        }

        public void setMapName(String name) {
            this.mapName = name;
        }

        public String getMapName() {
            return mapName;
        }

    }

    /*
     * 用于遍历ELNode
     */
    public static class Visitor {

        public void visit(Root n) throws JasperException {
            n.getExpression().visit(this);
        }

        @SuppressWarnings("unused")
        public void visit(Function n) throws JasperException {
            // NOOP by default
        }

        @SuppressWarnings("unused")
        public void visit(Text n) throws JasperException {
            // NOOP by default
        }

        @SuppressWarnings("unused")
        public void visit(ELText n) throws JasperException {
            // NOOP by default
        }
    }
}

