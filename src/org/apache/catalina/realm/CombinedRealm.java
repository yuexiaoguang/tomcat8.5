package org.apache.catalina.realm;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;

/**
 * Realm实现类包含一个或多个 realm.
 * 对每个realm 按其配置顺序进行身份验证. 如果任何realm 认证用户，则认证成功. 当合并realm时，用户名在所有组合realm都应该是唯一的.
 */
public class CombinedRealm extends RealmBase {

    private static final Log log = LogFactory.getLog(CombinedRealm.class);

    /**
     * 这个Realm包含的Realm.
     */
    protected final List<Realm> realms = new LinkedList<>();

    /**
     * 这个Realm 实现的描述信息.
     * 
     * @deprecated This will be removed in Tomcat 9 onwards.
     */
    @Deprecated
    protected static final String name = "CombinedRealm";

    /**
     * 添加一个 realm，将用于验证用户.
     * 
     * @param theRealm 应该被组合Realm包装的Realm
     */
    public void addRealm(Realm theRealm) {
        realms.add(theRealm);

        if (log.isDebugEnabled()) {
            sm.getString("combinedRealm.addRealm",
                    theRealm.getClass().getName(),
                    Integer.toString(realms.size()));
        }
    }


    /**
     * @return 这个Realm包装的Realm集合
     */
    public ObjectName[] getRealms() {
        ObjectName[] result = new ObjectName[realms.size()];
        for (Realm realm : realms) {
            if (realm instanceof RealmBase) {
                result[realms.indexOf(realm)] =
                    ((RealmBase) realm).getObjectName();
            }
        }
        return result;
    }

    /**
     * @return 这个Realm包含的Realm集合
     */
    public Realm[] getNestedRealms() {
        return realms.toArray(new Realm[0]);
    }

