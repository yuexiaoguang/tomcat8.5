package org.apache.tomcat.util.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 包装{@link MessageDigest}的线程安全的包装器, 它不使用ThreadLocal, 并且一般只创建足够的MessageDigest对象来满足并发性要求.
 */
public class ConcurrentMessageDigest {

    private static final String MD5 = "MD5";
    private static final String SHA1 = "SHA-1";

    private static final Map<String,Queue<MessageDigest>> queues =
            new HashMap<>();


    private ConcurrentMessageDigest() {
        // Hide default constructor for this utility class
    }

    static {
        try {
            // init常用算法
            init(MD5);
            init(SHA1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static byte[] digestMD5(byte[]... input) {
        return digest(MD5, input);
    }

    public static byte[] digestSHA1(byte[]... input) {
        return digest(SHA1, input);
    }

    public static byte[] digest(String algorithm, byte[]... input) {
        return digest(algorithm, 1, input);
    }


    public static byte[] digest(String algorithm, int rounds, byte[]... input) {

        Queue<MessageDigest> queue = queues.get(algorithm);
        if (queue == null) {
            throw new IllegalStateException("Must call init() first");
        }

        MessageDigest md = queue.poll();
        if (md == null) {
            try {
                md = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                // Ignore. 如果已成功调用了 init(), 则不可能.
                throw new IllegalStateException("Must call init() first");
            }
        }

        // Round 1
        for (byte[] bytes : input) {
            md.update(bytes);
        }
        byte[] result = md.digest();

        // Subsequent rounds
        if (rounds > 1) {
            for (int i = 1; i < rounds; i++) {
                md.update(result);
                result = md.digest();
            }
        }

        queue.add(md);

        return result;
    }


    /**
     * 确保 {@link #digest(String, byte[][])} 将支持指定的算法.
     * 必须调用此方法并成功返回, 在使用 {@link #digest(String, byte[][])} 之前.
     *
     * @param algorithm 要支持的消息摘要算法
     *
     * @throws NoSuchAlgorithmException 如果JVM不支持该算法
     */
    public static void init(String algorithm) throws NoSuchAlgorithmException {
        synchronized (queues) {
            if (!queues.containsKey(algorithm)) {
                MessageDigest md = MessageDigest.getInstance(algorithm);
                Queue<MessageDigest> queue = new ConcurrentLinkedQueue<>();
                queue.add(md);
                queues.put(algorithm, queue);
            }
        }
    }
}
