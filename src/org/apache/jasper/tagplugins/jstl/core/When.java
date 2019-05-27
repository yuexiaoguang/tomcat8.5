package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

public final class When implements TagPlugin {

    @Override
    public void doTag(TagPluginContext ctxt) {
        // 获取父上下文以确定这是否是第一个 <c:when>
        TagPluginContext parentContext = ctxt.getParentContext();
        if (parentContext == null) {
            ctxt.dontUseTagPlugin();
            return;
        }

        if ("true".equals(parentContext.getPluginAttribute("hasBeenHere"))) {
            ctxt.generateJavaSource("} else if(");
            // 由于生成额外的"}"，请参见下面的注释.
        }
        else {
            ctxt.generateJavaSource("if(");
            parentContext.setPluginAttribute("hasBeenHere", "true");
        }
        ctxt.generateAttribute("test");
        ctxt.generateJavaSource("){");
        ctxt.generateBody();

        // 不要在这里生成"if"的关闭的 "}", 因为 <c:when>之间可能有空格. 相反，延迟生成它, 直到下一个<c:when>或<c:otherwise>或<c:choose>
    }
}
