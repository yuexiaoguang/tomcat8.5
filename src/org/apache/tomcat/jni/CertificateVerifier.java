package org.apache.tomcat.jni;

/**
 * 在握手期间调用并通过{@code SSL_CTX_set_cert_verify_callback}连接到openssl.
 */
public interface CertificateVerifier {

    /**
     * 如果可以验证传入的证书链，则返回{@code true}，因此握手应该成功; 否则{@code false}.
     *
     * @param ssl               SSL 实例
     * @param x509              {@code X509} 证书链
     * @param authAlgorithm     auth算法
     * @return verified         {@code true} 如果验证成功; 否则{@code false}
     */
    boolean verify(long ssl, byte[][] x509, String authAlgorithm);
}
