package org.apache.tomcat.util.digester;


/**
 * <p>定义简写的公共接口在一次操作中配置一组完整的相关<code>Rule</code>定义，可能与特定的命名空间URI相关联.
 * 使用实现此接口的类的实例:</p>
 * <ul>
 * <li>创建此接口的具体实现.</li>
 * <li>可选, 可以通过配置<code>getNamespaceURI()</code>返回的值，将<code>RuleSet</code>配置为仅与特定命名空间URI相关.</li>
 * <li>在配置Digester实例时, 调用<code>digester.addRuleSet()</code>并传递RuleSet实例.</li>
 * <li>Digester 将调用RuleSet的<code>addRuleInstances()</code>方法配置必要的规则.</li>
 * </ul>
 */
public interface RuleSet {


    // ------------------------------------------------------------- Properties


    /**
     * @return 将应用于从此RuleSet创建的所有Rule实例的命名空间URI.
     *
     * @deprecated Unused. Will be removed in Tomcat 9
     */
    @Deprecated
    public String getNamespaceURI();


    // --------------------------------------------------------- Public Methods


    /**
     * 将此RuleSet中定义的Rule实例集添加到指定的<code>Digester</code>实例，并将它们与我们的命名空间URI相关联.
     * 此方法只能由Digester实例调用.
     *
     * @param digester 应添加新规则实例的Digester实例.
     */
    public void addRuleInstances(Digester digester);


}
