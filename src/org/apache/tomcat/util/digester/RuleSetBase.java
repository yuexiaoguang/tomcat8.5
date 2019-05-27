package org.apache.tomcat.util.digester;

/**
 * <p>实现 {@link RuleSet} 接口的基类.
 * 具体实现应该在<code>addRuleSet()</code>实现中列出它们的所有实际规则创建逻辑.</p>
 *
 * @deprecated Unnecessary once deprecated methods are removed. Will be removed
 *             in Tomcat 9.
 */
@Deprecated
public abstract class RuleSetBase implements RuleSet {

    // ----------------------------------------------------- Instance Variables

    /**
     * 将与此RuleSet创建的所有Rule实例关联的命名空间URI.
     *
     * @deprecated Unused. This will be removed in Tomcat 9.
     */
    @Deprecated
    protected String namespaceURI = null;


    // ------------------------------------------------------------- Properties

    /**
     * 返回将应用于从此RuleSet创建的所有Rule实例的命名空间URI.
     *
     * @deprecated Unused. This will be removed in Tomcat 9.
     */
    @Deprecated
    @Override
    public String getNamespaceURI() {
        return (this.namespaceURI);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 将此RuleSet中定义的Rule实例集添加到指定的<code>Digester</code>实例, 并将它们与我们的命名空间URI相关联.
     * 此方法只能由Digester实例调用.
     *
     * @param digester 应添加新规则实例的Digester实例.
     */
    @Override
    public abstract void addRuleInstances(Digester digester);
}
