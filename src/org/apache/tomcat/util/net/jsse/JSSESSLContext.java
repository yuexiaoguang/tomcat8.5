package org.apache.tomcat.util.net.jsse;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

import org.apache.tomcat.util.net.SSLContext;

class JSSESSLContext implements SSLContext {

    private javax.net.ssl.SSLContext context;
    JSSESSLContext(String protocol) throws NoSuchAlgorithmException {
        context = javax.net.ssl.SSLContext.getInstance(protocol);
    }

    @Override
    public void init(KeyManager[] kms, TrustManager[] tms, SecureRandom sr)
            throws KeyManagementException {
        context.init(kms, tms, sr);
    }

    @Override
    public void destroy() {
    }

    @Override
    public SSLSessionContext getServerSessionContext() {
        return context.getServerSessionContext();
    }

    @Override
    public SSLEngine createSSLEngine() {
        return context.createSSLEngine();
    }

    @Override
    public SSLServerSocketFactory getServerSocketFactory() {
        return context.getServerSocketFactory();
    }

    @Override
    public SSLParameters getSupportedSSLParameters() {
        return context.getSupportedSSLParameters();
    }
}
