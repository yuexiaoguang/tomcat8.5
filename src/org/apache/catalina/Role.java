package org.apache.catalina;


import java.security.Principal;


/**
 * <p>抽象安全角色, 在合适的环境中使用(希望处理<code>Principals</code>)，例如JAAS.</p>
 */
public interface Role extends Principal {


    // ------------------------------------------------------------- Properties


    /**
     * 返回描述信息.
     */
    public String getDescription();


    /**
     * 设置描述信息.
     *
     * @param description The new description
     */
    public void setDescription(String description);


    /**
     * 返回角色名称, 在 {@link UserDatabase}范围内必须是唯一的.
     */
    public String getRolename();


    /**
     * 设置角色名称, 在 {@link UserDatabase}范围内必须是唯一的.
     *
     * @param rolename The new role name
     */
    public void setRolename(String rolename);


    /**
     * 返回定义Role的{@link UserDatabase}
     */
    public UserDatabase getUserDatabase();


}
