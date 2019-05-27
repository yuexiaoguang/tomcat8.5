package org.apache.catalina.ant.jmx;

import org.apache.tools.ant.BuildException;

/**
 *
 * <b>定义</b>:
 * <pre>
 *   &lt;path id="catalina_ant"&gt;
 *       &lt;fileset dir="${catalina.home}/server/lib"&gt;
 *           &lt;include name="catalina-ant.jar"/&gt;
 *       &lt;/fileset&gt;
 *   &lt;/path&gt;
 *
 *   &lt;typedef
 *       name="jmxCondition"
 *       classname="org.apache.catalina.ant.jmx.JMXAccessorCondition"
 *       classpathref="catalina_ant"/&gt;
 *   &lt;taskdef
 *       name="jmxOpen"
 *       classname="org.apache.catalina.ant.jmx.JMXAccessorTask"
 *       classpathref="catalina_ant"/&gt;
 * </pre>
 *
 * <b>用法</b>: 等待开始备份节点
 * <pre>
 *     &lt;target name="wait"&gt;
 *       &lt;jmxOpen
 *               host="${jmx.host}" port="${jmx.port}" username="${jmx.username}" password="${jmx.password}" /&gt;
 *        &lt;waitfor maxwait="${maxwait}" maxwaitunit="second" timeoutproperty="server.timeout" &gt;
 *           &lt;and&gt;
 *               &lt;socket server="${server.name}" port="${server.port}"/&gt;
 *               &lt;http url="${url}"/&gt;
 *               &lt;jmxCondition
 *                   name="Catalina:type=IDataSender,host=localhost,senderAddress=192.168.111.1,senderPort=9025"
 *                   operation="=="
 *                   attribute="connected" value="true"
 *               /&gt;
 *               &lt;jmxCondition
 *                   operation="&amp;lt;"
 *                   name="Catalina:j2eeType=WebModule,name=//${tomcat.application.host}${tomcat.application.path},J2EEApplication=none,J2EEServer=none"
 *                   attribute="startupTime" value="250"
 *               /&gt;
 *           &lt;/and&gt;
 *       &lt;/waitfor&gt;
 *       &lt;fail if="server.timeout" message="Server ${url} don't answer inside ${maxwait} sec" /&gt;
 *       &lt;echo message="Server ${url} alive" /&gt;
 *   &lt;/target&gt;
 *
 * </pre>
 * 允许运行的JMX属性和参考值:
 * <ul>
 * <li>==  equals</li>
 * <li>!=  not equals</li>
 * <li>&gt; greater than (&amp;gt;)</li>
 * <li>&gt;= greater than or equals (&amp;gt;=)</li>
 * <li>&lt; lesser than (&amp;lt;)</li>
 * <li>&lt;= lesser than or equals (&amp;lt;=)</li>
 * </ul>
 * <b>NOTE</b>: 对于数值表达式，必须设置类型，并使用XML实体作为操作.<br/>
 * 目前支持的类型<em>long</em> 和 <em>double</em>.
 */
public class JMXAccessorCondition extends JMXAccessorConditionBase {

    // ----------------------------------------------------- Instance Variables

    private String operation = "==" ;
    private String type = "long" ;
    private String unlessCondition;
    private String ifCondition;


    // ----------------------------------------------------- Properties

    public String getOperation() {
        return operation;
    }
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getIf() {
        return ifCondition;
    }
    /**
     * 只有在当前项目中存在给定名称的属性时才执行.
     * 
     * @param c property name
     */
    public void setIf(String c) {
        ifCondition = c;
    }

    public String getUnless() {
        return unlessCondition;
    }
    /**
     * 只有在当前项目中不存在给定名称的属性时才执行
     * 
     * @param c property name
     */
    public void setUnless(String c) {
        unlessCondition = c;
    }

    /**
     * 测试if条件
     * @return true 如果没有if条件, 或命名属性存在
     */
    protected boolean testIfCondition() {
        if (ifCondition == null || "".equals(ifCondition)) {
            return true;
        }
        return getProject().getProperty(ifCondition) != null;
    }

    /**
     * 测试除非条件
     * @return true 如果没有除非条件, 或者有一个命名的属性，但是它不存在
     */
    protected boolean testUnlessCondition() {
        if (unlessCondition == null || "".equals(unlessCondition)) {
            return true;
        }
        return getProject().getProperty(unlessCondition) == null;
    }

    /**
     * 此方法评估条件
     * 如果支持条件 ">,>=,<,<=" 以及<code>long</code> 和 <code>double</code>.
     * @return expression <em>jmxValue</em> <em>operation</em> <em>value</em>
     */
    @Override
    public boolean eval() {
        String value = getValue();
        if (operation == null) {
            throw new BuildException("operation attribute is not set");
        }
        if (value == null) {
            throw new BuildException("value attribute is not set");
        }
        if ((getName() == null || getAttribute() == null)) {
            throw new BuildException(
                    "Must specify an MBean name and attribute for condition");
        }
        if (testIfCondition() && testUnlessCondition()) {
            String jmxValue = accessJMXValue();
            if (jmxValue != null) {
                String op = getOperation();
                if ("==".equals(op)) {
                    return jmxValue.equals(value);
                } else if ("!=".equals(op)) {
                    return !jmxValue.equals(value);
                } else {
                    if ("long".equals(type)) {
                        long jvalue = Long.parseLong(jmxValue);
                        long lvalue = Long.parseLong(value);
                        if (">".equals(op)) {
                            return jvalue > lvalue;
                        } else if (">=".equals(op)) {
                            return jvalue >= lvalue;
                        } else if ("<".equals(op)) {
                            return jvalue < lvalue;
                        } else if ("<=".equals(op)) {
                            return jvalue <= lvalue;
                        }
                    } else if ("double".equals(type)) {
                        double jvalue = Double.parseDouble(jmxValue);
                        double dvalue = Double.parseDouble(value);
                        if (">".equals(op)) {
                            return jvalue > dvalue;
                        } else if (">=".equals(op)) {
                            return jvalue >= dvalue;
                        } else if ("<".equals(op)) {
                            return jvalue < dvalue;
                        } else if ("<=".equals(op)) {
                            return jvalue <= dvalue;
                        }
                    }
                }
            }
            return false;
        }
        return true;
    }
 }

