package org.apache.naming;

import java.util.Enumeration;

import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

/**
 * 表示Web服务的引用处理程序.
 */
public class HandlerRef extends Reference {

    private static final long serialVersionUID = 1L;


    // -------------------------------------------------------------- Constants
    /**
     * 此引用的默认工厂.
     */
    public static final String DEFAULT_FACTORY =
        org.apache.naming.factory.Constants.DEFAULT_HANDLER_FACTORY;


    /**
     * HandlerName 地址类型.
     */
    public static final String HANDLER_NAME  = "handlername";


    /**
     * Handler Classname 地址类型.
     */
    public static final String HANDLER_CLASS  = "handlerclass";


    /**
     * Handler Classname 地址类型.
     */
    public static final String HANDLER_LOCALPART  = "handlerlocalpart";


    /**
     * Handler Classname 地址类型.
     */
    public static final String HANDLER_NAMESPACE  = "handlernamespace";


    /**
     * Handler Classname 地址类型.
     */
    public static final String HANDLER_PARAMNAME  = "handlerparamname";


    /**
     * Handler Classname 地址类型.
     */
    public static final String HANDLER_PARAMVALUE  = "handlerparamvalue";


    /**
     * Handler SoapRole 地址类型.
     */
    public static final String HANDLER_SOAPROLE  = "handlersoaprole";


    /**
     * Handler PortName 地址类型.
     */
    public static final String HANDLER_PORTNAME  = "handlerportname";


    // ----------------------------------------------------------- Constructors


    public HandlerRef(String refname, String handlerClass) {
        this(refname, handlerClass, null, null);
    }


    public HandlerRef(String refname, String handlerClass,
                    String factory, String factoryLocation) {
        super(refname, factory, factoryLocation);
        StringRefAddr refAddr = null;
        if (refname != null) {
            refAddr = new StringRefAddr(HANDLER_NAME, refname);
            add(refAddr);
        }
        if (handlerClass != null) {
            refAddr = new StringRefAddr(HANDLER_CLASS, handlerClass);
            add(refAddr);
        }
    }


    // ----------------------------------------------------- Instance Variables


    // ------------------------------------------------------ Reference Methods


    /**
     * 检索此引用引用的对象的工厂的类名称.
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

        StringBuilder sb = new StringBuilder("HandlerRef[");
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
