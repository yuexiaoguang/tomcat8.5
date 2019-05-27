package org.apache.tomcat.websocket;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * websocket客户端使用的身份验证方法的基类.
 */
public abstract class Authenticator {
    private static final Pattern pattern = Pattern
            .compile("(\\w+)\\s*=\\s*(\"([^\"]+)\"|([^,=\"]+))\\s*,?");

    /**
     * 生成将发送到服务器的身份验证头.
     * 
     * @param requestUri 请求URI
     * @param WWWAuthenticate 服务器认证
     * @param UserProperties 用户信息
     * 
     * @return 验证 header
     * @throws AuthenticationException 发生错误
     */
    public abstract String getAuthorization(String requestUri, String WWWAuthenticate,
            Map<String, Object> UserProperties) throws AuthenticationException;

    /**
     * 获取认证方法.
     */
    public abstract String getSchemeName();

    /**
     * 解析认证header.
     * 
     * @param WWWAuthenticate The server auth challenge
     * 
     * @return 解析后的 header
     */
    public Map<String, String> parseWWWAuthenticateHeader(String WWWAuthenticate) {

        Matcher m = pattern.matcher(WWWAuthenticate);
        Map<String, String> challenge = new HashMap<>();

        while (m.find()) {
            String key = m.group(1);
            String qtedValue = m.group(3);
            String value = m.group(4);

            challenge.put(key, qtedValue != null ? qtedValue : value);

        }
        return challenge;
    }
}
