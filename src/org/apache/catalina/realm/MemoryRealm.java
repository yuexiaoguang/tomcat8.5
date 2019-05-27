package org.apache.catalina.realm;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.file.ConfigFileLoader;


/**
 * <b>Realm</b>实现类， 读取XML文件以配置有效用户、密码和角色.
 * 文件格式（和默认文件位置）与当前由Tomcat 3支持的文件格式相同.
 * <p>
 * <strong>实现注意</strong>: 假设在应用程序启动时初始化定义的用户（及其角色）的内存集合，再也不会修改.
 * 因此，在访问主体集合时不执行线程同步.
 */
public class MemoryRealm  extends RealmBase {

    private static final Log log = LogFactory.getLog(MemoryRealm.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 用于处理内存数据库文件的Digester.
     */
    private static Digester digester = null;


    /**
     * 描述信息.
     * @deprecated This will be removed in Tomcat 9 onwards.
     */
    @Deprecated
    protected static final String name = "MemoryRealm";


    /**
     * 包含数据库信息的XML文件的路径(绝对路径，或者相对于Catalina的当前工作路径)
     */
    private String pathname = "conf/tomcat-users.xml";


    /**
     * 有效Principals集合, 使用用户名作为key.
     */
    private final Map<String,GenericPrincipal> principals = new HashMap<>();


    // ------------------------------------------------------------- Properties

    /**
     * @return 包含用户定义的XML文件的路径名.
     */
    public String getPathname() {
        return pathname;
    }


    /**
     * 设置包含用户定义的XML文件的路径名.
     * 如果指定相对路径, 将违反"catalina.base".
     *
     * @param pathname The new pathname
     */
    public void setPathname(String pathname) {
        this.pathname = pathname;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回指定用户名和凭据的Principal; 或者<code>null</code>.
     *
     * @param username 要查找Principal的用户名
     * @param credentials 验证这个用户名的Password或其它凭据
     * 
     * @return 关联的主体, 或<code>null</code>.
     */
    @Override
    public Principal authenticate(String username, String credentials) {

        // No user or no credentials
        // 不能验证, 不要打扰数据库
        if (username == null || credentials == null) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("memoryRealm.authenticateFailure", username));
            return null;
        }

        GenericPrincipal principal = principals.get(username);

        if(principal == null || principal.getPassword() == null) {
            // 数据库中未找到User或密码是 null
            // 浪费一点时间，不要透露用户不存在.
            getCredentialHandler().mutate(credentials);

            if (log.isDebugEnabled())
                log.debug(sm.getString("memoryRealm.authenticateFailure", username));
            return null;
        }

        boolean validated = getCredentialHandler().matches(credentials, principal.getPassword());

        if (validated) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("memoryRealm.authenticateSuccess", username));
            return principal;
        } else {
            if (log.isDebugEnabled())
                log.debug(sm.getString("memoryRealm.authenticateFailure", username));
            return null;
        }
    }


    // -------------------------------------------------------- Package Methods


    /**
     * 在内存数据库中添加一个新用户.
     *
     * @param username User's username
     * @param password User's password (clear text)
     * @param roles 与此用户关联的逗号分隔的角色集
     */
    void addUser(String username, String password, String roles) {

        // 为这个用户累加角色列表
        ArrayList<String> list = new ArrayList<>();
        roles += ",";
        while (true) {
            int comma = roles.indexOf(',');
            if (comma < 0)
                break;
            String role = roles.substring(0, comma).trim();
            list.add(role);
            roles = roles.substring(comma + 1);
        }

        // 构造并缓存这个用户的Principal
        GenericPrincipal principal =
            new GenericPrincipal(username, password, list);
        principals.put(username, principal);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * @return 一个用于处理XML输入文件的配置的<code>Digester</code>, 如果必要的话，创建一个新的.
     */
    protected synchronized Digester getDigester() {

        if (digester == null) {
            digester = new Digester();
            digester.setValidating(false);
            try {
                digester.setFeature(
                        "http://apache.org/xml/features/allow-java-encodings",
                        true);
            } catch (Exception e) {
                log.warn(sm.getString("memoryRealm.xmlFeatureEncoding"), e);
            }
            digester.addRuleSet(new MemoryRuleSet());
        }
        return (digester);

    }


    @Override
    @Deprecated
    protected String getName() {
        return name;
    }


    /**
     * @return 指定用户名关联的密码.
     */
    @Override
    protected String getPassword(String username) {

        GenericPrincipal principal = principals.get(username);
        if (principal != null) {
            return (principal.getPassword());
        } else {
            return (null);
        }
    }


    /**
     * @return 指定用户名关联的Principal.
     */
    @Override
    protected Principal getPrincipal(String username) {
        return principals.get(username);
    }


    // ------------------------------------------------------ Lifecycle Methods

    /**
     * @exception LifecycleException 如果此组件检测到阻止其启动的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {
        String pathName = getPathname();
        try (InputStream is = ConfigFileLoader.getInputStream(pathName)) {
            // 加载数据库文件的内容
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("memoryRealm.loadPath", pathName));
            }

            Digester digester = getDigester();
            try {
                synchronized (digester) {
                    digester.push(this);
                    digester.parse(is);
                }
            } catch (Exception e) {
                throw new LifecycleException(sm.getString("memoryRealm.readXml"), e);
            } finally {
                digester.reset();
            }
        } catch (IOException ioe) {
            throw new LifecycleException(sm.getString("memoryRealm.loadExist", pathName), ioe);
        }

        super.startInternal();
    }
}
