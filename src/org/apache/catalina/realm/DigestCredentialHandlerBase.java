package org.apache.catalina.realm;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import org.apache.catalina.CredentialHandler;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Tomcat提供的 {@link CredentialHandler}的基础实现类.
 */
public abstract class DigestCredentialHandlerBase implements CredentialHandler {

    protected static final StringManager sm =
            StringManager.getManager(DigestCredentialHandlerBase.class);

    public static final int DEFAULT_SALT_LENGTH = 32;

    private int iterations = getDefaultIterations();
    private int saltLength = getDefaultSaltLength();
    private final Object randomLock = new Object();
    private volatile Random random = null;
    private boolean logInvalidStoredCredentials = false;


    /**
     * @return 在为给定输入凭据创建新存储凭据时, 所使用的关联算法的迭代次数.
     */
    public int getIterations() {
        return iterations;
    }


    /**
     * 在为给定输入凭据创建新存储凭据时, 所使用的关联算法的迭代次数.
     * 
     * @param iterations 迭代次数
     */
    public void setIterations(int iterations) {
        this.iterations = iterations;
    }


    /**
     * @return 在为给定输入凭据创建新存储凭据时使用的saltLength.
     */
    public int getSaltLength() {
        return saltLength;
    }


    /**
     * 在为给定输入凭据创建新存储凭据时使用的saltLength.
     * 
     * @param saltLength the salt length
     */
    public void setSaltLength(int saltLength) {
        this.saltLength = saltLength;
    }


    /**
     * 在检查存储凭据的输入凭据时，如果发现了无效存储凭据，是否记录警告消息?
     * 
     * @return <code>true</code>如果发生日志记录
     */
    public boolean getLogInvalidStoredCredentials() {
        return logInvalidStoredCredentials;
    }


    /**
     * 在检查存储凭据的输入凭据时，如果发现了无效存储凭据，是否记录警告消息?
     * 
     * @param logInvalidStoredCredentials <code>true</code>记录, 默认<code>false</code>
     */
    public void setLogInvalidStoredCredentials(boolean logInvalidStoredCredentials) {
        this.logInvalidStoredCredentials = logInvalidStoredCredentials;
    }


    @Override
    public String mutate(String userCredential) {
        byte[] salt = null;
        int iterations = getIterations();
        int saltLength = getSaltLength();
        if (saltLength == 0) {
            salt = new byte[0];
        } else if (saltLength > 0) {
            // 双重锁检查. 因为random 是 volatile.
            if (random == null) {
                synchronized (randomLock) {
                    if (random == null) {
                        random = new SecureRandom();
                    }
                }
            }
            salt = new byte[saltLength];
            // 这种随机的并发使用不太可能是性能问题，因为它只在存储的密码生成中使用.
            random.nextBytes(salt);
        }

        String serverCredential = mutate(userCredential, salt, iterations);

        // 无法从用户凭据生成服务器凭据. 指向配置问题. 根异常已经在mutate()方法中记录.
        if (serverCredential == null) {
            return null;
        }

        if (saltLength == 0 && iterations == 1) {
            // 输出简单/旧格式的向后兼容性
            return serverCredential;
        } else {
            StringBuilder result =
                    new StringBuilder((saltLength << 1) + 10 + serverCredential.length() + 2);
            result.append(HexUtils.toHexString(salt));
            result.append('$');
            result.append(iterations);
            result.append('$');
            result.append(serverCredential);

            return result.toString();
        }
    }


