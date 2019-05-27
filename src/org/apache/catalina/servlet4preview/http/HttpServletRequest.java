package org.apache.catalina.servlet4preview.http;

/**
 * 提供早期对Servlet 4.0 API部分的访问.
 */
public interface HttpServletRequest extends javax.servlet.http.HttpServletRequest {

    public ServletMapping getServletMapping();

    /**
     * 为生成的push请求获取builder.
     * 每一次调用这个方法都会返回一个新的实例, 独立于之前获取的实例.
     *
     * @return builder或{@code null}（不支持push）.
     *         注意，即使返回了PushBuilder实例, 再次调用{@link PushBuilder#push()}, 他可能就不在有效.
     *
     * @since Servlet 4.0
     */
    public PushBuilder newPushBuilder();
}
