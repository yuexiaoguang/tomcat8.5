package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NetworkChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;

import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.SSLHostConfig.Type;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;

public abstract class AbstractJsseEndpoint<S> extends AbstractEndpoint<S> {

    private String sslImplementationName = null;
    private int sniParseLimit = 64 * 1024;

    private SSLImplementation sslImplementation = null;

    public String getSslImplementationName() {
        return sslImplementationName;
    }


    public void setSslImplementationName(String s) {
        this.sslImplementationName = s;
    }


    public SSLImplementation getSslImplementation() {
        return sslImplementation;
    }


    public int getSniParseLimit() {
        return sniParseLimit;
    }


    public void setSniParseLimit(int sniParseLimit) {
        this.sniParseLimit = sniParseLimit;
    }



    @Override
    protected Type getSslConfigType() {
        if (OpenSSLImplementation.class.getName().equals(sslImplementationName)) {
            return SSLHostConfig.Type.EITHER;
        } else {
            return SSLHostConfig.Type.JSSE;
        }
    }


    protected void initialiseSsl() throws Exception {
        if (isSSLEnabled()) {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());

            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                sslHostConfig.setConfigType(getSslConfigType());
                createSSLContext(sslHostConfig);
            }

            // Validate default SSLHostConfigName
            if (sslHostConfigs.get(getDefaultSSLHostConfigName()) == null) {
                throw new IllegalArgumentException(sm.getString("endpoint.noSslHostConfig",
                        getDefaultSSLHostConfigName(), getName()));
            }

        }
    }


    @Override
    protected void createSSLContext(SSLHostConfig sslHostConfig) throws IllegalArgumentException {
        boolean firstCertificate = true;
        for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
            SSLUtil sslUtil = sslImplementation.getSSLUtil(certificate);
            if (firstCertificate) {
                firstCertificate = false;
                sslHostConfig.setEnabledProtocols(sslUtil.getEnabledProtocols());
                sslHostConfig.setEnabledCiphers(sslUtil.getEnabledCiphers());
            }

            SSLContext sslContext;
            try {
                sslContext = sslUtil.createSSLContext(negotiableProtocols);
                sslContext.init(sslUtil.getKeyManagers(), sslUtil.getTrustManagers(), null);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }

            SSLSessionContext sessionContext = sslContext.getServerSessionContext();
            if (sessionContext != null) {
                sslUtil.configureSessionContext(sessionContext);
            }
            certificate.setSslContext(sslContext);
        }
    }


    protected void destroySsl() throws Exception {
        if (isSSLEnabled()) {
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                releaseSSLContext(sslHostConfig);
            }
        }
    }


    @Override
    protected void releaseSSLContext(SSLHostConfig sslHostConfig) {
        for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
            if (certificate.getSslContext() != null) {
                SSLContext sslContext = certificate.getSslContext();
                if (sslContext != null) {
                    sslContext.destroy();
                }
            }
        }
    }


    protected SSLEngine createSSLEngine(String sniHostName, List<Cipher> clientRequestedCiphers,
            List<String> clientRequestedApplicationProtocols) {
        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);

        SSLHostConfigCertificate certificate = selectCertificate(sslHostConfig, clientRequestedCiphers);

        SSLContext sslContext = certificate.getSslContext();
        if (sslContext == null) {
            throw new IllegalStateException(
                    sm.getString("endpoint.jsse.noSslContext", sniHostName));
        }

        SSLEngine engine = sslContext.createSSLEngine();
        switch (sslHostConfig.getCertificateVerification()) {
        case NONE:
            engine.setNeedClientAuth(false);
            engine.setWantClientAuth(false);
            break;
        case OPTIONAL:
        case OPTIONAL_NO_CA:
            engine.setWantClientAuth(true);
            break;
        case REQUIRED:
            engine.setNeedClientAuth(true);
            break;
        }
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(sslHostConfig.getEnabledCiphers());
        engine.setEnabledProtocols(sslHostConfig.getEnabledProtocols());

        String honorCipherOrderStr = sslHostConfig.getHonorCipherOrder();
        if (honorCipherOrderStr != null) {
            boolean honorCipherOrder = Boolean.parseBoolean(honorCipherOrderStr);
            JreCompat.getInstance().setUseServerCipherSuitesOrder(engine, honorCipherOrder);
        }

        if (JreCompat.isJre9Available() && clientRequestedApplicationProtocols.size() > 0 &&
                negotiableProtocols.size() > 0) {
            SSLParameters sslParameters = engine.getSSLParameters();
            // 如果客户端和服务器都至少有一个共同的协议，则尝试协商
            // Note: Tomcat没有明确地协商http/1.1
            // TODO: Is this correct? Should it change?
            List<String> commonProtocols = new ArrayList<>();
            commonProtocols.addAll(negotiableProtocols);
            commonProtocols.retainAll(clientRequestedApplicationProtocols);
            if (commonProtocols.size() > 0) {
                String[] commonProtocolsArray = commonProtocols.toArray(new String[commonProtocols.size()]);
                JreCompat.getInstance().setApplicationProtocols(sslParameters, commonProtocolsArray);
            }

            // 如果getter返回副本
            engine.setSSLParameters(sslParameters);
        }

        return engine;
    }


    private SSLHostConfigCertificate selectCertificate(
            SSLHostConfig sslHostConfig, List<Cipher> clientCiphers) {

        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates(true);
        if (certificates.size() == 1) {
            return certificates.iterator().next();
        }

        LinkedHashSet<Cipher> serverCiphers = sslHostConfig.getCipherList();

        List<Cipher> candidateCiphers = new ArrayList<>();
        if (Boolean.parseBoolean(sslHostConfig.getHonorCipherOrder())) {
            candidateCiphers.addAll(serverCiphers);
            candidateCiphers.retainAll(clientCiphers);
        } else {
            candidateCiphers.addAll(clientCiphers);
            candidateCiphers.retainAll(serverCiphers);
        }

        Iterator<Cipher> candidateIter = candidateCiphers.iterator();
        while (candidateIter.hasNext()) {
            Cipher candidate = candidateIter.next();
            for (SSLHostConfigCertificate certificate : certificates) {
                if (certificate.getType().isCompatibleWith(candidate.getAu())) {
                    return certificate;
                }
            }
        }

        // 不匹配. 只需返回第一个证书. 由于没有匹配的密码，握手将失败.
        return certificates.iterator().next();
    }


    @Override
    public boolean isAlpnSupported() {
        // 如果未启用TLS, 则ALPN需要TLS, 无法支持ALPN
        if (!isSSLEnabled()) {
            return false;
        }

        // 取决于SSLImplementation.
        SSLImplementation sslImplementation;
        try {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());
        } catch (ClassNotFoundException e) {
            // Ignore the exception. 尝试启动终点时会记录它.
            return false;
        }
        return sslImplementation.isAlpnSupported();
    }


    @Override
    public void init() throws Exception {
        testServerCipherSuitesOrderSupport();
        super.init();
    }


    private void testServerCipherSuitesOrderSupport() {
        // 如果在Java <8上运行而不使用OpenSSL SSLImplementation，则只需要对此进行测试
        if(!JreCompat.isJre8Available() &&
                !OpenSSLImplementation.class.getName().equals(getSslImplementationName())) {
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                if (sslHostConfig.getHonorCipherOrder() != null) {
                    throw new UnsupportedOperationException(
                            sm.getString("endpoint.jsse.cannotHonorServerCipherOrder"));
                }
            }
        }
    }


    @Override
    public void unbind() throws Exception {
        for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
            for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
                certificate.setSslContext(null);
            }
        }
    }


    protected abstract NetworkChannel getServerSocket();


    @Override
    protected final InetSocketAddress getLocalAddress() throws IOException {
        NetworkChannel serverSock = getServerSocket();
        if (serverSock == null) {
            return null;
        }
        SocketAddress sa = serverSock.getLocalAddress();
        if (sa instanceof InetSocketAddress) {
            return (InetSocketAddress) sa;
        }
        return null;
    }
}
