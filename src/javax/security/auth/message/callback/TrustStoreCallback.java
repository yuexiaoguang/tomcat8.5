package javax.security.auth.message.callback;

import java.security.KeyStore;

import javax.security.auth.callback.Callback;

/**
 * 回调，允许认证模块从运行时请求一个信任库.
 */
public class TrustStoreCallback implements Callback {

    private KeyStore trustStore;

    public void setTrustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
    }

    public KeyStore getTrustStore() {
        return trustStore;
    }
}
