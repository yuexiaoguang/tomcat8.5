package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.res.StringManager;

/**
 * 实现了Valve的默认基本行为.
 * <p>
 * <b>使用约束</b>: 只有在处理HTTP请求时，这种实现才可能有用.
 */
final class StandardHostValve extends ValveBase {

    private static final Log log = LogFactory.getLog(StandardHostValve.class);

    // 保存每个请求的getClassLoader()调用.
    // 在高负载下，这些调用只花了足够长的时间出现在分析器中作为热点（虽然非常小）.
    private static final ClassLoader MY_CLASSLOADER = StandardHostValve.class.getClassLoader();

    static final boolean STRICT_SERVLET_COMPLIANCE;

    static final boolean ACCESS_SESSION;

    static {
        STRICT_SERVLET_COMPLIANCE = Globals.STRICT_SERVLET_COMPLIANCE;

        String accessSession = System.getProperty(
                "org.apache.catalina.core.StandardHostValve.ACCESS_SESSION");
        if (accessSession == null) {
            ACCESS_SESSION = STRICT_SERVLET_COMPLIANCE;
        } else {
            ACCESS_SESSION = Boolean.parseBoolean(accessSession);
        }
    }

    //------------------------------------------------------ Constructor
    public StandardHostValve() {
        super(true);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods

    /**
     * 选择合适的子级Context处理这个请求, 基于指定的请求URI.
     * 如果找不到匹配的上下文，返回一个适当的HTTP错误.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 选择要用于此请求的上下文
        Context context = request.getContext();
        if (context == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                 sm.getString("standardHost.noContext"));
            return;
        }

        if (request.isAsyncSupported()) {
            request.setAsyncSupported(context.getPipeline().isAsyncSupported());
        }

        boolean asyncAtStart = request.isAsync();
        boolean asyncDispatching = request.isAsyncDispatching();

        try {
            context.bind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);

            if (!asyncAtStart && !context.fireRequestInitEvent(request.getRequest())) {
                // 不要在异步处理过程中触发监听器(调用startAsync()的请求触发监听器).
                // 如果请求init监听器抛出异常, 请求中止.
                return;
            }

            // 让这个Context处理这个请求. 处于异步模式且未被分配到此资源的请求必须是错误的，并路由到这里以检查应用程序定义的错误页.
            try {
                if (!asyncAtStart || asyncDispatching) {
                    context.getPipeline().getFirst().invoke(request, response);
                } else {
                    // 确保此请求/响应需要一个错误报告.
                    if (!response.isErrorReportRequired()) {
                        throw new IllegalStateException(sm.getString("standardHost.asyncStateError"));
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                container.getLogger().error("Exception Processing " + request.getRequestURI(), t);
                // 如果尝试报告以前的错误时发生新的错误，则允许报告原始错误.
                if (!response.isErrorReportRequired()) {
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
                    throwable(request, response, t);
                }
            }

            // 现在，请求/响应对回到容器控制之下，解除暂停，从而可以完成错误处理和/或容器可以刷新任何剩余数据
            response.setSuspended(false);

            Throwable t = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

            // 如果在长时间运行的请求中上下文被破坏，则保护NPE.
            if (!context.getState().isAvailable()) {
                return;
            }

            // 查找（并呈现）应用程序级别错误页
            if (response.isErrorReportRequired()) {
                if (t != null) {
                    throwable(request, response, t);
                } else {
                    status(request, response);
                }
            }

            if (!request.isAsync() && !asyncAtStart) {
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } finally {
            // 访问会话以更新上次访问时间, 基于对规范的严格解释
            if (ACCESS_SESSION) {
                request.getSession(false);
            }

            context.unbind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);
        }
    }


    // -------------------------------------------------------- Private Methods

    /**
     * 处理生成的HTTP状态码（和相应的消息）, 在处理指定请求期间. 在生成异常报告期间发生的任何异常都记录并重定向.
     *
     * @param request The request being processed
     * @param response The response being generated
     */
    private void status(Request request, Response response) {

        int statusCode = response.getStatus();

        // 处理此状态码的自定义错误页
        Context context = request.getContext();
        if (context == null) {
            return;
        }

        /* 只查找错误页面, 当isError()被设置.
         * isError()被设置, 当response.sendError()被调用的时候. 这允许自定义错误页而不依赖于web.xml的缺省值.
         */
        if (!response.isError()) {
            return;
        }

        ErrorPage errorPage = context.findErrorPage(statusCode);
        if (errorPage == null) {
            // 查找默认错误页
            errorPage = context.findErrorPage(0);
        }
        if (errorPage != null && response.isErrorReportRequired()) {
            response.setAppCommitted(false);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                              Integer.valueOf(statusCode));

            String message = response.getMessage();
            if (message == null) {
                message = "";
            }
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
            request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                    errorPage.getLocation());
            request.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                    DispatcherType.ERROR);


