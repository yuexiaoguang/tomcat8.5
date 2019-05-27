package org.apache.catalina.connector;

import java.io.Serializable;
import java.security.Principal;

/**
 * <strong>java.security.Principal</strong>通用实现类, 用于表示在协议处理程序级别上进行身份验证的主体.
 */
public class CoyotePrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;


    // ----------------------------------------------------------- Constructors

    public CoyotePrincipal(String name) {
        this.name = name;
    }


    // ------------------------------------------------------------- Properties


    /**
     * 这个Principal代表的用户的用户名.
     */
    protected final String name;

    @Override
    public String getName() {
        return (this.name);
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("CoyotePrincipal[");
        sb.append(this.name);
        sb.append("]");
        return (sb.toString());
    }
}
