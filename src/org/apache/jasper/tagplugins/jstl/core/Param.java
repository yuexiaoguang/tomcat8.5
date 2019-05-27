package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

public class Param implements TagPlugin {

    @Override
    public void doTag(TagPluginContext ctxt) {

    	//不支持主体内容
        
        //定义所有临时变量的名称
        String nameName = ctxt.getTemporaryVariableName();
        String valueName = ctxt.getTemporaryVariableName();
        String urlName = ctxt.getTemporaryVariableName();
        String encName = ctxt.getTemporaryVariableName();
        String index = ctxt.getTemporaryVariableName();

        //如果param标签没有父级, 抛出异常
        TagPluginContext parent = ctxt.getParentContext();
        if(parent == null){
            ctxt.generateJavaSource(" throw new JspTagException" +
            "(\"&lt;param&gt; outside &lt;import&gt; or &lt;urlEncode&gt;\");");
            return;
        }

        //加上此前获得的URL字符串参数
        ctxt.generateJavaSource("String " + urlName + " = " +
        "(String)pageContext.getAttribute(\"url_without_param\");");

        //获取"name"的值
        ctxt.generateJavaSource("String " + nameName + " = ");
        ctxt.generateAttribute("name");
        ctxt.generateJavaSource(";");

        //如果"name"是 null, 什么都不做.
        //否则添加这样的字符串"name=value"到 url.
        //URL应该被编码
        ctxt.generateJavaSource("if(" + nameName + " != null && !" + nameName + ".equals(\"\")){");
        ctxt.generateJavaSource("    String " + valueName + " = ");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource(";");
        ctxt.generateJavaSource("    if(" + valueName + " == null) " + valueName + " = \"\";");
        ctxt.generateJavaSource("    String " + encName + " = pageContext.getResponse().getCharacterEncoding();");
        ctxt.generateJavaSource("    " + nameName + " = java.net.URLEncoder.encode(" + nameName + ", " + encName + ");");
        ctxt.generateJavaSource("    " + valueName + " = java.net.URLEncoder.encode(" + valueName + ", " + encName + ");");
        ctxt.generateJavaSource("    int " + index + ";");
        ctxt.generateJavaSource("    " + index + " = " + urlName + ".indexOf(\'?\');");
        //如果当前的参数是第一个, 添加一个 "?" 在它的前方, 否则添加一个 "&" 在它的前方
        ctxt.generateJavaSource("    if(" + index + " == -1){");
        ctxt.generateJavaSource("        " + urlName + " = " + urlName + " + \"?\" + " + nameName + " + \"=\" + " + valueName + ";");
        ctxt.generateJavaSource("    }else{");
        ctxt.generateJavaSource("        " + urlName + " = " + urlName + " + \"&\" + " + nameName + " + \"=\" + " + valueName + ";");
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("    pageContext.setAttribute(\"url_without_param\"," + urlName + ");");
        ctxt.generateJavaSource("}");
    }
}
