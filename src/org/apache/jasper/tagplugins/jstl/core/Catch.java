package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

public class Catch implements TagPlugin {

    @Override
    public void doTag(TagPluginContext ctxt) {

        //用于var属性存在的标志
        boolean hasVar = ctxt.isAttributeSpecified("var");

        //捕获的异常的临时名称
        String exceptionName = ctxt.getTemporaryVariableName();
        String caughtName = ctxt.getTemporaryVariableName();

        //生成代码的主要部分
        ctxt.generateJavaSource("boolean " + caughtName + " = false;");
        ctxt.generateJavaSource("try{");
        ctxt.generateBody();
        ctxt.generateJavaSource("}");

        //do catch
        ctxt.generateJavaSource("catch(Throwable " + exceptionName + "){");

        //如果指定var, 异常对象应该设置在页面范围内定义的属性 "var"
        if(hasVar){
            String strVar = ctxt.getConstantAttribute("var");
            ctxt.generateJavaSource("    pageContext.setAttribute(\"" + strVar + "\", "
                    + exceptionName + ", PageContext.PAGE_SCOPE);");
        }

        //每当发现异常时, 捕获标志应该设置为 true;
        ctxt.generateJavaSource("    " + caughtName + " = true;");
        ctxt.generateJavaSource("}");

        //do finally
        ctxt.generateJavaSource("finally{");

        //如果指定var, 在页面范围内定义的属性应该被删除
        if(hasVar){
            String strVar = ctxt.getConstantAttribute("var");
            ctxt.generateJavaSource("    if(!" + caughtName + "){");
            ctxt.generateJavaSource("        pageContext.removeAttribute(\"" + strVar + "\", PageContext.PAGE_SCOPE);");
            ctxt.generateJavaSource("    }");
        }
        ctxt.generateJavaSource("}");
    }
}
