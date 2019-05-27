package org.apache.catalina.tribes.util;

import java.security.SecureRandom;
import java.util.Random;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * UUID的生成器
 */
public class UUIDGenerator {
    private static final Log log = LogFactory.getLog(UUIDGenerator.class);
    protected static final StringManager sm =
        StringManager.getManager("org.apache.catalina.tribes.util");

    public static final int UUID_LENGTH = 16;
    public static final int UUID_VERSION = 4;
    public static final int BYTES_PER_INT = 4;
    public static final int BITS_PER_BYTE = 8;

    protected static final SecureRandom secrand;
    protected static final Random rand = new Random();

    static {
        long start = System.currentTimeMillis();
        secrand = new SecureRandom();
        // seed the generator
        secrand.nextInt();
        long time = System.currentTimeMillis() - start;
        if (time > 100) {
            log.info(sm.getString("uuidGenerator.createRandom",
                    secrand.getAlgorithm(), Long.valueOf(time)));
        }
    }

    public static byte[] randomUUID(boolean secure) {
        byte[] result = new byte[UUID_LENGTH];
        return randomUUID(secure,result,0);
    }

    public static byte[] randomUUID(boolean secure, byte[] into, int offset) {
        if ( (offset+UUID_LENGTH)>into.length )
            throw new ArrayIndexOutOfBoundsException(sm.getString("uuidGenerator.unable.fit",
                    Integer.toString(UUID_LENGTH), Integer.toString(into.length),
                    Integer.toString(offset+UUID_LENGTH)));
        Random r = (secure&&(secrand!=null))?secrand:rand;
        nextBytes(into,offset,UUID_LENGTH,r);
        into[6+offset] &= 0x0F;
        into[6+offset] |= (UUID_VERSION << 4);
        into[8+offset] &= 0x3F; //0011 1111
        into[8+offset] |= 0x80; //1000 0000
        return into;
    }

    /**
     * 等同于 java.util.Random.nextBytes, 除了不必分配一个新的字节数组
     * @param into byte[]
     * @param offset int
     * @param length int
     * @param r Random
     */
    public static void nextBytes(byte[] into, int offset, int length, Random r) {
        int numRequested = length;
        int numGot = 0, rnd = 0;
        while (true) {
            for (int i = 0; i < BYTES_PER_INT; i++) {
                if (numGot == numRequested) return;
                rnd = (i == 0 ? r.nextInt() : rnd >> BITS_PER_BYTE);
                into[offset+numGot] = (byte) rnd;
                numGot++;
            }
        }
    }

}