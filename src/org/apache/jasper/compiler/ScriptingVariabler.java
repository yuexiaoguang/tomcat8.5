package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.JasperException;

/**
 * 负责确定每个自定义操作需要声明的脚本变量.
 */
class ScriptingVariabler {

    private static final Integer MAX_SCOPE = Integer.valueOf(Integer.MAX_VALUE);

    /*
     * 将标识符(integer类型)分配给每个自定义标签, 为了帮助识别, 对于每个自定义标签, 它需要声明的脚本变量.
     */
    private static class CustomTagCounter extends Node.Visitor {

        private int count;
        private Node.CustomTag parent;

        @Override
        public void visit(Node.CustomTag n) throws JasperException {
            n.setCustomTagParent(parent);
            Node.CustomTag tmpParent = parent;
            parent = n;
            visitBody(n);
            parent = tmpParent;
            n.setNumCount(Integer.valueOf(count++));
        }
    }

    /*
     * 对于每个自定义标签, 确定需要声明的脚本变量. 
     */
    private static class ScriptingVariableVisitor extends Node.Visitor {

        private final ErrorDispatcher err;
        private final Map<String, Integer> scriptVars;

        public ScriptingVariableVisitor(ErrorDispatcher err) {
            this.err = err;
            scriptVars = new HashMap<>();
        }

        @Override
        public void visit(Node.CustomTag n) throws JasperException {
            setScriptingVars(n, VariableInfo.AT_BEGIN);
            setScriptingVars(n, VariableInfo.NESTED);
            visitBody(n);
            setScriptingVars(n, VariableInfo.AT_END);
        }

        private void setScriptingVars(Node.CustomTag n, int scope)
                throws JasperException {

            TagVariableInfo[] tagVarInfos = n.getTagVariableInfos();
            VariableInfo[] varInfos = n.getVariableInfos();
            if (tagVarInfos.length == 0 && varInfos.length == 0) {
                return;
            }

            List<Object> vec = new ArrayList<>();

            Integer ownRange = null;
            Node.CustomTag parent = n.getCustomTagParent();
            if (scope == VariableInfo.AT_BEGIN
                    || scope == VariableInfo.AT_END) {
                if (parent == null)
                    ownRange = MAX_SCOPE;
                else
                    ownRange = parent.getNumCount();
            } else {
                // NESTED
                ownRange = n.getNumCount();
            }

            if (varInfos.length > 0) {
                for (int i=0; i<varInfos.length; i++) {
                    if (varInfos[i].getScope() != scope
                            || !varInfos[i].getDeclare()) {
                        continue;
                    }
                    String varName = varInfos[i].getVarName();

                    Integer currentRange = scriptVars.get(varName);
                    if (currentRange == null ||
                            ownRange.compareTo(currentRange) > 0) {
                        scriptVars.put(varName, ownRange);
                        vec.add(varInfos[i]);
                    }
                }
            } else {
                for (int i=0; i<tagVarInfos.length; i++) {
                    if (tagVarInfos[i].getScope() != scope
                            || !tagVarInfos[i].getDeclare()) {
                        continue;
                    }
                    String varName = tagVarInfos[i].getNameGiven();
                    if (varName == null) {
                        varName = n.getTagData().getAttributeString(
                                        tagVarInfos[i].getNameFromAttribute());
                        if (varName == null) {
                            err.jspError(n,
                                    "jsp.error.scripting.variable.missing_name",
                                    tagVarInfos[i].getNameFromAttribute());
                        }
                    }

                    Integer currentRange = scriptVars.get(varName);
                    if (currentRange == null ||
                            ownRange.compareTo(currentRange) > 0) {
                        scriptVars.put(varName, ownRange);
                        vec.add(tagVarInfos[i]);
                    }
                }
            }

            n.setScriptingVars(vec, scope);
        }
    }

    public static void set(Node.Nodes page, ErrorDispatcher err)
            throws JasperException {
        page.visit(new CustomTagCounter());
        page.visit(new ScriptingVariableVisitor(err));
    }
}
