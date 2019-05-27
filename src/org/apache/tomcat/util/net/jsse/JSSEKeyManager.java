package org.apache.tomcat.util.net.jsse;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

/**
 * 允许选择特定的密钥对和证书链（由其密钥库别名标识）, 供服务器用于向SSL客户端验证自身.
 */
public final class JSSEKeyManager extends X509ExtendedKeyManager {

    private X509KeyManager delegate;
    private String serverKeyAlias;


    /**
     * @param mgr X509KeyManager用作委托
     * @param serverKeyAlias 服务器密钥对和支持证书链的别名
     */
    public JSSEKeyManager(X509KeyManager mgr, String serverKeyAlias) {
        super();
        this.delegate = mgr;
        this.serverKeyAlias = serverKeyAlias;
    }


    /**
     * 如果没有指定别名, 则返回构造函数中提供的服务器密钥别名, 
     * 或委托的{@link X509KeyManager#chooseServerAlias(String, Principal[], Socket)}的结果.
     */
    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        if (serverKeyAlias != null) {
            return serverKeyAlias;
        }

        return delegate.chooseServerAlias(keyType, issuers, socket);
    }


    /**
     * 如果没有指定别名, 则返回构造函数中提供的服务器密钥别名, 
     * 或{@link X509ExtendedKeyManager#chooseEngineServerAlias(String, Principal[], SSLEngine)}的结果.
     */
    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {
        if (serverKeyAlias!=null) {
            return serverKeyAlias;
        }

        return super.chooseEngineServerAlias(keyType, issuers, engine);
    }


    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
                                    Socket socket) {
        return delegate.chooseClientAlias(keyType, issuers, socket);
    }


    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        return delegate.getCertificateChain(alias);
    }


    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return delegate.getClientAliases(keyType, issuers);
    }


    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return delegate.getServerAliases(keyType, issuers);
    }


    @Override
    public PrivateKey getPrivateKey(String alias) {
        return delegate.getPrivateKey(alias);
    }


    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
            SSLEngine engine) {
        return delegate.chooseClientAlias(keyType, issuers, null);
    }
}
