package org.apache.tomcat.util.net.openssl;

import java.util.List;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtilBase;
import org.apache.tomcat.util.net.jsse.JSSEUtil;

public class OpenSSLUtil extends SSLUtilBase {

    private static final Log log = LogFactory.getLog(OpenSSLUtil.class);

    private final JSSEUtil jsseUtil;

    public OpenSSLUtil(SSLHostConfigCertificate certificate) {
        super(certificate);

        if (certificate.getCertificateFile() == null) {
            // 对密钥库和信任库使用JSSE配置
            jsseUtil = new JSSEUtil(certificate);
        } else {
            // 对证书使用OpenSSL配置
            jsseUtil = null;
        }
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected Set<String> getImplementedProtocols() {
        return OpenSSLEngine.IMPLEMENTED_PROTOCOLS_SET;
    }


    @Override
    protected Set<String> getImplementedCiphers() {
        return OpenSSLEngine.AVAILABLE_CIPHER_SUITES;
    }


    @Override
    public SSLContext createSSLContext(List<String> negotiableProtocols) throws Exception {
        return new OpenSSLContext(certificate, negotiableProtocols);
    }

    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        if (jsseUtil != null) {
            return jsseUtil.getKeyManagers();
        } else {
            // 返回一些东西，虽然它实际上没有使用
            KeyManager[] managers = {
                    new OpenSSLKeyManager(SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()),
                            SSLHostConfig.adjustRelativePath(certificate.getCertificateKeyFile()))
            };
            return managers;
        }
    }

    @Override
    public TrustManager[] getTrustManagers() throws Exception {
        if (jsseUtil != null) {
            return jsseUtil.getTrustManagers();
        } else {
            return null;
        }
    }

    @Override
    public void configureSessionContext(SSLSessionContext sslSessionContext) {
        if (jsseUtil != null) {
            jsseUtil.configureSessionContext(sslSessionContext);
        }
    }
}
