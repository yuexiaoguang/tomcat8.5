package org.apache.tomcat.util.net.openssl;

import java.io.File;

import javax.net.ssl.KeyManager;

public class OpenSSLKeyManager implements KeyManager{

    private File certificateChain;
    public File getCertificateChain() { return certificateChain; }
    public void setCertificateChain(File certificateChain) { this.certificateChain = certificateChain; }

    private File privateKey;
    public File getPrivateKey() { return privateKey; }
    public void setPrivateKey(File privateKey) { this.privateKey = privateKey; }

    OpenSSLKeyManager(String certChainFile, String keyFile) {
        if (certChainFile == null) {
            return;
        }
        if (keyFile == null) {
            return;
        }
        this.certificateChain = new File(certChainFile);
        this.privateKey = new File(keyFile);
    }
}
