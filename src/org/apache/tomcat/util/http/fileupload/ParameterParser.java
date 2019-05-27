package org.apache.tomcat.util.http.fileupload;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.http.fileupload.util.mime.MimeUtility;

/**
 * 一个简单的解析器，用于解析名称/值对的序列.
 *
 * 如果参数值包含不安全的字符, 则它们应包含在引号中, 例如 '=' 字符或分隔符.
 * 参数值是可选的，可以省略.
 *
 * <p>
 *  <code>param1 = value; param2 = "anything goes; really"; param3</code>
 * </p>
 */
public class ParameterParser {

    /**
     * 要解析的字符串.
     */
    private char[] chars = null;

    /**
     * 字符串中的当前位置.
     */
    private int pos = 0;

    /**
     * 字符串中的最大位置.
     */
    private int len = 0;

    /**
     * token的开始.
     */
    private int i1 = 0;

    /**
     * token的结束.
     */
    private int i2 = 0;

    /**
     * 是否应将存储在 Map 中的名称转换为小写.
     */
    private boolean lowerCaseNames = false;

    public ParameterParser() {
        super();
    }

    /**
     * 是否还有字符需要解析?
     *
     * @return {@code true} 如果有未解析的字符,
     *         {@code false} 否则.
     */
    private boolean hasChar() {
        return this.pos < this.len;
    }

    /**
     * 处理解析后的 token 的辅助方法. 此方法删除前导和尾随空格以及包含的引号.
     *
     * @param quoted {@code true} 如果是引号,
     *               {@code false} 否则.
     * @return the token
     */
    private String getToken(boolean quoted) {
        // Trim leading white spaces
        while ((i1 < i2) && (Character.isWhitespace(chars[i1]))) {
            i1++;
        }
        // Trim trailing white spaces
        while ((i2 > i1) && (Character.isWhitespace(chars[i2 - 1]))) {
            i2--;
        }
        // Strip away quotation marks if necessary
        if (quoted
            && ((i2 - i1) >= 2)
            && (chars[i1] == '"')
            && (chars[i2 - 1] == '"')) {
            i1++;
            i2--;
        }
        String result = null;
        if (i2 > i1) {
            result = new String(chars, i1, i2 - i1);
        }
        return result;
    }

    /**
     * 测试字符数组中是否存在给定字符.
     *
     * @param ch 要测试字符数组中是否存在的字符
     * @param charray 要测试的字符数组
     *
     * @return {@code true} 如果字符存在于字符数组中, 否则{@code false}.
     */
    private boolean isOneOf(char ch, final char[] charray) {
        boolean result = false;
        for (char element : charray) {
            if (ch == element) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * 解析一个Token，直到遇到任何给定的终结符.
     *
     * @param terminators 终止字符数组. 遇到其中任何字符都表示Token的结束
     *
     * @return the token
     */
    private String parseToken(final char[] terminators) {
        char ch;
        i1 = pos;
        i2 = pos;
        while (hasChar()) {
            ch = chars[pos];
            if (isOneOf(ch, terminators)) {
                break;
            }
            i2++;
            pos++;
        }
        return getToken(false);
    }

    /**
     * 解析一个Token，直到在引号外遇到任何给定的终止符.
     *
     * @param terminators 终止字符数组. 在引号外遇到的其中任何字符都表示Token的结束
     *
     * @return the token
     */
    private String parseQuotedToken(final char[] terminators) {
        char ch;
        i1 = pos;
        i2 = pos;
        boolean quoted = false;
        boolean charEscaped = false;
        while (hasChar()) {
            ch = chars[pos];
            if (!quoted && isOneOf(ch, terminators)) {
                break;
            }
            if (!charEscaped && ch == '"') {
                quoted = !quoted;
            }
            charEscaped = (!charEscaped && ch == '\\');
            i2++;
            pos++;

        }
        return getToken(true);
    }

    /**
     * 如果在解析name/value对时将参数名称转换为小写，则返回{@code true}.
     *
     * @return {@code true} 如果在解析name/value对时将参数名称转换为小写.
     * 否则返回 {@code false}
     */
    public boolean isLowerCaseNames() {
        return this.lowerCaseNames;
    }

    /**
     * 在解析name/value对时, 是否将参数名称转换为小写.
     *
     * @param b {@code true} 如果在解析name/value对时将参数名称转换为小写. 否则 {@code false}.
     */
    public void setLowerCaseNames(boolean b) {
        this.lowerCaseNames = b;
    }

    /**
     * 从给定字符串中提取name/value对的Map.
     * 名称应该是唯一的. 可以指定多个分隔符，并使用输入字符串中最早的分隔符.
     *
     * @param str 包含一系列name/value对的字符串
     * @param separators name/value对分隔符
     *
     * @return name/value对的Map
     */
    public Map<String,String> parse(final String str, char[] separators) {
        if (separators == null || separators.length == 0) {
            return new HashMap<>();
        }
        char separator = separators[0];
        if (str != null) {
            int idx = str.length();
            for (char separator2 : separators) {
                int tmp = str.indexOf(separator2);
                if (tmp != -1 && tmp < idx) {
                    idx = tmp;
                    separator = separator2;
                }
            }
        }
        return parse(str, separator);
    }

    /**
     * 从给定字符串中提取name/value 对的映射. 名称应该是唯一的.
     *
     * @param str 包含一系列name/value对的字符串
     * @param separator name/value对分隔符
     *
     * @return name/value对的Map
     */
    public Map<String,String> parse(final String str, char separator) {
        if (str == null) {
            return new HashMap<>();
        }
        return parse(str.toCharArray(), separator);
    }

    /**
     * 从给定的字符数组中提取name/value对的映射. 名称应该是唯一的.
     *
     * @param charArray 包含一系列name/value对的字符数组
     * @param separator name/value对分隔符
     *
     * @return name/value对的Map
     */
    public Map<String,String> parse(final char[] charArray, char separator) {
        if (charArray == null) {
            return new HashMap<>();
        }
        return parse(charArray, 0, charArray.length, separator);
    }

    /**
     * 从给定的字符数组中提取name/value对的映射. 名称应该是唯一的.
     *
     * @param charArray 包含一系列name/value对的字符数组
     * @param offset - 初始偏移量.
     * @param length - 长度.
     * @param separator name/value对分隔符
     *
     * @return name/value对的Map
     */
    public Map<String,String> parse(
        final char[] charArray,
        int offset,
        int length,
        char separator) {

        if (charArray == null) {
            return new HashMap<>();
        }
        HashMap<String,String> params = new HashMap<>();
        this.chars = charArray;
        this.pos = offset;
        this.len = length;

        String paramName = null;
        String paramValue = null;
        while (hasChar()) {
            paramName = parseToken(new char[] {
                    '=', separator });
            paramValue = null;
            if (hasChar() && (charArray[pos] == '=')) {
                pos++; // skip '='
                paramValue = parseQuotedToken(new char[] {
                        separator });

                if (paramValue != null) {
                    try {
                        paramValue = MimeUtility.decodeText(paramValue);
                    } catch (UnsupportedEncodingException e) {
                        // 在这种情况下，让我们保留原始值
                    }
                }
            }
            if (hasChar() && (charArray[pos] == separator)) {
                pos++; // skip separator
            }
            if ((paramName != null) && (paramName.length() > 0)) {
                if (this.lowerCaseNames) {
                    paramName = paramName.toLowerCase(Locale.ENGLISH);
                }

                params.put(paramName, paramValue);
            }
        }
        return params;
    }

}
