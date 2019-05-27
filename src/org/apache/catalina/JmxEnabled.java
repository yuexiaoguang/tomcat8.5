package org.apache.catalina;

import javax.management.MBeanRegistration;
import javax.management.ObjectName;

/**
 * 实现这个接口的组件将与一个MBean服务器注册，当它们被创建和未注册或销毁时.
 * 它主要是由实现了{@link Lifecycle}的组件实现的，  但不是专门为它们实现的.
 */
public interface JmxEnabled extends MBeanRegistration {

    /**
     * @return 此组件将被注册的域名.
     */
    String getDomain();


    /**
     * 指定此组件应该注册的域名. 和不能通过组件层次结构的组件一起使用，来确定要使用的正确的域名.
     *
     * @param domain 组件应该被注册的域名的名称
     */
    void setDomain(String domain);


    /**
     * @return 该组件已经和JMX注册的名称.
     */
    ObjectName getObjectName();
}
