package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;


/**
 * <p>表示Web应用程序的安全角色引用, 作为部署描述符中<code>&lt;security-role-ref&gt;</code>元素的表示.</p>
 */
public class SecurityRoleRef implements Serializable {

    private static final long serialVersionUID = 1L;


    // ------------------------------------------------------------- Properties

    /**
     * 角色名, 必须.
     */
    private String name = null;

    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * 可选的角色链接.
     */
    private String link = null;

    public String getLink() {
        return (this.link);
    }

    public void setLink(String link) {
        this.link = link;
    }



    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SecurityRoleRef[");
        sb.append("name=");
        sb.append(name);
        if (link != null) {
            sb.append(", link=");
            sb.append(link);
        }
        sb.append("]");
        return (sb.toString());
    }
}
