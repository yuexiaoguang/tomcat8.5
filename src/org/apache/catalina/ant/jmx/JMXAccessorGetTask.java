package org.apache.catalina.ant.jmx;


import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;


/**
 * 访问<em>JMX</em> JSR 160 MBeans Server. 
 * <ul>
 * <li>获取 Mbeans 属性</li>
 * <li>在Ant控制台显示 Get 结果</li>
 * <li>绑定 Get 结果到 Ant 属性</li>
 * </ul>
 * <p>
 * 示例:
 * <br/>
 * 获取 Mbean IDataSender 属性 nrOfRequests 并创建新的Ant属性<em>IDataSender.9025.nrOfRequests</em> 
 * </p>
 * <pre>
 *   &lt;jmx:get
 *           ref="jmx.server"
 *           name="Catalina:type=IDataSender,host=localhost,senderAddress=192.168.1.2,senderPort=9025"
 *           attribute="nrOfRequests"
 *           resultproperty="IDataSender.9025.nrOfRequests"
 *           echo="false"&gt;
 *       /&gt;
 * </pre>
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a referenz <em>jmx.server</em>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorGetTask extends JMXAccessorTask {


    // ----------------------------------------------------- Instance Variables

    private String attribute;

    // ------------------------------------------------------------- Properties

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }


    // ------------------------------------------------------ protected Methods

    @Override
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((attribute == null)) {
            throw new BuildException(
                    "Must specify a 'attribute' for get");
        }
        return  jmxGet(jmxServerConnection, getName());
     }


    /**
     * 获取属性值.
     *
     * @param jmxServerConnection 到JMX 服务器的连接
     * @param name The MBean name
     * @return 错误信息
     * @throws Exception An error occurred
     */
    protected String jmxGet(MBeanServerConnection jmxServerConnection, String name) throws Exception {
        String error = null;
        if(isEcho()) {
            handleOutput("MBean " + name + " get attribute " + attribute );
        }
        Object result = jmxServerConnection.getAttribute(
                new ObjectName(name), attribute);
        if (result != null) {
            echoResult(attribute,result);
            createProperty(result);
        } else
            error = "Attribute " + attribute + " is empty";
        return error;
    }
}
