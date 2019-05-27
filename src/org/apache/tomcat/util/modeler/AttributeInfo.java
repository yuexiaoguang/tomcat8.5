package org.apache.tomcat.util.modeler;

import javax.management.MBeanAttributeInfo;


/**
 * <p><code>Attribute</code>描述符的内部配置信息.</p>
 */
public class AttributeInfo extends FeatureInfo {
    static final long serialVersionUID = -2511626862303972143L;

    // ----------------------------------------------------- Instance Variables
    protected String displayName = null;

    // 有关使用方法的信息
    protected String getMethod = null;
    protected String setMethod = null;
    protected boolean readable = true;
    protected boolean writeable = true;
    protected boolean is = false;

    // ------------------------------------------------------------- Properties

    /**
     * @return 此属性的显示名称.
     */
    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return 属性getter方法的名称，如果是非标准的.
     */
    public String getGetMethod() {
        if(getMethod == null)
            getMethod = getMethodName(getName(), true, isIs());
        return (this.getMethod);
    }

    public void setGetMethod(String getMethod) {
        this.getMethod = getMethod;
    }

    /**
     * boolean 属性是否有一个 "is" getter?
     * 
     * @return <code>true</code>是
     */
    public boolean isIs() {
        return (this.is);
    }

    public void setIs(boolean is) {
        this.is = is;
    }


    /**
     * 管理应用程序是否可以读取此属性?
     * @return <code>true</code>
     */
    public boolean isReadable() {
        return (this.readable);
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
    }


    /**
     * @return 属性setter方法的名称，如果是非标准的.
     */
    public String getSetMethod() {
        if( setMethod == null )
            setMethod = getMethodName(getName(), false, false);
        return (this.setMethod);
    }

    public void setSetMethod(String setMethod) {
        this.setMethod = setMethod;
    }

    /**
     * 此属性是否可由管理应用程序写入?
     * @return <code>true</code>
     */
    public boolean isWriteable() {
        return (this.writeable);
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 创建并返回与此实例描述的属性对应的<code>ModelMBeanAttributeInfo</code>对象.
     * 
     * @return 属性信息
     */
    MBeanAttributeInfo createAttributeInfo() {
        // Return our cached information (if any)
        if (info == null) {
            info = new MBeanAttributeInfo(getName(), getType(), getDescription(),
                            isReadable(), isWriteable(), false);
        }
        return (MBeanAttributeInfo)info;
    }

    // -------------------------------------------------------- Private Methods


    /**
     * 创建并返回默认属性getter或setter方法的名称, 根据指定的值.
     *
     * @param name 属性名称
     * @param getter 是否需要 get 方法 (versus a set method)?
     * @param is 如果返回一个getter, 是否是 "is" 格式?
     * 
     * @return 方法名称
     */
    private String getMethodName(String name, boolean getter, boolean is) {

        StringBuilder sb = new StringBuilder();
        if (getter) {
            if (is)
                sb.append("is");
            else
                sb.append("get");
        } else
            sb.append("set");
        sb.append(Character.toUpperCase(name.charAt(0)));
        sb.append(name.substring(1));
        return (sb.toString());
    }
}
