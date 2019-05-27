package org.apache.tomcat.util.descriptor.web;

/**
 * 表示应用程序资源引用, 作为部署描述符中<code>&lt;res-env-refy&gt;</code>元素的表示.
 */
public class ContextResourceEnvRef extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties

    /**
     * 此环境条目是否允许应用程序部署描述符覆盖?
     */
    private boolean override = true;

    public boolean getOverride() {
        return (this.override);
    }

    public void setOverride(boolean override) {
        this.override = override;
    }

    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextResourceEnvRef[");
        sb.append("name=");
        sb.append(getName());
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        sb.append(", override=");
        sb.append(override);
        sb.append("]");
        return (sb.toString());
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (override ? 1231 : 1237);
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
        ContextResourceEnvRef other = (ContextResourceEnvRef) obj;
        if (override != other.override) {
            return false;
        }
        return true;
    }
}
