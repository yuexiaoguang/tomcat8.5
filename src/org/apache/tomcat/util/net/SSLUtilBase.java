package org.apache.tomcat.util.net;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.res.StringManager;

/**
 * {@link SSLUtil}实现的公共基类.
 */
public abstract class SSLUtilBase implements SSLUtil {

    private static final Log log = LogFactory.getLog(SSLUtilBase.class);
    private static final StringManager sm = StringManager.getManager(SSLUtilBase.class);

    protected final SSLHostConfigCertificate certificate;

    private final String[] enabledProtocols;
    private final String[] enabledCiphers;


    protected SSLUtilBase(SSLHostConfigCertificate certificate) {
        this.certificate = certificate;
        SSLHostConfig sslHostConfig = certificate.getSSLHostConfig();

        // 计算启用的协议
        Set<String> configuredProtocols = sslHostConfig.getProtocols();
        Set<String> implementedProtocols = getImplementedProtocols();
        List<String> enabledProtocols =
                getEnabled("protocols", getLog(), true, configuredProtocols, implementedProtocols);
        if (enabledProtocols.contains("SSLv3")) {
            log.warn(sm.getString("jsse.ssl3"));
        }
        this.enabledProtocols = enabledProtocols.toArray(new String[enabledProtocols.size()]);

        // Calculate the enabled ciphers
        List<String> configuredCiphers = sslHostConfig.getJsseCipherNames();
        Set<String> implementedCiphers = getImplementedCiphers();
        List<String> enabledCiphers =
                getEnabled("ciphers", getLog(), false, configuredCiphers, implementedCiphers);
        this.enabledCiphers = enabledCiphers.toArray(new String[enabledCiphers.size()]);
    }


    static <T> List<T> getEnabled(String name, Log log, boolean warnOnSkip, Collection<T> configured,
            Collection<T> implemented) {

        List<T> enabled = new ArrayList<>();

        if (implemented.size() == 0) {
            // 无法确定可用协议列表. 这将在之前记录.
            // 使用配置的协议, 希望它们有效. 如果无效, 使用列表时将生成错误. 不理想, 但此时不能再做了.
            enabled.addAll(configured);
        } else {
            enabled.addAll(configured);
            enabled.retainAll(implemented);

            if (enabled.isEmpty()) {
                // 在这种情况下，请勿使用默认值. 它们可能不如用户期望的配置安全.
                // 强制连接器发生故障
                throw new IllegalArgumentException(
                        sm.getString("sslUtilBase.noneSupported", name, configured));
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("sslUtilBase.active", name, enabled));
            }
            if (log.isDebugEnabled() || warnOnSkip) {
                if (enabled.size() != configured.size()) {
                    List<T> skipped = new ArrayList<>();
                    skipped.addAll(configured);
                    skipped.removeAll(enabled);
                    String msg = sm.getString("sslUtilBase.skipped", name, skipped);
                    if (warnOnSkip) {
                        log.warn(msg);
                    } else {
                        log.debug(msg);
                    }
                }
            }
        }
        return enabled;
    }


    /*
     * 获取密钥 - 或具有指定类型，路径和密码的信任库.
     */
    static KeyStore getStore(String type, String provider, String path,
            String pass) throws IOException {

        KeyStore ks = null;
        InputStream istream = null;
        try {
            if (provider == null) {
                ks = KeyStore.getInstance(type);
            } else {
                ks = KeyStore.getInstance(type, provider);
            }
            if(!("PKCS11".equalsIgnoreCase(type) ||
                    "".equalsIgnoreCase(path)) ||
                    "NONE".equalsIgnoreCase(path)) {
                istream = ConfigFileLoader.getInputStream(path);
            }

            char[] storePass = null;
            if (pass != null && !"".equals(pass)) {
                storePass = pass.toCharArray();
            }
            ks.load(istream, storePass);
        } catch (FileNotFoundException fnfe) {
            log.error(sm.getString("jsse.keystore_load_failed", type, path,
                    fnfe.getMessage()), fnfe);
            throw fnfe;
        } catch (IOException ioe) {
            // May be expected when working with a trust store Re-throw. Caller will catch and log as required
            throw ioe;
        } catch(Exception ex) {
            String msg = sm.getString("jsse.keystore_load_failed", type, path,
                    ex.getMessage());
            log.error(msg, ex);
            throw new IOException(msg);
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (IOException ioe) {
                    // Do nothing
                }
            }
        }

        return ks;
    }


    @Override
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    @Override
    public String[] getEnabledCiphers() {
        return enabledCiphers;
    }

    protected abstract Set<String> getImplementedProtocols();
    protected abstract Set<String> getImplementedCiphers();
    protected abstract Log getLog();
}
