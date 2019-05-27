package org.apache.catalina.authenticator;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import javax.servlet.http.Cookie;

import org.apache.tomcat.util.buf.ByteChunk;


/**
 * 从请求中保存关键信息，以便基于表单的身份验证可以在用户身份验证后重现它
 * <p>
 * <b>IMPLEMENTATION NOTE</b> - 假设该对象仅从单个线程的上下文中访问，因此不进行内部集合类的同步.
 */
public final class SavedRequest {


    /**
     * 关联的Cookies集合.
     */
    private final ArrayList<Cookie> cookies = new ArrayList<>();

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public Iterator<Cookie> getCookies() {
        return (cookies.iterator());
    }


    /**
     * 关联的Headers集合. 
     * 每个key是头文件名, 而值是一个ArrayList包含一个或多个Header的实际值.
     */
    private final HashMap<String,ArrayList<String>> headers = new HashMap<>();

    public void addHeader(String name, String value) {
        ArrayList<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.add(value);
    }

    public Iterator<String> getHeaderNames() {
        return (headers.keySet().iterator());
    }

    public Iterator<String> getHeaderValues(String name) {
        ArrayList<String> values = headers.get(name);
        if (values == null)
            return ((new ArrayList<String>()).iterator());
        else
            return (values.iterator());
    }


    /**
     * 关联的Locales集合.
     */
    private final ArrayList<Locale> locales = new ArrayList<>();

    public void addLocale(Locale locale) {
        locales.add(locale);
    }

    public Iterator<Locale> getLocales() {
        return (locales.iterator());
    }


    /**
     * 使用的请求方法.
     */
    private String method = null;

    public String getMethod() {
        return (this.method);
    }

    public void setMethod(String method) {
        this.method = method;
    }


    /**
     * 关联的查询字符串.
     */
    private String queryString = null;

    public String getQueryString() {
        return (this.queryString);
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }


    /**
     * 请求URI.
     */
    private String requestURI = null;

    public String getRequestURI() {
        return (this.requestURI);
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }


    /**
     * 与此请求关联的解码请求URI. 路径参数也被排除
     */
    private String decodedRequestURI = null;

    public String getDecodedRequestURI() {
        return (this.decodedRequestURI);
    }

    public void setDecodedRequestURI(String decodedRequestURI) {
        this.decodedRequestURI = decodedRequestURI;
    }


    /**
     * 这个请求的主体.
     */
    private ByteChunk body = null;

    public ByteChunk getBody() {
        return (this.body);
    }

    public void setBody(ByteChunk body) {
        this.body = body;
    }

    /**
     * 请求的内容类型, POST才使用这个值.
     */
    private String contentType = null;

    public String getContentType() {
        return (this.contentType);
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
