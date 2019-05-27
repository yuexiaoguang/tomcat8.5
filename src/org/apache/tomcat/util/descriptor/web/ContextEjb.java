package org.apache.tomcat.util.descriptor.web;

/**
 * 表示Web应用程序的EJB资源引用, 作为在部署描述符中的<code>&lt;ejb-ref&gt;</code>元素的表示.
 */
public class ContextEjb extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties



    /**
     * EJB主目录实现类的名称.
     */
    private String home = null;

    public String getHome() {
        return (this.home);
    }

    public void setHome(String home) {
        this.home = home;
    }


    /**
     * J2EE EJB定义的链接.
     */
    private String link = null;

    public String getLink() {
        return (this.link);
    }

    public void setLink(String link) {
        this.link = link;
    }

    /**
     * EJB远程实现类的名称.
     */
    private String remote = null;

    public String getRemote() {
        return (this.remote);
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextEjb[");
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
        if (home != null) {
            sb.append(", home=");
            sb.append(home);
        }
        if (remote != null) {
            sb.append(", remote=");
            sb.append(remote);
        }
        if (link != null) {
            sb.append(", link=");
            sb.append(link);
        }
        sb.append("]");
        return (sb.toString());

    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((home == null) ? 0 : home.hashCode());
        result = prime * result + ((link == null) ? 0 : link.hashCode());
        result = prime * result + ((remote == null) ? 0 : remote.hashCode());
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
        ContextEjb other = (ContextEjb) obj;
        if (home == null) {
            if (other.home != null) {
                return false;
            }
        } else if (!home.equals(other.home)) {
            return false;
        }
        if (link == null) {
            if (other.link != null) {
                return false;
            }
        } else if (!link.equals(other.link)) {
            return false;
        }
        if (remote == null) {
            if (other.remote != null) {
                return false;
            }
        } else if (!remote.equals(other.remote)) {
            return false;
        }
        return true;
    }
}
