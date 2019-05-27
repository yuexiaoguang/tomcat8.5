package org.apache.tomcat.util.net.openssl;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.CertificateVerifier;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLConf;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.jsse.JSSEKeyManager;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

public class OpenSSLContext implements org.apache.tomcat.util.net.SSLContext {

    private static final Base64 BASE64_ENCODER = new Base64(64, new byte[] {'\n'});

    private static final Log log = LogFactory.getLog(OpenSSLContext.class);

    // Note: 这使用主要的“net”包字符串，因为APR很常见
    private static final StringManager netSm = StringManager.getManager(AbstractEndpoint.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLContext.class);

    private static final String defaultProtocol = "TLS";

    private final SSLHostConfig sslHostConfig;
    private final SSLHostConfigCertificate certificate;
    private OpenSSLSessionContext sessionContext;

    private final List<String> negotiableProtocols;

    private List<String> jsseCipherNames = new ArrayList<>();

    public List<String> getJsseCipherNames() {
        return jsseCipherNames;
    }

    private String enabledProtocol;

    public String getEnabledProtocol() {
        return enabledProtocol;
    }

    public void setEnabledProtocol(String protocol) {
        enabledProtocol = (protocol == null) ? defaultProtocol : protocol;
    }

    private final long aprPool;
    private final AtomicInteger aprPoolDestroyed = new AtomicInteger(0);

    // OpenSSLConfCmd context
    protected final long cctx;
    // SSL context
    protected final long ctx;

    static final CertificateFactory X509_CERT_FACTORY;

    private static final String BEGIN_KEY = "-----BEGIN RSA PRIVATE KEY-----\n";

    private static final Object END_KEY = "\n-----END RSA PRIVATE KEY-----";
    private boolean initialized = false;

    static {
        try {
            X509_CERT_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new IllegalStateException(sm.getString("openssl.X509FactoryError"), e);
        }
    }

