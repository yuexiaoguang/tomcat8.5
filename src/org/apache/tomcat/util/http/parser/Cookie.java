package org.apache.tomcat.util.http.parser;

import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.res.StringManager;


/**
 * <p>基于RFC6265和RFC2109的Cookie header解析器.</p>
 * <p>使用RFC6265解析cookie比以下方式更符合规范:</p>
 * <ul>
 *   <li>在cookie-octet中允许值0x80到0xFF, 以支持在HTML 5中使用的cookie值使用UTF-8.</li>
 *   <li>对于没有值的cookie, 名称后面不需要'=', 因为有些浏览器没有发送它.</li>
 * </ul>
 * <p>使用RFC2109解析cookie比以下方式更符合规范:</p>
 * <ul>
 *   <li>即使 / 在token中不允许, 也不必引用包含 / 字符的path属性的值.</li>
 * </ul>
 *
 * <p>Implementation note:<br>
 * 此类已经过仔细调整，以确保它具有与原始Netscape / RFC2109 cookie解析器相同或更好的性能.
 * 在提交和更改之前, 确保TesterCookiePerformance单元测试继续为新旧解析器提供1%的结果.</p>
 */
public class Cookie {

    private static final Log log = LogFactory.getLog(Cookie.class);
    private static final UserDataHelper invalidCookieVersionLog = new UserDataHelper(log);
    private static final UserDataHelper invalidCookieLog = new UserDataHelper(log);
    private static final StringManager sm =
            StringManager.getManager("org.apache.tomcat.util.http.parser");

