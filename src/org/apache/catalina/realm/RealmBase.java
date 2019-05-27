package org.apache.catalina.realm;


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.annotation.ServletSecurity.TransportGuarantee;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.CredentialHandler;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.SessionConfig;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.util.security.MD5Encoder;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;

/**
 * <b>Realm</b>实现类， 读取XML文件以配置有效用户、密码和角色.
 * 文件格式（和默认文件位置）与当前由Tomcat 3支持的文件格式相同.
 */
public abstract class RealmBase extends LifecycleMBeanBase implements Realm {

    private static final Log log = LogFactory.getLog(RealmBase.class);

    private static final List<Class<? extends DigestCredentialHandlerBase>> credentialHandlerClasses =
            new ArrayList<>();

    static {
        // 顺序是重要的，因为它仅在指定算法和调用main()时, 确定匹配处理程序的搜索顺序
        credentialHandlerClasses.add(MessageDigestCredentialHandler.class);
        credentialHandlerClasses.add(SecretKeyCredentialHandler.class);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * 关联的Container.
     */
    protected Container container = null;


    /**
     * Container log
     */
    protected Log containerLog = null;


    private CredentialHandler credentialHandler;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(RealmBase.class);


    /**
     * 属性修改支持.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 当客户端证书链出现时，是否应该验证?
     */
    protected boolean validate = true;

    /**
     * 用于从X509证书检索用户名的类名.
     */
    protected String x509UsernameRetrieverClassName;

    /**
     * 从X509客户端证书中提取用户名的对象.
     */
    protected X509UsernameRetriever x509UsernameRetriever;

    protected AllRolesMode allRolesMode = AllRolesMode.STRICT_MODE;


    /**
     * 当通过GSS-API验证用户时, 是否从用户名的末尾剥离&quot;@...&quot;?
     */
    protected boolean stripRealmForGss = true;


    private int transportGuaranteeRedirectStatus = HttpServletResponse.SC_FOUND;


    // ------------------------------------------------------------- Properties


    /**
     * @return 当容器需要发出HTTP重定向以满足配置的传输保证的要求时, 使用的HTTP状态代码.
     */
    public int getTransportGuaranteeRedirectStatus() {
        return transportGuaranteeRedirectStatus;
    }


    /**
     * 当容器需要发出HTTP重定向以满足配置的传输保证的要求时, 设置使用的HTTP状态代码.
     *
     * @param transportGuaranteeRedirectStatus 要使用的状态. 未验证此值
     */
    public void setTransportGuaranteeRedirectStatus(int transportGuaranteeRedirectStatus) {
        this.transportGuaranteeRedirectStatus = transportGuaranteeRedirectStatus;
    }


    @Override
    public CredentialHandler getCredentialHandler() {
        return credentialHandler;
    }


    @Override
    public void setCredentialHandler(CredentialHandler credentialHandler) {
        this.credentialHandler = credentialHandler;
    }


    /**
     * 返回关联的Container.
     */
    @Override
    public Container getContainer() {
        return (container);
    }


    /**
     * 设置关联的Container.
     *
     * @param container 关联的Container
     */
    @Override
    public void setContainer(Container container) {

        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);
    }

    public String getAllRolesMode() {
        return allRolesMode.toString();
    }


    public void setAllRolesMode(String allRolesMode) {
        this.allRolesMode = AllRolesMode.toMode(allRolesMode);
    }


    /**
     * 返回“验证证书链”标志.
     */
    public boolean getValidate() {
        return validate;
    }


    /**
     * 设置“验证证书链”标志.
     *
     * @param validate 新的验证证书链标志
     */
    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    /**
     * 用于从X509证书检索用户名的类名.
     */
    public String getX509UsernameRetrieverClassName() {
        return x509UsernameRetrieverClassName;
    }

    /**
     * 设置用于从X509证书检索用户名的类名. 类必须实现 X509UsernameRetriever.
     *
     * @param className 用于从X509证书检索用户名的类名.
     */
    public void setX509UsernameRetrieverClassName(String className) {
        this.x509UsernameRetrieverClassName = className;
    }

    public boolean isStripRealmForGss() {
        return stripRealmForGss;
    }


