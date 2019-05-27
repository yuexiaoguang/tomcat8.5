package org.apache.catalina;


import java.security.Principal;
import java.util.Iterator;


/**
 * <p>{@link UserDatabase}中的用户的抽象表示 . 
 * 每个用户都可选地与一组{@link Group}相关联,通过这些组,他或她继承附加的安全角色，并可选地分配一组特定的{@link Role}</p>
 */
public interface User extends Principal {


    // ------------------------------------------------------------- Properties


    /**
     * 返回用户的全名.
     */
    public String getFullName();


    /**
     * 设置用户的全名.
     *
     * @param fullName 新的全名
     */
    public void setFullName(String fullName);


    /**
     * 返回用户所属的{@link Group}集合.
     */
    public Iterator<Group> getGroups();


    /**
     * 返回用户的登录密码, 可选的前缀编码方案，花括号包围的标识符, 例如<code>{md5}xxxxx</code>.
     */
    public String getPassword();


    /**
     * 设置此用户的登录密码, 可选的前缀编码方案，花括号包围的标识符, 例如<code>{md5}xxxxx</code>.
     *
     * @param password 新登录密码
     */
    public void setPassword(String password);


    /**
     * 返回指定给用户的{@link Role}集合
     */
    public Iterator<Role> getRoles();


    /**
     * 返回定义此用户的{@link UserDatabase}
     */
    public UserDatabase getUserDatabase();


    /**
     * 返回登录用户名, 必须在{@link UserDatabase}范围内唯一.
     */
    public String getUsername();


    /**
     * 设置登录用户名, 必须在{@link UserDatabase}范围内唯一.
     *
     * @param username 新登录用户名
     */
    public void setUsername(String username);


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个新的用户所属的{@link Group}.
     *
     * @param group The new group
     */
    public void addGroup(Group group);


    /**
     * 添加一个用户的{@link Role}.
     *
     * @param role The new role
     */
    public void addRole(Role role);


    /**
     * 用户是否在指定的{@link Group}组中?
     *
     * @param group The group to check
     */
    public boolean isInGroup(Group group);


    /**
     * 用户是否具有指定的{@link Role}? 
     * 此方法不检查继承的角色基于 {@link Group} 会员.
     *
     * @param role The role to check
     */
    public boolean isInRole(Role role);


    /**
     * 移除一个用户的所属分组{@link Group}.
     *
     * @param group The old group
     */
    public void removeGroup(Group group);


    /**
     * 移除所有的用户所属分组{@link Group}.
     */
    public void removeGroups();


    /**
     * 移除一个用户的角色{@link Role}.
     *
     * @param role The old role
     */
    public void removeRole(Role role);


    /**
     * 移除用户所有的角色{@link Role}.
     */
    public void removeRoles();


}
