package org.apache.catalina.users;


import java.util.ArrayList;
import java.util.Iterator;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.UserDatabase;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.buf.StringUtils.Function;
import org.apache.tomcat.util.security.Escape;

public class MemoryUser extends AbstractUser {


    // ----------------------------------------------------------- Constructors


    /**
     * @param database 拥有这个用户的{@link MemoryUserDatabase}
     * @param username 新用户的用户名
     * @param password 新用户的登录密码
     * @param fullName 新用户的全名
     */
    MemoryUser(MemoryUserDatabase database, String username,
               String password, String fullName) {

        super();
        this.database = database;
        setUsername(username);
        setPassword(password);
        setFullName(fullName);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 拥有这个用户的{@link MemoryUserDatabase}.
     */
    protected final MemoryUserDatabase database;


    /**
     * 包含这个用户的一组{@link Group}.
     */
    protected final ArrayList<Group> groups = new ArrayList<>();


    /**
     * 用户拥有的一组{@link Role}.
     */
    protected final ArrayList<Role> roles = new ArrayList<>();


    // ------------------------------------------------------------- Properties


    /**
     * 返回包含这个用户的一组{@link Group}.
     */
    @Override
    public Iterator<Group> getGroups() {
        synchronized (groups) {
            return (groups.iterator());
        }
    }


    /**
     * 返回用户拥有的所有{@link Role}.
     */
    @Override
    public Iterator<Role> getRoles() {
        synchronized (roles) {
            return (roles.iterator());
        }
    }


    /**
     * 返回定义这个用户的{@link UserDatabase}.
     */
    @Override
    public UserDatabase getUserDatabase() {
        return (this.database);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个{@link Group}.
     *
     * @param group The new group
     */
    @Override
    public void addGroup(Group group) {
        synchronized (groups) {
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }
    }


    /**
     * 添加一个{@link Role}.
     *
     * @param role The new role
     */
    @Override
    public void addRole(Role role) {

        synchronized (roles) {
            if (!roles.contains(role)) {
                roles.add(role);
            }
        }

    }


    /**
     * 是否包含此用户?
     *
     * @param group The group to check
     */
    @Override
    public boolean isInGroup(Group group) {
        synchronized (groups) {
            return (groups.contains(group));
        }
    }


    /**
     * 这个用户是否拥有指定的{@link Role}?
     * 这个方法不检查从{@link Group} 继承下来的角色.
     *
     * @param role The role to check
     */
    @Override
    public boolean isInRole(Role role) {

        synchronized (roles) {
            return (roles.contains(role));
        }
    }


    /**
     * 删除{@link Group}.
     *
     * @param group The old group
     */
    @Override
    public void removeGroup(Group group) {
        synchronized (groups) {
            groups.remove(group);
        }
    }


    /**
     * 删除所有的{@link Group}
     */
    @Override
    public void removeGroups() {
        synchronized (groups) {
            groups.clear();
        }
    }


    /**
     * 删除一个{@link Role}.
     *
     * @param role The old role
     */
    @Override
    public void removeRole(Role role) {
        synchronized (roles) {
            roles.remove(role);
        }
    }


    /**
     * 删除所有的{@link Role}.
     */
    @Override
    public void removeRoles() {
        synchronized (roles) {
            roles.clear();
        }
    }


    /**
     * <p><strong>IMPLEMENTATION NOTE</strong> - 向后兼容, 处理这个条目的reader接收
     * <code>username</code>或<code>name</code>作为 username属性.</p>
     */
    public String toXml() {

        StringBuilder sb = new StringBuilder("<user username=\"");
        sb.append(Escape.xml(username));
        sb.append("\" password=\"");
        sb.append(Escape.xml(password));
        sb.append("\"");
        if (fullName != null) {
            sb.append(" fullName=\"");
            sb.append(Escape.xml(fullName));
            sb.append("\"");
        }
        synchronized (groups) {
            if (groups.size() > 0) {
                sb.append(" groups=\"");
                StringUtils.join(groups, ',', new Function<Group>() {
                    @Override public String apply(Group t) {
                        return Escape.xml(t.getGroupname());
                    }
                }, sb);
                sb.append("\"");
            }
        }
        synchronized (roles) {
            if (roles.size() > 0) {
                sb.append(" roles=\"");
                StringUtils.join(roles, ',', new Function<Role>() {
                    @Override public String apply(Role t) {
                        return Escape.xml(t.getRolename());
                    }
                }, sb);
                sb.append("\"");
            }
        }
        sb.append("/>");
        return sb.toString();
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("User username=\"");
        sb.append(Escape.xml(username));
        sb.append("\"");
        if (fullName != null) {
            sb.append(", fullName=\"");
            sb.append(Escape.xml(fullName));
            sb.append("\"");
        }
        synchronized (groups) {
            if (groups.size() > 0) {
                sb.append(", groups=\"");
                StringUtils.join(groups, ',', new Function<Group>() {
                    @Override public String apply(Group t) {
                        return Escape.xml(t.getGroupname());
                    }
                }, sb);
                sb.append("\"");
            }
        }
        synchronized (roles) {
            if (roles.size() > 0) {
                sb.append(", roles=\"");
                StringUtils.join(roles, ',', new Function<Role>() {
                    @Override public String apply(Role t) {
                        return Escape.xml(t.getRolename());
                    }
                }, sb);
                sb.append("\"");
            }
        }
        return sb.toString();
    }
}
