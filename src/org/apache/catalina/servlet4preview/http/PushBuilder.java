package org.apache.catalina.servlet4preview.http;

import java.util.Set;

/**
 * 基于从{@link HttpServletRequest}获取的builder构建一个 push请求.
 * push请求将根据以下条件构造:
 * <ul>
 * <li>请求的方法设置为<code>GET</code>.</li>
 * <li>路径没有设置. 必须通过调用{@link #path(String)}显式的设置.</li>
 * <li>Conditional, range, expectation, authorization, referer header将被删除.</li>
 * <li>添加到关联的响应中的Cookie将被添加到push请求，除非 maxAge &lt;= 0, 这种情况下任何请求的相同名称的cookie将被删除.</li>
 * <li>referer header 将被设置为{@link HttpServletRequest#getRequestURL()} 加上{@link HttpServletRequest#getQueryString()}的查询字符串.
 * </ul>
 */
public interface PushBuilder {

    /**
     * 指定push请求要使用的HTTP方法.
     *
     * @param method push请求要使用的HTTP方法
     *
     * @return This builder instance
     *
     * @throws IllegalArgumentException 如果指定的HTTP 方法不是POST, PUT, DELETE, CONNECT, OPTIONS, TRACE.
     */
    PushBuilder method(String method);

    /**
     * 指定随后调用{@link #push()}生成的push请求的查询字符串.
     * 这个将会追加到{@link #path(String)}指定的查询字符串后面.
     *
     * @param queryString 用于生成push请求的查询字符串
     *
     * @return This builder instance
     */
    PushBuilder queryString(String queryString);

    /**
     * 指定随后调用{@link #push()}生成的push请求的session ID.
     * session ID将和它在原生请求上相同的方式存在(cookie or URL parameter).
     * 默认以下列顺序确定:
     * <ul>
     * <li>原生的请求的session ID</li>
     * <li>原生的请求中生成的 session ID</li>
     * <li>{@code null}</li>
     * </ul>
     *
     * @param sessionId 用于生成push请求的session ID
     *
     * @return This builder instance
     */
    PushBuilder sessionId(String sessionId);

    /**
     * 设置请求上的 HTTP header. 任何现有的相同名称的 header将被删除.
     *
     * @param name  要设置的header的名称
     * @param value 值
     *
     * @return This builder instance
     */
    PushBuilder setHeader(String name, String value);

    /**
     * 添加一个HTTP header到请求.
     *
     * @param name  header的名称
     * @param value 值
     *
     * @return This builder instance
     */
    PushBuilder addHeader(String name, String value);

    /**
     * 从请求删除一个HTTP header.
     *
     * @param name  要删除的header的名称
     *
     * @return This builder instance
     */
    PushBuilder removeHeader(String name);

    /**
     * 设置push请求使用的 URI 路径.
     * 这将在每次调用{@link #push()}之前调用. 如果路径包含一个查询字符串, 将被追加到现有查询字符串后面，而且不会发生重复删除.
     *
     * @param path 路径以'/'开头视作绝对路径. 所有其它路径视作相对于上下文路径的相对路径.
     * 				路径可能包括查询字符串.
     *
     * @return This builder instance
     */
    PushBuilder path(String path);

    /**
     * 生成push 请求并发送它到客户端，除非因为某些原因push不可用.
     * 调用这个方法之后，以下字段被设置为 {@code null}:
     * <ul>
     * <li>{@code path}</li>
     * <li>{@code eTag}</li>
     * <li>{@code lastModified}</li>
     * </ul>
     *
     * @throws IllegalStateException 如果当{@code path}是{@code null}时，调用这个方法
     * @throws IllegalArgumentException 如果push请求需要一个主体
     */
    void push();

    /**
     * 获取用于将来调用{@code push()}生成的push请求的HTTP方法的名称.
     *
     * @return HTTP方法
     */
    String getMethod();

    /**
     * 获取push请求的查询字符串.
     *
     * @return 查询字符串.
     */
    String getQueryString();

    /**
     * 获取push请求的session ID.
     *
     * @return session ID.
     */
    String getSessionId();

    /**
     * @return 下次调用{@code push()}，将要使用的 HTTP header的名称.
     */
    Set<String> getHeaderNames();

    /**
     * 获取给定HTTP header属性的值.
     * TODO Servlet 4.0
     * 阐明这个方法的行为
     *
     * @param name  header属性的名称
     *
     * @return 给定header的值. 如果定义了多个值，返回其中之一
     */
    String getHeader(String name);

    /**
     * 获取push请求的path.
     *
     * @return The path value that will be associated with the next push request
     */
    String getPath();
}
