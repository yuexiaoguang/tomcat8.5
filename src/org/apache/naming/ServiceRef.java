package org.apache.naming;

import java.util.Enumeration;
import java.util.Vector;

import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

/**
 * 表示一个引用 web service.
 */
public class ServiceRef extends Reference {

    private static final long serialVersionUID = 1L;

    // -------------------------------------------------------------- Constants

    /**
     * 此引用的默认工厂.
     */
    public static final String DEFAULT_FACTORY =
        org.apache.naming.factory.Constants.DEFAULT_SERVICE_FACTORY;


    /**
     * Service Classname 地址类型.
     */
    public static final String SERVICE_INTERFACE  = "serviceInterface";


    /**
     * ServiceQname 地址类型.
     */
    public static final String SERVICE_NAMESPACE  = "service namespace";
    public static final String SERVICE_LOCAL_PART = "service local part";


    /**
     * Wsdl Location 地址类型.
     */
    public static final String WSDL      = "wsdl";


    /**
     * Jaxrpcmapping 地址类型.
     */
    public static final String JAXRPCMAPPING = "jaxrpcmapping";


    /**
     * port-component-ref/port-component-link 地址类型.
     */
    public static final String PORTCOMPONENTLINK = "portcomponentlink";


    /**
     * port-component-ref/service-endpoint-interface 地址类型.
     */
    public static final String SERVICEENDPOINTINTERFACE = "serviceendpointinterface";


    /**
     * 保存处理程序引用对象, 因为他们不能保存在 addrs vector中.
     */
    private final Vector<HandlerRef> handlers = new Vector<>();


    // ----------------------------------------------------------- Constructors

    public ServiceRef(String refname, String serviceInterface, String[] serviceQname,
                       String wsdl, String jaxrpcmapping) {
        this(refname, serviceInterface, serviceQname, wsdl, jaxrpcmapping,
                        null, null);
    }

    public ServiceRef(@SuppressWarnings("unused") String refname,
                       String serviceInterface, String[] serviceQname,
                       String wsdl, String jaxrpcmapping,
                       String factory, String factoryLocation) {
        super(serviceInterface, factory, factoryLocation);
        StringRefAddr refAddr = null;
        if (serviceInterface != null) {
            refAddr = new StringRefAddr(SERVICE_INTERFACE, serviceInterface);
            add(refAddr);
        }
        if (serviceQname[0] != null) {
            refAddr = new StringRefAddr(SERVICE_NAMESPACE, serviceQname[0]);
            add(refAddr);
        }
        if (serviceQname[1] != null) {
            refAddr = new StringRefAddr(SERVICE_LOCAL_PART, serviceQname[1]);
            add(refAddr);
        }
        if (wsdl != null) {
            refAddr = new StringRefAddr(WSDL, wsdl);
            add(refAddr);
        }
        if (jaxrpcmapping != null) {
            refAddr = new StringRefAddr(JAXRPCMAPPING, jaxrpcmapping);
            add(refAddr);
        }
    }


    // ------------------------------------------------------ Reference Methods


    public HandlerRef getHandler() {
        return handlers.remove(0);
    }


    public int getHandlersSize() {
        return handlers.size();
    }


    public void addHandler(HandlerRef handler) {
        handlers.add(handler);
    }


    /**
     * 检索此引用引用的对象的工厂的类名称.
     * @return the factory
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

        StringBuilder sb = new StringBuilder("ServiceRef[");
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
