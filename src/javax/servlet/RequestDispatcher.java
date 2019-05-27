package javax.servlet;

import java.io.IOException;

/**
 * 定义一个对象，该对象接收来自客户机的请求，并将其发送到服务器上的任何资源（如servlet、HTML文件或JSP文件）.
 * servlet容器创建<code>RequestDispatcher</code>对象, 其作为一个包装器封装一个位于某一特定路径或由某一特定名称标识的服务器资源.
 *
 * <p>
 * 该接口目的是包装servlet, 但是一个servlet容器可以创建<code>RequestDispatcher</code>对象来包装资源的任何类型.
 */
public interface RequestDispatcher {

    /**
     * 应该由容器设置的请求属性的名称， 当调用{@link #forward(ServletRequest, ServletResponse)}方法的时候.
     * 它提供请求的与路径相关的属性的原始值.
     */
    static final String FORWARD_REQUEST_URI = "javax.servlet.forward.request_uri";

    /**
     * 应该由容器设置的请求属性的名称，当调用{@link #forward(ServletRequest, ServletResponse)}方法的时候.
     * 它提供请求的与路径相关的属性的原始值.
     */
    static final String FORWARD_CONTEXT_PATH = "javax.servlet.forward.context_path";

    /**
     * 应该由容器设置的请求属性的名称，当调用{@link #forward(ServletRequest, ServletResponse)}方法的时候.
     * 它提供请求的与路径相关的属性的原始值.
     */
    static final String FORWARD_PATH_INFO = "javax.servlet.forward.path_info";

    /**
     * 应该由容器设置的请求属性的名称，当调用{@link #forward(ServletRequest, ServletResponse)}方法的时候.
     * 它提供请求的与路径相关的属性的原始值.
     */
    static final String FORWARD_SERVLET_PATH = "javax.servlet.forward.servlet_path";

    /**
     * 应该由容器设置的请求属性的名称，当调用{@link #forward(ServletRequest, ServletResponse)}方法的时候.
     * 它提供请求的与路径相关的属性的原始值.
     */
    static final String FORWARD_QUERY_STRING = "javax.servlet.forward.query_string";

    /**
     * 应该由容器设置的请求属性的名称，当调用通过路径而不是名称获得的{@code RequestDispatcher}上的{@link #include(ServletRequest, ServletResponse)}方法时.
     * 它提供了这个include调用用于获取{@code RequestDispatcher}实例的路径的信息.
     */
    static final String INCLUDE_REQUEST_URI = "javax.servlet.include.request_uri";

    /**
     * 应该由容器设置的请求属性的名称，当调用通过路径而不是名称获得的{@code RequestDispatcher}上的{@link #include(ServletRequest, ServletResponse)}方法时.
     * 它提供了这个include调用用于获取{@code RequestDispatcher}实例的路径的信息.
     */
    static final String INCLUDE_CONTEXT_PATH = "javax.servlet.include.context_path";

    /**
     * 应该由容器设置的请求属性的名称，当调用通过路径而不是名称获得的{@code RequestDispatcher}上的{@link #include(ServletRequest, ServletResponse)}方法时.
     * 它提供了这个include调用用于获取{@code RequestDispatcher}实例的路径的信息.
     */
    static final String INCLUDE_PATH_INFO = "javax.servlet.include.path_info";

    /**
     * 应该由容器设置的请求属性的名称，当调用通过路径而不是名称获得的{@code RequestDispatcher}上的{@link #include(ServletRequest, ServletResponse)}方法时.
     * 它提供了这个include调用用于获取{@code RequestDispatcher}实例的路径的信息.
     */
    static final String INCLUDE_SERVLET_PATH = "javax.servlet.include.servlet_path";

    /**
     * 应该由容器设置的请求属性的名称，当调用通过路径而不是名称获得的{@code RequestDispatcher}上的{@link #include(ServletRequest, ServletResponse)}方法时.
     * 它提供了这个include调用用于获取{@code RequestDispatcher}实例的路径的信息.
     */
    static final String INCLUDE_QUERY_STRING = "javax.servlet.include.query_string";

