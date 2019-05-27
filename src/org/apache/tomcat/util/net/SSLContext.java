package org.apache.tomcat.util.net;

import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

/**
 * 需要此接口来覆盖默认的SSLContext类，以允许SSL实现可插拔性，而无需使用JCE. 使用常规JSSE，它只会委托SSLContext.
 */
public interface SSLContext {

    public void init(KeyManager[] kms, TrustManager[] tms,
            SecureRandom sr) throws KeyManagementException;

    public void destroy();

    public SSLSessionContext getServerSessionContext();

    public SSLEngine createSSLEngine();

    public SSLServerSocketFactory getServerSocketFactory();

    public SSLParameters getSupportedSSLParameters();

}
