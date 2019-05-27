package org.apache.catalina.realm;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import org.apache.catalina.LifecycleException;
import org.ietf.jgss.GSSCredential;

/**
 * <p><strong>Realm</strong>实现类，通过java命名和目录接口（JNDI）API访问目录服务器.
 * 下面对底层目录服务器中的数据结构施加约束:</p>
 * <ul>
 * <li>可以验证的每个用户都由顶层<code>DirContext</code>的单个元素表示，通过<code>connectionURL</code>属性进入.</li>
 *
 * <li>如果不能将套接字连接到<code>connectURL</code>, 将尝试使用<code>alternateURL</code>.</li>
 *
 * <li>每个用户元素都有一个专有名称, 可以通过将当前用户名替换为模式来形成, 使用userPattern 属性配置.</li>
 *
 * <li>另外, 如果<code>userPattern</code>属性未被指定, 可以通过搜索目录上下文来定位一个独特的元素.
 * 		在这种情况下:
 *     <ul>
 *     <li><code>userSearch</code>模式指定用户名替换后的搜索筛选器.</li>
 *     <li><code>userBase</code> 属性可以设置为包含用户的子树的基元素. 如果未指定,搜索基础是顶级上下文.</li>
 *     <li><code>userSubtree</code> 属性可以设置为<code>true</code>, 如果您希望搜索目录上下文的整个子树.
 *     <code>false</code>默认值，将只搜索当前级别.</li>
 *    </ul>
 * </li>
 *
 * <li>用户可以通过提供用户名和密码绑定到目录来进行身份验证. 当<code>userPassword</code>属性未指定的时候，这个方法将会使用.</li>
 *
 * <li>通过从目录检索属性值并与用户所提供的值进行比较，可以对用户进行身份验证. 当<code>userPassword</code>属性被指定，这个方法将会使用, 
 * 		在这种情况下:
 *     <ul>
 *     <li>此用户的元素必须包含一个<code>userPassword</code>属性.
 *     <li>用户密码属性的值是一个明文字符串, 或通过<code>RealmBase.digest()</code>方法获得的明文字符串
 *     		(使用<code>RealmBase</code>支持的标准摘要).
 *     <li>如果所提交的凭据(通过<code>RealmBase.digest()</code>之后)与检索用户密码属性的检索值相等，则认为该用户是经过验证的.</li>
 *     </ul></li>
 *
 * <li>每个用户组被分配一个特定的角色, 可以由一个最高等级的<code>DirContext</code>元素表示，通过<code>connectionURL</code>属性访问.
 * 		此元素具有以下特性:
 *     <ul>
 *     <li>所有可能的组的集合可以通过<code>roleSearch</code>属性配置的搜索模式选择.</li>
 *     <li><code>roleSearch</code>模式选择包含模式替换 "{0}"为名称, 替换 "{1}"为用户名, 验证用户要检索的角色.</li>
 *     <li><code>roleBase</code>属性可以设置为搜索匹配角色的基础的元素. 如果未指定, 将搜索整个上下文.</li>
 *     <li><code>roleSubtree</code> 属性可以设置为<code>true</code>, 如果您希望搜索目录上下文的整个子树.
 *     		默认的<code>false</code>值只搜索当前级别.</li>
 *     <li>元素有一个属性(使用<code>roleName</code>属性配置名称) 包含由该元素表示的角色的名称.</li>
 *     </ul></li>
 *
 * <li>此外，角色可以由用户元素(可以使用<code>userRoleName</code>属性配置)中属性的值表示.</li>
 *
 * <li>可以为已成功认证的每个用户分配默认角色 , 通过设置<code>commonRole</code>属性为这个角色的名称. 该角色不必存在于目录中.</li>
 *
 * <li>如果目录服务器包含嵌套角色, 可以搜索并设置<code>roleNested</code>为<code>true</code>.
 * 默认为<code>false</code>, 因此角色搜索不会找到嵌套的角色.</li>
 *
 * <li>注意，Web应用部署描述符中的标准的<code>&lt;security-role-ref&gt;</code>元素允许应用通过名称以编程方式引用角色, 除了目录服务器本身使用的那些以外.</li>
 * </ul>
 *
 * <p><strong>TODO</strong> - 支持连接池 (包括消息格式对象), 因此<code>authenticate()</code>不需要同步.</p>
 */
public class JNDIRealm extends RealmBase {


    // ----------------------------------------------------- Instance Variables

    /**
     *  要使用的身份验证类型
     */
    protected String authentication = null;

    /**
     * 服务器的连接用户名.
     */
    protected String connectionName = null;


    /**
     * 服务器的连接密码.
     */
    protected String connectionPassword = null;


    /**
     * 服务器的连接URL.
     */
    protected String connectionURL = null;


    /**
     * 链接到目录服务器的目录上下文.
     */
    protected DirContext context = null;


    /**
     * JNDI上下文工厂用来获取InitialContext.
     * 默认情况下，假定LDAP服务器使用标准的JNDI LDAP供应者.
     */
    protected String contextFactory = "com.sun.jndi.ldap.LdapCtxFactory";


    /**
     * 如何在搜索中取消引用别名.
     */
    protected String derefAliases = null;

    /**
     * 保存环境属性的名称的常量, 指定取消引用别名的方式.
     */
    public static final String DEREF_ALIASES = "java.naming.ldap.derefAliases";


    /**
     * 描述信息.
     * @deprecated This will be removed in Tomcat 9 onwards.
     */
    @Deprecated
    protected static final String name = "JNDIRealm";


    /**
     * 用于与目录服务器通信的协议.
     */
    protected String protocol = null;


    /**
     * 是否忽略 PartialResultException, 当迭代NamingEnumeration时?
     * 设置为true, 忽略 PartialResultExceptions.
     */
    protected boolean adCompat = false;


    /**
     * 是否处理referrals? 
     * Caution: 如果DNS不是 AD的一部分, LDAP客户端lib可能尝试在DNS中解析域名，以找到另一个LDAP服务器.
     */
    protected String referrals = null;


    /**
     * 用户搜索的基本元素.
     */
    protected String userBase = "";


    /**
     * 用于搜索用户的消息格式, 使用"{0}" 标记用户名所在的位置.
     */
    protected String userSearch = null;


    /**
     * 当用户搜索时, 是否应在用户当前进行身份验证时执行搜索?
     * 如果是false, 将会使用{@link #connectionName}和{@link #connectionPassword}, 将使用其他的匿名连接.
     */
    private boolean userSearchAsUser = false;


    /**
     * 当前<code>userSearch</code>关联的MessageFormat对象.
     */
    protected MessageFormat userSearchFormat = null;


    /**
     * 应该搜索整个子树来匹配用户吗?
     */
    protected boolean userSubtree = false;


    /**
     * 用于检索用户密码的属性名称.
     */
    protected String userPassword = null;

    /**
     * 用户目录条目内的属性的名称，其中的值将被用于搜索角色.
     * 在嵌套搜索期间不使用此属性
     */
    protected String userRoleAttribute = null;


    /**
     * 一系列LDAP用户模式或路径, ":"-分隔
     * 这些将用于生成用户的专有名称, 使用"{0}" 标记用户名所在的位置.
     * 类似于userPattern, 但允许对用户进行多个搜索.
     */
    protected String[] userPatternArray = null;


    /**
     * 用于形成用户的专有名称的消息格式, 使用"{0}" 标记用户名所在的位置.  
     */
    protected String userPattern = null;


    /**
     * 当前<code>userPatternArray</code>相关的MessageFormat对象数组.
     */
    protected MessageFormat[] userPatternFormatArray = null;

    /**
     * 用于角色搜索的基本元素.
     */
    protected String roleBase = "";


    /**
     * 当前<code>roleBase</code>关联的 MessageFormat对象.
     */
    protected MessageFormat roleBaseFormat = null;


    /**
     * 当前<code>roleBase</code>关联的 MessageFormat对象.
     */
    protected MessageFormat roleFormat = null;


    /**
     * 用户条目中包含该用户角色的属性的名称
     */
    protected String userRoleName = null;


