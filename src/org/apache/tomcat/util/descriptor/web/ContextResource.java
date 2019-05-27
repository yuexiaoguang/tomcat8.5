package org.apache.tomcat.util.descriptor.web;

/**
 * 表示Web应用程序的资源引用, 作为部署描述符中 <code>&lt;resource-ref&gt;</code>元素的表示.
 */
public class ContextResource extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * 此资源的授权要求 (<code>Application</code> 或 <code>Container</code>).
     */
    private String auth = null;

    public String getAuth() {
        return (this.auth);
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    /**
     * 此资源工厂的共享范围 (<code>Shareable</code> 或 <code>Unshareable</code>).
     */
    private String scope = "Shareable";

    public String getScope() {
        return (this.scope);
    }

    public void setScope(String scope) {
        this.scope = scope;
    }


    /**
     * 此资源是否为单例资源. 默认值为true, 因为这是用户期望的, 尽管JavaEE规范暗示默认值应为false.
     */
    private boolean singleton = true;

    public boolean getSingleton() {
        return singleton;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }


    /**
     * 当清理资源时, 要调用的零参数方法的名称. 此方法必须加速清理, 否则将通过垃圾回收清理资源.
     */
    private String closeMethod = null;

    public String getCloseMethod() {
        return closeMethod;
    }

    public void setCloseMethod(String closeMethod) {
        this.closeMethod = closeMethod;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextResource[");
        sb.append("name=");
        sb.append(getName());
        if (getDescription() != null) {
            sb.append(", description=");
            sb.append(getDescription());
        }
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (auth != null) {
            sb.append(", auth=");
            sb.append(auth);
        }
        if (scope != null) {
            sb.append(", scope=");
            sb.append(scope);
        }
        sb.append("]");
        return (sb.toString());
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((auth == null) ? 0 : auth.hashCode());
        result = prime * result +
                ((closeMethod == null) ? 0 : closeMethod.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
        result = prime * result + (singleton ? 1231 : 1237);
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
        ContextResource other = (ContextResource) obj;
        if (auth == null) {
            if (other.auth != null) {
                return false;
            }
        } else if (!auth.equals(other.auth)) {
            return false;
        }
        if (closeMethod == null) {
            if (other.closeMethod != null) {
                return false;
            }
        } else if (!closeMethod.equals(other.closeMethod)) {
            return false;
        }
        if (scope == null) {
            if (other.scope != null) {
                return false;
            }
        } else if (!scope.equals(other.scope)) {
            return false;
        }
        if (singleton != other.singleton) {
            return false;
        }
        return true;
    }
}
