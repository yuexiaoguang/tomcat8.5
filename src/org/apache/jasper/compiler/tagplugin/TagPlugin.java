package org.apache.jasper.compiler.tagplugin;

/**
 * 此接口将由插件作者实现, 提供标记处理程序的另一个实现. 它可用于指定标签被调用时生成的Java代码.
 *
 * 此接口的实现必须在WEB-INF目录下的"tagPlugins.xml"文件中注册.
 */
public interface TagPlugin {

    /**
     * 生成自定义标签的代码.
     * @param ctxt 访问Jasper函数的TagPluginContext
     */
    void doTag(TagPluginContext ctxt);
}

