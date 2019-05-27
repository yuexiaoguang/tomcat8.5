package org.apache.naming;

import javax.naming.Context;
import javax.naming.Reference;

/**
 * 表示事务的引用地址.
 */
public class TransactionRef extends Reference {

    private static final long serialVersionUID = 1L;

    // -------------------------------------------------------------- Constants

    /**
     * 此引用的默认工厂.
     */
    public static final String DEFAULT_FACTORY =
        org.apache.naming.factory.Constants.DEFAULT_TRANSACTION_FACTORY;


    // ----------------------------------------------------------- Constructors


    /**
     * 资源引用.
     */
    public TransactionRef() {
        this(null, null);
    }


    /**
     * 资源引用.
     *
     * @param factory 工厂类
     * @param factoryLocation 工厂位置
     */
    public TransactionRef(String factory, String factoryLocation) {
        super("javax.transaction.UserTransaction", factory, factoryLocation);
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
