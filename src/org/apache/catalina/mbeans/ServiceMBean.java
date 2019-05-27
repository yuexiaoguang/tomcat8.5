package org.apache.catalina.mbeans;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.apache.catalina.Executor;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.modeler.BaseModelMBean;

public class ServiceMBean extends BaseModelMBean {

    public ServiceMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

    /**
     * @param address 要绑定的IP地址
     * @param port 要监听的TCP端口号
     * @param isAjp 是否创建一个 AJP/1.3 连接器
     * @param isSSL 是否创建一个 secure 连接器
     *
     * @throws MBeanException 创建连接器出错
     */
    public void addConnector(String address, int port, boolean isAjp, boolean isSSL) throws MBeanException {

        Service service;
        try {
            service = (Service)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }
        String protocol = isAjp ? "AJP/1.3" : "HTTP/1.1";
        Connector connector = new Connector(protocol);
        if ((address!=null) && (address.length()>0)) {
            connector.setProperty("address", address);
        }
        connector.setPort(port);
        connector.setSecure(isSSL);
        connector.setScheme(isSSL ? "https" : "http");

        service.addConnector(connector);

    }

    /**
     * @param type 要添加的Executor类名
     * @throws MBeanException 创建执行程序时出错
     */
    public void addExecutor(String type) throws MBeanException {

        Service service;
        try {
            service = (Service)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        Executor executor;
        try {
             executor = (Executor)Class.forName(type).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new MBeanException(e);
        }

        service.addExecutor(executor);
    }

    /**
     * 查找并返回与此Service关联的一组Connector.
     * 
     * @throws MBeanException 访问关联服务时出错
     */
    public String[] findConnectors() throws MBeanException {

        Service service;
        try {
            service = (Service)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        Connector[] connectors = service.findConnectors();
        String[] str = new String[connectors.length];

        for(int i=0; i< connectors.length; i++){
            str[i] = connectors[i].toString();
        }

        return str;

    }

    /**
     * 检索所有的executor.
     * 
     * @throws MBeanException 访问关联服务时出错
     */
    public String[] findExecutors() throws MBeanException {

        Service service;
        try {
            service = (Service)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        Executor[] executors = service.findExecutors();
        String[] str = new String[executors.length];

        for(int i=0; i< executors.length; i++){
            str[i] = executors[i].toString();
        }

        return str;
    }

    /**
     * 通过名称检索执行程序
     * 
     * @param name 要检索的执行器的名称
     * 
     * @throws MBeanException 访问关联服务时出错
     */
    public String getExecutor(String name) throws MBeanException{

        Service service;
        try {
            service = (Service)getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (RuntimeOperationsException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        Executor executor = service.getExecutor(name);
        return executor.toString();
    }
}
