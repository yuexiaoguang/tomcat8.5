package org.apache.tomcat.util.security;

import java.security.Permission;

/**
 * 此接口由组件实现，以允许特权代码检查组件是否具有给定权限.
 * 当特权组件（即，容器）正在代表不可信组件（即，web应用程序）执行操作, 而当前线程没有通过不可信组件提供的代码源时, 通常使用这种方式.
 * 因为当前线程没有通过不可信组件提供的代码源，所以SecurityManager假定代码是可信的，因此不能使用标准的检查机制.
 */
public interface PermissionCheck {

    /**
     * 该组件是否具有给定权限?
     *
     * @param permission 要测试的权限
     *
     * @return {@code false} 如果启用SecurityManager且组件没有给定权限, 否则 {@code true}
     */
    boolean check(Permission permission);
}
