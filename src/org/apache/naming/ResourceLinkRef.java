package org.apache.naming;

import javax.naming.Context;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

/**
 * 表示资源的引用地址.
 */
public class ResourceLinkRef extends Reference {

    private static final long serialVersionUID = 1L;


    /**
     * 此引用的默认工厂.
     */
    public static final String DEFAULT_FACTORY =
        org.apache.naming.factory.Constants.DEFAULT_RESOURCE_LINK_FACTORY;


    /**
     * 描述地址类型.
     */
    public static final String GLOBALNAME = "globalName";


    /**
     * ResourceLink Reference.
     *
     * @param resourceClass 资源类
     * @param globalName 全局名称
     * @param factory 对象的工厂的类名，可能是null.
     * @param factoryLocation 加载工厂的位置(e.g. URL)，可能是null.
     */
    public ResourceLinkRef(String resourceClass, String globalName,
                           String factory, String factoryLocation) {
        super(resourceClass, factory, factoryLocation);
        StringRefAddr refAddr = null;
        if (globalName != null) {
            refAddr = new StringRefAddr(GLOBALNAME, globalName);
            add(refAddr);
        }
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
