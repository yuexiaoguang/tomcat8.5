package org.apache.catalina.startup;

import java.util.HashMap;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;

/**
 * 使用反射设置属性的Rule.
 */
public class SetAllPropertiesRule extends Rule {


    public SetAllPropertiesRule() {}

    public SetAllPropertiesRule(String[] exclude) {
        for (int i=0; i<exclude.length; i++ ) if (exclude[i]!=null) this.excludes.put(exclude[i],exclude[i]);
    }

    protected final HashMap<String,String> excludes = new HashMap<>();


    /**
     * 处理 XML元素的开始.
     *
     * @param attributes 元素的属性
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
            String value = attributes.getValue(i);
            if ( !excludes.containsKey(name)) {
                if (!digester.isFakeAttribute(digester.peek(), name)
                        && !IntrospectionUtils.setProperty(digester.peek(), name, value)
                        && digester.getRulesValidation()) {
                    digester.getLogger().warn("[SetAllPropertiesRule]{" + digester.getMatch() +
                            "} Setting property '" + name + "' to '" +
                            value + "' did not find a matching property.");
                }
            }
        }
    }
}