    /**
     * 返回指定用户名关联的 Principal, 与使用给定参数计算的摘要匹配使用RFC 2069中描述的方法; 否则返回<code>null</code>.
     *
     * @param username 要查找的Principal的用户名
     * @param clientDigest 客户端提交的摘要
     * @param nonce 已被用于此请求的唯一的 token
     * @param realmName Realm名称
     * @param md5a2 用于计算摘要的第二个 MD5摘要 : MD5(Method + ":" + uri)
     */
    @Override
    public Principal authenticate(String username, String clientDigest,
            String nonce, String nc, String cnonce, String qop,
            String realmName, String md5a2) {
        Principal authenticatedUser = null;

        for (Realm realm : realms) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("combinedRealm.authStart", username,
                        realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(username, clientDigest, nonce,
                    nc, cnonce, qop, realmName, md5a2);

            if (authenticatedUser == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authFail", username,
                            realm.getClass().getName()));
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authSuccess",
                            username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    /**
     * 返回指定用户名关联的 Principal; 否则返回<code>null</code>.
     *
     * @param username 要查找的Principal的用户名
     */
    @Override
    public Principal authenticate(String username) {
        Principal authenticatedUser = null;

        for (Realm realm : realms) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("combinedRealm.authStart", username,
                        realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(username);

            if (authenticatedUser == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authFail", username,
                            realm.getClass().getName()));
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authSuccess",
                            username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    /**
     * 返回指定用户名和凭据关联的 Principal; 否则返回<code>null</code>.
     *
     * @param username 要查找的Principal的用户名
     * @param credentials 在验证用户名时使用的密码或其他凭据
     */
    @Override
    public Principal authenticate(String username, String credentials) {
        Principal authenticatedUser = null;

        for (Realm realm : realms) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("combinedRealm.authStart", username,
                        realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(username, credentials);

            if (authenticatedUser == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authFail", username,
                            realm.getClass().getName()));
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authSuccess",
                            username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }


    /**
     * 设置这个Realm关联的Container.
     *
     * @param container 关联的Container
     */
    @Override
    public void setContainer(Container container) {
        for(Realm realm : realms) {
            // 为JMX命名设置realmPath
            if (realm instanceof RealmBase) {
                ((RealmBase) realm).setRealmPath(
                        getRealmPath() + "/realm" + realms.indexOf(realm));
            }

            // 为子领域设置容器. 主要是记录工作.
            realm.setContainer(container);
        }
        super.setContainer(container);
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    @Override
    protected void startInternal() throws LifecycleException {
        // Start 'sub-realms' then this one
        Iterator<Realm> iter = realms.iterator();

        while (iter.hasNext()) {
            Realm realm = iter.next();
            if (realm instanceof Lifecycle) {
                try {
                    ((Lifecycle) realm).start();
                } catch (LifecycleException e) {
                    // 如果realm没有启动，就无法验证它
                    iter.remove();
                    log.error(sm.getString("combinedRealm.realmStartFail",
                            realm.getClass().getName()), e);
                }
            }
        }
        super.startInternal();
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
     @Override
    protected void stopInternal() throws LifecycleException {
        // 停止这个realm, 然后子realm (反转顺序启动)
        super.stopInternal();
        for (Realm realm : realms) {
            if (realm instanceof Lifecycle) {
                ((Lifecycle) realm).stop();
            }
        }
    }


    /**
     * 当这个Realm被销毁时，确保销毁子级 Realm.
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        for (Realm realm : realms) {
            if (realm instanceof Lifecycle) {
                ((Lifecycle) realm).destroy();
            }
        }
        super.destroyInternal();
    }

    /**
     * 将后台进程调用委托给所有子领域.
     */
    @Override
    public void backgroundProcess() {
        super.backgroundProcess();

        for (Realm r : realms) {
            r.backgroundProcess();
        }
    }

    /**
     * 返回与X509客户端证书的指定链关联的Principal. 如果没有, 返回<code>null</code>.
     *
     * @param certs 客户端证书数组, 数组中的第一个是客户端本身的证书.
     */
    @Override
    public Principal authenticate(X509Certificate[] certs) {
        Principal authenticatedUser = null;
        String username = null;
        if (certs != null && certs.length >0) {
            username = certs[0].getSubjectDN().getName();
        }

        for (Realm realm : realms) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("combinedRealm.authStart", username,
                        realm.getClass().getName()));
            }

            authenticatedUser = realm.authenticate(certs);

            if (authenticatedUser == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authFail", username,
                            realm.getClass().getName()));
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authSuccess",
                            username, realm.getClass().getName()));
                }
                break;
            }
        }
        return authenticatedUser;
    }

    @Override
    public Principal authenticate(GSSContext gssContext, boolean storeCreds) {
        if (gssContext.isEstablished()) {
            Principal authenticatedUser = null;
            String username = null;

            GSSName name = null;
            try {
                name = gssContext.getSrcName();
            } catch (GSSException e) {
                log.warn(sm.getString("realmBase.gssNameFail"), e);
                return null;
            }

            username = name.toString();

            for (Realm realm : realms) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("combinedRealm.authStart",
                            username, realm.getClass().getName()));
                }

                authenticatedUser = realm.authenticate(gssContext, storeCreds);

                if (authenticatedUser == null) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("combinedRealm.authFail",
                                username, realm.getClass().getName()));
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("combinedRealm.authSuccess",
                                username, realm.getClass().getName()));
                    }
                    break;
                }
            }
            return authenticatedUser;
        }

        // 在其他情况下失败
        return null;
    }

    @Override
    @Deprecated
    protected String getName() {
        return name;
    }

    @Override
    protected String getPassword(String username) {
        // 这个方法永远不应该被调用
        // 堆栈跟踪将显示该调用的位置
        UnsupportedOperationException uoe =
            new UnsupportedOperationException(
                    sm.getString("combinedRealm.getPassword"));
        log.error(sm.getString("combinedRealm.unexpectedMethod"), uoe);
        throw uoe;
    }

    @Override
    protected Principal getPrincipal(String username) {
    	// 这个方法永远不应该被调用
        // 堆栈跟踪将显示该调用的位置
        UnsupportedOperationException uoe =
            new UnsupportedOperationException(
                    sm.getString("combinedRealm.getPrincipal"));
        log.error(sm.getString("combinedRealm.unexpectedMethod"), uoe);
        throw uoe;
    }


    @Override
    public boolean isAvailable() {
        for (Realm realm : realms) {
            if (!realm.isAvailable()) {
                return false;
            }
        }
        return true;
    }
}