    public void setStripRealmForGss(boolean stripRealmForGss) {
        this.stripRealmForGss = stripRealmForGss;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加属性修改监听器.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 返回指定用户名和凭据关联的Principal; 或者<code>null</code>.
     *
     * @param username 要查找的Principal 用户名
     */
    @Override
    public Principal authenticate(String username) {

        if (username == null) {
            return null;
        }

        if (containerLog.isTraceEnabled()) {
            containerLog.trace(sm.getString("realmBase.authenticateSuccess", username));
        }

        return getPrincipal(username);
    }


    /**
     * 返回指定用户名和凭据关联的Principal; 或者<code>null</code>.
     *
     * @param username 要查找的Principal 用户名
     * @param credentials 要验证的用户名的Password或其它凭据
     * 
     * @return 关联的主体, 或<code>null</code>.
     */
    @Override
    public Principal authenticate(String username, String credentials) {
        // No user or no credentials
        // Can't possibly authenticate, don't bother doing anything.
        if(username == null || credentials == null) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateFailure",
                                                username));
            }
            return null;
        }

        // Look up the user's credentials
        String serverCredentials = getPassword(username);

        if (serverCredentials == null) {
            // User was not found
            // 浪费一点时间，不要透露用户不存在.
            getCredentialHandler().mutate(credentials);

            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateFailure",
                                                username));
            }
            return null;
        }

        boolean validated = getCredentialHandler().matches(credentials, serverCredentials);

        if (validated) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateSuccess",
                                                username));
            }
            return getPrincipal(username);
        } else {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateFailure",
                                                username));
            }
            return null;
        }
    }

    /**
     * 尝试用指定的用户名进行身份验证, 使用RFC 2617中描述的方法匹配使用给定参数计算的摘要.
     *
     * @param username Username of the Principal to look up
     * @param clientDigest 客户提交的摘要
     * @param nonce 用于此请求的唯一（或可能是唯一的）令牌
     * @param nc the nonce counter
     * @param cnonce the client chosen nonce
     * @param qop "保护质量" (将使用<code>nc</code>和<code>cnonce</code>, 如果<code>qop</code>不是<code>null</code>).
     * @param realm Realm name
     * @param md5a2 第二个MD5用于计算摘要 : MD5(Method + ":" + uri)
     * 
     * @return 关联的主体, 或<code>null</code>.
     */
    @Override
    public Principal authenticate(String username, String clientDigest,
                                  String nonce, String nc, String cnonce,
                                  String qop, String realm,
                                  String md5a2) {

        // In digest auth, 摘要总是小写
        String md5a1 = getDigest(username, realm);
        if (md5a1 == null)
            return null;
        md5a1 = md5a1.toLowerCase(Locale.ENGLISH);
        String serverDigestValue;
        if (qop == null) {
            serverDigestValue = md5a1 + ":" + nonce + ":" + md5a2;
        } else {
            serverDigestValue = md5a1 + ":" + nonce + ":" + nc + ":" +
                    cnonce + ":" + qop + ":" + md5a2;
        }

        byte[] valueBytes = null;
        try {
            valueBytes = serverDigestValue.getBytes(getDigestCharset());
        } catch (UnsupportedEncodingException uee) {
            log.error("Illegal digestEncoding: " + getDigestEncoding(), uee);
            throw new IllegalArgumentException(uee.getMessage());
        }

        String serverDigest = MD5Encoder.encode(ConcurrentMessageDigest.digestMD5(valueBytes));

        if (log.isDebugEnabled()) {
            log.debug("Digest : " + clientDigest + " Username:" + username
                    + " ClientDigest:" + clientDigest + " nonce:" + nonce
                    + " nc:" + nc + " cnonce:" + cnonce + " qop:" + qop
                    + " realm:" + realm + "md5a2:" + md5a2
                    + " Server digest:" + serverDigest);
        }

        if (serverDigest.equals(clientDigest)) {
            return getPrincipal(username);
        }

        return null;
    }


    /**
     * 指定X509客户端证书链关联的Principal.  如果没有，返回<code>null</code>.
     *
     * @param certs 客户端证书数组, 数组中的第一个是客户端本身的证书.
     */
    @Override
    public Principal authenticate(X509Certificate certs[]) {

        if ((certs == null) || (certs.length < 1))
            return (null);

        // 检查链中每个证书的有效性
        if (log.isDebugEnabled())
            log.debug("Authenticating client certificate chain");
        if (validate) {
            for (int i = 0; i < certs.length; i++) {
                if (log.isDebugEnabled())
                    log.debug(" Checking validity for '" +
                        certs[i].getSubjectDN().getName() + "'");
                try {
                    certs[i].checkValidity();
                } catch (Exception e) {
                    if (log.isDebugEnabled())
                        log.debug("  Validity exception", e);
                    return (null);
                }
            }
        }

        // 检查数据库中客户端Principal 是否存在
        return (getPrincipal(certs[0]));
    }


    @Override
    public Principal authenticate(GSSContext gssContext, boolean storeCreds) {
        if (gssContext.isEstablished()) {
            GSSName gssName = null;
            try {
                gssName = gssContext.getSrcName();
            } catch (GSSException e) {
                log.warn(sm.getString("realmBase.gssNameFail"), e);
            }

            if (gssName!= null) {
                String name = gssName.toString();

                if (isStripRealmForGss()) {
                    int i = name.indexOf('@');
                    if (i > 0) {
                        // Zero so we don;t leave a zero length name
                        name = name.substring(0, i);
                    }
                }
                GSSCredential gssCredential = null;
                if (storeCreds && gssContext.getCredDelegState()) {
                    try {
                        gssCredential = gssContext.getDelegCred();
                    } catch (GSSException e) {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString(
                                    "realmBase.delegatedCredentialFail", name),
                                    e);
                        }
                    }
                }
                return getPrincipal(name, gssCredential);
            }
        } else {
            log.error(sm.getString("realmBase.gssContextNotEstablished"));
        }

        // Fail in all other cases
        return null;
    }


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    @Override
    public void backgroundProcess() {
        // NOOP in base class
    }


    /**
     * 返回配置用于保护请求URI的SecurityConstraint, 或<code>null</code>如果没有约束.
     *
     * @param request 处理的请求
     * @param context 请求映射的上下文
     */
    @Override
    public SecurityConstraint [] findSecurityConstraints(Request request,
                                                         Context context) {

        ArrayList<SecurityConstraint> results = null;
        // Are there any defined security constraints?
        SecurityConstraint constraints[] = context.findConstraints();
        if ((constraints == null) || (constraints.length == 0)) {
            if (log.isDebugEnabled())
                log.debug("  No applicable constraints defined");
            return (null);
        }

        // Check each defined security constraint
        String uri = request.getRequestPathMB().toString();
        // Bug47080 - in rare cases this may be null
        // Mapper treats as '/' do the same to prevent NPE
        if (uri == null) {
            uri = "/";
        }

        String method = request.getMethod();
        int i;
        boolean found = false;
        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                for(int k=0; k < patterns.length; k++) {
                    if(uri.equals(patterns[k])) {
                        found = true;
                        if(collection[j].findMethod(method)) {
                            if(results == null) {
                                results = new ArrayList<>();
                            }
                            results.add(constraints[i]);
                        }
                    }
                }
            }
        }

        if(found) {
            return resultsToArray(results);
        }

        int longest = -1;

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                boolean matched = false;
                int length = -1;
                for(int k=0; k < patterns.length; k++) {
                    String pattern = patterns[k];
                    if(pattern.startsWith("/") && pattern.endsWith("/*") &&
                       pattern.length() >= longest) {

                        if(pattern.length() == 2) {
                            matched = true;
                            length = pattern.length();
                        } else if(pattern.regionMatches(0,uri,0,
                                                        pattern.length()-1) ||
                                  (pattern.length()-2 == uri.length() &&
                                   pattern.regionMatches(0,uri,0,
                                                        pattern.length()-2))) {
                            matched = true;
                            length = pattern.length();
                        }
                    }
                }
                if(matched) {
                    if(length > longest) {
                        found = false;
                        if(results != null) {
                            results.clear();
                        }
                        longest = length;
                    }
                    if(collection[j].findMethod(method)) {
                        found = true;
                        if(results == null) {
                            results = new ArrayList<>();
                        }
                        results.add(constraints[i]);
                    }
                }
            }
        }

        if(found) {
            return  resultsToArray(results);
        }

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            boolean matched = false;
            int pos = -1;
            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                for(int k=0; k < patterns.length && !matched; k++) {
                    String pattern = patterns[k];
                    if(pattern.startsWith("*.")){
                        int slash = uri.lastIndexOf('/');
                        int dot = uri.lastIndexOf('.');
                        if(slash >= 0 && dot > slash &&
                           dot != uri.length()-1 &&
                           uri.length()-dot == pattern.length()-1) {
                            if(pattern.regionMatches(1,uri,dot,uri.length()-dot)) {
                                matched = true;
                                pos = j;
                            }
                        }
                    }
                }
            }
            if(matched) {
                found = true;
                if(collection[pos].findMethod(method)) {
                    if(results == null) {
                        results = new ArrayList<>();
                    }
                    results.add(constraints[i]);
                }
            }
        }

        if(found) {
            return resultsToArray(results);
        }

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, continue to avoid an NPE
            // See Bugzilla 30624
            if ( collection == null) {
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, continue to avoid an NPE
                // See Bugzilla 30624
                if ( patterns == null) {
                    continue;
                }

                boolean matched = false;
                for(int k=0; k < patterns.length && !matched; k++) {
                    String pattern = patterns[k];
                    if(pattern.equals("/")){
                        matched = true;
                    }
                }
                if(matched) {
                    if(results == null) {
                        results = new ArrayList<>();
                    }
                    results.add(constraints[i]);
                }
            }
        }

        if(results == null) {
            // No applicable security constraint was found
            if (log.isDebugEnabled())
                log.debug("  No applicable constraint located");
        }
        return resultsToArray(results);
    }

    /**
     * 将ArrayList转换为SecurityConstraint [].
     */
    private SecurityConstraint [] resultsToArray(ArrayList<SecurityConstraint> results) {
        if(results == null || results.size() == 0) {
            return null;
        }
        SecurityConstraint [] array = new SecurityConstraint[results.size()];
        results.toArray(array);
        return array;
    }


    /**
     * 根据指定的授权约束执行访问控制.
     * 返回<code>true</code> 如果满足此约束，则处理将继续进行, 否则返回<code>false</code>.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraints 正在执行的安全约束
     * @param context 这个类的客户端所附的上下文.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public boolean hasResourcePermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraints,
                                         Context context)
        throws IOException {

        if (constraints == null || constraints.length == 0)
            return true;

        // Which user principal have we already authenticated?
        Principal principal = request.getPrincipal();
        boolean status = false;
        boolean denyfromall = false;
        for(int i=0; i < constraints.length; i++) {
            SecurityConstraint constraint = constraints[i];

            String roles[];
            if (constraint.getAllRoles()) {
                // * means all roles defined in web.xml
                roles = request.getContext().findSecurityRoles();
            } else {
                roles = constraint.findAuthRoles();
            }

            if (roles == null)
                roles = new String[0];

            if (log.isDebugEnabled())
                log.debug("  Checking roles " + principal);

            if (constraint.getAuthenticatedUsers() && principal != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Passing all authenticated users");
                }
                status = true;
            } else if (roles.length == 0 && !constraint.getAllRoles() &&
                    !constraint.getAuthenticatedUsers()) {
                if(constraint.getAuthConstraint()) {
                    if( log.isDebugEnabled() )
                        log.debug("No roles");
                    status = false; // No listed roles means no access at all
                    denyfromall = true;
                    break;
                }

                if(log.isDebugEnabled())
                    log.debug("Passing all access");
                status = true;
            } else if (principal == null) {
                if (log.isDebugEnabled())
                    log.debug("  No user authenticated, cannot grant access");
            } else {
                for (int j = 0; j < roles.length; j++) {
                    if (hasRole(null, principal, roles[j])) {
                        status = true;
                        if( log.isDebugEnabled() )
                            log.debug( "Role found:  " + roles[j]);
                    }
                    else if( log.isDebugEnabled() )
                        log.debug( "No role found:  " + roles[j]);
                }
            }
        }

        if (!denyfromall && allRolesMode != AllRolesMode.STRICT_MODE &&
                !status && principal != null) {
            if (log.isDebugEnabled()) {
                log.debug("Checking for all roles mode: " + allRolesMode);
            }
            // Check for an all roles(role-name="*")
            for (int i = 0; i < constraints.length; i++) {
                SecurityConstraint constraint = constraints[i];
                String roles[];
                // If the all roles mode exists, sets
                if (constraint.getAllRoles()) {
                    if (allRolesMode == AllRolesMode.AUTH_ONLY_MODE) {
                        if (log.isDebugEnabled()) {
                            log.debug("Granting access for role-name=*, auth-only");
                        }
                        status = true;
                        break;
                    }

                    // For AllRolesMode.STRICT_AUTH_ONLY_MODE there must be zero roles
                    roles = request.getContext().findSecurityRoles();
                    if (roles.length == 0 && allRolesMode == AllRolesMode.STRICT_AUTH_ONLY_MODE) {
                        if (log.isDebugEnabled()) {
                            log.debug("Granting access for role-name=*, strict auth-only");
                        }
                        status = true;
                        break;
                    }
                }
            }
        }

        // Return a "Forbidden" message denying access to this resource
        if(!status) {
            response.sendError
                (HttpServletResponse.SC_FORBIDDEN,
                 sm.getString("realmBase.forbidden"));
        }
        return status;

    }


    /**
     * 这个方法或 {@link #hasRoleInternal(Principal, String)}可以被Realm 实现类覆盖, 但是默认已经足够了, 
     * 当一个<code>GenericPrincipal</code>实例用于表示这个Realm验证的Principal.
     */
    @Override
    public boolean hasRole(Wrapper wrapper, Principal principal, String role) {
        // 检查<security-role-ref>元素中定义的角色别名
        if (wrapper != null) {
            String realRole = wrapper.findSecurityReference(role);
            if (realRole != null) {
                role = realRole;
            }
        }

        // 是否在JAASRealm中重写 - 避免相当低效的转换
        if (principal == null || role == null) {
            return false;
        }

        boolean result = hasRoleInternal(principal, role);

        if (log.isDebugEnabled()) {
            String name = principal.getName();
            if (result)
                log.debug(sm.getString("realmBase.hasRoleSuccess", name, role));
            else
                log.debug(sm.getString("realmBase.hasRoleFailure", name, role));
        }

        return result;
    }


    /**
     * 检查指定的主体是否具有指定的安全角色, 在这个Realm中的上下文中.
     *
     * 这个方法或 {@link #hasRoleInternal(Principal, String)}可以被Realm 实现类覆盖, 但是默认已经足够了, 
     * 当一个<code>GenericPrincipal</code>实例用于表示这个Realm验证的Principal.
     *
     * @param principal 要检查角色的Principal
     * @param role 要检查的角色
     *
     * @return <code>true</code>如果指定的Principal拥有指定的角色, 在这个Realm中的上下文中; 否则返回<code>false</code>.
     */
    protected boolean hasRoleInternal(Principal principal, String role) {
        // 是否在JAASRealm中重写 - 避免相当低效的转换
        if (!(principal instanceof GenericPrincipal)) {
            return false;
        }

        GenericPrincipal gp = (GenericPrincipal) principal;
        return gp.hasRole(role);
    }


    /**
     * 通过安全约束强制保护该请求URI所需的任何用户数据约束. 
     * 返回<code>true</code>如果该约束未被违反，则处理将继续进行, 或者<code>false</code>.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraints 正在检查的安全约束
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    public boolean hasUserDataPermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraints)
        throws IOException {

        // 是否有相关的用户数据约束?
        if (constraints == null || constraints.length == 0) {
            if (log.isDebugEnabled())
                log.debug("  No applicable security constraint defined");
            return true;
        }
        for(int i=0; i < constraints.length; i++) {
            SecurityConstraint constraint = constraints[i];
            String userConstraint = constraint.getUserConstraint();
            if (userConstraint == null) {
                if (log.isDebugEnabled())
                    log.debug("  No applicable user data constraint defined");
                return true;
            }
            if (userConstraint.equals(TransportGuarantee.NONE.name())) {
                if (log.isDebugEnabled())
                    log.debug("  User data constraint has no restrictions");
                return true;
            }

        }
        // 针对用户数据约束验证请求
        if (request.getRequest().isSecure()) {
            if (log.isDebugEnabled())
                log.debug("  User data constraint already satisfied");
            return true;
        }
        // 初始化需要确定适当操作的变量
        int redirectPort = request.getConnector().getRedirectPort();

        // 正在重定向禁用?
        if (redirectPort <= 0) {
            if (log.isDebugEnabled())
                log.debug("  SSL redirect is disabled");
            response.sendError
                (HttpServletResponse.SC_FORBIDDEN,
                 request.getRequestURI());
            return false;
        }

        // 重定向到相应的 SSL 端口
        StringBuilder file = new StringBuilder();
        String protocol = "https";
        String host = request.getServerName();
        // Protocol
        file.append(protocol).append("://").append(host);
        // Host with port
        if(redirectPort != 443) {
            file.append(":").append(redirectPort);
        }
        // URI
        file.append(request.getRequestURI());
        String requestedSessionId = request.getRequestedSessionId();
        if ((requestedSessionId != null) &&
            request.isRequestedSessionIdFromURL()) {
            file.append(";");
            file.append(SessionConfig.getSessionUriParamName(
                    request.getContext()));
            file.append("=");
            file.append(requestedSessionId);
        }
        String queryString = request.getQueryString();
        if (queryString != null) {
            file.append('?');
            file.append(queryString);
        }
        if (log.isDebugEnabled())
            log.debug("  Redirecting to " + file.toString());
        response.sendRedirect(file.toString(), transportGuaranteeRedirectStatus);
        return false;
    }


    /**
     * 移除属性修改监听器.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal();

        // We want logger as soon as possible
        if (container != null) {
            this.containerLog = container.getLogger();
        }

        x509UsernameRetriever = createUsernameRetriever(x509UsernameRetrieverClassName);
    }

    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {
        if (credentialHandler == null) {
            credentialHandler = new MessageDigestCredentialHandler();
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Realm[");
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }


    // ------------------------------------------------------ Protected Methods

    protected boolean hasMessageDigest() {
        CredentialHandler ch = credentialHandler;
        if (ch instanceof MessageDigestCredentialHandler) {
            return ((MessageDigestCredentialHandler) ch).getAlgorithm() != null;
        }
        return false;
    }


    /**
     * 返回给定用户名关联的摘要.
     * 
     * @param username 用户名
     * @param realmName the realm name
     * 
     * @return 给定用户名关联的摘要
     */
    protected String getDigest(String username, String realmName) {
        if (hasMessageDigest()) {
            // Use pre-generated digest
            return getPassword(username);
        }

        String digestValue = username + ":" + realmName + ":"
            + getPassword(username);

        byte[] valueBytes = null;
        try {
            valueBytes = digestValue.getBytes(getDigestCharset());
        } catch (UnsupportedEncodingException uee) {
            log.error("Illegal digestEncoding: " + getDigestEncoding(), uee);
            throw new IllegalArgumentException(uee.getMessage());
        }

        return MD5Encoder.encode(ConcurrentMessageDigest.digestMD5(valueBytes));
    }


    private String getDigestEncoding() {
        CredentialHandler ch = credentialHandler;
        if (ch instanceof MessageDigestCredentialHandler) {
            return ((MessageDigestCredentialHandler) ch).getEncoding();
        }
        return null;
    }


    private Charset getDigestCharset() throws UnsupportedEncodingException {
        String charset = getDigestEncoding();
        if (charset == null) {
            return StandardCharsets.ISO_8859_1;
        } else {
            return B2CConverter.getCharset(charset);
        }
    }


    /**
     * @returnRealm实现类的名称, 用于日志记录.
     *
     * @deprecated This will be removed in Tomcat 9 onwards. Use
     *             {@link Class#getSimpleName()} instead.
     */
    @Deprecated
    protected abstract String getName();


    /**
     * 返回指定用户名关联的密码.
     * 
     * @param username 用户名
     * @return 指定用户名关联的密码.
     */
    protected abstract String getPassword(String username);


    /**
     * 获取指定凭据关联的主体.
     * 
     * @param usercert 用户凭据
     * 
     * @return 给定凭据关联的Principal.
     */
    protected Principal getPrincipal(X509Certificate usercert) {
        String username = x509UsernameRetriever.getUsername(usercert);

        if(log.isDebugEnabled())
            log.debug(sm.getString("realmBase.gotX509Username", username));

        return(getPrincipal(username));
    }


    /**
     * 返回指定用户名关联的Principal.
     * 
     * @param username 用户名
     * 
     * @return 指定用户名关联的Principal.
     */
    protected abstract Principal getPrincipal(String username);


    protected Principal getPrincipal(String username,
            GSSCredential gssCredential) {
        Principal p = getPrincipal(username);

        if (p instanceof GenericPrincipal) {
            ((GenericPrincipal) p).setGssCredential(gssCredential);
        }

        return p;
    }

    /**
     * 返回这个Realm关联的容器的最终父级Server对象. 如果找不到Server (因为容器层次结构不完整), 返回<code>null</code>.
     * 
     * @return realm关联的Server
     */
    protected Server getServer() {
        Container c = container;
        if (c instanceof Context) {
            c = c.getParent();
        }
        if (c instanceof Host) {
            c = c.getParent();
        }
        if (c instanceof Engine) {
            Service s = ((Engine)c).getService();
            if (s != null) {
                return s.getServer();
            }
        }
        return null;
    }


    // --------------------------------------------------------- Static Methods

    /**
     * 摘要使用密码算法especificied并将结果转换为相应的字符串.
     * 如果异常，则返回明文凭据字符串
     *
     * @param credentials 验证这个用户名的Password或其它凭据
     * @param algorithm 用于加密的算法
     * @param encoding 要加密的字符串的字符编码
     *
     * @deprecated  Unused. This will be removed in Tomcat 9.
     */
    @Deprecated
    public static final String Digest(String credentials, String algorithm,
                                      String encoding) {

        try {
            // 用“摘要”加密获取新的消息摘要
            MessageDigest md =
                (MessageDigest) MessageDigest.getInstance(algorithm).clone();

            // encode the credentials
            // Should use the digestEncoding, but that's not a static field
            if (encoding == null) {
                md.update(credentials.getBytes());
            } else {
                md.update(credentials.getBytes(encoding));
            }

            // Digest the credentials and return as hexadecimal
            return (HexUtils.toHexString(md.digest()));
        } catch(Exception ex) {
            log.error(ex);
            return credentials;
        }

    }


    /**
     * 为给定密码和相关参数生成存储的凭据字符串.
     * <p>支持以下参数:</p>
     * <ul>
     * <li><b>-a</b> - 用于生成存储凭证的算法. 如果未指定，则将使用SHA-512.</li>
     * <li><b>-e</b> - 用于转换字节到字符的编码. 如果未指定, 将使用系统编码({@link Charset#defaultCharset()}).</li>
     * <li><b>-i</b> - 生成存储凭证时使用的迭代次数. 如果未指定, 将使用CredentialHandler.</li>
     * <li><b>-s</b> - 作为凭据一部分而生成和保存salt的长度(in bytes). 如果未指定, 将使用CredentialHandler.</li>
     * <li><b>-k</b> - key的长度(in bits), 在生成凭据期间创建. 如果未指定, 将使用CredentialHandler.</li>
     * <li><b>-h</b> - 要使用的CredentialHandler的完全限定类名. 如果未指定, 内置处理器将依次进行测试，第一个处理器将接受指定的算法.</li>
     * </ul>
     * <p>这个生成支持以下CredentialHandler, 根据指定的算法选择正确的一个:</p>
     * <ul>
     * <li>{@link MessageDigestCredentialHandler}</li>
     * <li>{@link SecretKeyCredentialHandler}</li>
     * </ul>
     * @param args The parameters passed on the command line
     */
    public static void main(String args[]) {

        // Use negative values since null is not an option to indicate 'not set'
        int saltLength = -1;
        int iterations = -1;
        int keyLength = -1;
        // Default
        String encoding = Charset.defaultCharset().name();
        // 这些默认值取决于它们是否在命令行上设置
        String algorithm = null;
        String handlerClassName = null;

        if (args.length == 0) {
            usage();
            return;
        }

        int argIndex = 0;

        while (args.length > argIndex + 2 && args[argIndex].length() == 2 &&
                args[argIndex].charAt(0) == '-' ) {
            switch (args[argIndex].charAt(1)) {
            case 'a': {
                algorithm = args[argIndex + 1];
                break;
            }
            case 'e': {
                encoding = args[argIndex + 1];
                break;
            }
            case 'i': {
                iterations = Integer.parseInt(args[argIndex + 1]);
                break;
            }
            case 's': {
                saltLength = Integer.parseInt(args[argIndex + 1]);
                break;
            }
            case 'k': {
                keyLength = Integer.parseInt(args[argIndex + 1]);
                break;
            }
            case 'h': {
                handlerClassName = args[argIndex + 1];
                break;
            }
            default: {
                usage();
                return;
            }
            }
            argIndex += 2;
        }

        // Determine defaults for -a and -h. The rules are more complex to
        // express than the implementation:
        // - if neither -a nor -h is set, use SHA-512 and
        //   MessageDigestCredentialHandler
        // - if only -a is set the built-in handlers will be searched in order
        //   (MessageDigestCredentialHandler, SecretKeyCredentialHandler) and
        //   the first handler that supports the algorithm will be used
        // - if only -h is set no default will be used for -a. The handler may
        //   or may nor support -a and may or may not supply a sensible default
        if (algorithm == null && handlerClassName == null) {
            algorithm = "SHA-512";
        }

        CredentialHandler handler = null;

        if (handlerClassName == null) {
            for (Class<? extends DigestCredentialHandlerBase> clazz : credentialHandlerClasses) {
                try {
                    handler = clazz.getConstructor().newInstance();
                    if (IntrospectionUtils.setProperty(handler, "algorithm", algorithm)) {
                        break;
                    }
                } catch (ReflectiveOperationException e) {
                    // This isn't good.
                    throw new RuntimeException(e);
                }
            }
        } else {
            try {
                Class<?> clazz = Class.forName(handlerClassName);
                handler = (DigestCredentialHandlerBase) clazz.getConstructor().newInstance();
                IntrospectionUtils.setProperty(handler, "algorithm", algorithm);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        if (handler == null) {
            throw new RuntimeException(new NoSuchAlgorithmException(algorithm));
        }

        IntrospectionUtils.setProperty(handler, "encoding", encoding);
        if (iterations > 0) {
            IntrospectionUtils.setProperty(handler, "iterations", Integer.toString(iterations));
        }
        if (saltLength > -1) {
            IntrospectionUtils.setProperty(handler, "saltLength", Integer.toString(saltLength));
        }
        if (keyLength > 0) {
            IntrospectionUtils.setProperty(handler, "keyLength", Integer.toString(keyLength));
        }

        for (; argIndex < args.length; argIndex++) {
            String credential = args[argIndex];
            System.out.print(credential + ":");
            System.out.println(handler.mutate(credential));
        }
    }


    private static void usage() {
        System.out.println("Usage: RealmBase [-a <algorithm>] [-e <encoding>] " +
                "[-i <iterations>] [-s <salt-length>] [-k <key-length>] " +
                "[-h <handler-class-name>] <credentials>");
    }


    // -------------------- JMX and Registration  --------------------

    @Override
    public String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("type=Realm");
        keyProperties.append(getRealmSuffix());
        keyProperties.append(container.getMBeanKeyProperties());

        return keyProperties.toString();
    }

    @Override
    public String getDomainInternal() {
        return container.getDomain();
    }

    protected String realmPath = "/realm0";

    public String getRealmPath() {
        return realmPath;
    }

    public void setRealmPath(String theRealmPath) {
        realmPath = theRealmPath;
    }

    protected String getRealmSuffix() {
        return ",realmPath=" + getRealmPath();
    }


    protected static class AllRolesMode {

        private final String name;
        /** 使用严格的servlet规范解释, 需要用户拥有 web-app/security-role/role-name 其中之一
         */
        public static final AllRolesMode STRICT_MODE = new AllRolesMode("strict");
        /** 允许任何认证的用户
         */
        public static final AllRolesMode AUTH_ONLY_MODE = new AllRolesMode("authOnly");
        /** 允许任何验证的用户，只有当没有 web-app/security-roles 时
         */
        public static final AllRolesMode STRICT_AUTH_ONLY_MODE = new AllRolesMode("strictAuthOnly");

        static AllRolesMode toMode(String name)
        {
            AllRolesMode mode;
            if( name.equalsIgnoreCase(STRICT_MODE.name) )
                mode = STRICT_MODE;
            else if( name.equalsIgnoreCase(AUTH_ONLY_MODE.name) )
                mode = AUTH_ONLY_MODE;
            else if( name.equalsIgnoreCase(STRICT_AUTH_ONLY_MODE.name) )
                mode = STRICT_AUTH_ONLY_MODE;
            else
                throw new IllegalStateException("Unknown mode, must be one of: strict, authOnly, strictAuthOnly");
            return mode;
        }

        private AllRolesMode(String name)
        {
            this.name = name;
        }

        @Override
        public boolean equals(Object o)
        {
            boolean equals = false;
            if( o instanceof AllRolesMode )
            {
                AllRolesMode mode = (AllRolesMode) o;
                equals = name.equals(mode.name);
            }
            return equals;
        }
        @Override
        public int hashCode()
        {
            return name.hashCode();
        }
        @Override
        public String toString()
        {
            return name;
        }
    }

    private static X509UsernameRetriever createUsernameRetriever(String className)
        throws LifecycleException {
        if(null == className || "".equals(className.trim()))
            return new X509SubjectDnRetriever();

        try {
            @SuppressWarnings("unchecked")
            Class<? extends X509UsernameRetriever> clazz = (Class<? extends X509UsernameRetriever>)Class.forName(className);
            return clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new LifecycleException(sm.getString("realmBase.createUsernameRetriever.newInstance", className), e);
        } catch (ClassCastException e) {
            throw new LifecycleException(sm.getString("realmBase.createUsernameRetriever.ClassCastException", className), e);
        }
    }


    @Override
    public String[] getRoles(Principal principal) {
        if (principal instanceof GenericPrincipal) {
            return ((GenericPrincipal) principal).getRoles();
        }

        String className = principal.getClass().getSimpleName();
        throw new IllegalStateException(sm.getString("realmBase.cannotGetRoles", className));
    }
}
