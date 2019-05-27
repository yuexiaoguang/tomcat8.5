package org.apache.catalina.ant.jmx;

import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;

/**
 * 在<em>JMX</em> JSR 160 MBeans 服务器上创建新的MBean. 
 * <ul>
 * <li>创建 Mbeans</li>
 * <li>创建有参数的 Mbeans</li>
 * <li>创建远程地使用不同类加载器的 Mbeans</li>
 * </ul>
 * <p>
 * 示例:
 * <br/>
 * 使用 jmx.server连接创建Mbean
 * </p>
 * <pre>
 *   &lt;jmx:create
 *           ref="jmx.server"
 *           name="Catalina:type=MBeanFactory"
 *           className="org.apache.catalina.mbeans.MBeanFactory"
 *           classLoader="Catalina:type=ServerClassLoader,name=server"&gt;
 *            &lt;Arg value="org.apache.catalina.mbeans.MBeanFactory" /&gt;
 *   &lt;/jmxCreate/&gt;
 * </pre>
 * <p>
 * <b>WARNING</b>不是所有的Tomcat MBeans都可以远程创建和注册通过它们的父级!
 * 请使用 MBeanFactory 生成 valves 和 realms.
 * </p>
 * <p>
 * 第一次调用远程MBeanserver，保存 JMXConnection 一个引用<em>jmx.server</em>
 * </p>
 * 这些任务必须Ant 1.6 或更新的接口.
 */
public class JMXAccessorCreateTask extends JMXAccessorTask {
    // ----------------------------------------------------- Instance Variables

    private String className;
    private String classLoader;
    private List<Arg> args=new ArrayList<>();

    // ------------------------------------------------------------- Properties

    public String getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(String classLoaderName) {
        this.classLoader = classLoaderName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
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
        if ((className == null)) {
            throw new BuildException(
                    "Must specify a 'className' for get");
        }
        jmxCreate(jmxServerConnection, getName());
        return null;
     }

    /**
     * 从ClassLoader创建一个新的MBean, 由一个ObjectName标识.
     *
     * @param jmxServerConnection 到JMX 服务器的连接
     * @param name MBean名称
     * @throws Exception 创建MBean的错误
     */
    protected void jmxCreate(MBeanServerConnection jmxServerConnection,
            String name) throws Exception {
        Object argsA[] = null;
        String sigA[] = null;
        if (args != null) {
           argsA = new Object[ args.size()];
           sigA = new String[args.size()];
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
        }
        if (classLoader != null && !"".equals(classLoader)) {
            if (isEcho()) {
                handleOutput("create MBean " + name + " from class "
                        + className + " with classLoader " + classLoader);
            }
            if(args == null)
                jmxServerConnection.createMBean(className, new ObjectName(name), new ObjectName(classLoader));
            else
                jmxServerConnection.createMBean(className, new ObjectName(name), new ObjectName(classLoader),argsA,sigA);

        } else {
            if (isEcho()) {
                handleOutput("create MBean " + name + " from class "
                        + className);
            }
            if(args == null)
                jmxServerConnection.createMBean(className, new ObjectName(name));
            else
                jmxServerConnection.createMBean(className, new ObjectName(name),argsA,sigA);
        }
    }

}
