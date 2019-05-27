package org.apache.tomcat.util.modeler;

import java.io.Serializable;

import javax.management.MBeanFeatureInfo;

/**
 * <p><code>AttributeInfo</code>和<code>OperationInfo</code>类的基类,  将用于收集为了管理公开的<code>ModelMBean</code> bean的配置信息.</p>
 */
public class FeatureInfo implements Serializable {
    static final long serialVersionUID = -911529176124712296L;

    protected String description = null;
    protected String name = null;
    protected MBeanFeatureInfo info = null;

    // all have type except Constructor
    protected String type = null;


    // ------------------------------------------------------------- Properties

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * @return 此功能的名称, 在同一个集合中的功能必须是唯一的.
     */
    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 此元素的完全限定Java类名.
     */
    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        this.type = type;
    }
}
