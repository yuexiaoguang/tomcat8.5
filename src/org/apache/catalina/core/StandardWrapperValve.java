package org.apache.catalina.core;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * 实现Valve的默认基本行为.
 */
final class StandardWrapperValve extends ValveBase {

    //------------------------------------------------------ Constructor
    public StandardWrapperValve() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables


//  一些JMX统计. 这个vavle 被关联到StandardWrapper.
    // We exponse the StandardWrapper as JMX ( j2eeType=Servlet ).这里的字段用于性能.
    private volatile long processingTime;
    private volatile long maxTime;
    private volatile long minTime = Long.MAX_VALUE;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    /**
     * 调用正在管理的servlet, 遵守servlet lifecycle和SingleThreadModel支持的有关规则.
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

        // 初始化可能需要的局部变量
        boolean unavailable = false;
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1=System.currentTimeMillis();
        requestCount.incrementAndGet();
        StandardWrapper wrapper = (StandardWrapper) getContainer();
        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();

        // 检查应用程序是否被标记为不可用
        if (!context.getState().isAvailable()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardContext.isUnavailable"));
            unavailable = true;
        }

        // 检查servlet是否被标记为不可用
        if (!unavailable && wrapper.isUnavailable()) {
            container.getLogger().info(sm.getString("standardWrapper.isUnavailable",
                    wrapper.getName()));
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        sm.getString("standardWrapper.isUnavailable",
                                wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        sm.getString("standardWrapper.notFound",
                                wrapper.getName()));
            }
            unavailable = true;
        }

        // 分配servlet实例来处理此请求
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (UnavailableException e) {
            container.getLogger().error(
                    sm.getString("standardWrapper.allocateException",
                            wrapper.getName()), e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                           sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }

        MessageBytes requestPathMB = request.getRequestPathMB();
        DispatcherType dispatcherType = DispatcherType.REQUEST;
        if (request.getDispatcherType()==DispatcherType.ASYNC) dispatcherType = DispatcherType.ASYNC;
        request.setAttribute(Globals.DISPATCHER_TYPE_ATTR,dispatcherType);
        request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                requestPathMB);
        // 为此请求创建过滤器链
        ApplicationFilterChain filterChain =
                ApplicationFilterFactory.createFilterChain(request, wrapper, servlet);

        // 调用这个请求的过滤器链
        // NOTE: 也调用servlet的 service() 方法
        try {
            if ((servlet != null) && (filterChain != null)) {
                // Swallow output if needed
                if (context.getSwallowOutput()) {
                    try {
                        SystemLogHandler.startCapture();
                        if (request.isAsyncDispatching()) {
                            request.getAsyncContextInternal().doInternalDispatch();
                        } else {
                            filterChain.doFilter(request.getRequest(),
                                    response.getResponse());
                        }
                    } finally {
                        String log = SystemLogHandler.stopCapture();
                        if (log != null && log.length() > 0) {
                            context.getLogger().info(log);
                        }
                    }
                } else {
                    if (request.isAsyncDispatching()) {
                        request.getAsyncContextInternal().doInternalDispatch();
                    } else {
                        filterChain.doFilter
                            (request.getRequest(), response.getResponse());
                    }
                }

            }
        } catch (ClientAbortException e) {
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            //            throwable = e;
            //            exception(request, response, e);
            wrapper.unavailable(e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString(
                        "standardWrapper.serviceExceptionRoot",
                        wrapper.getName(), context.getName(), e.getMessage()),
                        rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            throwable = e;
            exception(request, response, e);
        }

        // 释放过滤器链
        if (filterChain != null) {
            filterChain.release();
        }

        // 释放分配的servlet实例
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // 如果这个servlet被标记为永久不可用, 卸载它并释放这个实例
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }
        long t2=System.currentTimeMillis();

        long time=t2-t1;
        processingTime += time;
        if( time > maxTime) maxTime=time;
        if( time < minTime) minTime=time;
    }


    // -------------------------------------------------------- Private Methods

    /**
     * 处理请求的过程中遇到的ServletException.  在生成异常报告期间发生的任何异常都记录并转发.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param exception 发生的异常(可能导致一个根异常
     */
    private void exception(Request request, Response response, Throwable exception) {
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setError();
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public void incrementErrorCount() {
        errorCount.incrementAndGet();
    }

    @Override
    protected void initInternal() throws LifecycleException {
        // NOOP - Don't register this Valve in JMX
    }
}
