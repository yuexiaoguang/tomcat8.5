package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.apache.jasper.tagplugins.jstl.Util;

public class Url implements TagPlugin {

    @Override
    public void doTag(TagPluginContext ctxt) {

        //flags
        boolean hasVar, hasContext, hasScope;

        //init flags
        hasVar = ctxt.isAttributeSpecified("var");
        hasContext = ctxt.isAttributeSpecified("context");
        hasScope = ctxt.isAttributeSpecified("scope");

        //定义的临时变量的名字
        String valueName = ctxt.getTemporaryVariableName();
        String contextName = ctxt.getTemporaryVariableName();
        String baseUrlName = ctxt.getTemporaryVariableName();
        String resultName = ctxt.getTemporaryVariableName();
        String responseName = ctxt.getTemporaryVariableName();

        //获取范围
        String strScope = "page";
        if(hasScope){
            strScope = ctxt.getConstantAttribute("scope");
        }
        int iScope = Util.getScope(strScope);

        //get the value
        ctxt.generateJavaSource("String " + valueName + " = ");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource(";");

        //get the context
        ctxt.generateJavaSource("String " + contextName + " = null;");
        if(hasContext){
            ctxt.generateJavaSource(contextName + " = ");
            ctxt.generateAttribute("context");
            ctxt.generateJavaSource(";");
        }

        //get the raw url
        ctxt.generateJavaSource("String " + baseUrlName + " = " +
                "org.apache.jasper.tagplugins.jstl.Util.resolveUrl(" + valueName + ", " + contextName + ", pageContext);");
        ctxt.generateJavaSource("pageContext.setAttribute" +
                "(\"url_without_param\", " + baseUrlName + ");");

        //add params
        ctxt.generateBody();

        ctxt.generateJavaSource("String " + resultName + " = " +
        "(String)pageContext.getAttribute(\"url_without_param\");");
        ctxt.generateJavaSource("pageContext.removeAttribute(\"url_without_param\");");

        //如果url 是相对的, 编码它
        ctxt.generateJavaSource("if(!org.apache.jasper.tagplugins.jstl.Util.isAbsoluteUrl(" + resultName + ")){");
        ctxt.generateJavaSource("    HttpServletResponse " + responseName + " = " +
        "((HttpServletResponse) pageContext.getResponse());");
        ctxt.generateJavaSource("    " + resultName + " = "
                + responseName + ".encodeURL(" + resultName + ");");
        ctxt.generateJavaSource("}");

        //如果指定了"var", url字符串保存到var定义的属性中
        if(hasVar){
            String strVar = ctxt.getConstantAttribute("var");
            ctxt.generateJavaSource("pageContext.setAttribute" +
                    "(\"" + strVar + "\", " + resultName + ", " + iScope + ");");

            //如果未指定var, 只要打印出URL字符串
        }else{
            ctxt.generateJavaSource("try{");
            ctxt.generateJavaSource("    pageContext.getOut().print(" + resultName + ");");
            ctxt.generateJavaSource("}catch(java.io.IOException ex){");
            ctxt.generateJavaSource("    throw new JspTagException(ex.toString(), ex);");
            ctxt.generateJavaSource("}");
        }
    }

}
