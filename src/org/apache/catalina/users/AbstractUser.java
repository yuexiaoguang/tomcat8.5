package org.apache.catalina.users;


import java.util.Iterator;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;


/**
 * <p>{@link User}实现类的基类.</p>
 */
public abstract class AbstractUser implements User {


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个用户的全名.
     */
    protected String fullName = null;


    /**
     * 这个用户的登录密码
     */
    protected String password = null;


    /**
     * 用户的登录用户名
     */
    protected String username = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回这个用户的全名.
     */
    @Override
    public String getFullName() {
        return (this.fullName);
    }


    /**
     * 设置这个用户的全名.
     *
     * @param fullName The new full name
     */
    @Override
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }


    /**
     * 返回用户所属的一组{@link Group}.
     */
    @Override
    public abstract Iterator<Group> getGroups();


    /**
     * 返回此用户的登录密码,可选的前缀编码方案，花括号包围的标识符, 例如
     * <code>{md5}xxxxx</code>.
     */
    @Override
    public String getPassword() {
        return (this.password);
    }


    /**
     * 设置此用户的登录密码,可选的前缀编码方案，花括号包围的标识符, 例如
     * <code>{md5}xxxxx</code>.
     *
     * @param password The new logon password
     */
    @Override
    public void setPassword(String password) {
        this.password = password;
    }


    /**
     * 返回专门指定给该用户的一组{@link Role}.
     */
    @Override
    public abstract Iterator<Role> getRoles();


    /**
     * 返回此用户的登录用户名, 必须在{@link UserDatabase}范围内唯一.
     */
    @Override
    public String getUsername() {
        return (this.username);
    }


    /**
     * 设置此用户的登录用户名, 必须在{@link UserDatabase}范围内唯一.
     *
     * @param username The new logon username
     */
    @Override
    public void setUsername(String username) {
        this.username = username;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加用户所属的{@link Group}.
     *
     * @param group The new group
     */
    @Override
    public abstract void addGroup(Group group);


    /**
     * 添加专门指定给该用户的{@link Role}.
     *
     * @param role The new role
     */
    @Override
    public abstract void addRole(Role role);


    /**
     * 用户是否在指定的{@link Group}中?
     *
     * @param group The group to check
     */
    @Override
    public abstract boolean isInGroup(Group group);


    /**
     * 用户是否拥有指定的{@link Role}?  此方法不检查基于{@link Group}成员继承的角色.
     *
     * @param role The role to check
     */
    @Override
    public abstract boolean isInRole(Role role);


    /**
     * 删除一个{@link Group}.
     *
     * @param group The old group
     */
    @Override
    public abstract void removeGroup(Group group);


    /**
     * 删除所有的{@link Group}.
     */
    @Override
    public abstract void removeGroups();


    /**
     * 删除一个{@link Role}.
     *
     * @param role The old role
     */
    @Override
    public abstract void removeRole(Role role);


    /**
     * 删除所有的{@link Role}.
     */
    @Override
    public abstract void removeRoles();


    // ------------------------------------------------------ Principal Methods


    /**
     * 让principal的名称和组名相同.
     */
    @Override
    public String getName() {
        return (getUsername());
    }
}
