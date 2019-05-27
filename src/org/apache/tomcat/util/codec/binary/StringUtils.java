package org.apache.tomcat.util.codec.binary;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 使用Java规范要求的编码将字符串转换为字节和从字节转换.
 *
 * <p>这个类是不可变的和线程安全的.</p>
 */
public class StringUtils {

    /**
     * 调用 {@link String#getBytes(Charset)}
     *
     * @param string 要编码的字符串 (如果是 null, 返回 null).
     * @param charset 编码<code>String</code>使用的 {@link Charset}
     * 
     * @return 编码的字节
     */
    private static byte[] getBytes(final String string, final Charset charset) {
        if (string == null) {
            return null;
        }
        return string.getBytes(charset);
    }

    /**
     * 使用UTF-8字符集将给定字符串编码为字节序列, 将结果存储到新的字节数组中.
     *
     * @param string 要编码的字符串, 可能是<code>null</code>
     * 
     * @return 编码的字节, 或 <code>null</code> 如果输入字符串是<code>null</code>
     */
    public static byte[] getBytesUtf8(final String string) {
        return getBytes(string, StandardCharsets.UTF_8);
    }

    /**
     * 通过使用给定的字符集, 解码指定的字节数组, 构造一个新的<code>String</code>.
     *
     * @param bytes 要解码为字符的字节
     * @param charset  编码<code>String</code>使用的 {@link Charset}
     * 
     * @return 使用给定的字符集从指定的字节数组中解码的新<code>String</code>, 
     *         或<code>null</code> 如果输入字节数组是<code>null</code>.
     */
    private static String newString(final byte[] bytes, final Charset charset) {
        return bytes == null ? null : new String(bytes, charset);
    }

    /**
     * 通过使用UTF-8字符集解码指定的字节数组构造一个新的<code>String</code>.
     *
     * @param bytes 要解码为字符的字节
     * 
     * @return 使用UTF-8字符集从指定的字节数组中解码的新<code>String</code>,
     *         或<code>null</code> 如果输入字节数组是<code>null</code>.
     */
    public static String newStringUtf8(final byte[] bytes) {
        return newString(bytes, StandardCharsets.UTF_8);
    }
}
