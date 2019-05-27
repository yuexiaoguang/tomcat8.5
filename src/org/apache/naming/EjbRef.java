package org.apache.naming;

import javax.naming.Context;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

/**
 * 表示对EJB的引用地址.
 */
public class EjbRef extends Reference {

    private static final long serialVersionUID = 1L;


    // -------------------------------------------------------------- Constants
    /**
     * 此引用的默认工厂.
     */
    public static final String DEFAULT_FACTORY = org.apache.naming.factory.Constants.DEFAULT_EJB_FACTORY;


    /**
     * EJB类型地址类型.
     */
    public static final String TYPE = "type";


    /**
     * 远程接口名称地址类型.
     */
    public static final String REMOTE = "remote";


    /**
     * Link 地址类型.
     */
    public static final String LINK = "link";


    // ----------------------------------------------------------- Constructors


    /**
     * @param ejbType EJB类型
     * @param home Home接口类名
     * @param remote 远程接口类名
     * @param link EJB link
     */
    public EjbRef(String ejbType, String home, String remote, String link) {
        this(ejbType, home, remote, link, null, null);
    }


    /**
     * @param ejbType EJB类型
     * @param home Home接口类名
     * @param remote 远程接口类名
     * @param link EJB link
     * @param factory 对象的工厂的类名，可能是null.
     * @param factoryLocation   加载工厂的位置(e.g. URL)，可能是null
     */
    public EjbRef(String ejbType, String home, String remote, String link,
                  String factory, String factoryLocation) {
        super(home, factory, factoryLocation);
        StringRefAddr refAddr = null;
        if (ejbType != null) {
            refAddr = new StringRefAddr(TYPE, ejbType);
            add(refAddr);
        }
        if (remote != null) {
            refAddr = new StringRefAddr(REMOTE, remote);
            add(refAddr);
        }
        if (link != null) {
            refAddr = new StringRefAddr(LINK, link);
            add(refAddr);
        }
    }

    /**
     * 检索此引用引用的对象的工厂的类名..
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
