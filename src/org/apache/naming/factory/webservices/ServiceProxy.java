package org.apache.naming.factory.webservices;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.util.Hashtable;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.rpc.Service;
import javax.xml.rpc.ServiceException;

/**
 * Web服务的对象代理.
 */
public class ServiceProxy implements InvocationHandler {

    /**
     * 被代理的对象
     */
    private final Service service;

    /**
     * 改变行为方法 : Service.getPort(QName, Class)
     */
    private static Method portQNameClass = null;

    /**
     * 改变行为方法 : Service.getPort(Class)
     */
    private static Method portClass = null;

    /**
     * PortComponentRef list
     */
    private Hashtable<String,QName> portComponentRef = null;

    /**
     * @param service 被包装的 Service 实例
     * 
     * @throws ServiceException 永远不会发生
     */
    public ServiceProxy(Service service) throws ServiceException {
        this.service = service;
        try {
            portQNameClass = Service.class.getDeclaredMethod("getPort", new Class[]{QName.class, Class.class});
            portClass = Service.class.getDeclaredMethod("getPort", new Class[]{Class.class});
        } catch (Exception e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {

        if (portQNameClass.equals(method)) {
            return getProxyPortQNameClass(args);
        }

        if (portClass.equals(method)) {
            return getProxyPortClass(args);
        }

        try {
            return method.invoke(service, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
    }

    /**
     * @param args 方法调用参数
     * 
     * @return 返回正确的 Port
     * @throws ServiceException 如果端口的 QName 是一个未知的 Port (没有在WSDL中定义).
     */
    private Object getProxyPortQNameClass(Object[] args) throws ServiceException {
        QName name = (QName) args[0];
        String nameString = name.getLocalPart();
        Class<?> serviceendpointClass = (Class<?>) args[1];

        for (@SuppressWarnings("unchecked")
        Iterator<QName> ports = service.getPorts(); ports.hasNext();) {
            QName portName = ports.next();
            String portnameString = portName.getLocalPart();
            if (portnameString.equals(nameString)) {
                return service.getPort(name, serviceendpointClass);
            }
        }

        // 未找到端口
        throw new ServiceException("Port-component-ref : " + name + " not found");
    }

    /**
     * @param portComponentRef List
     */
    public void setPortComponentRef(Hashtable<String,QName> portComponentRef) {
        this.portComponentRef = portComponentRef;
    }

    /**
     * @param args 方法调用参数
     * @return 返回正确的 Port
     * @throws ServiceException 如果端口的QName是未知Port
     */
    private Remote getProxyPortClass(Object[] args)
    throws ServiceException {
        Class<?> serviceendpointClass = (Class<?>) args[0];

        if (this.portComponentRef == null)
            return service.getPort(serviceendpointClass);

        QName portname = this.portComponentRef.get(serviceendpointClass.getName());
        if (portname != null) {
            return service.getPort(portname, serviceendpointClass);
        } else {
            return service.getPort(serviceendpointClass);
        }
    }
}
