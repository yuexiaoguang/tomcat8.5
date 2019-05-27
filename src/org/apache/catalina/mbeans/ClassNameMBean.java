package org.apache.catalina.mbeans;

import javax.management.MBeanException;
import javax.management.RuntimeOperationsException;

import org.apache.tomcat.util.modeler.BaseModelMBean;

/**
 * <p><strong>ModelMBean</strong>实现类的基类，对于不同的标准接口实现，底层基类(因此支持的属性集)是不同的
 * 对于Catalina, 至少包括以下内容:
 * Connector, Logger, Realm, and Valve. 
 * 这个类创建了一个假的MBean属性，名称为<code>className</code>, 它将托管对象的完全限定类名作为其值来报告.</p>
 */
public class ClassNameMBean extends BaseModelMBean {


     // ---------------------------------------------------------- Constructors


     /**
      * @exception MBeanException 如果对象的初始化引发异常
      * @exception RuntimeOperationsException 如果发生IllegalArgumentException
      */
     public ClassNameMBean() throws MBeanException, RuntimeOperationsException {
         super();
     }


     // ------------------------------------------------------------ Properties


     /**
      * 返回完全限定java类的管理对象名称.
      */
     @Override
    public String getClassName() {
         return (this.resource.getClass().getName());
     }
 }