    private static final boolean isCookieOctet[] = new boolean[256];
    private static final boolean isText[] = new boolean[256];
    private static final byte[] VERSION_BYTES = "$Version".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PATH_BYTES = "$Path".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] DOMAIN_BYTES = "$Domain".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final byte TAB_BYTE = (byte) 0x09;
    private static final byte SPACE_BYTE = (byte) 0x20;
    private static final byte QUOTE_BYTE = (byte) 0x22;
    private static final byte COMMA_BYTE = (byte) 0x2C;
    private static final byte FORWARDSLASH_BYTE = (byte) 0x2F;
    private static final byte SEMICOLON_BYTE = (byte) 0x3B;
    private static final byte EQUALS_BYTE = (byte) 0x3D;
    private static final byte SLASH_BYTE = (byte) 0x5C;
    private static final byte DEL_BYTE = (byte) 0x7F;


    static {
        // %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E (RFC6265)
        // %x80 to %xFF                                 (UTF-8)
        for (int i = 0; i < 256; i++) {
            if (i < 0x21 || i == QUOTE_BYTE || i == COMMA_BYTE ||
                    i == SEMICOLON_BYTE || i == SLASH_BYTE || i == DEL_BYTE) {
                isCookieOctet[i] = false;
            } else {
                isCookieOctet[i] = true;
            }
        }
        for (int i = 0; i < 256; i++) {
            if (i < TAB_BYTE || (i > TAB_BYTE && i < SPACE_BYTE) || i == DEL_BYTE) {
                isText[i] = false;
            } else {
                isText[i] = true;
            }
        }
    }


    private Cookie() {
        // Hide default constructor
    }


    public static void parseCookie(byte[] bytes, int offset, int len,
            ServerCookies serverCookies) {

        // 整个解析器使用ByteBuffer，因为它允许在解析方法之间轻松传递byte []和位置信息
        ByteBuffer bb = new ByteBuffer(bytes, offset, len);

        // 使用RFC6265解析规则, 检查header是否以版本标记开头. 可以使用RFC6265解析规则读取RFC2109版本标记. 如果是版本 1, 使用 RFC2109. 否则使用 RFC6265.

        skipLWS(bb);

        // 记录位置，以防需要返回.
        int mark = bb.position();

        SkipResult skipResult = skipBytes(bb, VERSION_BYTES);
        if (skipResult != SkipResult.FOUND) {
            // 无需重置位置，因为skipConstant()将完成它
            parseCookieRfc6265(bb, serverCookies);
            return;
        }

        skipLWS(bb);

        skipResult = skipByte(bb, EQUALS_BYTE);
        if (skipResult != SkipResult.FOUND) {
            // 需要重置位置，因为skipConstant()只会在被调用之前重置到位置
            bb.position(mark);
            parseCookieRfc6265(bb, serverCookies);
            return;
        }

        skipLWS(bb);

        ByteBuffer value = readCookieValue(bb);
        if (value != null && value.remaining() == 1) {
            int version = value.get() - '0';
            if (version == 1 || version == 0) {
                // $Version=1 -> RFC2109
                // $Version=0 -> RFC2109
                skipLWS(bb);
                byte b = bb.get();
                if (b == SEMICOLON_BYTE || b == COMMA_BYTE) {
                    parseCookieRfc2109(bb, serverCookies, version);
                }
                return;
            } else {
                // Unrecognised version.
                // Ignore this header.
                value.rewind();
                logInvalidVersion(value);
            }
        } else {
            // Unrecognised version.
            // Ignore this header.
            logInvalidVersion(value);
        }
    }


    public static String unescapeCookieValueRfc2109(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }
        if (input.charAt(0) != '"' && input.charAt(input.length() - 1) != '"') {
            return input;
        }

        StringBuilder sb = new StringBuilder(input.length());
        char[] chars = input.toCharArray();
        boolean escaped = false;

        for (int i = 1; i < input.length() - 1; i++) {
            if (chars[i] == '\\') {
                escaped = true;
            } else if (escaped) {
                escaped = false;
                if (chars[i] < 128) {
                    sb.append(chars[i]);
                } else {
                    sb.append('\\');
                    sb.append(chars[i]);
                }
            } else {
                sb.append(chars[i]);
            }
        }
        return sb.toString();
    }


    private static void parseCookieRfc6265(ByteBuffer bb, ServerCookies serverCookies) {

        boolean moreToProcess = true;

        while (moreToProcess) {
            skipLWS(bb);

            ByteBuffer name = readToken(bb);
            ByteBuffer value = null;

            skipLWS(bb);

            SkipResult skipResult = skipByte(bb, EQUALS_BYTE);
            if (skipResult == SkipResult.FOUND) {
                skipLWS(bb);
                value = readCookieValueRfc6265(bb);
                if (value == null) {
                    logInvalidHeader(bb);
                    // Cookie值无效. Skip to the next semi-colon
                    skipUntilSemiColon(bb);
                    continue;
                }
                skipLWS(bb);
            }

            skipResult = skipByte(bb, SEMICOLON_BYTE);
            if (skipResult == SkipResult.FOUND) {
                // NO-OP
            } else if (skipResult == SkipResult.NOT_FOUND) {
                logInvalidHeader(bb);
                // Invalid cookie. Ignore it and skip to the next semi-colon
                skipUntilSemiColon(bb);
                continue;
            } else {
                // SkipResult.EOF
                moreToProcess = false;
            }

            if (name.hasRemaining()) {
                ServerCookie sc = serverCookies.addCookie();
                sc.getName().setBytes(name.array(), name.position(), name.remaining());
                if (value == null) {
                    sc.getValue().setBytes(EMPTY_BYTES, 0, EMPTY_BYTES.length);
                } else {
                    sc.getValue().setBytes(value.array(), value.position(), value.remaining());
                }
            }
        }
    }


    private static void parseCookieRfc2109(ByteBuffer bb, ServerCookies serverCookies,
            int version) {

        boolean moreToProcess = true;

        while (moreToProcess) {
            skipLWS(bb);

            boolean parseAttributes = true;

            ByteBuffer name = readToken(bb);
            ByteBuffer value = null;
            ByteBuffer path = null;
            ByteBuffer domain = null;

            skipLWS(bb);

            SkipResult skipResult = skipByte(bb, EQUALS_BYTE);
            if (skipResult == SkipResult.FOUND) {
                skipLWS(bb);
                value = readCookieValueRfc2109(bb, false);
                if (value == null) {
                    skipInvalidCookie(bb);
                    continue;
                }
                skipLWS(bb);
            }

            skipResult = skipByte(bb, COMMA_BYTE);
            if (skipResult == SkipResult.FOUND) {
                parseAttributes = false;
            }
            skipResult = skipByte(bb, SEMICOLON_BYTE);
            if (skipResult == SkipResult.EOF) {
                parseAttributes = false;
                moreToProcess = false;
            } else if (skipResult == SkipResult.NOT_FOUND) {
                skipInvalidCookie(bb);
                continue;
            }

            if (parseAttributes) {
                skipResult = skipBytes(bb, PATH_BYTES);
                if (skipResult == SkipResult.FOUND) {
                    skipLWS(bb);
                    skipResult = skipByte(bb, EQUALS_BYTE);
                    if (skipResult != SkipResult.FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                    path = readCookieValueRfc2109(bb, true);
                    if (path == null) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                    skipLWS(bb);

                    skipResult = skipByte(bb, COMMA_BYTE);
                    if (skipResult == SkipResult.FOUND) {
                        parseAttributes = false;
                    }
                    skipResult = skipByte(bb, SEMICOLON_BYTE);
                    if (skipResult == SkipResult.EOF) {
                        parseAttributes = false;
                        moreToProcess = false;
                    } else if (skipResult == SkipResult.NOT_FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                }
            }

            if (parseAttributes) {
                skipResult = skipBytes(bb, DOMAIN_BYTES);
                if (skipResult == SkipResult.FOUND) {
                    skipLWS(bb);
                    skipResult = skipByte(bb, EQUALS_BYTE);
                    if (skipResult != SkipResult.FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                    domain = readCookieValueRfc2109(bb, false);
                    if (domain == null) {
                        skipInvalidCookie(bb);
                        continue;
                    }

                    skipResult = skipByte(bb, COMMA_BYTE);
                    if (skipResult == SkipResult.FOUND) {
                        parseAttributes = false;
                    }
                    skipResult = skipByte(bb, SEMICOLON_BYTE);
                    if (skipResult == SkipResult.EOF) {
                        parseAttributes = false;
                        moreToProcess = false;
                    } else if (skipResult == SkipResult.NOT_FOUND) {
                        skipInvalidCookie(bb);
                        continue;
                    }
                }
            }

            if (name.hasRemaining() && value != null && value.hasRemaining()) {
                ServerCookie sc = serverCookies.addCookie();
                sc.setVersion(version);
                sc.getName().setBytes(name.array(), name.position(), name.remaining());
                sc.getValue().setBytes(value.array(), value.position(), value.remaining());
                if (domain != null) {
                    sc.getDomain().setBytes(domain.array(),  domain.position(),  domain.remaining());
                }
                if (path != null) {
                    sc.getPath().setBytes(path.array(),  path.position(),  path.remaining());
                }
            }
        }
    }


    private static void skipInvalidCookie(ByteBuffer bb) {
        logInvalidHeader(bb);
        // Invalid cookie value. Skip to the next semi-colon
        skipUntilSemiColonOrComma(bb);
    }


    private static void skipLWS(ByteBuffer bb) {
        while(bb.hasRemaining()) {
            byte b = bb.get();
            if (b != TAB_BYTE && b != SPACE_BYTE) {
                bb.rewind();
                break;
            }
        }
    }


    private static void skipUntilSemiColon(ByteBuffer bb) {
        while(bb.hasRemaining()) {
            if (bb.get() == SEMICOLON_BYTE) {
                break;
            }
        }
    }


    private static void skipUntilSemiColonOrComma(ByteBuffer bb) {
        while(bb.hasRemaining()) {
            byte b = bb.get();
            if (b == SEMICOLON_BYTE || b == COMMA_BYTE) {
                break;
            }
        }
    }


    private static SkipResult skipByte(ByteBuffer bb, byte target) {

        if (!bb.hasRemaining()) {
            return SkipResult.EOF;
        }
        if (bb.get() == target) {
            return SkipResult.FOUND;
        }

        bb.rewind();
        return SkipResult.NOT_FOUND;
    }


    private static SkipResult skipBytes(ByteBuffer bb, byte[] target) {
        int mark = bb.position();

        for (int i = 0; i < target.length; i++) {
            if (!bb.hasRemaining()) {
                bb.position(mark);
                return SkipResult.EOF;
            }
            if (bb.get() != target[i]) {
                bb.position(mark);
                return SkipResult.NOT_FOUND;
            }
        }
        return SkipResult.FOUND;
    }


    /**
     * 类似于 readCookieValueRfc6265(), 但也允许逗号终止值 (在RFC2109允许的情况下).
     */
    private static ByteBuffer readCookieValue(ByteBuffer bb) {
        boolean quoted = false;
        if (bb.hasRemaining()) {
            if (bb.get() == QUOTE_BYTE) {
                quoted = true;
            } else {
                bb.rewind();
            }
        }
        int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (isCookieOctet[(b & 0xFF)]) {
                // NO-OP
            } else if (b == SEMICOLON_BYTE || b == COMMA_BYTE || b == SPACE_BYTE || b == TAB_BYTE) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            } else if (quoted && b == QUOTE_BYTE) {
                end = bb.position() - 1;
                break;
            } else {
                // Invalid cookie
                return null;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    /**
     * 类似于 readCookieValue(), 但将逗号视为无效值的一部分.
     */
    private static ByteBuffer readCookieValueRfc6265(ByteBuffer bb) {
        boolean quoted = false;
        if (bb.hasRemaining()) {
            if (bb.get() == QUOTE_BYTE) {
                quoted = true;
            } else {
                bb.rewind();
            }
        }
        int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (isCookieOctet[(b & 0xFF)]) {
                // NO-OP
            } else if (b == SEMICOLON_BYTE || b == SPACE_BYTE || b == TAB_BYTE) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            } else if (quoted && b == QUOTE_BYTE) {
                end = bb.position() - 1;
                break;
            } else {
                // Invalid cookie
                return null;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    private static ByteBuffer readCookieValueRfc2109(ByteBuffer bb, boolean allowForwardSlash) {
        if (!bb.hasRemaining()) {
            return null;
        }

        if (bb.peek() == QUOTE_BYTE) {
            return readQuotedString(bb);
        } else {
            if (allowForwardSlash) {
                return readTokenAllowForwardSlash(bb);
            } else {
                return readToken(bb);
            }
        }
    }


    private static ByteBuffer readToken(ByteBuffer bb) {
        final int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            if (!HttpParser.isToken(bb.get())) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    private static ByteBuffer readTokenAllowForwardSlash(ByteBuffer bb) {
        final int start = bb.position();
        int end = bb.limit();
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (b != FORWARDSLASH_BYTE && !HttpParser.isToken(b)) {
                end = bb.position() - 1;
                bb.position(end);
                break;
            }
        }

        return new ByteBuffer(bb.bytes, start, end - start);
    }


    private static ByteBuffer readQuotedString(ByteBuffer bb) {
        int start = bb.position();

        // Read the opening quote
        bb.get();
        boolean escaped = false;
        while (bb.hasRemaining()) {
            byte b = bb.get();
            if (b == SLASH_BYTE) {
                // Escaping another character
                escaped = true;
            } else if (escaped && b > (byte) -1) {
                escaped = false;
            } else if (b == QUOTE_BYTE) {
                return new ByteBuffer(bb.bytes, start, bb.position() - start);
            } else if (isText[b & 0xFF]) {
                escaped = false;
            } else {
                return null;
            }
        }

        return null;
    }


    private static void logInvalidHeader(ByteBuffer bb) {
        UserDataHelper.Mode logMode = invalidCookieLog.getNextMode();
        if (logMode != null) {
            String headerValue = new String(bb.array(), bb.position(), bb.limit() - bb.position(),
                        StandardCharsets.UTF_8);
            String message = sm.getString("cookie.invalidCookieValue", headerValue);
            switch (logMode) {
                case INFO_THEN_DEBUG:
                    message += sm.getString("cookie.fallToDebug");
                    //$FALL-THROUGH$
                case INFO:
                    log.info(message);
                    break;
                case DEBUG:
                    log.debug(message);
            }
        }
    }


    private static void logInvalidVersion(ByteBuffer value) {
        UserDataHelper.Mode logMode = invalidCookieVersionLog.getNextMode();
        if (logMode != null) {
            String version;
            if (value == null) {
                version = sm.getString("cookie.valueNotPresent");
            } else {
                version = new String(value.bytes, value.position(),
                        value.limit() - value.position(), StandardCharsets.UTF_8);
            }
            String message = sm.getString("cookie.invalidCookieVersion", version);
            switch (logMode) {
                case INFO_THEN_DEBUG:
                    message += sm.getString("cookie.fallToDebug");
                    //$FALL-THROUGH$
                case INFO:
                    log.info(message);
                    break;
                case DEBUG:
                    log.debug(message);
            }
        }
    }


    /**
     * 自定义实现，跳过{@link javax.nio.ByteBuffer}中的许多安全检查.
     */
    private static class ByteBuffer {

        private final byte[] bytes;
        private int limit;
        private int position = 0;

        public ByteBuffer(byte[] bytes, int offset, int len) {
            this.bytes = bytes;
            this.position = offset;
            this.limit = offset + len;
        }

        public int position() {
            return position;
        }

        public void position(int position) {
            this.position = position;
        }

        public int limit() {
            return limit;
        }

        public int remaining() {
            return limit - position;
        }

        public boolean hasRemaining() {
            return position < limit;
        }

        public byte get() {
            return bytes[position++];
        }

        public byte peek() {
            return bytes[position];
        }

        public void rewind() {
            position--;
        }

        public byte[] array() {
            return bytes;
        }

        // For debug purposes
        @Override
        public String toString() {
            return "position [" + position + "], limit [" + limit + "]";
        }
    }
}