    /**
     * 当调用自定义错误处理servlet或JSP页面时，应该由容器设置的请求属性的名称.
     * 属性的值是{@code java.lang.Throwable}类型.
     */
    public static final String ERROR_EXCEPTION = "javax.servlet.error.exception";

    /**
     * 当调用自定义错误处理servlet或JSP页面时，应该由容器设置的请求属性的名称.
     * 属性的值是{@code java.lang.Class}类型.
     */
    public static final String ERROR_EXCEPTION_TYPE = "javax.servlet.error.exception_type";

    /**
     * 当调用自定义错误处理servlet或JSP页面时，应该由容器设置的请求属性的名称.
     * 属性的值是{@code java.lang.String}类型.
     */
    public static final String ERROR_MESSAGE = "javax.servlet.error.message";

    /**
     * 当调用自定义错误处理servlet或JSP页面时，应该由容器设置的请求属性的名称.
     * 属性的值是{@code java.lang.String}类型.
     */
    public static final String ERROR_REQUEST_URI = "javax.servlet.error.request_uri";

    /**
     * 当调用自定义错误处理servlet或JSP页面时，应该由容器设置的请求属性的名称.
     * 属性的值是{@code java.lang.String}类型.
     */
    public static final String ERROR_SERVLET_NAME = "javax.servlet.error.servlet_name";

    /**
     * 当调用自定义错误处理servlet或JSP页面时，应该由容器设置的请求属性的名称.
     * 属性的值是{@code java.lang.Integer}类型.
     */
    public static final String ERROR_STATUS_CODE = "javax.servlet.error.status_code";

    /**
     * 将servlet的请求转发到服务器上的另一个资源（servlet、JSP文件或HTML文件）.
     * 此方法允许一个servlet对请求和其他资源进行初步处理，以生成响应.
     *
     * <p>
     * 对于一个通过<code>getRequestDispatcher()</code>获取的<code>RequestDispatcher</code>, <code>ServletRequest</code>对象
     * 将调整其路径元素和参数匹配目标资源的路径.
     *
     * <p>
     * <code>forward</code>应该在响应提交给客户端之前(在响应主体输出被刷新之前)调用.
     * 如果响应已经提交, 这个方法抛出一个<code>IllegalStateException</code>. 响应缓冲区中未提交的输出在转发前自动清除.
     *
     * <p>
     * 传递给调用servlet的service方法或包装请求和响应的{@link ServletRequestWrapper}或{@link ServletResponseWrapper}子类的请求和响应对象必须是同一个对象.
     *
     *
     * @param request 表示客户端对servlet的请求
     *
     * @param response 表示servlet返回给客户端的响应
     *
     * @exception ServletException 如果目标资源抛出此异常
     * @exception IOException 如果目标资源抛出此异常
     * @exception IllegalStateException 如果响应已经提交
     */
    public void forward(ServletRequest request, ServletResponse response)
            throws ServletException, IOException;

    /**
     * 包含响应中的资源（servlet、JSP页、HTML文件）的内容. 在本质上, 此方法允许编程服务器端包括.
     *
     * <p>
     * {@link ServletResponse}对象具有路径元素，参数与调用方保持不变. 包含的servlet无法更改响应状态代码或设置标头; 任何更改的企图都被忽略.
     *
     * <p>
     * 传递给调用servlet的service方法或包装请求和响应的{@link ServletRequestWrapper}或{@link ServletResponseWrapper}子类的请求和响应对象必须是同一个对象.
     *
     * @param request 表示客户端对servlet的请求
     *
     * @param response 表示servlet返回给客户端的响应
     *
     * @exception ServletException 如果目标资源抛出此异常
     * @exception IOException 如果目标资源抛出此异常
     */
    public void include(ServletRequest request, ServletResponse response)
            throws ServletException, IOException;
}
