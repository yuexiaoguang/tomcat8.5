package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p><strong>RuleSet</strong>用于处理Host元素的内容. 
 * 这个<code>RuleSet</code>不包括任何规则的嵌套的上下文或DefaultContext元素, 应该通过<code>ContextRuleSet</code>实例添加.</p>
 */
@SuppressWarnings("deprecation")
public class HostRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    public HostRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀(包括尾部斜杠字符)
     */
    public HostRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加RuleSet中定义的一组Rule实例到指定的<code>Digester</code>实例, 将他们与命名空间URI关联起来.
     * 这个方法只能被Digester实例调用.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    @Override
    public void addRuleInstances(Digester digester) {

        digester.addObjectCreate(prefix + "Host",
                                 "org.apache.catalina.core.StandardHost",
                                 "className");
        digester.addSetProperties(prefix + "Host");
        digester.addRule(prefix + "Host",
                         new CopyParentClassLoaderRule());
        digester.addRule(prefix + "Host",
                         new LifecycleListenerRule
                         ("org.apache.catalina.startup.HostConfig",
                          "hostConfigClass"));
        digester.addSetNext(prefix + "Host",
                            "addChild",
                            "org.apache.catalina.Container");

        digester.addCallMethod(prefix + "Host/Alias",
                               "addAlias", 0);

        //Cluster configuration start
        digester.addObjectCreate(prefix + "Host/Cluster",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Host/Cluster");
        digester.addSetNext(prefix + "Host/Cluster",
                            "setCluster",
                            "org.apache.catalina.Cluster");
        //Cluster configuration end

        digester.addObjectCreate(prefix + "Host/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Host/Listener");
        digester.addSetNext(prefix + "Host/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addRuleSet(new RealmRuleSet(prefix + "Host/"));

        digester.addObjectCreate(prefix + "Host/Valve",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Host/Valve");
        digester.addSetNext(prefix + "Host/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");
    }
}
