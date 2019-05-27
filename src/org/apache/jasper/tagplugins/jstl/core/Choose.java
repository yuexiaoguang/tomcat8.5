package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

public final class Choose implements TagPlugin {

    @Override
    public void doTag(TagPluginContext ctxt) {

        // 这里没什么可干的, 大部分工作将在包含标签中完成, <c:when> 和 <c:otherwise>.

        ctxt.generateBody();
        // 查看 When.java 中的注释了解在这里生成 "}"的原因.
        ctxt.generateJavaSource("}");
    }
}
