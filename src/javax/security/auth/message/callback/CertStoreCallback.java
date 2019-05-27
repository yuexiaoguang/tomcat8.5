package javax.security.auth.message.callback;

import java.security.cert.CertStore;

import javax.security.auth.callback.Callback;

/**
 * 回调， 允许运行时通知要使用的CertStore的身份验证模块.
 */
public class CertStoreCallback implements Callback {

    private CertStore certStore;

    public CertStoreCallback() {
    }

    public void setCertStore(CertStore certStore) {
        this.certStore = certStore;
    }

    public CertStore getCertStore() {
        return certStore;
    }
}
