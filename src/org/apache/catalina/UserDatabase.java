package org.apache.catalina;

import java.util.Iterator;

/**
 * <p>{@link User}和{@link Group}的抽象数据库的表示，可以由应用程序维护,
 * 以及相应的{@link Role}定义, 并被一个{@link Realm} 用于身份验证和访问控制.</p>
 */
public interface UserDatabase {


    // ------------------------------------------------------------- Properties


    /**
     * @return 在这个用户数据库中定义的所有{@link Group}.
     */
    public Iterator<Group> getGroups();


    /**
     * @return 唯一的全局ID.
     */
    public String getId();


    /**
     * @return 在这个用户数据库中定义的所有{@link Role}.
     */
    public Iterator<Role> getRoles();


    /**
     * @return 在这个用户数据库中定义的所有{@link User}.
     */
    public Iterator<User> getUsers();


    // --------------------------------------------------------- Public Methods


    /**
     * 完成访问，关闭.
     *
     * @exception Exception 关闭期间出现异常
     */
    public void close() throws Exception;


    /**
     * 创建并返回一个新的{@link Group}
     *
     * @param groupname 组名，必须唯一
     * @param description 描述信息
     */
    public Group createGroup(String groupname, String description);


    /**
     * 创建并返回一个新的{@link Role}
     *
     * @param rolename 角色名(必须唯一)
     * @param description 描述信息
     */
    public Role createRole(String rolename, String description);


    /**
     * 创建并返回一个{@link User}
     *
     * @param username 登录用户名(必须唯一)
     * @param password 登录密码
     * @param fullName 用户全名
     */
    public User createUser(String username, String password,
                           String fullName);


    /**
     * 查找指定的{@link Group};或者返回<code>null</code>.
     *
     * @param groupname Name of the group to return
     */
    public Group findGroup(String groupname);


    /**
     * 查找指定的{@link Role};或者返回<code>null</code>.
     *
     * @param rolename Name of the role to return
     */
    public Role findRole(String rolename);


    /**
     * 查找指定的{@link User};或者返回<code>null</code>.
     *
     * @param username Name of the user to return
     */
    public User findUser(String username);


    /**
     * 初始化访问.
     *
     * @exception Exception 如果在打开期间抛出任何异常
     */
    public void open() throws Exception;


    /**
     * 移除指定的{@link Group}.
     *
     * @param group The group to be removed
     */
    public void removeGroup(Group group);


    /**
     * 移除指定的{@link Role}.
     *
     * @param role The role to be removed
     */
    public void removeRole(Role role);


    /**
     * 移除指定的{@link User}.
     *
     * @param user The user to be removed
     */
    public void removeUser(User user);


    /**
     * 将任何更新信息保存到持久存储位置
     *
     * @exception Exception 如果在保存期间抛出任何异常
     */
    public void save() throws Exception;


}