    /**
     * 包含在别处的角色的属性的名称
     */
    protected String roleName = null;


    /**
     * 用于为用户选择角色的消息格式, 使用"{0}" 标记用户的识别名所在的位置.
     * "{1}"和"{2}"在配置引用中描述.
     */
    protected String roleSearch = null;


    /**
     * 应该搜索整个子树来匹配角色吗?
     */
    protected boolean roleSubtree = false;

    /**
     * 应该寻找嵌套的组来确定角色吗?
     */
    protected boolean roleNested = false;

    /**
     * 在搜索用户角色时, 是否应在用户当前进行身份验证时执行搜索?
     * 如果是false, 将使用{@link #connectionName}和{@link #connectionPassword}, 否则将使用匿名连接.
     */
    protected boolean roleSearchAsUser = false;

    /**
     * 交替的 URL, 在connectionURL失败时使用.
     */
    protected String alternateURL;

    /**
     * 连接尝试次数. 如果大于零，则使用替代URL.
     */
    protected int connectionAttempt = 0;

    /**
     *  将此角色添加到每个已验证的用户
     */
    protected String commonRole = null;


    /**
     * 超时时间, 毫秒, 当尝试创建与目录的连接时使用. 默认是 5000 (5 seconds).
     */
    protected String connectionTimeout = "5000";

    /**
     * 超时时间, 毫秒, 当试图从连接到目录中读取时使用. 默认是 5000 (5 seconds).
     */
    protected String readTimeout = "5000";

    /**
     * 当使用{@link #userSearch}配置realm时，使用的sizeLimit. 零表示不限制.
     */
    protected long sizeLimit = 0;

    /**
     * 当使用{@link #userSearch}配置realm时，使用的timeLimit(毫秒). 零表示不限制.
     */
    protected int timeLimit = 0;


    /**
     * 是否使用SPNEGO认证程序的委托凭证
     */
    protected boolean useDelegatedCredential = true;


    /**
     * 在验证之后，应该用于连接到LDAP服务器的QOP.
     * 这个值用于设置LDAP连接的<code>javax.security.sasl.qop</code>环境属性.
     */
    protected String spnegoDelegationQop = "auth-conf";

    /**
     * 是否使用TLS连接
     */
    private boolean useStartTls = false;

    private StartTlsResponse tls = null;

    /**
     * 用于建立TLS连接的启用密码套件列表.
     * <code>null</code>表示使用默认密码套件的方法.
     */
    private String[] cipherSuitesArray = null;

    /**
     * StartTLS安全连接中主机名的验证器.
     * <code>null</code>表示使用默认验证器.
     */
    private HostnameVerifier hostnameVerifier = null;

    /**
     * 要使用的{@link SSLSocketFactory}, 当启用StartTLS的连接时.
     */
    private SSLSocketFactory sslSocketFactory = null;

    /**
     * {@link SSLSocketFactory}的类名.
     * <code>null</code>意味着使用默认工厂.
     */
    private String sslSocketFactoryClassName;

    /**
     * 用于StartTLS的逗号分隔的密码套件列表. 如果为空, 使用默认的套件.
     */
    private String cipherSuites;

    /**
     * {@link HostnameVerifier}的类名. <code>null</code>意味着使用默认验证器.
     */
    private String hostNameVerifierClassName;

    /**
     * StartTLS使用的 ssl协议.
     */
    private String sslProtocol;

    // ------------------------------------------------------------- Properties

    /**
     * @return 使用的身份验证类型.
     */
    public String getAuthentication() {
        return authentication;
    }

    /**
     * 设置要使用的身份验证类型.
     *
     * @param authentication The authentication
     */
    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    /**
     * @return 这个Realm的连接用户名.
     */
    public String getConnectionName() {
        return this.connectionName;
    }


    /**
     * 设置这个Realm的连接用户名.
     *
     * @param connectionName 连接用户名
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }


    /**
     * @return 这个Realm的连接密码.
     */
    public String getConnectionPassword() {
        return this.connectionPassword;
    }


    /**
     * 设置这个Realm的连接密码.
     *
     * @param connectionPassword 连接密码
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }


    /**
     * @return 这个Realm的连接URL.
     */
    public String getConnectionURL() {
        return this.connectionURL;
    }


    /**
     * 设置这个Realm的连接URL.
     *
     * @param connectionURL 连接URL
     */
    public void setConnectionURL(String connectionURL) {
        this.connectionURL = connectionURL;
    }


    /**
     * @return 这个Realm的JNDI上下文工厂.
     */
    public String getContextFactory() {
        return this.contextFactory;
    }


    /**
     * 设置这个Realm的JNDI上下文工厂.
     *
     * @param contextFactory 上下文工厂
     */
    public void setContextFactory(String contextFactory) {
        this.contextFactory = contextFactory;
    }

    /**
     * @return 要使用的derefAliases设置.
     */
    public java.lang.String getDerefAliases() {
        return derefAliases;
    }

    /**
     * 当设置目录时，要使用的derefAliases设置.
     *
     * @param derefAliases New value of property derefAliases.
     */
    public void setDerefAliases(java.lang.String derefAliases) {
      this.derefAliases = derefAliases;
    }

    /**
     * @return 要使用的协议.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * 设置这个Realm的协议.
     *
     * @param protocol 协议.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }


    /**
     * @return 处理PartialResultExceptions的当前设置
     */
    public boolean getAdCompat () {
        return adCompat;
    }


    /**
     * 是否处理PartialResultExceptions?
     * True: 忽略所有的 PartialResultExceptions.
     * 
     * @param adCompat <code>true</code>忽略部分结果
     */
    public void setAdCompat (boolean adCompat) {
        this.adCompat = adCompat;
    }


    /**
     * @return 处理JNDIreferrals的当前设置.
     */
    public String getReferrals () {
        return referrals;
    }


    /**
     * 如何处理JNDI referrals? 忽略, 追加, 或抛出.
     * 
     * @param referrals The referral handling
     */
    public void setReferrals (String referrals) {
        this.referrals = referrals;
    }


    /**
     * @return 用户搜索的基本元素.
     */
    public String getUserBase() {
        return this.userBase;
    }


    /**
     * 设置用户搜索的基本元素.
     *
     * @param userBase 基本元素
     */
    public void setUserBase(String userBase) {
        this.userBase = userBase;
    }


    /**
     * @return 这个Realm的用户选择的消息格式模式.
     */
    public String getUserSearch() {
        return this.userSearch;
    }


    /**
     * 设置这个Realm的用户选择的消息格式模式.
     *
     * @param userSearch 用户搜索模式
     */
    public void setUserSearch(String userSearch) {

        this.userSearch = userSearch;
        if (userSearch == null)
            userSearchFormat = null;
        else
            userSearchFormat = new MessageFormat(userSearch);

    }


    public boolean isUserSearchAsUser() {
        return userSearchAsUser;
    }


    public void setUserSearchAsUser(boolean userSearchAsUser) {
        this.userSearchAsUser = userSearchAsUser;
    }


    /**
     * @return "为用户搜索子树"标志.
     */
    public boolean getUserSubtree() {
        return this.userSubtree;
    }


    /**
     * 设置"为用户搜索子树"标志.
     *
     * @param userSubtree The new search flag
     */
    public void setUserSubtree(boolean userSubtree) {
        this.userSubtree = userSubtree;
    }


    /**
     * @return 这个Realm的用户角色名称属性名称.
     */
    public String getUserRoleName() {
        return userRoleName;
    }


    /**
     * 设置这个Realm的用户角色名称属性名称.
     *
     * @param userRoleName userRole名称属性名称
     */
    public void setUserRoleName(String userRoleName) {
        this.userRoleName = userRoleName;
    }


    /**
     * @return 角色搜索的基本元素.
     */
    public String getRoleBase() {
        return this.roleBase;
    }


    /**
     * 设置角色搜索的基本元素.
     *
     * @param roleBase The new base element
     */
    public void setRoleBase(String roleBase) {

        this.roleBase = roleBase;
        if (roleBase == null)
            roleBaseFormat = null;
        else
            roleBaseFormat = new MessageFormat(roleBase);

    }


