package javax.servlet;

public interface AsyncContext {
    public static final String ASYNC_REQUEST_URI =
        "javax.servlet.async.request_uri";
    public static final String ASYNC_CONTEXT_PATH  =
        "javax.servlet.async.context_path";
    public static final String ASYNC_PATH_INFO =
        "javax.servlet.async.path_info";
    public static final String ASYNC_SERVLET_PATH =
        "javax.servlet.async.servlet_path";
    public static final String ASYNC_QUERY_STRING =
        "javax.servlet.async.query_string";

    ServletRequest getRequest();

    ServletResponse getResponse();

    boolean hasOriginalRequestAndResponse();

    /**
     * @throws IllegalStateException 如果请求不是异步模式时调用此方法. 请求处于异步模式， 在
     * {@link javax.servlet.http.HttpServletRequest#startAsync()} 或
     * {@link javax.servlet.http.HttpServletRequest#startAsync(ServletRequest, ServletResponse)}被调用之后，
     * 并且在 {@link #complete()} 或其它任何dispatch()方法被调用之前.
     */
    void dispatch();

    /**
     * @param path 请求/响应应该被分派的相对于 {@link ServletContext}的路径，从这个异步请求开始的地方.
     *
     * @throws IllegalStateException 如果请求不是异步模式时调用此方法. 请求处于异步模式， 在
     * {@link javax.servlet.http.HttpServletRequest#startAsync()} 或
     * {@link javax.servlet.http.HttpServletRequest#startAsync(ServletRequest, ServletResponse)}被调用之后，
     * 并且在 {@link #complete()} 或其它任何dispatch()方法被调用之前.
     */
    void dispatch(String path);

    /**
     * @param path 请求/响应应该被分派的相对于指定的{@link ServletContext}的路径.
     * @param context 请求/响应应该被分派给的{@link ServletContext}.
     *
     * @throws IllegalStateException 如果请求不是异步模式时调用此方法. 请求处于异步模式， 在
     * {@link javax.servlet.http.HttpServletRequest#startAsync()} 或
     * {@link javax.servlet.http.HttpServletRequest#startAsync(ServletRequest, ServletResponse)}被调用之后，
     * 并且在 {@link #complete()} 或其它任何dispatch()方法被调用之前.
     */
    void dispatch(ServletContext context, String path);

    void complete();

    void start(Runnable run);

    void addListener(AsyncListener listener);

    void addListener(AsyncListener listener, ServletRequest request,
            ServletResponse response);

    <T extends AsyncListener> T createListener(Class<T> clazz)
    throws ServletException;

    /**
     * 设置超时.
     *
     * @param timeout 毫秒的超时时间. 0或更少表示没有超时.
     */
    void setTimeout(long timeout);

    /**
     * 获取当前的超时时间.
     *
     * @return 毫秒的超时时间. 0或更少表示没有超时.
     */
    long getTimeout();
}
