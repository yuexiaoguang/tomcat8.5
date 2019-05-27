package org.apache.tomcat.util.net.openssl;

import javax.net.ssl.SSLSession;

import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.jsse.JSSESupport;

public class OpenSSLImplementation extends SSLImplementation {

    @Override
    public SSLSupport getSSLSupport(SSLSession session) {
        return new JSSESupport(session);
    }

    @Override
    public SSLUtil getSSLUtil(SSLHostConfigCertificate certificate) {
        return new OpenSSLUtil(certificate);
    }

    @Override
    public boolean isAlpnSupported() {
        // OpenSSL supported ALPN
        return true;
    }
}
