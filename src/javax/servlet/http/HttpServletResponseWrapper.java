package javax.servlet.http;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletResponseWrapper;

/**
 * 实现了HttpServletResponse接口，可以被开发者子类化适应不同的Servlet的响应.
 * 这个类实现了Wrapper或Decorator模式. 方法默认调用已包装的响应对象.
 */
public class HttpServletResponseWrapper extends ServletResponseWrapper
        implements HttpServletResponse {

    /**
     * @param response 包装的响应
     *
     * @throws java.lang.IllegalArgumentException 如果响应是 null
     */
    public HttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    private HttpServletResponse _getHttpServletResponse() {
        return (HttpServletResponse) super.getResponse();
    }

    @Override
    public void addCookie(Cookie cookie) {
        this._getHttpServletResponse().addCookie(cookie);
    }

    @Override
    public boolean containsHeader(String name) {
        return this._getHttpServletResponse().containsHeader(name);
    }

    @Override
    public String encodeURL(String url) {
        return this._getHttpServletResponse().encodeURL(url);
    }

    @Override
    public String encodeRedirectURL(String url) {
        return this._getHttpServletResponse().encodeRedirectURL(url);
    }

    /**
     * @deprecated As of Version 3.0 of the Java Servlet API
     */
    @Override
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public String encodeUrl(String url) {
        return this._getHttpServletResponse().encodeUrl(url);
    }

    /**
     * @deprecated As of Version 3.0 of the Java Servlet API
     */
    @Override
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public String encodeRedirectUrl(String url) {
        return this._getHttpServletResponse().encodeRedirectUrl(url);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this._getHttpServletResponse().sendError(sc, msg);
    }

    @Override
    public void sendError(int sc) throws IOException {
        this._getHttpServletResponse().sendError(sc);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        this._getHttpServletResponse().sendRedirect(location);
    }

    @Override
    public void setDateHeader(String name, long date) {
        this._getHttpServletResponse().setDateHeader(name, date);
    }

    @Override
    public void addDateHeader(String name, long date) {
        this._getHttpServletResponse().addDateHeader(name, date);
    }

    @Override
    public void setHeader(String name, String value) {
        this._getHttpServletResponse().setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        this._getHttpServletResponse().addHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        this._getHttpServletResponse().setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        this._getHttpServletResponse().addIntHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        this._getHttpServletResponse().setStatus(sc);
    }

    /**
     * @deprecated As of Version 3.0 of the Java Servlet API
     */
    @Override
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public void setStatus(int sc, String sm) {
        this._getHttpServletResponse().setStatus(sc, sm);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 默认行为是调用包装的响应对象上的{@link HttpServletResponse#getStatus()}.
     *
     * @since Servlet 3.0
     */
    @Override
    public int getStatus() {
        return this._getHttpServletResponse().getStatus();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 默认行为是调用包装的响应对象上的{@link HttpServletResponse#getHeader(String)}.
     *
     * @since Servlet 3.0
     */
    @Override
    public String getHeader(String name) {
        return this._getHttpServletResponse().getHeader(name);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 默认行为是调用包装的响应对象上的{@link HttpServletResponse#getHeaders(String)}.
     *
     * @since Servlet 3.0
     */
    @Override
    public Collection<String> getHeaders(String name) {
        return this._getHttpServletResponse().getHeaders(name);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 默认行为是调用包装的响应对象上的{@link HttpServletResponse#getHeaderNames()}.
     *
     * @since Servlet 3.0
     */
    @Override
    public Collection<String> getHeaderNames() {
        return this._getHttpServletResponse().getHeaderNames();
    }
}
