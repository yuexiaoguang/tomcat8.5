package org.apache.coyote.http2;

/**
 * 从字节数组中提取值的工具类.
 */
public class ByteUtil {

    private ByteUtil() {
        // Hide default constructor
    }


    public static boolean isBit7Set(byte input) {
        return (input & 0x80) != 0;
    }


    public static int get31Bits(byte[] input, int firstByte) {
        return ((input[firstByte] & 0x7F) << 24) + ((input[firstByte + 1] & 0xFF) << 16) +
                ((input[firstByte + 2] & 0xFF) << 8) + (input[firstByte + 3] & 0xFF);
    }


    public static void set31Bits(byte[] output, int firstByte, int value) {
        output[firstByte] = (byte) ((value & 0x7F000000) >> 24);
        output[firstByte + 1] = (byte) ((value & 0xFF0000) >> 16);
        output[firstByte + 2] = (byte) ((value & 0xFF00) >> 8);
        output[firstByte + 3] = (byte) (value & 0xFF);
    }


    public static int getOneByte(byte[] input, int pos) {
        return (input[pos] & 0xFF);
    }


    public static int getTwoBytes(byte[] input, int firstByte) {
        return ((input[firstByte] & 0xFF) << 8) +  (input[firstByte + 1] & 0xFF);
    }


    public static int getThreeBytes(byte[] input, int firstByte) {
        return ((input[firstByte] & 0xFF) << 16) + ((input[firstByte + 1] & 0xFF) << 8) +
                (input[firstByte + 2] & 0xFF);
    }


    public static void setOneBytes(byte[] output, int firstByte, int value) {
        output[firstByte] = (byte) (value & 0xFF);
    }


    public static void setTwoBytes(byte[] output, int firstByte, int value) {
        output[firstByte] = (byte) ((value & 0xFF00) >> 8);
        output[firstByte + 1] = (byte) (value & 0xFF);
    }


    public static void setThreeBytes(byte[] output, int firstByte, int value) {
        output[firstByte] = (byte) ((value & 0xFF0000) >> 16);
        output[firstByte + 1] = (byte) ((value & 0xFF00) >> 8);
        output[firstByte + 2] = (byte) (value & 0xFF);
    }


    public static long getFourBytes(byte[] input, int firstByte) {
        return ((long)(input[firstByte] & 0xFF) << 24) + ((input[firstByte + 1] & 0xFF) << 16) +
                ((input[firstByte + 2] & 0xFF) << 8) + (input[firstByte + 3] & 0xFF);
    }


    public static void setFourBytes(byte[] output, int firstByte, long value) {
        output[firstByte]     = (byte) ((value & 0xFF000000) >> 24);
        output[firstByte + 1] = (byte) ((value & 0xFF0000) >> 16);
        output[firstByte + 2] = (byte) ((value & 0xFF00) >> 8);
        output[firstByte + 3] = (byte) (value & 0xFF);
    }
}
