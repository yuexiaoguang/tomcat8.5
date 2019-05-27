package org.apache.tomcat.util.security;

/**
 * 将MD5摘要编码为字符串.
 * <p>
 * 128位MD5哈希被转换成32个字符的长字符串. 字符串的每个字符是摘要的4位的十六进制表示.
 */
public final class MD5Encoder {


    private MD5Encoder() {
        // Hide default constructor for utility class
    }


    private static final char[] hexadecimal = {'0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};


    /**
     * 将128位（16字节）MD5编码为32字符串.
     *
     * @param binaryData 包含摘要的数组
     *
     * @return 编码的MD5, 或 null 如果编码失败
     */
    public static String encode(byte[] binaryData) {

        if (binaryData.length != 16)
            return null;

        char[] buffer = new char[32];

        for (int i=0; i<16; i++) {
            int low = binaryData[i] & 0x0f;
            int high = (binaryData[i] & 0xf0) >> 4;
            buffer[i*2] = hexadecimal[high];
            buffer[i*2 + 1] = hexadecimal[low];
        }

        return new String(buffer);
    }
}

