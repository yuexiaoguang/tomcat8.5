package org.apache.catalina.mbeans;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.NamingResources;
import org.apache.tomcat.util.modeler.BaseModelMBean;


/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.tomcat.util.descriptor.web.ContextEnvironment</code> component.</p>
 */
public class ContextEnvironmentMBean extends BaseModelMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public ContextEnvironmentMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

    // ------------------------------------------------------------- Attributes


    /**
     * 设置指定属性的值.
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

        super.setAttribute(attribute);

        ContextEnvironment ce = null;
        try {
            ce = (ContextEnvironment) getManagedResource();
        } catch (InstanceNotFoundException e) {
            throw new MBeanException(e);
        } catch (InvalidTargetObjectTypeException e) {
             throw new MBeanException(e);
        }

        // cannot use side-effects. 每次在资源中进行修改时，它都会被删除和添加
        NamingResources nr = ce.getNamingResources();
        nr.removeEnvironment(ce.getName());
        nr.addEnvironment(ce);
    }
}
