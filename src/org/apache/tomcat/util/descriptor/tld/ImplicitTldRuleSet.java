package org.apache.tomcat.util.descriptor.tld;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSetBase;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;

/**
 * RulesSet for digesting implicit.tld files.
 *
 * 仅允许使用版本信息和短名称.
 */
@SuppressWarnings("deprecation")
public class ImplicitTldRuleSet extends RuleSetBase {

    private static final StringManager sm = StringManager.getManager(ImplicitTldRuleSet.class);

    private static final String PREFIX = "taglib";
    private static final String VALIDATOR_PREFIX = PREFIX + "/validator";
    private static final String TAG_PREFIX = PREFIX + "/tag";
    private static final String TAGFILE_PREFIX = PREFIX + "/tag-file";
    private static final String FUNCTION_PREFIX = PREFIX + "/function";


    @Override
    public void addRuleInstances(Digester digester) {

        digester.addCallMethod(PREFIX + "/tlibversion", "setTlibVersion", 0);
        digester.addCallMethod(PREFIX + "/tlib-version", "setTlibVersion", 0);
        digester.addCallMethod(PREFIX + "/jspversion", "setJspVersion", 0);
        digester.addCallMethod(PREFIX + "/jsp-version", "setJspVersion", 0);
        digester.addRule(PREFIX, new Rule() {
            // for TLD 2.0 and later, jsp-version is set by version attribute
            @Override
            public void begin(String namespace, String name, Attributes attributes) {
                TaglibXml taglibXml = (TaglibXml) digester.peek();
                taglibXml.setJspVersion(attributes.getValue("version"));
            }
        });
        digester.addCallMethod(PREFIX + "/shortname", "setShortName", 0);
        digester.addCallMethod(PREFIX + "/short-name", "setShortName", 0);

        // 不允许使用元素
        digester.addRule(PREFIX + "/uri", new ElementNotAllowedRule());
        digester.addRule(PREFIX + "/info", new ElementNotAllowedRule());
        digester.addRule(PREFIX + "/description", new ElementNotAllowedRule());
        digester.addRule(PREFIX + "/listener/listener-class", new ElementNotAllowedRule());

        digester.addRule(VALIDATOR_PREFIX, new ElementNotAllowedRule());
        digester.addRule(TAG_PREFIX, new ElementNotAllowedRule());
        digester.addRule(TAGFILE_PREFIX, new ElementNotAllowedRule());
        digester.addRule(FUNCTION_PREFIX, new ElementNotAllowedRule());
    }


    private static class ElementNotAllowedRule extends Rule {
        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {
            throw new IllegalArgumentException(
                    sm.getString("implicitTldRule.elementNotAllowed", name));
        }
    }
}
