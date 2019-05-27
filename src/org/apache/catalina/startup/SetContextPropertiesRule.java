package org.apache.catalina.startup;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;

/**
 * 使用反射设置上下文的属性的Rule(除了 "path").
 */
public class SetContextPropertiesRule extends Rule {


    /**
     * 处理XML 元素的开始.
     *
     * @param attributes 元素属性
     *
     * @exception Exception if a processing error occurs
     */
    @Override
    public void begin(String namespace, String nameX, Attributes attributes)
        throws Exception {

        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getLocalName(i);
            if ("".equals(name)) {
                name = attributes.getQName(i);
            }
            if ("path".equals(name) || "docBase".equals(name)) {
                continue;
            }
            String value = attributes.getValue(i);
            if (!digester.isFakeAttribute(digester.peek(), name)
                    && !IntrospectionUtils.setProperty(digester.peek(), name, value)
                    && digester.getRulesValidation()) {
                digester.getLogger().warn("[SetContextPropertiesRule]{" + digester.getMatch() +
                        "} Setting property '" + name + "' to '" +
                        value + "' did not find a matching property.");
            }
        }
    }
}
