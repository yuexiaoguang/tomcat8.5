package org.apache.catalina.mbeans;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.NamingResources;
import org.apache.tomcat.util.modeler.BaseModelMBean;


/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.tomcat.util.descriptor.web.ContextResource</code> component.</p>
 */
public class ContextResourceMBean extends BaseModelMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public ContextResourceMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }


    /**
     * 获取并返回此MBean特定属性的值.
     *
     * @param name 属性名称
     *
     * @exception AttributeNotFoundException 如果此属性不支持这个MBean
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception ReflectionException 如果在执行getter时发生Java反射异常
     */
    @Override
    public Object getAttribute(String name)
        throws AttributeNotFoundException, MBeanException,
        ReflectionException {

        // Validate the input parameters
        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute name is null"),
                 "Attribute name is null");

        ContextResource cr = null;
        try {
            cr = (ContextResource) getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
             throw new MBeanException(e);
        }

        String value = null;
        if ("auth".equals(name)) {
            return (cr.getAuth());
        } else if ("description".equals(name)) {
            return (cr.getDescription());
        } else if ("name".equals(name)) {
            return (cr.getName());
        } else if ("scope".equals(name)) {
            return (cr.getScope());
        } else if ("type".equals(name)) {
            return (cr.getType());
        } else {
            value = (String) cr.getProperty(name);
            if (value == null) {
                throw new AttributeNotFoundException
                    ("Cannot find attribute "+name);
            }
        }

        return value;

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
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute is null"),
                 "Attribute is null");
        String name = attribute.getName();
        Object value = attribute.getValue();
        if (name == null)
            throw new RuntimeOperationsException
                (new IllegalArgumentException("Attribute name is null"),
                 "Attribute name is null");

        ContextResource cr = null;
        try {
            cr = (ContextResource) getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
             throw new MBeanException(e);
        }

        if ("auth".equals(name)) {
            cr.setAuth((String)value);
        } else if ("description".equals(name)) {
            cr.setDescription((String)value);
        } else if ("name".equals(name)) {
            cr.setName((String)value);
        } else if ("scope".equals(name)) {
            cr.setScope((String)value);
        } else if ("type".equals(name)) {
            cr.setType((String)value);
        } else {
            cr.setProperty(name, ""+value);
        }

        // cannot use side-effects. 每次在资源中进行修改时，它都会被删除和添加.
        NamingResources nr = cr.getNamingResources();
        nr.removeResource(cr.getName());
        nr.addResource(cr);
    }
}
