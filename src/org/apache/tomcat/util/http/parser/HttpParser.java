package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * HTTP header值解析器实现.
 * 根据RFC2616解析HTTP header并不总是像第一次出现那样简单. 对于仅使用token的header，简单方法通常就足够了.
 * 但是, 对于其它的header, 简单的代码符合99.9%的情况, 通常有一些边缘情况会使事情变得更加复杂.
 *
 * 这个解析器的目的是让解析器担心边缘情况. 它提供了容忍（在安全的情况下）解析HTTP header值, 假设包装的header行已经被解包.
 * (Tomcat header处理代码解包.)
 */
public class HttpParser {

    private static final StringManager sm = StringManager.getManager(HttpParser.class);

    private static final Log log = LogFactory.getLog(HttpParser.class);

    private static final int ARRAY_SIZE = 128;

    private static final boolean[] IS_CONTROL = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_SEPARATOR = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_TOKEN = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_HEX = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_NOT_REQUEST_TARGET = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_HTTP_PROTOCOL = new boolean[ARRAY_SIZE];
    private static final boolean[] REQUEST_TARGET_ALLOW = new boolean[ARRAY_SIZE];

    static {
        String prop = System.getProperty("tomcat.util.http.parser.HttpParser.requestTargetAllow");
        if (prop != null) {
            for (int i = 0; i < prop.length(); i++) {
                char c = prop.charAt(i);
                if (c == '{' || c == '}' || c == '|') {
                    REQUEST_TARGET_ALLOW[c] = true;
                } else {
                    log.warn(sm.getString("httpparser.invalidRequestTargetCharacter",
                            Character.valueOf(c)));
                }
            }
        }

        for (int i = 0; i < ARRAY_SIZE; i++) {
            // Control> 0-31, 127
            if (i < 32 || i == 127) {
                IS_CONTROL[i] = true;
            }

            // Separator
            if (    i == '(' || i == ')' || i == '<' || i == '>'  || i == '@'  ||
                    i == ',' || i == ';' || i == ':' || i == '\\' || i == '\"' ||
                    i == '/' || i == '[' || i == ']' || i == '?'  || i == '='  ||
                    i == '{' || i == '}' || i == ' ' || i == '\t') {
                IS_SEPARATOR[i] = true;
            }

            // Token: Anything 0-127 that is not a control and not a separator
            if (!IS_CONTROL[i] && !IS_SEPARATOR[i] && i < 128) {
                IS_TOKEN[i] = true;
            }

            // Hex: 0-9, a-f, A-F
            if ((i >= '0' && i <='9') || (i >= 'a' && i <= 'f') || (i >= 'A' && i <= 'F')) {
                IS_HEX[i] = true;
            }

            // 对请求目标无效.
            // RFC7230和RFC 3986的多个规则的组合. 必须是 ASCII, 没有控制加上一些额外的字符
            if (IS_CONTROL[i] || i > 127 ||
                    i == ' ' || i == '\"' || i == '#' || i == '<' || i == '>' || i == '\\' ||
                    i == '^' || i == '`'  || i == '{' || i == '|' || i == '}') {
                if (!REQUEST_TARGET_ALLOW[i]) {
                    IS_NOT_REQUEST_TARGET[i] = true;
                }
            }

            // 对HTTP协议无效
            // "HTTP/" DIGIT "." DIGIT
            if (i == 'H' || i == 'T' || i == 'P' || i == '/' || i == '.' || (i >= '0' && i <= '9')) {
                IS_HTTP_PROTOCOL[i] = true;
            }
        }
    }


    public static String unquote(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }

        int start;
        int end;

        // 如果有的话，请跳过周围的引号
        if (input.charAt(0) == '"') {
            start = 1;
            end = input.length() - 1;
        } else {
            start = 0;
            end = input.length();
        }

