package org.apache.catalina.realm;

import java.security.Principal;

/**
 * 该实现类总是返回null, 当验证用户名和密码时. 这是默认的Realm实现类，当没有指定其它Realm 时.
 */
public class NullRealm extends RealmBase {

    private static final String NAME = "NullRealm";

    @Override
    @Deprecated
    protected String getName() {
        return NAME;
    }

    @Override
    protected String getPassword(String username) {
        // Always return null
        return null;
    }

    @Override
    protected Principal getPrincipal(String username) {
        // Always return null
        return null;
    }
}
