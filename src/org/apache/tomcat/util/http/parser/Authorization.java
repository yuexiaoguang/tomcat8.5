package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.res.StringManager;

/**
 * "Authorization" header的解析器.
 */
public class Authorization {

    private static final StringManager sm = StringManager.getManager(Authorization.class);

    @SuppressWarnings("unused")  // Unused due to buggy client implementations
    private static final Integer FIELD_TYPE_TOKEN = Integer.valueOf(0);
    private static final Integer FIELD_TYPE_QUOTED_STRING = Integer.valueOf(1);
    private static final Integer FIELD_TYPE_TOKEN_OR_QUOTED_STRING = Integer.valueOf(2);
    private static final Integer FIELD_TYPE_LHEX = Integer.valueOf(3);
    private static final Integer FIELD_TYPE_QUOTED_TOKEN = Integer.valueOf(4);

    private static final Map<String,Integer> fieldTypes = new HashMap<>();

    static {
        // Digest field types.
        // Note: 这些比RFC2617更宽松. 这符合RFC2616的建议，即服务器可以容忍有错误的客户端，因为它们可以毫不含糊.
        fieldTypes.put("username", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("realm", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("nonce", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("digest-uri", FIELD_TYPE_QUOTED_STRING);
        // RFC2617 says response is <">32LHEX<">. 32LHEX will also be accepted
        fieldTypes.put("response", FIELD_TYPE_LHEX);
        // RFC2617 says algorithm is token. <">token<"> will also be accepted
        fieldTypes.put("algorithm", FIELD_TYPE_QUOTED_TOKEN);
        fieldTypes.put("cnonce", FIELD_TYPE_QUOTED_STRING);
        fieldTypes.put("opaque", FIELD_TYPE_QUOTED_STRING);
        // RFC2617 says qop is token. <">token<"> will also be accepted
        fieldTypes.put("qop", FIELD_TYPE_QUOTED_TOKEN);
        // RFC2617 says nc is 8LHEX. <">8LHEX<"> will also be accepted
        fieldTypes.put("nc", FIELD_TYPE_LHEX);

    }

    /**
     * 根据RFC 2617第3.2.2节，分析用于DIGEST身份验证的HTTP Authorization header.
     *
     * @param input 要解析的header值
     *
     * @return  如果发生解析错误，则为{@link String}或<code>null</code>的指令和值的Map.
     * 			虽然返回的值是{@link String}，但它们已经过验证，以确保它们符合RFC 2617.
     *
     * @throws IllegalArgumentException 如果header 不符合RFC 2617
     * @throws java.io.IOException 如果在读取输入时发生错误
     */
    public static Map<String,String> parseAuthorizationDigest (StringReader input)
            throws IllegalArgumentException, IOException {

        Map<String,String> result = new HashMap<>();

        if (HttpParser.skipConstant(input, "Digest") != SkipResult.FOUND) {
            return null;
        }
        // 所有字段名称都是有效的 token
        String field = HttpParser.readToken(input);
        if (field == null) {
            return null;
        }
        while (!field.equals("")) {
            if (HttpParser.skipConstant(input, "=") != SkipResult.FOUND) {
                return null;
            }
            String value;
            Integer type = fieldTypes.get(field.toLowerCase(Locale.ENGLISH));
            if (type == null) {
                // auth-param = token "=" ( token | quoted-string )
                type = FIELD_TYPE_TOKEN_OR_QUOTED_STRING;
            }
            switch (type.intValue()) {
                case 0:
                    // FIELD_TYPE_TOKEN
                    value = HttpParser.readToken(input);
                    break;
                case 1:
                    // FIELD_TYPE_QUOTED_STRING
                    value = HttpParser.readQuotedString(input, false);
                    break;
                case 2:
                    // FIELD_TYPE_TOKEN_OR_QUOTED_STRING
                    value = HttpParser.readTokenOrQuotedString(input, false);
                    break;
                case 3:
                    // FIELD_TYPE_LHEX
                    value = HttpParser.readLhex(input);
                    break;
                case 4:
                    // FIELD_TYPE_QUOTED_TOKEN
                    value = HttpParser.readQuotedToken(input);
                    break;
                default:
                    // Error
                    throw new IllegalArgumentException(
                            sm.getString("authorization.unknownType", type));
            }

            if (value == null) {
                return null;
            }
            result.put(field, value);

            if (HttpParser.skipConstant(input, ",") == SkipResult.NOT_FOUND) {
                return null;
            }
            field = HttpParser.readToken(input);
            if (field == null) {
                return null;
            }
        }

        return result;
    }
}
