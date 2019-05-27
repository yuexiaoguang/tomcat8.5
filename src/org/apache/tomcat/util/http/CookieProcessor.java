package org.apache.tomcat.util.http;

import java.nio.charset.Charset;

import javax.servlet.http.Cookie;

public interface CookieProcessor {

    /**
     * 将提供的标头解析为服务器cookie对象.
     *
     * @param headers       要解析的HTTP标头
     * @param serverCookies 用于填充解析结果的服务器cookie对象
     */
    void parseCookieHeader(MimeHeaders headers, ServerCookies serverCookies);

    /**
     * 为给定的Cookie生成{@code Set-Cookie} HTTP标头值.
     *
     * @param cookie 将为其生成标头的cookie
     *
     * @return 表单中的标头值，可以直接添加到响应中
     */
    String generateHeader(Cookie cookie);

    /**
     * 获取在解析和生成cookie的HTTP头时, 在字节和字符之间进行转换时将使用的字符集.
     */
    Charset getCharset();
}
