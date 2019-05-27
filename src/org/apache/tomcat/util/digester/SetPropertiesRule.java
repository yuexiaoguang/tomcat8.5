package org.apache.tomcat.util.digester;


import org.apache.tomcat.util.IntrospectionUtils;
import org.xml.sax.Attributes;


/**
 * <p>在堆栈顶部的对象上设置属性, 基于具有相应名称的属性.</p>
 */
public class SetPropertiesRule extends Rule {

    /**
     * 处理此元素的开头.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则返回空字符串
     * @param theName 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param attributes 此元素的属性列表
     */
    @Override
    public void begin(String namespace, String theName, Attributes attributes)
            throws Exception {

        // 标识要实例化的类的名称
        Object top = digester.peek();
        if (digester.log.isDebugEnabled()) {
            if (top != null) {
                digester.log.debug("[SetPropertiesRule]{" + digester.match +
                                   "} Set " + top.getClass().getName() +
                                   " properties");
            } else {
                digester.log.debug("[SetPropertiesRule]{" + digester.match +
                                   "} Set NULL properties");
            }
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getLocalName(i);
            if ("".equals(name)) {
                name = attributes.getQName(i);
            }
            String value = attributes.getValue(i);

            if (digester.log.isDebugEnabled()) {
                digester.log.debug("[SetPropertiesRule]{" + digester.match +
                        "} Setting property '" + name + "' to '" +
                        value + "'");
            }
            if (!digester.isFakeAttribute(top, name)
                    && !IntrospectionUtils.setProperty(top, name, value)
                    && digester.getRulesValidation()) {
                digester.log.warn("[SetPropertiesRule]{" + digester.match +
                        "} Setting property '" + name + "' to '" +
                        value + "' did not find a matching property.");
            }
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SetPropertiesRule[");
        sb.append("]");
        return (sb.toString());
    }
}
