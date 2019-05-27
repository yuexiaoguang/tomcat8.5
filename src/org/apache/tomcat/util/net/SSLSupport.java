package org.apache.tomcat.util.net;

import java.io.IOException;
import java.security.cert.X509Certificate;

/**
 * 定义与SSL会话交互的接口.
 */
public interface SSLSupport {
    /**
     * 密码套件的Request属性键.
     */
    public static final String CIPHER_SUITE_KEY =
            "javax.servlet.request.cipher_suite";

    /**
     * 密钥大小的Request属性键.
     */
    public static final String KEY_SIZE_KEY = "javax.servlet.request.key_size";

    /**
     * 客户端证书链的Request属性键.
     */
    public static final String CERTIFICATE_KEY =
            "javax.servlet.request.X509Certificate";

    /**
     * 会话ID的Request属性键.
     * 这是Servlet规范的Tomcat扩展.
     */
    public static final String SESSION_ID_KEY =
            "javax.servlet.request.ssl_session_id";

    /**
     * 会话管理器的Request属性键.
     * 这是Servlet规范的Tomcat扩展.
     */
    public static final String SESSION_MGR =
            "javax.servlet.request.ssl_session_mgr";

    /**
     * 用于记录表示创建SSL套接字的协议的String的Request属性键 - e.g. TLSv1 或 TLSv1.2 等.
     */
    public static final String PROTOCOL_VERSION_KEY =
            "org.apache.tomcat.util.net.secure_protocol_version";

    /**
     * 在此连接上使用的密码套件.
     *
     * @return SSL/TLS实现返回的密码套件的名称
     *
     * @throws IOException 如果尝试获取密码套件时发生错误
     */
    public String getCipherSuite() throws IOException;

    /**
     * 客户端证书链.
     *
     * @return 客户端首先使用对等方证书提供的证书链, 其次是任何证书颁发机构
     *
     * @throws IOException 如果尝试获取证书链时发生错误
     */
    public X509Certificate[] getPeerCertificateChain() throws IOException;

    /**
     * 获取 keysize.
     *
     * 应该放在这里的是Servlet规范定义不明确的 (S 4.7 again). 这里至少有4个可能的值:
     *
     * (a) 加密密钥的大小
     * (b) MAC密钥的大小
     * (c) key-exchange密钥的大小
     * (d) 服务器使用的签名密钥的大小
     *
     * 不幸的是, 所有这些值观都是荒谬的.
     *
     * @return 当前密码套件的有效密钥大小
     *
     * @throws IOException 如果尝试获取密钥大小时发生错误
     */
    public Integer getKeySize() throws IOException;

    /**
     * 当前会话ID.
     *
     * @return 当前的SSL/TLS会话ID
     *
     * @throws IOException 如果尝试获取会话ID时发生错误
     */
    public String getSessionId() throws IOException;

    /**
     * @return 协议字符串，指示SSL套接字的创建方式. e.g. TLSv1 或 TLSv1.2 等.
     *
     * @throws IOException 如果尝试从套接字获取协议信息时发生错误
     */
    public String getProtocol() throws IOException;
}

