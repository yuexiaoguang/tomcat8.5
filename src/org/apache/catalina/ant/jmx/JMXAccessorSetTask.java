package org.apache.catalina.ant.jmx;


import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;

/**
 * 访问<em>JMX</em> JSR 160 MBeans Server. 
 * <ul>
 * <li>获取Mbeans 属性</li>
 * <li>在Ant控制台显示 Get 结果</li>
 * <li>绑定 Get 结果到 Ant 属性</li>
 * </ul>
 * <p>
 * 示例:
 * 设置Mbean Manager 属性 maxActiveSessions.
 * 设置这个属性刷新jmx 连接, 不保存引用
 * </p>
 * <pre>
 *   &lt;jmx:set
 *           host="127.0.0.1"
 *           port="9014"
 *           ref=""
 *           name="Catalina:type=Manager,context="/ClusterTest",host=localhost"
 *           attribute="maxActiveSessions"
 *           value="100"
 *           type="int"
 *           echo="false"&gt;
 *       /&gt;
 * </pre>
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a referenz <em>jmx.server</em>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorSetTask extends JMXAccessorTask {

    // ----------------------------------------------------- Instance Variables

    private String attribute;
    private String value;
    private String type;
    private boolean convert = false ;

    // ------------------------------------------------------------- Properties

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }


    public String getType() {
        return type;
    }

    public void setType(String valueType) {
        this.type = valueType;
    }


    public boolean isConvert() {
        return convert;
    }
    public void setConvert(boolean convert) {
        this.convert = convert;
    }
    // ------------------------------------------------------ protected Methods

    @Override
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((attribute == null || value == null)) {
            throw new BuildException(
                    "Must specify a 'attribute' and 'value' for set");
        }
        return  jmxSet(jmxServerConnection, getName());
     }

    /**
     * 设置属性值.
     *
     * @param jmxServerConnection 到JMX 服务器的连接
     * @param name MBean名称
     * @return null (除异常外没有报告错误消息)
     * @throws Exception An error occurred
     */
    protected String jmxSet(MBeanServerConnection jmxServerConnection,
            String name) throws Exception {
        Object realValue;
        if (type != null) {
            realValue = convertStringToType(value, type);
        } else {
            if (isConvert()) {
                String mType = getMBeanAttributeType(jmxServerConnection, name,
                        attribute);
                realValue = convertStringToType(value, mType);
            } else
                realValue = value;
        }
        jmxServerConnection.setAttribute(new ObjectName(name), new Attribute(
                attribute, realValue));
        return null;
    }


    /**
     * 从Mbean服务器获取 MBean 属性
     *
     * @param jmxServerConnection JMX连接名称
     * @param name MBean名称
     * @param attribute 属性名
     * @return 属性类型
     * @throws Exception An error occurred
     */
    protected String getMBeanAttributeType(
            MBeanServerConnection jmxServerConnection,
            String name,
            String attribute) throws Exception {
        ObjectName oname = new ObjectName(name);
        String mattrType = null;
        MBeanInfo minfo = jmxServerConnection.getMBeanInfo(oname);
        MBeanAttributeInfo attrs[] = minfo.getAttributes();
        for (int i = 0; mattrType == null && i < attrs.length; i++) {
            if (attribute.equals(attrs[i].getName()))
                mattrType = attrs[i].getType();
        }
        return mattrType;
    }
 }