    /**
     * 检查所提供的凭据是否与存储的凭据匹配, 当保存的凭据的格式是 salt$iteration-count$credential
     *
     * @param inputCredentials  输入凭据
     * @param storedCredentials 保存的凭据
     *
     * @return <code>true</code>如果匹配, 否则<code>false</code>
     */
    protected boolean matchesSaltIterationsEncoded(String inputCredentials,
            String storedCredentials) {

        if (storedCredentials == null) {
            // 存储的凭据无效
            // 如果嵌套的证书处理程序正在使用，这可能是预期的
            logInvalidStoredCredentials(storedCredentials);
            return false;
        }

        int sep1 = storedCredentials.indexOf('$');
        int sep2 = storedCredentials.indexOf('$', sep1 + 1);

        if (sep1 < 0 || sep2 < 0) {
            // 存储的凭据无效
            // 如果嵌套的证书处理程序正在使用，这可能是预期的
            logInvalidStoredCredentials(storedCredentials);
            return false;
        }

        String hexSalt = storedCredentials.substring(0,  sep1);

        int iterations = Integer.parseInt(storedCredentials.substring(sep1 + 1, sep2));

        String storedHexEncoded = storedCredentials.substring(sep2 + 1);
        byte[] salt;
        try {
            salt = HexUtils.fromHexString(hexSalt);
        } catch (IllegalArgumentException iae) {
            logInvalidStoredCredentials(storedCredentials);
            return false;
        }

        String inputHexEncoded = mutate(inputCredentials, salt, iterations,
                HexUtils.fromHexString(storedHexEncoded).length * Byte.SIZE);
        if (inputHexEncoded == null) {
            // 无法更改用户凭据. 自动故障. 根异常应该通过 mutate() 记录
            return false;
        }

        return storedHexEncoded.equalsIgnoreCase(inputHexEncoded);
    }


    private void logInvalidStoredCredentials(String storedCredentials) {
        if (logInvalidStoredCredentials) {
            // 日志凭据可能是安全问题，但它们是无效的，这可能是一个更大的问题
            getLog().warn(sm.getString("credentialHandler.invalidStoredCredential",
                    storedCredentials));
        }
    }


    /**
     * @return {@link CredentialHandler}使用的默认的salt length.
     */
    protected int getDefaultSaltLength() {
        return DEFAULT_SALT_LENGTH;
    }


    /**
     * 为给定的输入凭据、salt和迭代次数生成等效的存储凭据. 如果算法需要一个密钥长度, 将使用默认的.
     *
     * @param inputCredentials  用户提供的凭证
     * @param salt              Salt
     * @param iterations        这个CredentialHandler关联的算法的迭代次数, 应用于inputCredentials以生成等效的存储凭据
     *
     * @return  给定输入凭据的等效存储凭据, 或<code>null</code>如果生成失败
     */
    protected abstract String mutate(String inputCredentials, byte[] salt, int iterations);


    /**
     * 为给定的输入凭据, salt, 迭代次数, 秘钥长度生成等效的存储凭据. 默认实现忽略秘钥长度, 并调用{@link #mutate(String, byte[], int)}.
     * 使用密钥长度的子类应该重写此方法.
     *
     * @param inputCredentials  用户提供的凭证
     * @param salt              Salt
     * @param iterations        这个CredentialHandler关联的算法的迭代次数, 应用于inputCredentials以生成等效的存储凭据
     * @param keyLength         所生成的摘要的位长
     *
     * @return  给定输入凭据的等效存储凭据, 或<code>null</code>如果生成失败
     */
    protected String mutate(String inputCredentials, byte[] salt, int iterations, int keyLength) {
        return mutate(inputCredentials, salt, iterations);
    }


    /**
     * 设置用于将输入凭据转换为存储凭据的算法.
     * 
     * @param algorithm 算法
     * @throws NoSuchAlgorithmException 如果不支持指定的算法
     */
    public abstract void setAlgorithm(String algorithm) throws NoSuchAlgorithmException;


    /**
     * @return 用于将输入凭据转换为存储凭据的算法.
     */
    public abstract String getAlgorithm();


    /**
     * @return {@link CredentialHandler}使用的默认迭代次数.
     */
    protected abstract int getDefaultIterations();


    /**
     * @return CredentialHandler实例的logger.
     */
    protected abstract Log getLog();
}