    /**
     * @return 这个Realm的角色名称属性名.
     */
    public String getRoleName() {
        return this.roleName;
    }


    /**
     * 设置这个Realm的角色名称属性名.
     *
     * @param roleName 角色名称属性名
     */
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }


    /**
     * @return 这个Realm的角色选择的消息格式模式.
     */
    public String getRoleSearch() {
        return this.roleSearch;
    }


    /**
     * 设置这个Realm的角色选择的消息格式模式.
     *
     * @param roleSearch 角色搜索模式
     */
    public void setRoleSearch(String roleSearch) {

        this.roleSearch = roleSearch;
        if (roleSearch == null)
            roleFormat = null;
        else
            roleFormat = new MessageFormat(roleSearch);

    }


    public boolean isRoleSearchAsUser() {
        return roleSearchAsUser;
    }


    public void setRoleSearchAsUser(boolean roleSearchAsUser) {
        this.roleSearchAsUser = roleSearchAsUser;
    }


    /**
     * @return "search subtree for roles"标记.
     */
    public boolean getRoleSubtree() {
        return this.roleSubtree;
    }


    /**
     * 设置"search subtree for roles"标记.
     *
     * @param roleSubtree The new search flag
     */
    public void setRoleSubtree(boolean roleSubtree) {
        this.roleSubtree = roleSubtree;
    }

    /**
     * @return "The nested group search flag"标志.
     */
    public boolean getRoleNested() {
        return this.roleNested;
    }


    /**
     * 设置"search subtree for roles"标志.
     *
     * @param roleNested The nested group search flag
     */
    public void setRoleNested(boolean roleNested) {
        this.roleNested = roleNested;
    }


    /**
     * @return 用于检索用户密码的密码属性.
     */
    public String getUserPassword() {
        return this.userPassword;
    }


    /**
     * 设置用于检索用户密码的密码属性.
     *
     * @param userPassword 密码属性
     */
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }


    public String getUserRoleAttribute() {
        return userRoleAttribute;
    }

    public void setUserRoleAttribute(String userRoleAttribute) {
        this.userRoleAttribute = userRoleAttribute;
    }

    /**
     * @return 用户选择的消息格式模式.
     */
    public String getUserPattern() {
        return this.userPattern;
    }




    /**
     * 设置用于选择用户的消息格式模式.
     * 这可能是一个简单的模式, 或要尝试的多种模式,用括号分隔. (例如, "cn={0}", 或
     * "(cn={0})(cn={0},o=myorg)" 还支持完整的LDAP搜索字符串,
     * 只支持 "OR", "|" 语法, 因此"(|(cn={0})(cn={0},o=myorg))"是有效的. 复杂搜索字符串 &, 等不支持.
     *
     * @param userPattern The new user pattern
     */
    public void setUserPattern(String userPattern) {

        this.userPattern = userPattern;
        if (userPattern == null)
            userPatternArray = null;
        else {
            userPatternArray = parseUserPatternString(userPattern);
            int len = this.userPatternArray.length;
            userPatternFormatArray = new MessageFormat[len];
            for (int i=0; i < len; i++) {
                userPatternFormatArray[i] =
                    new MessageFormat(userPatternArray[i]);
            }
        }
    }


    public String getAlternateURL() {
        return this.alternateURL;
    }


    public void setAlternateURL(String alternateURL) {
        this.alternateURL = alternateURL;
    }


    public String getCommonRole() {
        return commonRole;
    }


    public void setCommonRole(String commonRole) {
        this.commonRole = commonRole;
    }


    public String getConnectionTimeout() {
        return connectionTimeout;
    }


    public void setConnectionTimeout(String timeout) {
        this.connectionTimeout = timeout;
    }

    public String getReadTimeout() {
        return readTimeout;
    }


    public void setReadTimeout(String timeout) {
        this.readTimeout = timeout;
    }


    public long getSizeLimit() {
        return sizeLimit;
    }


    public void setSizeLimit(long sizeLimit) {
        this.sizeLimit = sizeLimit;
    }


    public int getTimeLimit() {
        return timeLimit;
    }


    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }


    public boolean isUseDelegatedCredential() {
        return useDelegatedCredential;
    }

    public void setUseDelegatedCredential(boolean useDelegatedCredential) {
        this.useDelegatedCredential = useDelegatedCredential;
    }


    public String getSpnegoDelegationQop() {
        return spnegoDelegationQop;
    }

    public void setSpnegoDelegationQop(String spnegoDelegationQop) {
        this.spnegoDelegationQop = spnegoDelegationQop;
    }


    /**
     * @return 是否使用StartTLS连接ldap 服务器
     */
    public boolean getUseStartTls() {
        return useStartTls;
    }

    /**
     * 是否使用StartTLS连接ldap 服务器.
     *
     * @param useStartTls {@code true}使用StartTLS. 默认是{@code false}.
     */
    public void setUseStartTls(boolean useStartTls) {
        this.useStartTls = useStartTls;
    }

    /**
     * @return 允许的密码套件列表，当连接StartTLS时
     */
    private String[] getCipherSuitesArray() {
        if (cipherSuites == null || cipherSuitesArray != null) {
            return cipherSuitesArray;
        }
        if (this.cipherSuites.trim().isEmpty()) {
            containerLog.warn(sm.getString("jndiRealm.emptyCipherSuites"));
            this.cipherSuitesArray = null;
        } else {
            this.cipherSuitesArray = cipherSuites.trim().split("\\s*,\\s*");
            containerLog.debug(sm.getString("jndiRealm.cipherSuites",
                    Arrays.toString(this.cipherSuitesArray)));
        }
        return this.cipherSuitesArray;
    }

    /**
     * 设置允许的密码套件列表，当连接StartTLS时.
     * 密码套件为逗号分隔列表.
     *
     * @param suites 允许的密码套件的逗号分隔列表
     */
    public void setCipherSuites(String suites) {
        this.cipherSuites = suites;
    }

    /**
     * @return 使用StartTLS连接的{@link HostnameVerifier}类名, 或空字符串, 如果应该使用默认验证器.
     */
    public String getHostnameVerifierClassName() {
        if (this.hostnameVerifier == null) {
            return "";
        }
        return this.hostnameVerifier.getClass().getCanonicalName();
    }

    /**
     * 设置使用StartTLS打开连接时使用的{@link HostnameVerifier}.
     * 使用默认构造函数构造给定类名的实例.
     *
     * @param verifierClassName 要构造的{@link HostnameVerifier}的类名
     */
    public void setHostnameVerifierClassName(String verifierClassName) {
        if (verifierClassName != null) {
            this.hostNameVerifierClassName = verifierClassName.trim();
        } else {
            this.hostNameVerifierClassName = null;
        }
    }

    /**
     * @return 用于证书验证使用的{@link HostnameVerifier}，当使用StartTLS打开连接时.
     */
    public HostnameVerifier getHostnameVerifier() {
        if (this.hostnameVerifier != null) {
            return this.hostnameVerifier;
        }
        if (this.hostNameVerifierClassName == null
                || hostNameVerifierClassName.equals("")) {
            return null;
        }
        try {
            Object o = constructInstance(hostNameVerifierClassName);
            if (o instanceof HostnameVerifier) {
                this.hostnameVerifier = (HostnameVerifier) o;
                return this.hostnameVerifier;
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "jndiRealm.invalidHostnameVerifier",
                        hostNameVerifierClassName));
            }
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException(sm.getString(
                    "jndiRealm.invalidHostnameVerifier",
                    hostNameVerifierClassName), e);
        }
    }

    /**
     * 设置使用的 {@link SSLSocketFactory}, 当使用StartTLS打开连接时.
     * 将使用默认构造函数创建具有给定名称的工厂实例. 也可以使用{@link JNDIRealm#setSslProtocol(String) setSslProtocol(String)}设置 SSLSocketFactory.
     *
     * @param factoryClassName 要创建的工厂的类名
     */
    public void setSslSocketFactoryClassName(String factoryClassName) {
        this.sslSocketFactoryClassName = factoryClassName;
    }

    /**
     * 设置用于连接的SSL协议.
     *
     * @param protocol 允许的SSL协议名称之一
     */
    public void setSslProtocol(String protocol) {
        this.sslProtocol = protocol;
    }

    /**
     * @return 通过默认{@link SSLContext}支持的SSL协议的列表
     */
    private String[] getSupportedSslProtocols() {
        try {
            SSLContext sslContext = SSLContext.getDefault();
            return sslContext.getSupportedSSLParameters().getProtocols();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(sm.getString("jndiRealm.exception"), e);
        }
    }

    private Object constructInstance(String className)
            throws ReflectiveOperationException {
        Class<?> clazz = Class.forName(className);
        return clazz.getConstructor().newInstance();
    }

    // ---------------------------------------------------------- Realm Methods

    /**
     * 返回指定用户名和凭据关联的 Principal; 或者<code>null</code>.
     *
     * 如果JDBC连接有任何错误, 执行查询或返回null的任何操作 (不验证).
     * 此事件也被记录, 连接将被关闭，以便随后的请求将自动重新打开它.
     *
     * @param username 要查找的Principal的用户名
     * @param credentials 验证这个用户名使用的Password 或其它凭据
     * 
     * @return 关联的主体, 或<code>null</code>.
     */
    @Override
    public Principal authenticate(String username, String credentials) {

        DirContext context = null;
        Principal principal = null;

        try {

            // 确保有可用的目录上下文
            context = open();

            // 目录上下文将偶尔超时. 再试一次
            try {
                // 验证指定的用户名
                principal = authenticate(context, username, credentials);
            } catch (NullPointerException | NamingException e) {
                /*
                 * BZ 61313
                 * NamingException 通过故障恢复可恢复的错误. 因此，需要做出是否失败的决定.
                 * 通常, 在不适当的时候失败是比在适当的时候不失败更好，因此代码总是试图使用NamingException失败.
                 */

                /*
                 * BZ 42449
                 * Catch NPE - Kludge Sun's LDAP provider with broken SSL.
                 */

                // log the exception so we know it's there.
                containerLog.info(sm.getString("jndiRealm.exception.retry"), e);

                // 关闭连接，知道它将被重新打开.
                if (context != null)
                    close(context);

                // 打开新目录上下文.
                context = open();

                // 再次尝试身份验证.
                principal = authenticate(context, username, credentials);
            }


            // 释放上下文
            release(context);

            // 返回已验证的Principal
            return principal;
        } catch (NamingException e) {

            // Log the problem for posterity
            containerLog.error(sm.getString("jndiRealm.exception"), e);

            // 关闭连接，以便下次重新打开
            if (context != null)
                close(context);

            // 返回这个请求的"not authenticated"
            if (containerLog.isDebugEnabled())
                containerLog.debug("Returning null principal.");
            return null;
        }
    }


    /**
     * 返回指定用户名和凭据关联的 Principal; 或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 要查找的Principal的用户名
     * @param credentials 验证这个用户名使用的Password 或其它凭据
     * 
     * @return 关联的主体, 或<code>null</code>.
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    public synchronized Principal authenticate(DirContext context,
                                               String username,
                                               String credentials)
        throws NamingException {

        if (username == null || username.equals("")
            || credentials == null || credentials.equals("")) {
            if (containerLog.isDebugEnabled())
                containerLog.debug("username null or empty: returning null principal.");
            return null;
        }

        if (userPatternArray != null) {
            for (int curUserPattern = 0;
                 curUserPattern < userPatternFormatArray.length;
                 curUserPattern++) {
                // Retrieve user information
                User user = getUser(context, username, credentials, curUserPattern);
                if (user != null) {
                    try {
                        // 检查用户的凭证
                        if (checkCredentials(context, user, credentials)) {
                            // 搜索其他角色
                            List<String> roles = getRoles(context, user);
                            if (containerLog.isDebugEnabled()) {
                                containerLog.debug("Found roles: " + roles.toString());
                            }
                            return (new GenericPrincipal(username, credentials, roles));
                        }
                    } catch (InvalidNameException ine) {
                        // Log the problem for posterity
                        containerLog.warn(sm.getString("jndiRealm.exception"), ine);
                        // ignore; 这可能是由于名称不完全符合搜索路径格式, 完全一样-
                        // 合格的名字已经变成搜索路径
                        // 已经包含 cn= 或 vice-versa
                    }
                }
            }
            return null;
        } else {
            // Retrieve user information
            User user = getUser(context, username, credentials);
            if (user == null)
                return null;

            // 检查用户的凭证
            if (!checkCredentials(context, user, credentials))
                return null;

            // 搜索其他角色
            List<String> roles = getRoles(context, user);
            if (containerLog.isDebugEnabled()) {
                containerLog.debug("Found roles: " + roles.toString());
            }

            // 创建并返回合适的 Principal
            return new GenericPrincipal(username, credentials, roles);
        }
    }


    /**
     * 返回包含指定用户名的用户信息的用户对象;或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 要查找的用户名
     * 
     * @return the User object
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUser(DirContext context, String username)
        throws NamingException {

        return getUser(context, username, null, -1);
    }


    /**
     * 返回包含指定用户名的用户信息的用户对象;或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 要查找的用户名
     * @param credentials 用户凭证(可选)
     * 
     * @return the User object
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUser(DirContext context, String username, String credentials)
        throws NamingException {

        return getUser(context, username, credentials, -1);
    }


    /**
     * 返回包含指定用户名的用户信息的用户对象;或者<code>null</code>.
     *
     * 如果指定<code>userPassword</code>配置属性, 该属性的值是从用户的目录项检索的.
     * 如果指定<code>userRoleName</code>配置属性, 该属性的所有值都从目录项中检索.
     *
     * @param context 目录上下文
     * @param username 要查找的用户名
     * @param credentials 用户凭证(可选)
     * @param curUserPattern 到userPatternFormatArray的索引
     * 
     * @return the User object
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUser(DirContext context, String username,
                           String credentials, int curUserPattern)
        throws NamingException {

        User user = null;

        // Get attributes to retrieve from user entry
        ArrayList<String> list = new ArrayList<>();
        if (userPassword != null)
            list.add(userPassword);
        if (userRoleName != null)
            list.add(userRoleName);
        if (userRoleAttribute != null) {
            list.add(userRoleAttribute);
        }
        String[] attrIds = new String[list.size()];
        list.toArray(attrIds);

        // Use pattern or search for user entry
        if (userPatternFormatArray != null && curUserPattern >= 0) {
            user = getUserByPattern(context, username, credentials, attrIds, curUserPattern);
            if (containerLog.isDebugEnabled()) {
                containerLog.debug("Found user by pattern [" + user + "]");
            }
        } else {
            boolean thisUserSearchAsUser = isUserSearchAsUser();
            try {
                if (thisUserSearchAsUser) {
                    userCredentialsAdd(context, username, credentials);
                }
                user = getUserBySearch(context, username, attrIds);
            } finally {
                if (thisUserSearchAsUser) {
                    userCredentialsRemove(context);
                }
            }
            if (containerLog.isDebugEnabled()) {
                containerLog.debug("Found user by search [" + user + "]");
            }
        }

        if (userPassword == null && credentials != null && user != null) {
            // 密码可用. 插入它，因为它可能需要角色搜索.
            return new User(user.getUserName(), user.getDN(), credentials,
                    user.getRoles(), user.getUserRoleId());
        }
        return user;
    }


    /**
     * 使用<code>UserPattern</code>配置属性使用指定的用户名定位用户的目录条目并返回用户对象; 或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 用户名
     * @param attrIds 要检索的属性的名称
     * @param dn 用户检索的识别名.
     * 
     * @return the User object
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUserByPattern(DirContext context,
                                    String username,
                                    String[] attrIds,
                                    String dn)
        throws NamingException {

        // If no attributes are requested, no need to look for them
        if (attrIds == null || attrIds.length == 0) {
            return new User(username, dn, null, null,null);
        }

        // Get required attributes from user entry
        Attributes attrs = null;
        try {
            attrs = context.getAttributes(dn, attrIds);
        } catch (NameNotFoundException e) {
            return null;
        }
        if (attrs == null)
            return null;

        // Retrieve value of userPassword
        String password = null;
        if (userPassword != null)
            password = getAttributeValue(userPassword, attrs);

        String userRoleAttrValue = null;
        if (userRoleAttribute != null) {
            userRoleAttrValue = getAttributeValue(userRoleAttribute, attrs);
        }

        // Retrieve values of userRoleName attribute
        ArrayList<String> roles = null;
        if (userRoleName != null)
            roles = addAttributeValues(userRoleName, attrs, roles);

        return new User(username, dn, password, roles, userRoleAttrValue);
    }


    /**
     * 使用<code>UserPattern</code>配置属性使用指定的用户名定位用户的目录条目并返回用户对象; 或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 用户名
     * @param credentials 用户凭据(可选)
     * @param attrIds 要检索的属性的名称
     * @param curUserPattern userPatternFormatArray的索引
     * 
     * @return the User object
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUserByPattern(DirContext context,
                                    String username,
                                    String credentials,
                                    String[] attrIds,
                                    int curUserPattern)
        throws NamingException {

        User user = null;

        if (username == null || userPatternFormatArray[curUserPattern] == null)
            return null;

        // Form the dn from the user pattern
        String dn = userPatternFormatArray[curUserPattern].format(new String[] { username });

        try {
            user = getUserByPattern(context, username, attrIds, dn);
        } catch (NameNotFoundException e) {
            return null;
        } catch (NamingException e) {
            // 如果调用getUserByPattern()失败, 用正在搜索的用户的凭据再试一次
            try {
                userCredentialsAdd(context, dn, credentials);

                user = getUserByPattern(context, username, attrIds, dn);
            } finally {
                userCredentialsRemove(context);
            }
        }
        return user;
    }


    /**
     * 在目录中搜索包含指定用户名的用户信息的用户对象; 或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 用户名
     * @param attrIds 要检索的属性的名称
     * 
     * @return the User object
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUserBySearch(DirContext context,
                                   String username,
                                   String[] attrIds)
        throws NamingException {

        if (username == null || userSearchFormat == null)
            return null;

        // Form the search filter
        String filter = userSearchFormat.format(new String[] { username });

        // Set up the search controls
        SearchControls constraints = new SearchControls();

        if (userSubtree) {
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
        }
        else {
            constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        }

        constraints.setCountLimit(sizeLimit);
        constraints.setTimeLimit(timeLimit);

        // 指定要检索的属性
        if (attrIds == null)
            attrIds = new String[0];
        constraints.setReturningAttributes(attrIds);

        NamingEnumeration<SearchResult> results =
            context.search(userBase, filter, constraints);

        try {
            // Fail if no entries found
            try {
                if (results == null || !results.hasMore()) {
                    return null;
                }
            } catch (PartialResultException ex) {
                if (!adCompat)
                    throw ex;
                else
                    return null;
            }

            // 获取第一个条目的结果
            SearchResult result = results.next();

            // 检查没有进一步的条目
            try {
                if (results.hasMore()) {
                    if(containerLog.isInfoEnabled())
                        containerLog.info("username " + username + " has multiple entries");
                    return null;
                }
            } catch (PartialResultException ex) {
                if (!adCompat)
                    throw ex;
            }

            String dn = getDistinguishedName(context, userBase, result);

            if (containerLog.isTraceEnabled())
                containerLog.trace("  entry found for " + username + " with dn " + dn);

            // 获取条目的属性
            Attributes attrs = result.getAttributes();
            if (attrs == null)
                return null;

            // 检索userPassword的值
            String password = null;
            if (userPassword != null)
                password = getAttributeValue(userPassword, attrs);

            String userRoleAttrValue = null;
            if (userRoleAttribute != null) {
                userRoleAttrValue = getAttributeValue(userRoleAttribute, attrs);
            }

            // 检索userRoleName 属性的值
            ArrayList<String> roles = null;
            if (userRoleName != null)
                roles = addAttributeValues(userRoleName, attrs, roles);

            return new User(username, dn, password, roles, userRoleAttrValue);
        } finally {
            if (results != null) {
                results.close();
            }
        }
    }


    /**
     * 检查给定的用户是否可以通过给定的凭据进行身份验证. 
     * 如果指定<code>userPassword</code>配置属性, 先前从目录检索的凭据与用户呈现的显式进行的比较.
     * 否则，通过绑定到用户的目录检查所提交的凭据.
     *
     * @param context 目录上下文
     * @param user 要验证的User
     * @param credentials 用户提交的凭据
     * 
     * @return <code>true</code>如果凭证被验证
     * @exception NamingException 如果出现目录服务器错误
     */
    protected boolean checkCredentials(DirContext context,
                                     User user,
                                     String credentials)
         throws NamingException {

         boolean validated = false;

         if (userPassword == null) {
             validated = bindAsUser(context, user, credentials);
         } else {
             validated = compareCredentials(context, user, credentials);
         }

         if (containerLog.isTraceEnabled()) {
             if (validated) {
                 containerLog.trace(sm.getString("jndiRealm.authenticateSuccess",
                                  user.getUserName()));
             } else {
                 containerLog.trace(sm.getString("jndiRealm.authenticateFailure",
                                  user.getUserName()));
             }
         }
         return validated;
     }


    /**
     * 检查用户提交的凭据与从目录检索的凭据是否匹配.
     *
     * @param context 目录上下文
     * @param info 要认证的User
     * @param credentials 认证凭证
     * 
     * @return <code>true</code>如果凭证被验证
     * @exception NamingException 如果出现目录服务器错误
     */
    protected boolean compareCredentials(DirContext context,
                                         User info,
                                         String credentials)
        throws NamingException {

        // 验证用户指定的凭据
        if (containerLog.isTraceEnabled())
            containerLog.trace("  validating credentials");

        if (info == null || credentials == null)
            return false;

        String password = info.getPassword();

        return getCredentialHandler().matches(credentials, password);
    }


    /**
     * 检查凭据，通过绑定用户到目录
     *
     * @param context 目录上下文
     * @param user 要验证的用户
     * @param credentials 验证的凭据
     * 
     * @return <code>true</code>如果凭证被验证
     * @exception NamingException 如果出现目录服务器错误
     */
     protected boolean bindAsUser(DirContext context,
                                  User user,
                                  String credentials)
         throws NamingException {

         if (credentials == null || user == null)
             return false;

         String dn = user.getDN();
         if (dn == null)
             return false;

         // 验证用户指定的凭据
         if (containerLog.isTraceEnabled()) {
             containerLog.trace("  validating credentials by binding as the user");
        }

        userCredentialsAdd(context, dn, credentials);

        // 引发LDAP绑定操作
        boolean validated = false;
        try {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace("  binding as "  + dn);
            }
            context.getAttributes("", null);
            validated = true;
        }
        catch (AuthenticationException e) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace("  bind attempt failed");
            }
        }

        userCredentialsRemove(context);

        return validated;
    }

     /**
      * 配置上下文以使用所提供的凭证进行身份验证.
      *
      * @param context      配置的DirContext
      * @param dn           用户地识别名
      * @param credentials  用户的凭证
      * @exception NamingException 如果出现目录服务器错误
      */
    private void userCredentialsAdd(DirContext context, String dn,
            String credentials) throws NamingException {
        // 设置安全环境以绑定用户
        context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
        context.addToEnvironment(Context.SECURITY_CREDENTIALS, credentials);
    }

    /**
     * 配置上下文，使用{@link #connectionName}和{@link #connectionPassword}, 如果未指定这些属性，则指定连接或匿名连接.
     *
      * @param context      要配置的DirContext
      * @exception NamingException 如果出现目录服务器错误
     */
    private void userCredentialsRemove(DirContext context)
            throws NamingException {
        // 恢复原始安全环境
        if (connectionName != null) {
            context.addToEnvironment(Context.SECURITY_PRINCIPAL,
                                     connectionName);
        } else {
            context.removeFromEnvironment(Context.SECURITY_PRINCIPAL);
        }

        if (connectionPassword != null) {
            context.addToEnvironment(Context.SECURITY_CREDENTIALS,
                                     connectionPassword);
        }
        else {
            context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
        }
    }

    /**
     * 返回与给定用户关联的角色列表.
     * 用户目录条目中的任何角色都可以通过目录搜索得到补充. 如果没有与该用户关联的角色，则返回一个零长度列表.
     *
     * @param context 搜索的目录上下文
     * @param user 要检查的用户
     * 
     * @return 角色名称列表
     * @exception NamingException 如果出现目录服务器错误
     */
    protected List<String> getRoles(DirContext context, User user)
        throws NamingException {

        if (user == null)
            return null;

        String dn = user.getDN();
        String username = user.getUserName();
        String userRoleId = user.getUserRoleId();

        if (dn == null || username == null)
            return null;

        if (containerLog.isTraceEnabled())
            containerLog.trace("  getRoles(" + dn + ")");

        // 从用户条目中检索的角色
        List<String> list = new ArrayList<>();
        List<String> userRoles = user.getRoles();
        if (userRoles != null) {
            list.addAll(userRoles);
        }
        if (commonRole != null)
            list.add(commonRole);

        if (containerLog.isTraceEnabled()) {
            containerLog.trace("  Found " + list.size() + " user internal roles");
            containerLog.trace("  Found user internal roles " + list.toString());
        }

        // 是否配置了角色搜索?
        if ((roleFormat == null) || (roleName == null))
            return list;

        // 为适当的搜索设置参数
        String filter = roleFormat.format(new String[] { doRFC2254Encoding(dn), username, userRoleId });
        SearchControls controls = new SearchControls();
        if (roleSubtree)
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        else
            controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        controls.setReturningAttributes(new String[] {roleName});

        String base = null;
        if (roleBaseFormat != null) {
            NameParser np = context.getNameParser("");
            Name name = np.parse(dn);
            String nameParts[] = new String[name.size()];
            for (int i = 0; i < name.size(); i++) {
                nameParts[i] = name.get(i);
            }
            base = roleBaseFormat.format(nameParts);
        } else {
            base = "";
        }

        // 执行配置的搜索并处理结果
        NamingEnumeration<SearchResult> results = searchAsUser(context, user, base, filter, controls,
                isRoleSearchAsUser());

        if (results == null)
            return list;  // Should never happen, but just in case ...

        HashMap<String, String> groupMap = new HashMap<>();
        try {
            while (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();
                if (attrs == null)
                    continue;
                String dname = getDistinguishedName(context, roleBase, result);
                String name = getAttributeValue(roleName, attrs);
                if (name != null && dname != null) {
                    groupMap.put(dname, name);
                }
            }
        } catch (PartialResultException ex) {
            if (!adCompat)
                throw ex;
        } finally {
            results.close();
        }

        if (containerLog.isTraceEnabled()) {
            Set<Entry<String, String>> entries = groupMap.entrySet();
            containerLog.trace("  Found " + entries.size() + " direct roles");
            for (Entry<String, String> entry : entries) {
                containerLog.trace(  "  Found direct role " + entry.getKey() + " -> " + entry.getValue());
            }
        }

        // 如果启用嵌套组搜索, 执行嵌套组的搜索，直到未找到新组为止
        if (getRoleNested()) {

            // The following efficient algorithm is known as memberOf Algorithm, as described in "Practices in
            // Directory Groups". It avoids group slurping and handles cyclic group memberships as well.
            // See http://middleware.internet2.edu/dir/ for details

            Map<String, String> newGroups = new HashMap<>(groupMap);
            while (!newGroups.isEmpty()) {
                Map<String, String> newThisRound = new HashMap<>(); // Stores the groups we find in this iteration

                for (Entry<String, String> group : newGroups.entrySet()) {
                    filter = roleFormat.format(new String[] { group.getKey(), group.getValue(), group.getValue() });

                    if (containerLog.isTraceEnabled()) {
                        containerLog.trace("Perform a nested group search with base "+ roleBase + " and filter " + filter);
                    }

                    results = searchAsUser(context, user, roleBase, filter, controls,
                            isRoleSearchAsUser());

                    try {
                        while (results.hasMore()) {
                            SearchResult result = results.next();
                            Attributes attrs = result.getAttributes();
                            if (attrs == null)
                                continue;
                            String dname = getDistinguishedName(context, roleBase, result);
                            String name = getAttributeValue(roleName, attrs);
                            if (name != null && dname != null && !groupMap.keySet().contains(dname)) {
                                groupMap.put(dname, name);
                                newThisRound.put(dname, name);

                                if (containerLog.isTraceEnabled()) {
                                    containerLog.trace("  Found nested role " + dname + " -> " + name);
                                }

                            }
                         }
                    } catch (PartialResultException ex) {
                        if (!adCompat)
                            throw ex;
                    } finally {
                        results.close();
                    }
                }

                newGroups = newThisRound;
            }
        }

        list.addAll(groupMap.values());
        return list;
    }

    /**
     * 在上下文中执行搜索, 当{@code searchAsUser} 是 {@code true}时, 否则使用默认凭据搜索上下文.
     *
     * @param context 要搜索的上下文
     * @param user 绑定的用户
     * @param base 开始搜索的基础
     * @param filter 用于搜索的过滤器
     * @param controls 用于搜索的控件
     * @param searchAsUser
     *            {@code true}当用户应进行搜索时, 
     *            {@code false}使用默认凭据
     *            
     * @return enumeration with all found entries
     * @throws NamingException 如果出现目录服务器错误
     */
    private NamingEnumeration<SearchResult> searchAsUser(DirContext context,
            User user, String base, String filter,
            SearchControls controls, boolean searchAsUser) throws NamingException {
        NamingEnumeration<SearchResult> results;
        try {
            if (searchAsUser) {
                userCredentialsAdd(context, user.getDN(), user.getPassword());
            }
            results = context.search(base, filter, controls);
        } finally {
            if (searchAsUser) {
                userCredentialsRemove(context);
            }
        }
        return results;
    }


    /**
     * 返回指定属性的值.
     *
     * @param attrId 属性名
     * @param attrs 包含所需值的属性
     * 
     * @return the attribute value
     * @exception NamingException 如果出现目录服务器错误
     */
    private String getAttributeValue(String attrId, Attributes attrs)
        throws NamingException {

        if (containerLog.isTraceEnabled())
            containerLog.trace("  retrieving attribute " + attrId);

        if (attrId == null || attrs == null)
            return null;

        Attribute attr = attrs.get(attrId);
        if (attr == null)
            return null;
        Object value = attr.get();
        if (value == null)
            return null;
        String valueString = null;
        if (value instanceof byte[])
            valueString = new String((byte[]) value);
        else
            valueString = value.toString();

        return valueString;
    }


    /**
     * 将指定属性的值添加到列表中
     *
     * @param attrId 属性名称
     * @param attrs 包含新值的属性
     * @param values 包含发现的值
     * 
     * @return 属性值列表
     * @exception NamingException 如果出现目录服务器错误
     */
    private ArrayList<String> addAttributeValues(String attrId,
                                         Attributes attrs,
                                         ArrayList<String> values)
        throws NamingException{

        if (containerLog.isTraceEnabled())
            containerLog.trace("  retrieving values for attribute " + attrId);
        if (attrId == null || attrs == null)
            return values;
        if (values == null)
            values = new ArrayList<>();
        Attribute attr = attrs.get(attrId);
        if (attr == null)
            return values;
        NamingEnumeration<?> e = attr.getAll();
        try {
            while(e.hasMore()) {
                String value = (String)e.next();
                values.add(value);
            }
        } catch (PartialResultException ex) {
            if (!adCompat)
                throw ex;
        } finally {
            e.close();
        }
        return values;
    }


    /**
     * 关闭任何与目录服务器的打开连接.
     *
     * @param context 要关闭的目录上下文
     */
    protected void close(DirContext context) {

        // Do nothing if there is no opened connection
        if (context == null)
            return;

        // Close tls startResponse if used
        if (tls != null) {
            try {
                tls.close();
            } catch (IOException e) {
                containerLog.error(sm.getString("jndiRealm.tlsClose"), e);
            }
        }
        // Close our opened connection
        try {
            if (containerLog.isDebugEnabled())
                containerLog.debug("Closing directory context");
            context.close();
        } catch (NamingException e) {
            containerLog.error(sm.getString("jndiRealm.close"), e);
        }
        this.context = null;

    }


    @Override
    @Deprecated
    protected String getName() {
        return name;
    }


    /**
     * 返回指定用户名的密码.
     * 
     * @param username 用户名
     * 
     * @return 与给定主体用户名关联的密码.
     */
    @Override
    protected String getPassword(String username) {
        String userPassword = getUserPassword();
        if (userPassword == null || userPassword.isEmpty()) {
            return null;
        }

        try {
            User user = getUser(open(), username, null);
             if (user == null) {
                // User should be found...
                return null;
            } else {
                // ... and have a password
                return user.getPassword();
            }
        } catch (NamingException e) {
            return null;
        }

    }

    /**
     * 返回给定用户名的Principal.
     * 
     * @param username 用户名
     * @return 关联的Principal
     */
    @Override
    protected Principal getPrincipal(String username) {
        return getPrincipal(username, null);
    }

    @Override
    protected Principal getPrincipal(String username,
            GSSCredential gssCredential) {

        DirContext context = null;
        Principal principal = null;

        try {

            // 确保有一个可用的目录上下文
            context = open();

            // 有时目录上下文将超时. 在放弃之前再试一次.
            try {

                // 验证指定用户名
                principal = getPrincipal(context, username, gssCredential);
            } catch (CommunicationException | ServiceUnavailableException e) {

                // log the exception so we know it's there.
                containerLog.info(sm.getString("jndiRealm.exception.retry"), e);

                // 关闭连接，因为它将被重新打开.
                if (context != null)
                    close(context);

                // 打开目录上下文.
                context = open();

                // 再次尝试身份验证.
                principal = getPrincipal(context, username, gssCredential);
            }

            // 释放上下文
            release(context);

            // 返回验证的Principal
            return principal;
        } catch (NamingException e) {

            // Log the problem for posterity
            containerLog.error(sm.getString("jndiRealm.exception"), e);

            // 关闭连接，以便下次重新打开
            if (context != null)
                close(context);

            // 返回这个请求的"not authenticated"
            return null;
        }
    }


    /**
     * 获取与指定证书关联的主体.
     * 
     * @param context 目录上下文
     * @param username 用户名
     * @param gssCredential 凭证
     * 
     * @return 给定凭据关联的Principal
     * @exception NamingException 如果出现目录服务器错误
     */
    protected synchronized Principal getPrincipal(DirContext context,
            String username, GSSCredential gssCredential)
        throws NamingException {

        User user = null;
        List<String> roles = null;
        Hashtable<?, ?> preservedEnvironment = null;

        try {
            if (gssCredential != null && isUseDelegatedCredential()) {
                // 保存当前上下文环境参数
                preservedEnvironment = context.getEnvironment();
                // Set up context
                context.addToEnvironment(
                        Context.SECURITY_AUTHENTICATION, "GSSAPI");
                context.addToEnvironment(
                        "javax.security.sasl.server.authentication", "true");
                context.addToEnvironment(
                        "javax.security.sasl.qop", spnegoDelegationQop);
                // Note: Subject already set in SPNEGO authenticator so no need
                //       for Subject.doAs() here
            }
            user = getUser(context, username);
            if (user != null) {
                roles = getRoles(context, user);
            }
        } finally {
            restoreEnvironmentParameter(context,
                    Context.SECURITY_AUTHENTICATION, preservedEnvironment);
            restoreEnvironmentParameter(context,
                    "javax.security.sasl.server.authentication", preservedEnvironment);
            restoreEnvironmentParameter(context, "javax.security.sasl.qop",
                    preservedEnvironment);
        }

        if (user != null) {
            return new GenericPrincipal(user.getUserName(), user.getPassword(),
                    roles, null, null, gssCredential);
        }

        return null;
    }

    private void restoreEnvironmentParameter(DirContext context,
            String parameterName, Hashtable<?, ?> preservedEnvironment) {
        try {
            context.removeFromEnvironment(parameterName);
            if (preservedEnvironment != null && preservedEnvironment.containsKey(parameterName)) {
                context.addToEnvironment(parameterName,
                        preservedEnvironment.get(parameterName));
            }
        } catch (NamingException e) {
            // Ignore
        }
    }

    /**
     * 打开并返回到该Realm的已配置的目录服务器的连接.
     * 
     * @return 目录上下文
     * @exception NamingException 如果出现目录服务器错误
     */
    protected DirContext open() throws NamingException {

        // 如果目录服务器连接已经打开
        if (context != null)
            return context;

        try {
            // 确保有一个可用的目录上下文
            context = createDirContext(getDirectoryContextEnvironment());
        } catch (Exception e) {

            connectionAttempt = 1;

            // log the first exception.
            containerLog.info(sm.getString("jndiRealm.exception.retry"), e);

            // 尝试连接到另一个URL.
            context = createDirContext(getDirectoryContextEnvironment());
        } finally {
            // 重置它以防连接超时.
            // the primary may come back.
            connectionAttempt = 0;
        }
        return context;
    }

    @Override
    public boolean isAvailable() {
        // Simple best effort check
        return (context != null);
    }

    private DirContext createDirContext(Hashtable<String, String> env) throws NamingException {
        if (useStartTls) {
            return createTlsDirContext(env);
        } else {
            return new InitialDirContext(env);
        }
    }

    private SSLSocketFactory getSSLSocketFactory() {
        if (sslSocketFactory != null) {
            return sslSocketFactory;
        }
        final SSLSocketFactory result;
        if (this.sslSocketFactoryClassName != null
                && !sslSocketFactoryClassName.trim().equals("")) {
            result = createSSLSocketFactoryFromClassName(this.sslSocketFactoryClassName);
        } else {
            result = createSSLContextFactoryFromProtocol(sslProtocol);
        }
        this.sslSocketFactory = result;
        return result;
    }

    private SSLSocketFactory createSSLSocketFactoryFromClassName(String className) {
        try {
            Object o = constructInstance(className);
            if (o instanceof SSLSocketFactory) {
                return sslSocketFactory;
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "jndiRealm.invalidSslSocketFactory",
                        className));
            }
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException(sm.getString(
                    "jndiRealm.invalidSslSocketFactory",
                    className), e);
        }
    }

    private SSLSocketFactory createSSLContextFactoryFromProtocol(String protocol) {
        try {
            SSLContext sslContext;
            if (protocol != null) {
                sslContext = SSLContext.getInstance(protocol);
                sslContext.init(null, null, null);
            } else {
                sslContext = SSLContext.getDefault();
            }
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            List<String> allowedProtocols = Arrays
                    .asList(getSupportedSslProtocols());
            throw new IllegalArgumentException(
                    sm.getString("jndiRealm.invalidSslProtocol", protocol,
                            allowedProtocols), e);
        }
    }

    /**
     * 创建一个tls, 启用 LdapContext并设置StartTlsResponse tls实例变量.
     *
     * @param env 用于上下文创建的环境
     * 
     * @return 配置的{@link LdapContext}
     * @throws NamingException 在连接过程中出错
     */
    private DirContext createTlsDirContext(Hashtable<String, String> env) throws NamingException {
        Map<String, Object> savedEnv = new HashMap<>();
        for (String key : Arrays.asList(Context.SECURITY_AUTHENTICATION,
                Context.SECURITY_CREDENTIALS, Context.SECURITY_PRINCIPAL,
                Context.SECURITY_PROTOCOL)) {
            Object entry = env.remove(key);
            if (entry != null) {
                savedEnv.put(key, entry);
            }
        }
        LdapContext result = null;
        try {
            result = new InitialLdapContext(env, null);
            tls = (StartTlsResponse) result
                    .extendedOperation(new StartTlsRequest());
            if (getHostnameVerifier() != null) {
                tls.setHostnameVerifier(getHostnameVerifier());
            }
            if (getCipherSuitesArray() != null) {
                tls.setEnabledCipherSuites(getCipherSuitesArray());
            }
            try {
                SSLSession negotiate = tls.negotiate(getSSLSocketFactory());
                containerLog.debug(sm.getString("jndiRealm.negotiatedTls",
                        negotiate.getProtocol()));
            } catch (IOException e) {
                throw new NamingException(e.getMessage());
            }
        } finally {
            if (result != null) {
                for (Map.Entry<String, Object> savedEntry : savedEnv.entrySet()) {
                    result.addToEnvironment(savedEntry.getKey(),
                            savedEntry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * 创建目录上下文配置.
     *
     * @return java.util.Hashtable 目录上下文的配置.
     */
    protected Hashtable<String,String> getDirectoryContextEnvironment() {

        Hashtable<String,String> env = new Hashtable<>();

        // 配置目录上下文环境.
        if (containerLog.isDebugEnabled() && connectionAttempt == 0)
            containerLog.debug("Connecting to URL " + connectionURL);
        else if (containerLog.isDebugEnabled() && connectionAttempt > 0)
            containerLog.debug("Connecting to URL " + alternateURL);
        env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
        if (connectionName != null)
            env.put(Context.SECURITY_PRINCIPAL, connectionName);
        if (connectionPassword != null)
            env.put(Context.SECURITY_CREDENTIALS, connectionPassword);
        if (connectionURL != null && connectionAttempt == 0)
            env.put(Context.PROVIDER_URL, connectionURL);
        else if (alternateURL != null && connectionAttempt > 0)
            env.put(Context.PROVIDER_URL, alternateURL);
        if (authentication != null)
            env.put(Context.SECURITY_AUTHENTICATION, authentication);
        if (protocol != null)
            env.put(Context.SECURITY_PROTOCOL, protocol);
        if (referrals != null)
            env.put(Context.REFERRAL, referrals);
        if (derefAliases != null)
            env.put(JNDIRealm.DEREF_ALIASES, derefAliases);
        if (connectionTimeout != null)
            env.put("com.sun.jndi.ldap.connect.timeout", connectionTimeout);
        if (readTimeout != null)
            env.put("com.sun.jndi.ldap.read.timeout", readTimeout);

        return env;
    }


    /**
     * 释放这个连接，以便它可以被回收.
     *
     * @param context 要释放的目录上下文
     */
    protected void release(DirContext context) {
        // NO-OP since we are not pooling anything
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {

        // 检查是否可以打开与目录的连接
        try {
            open();
        } catch (NamingException e) {
            // 这里的故障不是致命的，因为目录现在可能不可用，但稍后可用. 目录的不可用性不是致命的, 一旦 Realm已经启动, 因此，当Realm 开始时，没有任何原因是致命的.
            containerLog.error(sm.getString("jndiRealm.open"), e);
        }
        super.startInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
     @Override
    protected void stopInternal() throws LifecycleException {

        super.stopInternal();

        // 关闭任何打开的目录服务器连接
        close(this.context);
    }

    /**
     * 给定包含用户位置的LDAP模式的字符串 (在一个伪LDAP搜索字符串格式中用圆括号分隔开 -
     * "(location1)(location2)", 返回这些路径的数组. 真正的 LDAP 还支持搜索字符串(只支持 "|" "OR" 类型).
     *
     * @param userPatternString - 由括号包围的字符串LDAP搜索路径
     * @return a parsed string array
     */
    protected String[] parseUserPatternString(String userPatternString) {

        if (userPatternString != null) {
            ArrayList<String> pathList = new ArrayList<>();
            int startParenLoc = userPatternString.indexOf('(');
            if (startParenLoc == -1) {
                // no parens here; return whole thing
                return new String[] {userPatternString};
            }
            int startingPoint = 0;
            while (startParenLoc > -1) {
                int endParenLoc = 0;
                // 剔除括号和包含整个声明的括号(在有效的LDAP搜索字符串的情况下:
                // strings: (|(something)(somethingelse))
                while ( (userPatternString.charAt(startParenLoc + 1) == '|') ||
                        (startParenLoc != 0 && userPatternString.charAt(startParenLoc - 1) == '\\') ) {
                    startParenLoc = userPatternString.indexOf('(', startParenLoc+1);
                }
                endParenLoc = userPatternString.indexOf(')', startParenLoc+1);
                // 剔除了结束括号
                while (userPatternString.charAt(endParenLoc - 1) == '\\') {
                    endParenLoc = userPatternString.indexOf(')', endParenLoc+1);
                }
                String nextPathPart = userPatternString.substring
                    (startParenLoc+1, endParenLoc);
                pathList.add(nextPathPart);
                startingPoint = endParenLoc+1;
                startParenLoc = userPatternString.indexOf('(', startingPoint);
            }
            return pathList.toArray(new String[] {});
        }
        return null;

    }


    /**
     * 给定LDAP搜索字符串, 根据RFC 2254规则返回具有某些字符的字符串.
     * 字符映射如下所示:
     *     char ->  Replacement
     *    ---------------------------
     *     *  -> \2a
     *     (  -> \28
     *     )  -> \29
     *     \  -> \5c
     *     \0 -> \00
     * @param inString 根据RFC 2254准则要转义的字符串
     * @return String 转义/编码结果
     */
    protected String doRFC2254Encoding(String inString) {
        StringBuilder buf = new StringBuilder(inString.length());
        for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            switch (c) {
                case '\\':
                    buf.append("\\5c");
                    break;
                case '*':
                    buf.append("\\2a");
                    break;
                case '(':
                    buf.append("\\28");
                    break;
                case ')':
                    buf.append("\\29");
                    break;
                case '\0':
                    buf.append("\\00");
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }


    /**
     * 返回搜索结果的可识别的名称.
     *
     * @param context 
     * @param base The base DN
     * @param result 搜索的结果
     * 
     * @return 可识别的名称
     * @exception NamingException 如果出现目录服务器错误
     */
    protected String getDistinguishedName(DirContext context, String base,
            SearchResult result) throws NamingException {
        // 获取条目的识别名. 相关结果, 意味着需要合成一个名字, 使用基础名称, 上下文名称, 和结果名称. 对于非相对名称, 使用返回的名称.
        String resultName = result.getName();
        if (result.isRelative()) {
           if (containerLog.isTraceEnabled()) {
               containerLog.trace("  search returned relative name: " + resultName);
           }
           NameParser parser = context.getNameParser("");
           Name contextName = parser.parse(context.getNameInNamespace());
           Name baseName = parser.parse(base);

           // Bugzilla 32269
           Name entryName = parser.parse(new CompositeName(resultName).get(0));

           Name name = contextName.addAll(baseName);
           name = name.addAll(entryName);
           return name.toString();
        } else {
           if (containerLog.isTraceEnabled()) {
               containerLog.trace("  search returned absolute name: " + resultName);
           }
           try {
               // 通过在名称解析器中运行它来规范名称.
               NameParser parser = context.getNameParser("");
               URI userNameUri = new URI(resultName);
               String pathComponent = userNameUri.getPath();
               // 不应该有一个空路径, 因为那是 /{DN}
               if (pathComponent.length() < 1 ) {
                   throw new InvalidNameException(
                           "Search returned unparseable absolute name: " +
                           resultName );
               }
               Name name = parser.parse(pathComponent.substring(1));
               return name.toString();
           } catch ( URISyntaxException e ) {
               throw new InvalidNameException(
                       "Search returned unparseable absolute name: " +
                       resultName );
           }
        }
    }


    // ------------------------------------------------------ Private Classes

    /**
     * 表示一个User
     */
    protected static class User {

        private final String username;
        private final String dn;
        private final String password;
        private final List<String> roles;
        private final String userRoleId;


        public User(String username, String dn, String password,
                List<String> roles, String userRoleId) {
            this.username = username;
            this.dn = dn;
            this.password = password;
            if (roles == null) {
                this.roles = Collections.emptyList();
            } else {
                this.roles = Collections.unmodifiableList(roles);
            }
            this.userRoleId = userRoleId;
        }

        public String getUserName() {
            return username;
        }

        public String getDN() {
            return dn;
        }

        public String getPassword() {
            return password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public String getUserRoleId() {
            return userRoleId;
        }
    }
}

