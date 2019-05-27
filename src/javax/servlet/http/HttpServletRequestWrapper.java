package javax.servlet.http;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.ServletRequestWrapper;

/**
 * 实现了HttpServletRequest接口，可以被开发者子类化使请求适应Servlet.
 * 这个类实现了Wrapper或Decorator（装饰器）模式. 方法默认调用已包装的请求对象.
 */
public class HttpServletRequestWrapper extends ServletRequestWrapper implements
        HttpServletRequest {

    /**
     * @param request 要包装的请求
     *
     * @throws java.lang.IllegalArgumentException 如果请求是 null
     */
    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    private HttpServletRequest _getHttpServletRequest() {
        return (HttpServletRequest) super.getRequest();
    }

    @Override
    public String getAuthType() {
        return this._getHttpServletRequest().getAuthType();
    }

    @Override
    public Cookie[] getCookies() {
        return this._getHttpServletRequest().getCookies();
    }

    @Override
    public long getDateHeader(String name) {
        return this._getHttpServletRequest().getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return this._getHttpServletRequest().getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return this._getHttpServletRequest().getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return this._getHttpServletRequest().getHeaderNames();
    }

    @Override
    public int getIntHeader(String name) {
        return this._getHttpServletRequest().getIntHeader(name);
    }

    @Override
    public String getMethod() {
        return this._getHttpServletRequest().getMethod();
    }

    @Override
    public String getPathInfo() {
        return this._getHttpServletRequest().getPathInfo();
    }

    @Override
    public String getPathTranslated() {
        return this._getHttpServletRequest().getPathTranslated();
    }

    @Override
    public String getContextPath() {
        return this._getHttpServletRequest().getContextPath();
    }

    @Override
    public String getQueryString() {
        return this._getHttpServletRequest().getQueryString();
    }

    @Override
    public String getRemoteUser() {
        return this._getHttpServletRequest().getRemoteUser();
    }

    @Override
    public boolean isUserInRole(String role) {
        return this._getHttpServletRequest().isUserInRole(role);
    }

    @Override
    public java.security.Principal getUserPrincipal() {
        return this._getHttpServletRequest().getUserPrincipal();
    }

    @Override
    public String getRequestedSessionId() {
        return this._getHttpServletRequest().getRequestedSessionId();
    }

    @Override
    public String getRequestURI() {
        return this._getHttpServletRequest().getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
        return this._getHttpServletRequest().getRequestURL();
    }

    @Override
    public String getServletPath() {
        return this._getHttpServletRequest().getServletPath();
    }

    @Override
    public HttpSession getSession(boolean create) {
        return this._getHttpServletRequest().getSession(create);
    }

    @Override
    public HttpSession getSession() {
        return this._getHttpServletRequest().getSession();
    }

    @Override
    public String changeSessionId() {
        return this._getHttpServletRequest().changeSessionId();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return this._getHttpServletRequest().isRequestedSessionIdValid();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return this._getHttpServletRequest().isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return this._getHttpServletRequest().isRequestedSessionIdFromURL();
    }

    /**
     * @deprecated As of Version 3.0 of the Java Servlet API
     */
    @Override
    @SuppressWarnings("dep-ann")
    // Spec API does not use @Deprecated
    public boolean isRequestedSessionIdFromUrl() {
        return this._getHttpServletRequest().isRequestedSessionIdFromUrl();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的默认行为是返回包装的请求对象上的{@link HttpServletRequest#authenticate(HttpServletResponse)}.
     *
     * @since Servlet 3.0
     */
    @Override
    public boolean authenticate(HttpServletResponse response)
            throws IOException, ServletException {
        return this._getHttpServletRequest().authenticate(response);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的默认行为是返回包装的请求对象上的{@link HttpServletRequest#login(String, String)}.
     *
     * @since Servlet 3.0
     */
    @Override
    public void login(String username, String password) throws ServletException {
        this._getHttpServletRequest().login(username, password);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的默认行为是返回包装的请求对象上的{@link HttpServletRequest#logout()}.
     *
     * @since Servlet 3.0
     */
    @Override
    public void logout() throws ServletException {
        this._getHttpServletRequest().logout();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的默认行为是返回包装的请求对象上的 {@link HttpServletRequest#getParts()}.
     *
     * @since Servlet 3.0
     */
    @Override
    public Collection<Part> getParts() throws IOException,
            ServletException {
        return this._getHttpServletRequest().getParts();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的默认行为是返回包装的请求对象上的{@link HttpServletRequest#getPart(String)}.
     *
     * @since Servlet 3.0
     */
    @Override
    public Part getPart(String name) throws IOException,
            ServletException {
        return this._getHttpServletRequest().getPart(name);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 此方法的默认行为是返回包装的请求对象上的{@link HttpServletRequest#upgrade(Class)}.
     *
     * @since Servlet 3.1
     */
    @Override
    public <T extends HttpUpgradeHandler> T upgrade(
            Class<T> httpUpgradeHandlerClass) throws IOException, ServletException {
        return this._getHttpServletRequest().upgrade(httpUpgradeHandlerClass);
    }
}
