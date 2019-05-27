package org.apache.tomcat.util.buf;

/**
 * 该类实现了一些基本的ASCII字符处理函数.
 */
public final class Ascii {
    /*
     * 字符转换表.
     */
    private static final byte[] toLower = new byte[256];

    /*
     * 字符类型表.
     */
    private static final boolean[] isDigit = new boolean[256];

    private static final long OVERFLOW_LIMIT = Long.MAX_VALUE / 10;

    /*
     * 初始化字符转换和类型表.
     */
    static {
        for (int i = 0; i < 256; i++) {
            toLower[i] = (byte)i;
        }

        for (int lc = 'a'; lc <= 'z'; lc++) {
            int uc = lc + 'A' - 'a';

            toLower[uc] = (byte)lc;
        }

        for (int d = '0'; d <= '9'; d++) {
            isDigit[d] = true;
        }
    }

    /**
     * 返回指定ASCII字符的小写等效项.
     * @param c 字符
     * @return 小写等效字符
     */
    public static int toLower(int c) {
        return toLower[c & 0xff] & 0xff;
    }

    /**
     * @return <code>true</code> 如果指定的ASCII字符是数字.
     * @param c The char
     */
    private static boolean isDigit(int c) {
        return isDigit[c & 0xff];
    }

    /**
     * 从指定的字节子数组中解析无符号Long.
     * 
     * @param b 要解析的字节数
     * @param off 字节的起始偏移量
     * @param len 字节的长度
     * 
     * @return the long value
     * @exception NumberFormatException 如果 long 格式无效
     */
    public static long parseLong(byte[] b, int off, int len)
        throws NumberFormatException
    {
        int c;

        if (b == null || len <= 0 || !isDigit(c = b[off++])) {
            throw new NumberFormatException();
        }

        long n = c - '0';
        while (--len > 0) {
            if (isDigit(c = b[off++]) &&
                    (n < OVERFLOW_LIMIT || (n == OVERFLOW_LIMIT && (c - '0') < 8))) {
                n = n * 10 + c - '0';
            } else {
                throw new NumberFormatException();
            }
        }

        return n;
    }
}
