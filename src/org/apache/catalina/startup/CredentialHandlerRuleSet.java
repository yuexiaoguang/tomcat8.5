package org.apache.catalina.startup;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;

/**
 * <p>用于处理CredentialHandler定义的元素的内容.
 * 这个<code>RuleSet</code>支持CredentialHandler, 例如<code>NestedCredentialHandler</code>, 使用嵌套的 CredentialHandler.</p>
 */
@SuppressWarnings("deprecation")
public class CredentialHandlerRuleSet extends RuleSetBase {


    private static final int MAX_NESTED_LEVELS = Integer.getInteger(
            "org.apache.catalina.startup.CredentialHandlerRuleSet.MAX_NESTED_LEVELS",
            3).intValue();

    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    public CredentialHandlerRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀(包括结尾的斜杠)
     */
    public CredentialHandlerRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加这个RuleSet中定义的一组Rule实例到指定的<code>Digester</code>实例, 和命名空间URI关联.
     * 这个方法只能通过Digester实例调用.</p>
     *
     * @param digester 新的Rule实例应该添加到的Digester实例.
     */
    @Override
    public void addRuleInstances(Digester digester) {
        StringBuilder pattern = new StringBuilder(prefix);
        for (int i = 0; i < MAX_NESTED_LEVELS; i++) {
            if (i > 0) {
                pattern.append('/');
            }
            pattern.append("CredentialHandler");
            addRuleInstances(digester, pattern.toString(), i == 0 ? "setCredentialHandler"
                    : "addCredentialHandler");
        }
    }

    private void addRuleInstances(Digester digester, String pattern, String methodName) {
        digester.addObjectCreate(pattern, null /* MUST be specified in the element */,
                "className");
        digester.addSetProperties(pattern);
        digester.addSetNext(pattern, methodName, "org.apache.catalina.CredentialHandler");
    }
}
