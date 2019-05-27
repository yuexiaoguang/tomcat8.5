package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;



/**
 * Context元素的表示
 */
public class ResourceBase implements Serializable, Injectable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * 此资源的描述.
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }



    /**
     * 此资源的名称.
     */
    private String name = null;

    @Override
    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * 资源实现类的名称.
     */
    private String type = null;

    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        this.type = type;
    }


    /**
     * 配置的属性的持有者.
     */
    private final HashMap<String, Object> properties = new HashMap<>();

    /**
     * @param name 属性名称
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

    private final List<InjectionTarget> injectionTargets = new ArrayList<>();

    @Override
    public void addInjectionTarget(String injectionTargetName, String jndiName) {
        InjectionTarget target = new InjectionTarget(injectionTargetName, jndiName);
        injectionTargets.add(target);
    }

    @Override
    public List<InjectionTarget> getInjectionTargets() {
        return injectionTargets;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result +
                ((description == null) ? 0 : description.hashCode());
        result = prime * result +
                ((injectionTargets == null) ? 0 : injectionTargets.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result +
                ((properties == null) ? 0 : properties.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ResourceBase other = (ResourceBase) obj;
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (injectionTargets == null) {
            if (other.injectionTargets != null) {
                return false;
            }
        } else if (!injectionTargets.equals(other.injectionTargets)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (properties == null) {
            if (other.properties != null) {
                return false;
            }
        } else if (!properties.equals(other.properties)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }


    // -------------------------------------------------------- Package Methods

    /**
     * 关联的NamingResources.
     */
    private NamingResources resources = null;

    public NamingResources getNamingResources() {
        return (this.resources);
    }

    public void setNamingResources(NamingResources resources) {
        this.resources = resources;
    }
}
