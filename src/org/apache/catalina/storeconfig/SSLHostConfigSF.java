package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.openssl.OpenSSLConf;

/**
 * Store SSLHostConfig
 */
public class SSLHostConfigSF extends StoreFactoryBase {

    /**
     * 保存嵌套的 SSLHostConfigCertificate 元素.
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aSSLHostConfig,
            StoreDescription parentDesc) throws Exception {
        if (aSSLHostConfig instanceof SSLHostConfig) {
            SSLHostConfig sslHostConfig = (SSLHostConfig) aSSLHostConfig;
            // 保存嵌套的 <SSLHostConfigCertificate> 元素
            SSLHostConfigCertificate[] hostConfigsCertificates = sslHostConfig.getCertificates().toArray(new SSLHostConfigCertificate[0]);
            storeElementArray(aWriter, indent, hostConfigsCertificates);
            // 保存嵌套的 <OpenSSLConf> 元素
            OpenSSLConf openSslConf = sslHostConfig.getOpenSslConf();
            storeElement(aWriter, indent, openSslConf);
        }
    }

}
