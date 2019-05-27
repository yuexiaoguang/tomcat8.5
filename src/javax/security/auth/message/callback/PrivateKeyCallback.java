package javax.security.auth.message.callback;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import javax.security.auth.callback.Callback;
import javax.security.auth.x500.X500Principal;

/**
 * 回调，允许身份验证模块从运行时间请求证书链和私钥. 指定链和键的信息可以是别名、摘要、主题键或发行者ID. 可以支持其他请求类型.
 */
public class PrivateKeyCallback implements Callback {

    private final Request request;
    private Certificate[] chain;
    private PrivateKey key;

    public PrivateKeyCallback(Request request) {
        this.request = request;
    }

    public Request getRequest() {
        return request;
    }

    public void setKey(PrivateKey key, Certificate[] chain) {
        this.key = key;
        this.chain = chain;
    }

    public PrivateKey getKey() {
        return key;
    }

    public Certificate[] getChain() {
        return chain;
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

    public static class DigestRequest implements Request {
        private final byte[] digest;
        private final String algorithm;

        public DigestRequest(byte[] digest, String algorithm) {
            this.digest = digest;
            this.algorithm = algorithm;
        }

        public byte[] getDigest() {
            return digest;
        }

        public String getAlgorithm() {
            return algorithm;
        }
    }

    public static class SubjectKeyIDRequest implements Request {

        private final byte[] subjectKeyID;

        public SubjectKeyIDRequest(byte[] subjectKeyID) {
            this.subjectKeyID = subjectKeyID;
        }

        public byte[] getSubjectKeyID() {
            return subjectKeyID;
        }
    }

    public static class IssuerSerialNumRequest implements Request {
        private final X500Principal issuer;
        private final BigInteger serialNum;

        public IssuerSerialNumRequest(X500Principal issuer, BigInteger serialNum) {
            this.issuer = issuer;
            this.serialNum = serialNum;
        }

        public X500Principal getIssuer() {
            return issuer;
        }

        public BigInteger getSerialNum() {
            return serialNum;
        }
    }
}
