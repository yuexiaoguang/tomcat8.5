package org.apache.tomcat.util.compat;

import java.security.AccessController;
import java.security.PrivilegedAction;

public class JrePlatform {

    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String OS_NAME_WINDOWS_PREFIX = "Windows";

    static {
        /*
         * 有几个地方
         * a)Java API的行为取决于底层平台;
         * b)那些行为差异对Tomcat有影响.
         *
         * 因此，Tomcat需要能够确定它运行的平台以解决这些差异.
         *
         * 在理想的世界中，这段代码不存在.
         */

        // 此检查来自Apache Commons Lang中的检查
        String osName;
        if (System.getSecurityManager() == null) {
            osName = System.getProperty(OS_NAME_PROPERTY);
        } else {
            osName = AccessController.doPrivileged(
                    new PrivilegedAction<String>() {

                    @Override
                    public String run() {
                        return System.getProperty(OS_NAME_PROPERTY);
                    }
                });
        }

        IS_WINDOWS = osName.startsWith(OS_NAME_WINDOWS_PREFIX);
    }


    public static final boolean IS_WINDOWS;
}
