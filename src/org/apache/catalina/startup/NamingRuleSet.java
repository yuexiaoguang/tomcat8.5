package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p><strong>RuleSet</strong>用于处理 JNDI Enterprise命名上下文资源声明元素.</p>
 */
@SuppressWarnings("deprecation")
public class NamingRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    public NamingRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀(包括尾部斜杠字符)
     */
    public NamingRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加RuleSet中定义的一组Rule实例到指定的<code>Digester</code>实例, 并将它们与命名空间URI相关联.
     * 这个方法只能被Digester实例调用.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    @Override
    public void addRuleInstances(Digester digester) {

        digester.addObjectCreate(prefix + "Ejb",
                                 "org.apache.tomcat.util.descriptor.web.ContextEjb");
        digester.addRule(prefix + "Ejb", new SetAllPropertiesRule());
        digester.addRule(prefix + "Ejb",
                new SetNextNamingRule("addEjb",
                            "org.apache.tomcat.util.descriptor.web.ContextEjb"));

        digester.addObjectCreate(prefix + "Environment",
                                 "org.apache.tomcat.util.descriptor.web.ContextEnvironment");
        digester.addSetProperties(prefix + "Environment");
        digester.addRule(prefix + "Environment",
                            new SetNextNamingRule("addEnvironment",
                            "org.apache.tomcat.util.descriptor.web.ContextEnvironment"));

        digester.addObjectCreate(prefix + "LocalEjb",
                                 "org.apache.tomcat.util.descriptor.web.ContextLocalEjb");
        digester.addRule(prefix + "LocalEjb", new SetAllPropertiesRule());
        digester.addRule(prefix + "LocalEjb",
                new SetNextNamingRule("addLocalEjb",
                            "org.apache.tomcat.util.descriptor.web.ContextLocalEjb"));

        digester.addObjectCreate(prefix + "Resource",
                                 "org.apache.tomcat.util.descriptor.web.ContextResource");
        digester.addRule(prefix + "Resource", new SetAllPropertiesRule());
        digester.addRule(prefix + "Resource",
                new SetNextNamingRule("addResource",
                            "org.apache.tomcat.util.descriptor.web.ContextResource"));

        digester.addObjectCreate(prefix + "ResourceEnvRef",
            "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef");
        digester.addRule(prefix + "ResourceEnvRef", new SetAllPropertiesRule());
        digester.addRule(prefix + "ResourceEnvRef",
                new SetNextNamingRule("addResourceEnvRef",
                            "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef"));

        digester.addObjectCreate(prefix + "ServiceRef",
            "org.apache.tomcat.util.descriptor.web.ContextService");
        digester.addRule(prefix + "ServiceRef", new SetAllPropertiesRule());
        digester.addRule(prefix + "ServiceRef",
                new SetNextNamingRule("addService",
                            "org.apache.tomcat.util.descriptor.web.ContextService"));

        digester.addObjectCreate(prefix + "Transaction",
            "org.apache.tomcat.util.descriptor.web.ContextTransaction");
        digester.addRule(prefix + "Transaction", new SetAllPropertiesRule());
        digester.addRule(prefix + "Transaction",
                new SetNextNamingRule("setTransaction",
                            "org.apache.tomcat.util.descriptor.web.ContextTransaction"));

    }


}
