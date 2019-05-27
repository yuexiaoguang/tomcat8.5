package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

public class ForTokens implements TagPlugin {

    @Override
    public void doTag(TagPluginContext ctxt) {
        boolean hasVar, hasVarStatus, hasBegin, hasEnd, hasStep;

        //init the flags
        hasVar = ctxt.isAttributeSpecified("var");
        hasVarStatus = ctxt.isAttributeSpecified("varStatus");
        hasBegin = ctxt.isAttributeSpecified("begin");
        hasEnd = ctxt.isAttributeSpecified("end");
        hasStep = ctxt.isAttributeSpecified("step");

        if(hasVarStatus){
            ctxt.dontUseTagPlugin();
            return;
        }

        //define all the temp variables' names
        String itemsName = ctxt.getTemporaryVariableName();
        String delimsName = ctxt.getTemporaryVariableName();
        String stName = ctxt.getTemporaryVariableName();
        String beginName = ctxt.getTemporaryVariableName();
        String endName  = ctxt.getTemporaryVariableName();
        String stepName = ctxt.getTemporaryVariableName();
        String index = ctxt.getTemporaryVariableName();
        String temp  = ctxt.getTemporaryVariableName();
        String tokensCountName = ctxt.getTemporaryVariableName();

        //获取"items"属性的值
        ctxt.generateJavaSource("String " + itemsName + " = (String)");
        ctxt.generateAttribute("items");
        ctxt.generateJavaSource(";");

        //获取"delim"属性的值
        ctxt.generateJavaSource("String " + delimsName + " = (String)");
        ctxt.generateAttribute("delims");
        ctxt.generateJavaSource(";");

        //new a StringTokenizer Object according to the "items" and the "delim"
        ctxt.generateJavaSource("java.util.StringTokenizer " + stName + " = " +
                "new java.util.StringTokenizer(" + itemsName + ", " + delimsName + ");");

        //如果指定了"begin", 将令牌移动到 "begin"位置, 并记录开始索引. 默认开始位置是 0.
        ctxt.generateJavaSource("int " + tokensCountName + " = " + stName + ".countTokens();");
        if(hasBegin){
            ctxt.generateJavaSource("int " + beginName + " = "  );
            ctxt.generateAttribute("begin");
            ctxt.generateJavaSource(";");
            ctxt.generateJavaSource("for(int " + index + " = 0; " + index + " < " + beginName + " && " + stName + ".hasMoreTokens(); " + index + "++, " + stName + ".nextToken()){}");
        }else{
            ctxt.generateJavaSource("int " + beginName + " = 0;");
        }

        //如果指定了"end", 如果"end"超过最后一个索引, 记录结束的地方为最终索引, 否则, 记录它为"end";
        //默认结束位置是最后一个索引 
        if(hasEnd){
            ctxt.generateJavaSource("int " + endName + " = 0;"  );
            ctxt.generateJavaSource("if((" + tokensCountName + " - 1) < ");
            ctxt.generateAttribute("end");
            ctxt.generateJavaSource("){");
            ctxt.generateJavaSource("    " + endName + " = " + tokensCountName + " - 1;");
            ctxt.generateJavaSource("}else{");
            ctxt.generateJavaSource("    " + endName + " = ");
            ctxt.generateAttribute("end");
            ctxt.generateJavaSource(";}");
        }else{
            ctxt.generateJavaSource("int " + endName + " = " + tokensCountName + " - 1;");
        }

        //从"step" 获取步骤值. 默认步骤值是 1.
        if(hasStep){
            ctxt.generateJavaSource("int " + stepName + " = "  );
            ctxt.generateAttribute("step");
            ctxt.generateJavaSource(";");
        }else{
            ctxt.generateJavaSource("int " + stepName + " = 1;");
        }

        //the loop
        ctxt.generateJavaSource("for(int " + index + " = " + beginName + "; " + index + " <= " + endName + "; " + index + "++){");
        ctxt.generateJavaSource("    String " + temp + " = " + stName + ".nextToken();");
        ctxt.generateJavaSource("    if(((" + index + " - " + beginName + ") % " + stepName + ") == 0){");
        //如果指定了var, 将当前令牌放入属性"var"中.
        if(hasVar){
            String strVar = ctxt.getConstantAttribute("var");
            ctxt.generateJavaSource("        pageContext.setAttribute(\"" + strVar + "\", " + temp + ");");
        }
        ctxt.generateBody();
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("}");
    }

}
