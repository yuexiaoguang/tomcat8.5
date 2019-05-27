package org.apache.catalina.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.AsyncDispatcher;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.catalina.servlet4preview.http.ServletMapping;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * <code>RequestDispatcher</code>标准实现类,允许将请求转发到不同的资源以创建最终响应, 
 * 或者将另一个资源的输出包含在该资源的响应中.  
 * 这个实现允许应用程序级servlet包装被传递到调用资源的请求和响应对象, 只要包装的类扩展
 * <code>javax.servlet.ServletRequestWrapper</code> 和
 * <code>javax.servlet.ServletResponseWrapper</code>.
 */
final class ApplicationDispatcher implements AsyncDispatcher, RequestDispatcher {

    /* Servlet 4.0 constants */
    public static final String ASYNC_MAPPING = "javax.servlet.async.mapping";
    public static final String FORWARD_MAPPING = "javax.servlet.forward.mapping";
    public static final String INCLUDE_MAPPING = "javax.servlet.include.mapping";

    static final boolean STRICT_SERVLET_COMPLIANCE;

    static final boolean WRAP_SAME_OBJECT;


    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

        String wrapSameObject = System.getProperty(
                "org.apache.catalina.core.ApplicationDispatcher.WRAP_SAME_OBJECT");
        if (wrapSameObject == null) {
            WRAP_SAME_OBJECT = STRICT_SERVLET_COMPLIANCE;
        } else {
            WRAP_SAME_OBJECT = Boolean.parseBoolean(wrapSameObject);
        }
    }


    protected class PrivilegedForward
            implements PrivilegedExceptionAction<Void> {
        private final ServletRequest request;
        private final ServletResponse response;

        PrivilegedForward(ServletRequest request, ServletResponse response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public Void run() throws java.lang.Exception {
            doForward(request,response);
            return null;
        }
    }

    protected class PrivilegedInclude implements
            PrivilegedExceptionAction<Void> {
        private final ServletRequest request;
        private final ServletResponse response;

        PrivilegedInclude(ServletRequest request, ServletResponse response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public Void run() throws ServletException, IOException {
            doInclude(request, response);
            return null;
        }
    }

    protected class PrivilegedDispatch implements
            PrivilegedExceptionAction<Void> {
        private final ServletRequest request;
        private final ServletResponse response;

        PrivilegedDispatch(ServletRequest request, ServletResponse response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public Void run() throws ServletException, IOException {
            doDispatch(request, response);
            return null;
        }
    }


    /**
     * 用于使用请求调度器时传递状态.
     * 使用实例变量会导致线程问题，并且状态太复杂，无法传递和返回单个ServletRequest 或 ServletResponse 对象.
     */
    private static class State {
        State(ServletRequest request, ServletResponse response,
                boolean including) {
            this.outerRequest = request;
            this.outerResponse = response;
            this.including = including;
        }

        /**
         * 将传递给调用servlet的最外层请求.
         */
        ServletRequest outerRequest = null;


        /**
         * 将被传递给调用servlet的最外层响应.
         */
        ServletResponse outerResponse = null;

        /**
         * 创建并安装的请求包装器.
         */
        ServletRequest wrapRequest = null;


        /**
         * 创建并安装的响应包装器.
         */
        ServletResponse wrapResponse = null;

        /**
         * 是否在执行include(), 而不是 forward()?
         */
        boolean including = false;

        /**
         * 链中最外层的HttpServletRequest
         */
        HttpServletRequest hrequest = null;

        /**
         * 链中最外层的HttpServletResponse
         */
        HttpServletResponse hresponse = null;
    }

    // ----------------------------------------------------------- Constructors


    /**
     * 如果servletPath 和 pathInfo都是<code>null</code>, 将假设RequestDispatcher由名称获取, 而不是路径.
     *
     * @param wrapper 关联的Wrapper，将要重定向或包含的资源(required)
     * @param requestURI 此资源的请求URI
     * @param servletPath 资源修改后的servlet路径
     * @param pathInfo 资源修改后的额外路径信息
     * @param queryString 请求包含的查询字符串参数
     * @param mapping 该资源的映射
     * @param name Servlet name (如果创建了指定的调度程序)或<code>null</code>
     */
    public ApplicationDispatcher(Wrapper wrapper, String requestURI, String servletPath,
         String pathInfo, String queryString, ServletMapping mapping, String name) {

        super();

        // 保存所有的配置参数
        this.wrapper = wrapper;
        this.context = (Context) wrapper.getParent();
        this.requestURI = requestURI;
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
        this.queryString = queryString;
        this.mapping = mapping;
        this.name = name;
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 关联的Context.
     */
    private final Context context;


    /**
     * 指定dispatcher的servlet名称.
     */
    private final String name;


    /**
     * 这个 RequestDispatcher的额外路径信息.
     */
    private final String pathInfo;


    /**
     * 这个RequestDispatcher的查询字符串.
     */
    private final String queryString;


    /**
     * 这个RequestDispatcher的请求URI.
     */
    private final String requestURI;


    /**
     * 这个RequestDispatcher的servlet路径
     */
    private final String servletPath;


    /**
     * 这个RequestDispatcher的映射.
     */
    private final ServletMapping mapping;


    /**
     * The StringManager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 将被重定向或包含的资源关联的Wrapper.
     */
    private final Wrapper wrapper;


    // --------------------------------------------------------- Public Methods


    /**
     * 重定向请求和响应到另一个资源.
     * 任何runtime exception, IOException, ServletException异常将被抛出给调用者.
     *
     * @param request 要转发的servlet请求
     * @param response 要转发的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet异常
     */
    @Override
    public void forward(ServletRequest request, ServletResponse response)
        throws ServletException, IOException
    {
        if (Globals.IS_SECURITY_ENABLED) {
            try {
                PrivilegedForward dp = new PrivilegedForward(request,response);
                AccessController.doPrivileged(dp);
            } catch (PrivilegedActionException pe) {
                Exception e = pe.getException();
                if (e instanceof ServletException)
                    throw (ServletException) e;
                throw (IOException) e;
            }
        } else {
            doForward(request,response);
        }
    }

    private void doForward(ServletRequest request, ServletResponse response)
        throws ServletException, IOException
    {

        // 重置已缓冲的输出，但保留headers/cookies
        if (response.isCommitted()) {
            throw new IllegalStateException
                (sm.getString("applicationDispatcher.forward.ise"));
        }
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            throw e;
        }

        // 设置以处理指定的请求和响应
        State state = new State(request, response, false);

        if (WRAP_SAME_OBJECT) {
            // Check SRV.8.2 / SRV.14.2.5.1 compliance
            checkSameObjects(request, response);
        }

        wrapResponse(state);
        // Handle an HTTP named dispatcher forward
        if ((servletPath == null) && (pathInfo == null)) {

            ApplicationHttpRequest wrequest =
                (ApplicationHttpRequest) wrapRequest(state);
            HttpServletRequest hrequest = state.hrequest;
            wrequest.setRequestURI(hrequest.getRequestURI());
            wrequest.setContextPath(hrequest.getContextPath());
            wrequest.setServletPath(hrequest.getServletPath());
            wrequest.setPathInfo(hrequest.getPathInfo());
            wrequest.setQueryString(hrequest.getQueryString());

            processRequest(request,response,state);
        }

        // Handle an HTTP path-based forward
        else {

            ApplicationHttpRequest wrequest = (ApplicationHttpRequest) wrapRequest(state);
            HttpServletRequest hrequest = state.hrequest;
            if (hrequest.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) == null) {
                wrequest.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI,
                                      hrequest.getRequestURI());
                wrequest.setAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH,
                                      hrequest.getContextPath());
                wrequest.setAttribute(RequestDispatcher.FORWARD_SERVLET_PATH,
                                      hrequest.getServletPath());
                wrequest.setAttribute(RequestDispatcher.FORWARD_PATH_INFO,
                                      hrequest.getPathInfo());
                wrequest.setAttribute(RequestDispatcher.FORWARD_QUERY_STRING,
                                      hrequest.getQueryString());
                ServletMapping mapping;
                if (hrequest instanceof org.apache.catalina.servlet4preview.http.HttpServletRequest) {
                    mapping = ((org.apache.catalina.servlet4preview.http.HttpServletRequest)
                            hrequest).getServletMapping();
                } else {
                    mapping = (new ApplicationMapping(null)).getServletMapping();
                }
                wrequest.setAttribute(FORWARD_MAPPING, mapping);
            }

            wrequest.setContextPath(context.getPath());
            wrequest.setRequestURI(requestURI);
            wrequest.setServletPath(servletPath);
            wrequest.setPathInfo(pathInfo);
            if (queryString != null) {
                wrequest.setQueryString(queryString);
                wrequest.setQueryParams(queryString);
            }
            wrequest.setMapping(mapping);

            processRequest(request,response,state);
        }

        if (request.isAsyncStarted()) {
            // 在转发过程中启动了异步请求, 不要关闭响应，因为它可能在异步处理期间被写入
            return;
        }

        // 这不是真正的关闭以支持错误处理
        if (wrapper.getLogger().isDebugEnabled() )
            wrapper.getLogger().debug(" Disabling the response for further output");

        if  (response instanceof ResponseFacade) {
            ((ResponseFacade) response).finish();
        } else {
            // Servlet SRV.6.2.2. Request/Response可能已经被包装，不再是RequestFacade实例
            if (wrapper.getLogger().isDebugEnabled()){
                wrapper.getLogger().debug( " The Response is vehiculed using a wrapper: "
                           + response.getClass().getName() );
            }

            // Close anyway
            try {
                PrintWriter writer = response.getWriter();
                writer.close();
            } catch (IllegalStateException e) {
                try {
                    ServletOutputStream stream = response.getOutputStream();
                    stream.close();
                } catch (IllegalStateException f) {
                    // Ignore
                } catch (IOException f) {
                    // Ignore
                }
            } catch (IOException e) {
                // Ignore
            }
        }

    }


    /**
     * 根据过滤器配置来准备请求.
     * 
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     * @param state RD状态
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet错误
     */
    private void processRequest(ServletRequest request,
                                ServletResponse response,
                                State state)
        throws IOException, ServletException {

        DispatcherType disInt = (DispatcherType) request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);
        if (disInt != null) {
            boolean doInvoke = true;

            if (context.getFireRequestListenersOnForwards() &&
                    !context.fireRequestInitEvent(request)) {
                doInvoke = false;
            }

            if (doInvoke) {
                if (disInt != DispatcherType.ERROR) {
                    state.outerRequest.setAttribute(
                            Globals.DISPATCHER_REQUEST_PATH_ATTR,
                            getCombinedPath());
                    state.outerRequest.setAttribute(
                            Globals.DISPATCHER_TYPE_ATTR,
                            DispatcherType.FORWARD);
                    invoke(state.outerRequest, response, state);
                } else {
                    invoke(state.outerRequest, response, state);
                }

                if (context.getFireRequestListenersOnForwards()) {
                    context.fireRequestDestroyEvent(request);
                }
            }
        }
    }


    /**
     * 组合servletPath 和 pathInfo.
     * 如果pathInfo是<code>null</code>, 忽略. 如果servletPath 是<code>null</code>返回<code>null</code>.
     * 
     * @return 组合的路径
     */
    private String getCombinedPath() {
        if (servletPath == null) {
            return null;
        }
        if (pathInfo == null) {
            return servletPath;
        }
        return servletPath + pathInfo;
    }


    /**
     * 在当前响应中包含来自另一资源的响应.
     * 任何runtime exception, IOException, ServletException异常将抛出给调用者.
     *
     * @param request 包含这一个的servlet请求
     * @param response 要附加的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet异常
     */
    @Override
    public void include(ServletRequest request, ServletResponse response)
        throws ServletException, IOException
    {
        if (Globals.IS_SECURITY_ENABLED) {
            try {
                PrivilegedInclude dp = new PrivilegedInclude(request,response);
                AccessController.doPrivileged(dp);
            } catch (PrivilegedActionException pe) {
                Exception e = pe.getException();

                if (e instanceof ServletException)
                    throw (ServletException) e;
                throw (IOException) e;
            }
        } else {
            doInclude(request, response);
        }
    }

    private void doInclude(ServletRequest request, ServletResponse response)
            throws ServletException, IOException {

        // 设置以处理指定的请求和响应
        State state = new State(request, response, true);

        if (WRAP_SAME_OBJECT) {
            // Check SRV.8.2 / SRV.14.2.5.1 compliance
            checkSameObjects(request, response);
        }

        // 创建用于此请求的封装响应
        wrapResponse(state);

        // Handle an HTTP named dispatcher include
        if (name != null) {

            ApplicationHttpRequest wrequest =
                (ApplicationHttpRequest) wrapRequest(state);
            wrequest.setAttribute(Globals.NAMED_DISPATCHER_ATTR, name);
            if (servletPath != null)
                wrequest.setServletPath(servletPath);
            wrequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                    DispatcherType.INCLUDE);
            wrequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                    getCombinedPath());
            invoke(state.outerRequest, state.outerResponse, state);
        }

        // Handle an HTTP path based include
        else {

            ApplicationHttpRequest wrequest =
                (ApplicationHttpRequest) wrapRequest(state);
            String contextPath = context.getPath();
            if (requestURI != null)
                wrequest.setAttribute(RequestDispatcher.INCLUDE_REQUEST_URI,
                                      requestURI);
            if (contextPath != null)
                wrequest.setAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH,
                                      contextPath);
            if (servletPath != null)
                wrequest.setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH,
                                      servletPath);
            if (pathInfo != null)
                wrequest.setAttribute(RequestDispatcher.INCLUDE_PATH_INFO,
                                      pathInfo);
            if (queryString != null) {
                wrequest.setAttribute(RequestDispatcher.INCLUDE_QUERY_STRING,
                                      queryString);
                wrequest.setQueryParams(queryString);
            }
            if (mapping != null) {
                wrequest.setAttribute(INCLUDE_MAPPING, mapping);
            }

            wrequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                    DispatcherType.INCLUDE);
            wrequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                    getCombinedPath());
            invoke(state.outerRequest, state.outerResponse, state);
        }

    }


    @Override
    public void dispatch(ServletRequest request, ServletResponse response)
            throws ServletException, IOException {
        if (Globals.IS_SECURITY_ENABLED) {
            try {
                PrivilegedDispatch dp = new PrivilegedDispatch(request,response);
                AccessController.doPrivileged(dp);
            } catch (PrivilegedActionException pe) {
                Exception e = pe.getException();

                if (e instanceof ServletException)
                    throw (ServletException) e;
                throw (IOException) e;
            }
        } else {
            doDispatch(request, response);
        }
    }

    private void doDispatch(ServletRequest request, ServletResponse response)
            throws ServletException, IOException {

        // 设置以处理指定的请求和响应
        State state = new State(request, response, false);

        // 创建用于此请求的封装响应
        wrapResponse(state);

        ApplicationHttpRequest wrequest = (ApplicationHttpRequest) wrapRequest(state);
        HttpServletRequest hrequest = state.hrequest;

        wrequest.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.ASYNC);
        wrequest.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR, getCombinedPath());
        ServletMapping mapping;
        if (hrequest instanceof org.apache.catalina.servlet4preview.http.HttpServletRequest) {
            mapping = ((org.apache.catalina.servlet4preview.http.HttpServletRequest)
                    hrequest).getServletMapping();
        } else {
            mapping = (new ApplicationMapping(null)).getServletMapping();
        }
        wrequest.setAttribute(ASYNC_MAPPING, mapping);

        wrequest.setContextPath(context.getPath());
        wrequest.setRequestURI(requestURI);
        wrequest.setServletPath(servletPath);
        wrequest.setPathInfo(pathInfo);
        if (queryString != null) {
            wrequest.setQueryString(queryString);
            wrequest.setQueryParams(queryString);
        }
        wrequest.setMapping(this.mapping);

        invoke(state.outerRequest, state.outerResponse, state);
    }


    // -------------------------------------------------------- Private Methods


    /**
     * RequestDispatcher代表的资源处理相关的请求, 并创建（或追加）相关的响应.
     * <p>
     * <strong>IMPLEMENTATION NOTE</strong>: 此实现假定没有对转发或包含的资源使用过滤器，因为它们已经为原始请求做过了.
     *
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    private void invoke(ServletRequest request, ServletResponse response,
            State state) throws IOException, ServletException {

        // 看看上下文类加载器是不是当前上下文类加载器. 如果不是, 保存它, 并设置上下文类加载器的Context类加载器
        ClassLoader oldCCL = context.bind(false, null);

        // 初始化可能需要的局部变量
        HttpServletResponse hresponse = state.hresponse;
        Servlet servlet = null;
        IOException ioException = null;
        ServletException servletException = null;
        RuntimeException runtimeException = null;
        boolean unavailable = false;

        // 检查servlet是否被标记为不可用
        if (wrapper.isUnavailable()) {
            wrapper.getLogger().warn(
                    sm.getString("applicationDispatcher.isUnavailable",
                    wrapper.getName()));
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE))
                hresponse.setDateHeader("Retry-After", available);
            hresponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, sm
                    .getString("applicationDispatcher.isUnavailable", wrapper
                            .getName()));
            unavailable = true;
        }

        // 分配servlet实例来处理此请求
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (ServletException e) {
            wrapper.getLogger().error(sm.getString("applicationDispatcher.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            servletException = e;
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            wrapper.getLogger().error(sm.getString("applicationDispatcher.allocateException",
                             wrapper.getName()), e);
            servletException = new ServletException
                (sm.getString("applicationDispatcher.allocateException",
                              wrapper.getName()), e);
            servlet = null;
        }

        // Get the FilterChain Here
        ApplicationFilterChain filterChain =
                ApplicationFilterFactory.createFilterChain(request, wrapper, servlet);

        // Call the service() method for the allocated servlet instance
        try {
            // for includes/forwards
            if ((servlet != null) && (filterChain != null)) {
               filterChain.doFilter(request, response);
             }
            // Servlet Service Method is called by the FilterChain
        } catch (ClientAbortException e) {
            ioException = e;
        } catch (IOException e) {
            wrapper.getLogger().error(sm.getString("applicationDispatcher.serviceException",
                             wrapper.getName()), e);
            ioException = e;
        } catch (UnavailableException e) {
            wrapper.getLogger().error(sm.getString("applicationDispatcher.serviceException",
                             wrapper.getName()), e);
            servletException = e;
            wrapper.unavailable(e);
        } catch (ServletException e) {
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                wrapper.getLogger().error(sm.getString("applicationDispatcher.serviceException",
                        wrapper.getName()), rootCause);
            }
            servletException = e;
        } catch (RuntimeException e) {
            wrapper.getLogger().error(sm.getString("applicationDispatcher.serviceException",
                             wrapper.getName()), e);
            runtimeException = e;
        }

        // 释放此请求的过滤器链
        try {
            if (filterChain != null)
                filterChain.release();
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            wrapper.getLogger().error(sm.getString("standardWrapper.releaseFilters",
                             wrapper.getName()), e);
            // FIXME: Exception handling needs to be similar to what is in the StandardWrapperValue
        }

        // 释放分配的servlet实例
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (ServletException e) {
            wrapper.getLogger().error(sm.getString("applicationDispatcher.deallocateException",
                             wrapper.getName()), e);
            servletException = e;
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            wrapper.getLogger().error(sm.getString("applicationDispatcher.deallocateException",
                             wrapper.getName()), e);
            servletException = new ServletException
                (sm.getString("applicationDispatcher.deallocateException",
                              wrapper.getName()), e);
        }

        // 重置旧的上下文类装入器
        context.unbind(false, oldCCL);

        // Unwrap request/response if needed
        // See Bugzilla 30949
        unwrapRequest(state);
        unwrapResponse(state);
        // Recycle request if necessary (also BZ 30949)
        recycleRequestWrapper(state);

        // 重新抛出异常如果一个被调用Servlet抛出
        if (ioException != null)
            throw ioException;
        if (servletException != null)
            throw servletException;
        if (runtimeException != null)
            throw runtimeException;

    }


    /**
     * 解封装请求.
     */
    private void unwrapRequest(State state) {

        if (state.wrapRequest == null)
            return;

        if (state.outerRequest.isAsyncStarted()) {
            if (!state.outerRequest.getAsyncContext().hasOriginalRequestAndResponse()) {
                return;
            }
        }

        ServletRequest previous = null;
        ServletRequest current = state.outerRequest;
        while (current != null) {

            // 如果遇到容器请求，就完成了
            if ((current instanceof Request)
                || (current instanceof RequestFacade))
                break;

            // 删除当前请求，如果它是包装器
            if (current == state.wrapRequest) {
                ServletRequest next =
                  ((ServletRequestWrapper) current).getRequest();
                if (previous == null)
                    state.outerRequest = next;
                else
                    ((ServletRequestWrapper) previous).setRequest(next);
                break;
            }

            // 前进到链中的下一个请求
            previous = current;
            current = ((ServletRequestWrapper) current).getRequest();
        }
    }

    /**
     * 解封装响应.
     */
    private void unwrapResponse(State state) {

        if (state.wrapResponse == null)
            return;

        if (state.outerRequest.isAsyncStarted()) {
            if (!state.outerRequest.getAsyncContext().hasOriginalRequestAndResponse()) {
                return;
            }
        }

        ServletResponse previous = null;
        ServletResponse current = state.outerResponse;
        while (current != null) {

            // 如果遇到容器响应，就完成了
            if ((current instanceof Response)
                || (current instanceof ResponseFacade))
                break;

            // 如果它是包装器，请删除当前响应
            if (current == state.wrapResponse) {
                ServletResponse next =
                  ((ServletResponseWrapper) current).getResponse();
                if (previous == null)
                    state.outerResponse = next;
                else
                    ((ServletResponseWrapper) previous).setResponse(next);
                break;
            }

            // 前进到链中的下一个响应
            previous = current;
            current = ((ServletResponseWrapper) current).getResponse();
        }
    }


    /**
     * 创建并返回一个包装的请求，已插入请求链中的适当位置.
     */
    private ServletRequest wrapRequest(State state) {

        // 定位我们应该在前面插入的请求
        ServletRequest previous = null;
        ServletRequest current = state.outerRequest;
        while (current != null) {
            if(state.hrequest == null && (current instanceof HttpServletRequest))
                state.hrequest = (HttpServletRequest)current;
            if (!(current instanceof ServletRequestWrapper))
                break;
            if (current instanceof ApplicationHttpRequest)
                break;
            if (current instanceof ApplicationRequest)
                break;
            previous = current;
            current = ((ServletRequestWrapper) current).getRequest();
        }

        // 实例化一个新的包装器并将其插入到链中
        ServletRequest wrapper = null;
        if ((current instanceof ApplicationHttpRequest) ||
            (current instanceof Request) ||
            (current instanceof HttpServletRequest)) {
            // Compute a crossContext flag
            HttpServletRequest hcurrent = (HttpServletRequest) current;
            boolean crossContext = false;
            if ((state.outerRequest instanceof ApplicationHttpRequest) ||
                (state.outerRequest instanceof Request) ||
                (state.outerRequest instanceof HttpServletRequest)) {
                HttpServletRequest houterRequest =
                    (HttpServletRequest) state.outerRequest;
                Object contextPath = houterRequest.getAttribute(
                        RequestDispatcher.INCLUDE_CONTEXT_PATH);
                if (contextPath == null) {
                    // Forward
                    contextPath = houterRequest.getContextPath();
                }
                crossContext = !(context.getPath().equals(contextPath));
            }
            wrapper = new ApplicationHttpRequest
                (hcurrent, context, crossContext);
        } else {
            wrapper = new ApplicationRequest(current);
        }
        if (previous == null)
            state.outerRequest = wrapper;
        else
            ((ServletRequestWrapper) previous).setRequest(wrapper);
        state.wrapRequest = wrapper;
        return (wrapper);

    }


    /**
     * 创建并返回一个包装的响应，已插入响应链中的适当位置.
     */
    private ServletResponse wrapResponse(State state) {

        // 定位应该在前面插入的响应
        ServletResponse previous = null;
        ServletResponse current = state.outerResponse;
        while (current != null) {
            if(state.hresponse == null && (current instanceof HttpServletResponse)) {
                state.hresponse = (HttpServletResponse)current;
                if(!state.including) // Forward only needs hresponse
                    return null;
            }
            if (!(current instanceof ServletResponseWrapper))
                break;
            if (current instanceof ApplicationHttpResponse)
                break;
            if (current instanceof ApplicationResponse)
                break;
            previous = current;
            current = ((ServletResponseWrapper) current).getResponse();
        }

        // 实例化一个新的包装器并将其插入到链中
        ServletResponse wrapper = null;
        if ((current instanceof ApplicationHttpResponse) ||
            (current instanceof Response) ||
            (current instanceof HttpServletResponse))
            wrapper =
                new ApplicationHttpResponse((HttpServletResponse) current,
                        state.including);
        else
            wrapper = new ApplicationResponse(current, state.including);
        if (previous == null)
            state.outerResponse = wrapper;
        else
            ((ServletResponseWrapper) previous).setResponse(wrapper);
        state.wrapResponse = wrapper;
        return (wrapper);

    }

    private void checkSameObjects(ServletRequest appRequest,
            ServletResponse appResponse) throws ServletException {
        ServletRequest originalRequest =
            ApplicationFilterChain.getLastServicedRequest();
        ServletResponse originalResponse =
            ApplicationFilterChain.getLastServicedResponse();

        // Some forwards, eg from valves will not set original values
        if (originalRequest == null || originalResponse == null) {
            return;
        }

        boolean same = false;
        ServletRequest dispatchedRequest = appRequest;

        // 查找传递给service方法的请求
        while (originalRequest instanceof ServletRequestWrapper &&
                ((ServletRequestWrapper) originalRequest).getRequest()!=null ) {
            originalRequest =
                ((ServletRequestWrapper) originalRequest).getRequest();
        }
        // 与发送请求比较
        while (!same) {
            if (originalRequest.equals(dispatchedRequest)) {
                same = true;
            }
            if (!same && dispatchedRequest instanceof ServletRequestWrapper) {
                dispatchedRequest =
                    ((ServletRequestWrapper) dispatchedRequest).getRequest();
            } else {
                break;
            }
        }
        if (!same) {
            throw new ServletException(sm.getString(
                    "applicationDispatcher.specViolation.request"));
        }

        same = false;
        ServletResponse dispatchedResponse = appResponse;

        // 查找传递给service方法的响应
        while (originalResponse instanceof ServletResponseWrapper &&
                ((ServletResponseWrapper) originalResponse).getResponse() !=
                    null ) {
            originalResponse =
                ((ServletResponseWrapper) originalResponse).getResponse();
        }
        // 与分派的响应比较
        while (!same) {
            if (originalResponse.equals(dispatchedResponse)) {
                same = true;
            }

            if (!same && dispatchedResponse instanceof ServletResponseWrapper) {
                dispatchedResponse =
                    ((ServletResponseWrapper) dispatchedResponse).getResponse();
            } else {
                break;
            }
        }

        if (!same) {
            throw new ServletException(sm.getString(
                    "applicationDispatcher.specViolation.response"));
        }
    }

    private void recycleRequestWrapper(State state) {
        if (state.wrapRequest instanceof ApplicationHttpRequest) {
            ((ApplicationHttpRequest) state.wrapRequest).recycle();        }
    }
}