        StringBuilder result = new StringBuilder();
        for (int i = start ; i < end; i++) {
            char c = input.charAt(i);
            if (input.charAt(i) == '\\') {
                i++;
                result.append(input.charAt(i));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }


    public static boolean isToken(int c) {
        // 快速获取正确的值，对于不正确的值较慢
        try {
            return IS_TOKEN[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isHex(int c) {
        // 快速获取正确的值，对于某些不正确的值较慢
        try {
            return IS_HEX[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    public static boolean isNotRequestTarget(int c) {
        // 快速有效请求目标字符，对于某些不正确的字符较慢
        try {
            return IS_NOT_REQUEST_TARGET[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return true;
        }
    }


    public static boolean isHttpProtocol(int c) {
        // 快速有效的HTTP协议字符，对于某些不正确的字符较慢
        try {
            return IS_HTTP_PROTOCOL[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    // 跳过任何LWS并返回下一个字符
    static int skipLws(StringReader input, boolean withReset) throws IOException {

        if (withReset) {
            input.mark(1);
        }
        int c = input.read();

        while (c == 32 || c == 9 || c == 10 || c == 13) {
            if (withReset) {
                input.mark(1);
            }
            c = input.read();
        }

        if (withReset) {
            input.reset();
        }
        return c;
    }

    static SkipResult skipConstant(StringReader input, String constant) throws IOException {
        int len = constant.length();

        int c = skipLws(input, false);

        for (int i = 0; i < len; i++) {
            if (i == 0 && c == -1) {
                return SkipResult.EOF;
            }
            if (c != constant.charAt(i)) {
                input.skip(-(i + 1));
                return SkipResult.NOT_FOUND;
            }
            if (i != (len - 1)) {
                c = input.read();
            }
        }
        return SkipResult.FOUND;
    }

    /**
     * @return  如果找到一个token, 如果没有可用于读取的数据，则为空字符串; 如果找到除令牌以外的数据，则为<code>null</code>
     */
    static String readToken(StringReader input) throws IOException {
        StringBuilder result = new StringBuilder();

        int c = skipLws(input, false);

        while (c != -1 && isToken(c)) {
            result.append((char) c);
            c = input.read();
        }
        // 向后跳过，以便下次读取时可以使用非token字符
        input.skip(-1);

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    /**
     * @return 如果找到一个引用的字符串, 如果找到除引号字符串以外的数据，则返回null; 如果在引用的字符串终止之前到达数据末尾，则返回null
     */
    static String readQuotedString(StringReader input, boolean returnQuoted) throws IOException {

        int c = skipLws(input, false);

        if (c != '"') {
            return null;
        }

        StringBuilder result = new StringBuilder();
        if (returnQuoted) {
            result.append('\"');
        }
        c = input.read();

        while (c != '"') {
            if (c == -1) {
                return null;
            } else if (c == '\\') {
                c = input.read();
                if (returnQuoted) {
                    result.append('\\');
                }
                result.append(c);
            } else {
                result.append((char) c);
            }
            c = input.read();
        }
        if (returnQuoted) {
            result.append('\"');
        }

        return result.toString();
    }

    static String readTokenOrQuotedString(StringReader input, boolean returnQuoted)
            throws IOException {

        // 返回，以便可以再次读取第一个非LWS字符
        int c = skipLws(input, true);

        if (c == '"') {
            return readQuotedString(input, returnQuoted);
        } else {
            return readToken(input);
        }
    }

    /**
     * 可以使用或不使用引号来明确地读取token，因此token的这种解析方法允许可选的双引号.
     * 这在任何RFC中都没有定义. 处理有缺陷的客户端的数据是一种特殊情况 (已知的DIGEST auth的有bug的客户端, 包括 Microsoft IE 8
     * &amp; 9, Apple Safari for OSX and iOS), 添加引号到应该是token的值.
     *
     * @return 如果找到一个token, 如果找到令牌或引用令牌以外的数据，则返回null; 如果在引用令牌终止之前达到数据末尾，则返回null
     */
    static String readQuotedToken(StringReader input) throws IOException {

        StringBuilder result = new StringBuilder();
        boolean quoted = false;

        int c = skipLws(input, false);

        if (c == '"') {
            quoted = true;
        } else if (c == -1 || !isToken(c)) {
            return null;
        } else {
            result.append((char) c);
        }
        c = input.read();

        while (c != -1 && isToken(c)) {
            result.append((char) c);
            c = input.read();
        }

        if (quoted) {
            if (c != '"') {
                return null;
            }
        } else {
            // 向后跳过，以便下次读取时可以使用非token字符
            input.skip(-1);
        }

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    /**
     * 可以使用或不使用周围引号明确读取LHEX，因此LHEX的这种解析方法允许可选的双引号.
     * 一些有缺陷的客户端（用于DIGEST auth的libwww-perl）已知在规范仅需要LHEX时发送带引号的LHEX.
     *
     * <p>
     * 从字面上看，LHEX是小写的十六进制数字. 此实现也允许大写数字, 将返回值转换为小写.
     *
     * @return  如果发现任何LHEX序列（减去周围的引号）, 或<code>null</code>如果找到其他LHEX数据
     */
    static String readLhex(StringReader input) throws IOException {

        StringBuilder result = new StringBuilder();
        boolean quoted = false;

        int c = skipLws(input, false);

        if (c == '"') {
            quoted = true;
        } else if (c == -1 || !isHex(c)) {
            return null;
        } else {
            if ('A' <= c && c <= 'F') {
                c -= ('A' - 'a');
            }
            result.append((char) c);
        }
        c = input.read();

        while (c != -1 && isHex(c)) {
            if ('A' <= c && c <= 'F') {
                c -= ('A' - 'a');
            }
            result.append((char) c);
            c = input.read();
        }

        if (quoted) {
            if (c != '"') {
                return null;
            }
        } else {
            // 向后跳过，以便下次读取时可以使用非十六进制字符
            input.skip(-1);
        }

        if (c != -1 && result.length() == 0) {
            return null;
        } else {
            return result.toString();
        }
    }

    static double readWeight(StringReader input, char delimiter) throws IOException {
        int c = skipLws(input, false);
        if (c == -1 || c == delimiter) {
            // 没有q值只是空格
            return 1;
        } else if (c != 'q') {
            // 异常. 使用零的质量，以便它被删除.
            skipUntil(input, c, delimiter);
            return 0;
        }
        // RFC 7231不允许在这里使用空格，但要容忍
        c = skipLws(input, false);
        if (c != '=') {
            // 异常. 使用零的质量，以便它被删除.
            skipUntil(input, c, delimiter);
            return 0;
        }

        // RFC 7231不允许在这里使用空格，但要容忍
        c = skipLws(input, false);

        // 应该不超过3位小数
        StringBuilder value = new StringBuilder(5);
        int decimalPlacesRead = 0;
        if (c == '0' || c == '1') {
            value.append((char) c);
            c = input.read();
            if (c == '.') {
                value.append('.');
            } else if (c < '0' || c > '9') {
                decimalPlacesRead = 3;
            }
            while (true) {
                c = input.read();
                if (c >= '0' && c <= '9') {
                    if (decimalPlacesRead < 3) {
                        value.append((char) c);
                        decimalPlacesRead++;
                    }
                } else if (c == delimiter || c == 9 || c == 32 || c == -1) {
                    break;
                } else {
                    // 异常. 使用零的质量，以便它被删除并跳过EOF或下一个分隔符
                    skipUntil(input, c, delimiter);
                    return 0;
                }
            }
        } else {
            // 异常. 使用零的质量，以便它被删除并跳过EOF或下一个分隔符
            skipUntil(input, c, delimiter);
            return 0;
        }

        double result = Double.parseDouble(value.toString());
        if (result > 1) {
            return 0;
        }
        return result;
    }


    /**
     * 跳过所有字符，直到找到EOF或指定的目标. 通常用于跳过无效输入直到下一个分隔符.
     */
    static SkipResult skipUntil(StringReader input, int c, char target) throws IOException {
        while (c != -1 && c != target) {
            c = input.read();
        }
        if (c == -1) {
            return SkipResult.EOF;
        } else {
            return SkipResult.FOUND;
        }
    }
}
