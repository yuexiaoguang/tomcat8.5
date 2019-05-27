package org.apache.tomcat.util.modeler;

import javax.management.MBeanParameterInfo;

/**
 * <p><code>Parameter</code>描述符的内部配置信息.</p>
 */
public class ParameterInfo extends FeatureInfo {
    static final long serialVersionUID = 2222796006787664020L;
    // ----------------------------------------------------------- Constructors


    public ParameterInfo() {
        super();
    }

    /**
     * 创建并返回与此实例描述的参数对应的<code>MBeanParameterInfo</code>对象.
     */
    public MBeanParameterInfo createParameterInfo() {

        // 返回缓存的信息
        if (info == null) {
            info = new MBeanParameterInfo
                (getName(), getType(), getDescription());
        }
        return (MBeanParameterInfo)info;
    }
}