    public OpenSSLContext(SSLHostConfigCertificate certificate, List<String> negotiableProtocols)
            throws SSLException {
        this.sslHostConfig = certificate.getSSLHostConfig();
        this.certificate = certificate;
        aprPool = Pool.create(0);
        boolean success = false;
        try {
            // 如果使用，则创建OpenSSLConfCmd上下文
            OpenSSLConf openSslConf = sslHostConfig.getOpenSslConf();
            if (openSslConf != null) {
                try {
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("openssl.makeConf"));
                    cctx = SSLConf.make(aprPool,
                                        SSL.SSL_CONF_FLAG_FILE |
                                        SSL.SSL_CONF_FLAG_SERVER |
                                        SSL.SSL_CONF_FLAG_CERTIFICATE |
                                        SSL.SSL_CONF_FLAG_SHOW_ERRORS);
                } catch (Exception e) {
                    throw new SSLException(sm.getString("openssl.errMakeConf"), e);
                }
            } else {
                cctx = 0;
            }
            sslHostConfig.setOpenSslConfContext(Long.valueOf(cctx));

            // SSL协议
            int value = SSL.SSL_PROTOCOL_NONE;
            for (String protocol : sslHostConfig.getEnabledProtocols()) {
                if (Constants.SSL_PROTO_SSLv2Hello.equalsIgnoreCase(protocol)) {
                    // NO-OP. OpenSSL始终支持SSLv2Hello
                } else if (Constants.SSL_PROTO_SSLv2.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_SSLV2;
                } else if (Constants.SSL_PROTO_SSLv3.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_SSLV3;
                } else if (Constants.SSL_PROTO_TLSv1.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_TLSV1;
                } else if (Constants.SSL_PROTO_TLSv1_1.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_TLSV1_1;
                } else if (Constants.SSL_PROTO_TLSv1_2.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_TLSV1_2;
                } else if (Constants.SSL_PROTO_ALL.equalsIgnoreCase(protocol)) {
                    value |= SSL.SSL_PROTOCOL_ALL;
                } else {
                    // 未识别协议, 无法启动，因为它比继续使用默认值更安全，可能会启用超过所需的值
                    throw new Exception(netSm.getString(
                            "endpoint.apr.invalidSslProtocol", protocol));
                }
            }

            // Create SSL Context
            try {
                ctx = SSLContext.make(aprPool, value, SSL.SSL_MODE_SERVER);
            } catch (Exception e) {
                // 如果在AprLifecycleListener上禁用sslEngine，则会出现Exception，但无法从此处检查AprLifecycleListener设置
                throw new Exception(
                        netSm.getString("endpoint.apr.failSslContextMake"), e);
            }

            this.negotiableProtocols = negotiableProtocols;

            success = true;
        } catch(Exception e) {
            throw new SSLException(sm.getString("openssl.errorSSLCtxInit"), e);
        } finally {
            if (!success) {
                destroy();
            }
        }
    }

    @Override
    public synchronized void destroy() {
        // 防止由构造异常触发的多个destroyPools()调用, 以及稍后的finalize()
        if (aprPoolDestroyed.compareAndSet(0, 1)) {
            if (ctx != 0) {
                SSLContext.free(ctx);
            }
            if (cctx != 0) {
                SSLConf.free(cctx);
            }
            if (aprPool != 0) {
                Pool.destroy(aprPool);
            }
        }
    }

    /**
     * 设置SSL_CTX.
     *
     * @param kms 必须包含{@code OpenSSLKeyManager}类型的KeyManager
     * @param tms 必须包含{@code X509TrustManager}类型的TrustManager
     * @param sr 不用于此实现.
     */
    @Override
    public synchronized void init(KeyManager[] kms, TrustManager[] tms, SecureRandom sr) {
        if (initialized) {
            log.warn(sm.getString("openssl.doubleInit"));
            return;
        }
        try {
            if (sslHostConfig.getInsecureRenegotiation()) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION);
            }

            // 使用服务器的密码优先顺序 (而不是客户端的)
            String honorCipherOrderStr = sslHostConfig.getHonorCipherOrder();
            if (honorCipherOrderStr != null) {
                if (Boolean.parseBoolean(honorCipherOrderStr)) {
                    SSLContext.setOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                } else {
                    SSLContext.clearOptions(ctx, SSL.SSL_OP_CIPHER_SERVER_PREFERENCE);
                }
            }

            // 如果请求，禁用压缩
            if (sslHostConfig.getDisableCompression()) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_COMPRESSION);
            }

            // 禁用TLS会话票证（RFC4507）以保护完美的前向保密
            if (sslHostConfig.getDisableSessionTickets()) {
                SSLContext.setOptions(ctx, SSL.SSL_OP_NO_TICKET);
            } else {
                SSLContext.clearOptions(ctx, SSL.SSL_OP_NO_TICKET);
            }

            // 设置会话缓存大小, 如果指定
            if (sslHostConfig.getSessionCacheSize() > 0) {
                SSLContext.setSessionCacheSize(ctx, sslHostConfig.getSessionCacheSize());
            } else {
                // 使用SSLContext.setSessionCacheSize()获取默认会话高速缓存大小
                long sessionCacheSize = SSLContext.setSessionCacheSize(ctx, 20480);
                // 将会话高速缓存大小还原为默认值.
                SSLContext.setSessionCacheSize(ctx, sessionCacheSize);
            }

            // 设置会话超时
            if (sslHostConfig.getSessionTimeout() > 0) {
                SSLContext.setSessionCacheTimeout(ctx, sslHostConfig.getSessionTimeout());
            } else {
                // 使用SSLContext.setSessionCacheTimeout()获取默认会话超时时间
                long sessionTimeout = SSLContext.setSessionCacheTimeout(ctx, 300);
                // 将会话超时时间恢复为默认值.
                SSLContext.setSessionCacheTimeout(ctx, sessionTimeout);
            }

            // 列出允许客户端协商的密码
            String opensslCipherConfig = sslHostConfig.getCiphers();
            this.jsseCipherNames = OpenSSLCipherConfigurationParser.parseExpression(opensslCipherConfig);
            SSLContext.setCipherSuite(ctx, opensslCipherConfig);
            // 加载服务器密钥和证书
            if (certificate.getCertificateFile() != null) {
                // 设置证书
                SSLContext.setCertificate(ctx,
                        SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()),
                        SSLHostConfig.adjustRelativePath(certificate.getCertificateKeyFile()),
                        certificate.getCertificateKeyPassword(), SSL.SSL_AIDX_RSA);
                // 设置证书链文件
                SSLContext.setCertificateChainFile(ctx,
                        SSLHostConfig.adjustRelativePath(certificate.getCertificateChainFile()), false);
                // 设置撤销
                SSLContext.setCARevocation(ctx,
                        SSLHostConfig.adjustRelativePath(
                                sslHostConfig.getCertificateRevocationListFile()),
                        SSLHostConfig.adjustRelativePath(
                                sslHostConfig.getCertificateRevocationListPath()));
            } else {
                X509KeyManager keyManager = chooseKeyManager(kms);
                String alias = certificate.getCertificateKeyAlias();
                if (alias == null) {
                    alias = "tomcat";
                }
                X509Certificate[] chain = keyManager.getCertificateChain(alias);
                if (chain == null) {
                    alias = findAlias(keyManager, certificate);
                    chain = keyManager.getCertificateChain(alias);
                }
                PrivateKey key = keyManager.getPrivateKey(alias);
                StringBuilder sb = new StringBuilder(BEGIN_KEY);
                String encoded = BASE64_ENCODER.encodeToString(key.getEncoded());
                if (encoded.endsWith("\n")) {
                    encoded = encoded.substring(0, encoded.length() - 1);
                }
                sb.append(encoded);
                sb.append(END_KEY);
                SSLContext.setCertificateRaw(ctx, chain[0].getEncoded(), sb.toString().getBytes(StandardCharsets.US_ASCII), SSL.SSL_AIDX_RSA);
                for (int i = 1; i < chain.length; i++) {
                    SSLContext.addChainCertificateRaw(ctx, chain[i].getEncoded());
                }
            }
            // 客户端证书验证
            int value = 0;
            switch (sslHostConfig.getCertificateVerification()) {
            case NONE:
                value = SSL.SSL_CVERIFY_NONE;
                break;
            case OPTIONAL:
                value = SSL.SSL_CVERIFY_OPTIONAL;
                break;
            case OPTIONAL_NO_CA:
                value = SSL.SSL_CVERIFY_OPTIONAL_NO_CA;
                break;
            case REQUIRED:
                value = SSL.SSL_CVERIFY_REQUIRE;
                break;
            }
            SSLContext.setVerify(ctx, value, sslHostConfig.getCertificateVerificationDepth());

            if (tms != null) {
                // 基于自定义信任管理器的客户端证书验证
                final X509TrustManager manager = chooseTrustManager(tms);
                SSLContext.setCertVerifyCallback(ctx, new CertificateVerifier() {
                    @Override
                    public boolean verify(long ssl, byte[][] chain, String auth) {
                        X509Certificate[] peerCerts = certificates(chain);
                        try {
                            manager.checkClientTrusted(peerCerts, auth);
                            return true;
                        } catch (Exception e) {
                            log.debug(sm.getString("openssl.certificateVerificationFailed"), e);
                        }
                        return false;
                    }
                });
                // 传递接受的客户端证书颁发者的DER编码证书, 这样他们的主题可以在握手期间由服务器呈现, 以允许客户端选择可接受的证书
                for (X509Certificate caCert : manager.getAcceptedIssuers()) {
                    SSLContext.addClientCACertificateRaw(ctx, caCert.getEncoded());
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("openssl.addedClientCaCert", caCert.toString()));
                }
            } else {
                // 基于可信CA文件和目录的客户端证书验证
                SSLContext.setCACertificate(ctx,
                        SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificateFile()),
                        SSLHostConfig.adjustRelativePath(sslHostConfig.getCaCertificatePath()));
            }

            if (negotiableProtocols != null && negotiableProtocols.size() > 0) {
                ArrayList<String> protocols = new ArrayList<>();
                protocols.addAll(negotiableProtocols);
                protocols.add("http/1.1");
                String[] protocolsArray = protocols.toArray(new String[0]);
                SSLContext.setAlpnProtos(ctx, protocolsArray, SSL.SSL_SELECTOR_FAILURE_NO_ADVERTISE);
                SSLContext.setNpnProtos(ctx, protocolsArray, SSL.SSL_SELECTOR_FAILURE_NO_ADVERTISE);
            }

            // 如果使用，请应用OpenSSLConfCmd
            OpenSSLConf openSslConf = sslHostConfig.getOpenSslConf();
            if (openSslConf != null && cctx != 0) {
                // 如果使用，请检查OpenSSLConfCmd
                if (log.isDebugEnabled())
                    log.debug(sm.getString("openssl.checkConf"));
                try {
                    if (!openSslConf.check(cctx)) {
                        log.error(sm.getString("openssl.errCheckConf"));
                        throw new Exception(sm.getString("openssl.errCheckConf"));
                    }
                } catch (Exception e) {
                    throw new Exception(sm.getString("openssl.errCheckConf"), e);
                }
                if (log.isDebugEnabled())
                    log.debug(sm.getString("openssl.applyConf"));
                try {
                    if (!openSslConf.apply(cctx, ctx)) {
                        log.error(sm.getString("openssl.errApplyConf"));
                        throw new SSLException(sm.getString("openssl.errApplyConf"));
                    }
                } catch (Exception e) {
                    throw new SSLException(sm.getString("openssl.errApplyConf"), e);
                }
                // 重新配置启用的协议
                int opts = SSLContext.getOptions(ctx);
                List<String> enabled = new ArrayList<>();
                // 似乎无法在OpenSSL中显式禁用SSLv2Hello，因此始终启用它
                enabled.add(Constants.SSL_PROTO_SSLv2Hello);
                if ((opts & SSL.SSL_OP_NO_TLSv1) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1);
                }
                if ((opts & SSL.SSL_OP_NO_TLSv1_1) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1_1);
                }
                if ((opts & SSL.SSL_OP_NO_TLSv1_2) == 0) {
                    enabled.add(Constants.SSL_PROTO_TLSv1_2);
                }
                if ((opts & SSL.SSL_OP_NO_SSLv2) == 0) {
                    enabled.add(Constants.SSL_PROTO_SSLv2);
                }
                if ((opts & SSL.SSL_OP_NO_SSLv3) == 0) {
                    enabled.add(Constants.SSL_PROTO_SSLv3);
                }
                sslHostConfig.setEnabledProtocols(
                        enabled.toArray(new String[enabled.size()]));
                // 重新配置启用的密码
                sslHostConfig.setEnabledCiphers(SSLContext.getCiphers(ctx));
            }

            sessionContext = new OpenSSLSessionContext(ctx);
            // 如果正在使用客户端身份验证, OpenSSL要求设置此项, 以便始终设置它, 以防应用程序配置为需要它
            sessionContext.setSessionIdContext(SSLContext.DEFAULT_SESSION_ID_CONTEXT);
            sslHostConfig.setOpenSslContext(Long.valueOf(ctx));
            initialized = true;
        } catch (Exception e) {
            log.warn(sm.getString("openssl.errorSSLCtxInit"), e);
            destroy();
        }
    }

    /*
     * 在配置中未指定任何别名时, 查找有效别名.
     */
    private static String findAlias(X509KeyManager keyManager,
            SSLHostConfigCertificate certificate) {

        Type type = certificate.getType();
        String result = null;

        List<Type> candidateTypes = new ArrayList<>();
        if (Type.UNDEFINED.equals(type)) {
            // 尝试所有类型以找到合适的别名
            candidateTypes.addAll(Arrays.asList(Type.values()));
            candidateTypes.remove(Type.UNDEFINED);
        } else {
            // 查找特定类型以查找合适的别名
            candidateTypes.add(type);
        }

        Iterator<Type> iter = candidateTypes.iterator();
        while (result == null && iter.hasNext()) {
            result = keyManager.chooseServerAlias(iter.next().toString(),  null,  null);
        }

        return result;
    }

    private static X509KeyManager chooseKeyManager(KeyManager[] managers) throws Exception {
        for (KeyManager manager : managers) {
            if (manager instanceof JSSEKeyManager) {
                return (JSSEKeyManager) manager;
            }
        }
        for (KeyManager manager : managers) {
            if (manager instanceof X509KeyManager) {
                return (X509KeyManager) manager;
            }
        }
        throw new IllegalStateException(sm.getString("openssl.keyManagerMissing"));
    }

    private static X509TrustManager chooseTrustManager(TrustManager[] managers) {
        for (TrustManager m : managers) {
            if (m instanceof X509TrustManager) {
                return (X509TrustManager) m;
            }
        }
        throw new IllegalStateException(sm.getString("openssl.trustManagerMissing"));
    }

    private static X509Certificate[] certificates(byte[][] chain) {
        X509Certificate[] peerCerts = new X509Certificate[chain.length];
        for (int i = 0; i < peerCerts.length; i++) {
            peerCerts[i] = new OpenSSLX509Certificate(chain[i]);
        }
        return peerCerts;
    }

    @Override
    public SSLSessionContext getServerSessionContext() {
        return sessionContext;
    }

    @Override
    public SSLEngine createSSLEngine() {
        return new OpenSSLEngine(ctx, defaultProtocol, false, sessionContext,
                (negotiableProtocols != null && negotiableProtocols.size() > 0), initialized);
    }

    @Override
    public SSLServerSocketFactory getServerSocketFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLParameters getSupportedSSLParameters() {
        throw new UnsupportedOperationException();
    }
}
