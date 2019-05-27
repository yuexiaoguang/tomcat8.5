package org.apache.tomcat.util.net.jsse;

import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPathParameters;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreVendor;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtilBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * JSSE的SSLUtil实现.
 */
public class JSSEUtil extends SSLUtilBase {

    private static final Log log = LogFactory.getLog(JSSEUtil.class);
    private static final StringManager sm = StringManager.getManager(JSSEUtil.class);

    private static final Set<String> implementedProtocols;
    private static final Set<String> implementedCiphers;

    static {
        SSLContext context;
        try {
            context = new JSSESSLContext(Constants.SSL_PROTO_TLS);
            context.init(null,  null,  null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            // 这对连接器来说是致命的，所以抛出异常以防止它启动
            throw new IllegalArgumentException(e);
        }

        String[] implementedProtocolsArray = context.getSupportedSSLParameters().getProtocols();
        implementedProtocols = new HashSet<>(implementedProtocolsArray.length);

        // 从已实现的协议列表中筛选出SSLv2 (以防万一在支持它的JVM上运行), 因为它不再被认为是安全的, 但允许SSLv2Hello.
        // 注意尽管存在已知的不安全因素，但允许使用SSLv3，因为某些用户仍然需要它.
        for (String protocol : implementedProtocolsArray) {
            String protocolUpper = protocol.toUpperCase(Locale.ENGLISH);
            if (!"SSLV2HELLO".equals(protocolUpper) && !"SSLV3".equals(protocolUpper)) {
                if (protocolUpper.contains("SSL")) {
                    log.debug(sm.getString("jsse.excludeProtocol", protocol));
                    continue;
                }
            }
            implementedProtocols.add(protocol);
        }

        if (implementedProtocols.size() == 0) {
            log.warn(sm.getString("jsse.noDefaultProtocols"));
        }

        String[] implementedCipherSuiteArray = context.getSupportedSSLParameters().getCipherSuites();
        // IBM JRE将接受密码套件名称SSL_xxx或TLS_xxx, 但仅返回支持的密码套件的SSL_xxx表单.
        // 因此，需要使用IBM JRE的两个表单过滤所请求的密码套件.
        if (JreVendor.IS_IBM_JVM) {
            implementedCiphers = new HashSet<>(implementedCipherSuiteArray.length * 2);
            for (String name : implementedCipherSuiteArray) {
                implementedCiphers.add(name);
                if (name.startsWith("SSL")) {
                    implementedCiphers.add("TLS" + name.substring(3));
                }
            }
        } else {
            implementedCiphers = new HashSet<>(implementedCipherSuiteArray.length);
            implementedCiphers.addAll(Arrays.asList(implementedCipherSuiteArray));
        }
    }


    private final SSLHostConfig sslHostConfig;


    public JSSEUtil (SSLHostConfigCertificate certificate) {
        super(certificate);
        this.sslHostConfig = certificate.getSSLHostConfig();
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected Set<String> getImplementedProtocols() {
        return implementedProtocols;
    }


    @Override
    protected Set<String> getImplementedCiphers() {
        return implementedCiphers;
    }


    @Override
    public SSLContext createSSLContext(List<String> negotiableProtocols) throws NoSuchAlgorithmException {
        return new JSSESSLContext(sslHostConfig.getSslProtocol());
    }


    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        String keyAlias = certificate.getCertificateKeyAlias();
        String algorithm = sslHostConfig.getKeyManagerAlgorithm();
        String keyPass = certificate.getCertificateKeyPassword();
        // 这必须在这里，因为它无法移动到SSLHostConfig，因为JSSE和OpenSSL之间的默认值不同.
        if (keyPass == null) {
            keyPass = certificate.getCertificateKeystorePassword();
        }

        KeyStore ks = certificate.getCertificateKeystore();
        KeyStore ksUsed = ks;

        /*
         * 尽可能使用内存密钥存储区.
         * 对于PEM格式密钥和证书, 它允许将它们导入到预期的格式.
         * 对于具有PKCS8编码密钥的Java密钥库 (e.g. JKS files), 它使Tomcat能够处理密钥库中存在多个密钥的情况,
         * 每个都有不同的密码. KeyManagerFactory无法处理，因此使用内存密钥存储区只需要所需的密钥.
         * 其他密钥存储（硬件，MS等）将按原样使用.
         */
        char[] keyPassArray = keyPass.toCharArray();

        if (ks == null) {
            PEMFile privateKeyFile = new PEMFile(SSLHostConfig.adjustRelativePath
                    (certificate.getCertificateKeyFile() != null ? certificate.getCertificateKeyFile() : certificate.getCertificateFile()),
                    keyPass);
            PEMFile certificateFile = new PEMFile(SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()));

            Collection<Certificate> chain = new ArrayList<>();
            chain.addAll(certificateFile.getCertificates());
            if (certificate.getCertificateChainFile() != null) {
                PEMFile certificateChainFile = new PEMFile(SSLHostConfig.adjustRelativePath(certificate.getCertificateChainFile()));
                chain.addAll(certificateChainFile.getCertificates());
            }

            if (keyAlias == null) {
                keyAlias = "tomcat";
            }

            // 切换到内存中的密钥库
            ksUsed = KeyStore.getInstance("JKS");
            ksUsed.load(null,  null);
            ksUsed.setKeyEntry(keyAlias, privateKeyFile.getPrivateKey(), keyPass.toCharArray(),
                    chain.toArray(new Certificate[chain.size()]));
        } else {
            if (keyAlias != null && !ks.isKeyEntry(keyAlias)) {
                throw new IOException(sm.getString("jsse.alias_no_key_entry", keyAlias));
            } else if (keyAlias == null) {
                Enumeration<String> aliases = ks.aliases();
                if (!aliases.hasMoreElements()) {
                    throw new IOException(sm.getString("jsse.noKeys"));
                }
                while (aliases.hasMoreElements() && keyAlias == null) {
                    keyAlias = aliases.nextElement();
                    if (!ks.isKeyEntry(keyAlias)) {
                        keyAlias = null;
                    }
                }
                if (keyAlias == null) {
                    throw new IOException(sm.getString("jsse.alias_no_key_entry", keyAlias));
                }
            }

            Key k = ks.getKey(keyAlias, keyPassArray);
            if (k != null && "PKCS#8".equalsIgnoreCase(k.getFormat())) {
                // 切换到内存中的密钥库
                String provider = certificate.getCertificateKeystoreProvider();
                if (provider == null) {
                    ksUsed = KeyStore.getInstance(certificate.getCertificateKeystoreType());
                } else {
                    ksUsed = KeyStore.getInstance(certificate.getCertificateKeystoreType(),
                            provider);
                }
                ksUsed.load(null,  null);
                ksUsed.setKeyEntry(keyAlias, k, keyPassArray, ks.getCertificateChain(keyAlias));
            }
            // 非PKCS＃8密钥库将使用原始密钥库
        }


        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ksUsed, keyPassArray);

