package org.apache.catalina.servlet4preview.http;


/**
 * 提供对所建议的servlet 4.0 API的某些部分的早期访问.
 */
public class HttpServletRequestWrapper extends javax.servlet.http.HttpServletRequestWrapper
        implements HttpServletRequest {

    /**
     * @param request 要包装的请求
     *
     * @throws java.lang.IllegalArgumentException 如果请求是 null
     */
    public HttpServletRequestWrapper(javax.servlet.http.HttpServletRequest request) {
        super(request);
    }

    private HttpServletRequest _getHttpServletRequest() {
        return (HttpServletRequest) super.getRequest();
    }

    /**
     * 默认返回包装的请求对象的{@link HttpServletRequest#getServletMapping()}.
     *
     * @since Servlet 4.0
     */
    @Override
    public ServletMapping getServletMapping() {
        return this._getHttpServletRequest().getServletMapping();
    }

    /**
     * 默认返回包装的请求对象的{@link HttpServletRequest#newPushBuilder()}.
     *
     * @since Servlet 4.0
     */
    @Override
    public PushBuilder newPushBuilder() {
        return this._getHttpServletRequest().newPushBuilder();
    }
}
