package org.apache.catalina.ant.jmx;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;

/**
 * 注销一个 MBean 在<em>JMX</em> JSR 160 MBeans Server. 
 * <ul>
 * <li>注销 Mbeans</li>
 * </ul>
 * <p>
 * 示例:
 * <br>
 * 注销一个现有的 Mbean 在 jmx.server 连接 
 * </p>
 * <pre>
 *   &lt;jmx:unregister
 *           ref="jmx.server"
 *           name="Catalina:type=MBeanFactory" /&gt;
 * </pre>
 * <p>
 * <b>WARNING</b>不是所有的Tomcat MBeans 可以成功的远程注销. mbean的注销不会从父类删除 valves, realm, ...
 * 使用 MBeanFactory 的操作删除 valves 和 realms.
 * </p>
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a reference <em>jmx.server</em>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorUnregisterTask extends JMXAccessorTask {

    // ------------------------------------------------------ protected Methods

    @Override
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        return  jmxUuregister(jmxServerConnection, getName());
     }


    /**
     * 注销 Mbean.
     *
     * @param jmxServerConnection 到JMX 服务器的连接
     * @param name MBean名称
     * @return null (除异常外没有报告错误消息)
     * @throws Exception An error occurred
     */
    protected String jmxUuregister(MBeanServerConnection jmxServerConnection,String name) throws Exception {
        String error = null;
        if(isEcho()) {
            handleOutput("Unregister MBean " + name  );
        }
        jmxServerConnection.unregisterMBean(
                new ObjectName(name));
        return error;
    }
}
