package org.apache.tomcat.util.net.openssl.ciphers;

import org.apache.tomcat.util.net.Constants;

enum Protocol {

    SSLv3(Constants.SSL_PROTO_SSLv3),
    SSLv2(Constants.SSL_PROTO_SSLv2),
    TLSv1(Constants.SSL_PROTO_TLSv1),
    TLSv1_2(Constants.SSL_PROTO_TLSv1_2);

    private final String openSSLName;

    private Protocol(String openSSLName) {
        this.openSSLName = openSSLName;
    }

    /**
     * 使用<code>openssl ciphers -v</code>时, OpenSSL在协议列中返回的名称. 这目前仅用于单元测试，因此它是包私有的.
     */
    String getOpenSSLName() {
        return openSSLName;
    }
}
