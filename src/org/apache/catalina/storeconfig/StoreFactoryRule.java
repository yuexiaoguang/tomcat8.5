package org.apache.catalina.storeconfig;

import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;

/**
 * <p>
 * 创建新<code>IStoreFactory</code>实例的规则, 并将其和栈中的顶层对象关联.
 * </p>
 */
public class StoreFactoryRule extends Rule {

    // ----------------------------------------------------------- Constructors

    /**
     * @param storeFactoryClass 要创建的StoreFactory实现类的默认名称
     * @param attributeName 
     *            Name of the attribute that optionally includes an override
     *            name of the IStoreFactory class
     * @param storeAppenderClass The store appender class
     * @param appenderAttributeName store appender类的属性名
     */
    public StoreFactoryRule(String storeFactoryClass, String attributeName,
            String storeAppenderClass, String appenderAttributeName) {

        this.storeFactoryClass = storeFactoryClass;
        this.attributeName = attributeName;
        this.appenderAttributeName = appenderAttributeName;
        this.storeAppenderClass = storeAppenderClass;

    }

    // ----------------------------------------------------- Instance Variables

    /**
     * 可以覆盖实现类类名的属性的名称.
     */
    private String attributeName;

    private String appenderAttributeName;

    /**
     * <code>IStoreFactory</code>实现类的名称.
     */
    private String storeFactoryClass;

    private String storeAppenderClass;

    // --------------------------------------------------------- Public Methods

    /**
     * 处理XML元素的开始.
     *
     * @param namespace XML 命名空间
     * @param name 元素名称
     * @param attributes 整个元素的属性
     * @exception Exception if a processing error occurs
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {

        IStoreFactory factory = (IStoreFactory) newInstance(attributeName,
                storeFactoryClass, attributes);
        StoreAppender storeAppender = (StoreAppender) newInstance(
                appenderAttributeName, storeAppenderClass, attributes);
        factory.setStoreAppender(storeAppender);

        // Add this StoreFactory to our associated component
        StoreDescription desc = (StoreDescription) digester.peek(0);
        StoreRegistry registry = (StoreRegistry) digester.peek(1);
        factory.setRegistry(registry);
        desc.setStoreFactory(factory);

    }

    /**
     * @param attr class Name attribute
     * @param defaultName Default Class
     * @param attributes current digester attribute elements
     * @return new configured object instance
     * @throws ReflectiveOperationException Error creating an instance
     */
    protected Object newInstance(String attr, String defaultName,
            Attributes attributes) throws ReflectiveOperationException {
        String className = defaultName;
        if (attr != null) {
            String value = attributes.getValue(attr);
            if (value != null)
                className = value;
        }
        Class<?> clazz = Class.forName(className);
        return clazz.getConstructor().newInstance();
    }
}