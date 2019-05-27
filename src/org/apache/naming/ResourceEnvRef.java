package org.apache.naming;

import javax.naming.Context;
import javax.naming.Reference;

/**
 * 表示资源环境的引用地址.
 */
public class ResourceEnvRef extends Reference {

    private static final long serialVersionUID = 1L;


    /**
     * 此引用的默认工厂.
     */
    public static final String DEFAULT_FACTORY =
        org.apache.naming.factory.Constants.DEFAULT_RESOURCE_ENV_FACTORY;


    /**
     * 资源环境引用
     *
     * @param resourceType Type
     */
    public ResourceEnvRef(String resourceType) {
        super(resourceType);
    }


    /**
     * 检索引用的对象的工厂的类名.
     */
    @Override
    public String getFactoryClassName() {
        String factory = super.getFactoryClassName();
        if (factory != null) {
            return factory;
        } else {
            factory = System.getProperty(Context.OBJECT_FACTORIES);
            if (factory != null) {
                return null;
            } else {
                return DEFAULT_FACTORY;
            }
        }
    }
}
