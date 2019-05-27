package javax.security.auth.message.callback;

import javax.crypto.SecretKey;
import javax.security.auth.callback.Callback;

/**
 * 回调，通过提供别名，使身份验证模块从运行时请求密钥. 还可以支持其他请求类型.
 */
public class SecretKeyCallback implements Callback {

    private final Request request;
    private SecretKey key;

    public SecretKeyCallback(Request request) {
        this.request = request;
    }

    public Request getRequest() {
        return request;
    }

    public void setKey(SecretKey key) {
        this.key = key;
    }

    public SecretKey getKey() {
        return key;
    }

    public static interface Request {
    }

    public static class AliasRequest implements Request {

        private final String alias;

        public AliasRequest(String alias) {
            this.alias = alias;
        }

        public String getAlias() {
            return alias;
        }
    }
}
