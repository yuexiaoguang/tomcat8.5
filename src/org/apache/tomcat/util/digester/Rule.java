package org.apache.tomcat.util.digester;

import org.xml.sax.Attributes;

/**
 * 实现了在匹配XML元素的相应嵌套模式时要采取的操作.
 */
public abstract class Rule {

    // ----------------------------------------------------------- Constructors

    /**
     * <p>现在，添加规则时将设置digester.</p>
     */
    public Rule() {}


    // ----------------------------------------------------- Instance Variables


    /**
     * 与此Rule相关的Digester.
     */
    protected Digester digester = null;


    /**
     * 与此 Rule 相关的命名空间URI.
     */
    protected String namespaceURI = null;


    // ------------------------------------------------------------- Properties

    /**
     * 确定与此规则关联的Digester.
     */
    public Digester getDigester() {
        return digester;
    }


    /**
     * 设置与<code>Rule</code>关联的<code>Digester</code>.
     *
     * @param digester 与此规则相关联的digester
     */
    public void setDigester(Digester digester) {
        this.digester = digester;
    }


    /**
     * 返回与此规则相关的命名空间URI.
     *
     * @return 与此规则相关的命名空间URI, 或 <code>null</code>.
     */
    public String getNamespaceURI() {
        return namespaceURI;
    }


    /**
     * 设置与此规则相关的命名空间URI.
     *
     * @param namespaceURI 与此规则相关的命名空间URI, 或<code>null</code>匹配独立的命名空间.
     */
    public void setNamespaceURI(String namespaceURI) {
        this.namespaceURI = namespaceURI;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 遇到匹配的XML元素的开头时调用此方法. 默认实现是 NO-OP.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则返回空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param attributes 此元素的属性列表
     *
     * @throws Exception 如果在处理事件时发生错误
     */
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        // NO-OP by default.
    }


    /**
     * 遇到匹配的XML元素的主体时调用此方法.  如果元素没有主体, 不会调用此方法. 默认实现是 NO-OP.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则返回空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param text 这个元素的正文主体
     *
     * @throws Exception 如果在处理事件时发生错误
     */
    public void body(String namespace, String name, String text) throws Exception {
        // NO-OP by default.
    }


    /**
     * 遇到匹配的XML元素的末尾时调用此方法. 默认实现是 NO-OP.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则返回空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     *
     * @throws Exception 如果在处理事件时发生错误
     */
    public void end(String namespace, String name) throws Exception {
        // NO-OP by default.
    }


    /**
     * 调用所有解析方法后调用此方法, 允许 Rule 删除临时数据.
     *
     * @throws Exception 如果在处理事件时发生错误
     */
    public void finish() throws Exception {
        // NO-OP by default.
    }
}