            Wrapper wrapper = request.getWrapper();
            if (wrapper != null) {
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,
                                  wrapper.getName());
            }
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                                 request.getRequestURI());
            if (custom(request, response, errorPage)) {
                response.setErrorReported();
                try {
                    response.finishResponse();
                } catch (ClientAbortException e) {
                    // Ignore
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        }
    }


    /**
     * 在处理指定的请求期间，处理指定的遇到的异常. 在生成异常报告期间发生的任何异常都记录并重定向.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable 发生的异常(可能导致一个根异常
     */
    protected void throwable(Request request, Response response,
                             Throwable throwable) {
        Context context = request.getContext();
        if (context == null) {
            return;
        }

        Throwable realError = throwable;

        if (realError instanceof ServletException) {
            realError = ((ServletException) realError).getRootCause();
            if (realError == null) {
                realError = throwable;
            }
        }

        // 如果这是客户端放弃的请求，只需记录并返回
        if (realError instanceof ClientAbortException ) {
            if (log.isDebugEnabled()) {
                log.debug
                    (sm.getString("standardHost.clientAbort",
                        realError.getCause().getMessage()));
            }
            return;
        }

        ErrorPage errorPage = findErrorPage(context, throwable);
        if ((errorPage == null) && (realError != throwable)) {
            errorPage = findErrorPage(context, realError);
        }

        if (errorPage != null) {
            if (response.setErrorReported()) {
                response.setAppCommitted(false);
                request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                        errorPage.getLocation());
                request.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                        DispatcherType.ERROR);
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                        Integer.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
                request.setAttribute(RequestDispatcher.ERROR_MESSAGE,
                                  throwable.getMessage());
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                                  realError);
                Wrapper wrapper = request.getWrapper();
                if (wrapper != null) {
                    request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME,
                                      wrapper.getName());
                }
                request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                                     request.getRequestURI());
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE,
                                  realError.getClass());
                if (custom(request, response, errorPage)) {
                    try {
                        response.finishResponse();
                    } catch (IOException e) {
                        container.getLogger().warn("Exception Processing " + errorPage, e);
                    }
                }
            }
        } else {
        	// 对于请求处理期间抛出的异常，尚未定义自定义错误页.
            // 检查是否指定了错误代码500的错误页，如果指定了,将该页发送回作为响应.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // The response is an error
            response.setError();

            status(request, response);
        }
    }


    /**
     * 处理一个HTTP状态码或Java异常通过转发控制指定errorPage对象中的位置.
     * 假定调用方已经记录了要转发到该页面的任何请求属性.
     * 返回<code>true</code>如果成功地使用指定的错误页面位置, 或<code>false</code> 如果应该呈现默认错误报告.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param errorPage 遵守的errorPage指令
     */
    private boolean custom(Request request, Response response,
                             ErrorPage errorPage) {

        if (container.getLogger().isDebugEnabled()) {
            container.getLogger().debug("Processing " + errorPage);
        }

        try {
            // 将控制转发到指定位置
            ServletContext servletContext =
                request.getContext().getServletContext();
            RequestDispatcher rd =
                servletContext.getRequestDispatcher(errorPage.getLocation());

            if (rd == null) {
                container.getLogger().error(
                    sm.getString("standardHostValue.customStatusFailed", errorPage.getLocation()));
                return false;
            }

            if (response.isCommitted()) {
                // 响应被提交 - 包括错误页面是我们能做的最好的
                rd.include(request.getRequest(), response.getResponse());
            } else {
                // 重置响应 (保持真正的错误代码和消息)
                response.resetBuffer(true);
                response.setContentLength(-1);

                rd.forward(request.getRequest(), response.getResponse());

                // 如果转发, 响应再次暂停
                response.setSuspended(false);
            }

            // 是否成功处理这个自定义页面
            return true;

        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // 报告未能处理这个自定义页面
            container.getLogger().error("Exception Processing " + errorPage, t);
            return false;
        }
    }


    /**
     * 找到并返回指定异常类的ErrorPage实例, 或最近的父类的 ErrorPage实例. 如果没有找到 ErrorPage实例, 返回<code>null</code>.
     *
     * @param context 要搜索的Context
     * @param exception 要查找的ErrorPage的异常
     */
    private static ErrorPage findErrorPage(Context context, Throwable exception) {

        if (exception == null) {
            return (null);
        }
        Class<?> clazz = exception.getClass();
        String name = clazz.getName();
        while (!Object.class.equals(clazz)) {
            ErrorPage errorPage = context.findErrorPage(name);
            if (errorPage != null) {
                return (errorPage);
            }
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                break;
            }
            name = clazz.getName();
        }
        return (null);
    }
}
