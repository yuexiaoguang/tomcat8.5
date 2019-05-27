package org.apache.catalina.mbeans;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.IntrospectionUtils;


/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.coyote.tomcat5.CoyoteConnector</code> component.</p>
 */
public class ConnectorMBean extends ClassNameMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public ConnectorMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }


    // ------------------------------------------------------------- Attributes


    /**
     * 获取并返回此MBean特定属性的值.
     *
     * @param name 请求属性名称
     *
     * @exception AttributeNotFoundException 如果此属性不支持这个MBean
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception ReflectionException 如果在执行getter时发生Java反射异常
     */
    @Override
    public Object getAttribute(String name) throws AttributeNotFoundException,
            MBeanException, ReflectionException {

        // Validate the input parameters
        if (name == null)
            throw new RuntimeOperationsException(new IllegalArgumentException(
                    "Attribute name is null"), "Attribute name is null");

        Object result = null;
        try {
            Connector connector = (Connector) getManagedResource();
            result = IntrospectionUtils.getProperty(connector, name);
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }

        return result;

    }


    /**
     * 设置此MBean特定属性的值.
     *
     * @param attribute 要设置的属性的标识和新值
     *
     * @exception AttributeNotFoundException 如果此属性不支持这个MBean
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception ReflectionException 如果在执行getter时发生Java反射异常
     */
     @Override
    public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, MBeanException,
            ReflectionException {

        // Validate the input parameters
        if (attribute == null)
            throw new RuntimeOperationsException(new IllegalArgumentException(
                    "Attribute is null"), "Attribute is null");
        String name = attribute.getName();
        Object value = attribute.getValue();
        if (name == null)
            throw new RuntimeOperationsException(new IllegalArgumentException(
                    "Attribute name is null"), "Attribute name is null");

        try {
            Connector connector = (Connector) getManagedResource();
            IntrospectionUtils.setProperty(connector, name, String.valueOf(value));
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
            throw new MBeanException(e);
        }
    }
}
