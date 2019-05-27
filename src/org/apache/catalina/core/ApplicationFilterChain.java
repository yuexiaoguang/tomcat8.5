package org.apache.catalina.core;

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * <code>javax.servlet.FilterChain</code>实现类，用于管理特定请求的一组过滤器的执行. 
 * 当定义的一组过滤器已经执行的时候，下一个调用<code>doFilter()</code>将执行servlet的<code>service()</code>方法本身.
 */
public final class ApplicationFilterChain implements FilterChain {

    // 用于强制要求 SRV.8.2 / SRV.14.2.5.1
    private static final ThreadLocal<ServletRequest> lastServicedRequest;
    private static final ThreadLocal<ServletResponse> lastServicedResponse;

    static {
        if (ApplicationDispatcher.WRAP_SAME_OBJECT) {
            lastServicedRequest = new ThreadLocal<>();
            lastServicedResponse = new ThreadLocal<>();
        } else {
            lastServicedRequest = null;
            lastServicedResponse = null;
        }
    }

    // -------------------------------------------------------------- Constants


    public static final int INCREMENT = 10;


    // ----------------------------------------------------- Instance Variables

    /**
     * Filters.
     */
    private ApplicationFilterConfig[] filters = new ApplicationFilterConfig[0];


    /**
     * 用于维护过滤器链中当前位置的int.
     */
    private int pos = 0;


    /**
     * 给出链中当前过滤器数量的int.
     */
    private int n = 0;


    /**
     * 要由该链执行的servlet实例.
     */
    private Servlet servlet = null;


    /**
     * 关联的servlet实例是否支持异步处理?
     */
    private boolean servletSupportsAsync = false;

    /**
     * The string manager for our package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 当SecurityManager打开和<code>doFilter</code>调用的时候使用.
     */
    private static final Class<?>[] classType = new Class[]{
        ServletRequest.class, ServletResponse.class, FilterChain.class};

    /**
     * 当SecurityManager打开和<code>service</code>调用的时候使用.
     */
    private static final Class<?>[] classTypeUsedInService = new Class[]{
        ServletRequest.class, ServletResponse.class};


    // ---------------------------------------------------- FilterChain Methods

