package org.apache.tomcat.util.descriptor.web;


/**
 * <p>表示Web应用程序的消息目标, 作为部署描述符中 <code>&lt;message-destination&gt;</code> 元素的表示.</p>
 */
public class MessageDestination extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * 此目标的显示名称.
     */
    private String displayName = null;

    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 此目标的大图标.
     */
    private String largeIcon = null;

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * 此目标的小图标.
     */
    private String smallIcon = null;

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("MessageDestination[");
        sb.append("name=");
        sb.append(getName());
        if (displayName != null) {
            sb.append(", displayName=");
            sb.append(displayName);
        }
        if (largeIcon != null) {
            sb.append(", largeIcon=");
            sb.append(largeIcon);
        }
        if (smallIcon != null) {
            sb.append(", smallIcon=");
            sb.append(smallIcon);
        }
        if (getDescription() != null) {
            sb.append(", description=");
            sb.append(getDescription());
        }
        sb.append("]");
        return (sb.toString());
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result +
                ((displayName == null) ? 0 : displayName.hashCode());
        result = prime * result +
                ((largeIcon == null) ? 0 : largeIcon.hashCode());
        result = prime * result +
                ((smallIcon == null) ? 0 : smallIcon.hashCode());
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
        MessageDestination other = (MessageDestination) obj;
        if (displayName == null) {
            if (other.displayName != null) {
                return false;
            }
        } else if (!displayName.equals(other.displayName)) {
            return false;
        }
        if (largeIcon == null) {
            if (other.largeIcon != null) {
                return false;
            }
        } else if (!largeIcon.equals(other.largeIcon)) {
            return false;
        }
        if (smallIcon == null) {
            if (other.smallIcon != null) {
                return false;
            }
        } else if (!smallIcon.equals(other.smallIcon)) {
            return false;
        }
        return true;
    }
}
