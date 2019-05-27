package org.apache.catalina.ant.jmx;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.taskdefs.condition.Condition;

public abstract class JMXAccessorConditionBase extends ProjectComponent implements Condition {

    private String url = null;
    private String host = "localhost";
    private String port = "8050";
    private String password = null;
    private String username = null;
    private String name = null;
    private String attribute;
    private String value;
    private String ref = "jmx.server" ;

    public String getAttribute() {
        return attribute;
    }
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public String getName() {
        return name;
    }
    public void setName(String objectName) {
        this.name = objectName;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getPort() {
        return port;
    }
    public void setPort(String port) {
        this.port = port;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }

    public String getRef() {
        return ref;
    }
    public void setRef(String refId) {
        this.ref = refId;
    }

    /**
     * 获取JMXConnection (默认查找从jmxOpen任务引用的<em>jmx.server</em>).
     *
     * @return active JMXConnection
     * @throws MalformedURLException JMX服务器的无效URL
     * @throws IOException Connection错误
     */
    protected MBeanServerConnection getJMXConnection()
            throws MalformedURLException, IOException {
        return JMXAccessorTask.accessJMXConnection(
                getProject(),
                getUrl(), getHost(),
                getPort(), getUsername(), getPassword(), ref);
    }

    /**
     * 获取MBeans 属性的值.
     *
     * @return The value
     */
    protected String accessJMXValue() {
        try {
            Object result = getJMXConnection().getAttribute(
                    new ObjectName(name), attribute);
            if(result != null)
                return result.toString();
        } catch (Exception e) {
            // ignore access or connection open errors
        }
        return null;
    }
}

