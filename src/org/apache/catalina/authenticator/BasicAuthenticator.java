package org.apache.catalina.authenticator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.codec.binary.Base64;

/**
 * <b>Authenticator</b>和<b>Valve</b>的HTTP BASIC身份验证实现类, 
 * RFC 2617概述: "HTTP Authentication: 基本和摘要访问验证."
 */
public class BasicAuthenticator extends AuthenticatorBase {

    private static final Log log = LogFactory.getLog(BasicAuthenticator.class);

    private Charset charset = StandardCharsets.ISO_8859_1;
    private String charsetString = null;


    public String getCharset() {
        return charsetString;
    }


    public void setCharset(String charsetString) {
        // 唯一可接受的选择是 null, "" 或 "UTF-8" (不区分大小写)
        if (charsetString == null || charsetString.isEmpty()) {
            charset = StandardCharsets.ISO_8859_1;
        } else if ("UTF-8".equalsIgnoreCase(charsetString)) {
            charset = StandardCharsets.UTF_8;
        } else {
            throw new IllegalArgumentException(sm.getString("basicAuthenticator.invalidCharset"));
        }
        this.charsetString = charsetString;
    }


    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response)
            throws IOException {

        if (checkForCachedAuthentication(request, response, true)) {
            return true;
        }

        // 验证已经包含在此请求中的所有凭据
        MessageBytes authorization =
            request.getCoyoteRequest().getMimeHeaders()
            .getValue("authorization");

        if (authorization != null) {
            authorization.toBytes();
            ByteChunk authorizationBC = authorization.getByteChunk();
            BasicCredentials credentials = null;
            try {
                credentials = new BasicCredentials(authorizationBC, charset);
                String username = credentials.getUsername();
                String password = credentials.getPassword();

                Principal principal = context.getRealm().authenticate(username, password);
                if (principal != null) {
                    register(request, response, principal,
                        HttpServletRequest.BASIC_AUTH, username, password);
                    return true;
                }
            }
            catch (IllegalArgumentException iae) {
                if (log.isDebugEnabled()) {
                    log.debug("Invalid Authorization" + iae.getMessage());
                }
            }
        }

        // 无法验证请求, 所以重新发布挑战
        StringBuilder value = new StringBuilder(16);
        value.append("Basic realm=\"");
        value.append(getRealmName(context));
        value.append('\"');
        if (charsetString != null && !charsetString.isEmpty()) {
            value.append(", charset=");
            value.append(charsetString);
        }
        response.setHeader(AUTH_HEADER_NAME, value.toString());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;

    }

    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.BASIC_AUTH;
    }


    /**
     * 基于RFC 2617 section 2的用于基本身份验证的HTTP授权标头的解析器, 以及基于RFC 2045 section 6.8的 Base64编码凭证.
     */
    public static class BasicCredentials {

        // 此解析器注释支持的唯一身份验证方法: 使用单个空格作为分界符
        private static final String METHOD = "basic ";

        private final Charset charset;
        private final ByteChunk authorization;
        private final int initialOffset;
        private int base64blobOffset;
        private int base64blobLength;

        private String username = null;
        private String password = null;
        
        /**
         * @param input   要解析的header值
         * @param charset 用于将字节转换为字符串的字符集
         *
         * @throws IllegalArgumentException 如果header不符合RFC 2617
         */
        public BasicCredentials(ByteChunk input, Charset charset) throws IllegalArgumentException {
            authorization = input;
            initialOffset = input.getOffset();
            this.charset = charset;

            parseMethod();
            byte[] decoded = parseBase64();
            parseCredentials(decoded);
        }

        /**
         * @return  解码的用户名, 永远不会是<code>null</code>, 但可以是空字符串.
         */
        public String getUsername() {
            return username;
        }

        /**
         * @return  解码的密码, 或<code>null</code>.
         */
        public String getPassword() {
            return password;
        }

        /*
         * 授权方法字符串不区分大小写，必须至少有一个空格字符作为定界符.
         */
        private void parseMethod() throws IllegalArgumentException {
            if (authorization.startsWithIgnoreCase(METHOD, 0)) {
                // step past the auth method name
                base64blobOffset = initialOffset + METHOD.length();
                base64blobLength = authorization.getLength() - METHOD.length();
            }
            else {
                // is this possible, or permitted?
                throw new IllegalArgumentException(
                        "Authorization header method is not \"Basic\"");
            }
        }
        /*
         * 解码Base64用户传递的令牌, RFC 2617状态可以比RFC 2045中定义的每行限制的76个字符长.
         * Base64解码器将忽略嵌入的行break字符以及剩余的周围空格.
         */
        private byte[] parseBase64() throws IllegalArgumentException {
            byte[] decoded = Base64.decodeBase64(
                        authorization.getBuffer(),
                        base64blobOffset, base64blobLength);
            //  restore original offset
            authorization.setOffset(initialOffset);
            if (decoded == null) {
                throw new IllegalArgumentException(
                        "Basic Authorization credentials are not Base64");
            }
            return decoded;
        }

        /*
         * 提取强制用户名令牌并将其从可选密码令牌中分离出来. 允许剩下周围的空格.
         */
        private void parseCredentials(byte[] decoded)
                throws IllegalArgumentException {

            int colon = -1;
            for (int i = 0; i < decoded.length; i++) {
                if (decoded[i] == ':') {
                    colon = i;
                    break;
                }
            }

            if (colon < 0) {
                username = new String(decoded, charset);
                // password will remain null!
            }
            else {
                username = new String(decoded, 0, colon, charset);
                password = new String(decoded, colon + 1, decoded.length - colon - 1, charset);
                // tolerate surplus white space around credentials
                if (password.length() > 1) {
                    password = password.trim();
                }
            }
            // tolerate surplus white space around credentials
            if (username.length() > 1) {
                username = username.trim();
            }
        }
    }
}
