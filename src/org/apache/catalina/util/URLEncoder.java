package org.apache.catalina.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.BitSet;

import org.apache.tomcat.util.buf.B2CConverter;

/**
 * 这个类和java.net.URLEncoder非常相似.
 *
 * 不幸的是, java.net.URLEncoder 没有办法指定哪些字符不应该被编码.
 *
 * 这些代码从 DefaultServlet.java移动过来的
 */
public class URLEncoder implements Cloneable {

    private static final char[] hexadecimal =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static final URLEncoder DEFAULT = new URLEncoder();
    public static final URLEncoder QUERY = new URLEncoder();

    static {
        /*
         * URI路径的Encoder, 因此从规范:
         *
         * pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
         *
         * unreserved  = ALPHA / DIGIT / "-" / "." / "_" / "~"
         *
         * sub-delims = "!" / "$" / "&" / "'" / "(" / ")"
         *              / "*" / "+" / "," / ";" / "="
         */
        // ALPHA 和 DIGIT 总是被当作安全的角色
        // 添加剩余的未保留字符
        DEFAULT.addSafeCharacter('-');
        DEFAULT.addSafeCharacter('.');
        DEFAULT.addSafeCharacter('_');
        DEFAULT.addSafeCharacter('~');
        // Add the sub-delims
        DEFAULT.addSafeCharacter('!');
        DEFAULT.addSafeCharacter('$');
        DEFAULT.addSafeCharacter('&');
        DEFAULT.addSafeCharacter('\'');
        DEFAULT.addSafeCharacter('(');
        DEFAULT.addSafeCharacter(')');
        DEFAULT.addSafeCharacter('*');
        DEFAULT.addSafeCharacter('+');
        DEFAULT.addSafeCharacter(',');
        DEFAULT.addSafeCharacter(';');
        DEFAULT.addSafeCharacter('=');
        // Add the remaining literals
        DEFAULT.addSafeCharacter(':');
        DEFAULT.addSafeCharacter('@');
        // Add '/' 所以在编码路径时没有编码
        DEFAULT.addSafeCharacter('/');

        /*
         * 查询字符串的Encoder
         * https://www.w3.org/TR/html5/forms.html#application/x-www-form-urlencoded-encoding-algorithm
         * 0x20 ' ' -> '+'
         * 0x2A, 0x2D, 0x2E, 0x30 to 0x39, 0x41 to 0x5A, 0x5F, 0x61 to 0x7A as-is
         * '*',  '-',  '.',  '0'  to '9',  'A'  to 'Z',  '_',  'a'  to 'z'
         * Also '=' and '&' are not encoded
         * Everything else %nn encoded
         */
        // 空格的特殊编码
        QUERY.setEncodeSpaceAsPlus(true);
        // 默认情况下，字母和数字是安全的
        // 添加其他允许的字符
        QUERY.addSafeCharacter('*');
        QUERY.addSafeCharacter('-');
        QUERY.addSafeCharacter('.');
        QUERY.addSafeCharacter('_');
        QUERY.addSafeCharacter('=');
        QUERY.addSafeCharacter('&');
    }

    //Array containing the safe characters set.
    private final BitSet safeCharacters;

    private boolean encodeSpaceAsPlus = false;


    public URLEncoder() {
        this(new BitSet(256));

        for (char i = 'a'; i <= 'z'; i++) {
            addSafeCharacter(i);
        }
        for (char i = 'A'; i <= 'Z'; i++) {
            addSafeCharacter(i);
        }
        for (char i = '0'; i <= '9'; i++) {
            addSafeCharacter(i);
        }
    }


    private URLEncoder(BitSet safeCharacters) {
        this.safeCharacters = safeCharacters;
    }


    public void addSafeCharacter(char c) {
        safeCharacters.set(c);
    }


    public void removeSafeCharacter(char c) {
        safeCharacters.clear(c);
    }


    public void setEncodeSpaceAsPlus(boolean encodeSpaceAsPlus) {
        this.encodeSpaceAsPlus = encodeSpaceAsPlus;
    }


    /**
     * 对所提供的路径进行URL编码, 使用UTF-8.
     *
     * @param path 要编码的路径
     *
     * @return The encoded path
     *
     * @deprecated Use {@link #encode(String, String)}
     */
    @Deprecated
    public String encode(String path) {
        return encode(path, "UTF-8");
    }


    /**
     * 对所提供的路径进行URL编码, 使用给定的编码.
     *
     * @param path      要编码的路径
     * @param encoding  用于将路径转换为字节的编码
     *
     * @return The encoded path
     *
     * @deprecated This will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public String encode(String path, String encoding) {
        Charset charset;
        try {
            charset = B2CConverter.getCharset(encoding);
        } catch (UnsupportedEncodingException e) {
            charset = Charset.defaultCharset();
        }
        return encode(path, charset);
    }


    /**
     * URL使用给定的字符集对所提供的路径进行编码.
     *
     * @param path      要编码的路径
     * @param charset   用于将路径转换为字节的字符集
     *
     * @return The encoded path
     */
    public String encode(String path, Charset charset) {

        int maxBytesPerChar = 10;
        StringBuilder rewrittenPath = new StringBuilder(path.length());
        ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);
        OutputStreamWriter writer = new OutputStreamWriter(buf, charset);

        for (int i = 0; i < path.length(); i++) {
            int c = path.charAt(i);
            if (safeCharacters.get(c)) {
                rewrittenPath.append((char)c);
            } else if (encodeSpaceAsPlus && c == ' ') {
                rewrittenPath.append('+');
            } else {
                // 十六进制转换前转换为外部编码
                try {
                    writer.write((char)c);
                    writer.flush();
                } catch(IOException e) {
                    buf.reset();
                    continue;
                }
                byte[] ba = buf.toByteArray();
                for (int j = 0; j < ba.length; j++) {
                    // 转换缓冲区中的每个字节
                    byte toEncode = ba[j];
                    rewrittenPath.append('%');
                    int low = toEncode & 0x0f;
                    int high = (toEncode & 0xf0) >> 4;
                    rewrittenPath.append(hexadecimal[high]);
                    rewrittenPath.append(hexadecimal[low]);
                }
                buf.reset();
            }
        }
        return rewrittenPath.toString();
    }


    @Override
    public Object clone() {
        URLEncoder result = new URLEncoder((BitSet) safeCharacters.clone());
        result.setEncodeSpaceAsPlus(encodeSpaceAsPlus);
        return result;
    }
}
