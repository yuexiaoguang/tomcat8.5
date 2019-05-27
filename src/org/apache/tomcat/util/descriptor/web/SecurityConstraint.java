package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.HttpConstraintElement;
import javax.servlet.HttpMethodConstraintElement;
import javax.servlet.ServletSecurityElement;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;


/**
 * 表示Web应用程序的安全性约束元素, 作为部署描述符中<code>&lt;security-constraint&gt;</code>元素的表示.
 * <p>
 * <b>WARNING</b>:  假设仅在单个线程的上下文中创建和修改此类的实例, 在实例对应用程序的其余部分可见之前.
 * 之后，只能进行读访问.  因此，此类中的读取和写入访问都不会同步.
 */
public class SecurityConstraint extends XmlEncodingBase implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_ALL_ROLES = "*";
    public static final String ROLE_ALL_AUTHENTICATED_USERS = "**";

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);


    // ----------------------------------------------------------- Constructors

    public SecurityConstraint() {
        super();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 是“所有角色”的通配符 - {@link #ROLE_ALL_ROLES} - 包含在此安全约束的授权约束中?
     */
    private boolean allRoles = false;


    /**
     * 是“所有经过身份验证的用户”的通配符 - {@link #ROLE_ALL_AUTHENTICATED_USERS} - 包含在此安全约束的授权约束中?
     */
    private boolean authenticatedUsers = false;


    /**
     * 此安全性约束中是否包含授权约束?
     * 这是必要的，以区分请求 auth-constraint 没有角色（表示根本没有直接访问）的情况，而不是缺少auth-constraint，这意味着没有访问控制检查.
     */
    private boolean authConstraint = false;


    /**
     * 允许访问受此安全约束保护的资源的角色集.
     */
    private String authRoles[] = new String[0];


    /**
     * 受此安全性约束保护的Web资源集合集.
     */
    private SecurityCollection collections[] = new SecurityCollection[0];


    /**
     * 此安全性约束的显示名称.
     */
    private String displayName = null;


    /**
     * 此安全性约束的用户数据约束.  必须是 NONE, INTEGRAL, CONFIDENTIAL.
     */
    private String userConstraint = "NONE";


    // ------------------------------------------------------------- Properties


    /**
     * 此“身份验证”约束中是否包含“所有角色”通配符?
     * 
     * @return <code>true</code> 所有角色
     */
    public boolean getAllRoles() {
        return this.allRoles;
    }


    /**
     * 此身份验证约束中是否包含“所有经过身份验证的用户”通配符?
     * 
     * @return <code>true</code> 所有经过身份验证的用户
     */
    public boolean getAuthenticatedUsers() {
        return this.authenticatedUsers;
    }


    /**
     * 返回此安全性约束的授权约束存在标志.
     * 
     * @return <code>true</code> 如果这需要授权
     */
    public boolean getAuthConstraint() {
        return this.authConstraint;
    }


    /**
     * 为此安全性约束设置授权约束存在标志.
     * 
     * @param authConstraint
     */
    public void setAuthConstraint(boolean authConstraint) {
        this.authConstraint = authConstraint;
    }


    /**
     * @return 此安全性约束的显示名称.
     */
    public String getDisplayName() {
        return this.displayName;
    }


    /**
     * 设置此安全性约束的显示名称.
     * 
     * @param displayName
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 返回此安全性约束的用户数据约束.
     * 
     * @return 用户约束
     */
    public String getUserConstraint() {
        return userConstraint;
    }


    /**
     * 为此安全性约束设置用户数据约束.
     *
     * @param userConstraint 用户数据约束
     */
    public void setUserConstraint(String userConstraint) {
        if (userConstraint != null)
            this.userConstraint = userConstraint;

    }


    /**
     * 在极少数情况下调用, 应用程序定义名为“**”的角色.
     */
    public void treatAllAuthenticatedUsersAsApplicationRole() {
        if (authenticatedUsers) {
            authenticatedUsers = false;

            String results[] = new String[authRoles.length + 1];
            for (int i = 0; i < authRoles.length; i++)
                results[i] = authRoles[i];
            results[authRoles.length] = ROLE_ALL_AUTHENTICATED_USERS;
            authRoles = results;
            authConstraint = true;
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加授权角色, 该角色是允许访问受此安全约束保护的资源的角色名称.
     *
     * @param authRole 要添加的角色名称
     */
    public void addAuthRole(String authRole) {

        if (authRole == null)
            return;

        if (ROLE_ALL_ROLES.equals(authRole)) {
            allRoles = true;
            return;
        }

        if (ROLE_ALL_AUTHENTICATED_USERS.equals(authRole)) {
            authenticatedUsers = true;
            return;
        }

        String results[] = new String[authRoles.length + 1];
        for (int i = 0; i < authRoles.length; i++)
            results[i] = authRoles[i];
        results[authRoles.length] = authRole;
        authRoles = results;
        authConstraint = true;
    }


    /**
     * 向受此安全约束保护的用户添加新的Web资源集合.
     *
     * @param collection Web资源集合
     */
    public void addCollection(SecurityCollection collection) {

        if (collection == null)
            return;

        collection.setCharset(getCharset());

        SecurityCollection results[] =
            new SecurityCollection[collections.length + 1];
        for (int i = 0; i < collections.length; i++)
            results[i] = collections[i];
        results[collections.length] = collection;
        collections = results;
    }


    /**
     * 检查一个角色.
     *
     * @param role 要检查的角色名称
     * 
     * @return <code>true</code> 如果允许指定的角色访问受此安全约束保护的资源.
     */
    public boolean findAuthRole(String role) {

        if (role == null)
            return false;
        for (int i = 0; i < authRoles.length; i++) {
            if (role.equals(authRoles[i]))
                return true;
        }
        return false;
    }


    /**
     * 返回允许访问受此安全约束保护的资源的角色集.
     * 如果没有定义, 返回零长度数组 (这意味着允许所有经过身份验证的用户访问).
     * 
     * @return 角色数组
     */
    public String[] findAuthRoles() {
        return (authRoles);
    }


    /**
     * 返回指定名称的Web资源集合; 否则<code>null</code>.
     *
     * @param name 要返回的Web资源集合名称
     * 
     * @return the collection
     */
    public SecurityCollection findCollection(String name) {

        if (name == null)
            return (null);
        for (int i = 0; i < collections.length; i++) {
            if (name.equals(collections[i].getName()))
                return (collections[i]);
        }
        return (null);
    }


    /**
     * 返回受此安全约束保护的所有Web资源集合.
     * 如果没有, 返回零长度数组.
     */
    public SecurityCollection[] findCollections() {
        return (collections);
    }


    /**
     * 检查约束是否适用于URI和方法.
     * 
     * @param uri 要检查的上下文相对URI
     * @param method 正在使用的请求方法
     * 
     * @return <code>true</code> 如果指定的上下文相关URI（和关联的HTTP方法）受此安全性约束的保护.
     */
    public boolean included(String uri, String method) {

        // 没有有效的请求方法, 无法匹配
        if (method == null)
            return false;

        // 检查此约束中包含的所有集合
        for (int i = 0; i < collections.length; i++) {
            if (!collections[i].findMethod(method))
                continue;
            String patterns[] = collections[i].findPatterns();
            for (int j = 0; j < patterns.length; j++) {
                if (matchPattern(uri, patterns[j]))
                    return true;
            }
        }

        // 此约束中不包含任何集合与此请求匹配
        return false;
    }


    /**
     * 从允许访问受此安全约束保护的资源的角色集中, 删除指定的角色.
     *
     * @param authRole 要删除的角色名称
     */
    public void removeAuthRole(String authRole) {

        if (authRole == null)
            return;

        if (ROLE_ALL_ROLES.equals(authRole)) {
            allRoles = false;
            return;
        }

        if (ROLE_ALL_AUTHENTICATED_USERS.equals(authRole)) {
            authenticatedUsers = false;
            return;
        }

        int n = -1;
        for (int i = 0; i < authRoles.length; i++) {
            if (authRoles[i].equals(authRole)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[authRoles.length - 1];
            for (int i = 0; i < authRoles.length; i++) {
                if (i != n)
                    results[j++] = authRoles[i];
            }
            authRoles = results;
        }
    }


    /**
     * 从受此安全约束保护的Web资源集合中, 删除指定的Web资源集合.
     *
     * @param collection 要删除的Web资源集合
     */
    public void removeCollection(SecurityCollection collection) {

        if (collection == null)
            return;
        int n = -1;
        for (int i = 0; i < collections.length; i++) {
            if (collections[i].equals(collection)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            SecurityCollection results[] =
                new SecurityCollection[collections.length - 1];
            for (int i = 0; i < collections.length; i++) {
                if (i != n)
                    results[j++] = collections[i];
            }
            collections = results;
        }
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("SecurityConstraint[");
        for (int i = 0; i < collections.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(collections[i].getName());
        }
        sb.append("]");
        return (sb.toString());
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 指定的请求路径是否与指定的URL模式匹配?
     * 此方法遵循与用于将请求映射到servlet的规则相同的规则（以相同的顺序）.
     *
     * @param path 要检查的上下文相关请求路径 (必须以 '/' 开头)
     * @param pattern 要比较的URL模式
     */
    private boolean matchPattern(String path, String pattern) {

        // 规范化参数字符串
        if ((path == null) || (path.length() == 0))
            path = "/";
        if ((pattern == null) || (pattern.length() == 0))
            pattern = "/";

        // 精确匹配
        if (path.equals(pattern))
            return true;

        // 检查路径前缀匹配
        if (pattern.startsWith("/") && pattern.endsWith("/*")) {
            pattern = pattern.substring(0, pattern.length() - 2);
            if (pattern.length() == 0)
                return true;  // "/*" 等同于 "/"
            if (path.endsWith("/"))
                path = path.substring(0, path.length() - 1);
            while (true) {
                if (pattern.equals(path))
                    return true;
                int slash = path.lastIndexOf('/');
                if (slash <= 0)
                    break;
                path = path.substring(0, slash);
            }
            return false;
        }

        // 检查后缀匹配
        if (pattern.startsWith("*.")) {
            int slash = path.lastIndexOf('/');
            int period = path.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) &&
                path.endsWith(pattern.substring(1))) {
                return true;
            }
            return false;
        }

        // 检查通用映射
        if (pattern.equals("/"))
            return true;

        return false;
    }


    /**
     * 将{@link ServletSecurityElement}转换为{@link SecurityConstraint}数组.
     *
     * @param element       要转换的元素
     * @param urlPattern    应该应用元素的url模式
     *                      
     * @return              （可能为零长度）约束数组，与输入等效
     */
    public static SecurityConstraint[] createConstraints(
            ServletSecurityElement element, String urlPattern) {
        Set<SecurityConstraint> result = new HashSet<>();

        // 添加每个方法的约束
        Collection<HttpMethodConstraintElement> methods =
            element.getHttpMethodConstraints();
        Iterator<HttpMethodConstraintElement> methodIter = methods.iterator();
        while (methodIter.hasNext()) {
            HttpMethodConstraintElement methodElement = methodIter.next();
            SecurityConstraint constraint =
                createConstraint(methodElement, urlPattern, true);
            // 总会有一个集合
            SecurityCollection collection = constraint.findCollections()[0];
            collection.addMethod(methodElement.getMethodName());
            result.add(constraint);
        }

        // 为所有其他方法添加约束
        SecurityConstraint constraint = createConstraint(element, urlPattern, false);
        if (constraint != null) {
            // 总会有一个集合
            SecurityCollection collection = constraint.findCollections()[0];
            Iterator<String> ommittedMethod = element.getMethodNames().iterator();
            while (ommittedMethod.hasNext()) {
                collection.addOmittedMethod(ommittedMethod.next());
            }

            result.add(constraint);

        }

        return result.toArray(new SecurityConstraint[result.size()]);
    }

    private static SecurityConstraint createConstraint(
            HttpConstraintElement element, String urlPattern, boolean alwaysCreate) {

        SecurityConstraint constraint = new SecurityConstraint();
        SecurityCollection collection = new SecurityCollection();
        boolean create = alwaysCreate;

        if (element.getTransportGuarantee() !=
                ServletSecurity.TransportGuarantee.NONE) {
            constraint.setUserConstraint(element.getTransportGuarantee().name());
            create = true;
        }
        if (element.getRolesAllowed().length > 0) {
            String[] roles = element.getRolesAllowed();
            for (String role : roles) {
                constraint.addAuthRole(role);
            }
            create = true;
        }
        if (element.getEmptyRoleSemantic() != EmptyRoleSemantic.PERMIT) {
            constraint.setAuthConstraint(true);
            create = true;
        }

        if (create) {
            collection.addPattern(urlPattern);
            constraint.addCollection(collection);
            return constraint;
        }

        return null;
    }


    public static SecurityConstraint[] findUncoveredHttpMethods(
            SecurityConstraint[] constraints,
            boolean denyUncoveredHttpMethods, Log log) {

        Set<String> coveredPatterns = new HashSet<>();
        Map<String,Set<String>> urlMethodMap = new HashMap<>();
        Map<String,Set<String>> urlOmittedMethodMap = new HashMap<>();

        List<SecurityConstraint> newConstraints = new ArrayList<>();

        // 首先构建覆盖模式列表和可能不被覆盖的模式
        for (SecurityConstraint constraint : constraints) {
            SecurityCollection[] collections = constraint.findCollections();
            for (SecurityCollection collection : collections) {
                String[] patterns = collection.findPatterns();
                String[] methods = collection.findMethods();
                String[] omittedMethods = collection.findOmittedMethods();
                // Simple case: no methods
                if (methods.length == 0 && omittedMethods.length == 0) {
                    for (String pattern : patterns) {
                        coveredPatterns.add(pattern);
                    }
                    continue;
                }

                // 预先计算, 因此不会对以下循环的每次迭代执行此操作
                List<String> omNew = null;
                if (omittedMethods.length != 0) {
                    omNew = Arrays.asList(omittedMethods);
                }

                // 只需要处理未覆盖的模式
                for (String pattern : patterns) {
                    if (!coveredPatterns.contains(pattern)) {
                        if (methods.length == 0) {
                            // 建立对此模式的省略方法的兴趣
                            Set<String> om = urlOmittedMethodMap.get(pattern);
                            if (om == null) {
                                om = new HashSet<>();
                                urlOmittedMethodMap.put(pattern, om);
                                om.addAll(omNew);
                            } else {
                                om.retainAll(omNew);
                            }
                        } else {
                            // 为此模式构建方法的并集
                            Set<String> m = urlMethodMap.get(pattern);
                            if (m == null) {
                                m = new HashSet<>();
                                urlMethodMap.put(pattern, m);
                            }
                            for (String method : methods) {
                                m.add(method);
                            }
                        }
                    }
                }
            }
        }

        // 现在检查可能未覆盖的模式
        for (Map.Entry<String, Set<String>> entry : urlMethodMap.entrySet()) {
            String pattern = entry.getKey();
            if (coveredPatterns.contains(pattern)) {
                // 完全覆盖. 忽略任何部分覆盖
                urlOmittedMethodMap.remove(pattern);
                continue;
            }

            Set<String> omittedMethods = urlOmittedMethodMap.remove(pattern);
            Set<String> methods = entry.getValue();

            if (omittedMethods == null) {
                StringBuilder msg = new StringBuilder();
                for (String method : methods) {
                    msg.append(method);
                    msg.append(' ');
                }
                if (denyUncoveredHttpMethods) {
                    log.info(sm.getString(
                            "securityConstraint.uncoveredHttpMethodFix",
                            pattern, msg.toString().trim()));
                    SecurityCollection collection = new SecurityCollection();
                    for (String method : methods) {
                        collection.addOmittedMethod(method);
                    }
                    collection.addPatternDecoded(pattern);
                    collection.setName("deny-uncovered-http-methods");
                    SecurityConstraint constraint = new SecurityConstraint();
                    constraint.setAuthConstraint(true);
                    constraint.addCollection(collection);
                    newConstraints.add(constraint);
                } else {
                    log.error(sm.getString(
                            "securityConstraint.uncoveredHttpMethod",
                            pattern, msg.toString().trim()));
                }
                continue;
            }

            // 只要每个省略的方法作为相应的方法，模式就完全覆盖了.
            omittedMethods.removeAll(methods);

            handleOmittedMethods(omittedMethods, pattern, denyUncoveredHttpMethods,
                    newConstraints, log);
        }
        for (Map.Entry<String, Set<String>> entry :
                urlOmittedMethodMap.entrySet()) {
            String pattern = entry.getKey();
            if (coveredPatterns.contains(pattern)) {
                // 完全覆盖. 忽略任何部分覆盖
                continue;
            }

            handleOmittedMethods(entry.getValue(), pattern, denyUncoveredHttpMethods,
                    newConstraints, log);
        }

        return newConstraints.toArray(new SecurityConstraint[newConstraints.size()]);
    }


    private static void handleOmittedMethods(Set<String> omittedMethods, String pattern,
            boolean denyUncoveredHttpMethods, List<SecurityConstraint> newConstraints, Log log) {
        if (omittedMethods.size() > 0) {
            StringBuilder msg = new StringBuilder();
            for (String method : omittedMethods) {
                msg.append(method);
                msg.append(' ');
            }
            if (denyUncoveredHttpMethods) {
                log.info(sm.getString(
                        "securityConstraint.uncoveredHttpOmittedMethodFix",
                        pattern, msg.toString().trim()));
                SecurityCollection collection = new SecurityCollection();
                for (String method : omittedMethods) {
                    collection.addMethod(method);
                }
                collection.addPatternDecoded(pattern);
                collection.setName("deny-uncovered-http-methods");
                SecurityConstraint constraint = new SecurityConstraint();
                constraint.setAuthConstraint(true);
                constraint.addCollection(collection);
                newConstraints.add(constraint);
            } else {
                log.error(sm.getString(
                        "securityConstraint.uncoveredHttpOmittedMethod",
                        pattern, msg.toString().trim()));
            }
        }
    }
}
