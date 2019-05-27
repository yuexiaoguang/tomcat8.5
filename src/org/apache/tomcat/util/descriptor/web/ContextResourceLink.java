package org.apache.tomcat.util.descriptor.web;



/**
 * 表示Web应用程序的资源链接, 作为部署描述符中<code>&lt;ResourceLink&gt;</code>元素的表示.
 */
public class ContextResourceLink extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties

   /**
     * 此资源的全局名称.
     */
    private String global = null;
    /**
     * 用于创建对象的工厂
     */
    private String factory = null;

    public String getGlobal() {
        return (this.global);
    }

    public void setGlobal(String global) {
        this.global = global;
    }

    public String getFactory() {
        return factory;
    }

    public void setFactory(String factory) {
        this.factory = factory;
    }
    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextResourceLink[");
        sb.append("name=");
        sb.append(getName());
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (getGlobal() != null) {
            sb.append(", global=");
            sb.append(getGlobal());
        }
        sb.append("]");
        return (sb.toString());
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((factory == null) ? 0 : factory.hashCode());
        result = prime * result + ((global == null) ? 0 : global.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ContextResourceLink other = (ContextResourceLink) obj;
        if (factory == null) {
            if (other.factory != null) {
                return false;
            }
        } else if (!factory.equals(other.factory)) {
            return false;
        }
        if (global == null) {
            if (other.global != null) {
                return false;
            }
        } else if (!global.equals(other.global)) {
            return false;
        }
        return true;
    }
}