        KeyManager[] kms = kmf.getKeyManagers();

        // 如果要使用密钥管理器进行过滤并使用原始密钥库，则只需按别名过滤密钥.
        // 内存中的密钥存储只有一个密钥，因此不需要过滤
        if (kms != null && ksUsed == ks) {
            String alias = keyAlias;
            // JKS密钥库始终将别名转换为小写
            if ("JKS".equals(certificate.getCertificateKeystoreType())) {
                alias = alias.toLowerCase(Locale.ENGLISH);
            }
            for(int i = 0; i < kms.length; i++) {
                kms[i] = new JSSEKeyManager((X509KeyManager)kms[i], alias);
            }
        }

        return kms;
    }


    @Override
    public TrustManager[] getTrustManagers() throws Exception {

        String className = sslHostConfig.getTrustManagerClassName();
        if(className != null && className.length() > 0) {
             ClassLoader classLoader = getClass().getClassLoader();
             Class<?> clazz = classLoader.loadClass(className);
             if(!(TrustManager.class.isAssignableFrom(clazz))){
                throw new InstantiationException(sm.getString(
                        "jsse.invalidTrustManagerClassName", className));
             }
             Object trustManagerObject = clazz.getConstructor().newInstance();
             TrustManager trustManager = (TrustManager) trustManagerObject;
             return new TrustManager[]{ trustManager };
        }

        TrustManager[] tms = null;

        KeyStore trustStore = sslHostConfig.getTruststore();
        if (trustStore != null) {
            checkTrustStoreEntries(trustStore);
            String algorithm = sslHostConfig.getTruststoreAlgorithm();
            String crlf = sslHostConfig.getCertificateRevocationListFile();
            boolean revocationEnabled = sslHostConfig.getRevocationEnabled();

            if ("PKIX".equalsIgnoreCase(algorithm)) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
                CertPathParameters params = getParameters(crlf, trustStore, revocationEnabled);
                ManagerFactoryParameters mfp = new CertPathTrustManagerParameters(params);
                tmf.init(mfp);
                tms = tmf.getTrustManagers();
            } else {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
                tmf.init(trustStore);
                tms = tmf.getTrustManagers();
                if (crlf != null && crlf.length() > 0) {
                    throw new CRLException(sm.getString("jsseUtil.noCrlSupport", algorithm));
                }
                // 仅在显式配置属性时发出警告
                if (sslHostConfig.isCertificateVerificationDepthConfigured()) {
                    log.warn(sm.getString("jsseUtil.noVerificationDepth", algorithm));
                }
            }
        }

        return tms;
    }


    private void checkTrustStoreEntries(KeyStore trustStore) throws Exception {
        Enumeration<String> aliases = trustStore.aliases();
        if (aliases != null) {
            Date now = new Date();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (trustStore.isCertificateEntry(alias)) {
                    Certificate cert = trustStore.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        try {
                            ((X509Certificate) cert).checkValidity(now);
                        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                            String msg = sm.getString("jsseUtil.trustedCertNotValid", alias,
                                    ((X509Certificate) cert).getSubjectDN(), e.getMessage());
                            if (log.isDebugEnabled()) {
                                log.debug(msg, e);
                            } else {
                                log.warn(msg);
                            }
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("jsseUtil.trustedCertNotChecked", alias));
                        }
                    }
                }
            }
        }
    }


    @Override
    public void configureSessionContext(SSLSessionContext sslSessionContext) {
        sslSessionContext.setSessionCacheSize(sslHostConfig.getSessionCacheSize());
        sslSessionContext.setSessionTimeout(sslHostConfig.getSessionTimeout());
    }


    /**
     * 返回TrustManager的初始化参数. 当前, 只支持默认的<code>PKIX</code>.
     *
     * @param crlf CRL文件的路径.
     * @param trustStore 配置的TrustStore.
     * @param revocationEnabled JSSE提供程序是否应执行吊销检查? 如果{@code crlf}非空, 则忽略.
     *                          撤销检查的配置应该通过专有的JSSE提供程序方法进行.
     *                          
     * @return 参数包括CRL和TrustStore.
     * @throws Exception 发生错误
     */
    protected CertPathParameters getParameters(String crlf, KeyStore trustStore,
            boolean revocationEnabled) throws Exception {

        PKIXBuilderParameters xparams =
                new PKIXBuilderParameters(trustStore, new X509CertSelector());
        if (crlf != null && crlf.length() > 0) {
            Collection<? extends CRL> crls = getCRLs(crlf);
            CertStoreParameters csp = new CollectionCertStoreParameters(crls);
            CertStore store = CertStore.getInstance("Collection", csp);
            xparams.addCertStore(store);
            xparams.setRevocationEnabled(true);
        } else {
            xparams.setRevocationEnabled(revocationEnabled);
        }
        xparams.setMaxPathLength(sslHostConfig.getCertificateVerificationDepth());
        return xparams;
    }


    /**
     * 加载CRL集合.
     * 
     * @param crlf CRL文件的路径.
     * 
     * @return CRL集合
     * @throws IOException 读取CRL文件时出错
     * @throws CRLException CRL错误
     * @throws CertificateException 处理证书时出错
     */
    protected Collection<? extends CRL> getCRLs(String crlf)
        throws IOException, CRLException, CertificateException {

        Collection<? extends CRL> crls = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = ConfigFileLoader.getInputStream(crlf)) {
                crls = cf.generateCRLs(is);
            }
        } catch(IOException iex) {
            throw iex;
        } catch(CRLException crle) {
            throw crle;
        } catch(CertificateException ce) {
            throw ce;
        }
        return crls;
    }
}
