package org.apache.catalina.users;


import java.util.ArrayList;
import java.util.Iterator;

import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.buf.StringUtils.Function;

public class MemoryGroup extends AbstractGroup {


    // ----------------------------------------------------------- Constructors


    /**
     * @param database 拥有这个组的{@link MemoryUserDatabase}
     * @param groupname 这个组的组名
     * @param description 这个组的描述
     */
    MemoryGroup(MemoryUserDatabase database,
                String groupname, String description) {

        super();
        this.database = database;
        setGroupname(groupname);
        setDescription(description);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个组所属的{@link MemoryUserDatabase}.
     */
    protected final MemoryUserDatabase database;


    /**
     * 关联的一组{@link Role}.
     */
    protected final ArrayList<Role> roles = new ArrayList<>();


    // ------------------------------------------------------------- Properties


    /**
     * 返回这个组拥有的一组{@link Role}.
     */
    @Override
    public Iterator<Role> getRoles() {
        synchronized (roles) {
            return (roles.iterator());
        }
    }


    /**
     * 返回定义这个Group的{@link UserDatabase}.
     */
    @Override
    public UserDatabase getUserDatabase() {
        return (this.database);
    }


    /**
     * 返回这个组包含的一组{@link User}.
     */
    @Override
    public Iterator<User> getUsers() {

        ArrayList<User> results = new ArrayList<>();
        Iterator<User> users = database.getUsers();
        while (users.hasNext()) {
            User user = users.next();
            if (user.isInGroup(this)) {
                results.add(user);
            }
        }
        return (results.iterator());
    }


    // --------------------------------------------------------- Public Methods


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
     * 这个组是否拥有指定的{@link Role}?
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


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("<group groupname=\"");
        sb.append(groupname);
        sb.append("\"");
        if (description != null) {
            sb.append(" description=\"");
            sb.append(description);
            sb.append("\"");
        }
        synchronized (roles) {
            if (roles.size() > 0) {
                sb.append(" roles=\"");
                StringUtils.join(roles, ',', new Function<Role>(){
                    @Override public String apply(Role t) { return t.getRolename(); }}, sb);
                sb.append("\"");
            }
        }
        sb.append("/>");
        return (sb.toString());
    }
}
