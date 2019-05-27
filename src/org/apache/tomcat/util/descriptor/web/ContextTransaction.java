package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;


/**
 * 表示应用程序资源引用, 作为部署描述符中<code>&lt;res-env-refy&gt;</code>元素的表示.
 */
public class ContextTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * 配置的属性的持有者.
     */
    private final HashMap<String, Object> properties = new HashMap<>();

    /**
     * @param name 属性名
     * 
     * @return 配置的属性.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * 设置已配置的属性.
     * 
     * @param name 属性名
     * @param value 属性值
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * 删除已配置的属性.
     * 
     * @param name 属性名
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    /**
     * 列出属性.
     * 
     * @return 属性名称迭代器
     */
    public Iterator<String> listProperties() {
        return properties.keySet().iterator();
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Transaction[");
        sb.append("]");
        return (sb.toString());
    }
}
