package org.apache.naming;

import java.util.Hashtable;

/**
 * 在JNDI上下文句柄的访问控制.
 */
public class ContextAccessController {

    // -------------------------------------------------------------- Variables

    /**
     * Catalina 不允许写的上下文名称.
     */
    private static final Hashtable<Object,Object> readOnlyContexts = new Hashtable<>();


    /**
     * 安全令牌存储库.
     */
    private static final Hashtable<Object,Object> securityTokens = new Hashtable<>();


    // --------------------------------------------------------- Public Methods

    /**
     * 设置security token. 只能设置一次.
     *
     * @param name Name of the Catalina context
     * @param token Security token
     */
    public static void setSecurityToken(Object name, Object token) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission(
                    ContextAccessController.class.getName()
                            + ".setSecurityToken"));
        }
        if ((!securityTokens.containsKey(name)) && (token != null)) {
            securityTokens.put(name, token);
        }
    }


    /**
     * 删除一个security.
     *
     * @param name Name of the Catalina context
     * @param token Security token
     */
    public static void unsetSecurityToken(Object name, Object token) {
        if (checkSecurityToken(name, token)) {
            securityTokens.remove(name);
        }
    }


    /**
     * 验证提交的security token.
     *
     * @param name Name of the Catalina context
     * @param token Submitted security token
     *
     * @return <code>true</code>如果提交的令牌等于存储库中的令牌，或者如果存储库中没有令牌.
     *         否则<code>false</code>
     */
    public static boolean checkSecurityToken(Object name, Object token) {
        Object refToken = securityTokens.get(name);
        return (refToken == null || refToken.equals(token));
    }


    /**
     * 允许写入上下文.
     *
     * @param name Name of the Catalina context
     * @param token Security token
     */
    public static void setWritable(Object name, Object token) {
        if (checkSecurityToken(name, token))
            readOnlyContexts.remove(name);
    }


    /**
     *设置Catalina上下文是否可写.
     *
     * @param name Name of the Catalina context
     */
    public static void setReadOnly(Object name) {
        readOnlyContexts.put(name, name);
    }


    /**
     * 上下文是否可写?
     *
     * @param name Name of the Catalina context
     *
     * @return <code>true</code>可写, 否则<code>false</code>
     */
    public static boolean isWritable(Object name) {
        return !(readOnlyContexts.containsKey(name));
    }
}

