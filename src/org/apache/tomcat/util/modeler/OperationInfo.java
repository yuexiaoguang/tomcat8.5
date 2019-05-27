package org.apache.tomcat.util.modeler;

import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;

/**
 * <p><code>Operation</code>描述符的内部配置信息.</p>
 */
public class OperationInfo extends FeatureInfo {

    static final long serialVersionUID = 4418342922072614875L;

    // ----------------------------------------------------------- Constructors

    public OperationInfo() {
        super();
    }


    // ----------------------------------------------------- Instance Variables

    protected String impact = "UNKNOWN";
    protected String role = "operation";
    protected final ReadWriteLock parametersLock = new ReentrantReadWriteLock();
    protected ParameterInfo parameters[] = new ParameterInfo[0];


    // ------------------------------------------------------------- Properties

    /**
     * @return 操作的"impact", 应该是以下值(不区分大小写) :
     *  "ACTION", "ACTION_INFO", "INFO", "UNKNOWN".
     */
    public String getImpact() {
        return this.impact;
    }

    public void setImpact(String impact) {
        if (impact == null)
            this.impact = null;
        else
            this.impact = impact.toUpperCase(Locale.ENGLISH);
    }


    /**
     * @return 这个操作的作用 ("getter", "setter", "operation", "constructor").
     */
    public String getRole() {
        return this.role;
    }

    public void setRole(String role) {
        this.role = role;
    }


    /**
     * @return 此操作的返回类型的完全限定Java类名.
     */
    public String getReturnType() {
        if(type == null) {
            type = "void";
        }
        return type;
    }

    public void setReturnType(String returnType) {
        this.type = returnType;
    }

    /**
     * @return 此操作的参数集.
     */
    public ParameterInfo[] getSignature() {
        Lock readLock = parametersLock.readLock();
        readLock.lock();
        try {
            return this.parameters;
        } finally {
            readLock.unlock();
        }
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 将参数添加到此操作的参数集中.
     *
     * @param parameter 参数描述符
     */
    public void addParameter(ParameterInfo parameter) {

        Lock writeLock = parametersLock.writeLock();
        writeLock.lock();
        try {
            ParameterInfo results[] = new ParameterInfo[parameters.length + 1];
            System.arraycopy(parameters, 0, results, 0, parameters.length);
            results[parameters.length] = parameter;
            parameters = results;
            this.info = null;
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * 创建并返回与此实例描述的属性对应的<code>ModelMBeanOperationInfo</code>对象.
     */
    MBeanOperationInfo createOperationInfo() {

        // 返回缓存的信息
        if (info == null) {
            // Create and return a new information object
            int impact = MBeanOperationInfo.UNKNOWN;
            if ("ACTION".equals(getImpact()))
                impact = MBeanOperationInfo.ACTION;
            else if ("ACTION_INFO".equals(getImpact()))
                impact = MBeanOperationInfo.ACTION_INFO;
            else if ("INFO".equals(getImpact()))
                impact = MBeanOperationInfo.INFO;

            info = new MBeanOperationInfo(getName(), getDescription(),
                                          getMBeanParameterInfo(),
                                          getReturnType(), impact);
        }
        return (MBeanOperationInfo)info;
    }

    protected MBeanParameterInfo[] getMBeanParameterInfo() {
        ParameterInfo params[] = getSignature();
        MBeanParameterInfo parameters[] =
            new MBeanParameterInfo[params.length];
        for (int i = 0; i < params.length; i++)
            parameters[i] = params[i].createParameterInfo();
        return parameters;
    }
}
