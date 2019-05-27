package org.apache.catalina.startup;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;

/**
 * <p>用于处理Realm元素的内容. 支持嵌套.</p>
 */
@SuppressWarnings("deprecation")
public class RealmRuleSet extends RuleSetBase {


    private static final int MAX_NESTED_REALM_LEVELS = Integer.getInteger(
            "org.apache.catalina.startup.RealmRuleSet.MAX_NESTED_REALM_LEVELS",
            3).intValue();

    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    public RealmRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀 (包括结尾的斜杠)
     */
    public RealmRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加这个RuleSet中定义的一组 Rule实例到指定的 <code>Digester</code>实例, 并将其和命名空间 URI关联.
     * 这个方法只能被 Digester 实例调用.</p>
     *
     * @param digester 新的Rule实例应该被添加到的Digester实例
     */
    @Override
    public void addRuleInstances(Digester digester) {
        StringBuilder pattern = new StringBuilder(prefix);
        for (int i = 0; i < MAX_NESTED_REALM_LEVELS; i++) {
            if (i > 0) {
                pattern.append('/');
            }
            pattern.append("Realm");
            addRuleInstances(digester, pattern.toString(), i == 0 ? "setRealm" : "addRealm");
        }
    }

    private void addRuleInstances(Digester digester, String pattern, String methodName) {
        digester.addObjectCreate(pattern, null /* MUST be specified in the element */,
                "className");
        digester.addSetProperties(pattern);
        digester.addSetNext(pattern, methodName, "org.apache.catalina.Realm");
        digester.addRuleSet(new CredentialHandlerRuleSet(pattern + "/"));
    }
}
