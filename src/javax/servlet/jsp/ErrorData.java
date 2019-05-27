package javax.servlet.jsp;

/**
 * 包含有关错误的信息，用于错误页. 如果不在错误页面的上下文中使用，这个实例中包含的信息是没有意义的.
 * 指示JSP是一个错误页, 页面的作者必须设置页面指令中的isErrorPage属性为 "true".
 */
public final class ErrorData {

    private final Throwable throwable;
    private final int statusCode;
    private final String uri;
    private final String servletName;

    /**
     * @param throwable 导致错误的Throwable
     * @param statusCode 错误的状态码
     * @param uri 请求 URI
     * @param servletName 调用的servlet的名称
     */
    public ErrorData(Throwable throwable, int statusCode, String uri,
            String servletName) {
        this.throwable = throwable;
        this.statusCode = statusCode;
        this.uri = uri;
        this.servletName = servletName;
    }

    /**
     * 返回导致错误的Throwable.
     */
    public Throwable getThrowable() {
        return this.throwable;
    }

    /**
     * 返回错误的状态码.
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * 返回请求URI.
     */
    public String getRequestURI() {
        return this.uri;
    }

    /**
     * 返回调用的servlet的名称.
     */
    public String getServletName() {
        return this.servletName;
    }
}
