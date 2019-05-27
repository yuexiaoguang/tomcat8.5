package org.apache.catalina.security;

import java.security.BasicPermission;

/**
 * 当<code>deployXML</code>在Host等级被禁用时，假定到docBase的权限允许web应用使用任何存在的<code>META-INF/context.xml</code>.
 * 权限的名称应该是Web应用程序的基本名称.
 */
public class DeployXmlPermission extends BasicPermission {

    private static final long serialVersionUID = 1L;

    public DeployXmlPermission(String name) {
        super(name);
    }

    public DeployXmlPermission(String name, String actions) {
        super(name, actions);
    }
}
