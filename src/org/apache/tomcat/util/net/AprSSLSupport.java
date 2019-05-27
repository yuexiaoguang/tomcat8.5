package org.apache.tomcat.util.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.tomcat.jni.SSL;

/**
 * APR的SSLSupport的实现.
 * <p>
 * TODO: 添加一种机制 (或弄清楚如何使用已有的东西)以使会话无效.
 */
public class AprSSLSupport implements SSLSupport {

    private final AprEndpoint.AprSocketWrapper socketWrapper;
    private final String clientCertProvider;


    public AprSSLSupport(AprEndpoint.AprSocketWrapper socketWrapper, String clientCertProvider) {
        this.socketWrapper = socketWrapper;
        this.clientCertProvider = clientCertProvider;
    }


    @Override
    public String getCipherSuite() throws IOException {
        try {
            return socketWrapper.getSSLInfoS(SSL.SSL_INFO_CIPHER);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public X509Certificate[] getPeerCertificateChain() throws IOException {
        try {
            // certLength == -1 表示错误, 除非正在使用TLS会话票据，否则OpenSSL不会将链存储在票证中.
            int certLength = socketWrapper.getSSLInfoI(SSL.SSL_INFO_CLIENT_CERT_CHAIN);
            byte[] clientCert = socketWrapper.getSSLInfoB(SSL.SSL_INFO_CLIENT_CERT);
            X509Certificate[] certs = null;

            if (clientCert != null) {
                if (certLength < 0) {
                    certLength = 0;
                }
                certs = new X509Certificate[certLength + 1];
                CertificateFactory cf;
                if (clientCertProvider == null) {
                    cf = CertificateFactory.getInstance("X.509");
                } else {
                    cf = CertificateFactory.getInstance("X.509", clientCertProvider);
                }
                certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
                for (int i = 0; i < certLength; i++) {
                    byte[] data = socketWrapper.getSSLInfoB(SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
                    certs[i+1] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
                }
            }
            return certs;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public Integer getKeySize() throws IOException {
        try {
            return Integer.valueOf(socketWrapper.getSSLInfoI(SSL.SSL_INFO_CIPHER_USEKEYSIZE));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public String getSessionId() throws IOException {
        try {
            return socketWrapper.getSSLInfoS(SSL.SSL_INFO_SESSION_ID);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getProtocol() throws IOException {
        try {
            return socketWrapper.getSSLInfoS(SSL.SSL_INFO_PROTOCOL);
        } catch (Exception e) {
            throw new IOException(e);
        }
   }
}
