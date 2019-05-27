package javax.servlet;

import java.io.IOException;

/**
 * FilterChain 是一个servlet容器提供给开发者的对象，给过滤的资源请求的调用链一个视图.
 * 过滤器使用FilterChain执行链中下一个过滤器, 或者如果调用的过滤器是链中最后一个过滤器, 执行链中结尾的资源.
 */
public interface FilterChain {

    /**
     * 执行链中下一个过滤器, 或者如果调用的过滤器是链中最后一个过滤器, 执行链中结尾的资源.
     *
     * @param request 链中传递的请求.
     * @param response 链中传递的响应.
     *
     * @throws IOException 如果在请求处理过程中发生I/O错误
     * @throws ServletException 如果处理因其他原因失败
     */
    public void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, ServletException;

}
