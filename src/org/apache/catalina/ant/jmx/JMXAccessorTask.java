package org.apache.catalina.ant.jmx;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.catalina.ant.BaseRedirectorHelperTask;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * 访问<em>JMX</em> JSR 160 MBeans Server.
 * <ul>
 * <li>打开不止一个 JSR 160 rmi 连接</li>
 * <li>Get/Set Mbeans 属性</li>
 * <li>调用有参数的Mbean 操作<</li>
 * <li>A参数值可以从String转换为 int,long,float,double,boolean,ObjectName or InetAddress</li>
 * <li>查询 Mbeans</li>
 * <li>在Ant控制台显示 Get, Call, Query 结果</li>
 * <li>绑定 Get, Call, Query 结果到 Ant 属性</li>
 * </ul>
 *
 * 示例: 使用reference 和 autorisation打开服务器
 * <pre>
 *    &lt;jmxOpen
 *            host=&quot;127.0.0.1&quot;
 *            port=&quot;9014&quot;
 *            username=&quot;monitorRole&quot;
 *            password=&quot;mysecret&quot;
 *            ref=&quot;jmx.myserver&quot;
 *        /&gt;
 * </pre>
 *
 * 所有调用打开相同的refid重用连接.
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a referenz <em>jmx.server</em>
 * </p>
 * 所有的 JMXAccessorXXXTask 支持属性<em>if</em>和<em>unless</em>. <em>if</em>任务只有在属性存在以及<em>unless</em>的属性不存在的时候，才会执行.
 * <br><b>NOTE</b>: These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorTask extends BaseRedirectorHelperTask {

    public static final String JMX_SERVICE_PREFIX = "service:jmx:rmi:///jndi/rmi://";

    public static final String JMX_SERVICE_SUFFIX = "/jmxrmi";

    // ----------------------------------------------------- Instance Variables

    private String name = null;

    private String resultproperty;

    private String url = null;

    private String host = "localhost";

    private String port = "8050";

    private String password = null;

    private String username = null;

    private String ref = "jmx.server";

    private boolean echo = false;

    private boolean separatearrayresults = true;

    private String delimiter;

    private String unlessCondition;

    private String ifCondition;

    private final Properties properties = new Properties();

    // ------------------------------------------------------------- Properties

    /**
     * 远程 MbeanServer的名称
     */
    public String getName() {
        return (this.name);
    }

    public void setName(String objectName) {
        this.name = objectName;
    }

    public String getResultproperty() {
        return resultproperty;
    }

    public void setResultproperty(String propertyName) {
        this.resultproperty = propertyName;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String separator) {
        this.delimiter = separator;
    }

    public boolean isEcho() {
        return echo;
    }

    public void setEcho(boolean echo) {
        this.echo = echo;
    }

    public boolean isSeparatearrayresults() {
        return separatearrayresults;
    }

    public void setSeparatearrayresults(boolean separateArrayResults) {
        this.separatearrayresults = separateArrayResults;
    }

    /**
     * <code>Manager</code>应用的登录密码.
     */
    public String getPassword() {
        return (this.password);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * <code>JMX</code> MBeanServer的登录用户名.
     */
    public String getUsername() {
        return (this.username);
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * <code>JMX JSR 160</code> MBeanServer使用的URL.
     */
    public String getUrl() {
        return (this.url);
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * <code>JMX JSR 160</code> MBeanServer使用的Host.
     */
    public String getHost() {
        return (this.host);
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * <code>JMX JSR 160</code> MBeanServer使用的Port.
     */
    public String getPort() {
        return (this.port);
    }

    public void setPort(String port) {
        this.port = port;
    }

    public boolean isUseRef() {
        return ref != null && !"".equals(ref);
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String refId) {
        this.ref = refId;
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
     * 只有在当前项目中不存在给定名称的属性时才执行.
     *
     * @param c property name
     */
    public void setUnless(String c) {
        unlessCondition = c;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 执行指定命令. 此逻辑只执行所有子类所需的公共属性验证; 它不直接执行任何功能逻辑.
     *
     * @exception BuildException 如果出现验证错误
     */
    @Override
    public void execute() throws BuildException {
        if (testIfCondition() && testUnlessCondition()) {
            try {
                String error = null;

                MBeanServerConnection jmxServerConnection = getJMXConnection();
                error = jmxExecute(jmxServerConnection);
                if (error != null && isFailOnError()) {
                    // exception should be thrown only if failOnError == true
                    // or error line will be logged twice
                    throw new BuildException(error);
                }
            } catch (Exception e) {
                if (isFailOnError()) {
                    throw new BuildException(e);
                } else {
                    handleErrorOutput(e.getMessage());
                }
            } finally {
                closeRedirector();
            }
        }
    }

    /**
     * 创建一个新的JMX认证连接, 当用户名和密码已经设置时.
     *
     * @param url JMX连接使用的URL(如果指定了, 它是完整的URL，所以主机和端口将不被使用)
     * @param host JMX服务器的主机名
     * @param port JMX服务器的端口号
     * @param username 连接的用户名
     * @param password 与指定用户对应的凭据
     * 
     * @throws MalformedURLException 指定无效的URL
     * @throws IOException 其他连接错误
     * @return the JMX connection
     */
    public static MBeanServerConnection createJMXConnection(String url,
            String host, String port, String username, String password)
            throws MalformedURLException, IOException {
        String urlForJMX;
        if (url != null)
            urlForJMX = url;
        else
            urlForJMX = JMX_SERVICE_PREFIX + host + ":" + port
                    + JMX_SERVICE_SUFFIX;
        Map<String, String[]> environment = null;
        if (username != null && password != null) {
            String[] credentials = new String[2];
            credentials[0] = username;
            credentials[1] = password;
            environment = new HashMap<>();
            environment.put(JMXConnector.CREDENTIALS, credentials);
        }
        return JMXConnectorFactory.connect(new JMXServiceURL(urlForJMX),
                environment).getMBeanServerConnection();

    }

    /**
     * 测试if条件
     *
     * @return true 如果没有if条件, 或命名属性存在
     */
    protected boolean testIfCondition() {
        if (ifCondition == null || "".equals(ifCondition)) {
            return true;
        }
        return getProperty(ifCondition) != null;
    }

    /**
     * 测试 unless条件
     *
     * @return true 如果没有unless条件, 或者有一个命名的属性，但是它不存在
     */
    protected boolean testUnlessCondition() {
        if (unlessCondition == null || "".equals(unlessCondition)) {
            return true;
        }
        return getProperty(unlessCondition) == null;
    }

    /**
     * 从<em>ref</em>参数获取当前连接或创建一个新的!
     *
     * @param project Ant项目
     * @param url JMX连接使用的URL(如果指定了, 它是完整的URL，所以主机和端口将不被使用)
     * @param host JMX服务器的主机名
     * @param port JMX服务器的端口号
     * @param username 连接的用户名
     * @param password 与指定用户对应的凭据
     * @param refId 项目中检索的引用的ID
     * 
     * @throws MalformedURLException 指定无效的URL
     * @throws IOException 其他连接错误
     * @return the JMX connection
     */
    @SuppressWarnings("null")
    public static MBeanServerConnection accessJMXConnection(Project project,
            String url, String host, String port, String username,
            String password, String refId) throws MalformedURLException,
            IOException {
        MBeanServerConnection jmxServerConnection = null;
        boolean isRef = project != null && refId != null && refId.length() > 0;
        if (isRef) {
            Object pref = project.getReference(refId);
            try {
                jmxServerConnection = (MBeanServerConnection) pref;
            } catch (ClassCastException cce) {
                project.log("wrong object reference " + refId + " - "
                            + pref.getClass());
                return null;
            }
        }
        if (jmxServerConnection == null) {
            jmxServerConnection = createJMXConnection(url, host, port,
                    username, password);
        }
        if (isRef && jmxServerConnection != null) {
            project.addReference(refId, jmxServerConnection);
        }
        return jmxServerConnection;
    }

    // ------------------------------------------------------ protected Methods

    /**
     * get JMXConnection
     *
     * @throws MalformedURLException 指定无效的URL
     * @throws IOException 其他连接错误
     * @return the JMX connection
     */
    protected MBeanServerConnection getJMXConnection()
            throws MalformedURLException, IOException {

        MBeanServerConnection jmxServerConnection = null;
        if (isUseRef()) {
            Object pref = null ;
            if(getProject() != null) {
                pref = getProject().getReference(getRef());
                if (pref != null) {
                    try {
                        jmxServerConnection = (MBeanServerConnection) pref;
                    } catch (ClassCastException cce) {
                        getProject().log(
                            "Wrong object reference " + getRef() + " - "
                                    + pref.getClass());
                        return null;
                    }
                }
            }
            if (jmxServerConnection == null) {
                jmxServerConnection = accessJMXConnection(getProject(),
                        getUrl(), getHost(), getPort(), getUsername(),
                        getPassword(), getRef());
            }
        } else {
            jmxServerConnection = accessJMXConnection(getProject(), getUrl(),
                    getHost(), getPort(), getUsername(), getPassword(), null);
        }
        return jmxServerConnection;
    }

    /**
     * 根据所配置的属性执行指定的命令.
     * 完成任务后，输入流将被关闭，无论它是否成功执行.
     *
     * @param jmxServerConnection 应该使用的JMX连接
     * @return 某些情况下的错误消息字符串
     * @exception Exception 如果发生错误
     */
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
            throws Exception {

        if ((jmxServerConnection == null)) {
            throw new BuildException("Must open a connection!");
        } else if (isEcho()) {
            handleOutput("JMX Connection ref=" + ref + " is open!");
        }
        return null;
    }

    /**
     * 将字符串转换为数据类型 FIXME How we can transfer values from ant project reference store (ref)?
     *
     * @param value The value
     * @param valueType The type
     * @return The converted object
     */
    protected Object convertStringToType(String value, String valueType) {
        if ("java.lang.String".equals(valueType))
            return value;

        Object convertValue = value;
        if ("java.lang.Integer".equals(valueType) || "int".equals(valueType)) {
            try {
                convertValue = Integer.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho())
                    handleErrorOutput("Unable to convert to integer:" + value);
            }
        } else if ("java.lang.Long".equals(valueType)
                || "long".equals(valueType)) {
            try {
                convertValue = Long.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho())
                    handleErrorOutput("Unable to convert to long:" + value);
            }
        } else if ("java.lang.Boolean".equals(valueType)
                || "boolean".equals(valueType)) {
            convertValue = Boolean.valueOf(value);
        } else if ("java.lang.Float".equals(valueType)
                || "float".equals(valueType)) {
            try {
                convertValue = Float.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho())
                    handleErrorOutput("Unable to convert to float:" + value);
            }
        } else if ("java.lang.Double".equals(valueType)
                || "double".equals(valueType)) {
            try {
                convertValue = Double.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho())
                    handleErrorOutput("Unable to convert to double:" + value);
            }
        } else if ("javax.management.ObjectName".equals(valueType)
                || "name".equals(valueType)) {
            try {
                convertValue = new ObjectName(value);
            } catch (MalformedObjectNameException e) {
                if (isEcho())
                    handleErrorOutput("Unable to convert to ObjectName:"
                            + value);
            }
        } else if ("java.net.InetAddress".equals(valueType)) {
            try {
                convertValue = InetAddress.getByName(value);
            } catch (UnknownHostException exc) {
                if (isEcho())
                    handleErrorOutput("Unable to resolve host name:" + value);
            }
        }
        return convertValue;
    }

    /**
     * @param name context of result
     * @param result The result
     */
    protected void echoResult(String name, Object result) {
        if (isEcho()) {
            if (result.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(result); i++) {
                    handleOutput(name + "." + i + "=" + Array.get(result, i));
                }
            } else
                handleOutput(name + "=" + result);
        }
    }

    /**
     * 创建结果为一个属性，使用resultproperty属性指定的名称
     *
     * @param result The result
     */
    protected void createProperty(Object result) {
        if (resultproperty != null) {
            createProperty(resultproperty, result);
        }
    }

    /**
     * 创建属性名为 prefix的值得属性, 当结果是一个数组以及isSeparateArrayResults 是 true, resultproperty 作为前缀
     * (<code>resultproperty.0-array.length</code>保存结果数组长度到<code>resultproperty.length</code>.
     * 另一种选择是，用分隔符分隔结果(java.util.StringTokenizer is used).
     *
     * @param propertyPrefix 属性的前缀
     * @param result The result
     */
    protected void createProperty(String propertyPrefix, Object result) {
        if (propertyPrefix == null)
            propertyPrefix = "";
        if (result instanceof CompositeDataSupport) {
            CompositeDataSupport data = (CompositeDataSupport) result;
            CompositeType compositeType = data.getCompositeType();
            Set<String> keys = compositeType.keySet();
            for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
                String key = iter.next();
                Object value = data.get(key);
                OpenType<?> type = compositeType.getType(key);
                if (type instanceof SimpleType<?>) {
                    setProperty(propertyPrefix + "." + key, value);
                } else {
                    createProperty(propertyPrefix + "." + key, value);
                }
            }
        } else if (result instanceof TabularDataSupport) {
            TabularDataSupport data = (TabularDataSupport) result;
            for (Iterator<Object> iter = data.keySet().iterator(); iter.hasNext();) {
                Object key = iter.next();
                for (Iterator<?> iter1 = ((List<?>) key).iterator(); iter1.hasNext();) {
                    Object key1 = iter1.next();
                    CompositeData valuedata = data.get(new Object[] { key1 });
                    Object value = valuedata.get("value");
                    OpenType<?> type = valuedata.getCompositeType().getType(
                            "value");
                    if (type instanceof SimpleType<?>) {
                        setProperty(propertyPrefix + "." + key1, value);
                    } else {
                        createProperty(propertyPrefix + "." + key1, value);
                    }
                }
            }
        } else if (result.getClass().isArray()) {
            if (isSeparatearrayresults()) {
                int size = 0;
                for (int i = 0; i < Array.getLength(result); i++) {
                    if (setProperty(propertyPrefix + "." + size, Array.get(
                            result, i))) {
                        size++;
                    }
                }
                if (size > 0) {
                    setProperty(propertyPrefix + ".Length", Integer
                            .toString(size));
                }
            }
        } else {
            String delim = getDelimiter();
            if (delim != null) {
                StringTokenizer tokenizer = new StringTokenizer(result
                        .toString(), delim);
                int size = 0;
                for (; tokenizer.hasMoreTokens();) {
                    String token = tokenizer.nextToken();
                    if (setProperty(propertyPrefix + "." + size, token)) {
                        size++;
                    }
                }
                if (size > 0)
                    setProperty(propertyPrefix + ".Length", Integer
                            .toString(size));
            } else {
                setProperty(propertyPrefix, result.toString());
            }
        }
    }

    /**
     * @param property 属性名
     * @return 属性值
     */
    public String getProperty(String property) {
        Project currentProject = getProject();
        if (currentProject != null) {
            return currentProject.getProperty(property);
        } else {
            return properties.getProperty(property);
        }
    }

    /**
     * @param property 属性名
     * @param value 属性值
     * @return True 如果成功
     */
    public boolean setProperty(String property, Object value) {
        if (property != null) {
            if (value == null)
                value = "";
            if (isEcho()) {
                handleOutput(property + "=" + value.toString());
            }
            Project currentProject = getProject();
            if (currentProject != null) {
                currentProject.setNewProperty(property, value.toString());
            } else {
                properties.setProperty(property, value.toString());
            }
            return true;
        }
        return false;
    }
}
