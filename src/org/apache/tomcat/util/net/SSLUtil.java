package org.apache.tomcat.util.net;

import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

/**
 * 为{@link SSLImplementation}提供通用接口，为通过JSSE API创建的TLS连接创建必要的JSSE实现对象.
 */
public interface SSLUtil {

    public SSLContext createSSLContext(List<String> negotiableProtocols) throws Exception;

    public KeyManager[] getKeyManagers() throws Exception;

    public TrustManager[] getTrustManagers() throws Exception;

    public void configureSessionContext(SSLSessionContext sslSessionContext);

    /**
     * 启用的协议集是实现的协议和配置的协议的交集.
     * 如果没有明确配置协议, 然后所有实现的协议都将包含在返回的数组中.
     *
     * @return 当前启用的协议, 可供客户端从关联连接中进行选择
     *
     * @throws IllegalArgumentException  如果已实现和已配置的协议之间没有交集
     */
    public String[] getEnabledProtocols() throws IllegalArgumentException;

    /**
     * 启用的密码集是已实现的密码和配置的密码的交集.
     * 如果未明确配置密码, 然后默认密码将包含在返回的数组中.
     * <p>
     * 在TLS握手期间使用的密码, 可能会受到{@link #getEnabledProtocols()}和证书的进一步限制.
     *
     * @return 当前已启用的密码,并可供客户端选择用于关联的连接
     *
     * @throws IllegalArgumentException  如果已实现和已配置的密码之间没有交集
     */
    public String[] getEnabledCiphers() throws IllegalArgumentException;

    /**
     * 可选的接口, 可由{@link javax.net.ssl.SSLEngine}实现, 表明它们支持ALPN, 并且可以提供与客户端的协议.
     */
    public interface ProtocolInfo {
        /**
         * ALPN信息.
         * 
         * @return 使用ALPN选择的协议
         */
        public String getNegotiatedProtocol();
    }
}
