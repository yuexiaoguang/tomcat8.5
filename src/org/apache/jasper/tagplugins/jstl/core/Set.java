package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.apache.jasper.tagplugins.jstl.Util;

public class Set implements TagPlugin {

    @Override
    public void doTag(TagPluginContext ctxt) {

        //用于指示属性是否已指定的标志
        boolean hasValue = false, hasVar = false, hasScope = false,
        hasTarget = false;

        //作用域名称
        String strScope;
        //范围的id
        int iScope;

        //initialize the flags
        hasValue = ctxt.isAttributeSpecified("value");
        hasVar = ctxt.isAttributeSpecified("var");
        hasScope = ctxt.isAttributeSpecified("scope");
        hasTarget = ctxt.isAttributeSpecified("target");

        //the temp variables name
        String resultName = ctxt.getTemporaryVariableName();
        String targetName = ctxt.getTemporaryVariableName();
        String propertyName = ctxt.getTemporaryVariableName();

        //初始化"result" 将分配给var或 target.property
        ctxt.generateJavaSource("Object " + resultName + " = null;");
        if(hasValue){
            ctxt.generateJavaSource(resultName + " = ");
            ctxt.generateAttribute("value");
            ctxt.generateJavaSource(";");
        }else{
            ctxt.dontUseTagPlugin();
            return;
        }

        //initialize the strScope
        if(hasScope){
            strScope = ctxt.getConstantAttribute("scope");
        }else{
            strScope = "page";
        }

        //get the iScope according to the strScope
        iScope = Util.getScope(strScope);

        String jspCtxt = null;
        if (ctxt.isTagFile()) {
            jspCtxt = "this.getJspContext()";
        } else {
            jspCtxt = "_jspx_page_context";
        }
        //如果指定了属性var，则将结果分配给var;
        if(hasVar){
            String strVar = ctxt.getConstantAttribute("var");
            ctxt.generateJavaSource("if(null != " + resultName + "){");
            ctxt.generateJavaSource("    " + jspCtxt + ".setAttribute(\"" + strVar + "\"," + resultName + "," + iScope + ");");
            ctxt.generateJavaSource("} else {");
            if(hasScope){
                ctxt.generateJavaSource("    " + jspCtxt + ".removeAttribute(\"" + strVar + "\"," + iScope + ");");
            }else{
                ctxt.generateJavaSource("    " + jspCtxt + ".removeAttribute(\"" + strVar + "\");");
            }
            ctxt.generateJavaSource("}");

            //否则将结果分配给 target.property
        }else if(hasTarget){

            //生成临时变量名
            String pdName = ctxt.getTemporaryVariableName();
            String successFlagName = ctxt.getTemporaryVariableName();
            String index = ctxt.getTemporaryVariableName();
            String methodName = ctxt.getTemporaryVariableName();

            //初始化属性
            ctxt.generateJavaSource("String " + propertyName + " = null;");
            ctxt.generateJavaSource("if(");
            ctxt.generateAttribute("property");
            ctxt.generateJavaSource(" != null){");
            ctxt.generateJavaSource("    " + propertyName + " = (");
            ctxt.generateAttribute("property");
            ctxt.generateJavaSource(").toString();");
            ctxt.generateJavaSource("}");

            //初始化目标
            ctxt.generateJavaSource("Object " + targetName + " = ");
            ctxt.generateAttribute("target");
            ctxt.generateJavaSource(";");

            //目标是 ok
            ctxt.generateJavaSource("if(" + targetName + " != null){");

            //如果目标是一个 map, 然后用key属性将结果放入map
            ctxt.generateJavaSource("    if(" + targetName + " instanceof java.util.Map){");
            ctxt.generateJavaSource("        if(null != " + resultName + "){");
            ctxt.generateJavaSource("            ((java.util.Map) " + targetName + ").put(" + propertyName + "," + resultName + ");");
            ctxt.generateJavaSource("        }else{");
            ctxt.generateJavaSource("            ((java.util.Map) " + targetName + ").remove(" + propertyName + ");");
            ctxt.generateJavaSource("        }");

            //否则将结果分配给target.property
            ctxt.generateJavaSource("    }else{");
            ctxt.generateJavaSource("        try{");

            //获取目标的所有属性
            ctxt.generateJavaSource("            java.beans.PropertyDescriptor " + pdName + "[] = java.beans.Introspector.getBeanInfo(" + targetName + ".getClass()).getPropertyDescriptors();");

            //成功标志意味着赋值是否成功
            ctxt.generateJavaSource("            boolean " + successFlagName + " = false;");

            //找到合适的属性
            ctxt.generateJavaSource("            for(int " + index + "=0;" + index + "<" + pdName + ".length;" + index + "++){");
            ctxt.generateJavaSource("                if(" + pdName + "[" + index + "].getName().equals(" + propertyName + ")){");

            //get the "set" method;
            ctxt.generateJavaSource("                    java.lang.reflect.Method " + methodName + " = " + pdName + "[" + index + "].getWriteMethod();");
            ctxt.generateJavaSource("                    if(null == " + methodName + "){");
            ctxt.generateJavaSource("                        throw new JspException(\"No setter method in &lt;set&gt; for property \"+" + propertyName + ");");
            ctxt.generateJavaSource("                    }");

            //通过反射调用方法
            ctxt.generateJavaSource("                    if(" + resultName + " != null){");
            ctxt.generateJavaSource("                        " + methodName + ".invoke(" + targetName + ", new Object[]{org.apache.el.lang.ELSupport.coerceToType(" + jspCtxt + ".getELContext(), " + resultName + ", " + methodName + ".getParameterTypes()[0])});");
            ctxt.generateJavaSource("                    }else{");
            ctxt.generateJavaSource("                        " + methodName + ".invoke(" + targetName + ", new Object[]{null});");
            ctxt.generateJavaSource("                    }");
            ctxt.generateJavaSource("                    " + successFlagName + " = true;");
            ctxt.generateJavaSource("                }");
            ctxt.generateJavaSource("            }");
            ctxt.generateJavaSource("            if(!" + successFlagName + "){");
            ctxt.generateJavaSource("                throw new JspException(\"Invalid property in &lt;set&gt;:\"+" + propertyName + ");");
            ctxt.generateJavaSource("            }");
            ctxt.generateJavaSource("        }");

            //捕捉EL异常并将其抛出为JspException
            ctxt.generateJavaSource("        catch (IllegalAccessException ex) {");
            ctxt.generateJavaSource("            throw new JspException(ex);");
            ctxt.generateJavaSource("        } catch (java.beans.IntrospectionException ex) {");
            ctxt.generateJavaSource("            throw new JspException(ex);");
            ctxt.generateJavaSource("        } catch (java.lang.reflect.InvocationTargetException ex) {");
            ctxt.generateJavaSource("            if (ex.getCause() instanceof ThreadDeath) {");
            ctxt.generateJavaSource("                throw (ThreadDeath) ex.getCause();");
            ctxt.generateJavaSource("            }");
            ctxt.generateJavaSource("            if (ex.getCause() instanceof VirtualMachineError) {");
            ctxt.generateJavaSource("                throw (VirtualMachineError) ex.getCause();");
            ctxt.generateJavaSource("            }");
            ctxt.generateJavaSource("            throw new JspException(ex);");
            ctxt.generateJavaSource("        }");
            ctxt.generateJavaSource("    }");
            ctxt.generateJavaSource("}else{");
            ctxt.generateJavaSource("    throw new JspException();");
            ctxt.generateJavaSource("}");
        }
    }
}
