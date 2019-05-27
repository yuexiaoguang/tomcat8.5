package org.apache.tomcat.util.buf;

import org.apache.tomcat.util.res.StringManager;

/**
 * 将字节数组转换为十六进制数字字符串, 从十六进制数字字符串转换.
 * Code from Ajp11, from Apache's JServ.
 */
public final class HexUtils {

    private static final StringManager sm =
            StringManager.getManager(Constants.Package);

    // -------------------------------------------------------------- Constants

    /**
     *  HEX到DEC字节转换的表.
     */
    private static final int[] DEC = {
        00, 01, 02, 03, 04, 05, 06, 07,  8,  9, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15,
    };


    /**
     * DEC到HEX字节转换的表.
     */
    private static final byte[] HEX =
    { (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5',
      (byte) '6', (byte) '7', (byte) '8', (byte) '9', (byte) 'a', (byte) 'b',
      (byte) 'c', (byte) 'd', (byte) 'e', (byte) 'f' };


    /**
     * 字节到十六进制字符串转换的表.
     */
    private static final char[] hex = "0123456789abcdef".toCharArray();


    // --------------------------------------------------------- Static Methods

    public static int getDec(int index) {
        // 快速获取正确的值, 不正确的
        try {
            return DEC[index - '0'];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return -1;
        }
    }


    public static byte getHex(int index) {
        return HEX[index];
    }


    public static String toHexString(byte[] bytes) {
        if (null == bytes) {
            return null;
        }

        StringBuilder sb = new StringBuilder(bytes.length << 1);

        for(int i = 0; i < bytes.length; ++i) {
            sb.append(hex[(bytes[i] & 0xf0) >> 4])
                .append(hex[(bytes[i] & 0x0f)])
                ;
        }

        return sb.toString();
    }


    public static byte[] fromHexString(String input) {
        if (input == null) {
            return null;
        }

        if ((input.length() & 1) == 1) {
            // 奇数个字符
            throw new IllegalArgumentException(sm.getString("hexUtils.fromHex.oddDigits"));
        }

        char[] inputChars = input.toCharArray();
        byte[] result = new byte[input.length() >> 1];
        for (int i = 0; i < result.length; i++) {
            int upperNibble = getDec(inputChars[2*i]);
            int lowerNibble =  getDec(inputChars[2*i + 1]);
            if (upperNibble < 0 || lowerNibble < 0) {
                // 非十六进制字符
                throw new IllegalArgumentException(sm.getString("hexUtils.fromHex.nonHex"));
            }
            result[i] = (byte) ((upperNibble << 4) + lowerNibble);
        }
        return result;
    }
}
