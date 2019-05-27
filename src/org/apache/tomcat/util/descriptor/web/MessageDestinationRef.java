package org.apache.tomcat.util.descriptor.web;


/**
 * <p>表示Web应用程序的消息目标引用, 作为部署描述符中<code>&lt;message-destination-ref&gt;</code>元素的表示.</p>
 */
public class MessageDestinationRef extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * 此目标引用的链接.
     */
    private String link = null;

    public String getLink() {
        return (this.link);
    }

    public void setLink(String link) {
        this.link = link;
    }


    /**
     * 此目标引用的用法.
     */
    private String usage = null;

    public String getUsage() {
        return (this.usage);
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("MessageDestination[");
        sb.append("name=");
        sb.append(getName());
        if (link != null) {
            sb.append(", link=");
            sb.append(link);
        }
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (usage != null) {
            sb.append(", usage=");
            sb.append(usage);
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
        result = prime * result + ((link == null) ? 0 : link.hashCode());
        result = prime * result + ((usage == null) ? 0 : usage.hashCode());
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
        MessageDestinationRef other = (MessageDestinationRef) obj;
        if (link == null) {
            if (other.link != null) {
                return false;
            }
        } else if (!link.equals(other.link)) {
            return false;
        }
        if (usage == null) {
            if (other.usage != null) {
                return false;
            }
        } else if (!usage.equals(other.usage)) {
            return false;
        }
        return true;
    }
}