    /**
     * 执行下一个过滤器, 传递指定的 request和response. 
     * 如果没有下一个过滤器, 执行servlet的<code>service()</code>方法.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {

        if( Globals.IS_SECURITY_ENABLED ) {
            final ServletRequest req = request;
            final ServletResponse res = response;
            try {
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction<Void>() {
                        @Override
                        public Void run()
                            throws ServletException, IOException {
                            internalDoFilter(req,res);
                            return null;
                        }
                    }
                );
            } catch( PrivilegedActionException pe) {
                Exception e = pe.getException();
                if (e instanceof ServletException)
                    throw (ServletException) e;
                else if (e instanceof IOException)
                    throw (IOException) e;
                else if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                else
                    throw new ServletException(e.getMessage(), e);
            }
        } else {
            internalDoFilter(request,response);
        }
    }

    private void internalDoFilter(ServletRequest request,
                                  ServletResponse response)
        throws IOException, ServletException {

        // 调用下一个过滤器
        if (pos < n) {
            ApplicationFilterConfig filterConfig = filters[pos++];
            try {
                Filter filter = filterConfig.getFilter();

                if (request.isAsyncSupported() && "false".equalsIgnoreCase(
                        filterConfig.getFilterDef().getAsyncSupported())) {
                    request.setAttribute(Globals.ASYNC_SUPPORTED_ATTR, Boolean.FALSE);
                }
                if( Globals.IS_SECURITY_ENABLED ) {
                    final ServletRequest req = request;
                    final ServletResponse res = response;
                    Principal principal =
                        ((HttpServletRequest) req).getUserPrincipal();

                    Object[] args = new Object[]{req, res, this};
                    SecurityUtil.doAsPrivilege ("doFilter", filter, classType, args, principal);
                } else {
                    filter.doFilter(request, response, this);
                }
            } catch (IOException | ServletException | RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                e = ExceptionUtils.unwrapInvocationTargetException(e);
                ExceptionUtils.handleThrowable(e);
                throw new ServletException(sm.getString("filterChain.filter"), e);
            }
            return;
        }

        // 过滤器链结束 -- 调用servlet 实例
        try {
            if (ApplicationDispatcher.WRAP_SAME_OBJECT) {
                lastServicedRequest.set(request);
                lastServicedResponse.set(response);
            }

            if (request.isAsyncSupported() && !servletSupportsAsync) {
                request.setAttribute(Globals.ASYNC_SUPPORTED_ATTR,
                        Boolean.FALSE);
            }
            // Use potentially wrapped request from this point
            if ((request instanceof HttpServletRequest) &&
                    (response instanceof HttpServletResponse) &&
                    Globals.IS_SECURITY_ENABLED ) {
                final ServletRequest req = request;
                final ServletResponse res = response;
                Principal principal =
                    ((HttpServletRequest) req).getUserPrincipal();
                Object[] args = new Object[]{req, res};
                SecurityUtil.doAsPrivilege("service",
                                           servlet,
                                           classTypeUsedInService,
                                           args,
                                           principal);
            } else {
                servlet.service(request, response);
            }
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            e = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(e);
            throw new ServletException(sm.getString("filterChain.servlet"), e);
        } finally {
            if (ApplicationDispatcher.WRAP_SAME_OBJECT) {
                lastServicedRequest.set(null);
                lastServicedResponse.set(null);
            }
        }
    }


    /**
     * 通过当前线程传递给servlet的最后一个请求.
     *
     * @return 最后的请求.
     */
    public static ServletRequest getLastServicedRequest() {
        return lastServicedRequest.get();
    }


    /**
     * 通过当前线程传递给servlet的最后一个响应.
     *
     * @return 最后的响应.
     */
    public static ServletResponse getLastServicedResponse() {
        return lastServicedResponse.get();
    }


    // -------------------------------------------------------- Package Methods

    /**
     * 添加一个过滤器到过滤器链中.
     *
     * @param filterConfig The FilterConfig for the servlet to be executed
     */
    void addFilter(ApplicationFilterConfig filterConfig) {

        // Prevent the same filter being added multiple times
        for(ApplicationFilterConfig filter:filters)
            if(filter==filterConfig)
                return;

        if (n == filters.length) {
            ApplicationFilterConfig[] newFilters =
                new ApplicationFilterConfig[n + INCREMENT];
            System.arraycopy(filters, 0, newFilters, 0, n);
            filters = newFilters;
        }
        filters[n++] = filterConfig;

    }


    /**
     * 释放对该链执行的过滤器和包装器的引用.
     */
    void release() {
        for (int i = 0; i < n; i++) {
            filters[i] = null;
        }
        n = 0;
        pos = 0;
        servlet = null;
        servletSupportsAsync = false;
    }


    /**
     * 准备重复使用由该链执行的过滤器和包装器.
     */
    void reuse() {
        pos = 0;
    }


    /**
     * 设置将在链的末尾执行的servlet.
     *
     * @param servlet The Wrapper for the servlet to be executed
     */
    void setServlet(Servlet servlet) {
        this.servlet = servlet;
    }


    void setServletSupportsAsync(boolean servletSupportsAsync) {
        this.servletSupportsAsync = servletSupportsAsync;
    }


    /**
     * 标识不支持异步的FilterChain中的Filter.
     *
     * @param result 不支持异步的FilterChain中的每个Filter的完全限定类名集合
     */
    public void findNonAsyncFilters(Set<String> result) {
        for (int i = 0; i < n ; i++) {
            ApplicationFilterConfig filter = filters[i];
            if ("false".equalsIgnoreCase(filter.getFilterDef().getAsyncSupported())) {
                result.add(filter.getFilterClass());
            }
        }
    }
}
