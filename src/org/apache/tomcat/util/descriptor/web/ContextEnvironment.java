package org.apache.tomcat.util.descriptor.web;

/**
 * 表示应用程序环境条目, 作为部署描述符中<code>&lt;env-entry&gt;</code>元素的表示.
 */
public class ContextEnvironment extends ResourceBase {

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


    /**
     * 此环境条目的值.
     */
    private String value = null;

    public String getValue() {
        return (this.value);
    }

    public void setValue(String value) {
        this.value = value;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextEnvironment[");
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
        if (value != null) {
            sb.append(", value=");
            sb.append(value);
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
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        ContextEnvironment other = (ContextEnvironment) obj;
        if (override != other.override) {
            return false;
        }
        if (value == null) {
            if (other.value != null) {
                return false;
            }
        } else if (!value.equals(other.value)) {
            return false;
        }
        return true;
    }
}
