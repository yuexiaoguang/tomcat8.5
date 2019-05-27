package org.apache.tomcat.util.net;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.openssl.OpenSSLConf;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

/**
 * 表示虚拟主机的TLS配置.
 */
public class SSLHostConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(SSLHostConfig.class);
    private static final StringManager sm = StringManager.getManager(SSLHostConfig.class);

    private static final String DEFAULT_CIPHERS = "HIGH:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!kRSA";

    protected static final String DEFAULT_SSL_HOST_NAME = "_default_";
    protected static final Set<String> SSL_PROTO_ALL_SET = new HashSet<>();

    static {
        /* 如果未配置协议或protocols="All", 则使用默认值
         */
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_SSLv2Hello);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_1);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_2);
    }

    private Type configType = null;
    private Type currentConfigType = null;
    private Map<Type,Set<String>> configuredProperties = new HashMap<>();

    private String hostName = DEFAULT_SSL_HOST_NAME;

    private transient Long openSslConfContext = Long.valueOf(0);
    // OpenSSL可以在单个配置中处理多个证书，因此上下文的引用位于虚拟主机级别.
    // JSSE不能在证书上保留引用.
    private transient Long openSslContext = Long.valueOf(0);

    // Configuration properties

    // Internal
    private String[] enabledCiphers;
    private String[] enabledProtocols;
    private ObjectName oname;
    // Nested
    private SSLHostConfigCertificate defaultCertificate = null;
    private Set<SSLHostConfigCertificate> certificates = new HashSet<>(4);
    // Common
    private String certificateRevocationListFile;
    private CertificateVerification certificateVerification = CertificateVerification.NONE;
    private int certificateVerificationDepth = 10;
    // 用于跟踪是否已明确设置certificateVerificationDepth
    private boolean certificateVerificationDepthConfigured = false;
    private String ciphers;
    private LinkedHashSet<Cipher> cipherList = null;
    private List<String> jsseCipherNames = null;
    private String honorCipherOrder = null;
    private Set<String> protocols = new HashSet<>();
    // JSSE
    private String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
    private boolean revocationEnabled = false;
    private int sessionCacheSize = 0;
    private int sessionTimeout = 86400;
    private String sslProtocol = Constants.SSL_PROTO_TLS;
    private String trustManagerClassName;
    private String truststoreAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
    private String truststoreFile = System.getProperty("javax.net.ssl.trustStore");
    private String truststorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
    private String truststoreProvider = System.getProperty("javax.net.ssl.trustStoreProvider");
    private String truststoreType = System.getProperty("javax.net.ssl.trustStoreType");
    private transient KeyStore truststore = null;
    // OpenSSL
    private String certificateRevocationListPath;
    private String caCertificateFile;
    private String caCertificatePath;
    private boolean disableCompression = true;
    private boolean disableSessionTickets = false;
    private boolean insecureRenegotiation = false;
    private OpenSSLConf openSslConf = null;

    public SSLHostConfig() {
        // 设置定义字段时无法（轻松）设置的默认值.
        setProtocols(Constants.SSL_PROTO_ALL);
    }


    public Long getOpenSslConfContext() {
        return openSslConfContext;
    }


    public void setOpenSslConfContext(Long openSslConfContext) {
        this.openSslConfContext = openSslConfContext;
    }


    public Long getOpenSslContext() {
        return openSslContext;
    }


    public void setOpenSslContext(Long openSslContext) {
        this.openSslContext = openSslContext;
    }


    // 以字符串形式显示JMX
    public String getConfigType() {
        return configType.name();
    }
    public void setConfigType(Type configType) {
        this.configType = configType;
        if (configType == Type.EITHER) {
            if (configuredProperties.remove(Type.JSSE) == null) {
                configuredProperties.remove(Type.OPENSSL);
            }
        } else {
            configuredProperties.remove(configType);
        }
        for (Map.Entry<Type,Set<String>> entry : configuredProperties.entrySet()) {
            for (String property : entry.getValue()) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        property, getHostName(), entry.getKey(), configType));
            }
        }
    }


    void setProperty(String name, Type configType) {
        if (this.configType == null) {
            Set<String> properties = configuredProperties.get(configType);
            if (properties == null) {
                properties = new HashSet<>();
                configuredProperties.put(configType, properties);
            }
            properties.add(name);
        } else if (this.configType == Type.EITHER) {
            if (currentConfigType == null) {
                currentConfigType = configType;
            } else if (currentConfigType != configType) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        name, getHostName(), configType, currentConfigType));
            }
        } else {
            if (configType != this.configType) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        name, getHostName(), configType, this.configType));
            }
        }
    }


    // ----------------------------------------------------- Internal properties

    /**
     * @return 为此TLS虚拟主机启用的协议
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }


    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }


    /**
     * @return 为此TLS虚拟主机启用的密码
     */
    public String[] getEnabledCiphers() {
        return enabledCiphers;
    }


    public void setEnabledCiphers(String[] enabledCiphers) {
        this.enabledCiphers = enabledCiphers;
    }


    public ObjectName getObjectName() {
        return oname;
    }


    public void setObjectName(ObjectName oname) {
        this.oname = oname;
    }


    // ------------------------------------------- Nested configuration elements

    private void registerDefaultCertificate() {
        if (defaultCertificate == null) {
            defaultCertificate = new SSLHostConfigCertificate(
                    this, SSLHostConfigCertificate.Type.UNDEFINED);
            certificates.add(defaultCertificate);
        }
    }


    public void addCertificate(SSLHostConfigCertificate certificate) {
        // 需要确保如果有多个证书，则它们都没有未定义的类型.
        if (certificates.size() == 0) {
            certificates.add(certificate);
            return;
        }

        if (certificates.size() == 1 &&
                certificates.iterator().next().getType() == SSLHostConfigCertificate.Type.UNDEFINED ||
                        certificate.getType() == SSLHostConfigCertificate.Type.UNDEFINED) {
            // 配置无效
            throw new IllegalArgumentException(sm.getString("sslHostConfig.certificate.notype"));
        }

        certificates.add(certificate);
    }


    public OpenSSLConf getOpenSslConf() {
        return openSslConf;
    }


    public void setOpenSslConf(OpenSSLConf conf) {
        if (conf == null) {
            throw new IllegalArgumentException(sm.getString("sslHostConfig.opensslconf.null"));
        } else if (openSslConf != null) {
            throw new IllegalArgumentException(sm.getString("sslHostConfig.opensslconf.alreadySet"));
        }
        setProperty("<OpenSSLConf>", Type.OPENSSL);
        openSslConf = conf;
    }


    public Set<SSLHostConfigCertificate> getCertificates() {
        return getCertificates(false);
    }


    public Set<SSLHostConfigCertificate> getCertificates(boolean createDefaultIfEmpty) {
        if (certificates.size() == 0 && createDefaultIfEmpty) {
            registerDefaultCertificate();
        }
        return certificates;
    }


    // ----------------------------------------- Common configuration properties

    // TODO: 一旦不再需要支持旧配置属性，就可以删除此证书setter (Tomcat 10?).

    public String getCertificateKeyPassword() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateKeyPassword();
    }
    public void setCertificateKeyPassword(String certificateKeyPassword) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeyPassword(certificateKeyPassword);
    }


    public void setCertificateRevocationListFile(String certificateRevocationListFile) {
        this.certificateRevocationListFile = certificateRevocationListFile;
    }


    public String getCertificateRevocationListFile() {
        return certificateRevocationListFile;
    }


    public void setCertificateVerification(String certificateVerification) {
        try {
            this.certificateVerification =
                    CertificateVerification.fromString(certificateVerification);
        } catch (IllegalArgumentException iae) {
            // 如果未识别指定的值, 默认为最严格的选项.
            this.certificateVerification = CertificateVerification.REQUIRED;
            throw iae;
        }
    }


    public CertificateVerification getCertificateVerification() {
        return certificateVerification;
    }


    public void setCertificateVerificationDepth(int certificateVerificationDepth) {
        this.certificateVerificationDepth = certificateVerificationDepth;
        certificateVerificationDepthConfigured = true;
    }


    public int getCertificateVerificationDepth() {
        return certificateVerificationDepth;
    }


    public boolean isCertificateVerificationDepthConfigured() {
        return certificateVerificationDepthConfigured;
    }


    /**
     * 设置新的密码配置.
     * Note: 无论用于设置配置的格式如何, 它始终以OpenSSL格式存储.
     *
     * @param ciphersList OpenSSL或JSSE格式的新密码配置
     */
    public void setCiphers(String ciphersList) {
        // 密码以OpenSSL格式存储. 如有必要，转换提供的值.
        if (ciphersList != null && !ciphersList.contains(":")) {
            StringBuilder sb = new StringBuilder();
            // 在OpenSSL格式中并不明显. 可以是单个OpenSSL或JSSE密码名称. 可以是以逗号分隔的密码名称列表
            String ciphers[] = ciphersList.split(",");
            for (String cipher : ciphers) {
                String trimmed = cipher.trim();
                if (trimmed.length() > 0) {
                    String openSSLName = OpenSSLCipherConfigurationParser.jsseToOpenSSL(trimmed);
                    if (openSSLName == null) {
                        // 不是JSSE名称. 也许是一个OpenSSL名称或别名
                        openSSLName = trimmed;
                    }
                    if (sb.length() > 0) {
                        sb.append(':');
                    }
                    sb.append(openSSLName);
                }
            }
            this.ciphers = sb.toString();
        } else {
            this.ciphers = ciphersList;
        }
        this.cipherList = null;
        this.jsseCipherNames = null;
    }


    /**
     * @return 用于当前配置的OpenSSL密码字符串.
     */
    public String getCiphers() {
        if (ciphers == null) {
            if (!JreCompat.isJre8Available() && Type.JSSE.equals(configType)) {
                ciphers = DEFAULT_CIPHERS + ":!DHE";
            } else {
                ciphers = DEFAULT_CIPHERS;
            }

        }
        return ciphers;
    }


    public LinkedHashSet<Cipher> getCipherList() {
        if (cipherList == null) {
            cipherList = OpenSSLCipherConfigurationParser.parse(getCiphers());
        }
        return cipherList;
    }


    /**
     * 获取当前配置的JSSE密码名称列表.
     * 配置中包含但不受JSSE支持的密码将从此列表中排除.
     *
     * @return JSSE密码名称列表
     */
    public List<String> getJsseCipherNames() {
        if (jsseCipherNames == null) {
            jsseCipherNames = OpenSSLCipherConfigurationParser.convertForJSSE(getCipherList());
        }
        return jsseCipherNames;
    }


    public void setHonorCipherOrder(String honorCipherOrder) {
        this.honorCipherOrder = honorCipherOrder;
    }


    public String getHonorCipherOrder() {
        return honorCipherOrder;
    }


    public void setHostName(String hostName) {
        this.hostName = hostName;
    }


    public String getHostName() {
        return hostName;
    }


    public void setProtocols(String input) {
        protocols.clear();

        // 协议名称列表, 由 ",", "+", "-"分隔.
        // 语法上是从左到右添加 ("+") 或删除 ("-"), 从空协议集开始.
        // token是单独的协议名称, 或 "all"对于默认的受支持协议集.
        // 分隔符 "," 仅保留兼容性并具有与 "+"相同的语义, 除了它警告可能缺少 "+" 或 "-".

        // 使用正向前瞻分割将分隔符保留在捕获中，以便我们可以检查它是哪种情况.
        for (String value: input.split("(?=[-+,])")) {
            String trimmed = value.trim();
            // 忽略仅包含前缀字符的token
            if (trimmed.length() > 1) {
                if (trimmed.charAt(0) == '+') {
                    trimmed = trimmed.substring(1).trim();
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.addAll(SSL_PROTO_ALL_SET);
                    } else {
                        protocols.add(trimmed);
                    }
                } else if (trimmed.charAt(0) == '-') {
                    trimmed = trimmed.substring(1).trim();
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.removeAll(SSL_PROTO_ALL_SET);
                    } else {
                        protocols.remove(trimmed);
                    }
                } else {
                    if (trimmed.charAt(0) == ',') {
                        trimmed = trimmed.substring(1).trim();
                    }
                    if (!protocols.isEmpty()) {
                        log.warn(sm.getString("sslHostConfig.prefix_missing",
                                 trimmed, getHostName()));
                    }
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.addAll(SSL_PROTO_ALL_SET);
                    } else {
                        protocols.add(trimmed);
                    }
                }
            }
        }
    }


    public Set<String> getProtocols() {
        return protocols;
    }


    // ---------------------------------- JSSE specific configuration properties

    // TODO: 一旦不再需要支持旧配置属性，就可以删除这些证书setter (Tomcat 10?).

    public String getCertificateKeyAlias() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateKeyAlias();
    }
    public void setCertificateKeyAlias(String certificateKeyAlias) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeyAlias(certificateKeyAlias);
    }


    public String getCertificateKeystoreFile() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateKeystoreFile();
    }
    public void setCertificateKeystoreFile(String certificateKeystoreFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystoreFile(certificateKeystoreFile);
    }


    public String getCertificateKeystorePassword() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateKeystorePassword();
    }
    public void setCertificateKeystorePassword(String certificateKeystorePassword) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystorePassword(certificateKeystorePassword);
    }


    public String getCertificateKeystoreProvider() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateKeystoreProvider();
    }
    public void setCertificateKeystoreProvider(String certificateKeystoreProvider) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystoreProvider(certificateKeystoreProvider);
    }


    public String getCertificateKeystoreType() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateKeystoreType();
    }
    public void setCertificateKeystoreType(String certificateKeystoreType) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystoreType(certificateKeystoreType);
    }


    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        setProperty("keyManagerAlgorithm", Type.JSSE);
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }


    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }


    public void setRevocationEnabled(boolean revocationEnabled) {
        setProperty("revocationEnabled", Type.JSSE);
        this.revocationEnabled = revocationEnabled;
    }


    public boolean getRevocationEnabled() {
        return revocationEnabled;
    }


    public void setSessionCacheSize(int sessionCacheSize) {
        setProperty("sessionCacheSize", Type.JSSE);
        this.sessionCacheSize = sessionCacheSize;
    }


    public int getSessionCacheSize() {
        return sessionCacheSize;
    }


    public void setSessionTimeout(int sessionTimeout) {
        setProperty("sessionTimeout", Type.JSSE);
        this.sessionTimeout = sessionTimeout;
    }


    public int getSessionTimeout() {
        return sessionTimeout;
    }


    public void setSslProtocol(String sslProtocol) {
        setProperty("sslProtocol", Type.JSSE);
        this.sslProtocol = sslProtocol;
    }


    public String getSslProtocol() {
        return sslProtocol;
    }


    public void setTrustManagerClassName(String trustManagerClassName) {
        setProperty("trustManagerClassName", Type.JSSE);
        this.trustManagerClassName = trustManagerClassName;
    }


    public String getTrustManagerClassName() {
        return trustManagerClassName;
    }


    public void setTruststoreAlgorithm(String truststoreAlgorithm) {
        setProperty("truststoreAlgorithm", Type.JSSE);
        this.truststoreAlgorithm = truststoreAlgorithm;
    }


    public String getTruststoreAlgorithm() {
        return truststoreAlgorithm;
    }


    public void setTruststoreFile(String truststoreFile) {
        setProperty("truststoreFile", Type.JSSE);
        this.truststoreFile = truststoreFile;
    }


    public String getTruststoreFile() {
        return truststoreFile;
    }


    public void setTruststorePassword(String truststorePassword) {
        setProperty("truststorePassword", Type.JSSE);
        this.truststorePassword = truststorePassword;
    }


    public String getTruststorePassword() {
        return truststorePassword;
    }


    public void setTruststoreProvider(String truststoreProvider) {
        setProperty("truststoreProvider", Type.JSSE);
        this.truststoreProvider = truststoreProvider;
    }


    public String getTruststoreProvider() {
        if (truststoreProvider == null) {
            Set<SSLHostConfigCertificate> certificates = getCertificates();
            if (certificates.size() == 1) {
                return certificates.iterator().next().getCertificateKeystoreProvider();
            }
            return SSLHostConfigCertificate.DEFAULT_KEYSTORE_PROVIDER;
        } else {
            return truststoreProvider;
        }
    }


    public void setTruststoreType(String truststoreType) {
        setProperty("truststoreType", Type.JSSE);
        this.truststoreType = truststoreType;
    }


    public String getTruststoreType() {
        if (truststoreType == null) {
            Set<SSLHostConfigCertificate> certificates = getCertificates();
            if (certificates.size() == 1) {
                String keystoreType = certificates.iterator().next().getCertificateKeystoreType();
                // 如果已知它不会被用作信任存储类型，请不要将密钥库类型用作默认值
                if (!"PKCS12".equalsIgnoreCase(keystoreType)) {
                    return keystoreType;
                }
            }
            return SSLHostConfigCertificate.DEFAULT_KEYSTORE_TYPE;
        } else {
            return truststoreType;
        }
    }


    public void setTrustStore(KeyStore truststore) {
        this.truststore = truststore;
    }


    public KeyStore getTruststore() throws IOException {
        KeyStore result = truststore;
        if (result == null) {
            if (truststoreFile != null){
                try {
                    result = SSLUtilBase.getStore(getTruststoreType(), getTruststoreProvider(),
                            getTruststoreFile(), getTruststorePassword());
                } catch (IOException ioe) {
                    Throwable cause = ioe.getCause();
                    if (cause instanceof UnrecoverableKeyException) {
                        // 遇到密码问题
                        log.warn(sm.getString("jsse.invalid_truststore_password"),
                                cause);
                        // Re-try
                        result = SSLUtilBase.getStore(getTruststoreType(), getTruststoreProvider(),
                                getTruststoreFile(), null);
                    } else {
                        // Something else went wrong - re-throw
                        throw ioe;
                    }
                }
            }
        }
        return result;
    }


    // ------------------------------- OpenSSL specific configuration properties

    // TODO: 一旦不再需要支持旧配置属性，就可以删除这些证书setter (Tomcat 10?).

    public String getCertificateChainFile() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateChainFile();
    }
    public void setCertificateChainFile(String certificateChainFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateChainFile(certificateChainFile);
    }


    public String getCertificateFile() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateFile();
    }
    public void setCertificateFile(String certificateFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateFile(certificateFile);
    }


    public String getCertificateKeyFile() {
        registerDefaultCertificate();
        return defaultCertificate.getCertificateKeyFile();
    }
    public void setCertificateKeyFile(String certificateKeyFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeyFile(certificateKeyFile);
    }


    public void setCertificateRevocationListPath(String certificateRevocationListPath) {
        setProperty("certificateRevocationListPath", Type.OPENSSL);
        this.certificateRevocationListPath = certificateRevocationListPath;
    }


    public String getCertificateRevocationListPath() {
        return certificateRevocationListPath;
    }


    public void setCaCertificateFile(String caCertificateFile) {
        setProperty("caCertificateFile", Type.OPENSSL);
        this.caCertificateFile = caCertificateFile;
    }


    public String getCaCertificateFile() {
        return caCertificateFile;
    }


    public void setCaCertificatePath(String caCertificatePath) {
        setProperty("caCertificatePath", Type.OPENSSL);
        this.caCertificatePath = caCertificatePath;
    }


    public String getCaCertificatePath() {
        return caCertificatePath;
    }


    public void setDisableCompression(boolean disableCompression) {
        setProperty("disableCompression", Type.OPENSSL);
        this.disableCompression = disableCompression;
    }


    public boolean getDisableCompression() {
        return disableCompression;
    }


    public void setDisableSessionTickets(boolean disableSessionTickets) {
        setProperty("disableSessionTickets", Type.OPENSSL);
        this.disableSessionTickets = disableSessionTickets;
    }


    public boolean getDisableSessionTickets() {
        return disableSessionTickets;
    }


    public void setInsecureRenegotiation(boolean insecureRenegotiation) {
        setProperty("insecureRenegotiation", Type.OPENSSL);
        this.insecureRenegotiation = insecureRenegotiation;
    }


    public boolean getInsecureRenegotiation() {
        return insecureRenegotiation;
    }


    // --------------------------------------------------------- Support methods

    public static String adjustRelativePath(String path) {
        // 空路径或null路径不能指向任何有用的东西. 假设该值是故意为空/null, 所以保持这种方式.
        if (path == null || path.length() == 0) {
            return path;
        }
        String newPath = path;
        File f = new File(newPath);
        if ( !f.isAbsolute()) {
            newPath = System.getProperty(Constants.CATALINA_BASE_PROP) + File.separator + newPath;
            f = new File(newPath);
        }
        if (!f.exists()) {
            // TODO i18n, sm
            log.warn("configured file:["+newPath+"] does not exist.");
        }
        return newPath;
    }


    // ----------------------------------------------------------- Inner classes

    public static enum Type {
        JSSE,
        OPENSSL,
        EITHER
    }


    public static enum CertificateVerification {
        NONE,
        OPTIONAL_NO_CA,
        OPTIONAL,
        REQUIRED;

        public static CertificateVerification fromString(String value) {
            if ("true".equalsIgnoreCase(value) ||
                    "yes".equalsIgnoreCase(value) ||
                    "require".equalsIgnoreCase(value) ||
                    "required".equalsIgnoreCase(value)) {
                return REQUIRED;
            } else if ("optional".equalsIgnoreCase(value) ||
                    "want".equalsIgnoreCase(value)) {
                return OPTIONAL;
            } else if ("optionalNoCA".equalsIgnoreCase(value) ||
                    "optional_no_ca".equalsIgnoreCase(value)) {
                return OPTIONAL_NO_CA;
            } else if ("false".equalsIgnoreCase(value) ||
                    "no".equalsIgnoreCase(value) ||
                    "none".equalsIgnoreCase(value)) {
                return NONE;
            } else {
                // 可能是一个错字. 不要默认为NONE，因为这不安全. 强制用户修复配置. 可以默认为REQUIRED.
                throw new IllegalArgumentException(
                        sm.getString("sslHostConfig.certificateVerificationInvalid", value));
            }
        }
    }
}
