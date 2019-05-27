package org.apache.catalina.realm;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.HexUtils;

public class SecretKeyCredentialHandler extends DigestCredentialHandlerBase {

    private static final Log log = LogFactory.getLog(SecretKeyCredentialHandler.class);

    public static final String DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA1";
    public static final int DEFAULT_KEY_LENGTH = 160;
    public static final int DEFAULT_ITERATIONS = 20000;


    private SecretKeyFactory secretKeyFactory;
    private int keyLength = DEFAULT_KEY_LENGTH;


    public SecretKeyCredentialHandler() throws NoSuchAlgorithmException {
        setAlgorithm(DEFAULT_ALGORITHM);
    }


    @Override
    public String getAlgorithm() {
        return secretKeyFactory.getAlgorithm();
    }


    @Override
    public void setAlgorithm(String algorithm) throws NoSuchAlgorithmException {
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
        this.secretKeyFactory = secretKeyFactory;
    }


    public int getKeyLength() {
        return keyLength;
    }


    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }


    @Override
    public boolean matches(String inputCredentials, String storedCredentials) {
        return matchesSaltIterationsEncoded(inputCredentials, storedCredentials);
    }


    @Override
    protected String mutate(String inputCredentials, byte[] salt, int iterations) {
        return mutate(inputCredentials, salt, iterations, getKeyLength());
    }


    @Override
    protected String mutate(String inputCredentials, byte[] salt, int iterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(inputCredentials.toCharArray(), salt, iterations, keyLength);
            return HexUtils.toHexString(secretKeyFactory.generateSecret(spec).getEncoded());
        } catch (InvalidKeySpecException | IllegalArgumentException e) {
            log.warn(sm.getString("pbeCredentialHandler.invalidKeySpec"), e);
            return null;
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
