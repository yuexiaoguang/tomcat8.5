package org.apache.catalina.realm;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

/**
 * 这个凭据处理器支持以下形式的密码:
 * <ul>
 * <li><b>encodedCredential</b> - 使用配置的算法编码的十六进制密码</li>
 * <li><b>{MD5}encodedCredential</b> - 一个BASE64编码的MD5密码摘要</li>
 * <li><b>{SHA}encodedCredential</b> - 一个BASE64编码的SHA1密码摘要</li>
 * <li><b>{SSHA}encodedCredential</b> - 20 character salt followed by the salted
 *     SHA1 digest Base64 encoded</li>
 * <li><b>salt$iterationCount$encodedCredential</b> - a hex encoded salt,
 *     iteration code and a hex encoded credential, each separated by $</li>
 * </ul>
 *
 * <p>
 * 如果存储的密码格式不包括迭代计数，则使用迭代计数1.
 * <p>
 * 如果存储的密码不包括salt, 不使用salt.
 */
public class MessageDigestCredentialHandler extends DigestCredentialHandlerBase {

    private static final Log log = LogFactory.getLog(MessageDigestCredentialHandler.class);

    public static final int DEFAULT_ITERATIONS = 1;

    private Charset encoding = StandardCharsets.UTF_8;
    private String algorithm = null;


    public String getEncoding() {
        return encoding.name();
    }


    public void setEncoding(String encodingName) {
        if (encodingName == null) {
            encoding = StandardCharsets.UTF_8;
        } else {
            try {
                this.encoding = B2CConverter.getCharset(encodingName);
            } catch (UnsupportedEncodingException e) {
                log.warn(sm.getString("mdCredentialHandler.unknownEncoding",
                        encodingName, encoding.name()));
            }
        }
    }


    @Override
    public String getAlgorithm() {
        return algorithm;
    }


    @Override
    public void setAlgorithm(String algorithm) throws NoSuchAlgorithmException {
        ConcurrentMessageDigest.init(algorithm);
        this.algorithm = algorithm;
    }


    @Override
    public boolean matches(String inputCredentials, String storedCredentials) {

        if (inputCredentials == null || storedCredentials == null) {
            return false;
        }

        if (getAlgorithm() == null) {
            // 没有加密, 直接比较
            return storedCredentials.equals(inputCredentials);
        } else {
            // 一些目录和数据库使用哈希类型给密码加前缀. 字符串使用和Base64.encode兼容的格式，不是常见的十六进制编码
            if (storedCredentials.startsWith("{MD5}") ||
                    storedCredentials.startsWith("{SHA}")) {
                // Server正在保存加密后的密码，使用一个前缀表示其加密类型
                String serverDigest = storedCredentials.substring(5);
                String userDigest = Base64.encodeBase64String(ConcurrentMessageDigest.digest(
                        getAlgorithm(), inputCredentials.getBytes(StandardCharsets.ISO_8859_1)));
                return userDigest.equals(serverDigest);

            } else if (storedCredentials.startsWith("{SSHA}")) {
                // Server正在保存加密后的密码，使用一个前缀表示其加密类型和创建时使用的salt

                String serverDigestPlusSalt = storedCredentials.substring(6);

                // Need to convert the salt to bytes to apply it to the user's
                // digested password.
                byte[] serverDigestPlusSaltBytes =
                        Base64.decodeBase64(serverDigestPlusSalt);
                final int saltPos = 20;
                byte[] serverDigestBytes = new byte[saltPos];
                System.arraycopy(serverDigestPlusSaltBytes, 0,
                        serverDigestBytes, 0, saltPos);
                final int saltLength = serverDigestPlusSaltBytes.length - saltPos;
                byte[] serverSaltBytes = new byte[saltLength];
                System.arraycopy(serverDigestPlusSaltBytes, saltPos,
                        serverSaltBytes, 0, saltLength);

                // Generate the digested form of the user provided password
                // using the salt
                byte[] userDigestBytes = ConcurrentMessageDigest.digest(getAlgorithm(),
                        inputCredentials.getBytes(StandardCharsets.ISO_8859_1),
                        serverSaltBytes);

                return Arrays.equals(userDigestBytes, serverDigestBytes);

            } else if (storedCredentials.indexOf('$') > -1) {
                return matchesSaltIterationsEncoded(inputCredentials, storedCredentials);

            } else {
                // 十六进制编码不区分大小写
                String userDigest = mutate(inputCredentials, null, 1);
                if (userDigest == null) {
                    // 无法更改用户凭据. 自动故障.
                    // Root cause should be logged by mutate()
                    return false;
                }
                return storedCredentials.equalsIgnoreCase(userDigest);
            }
        }
    }


    @Override
    protected String mutate(String inputCredentials, byte[] salt, int iterations) {
        if (algorithm == null) {
            return inputCredentials;
        } else {
            byte[] userDigest;
            if (salt == null) {
                userDigest = ConcurrentMessageDigest.digest(algorithm, iterations,
                        inputCredentials.getBytes(encoding));
            } else {
                userDigest = ConcurrentMessageDigest.digest(algorithm, iterations,
                        salt, inputCredentials.getBytes(encoding));
            }
            return HexUtils.toHexString(userDigest);
        }
    }


    @Override
    protected int getDefaultIterations() {
        return DEFAULT_ITERATIONS;
    }


    @Override
    protected Log getLog() {
        return log;
    }
}
