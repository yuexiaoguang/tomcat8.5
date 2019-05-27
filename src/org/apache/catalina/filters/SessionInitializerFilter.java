package org.apache.catalina.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * {@link javax.servlet.Filter}, 通过调用{@link HttpServletRequest}的getSession()方法初始化{@link HttpSession}.
 * <p>
 * 这对于某些具有WebSocket请求的操作是必需的, 在这里初始化HttpSession 对象太晚了, 而且当前Java WebSocket规范没有提供支持.
 */
public class SessionInitializerFilter implements Filter {

    /**
     * 调用{@link HttpServletRequest}的 getSession()方法初始化HttpSession并继续处理链.
     *
     * @param request  处理的请求
     * @param response 与请求相关联的响应
     * @param chain    为该过滤器提供对链中的下一个过滤器的访问，以便对请求和响应进一步处理
     * 
     * @throws IOException      如果在该过滤器处理请求时发生I/O错误
     * @throws ServletException 如果处理失败的任何其他原因
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        ((HttpServletRequest)request).getSession();

        chain.doFilter(request, response);
    }


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // NO-OP
    }

    @Override
    public void destroy() {
        // NO-OP
    }
}
