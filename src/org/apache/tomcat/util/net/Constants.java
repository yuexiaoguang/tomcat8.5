package org.apache.tomcat.util.net;

public class Constants {

    /**
     * 包含tomcat实例安装路径的系统属性的名称
     */
    public static final String CATALINA_BASE_PROP = "catalina.base";

    /**
     * JSSE和OpenSSL协议名称
     */
    public static final String SSL_PROTO_ALL        = "all";
    public static final String SSL_PROTO_TLS        = "TLS";
    public static final String SSL_PROTO_TLSv1_2    = "TLSv1.2";
    public static final String SSL_PROTO_TLSv1_1    = "TLSv1.1";
    // TLS 1.0的两种不同形式
    public static final String SSL_PROTO_TLSv1_0    = "TLSv1.0";
    public static final String SSL_PROTO_TLSv1      = "TLSv1";
    public static final String SSL_PROTO_SSLv3      = "SSLv3";
    public static final String SSL_PROTO_SSLv2      = "SSLv2";
    public static final String SSL_PROTO_SSLv2Hello = "SSLv2Hello";
}
