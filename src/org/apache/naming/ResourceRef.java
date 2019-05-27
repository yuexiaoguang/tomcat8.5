package org.apache.naming;

import java.util.Enumeration;

import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

/**
 * 表示资源的引用地址.
 */
public class ResourceRef extends Reference {

    private static final long serialVersionUID = 1L;


    // -------------------------------------------------------------- Constants

    /**
     * 此引用的默认工厂.
     */
    public static final String DEFAULT_FACTORY =
        org.apache.naming.factory.Constants.DEFAULT_RESOURCE_FACTORY;


    /**
     * 描述地址类型.
     */
    public static final String DESCRIPTION = "description";


    /**
     * 范围地址类型.
     */
    public static final String SCOPE = "scope";


    /**
     * Auth 地址类型.
     */
    public static final String AUTH = "auth";


    /**
     * 资源是否是单例
     */
    public static final String SINGLETON = "singleton";

    // ----------------------------------------------------------- Constructors


    /**
     * 资源引用.
     *
     * @param resourceClass Resource class
     * @param description 资源描述
     * @param scope 资源范围
     * @param auth 资源认证
     * @param singleton 资源是否是单例 (每个查找应返回相同的实例而不是新实例)?
     */
    public ResourceRef(String resourceClass, String description,
                       String scope, String auth, boolean singleton) {
        this(resourceClass, description, scope, auth, singleton, null, null);
    }


    /**
     * Resource Reference.
     *
     * @param resourceClass Resource class
     * @param description 资源描述
     * @param scope 资源范围
     * @param auth 资源认证
     * @param singleton 资源是否是单例 (每个查找应返回相同的实例而不是新实例)?
     * @param factory 对象工厂的可能为null的类名.
     * @param factoryLocation 加载工厂的可能为null的位置 (e.g. URL)
     */
    public ResourceRef(String resourceClass, String description,
                       String scope, String auth, boolean singleton,
                       String factory, String factoryLocation) {
        super(resourceClass, factory, factoryLocation);
        StringRefAddr refAddr = null;
        if (description != null) {
            refAddr = new StringRefAddr(DESCRIPTION, description);
            add(refAddr);
        }
        if (scope != null) {
            refAddr = new StringRefAddr(SCOPE, scope);
            add(refAddr);
        }
        if (auth != null) {
            refAddr = new StringRefAddr(AUTH, auth);
            add(refAddr);
        }
        // singleton 是一个 boolean， 处理方式略有不同
        refAddr = new StringRefAddr(SINGLETON, Boolean.toString(singleton));
        add(refAddr);
    }

    // ------------------------------------------------------ Reference Methods


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


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ResourceRef[");
        sb.append("className=");
        sb.append(getClassName());
        sb.append(",factoryClassLocation=");
        sb.append(getFactoryClassLocation());
        sb.append(",factoryClassName=");
        sb.append(getFactoryClassName());
        Enumeration<RefAddr> refAddrs = getAll();
        while (refAddrs.hasMoreElements()) {
            RefAddr refAddr = refAddrs.nextElement();
            sb.append(",{type=");
            sb.append(refAddr.getType());
            sb.append(",content=");
            sb.append(refAddr.getContent());
            sb.append("}");
        }
        sb.append("]");
        return (sb.toString());

    }
}
