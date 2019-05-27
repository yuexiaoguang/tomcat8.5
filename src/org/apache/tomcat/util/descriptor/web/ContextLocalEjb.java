package org.apache.tomcat.util.descriptor.web;

/**
 * 表示Web应用程序的本地EJB资源引用, 作为部署描述符中 <code>&lt;ejb-local-ref&gt;</code> 元素的表示.
 */
public class ContextLocalEjb extends ResourceBase {

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
     * EJB本地实现类的名称.
     */
    private String local = null;

    public String getLocal() {
        return (this.local);
    }

    public void setLocal(String local) {
        this.local = local;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextLocalEjb[");
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
        if (link != null) {
            sb.append(", link=");
            sb.append(link);
        }
        if (local != null) {
            sb.append(", local=");
            sb.append(local);
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
        result = prime * result + ((local == null) ? 0 : local.hashCode());
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
        ContextLocalEjb other = (ContextLocalEjb) obj;
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
        if (local == null) {
            if (other.local != null) {
                return false;
            }
        } else if (!local.equals(other.local)) {
            return false;
        }
        return true;
    }
}
