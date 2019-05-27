package org.apache.catalina.ant.jmx;


import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;


/**
 * 访问<em>JMX</em> JSR 160 MBeans Server. 
 * <ul>
 * <li>打开不止一个JSR 160 rmi 连接</li>
 * <li>Get/Set Mbeans 属性</li>
 * <li>调用有参数的Mbean 操作</li>
 * <li>参数值可以从String转换为 int,long,float,double,boolean,ObjectName 或 InetAddress </li>
 * <li>查询 Mbeans</li>
 * <li>在Ant控制台显示 Get, Call, Query 结果</li>
 * <li>绑定 Get, Call, Query 结果到 Ant 属性</li>
 * </ul>
 *
 * 示例:
 * <ul>
 * <li>
 * 从会话获得一个会话属性hello, 使用<em>${sessionid.0}</em>表单应用<em>Catalina:type=Manager,path=/ClusterTest,host=localhost</em> 
 * <pre>
 *   &lt;jmx:invoke
 *           name="Catalina:type=Manager,context=/ClusterTest,host=localhost"
 *           operation="getSessionAttribute"
 *           resultproperty="hello"&gt;
 *         &lt;arg value="${sessionid.0}"/&gt;
 *         &lt;arg value="Hello"/&gt;
 *   &lt;/jmx:invoke&gt;
 * </pre>
 * </li>
 * <li>
 * 在本地创建新的AccessLogger 
 * <code>
 *   &lt;jmx:invoke
 *           name="Catalina:type=MBeanFactory"
 *           operation="createAccessLoggerValve"
 *           resultproperty="accessLoggerObjectName"
 *       &gt;
 *         &lt;arg value="Catalina:type=Host,host=localhost"/&gt;
 *   &lt;/jmx:invoke&gt;
 *
 * </code>
 * </li>
 * <li>
 * 删除本地的现有AccessLogger
 * <code>
 *   &lt;jmx:invoke
 *           name="Catalina:type=MBeanFactory"
 *           operation="removeValve"
 *       &gt;
 *         &lt;arg value="Catalina:type=Valve,name=AccessLogValve,host=localhost"/&gt;
 *   &lt;/jmx:invoke&gt;
 *
 * </code>
 * </li>
 * </ul>
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a referenz <em>jmx.server</em>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorInvokeTask extends JMXAccessorTask {


    // ----------------------------------------------------- Instance Variables

    private String operation ;
    private List<Arg> args=new ArrayList<>();

    // ------------------------------------------------------------- Properties

    public String getOperation() {
        return operation;
    }
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void addArg(Arg arg ) {
        args.add(arg);
    }

    public List<Arg> getArgs() {
        return args;
    }
    public void setArgs(List<Arg> args) {
        this.args = args;
    }

    // ------------------------------------------------------ protected Methods

    @Override
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((operation == null)) {
            throw new BuildException(
                    "Must specify a 'operation' for call");
        }
        return  jmxInvoke(jmxServerConnection, getName());
     }

    /**
     * 执行指定的命令.
     *
     * @param jmxServerConnection 连接到JMX 服务器的连接
     * @param name MBean名称
     * @return null (除异常外没有报告错误消息)
     * @throws Exception An error occurred
     */
    protected String jmxInvoke(MBeanServerConnection jmxServerConnection, String name) throws Exception {
        Object result ;
        if (args == null) {
             result = jmxServerConnection.invoke(new ObjectName(name),
                    operation, null, null);
        } else {
            Object argsA[]=new Object[ args.size()];
            String sigA[]=new String[args.size()];
            for( int i=0; i<args.size(); i++ ) {
                Arg arg=args.get(i);
                if (arg.getType() == null) {
                    arg.setType("java.lang.String");
                    sigA[i]=arg.getType();
                    argsA[i]=arg.getValue();
                } else {
                    sigA[i]=arg.getType();
                    argsA[i]=convertStringToType(arg.getValue(),arg.getType());
                }
            }
            result = jmxServerConnection.invoke(new ObjectName(name), operation, argsA, sigA);
        }
        if(result != null) {
            echoResult(operation,result);
            createProperty(result);
        }
        return null;
    }

}
