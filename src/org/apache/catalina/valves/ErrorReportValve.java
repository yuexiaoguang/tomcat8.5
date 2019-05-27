package org.apache.catalina.valves;

import java.io.IOException;
import java.io.Writer;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.TomcatCSS;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * <p>Valve实现类输出HTML错误页面.</p>
 *
 * <p>这个Valve 应该附加在Host等级, 如果附加到Context等级，它也可以工作.</p>
 *
 * <p>HTML code from the Cocoon 2 project.</p>
 */
public class ErrorReportValve extends ValveBase {

    private boolean showReport = true;

    private boolean showServerInfo = true;

    //------------------------------------------------------ Constructor
    public ErrorReportValve() {
        super(true);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 执行序列中的下一个Valve. 当执行返回时, 检查响应状态.
     * 如果状态码大于等于 400, 或者抛出一个异常, 然后将触发错误处理.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        // Perform the request
        getNext().invoke(request, response);

        if (response.isCommitted()) {
            if (response.setErrorReported()) {
                // 以前未报告错误，但由于响应已提交，无法写入错误页. 尝试刷新仍要写入客户端的任何数据.
                try {
                    response.flushBuffer();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }
                // 立即向客户发出出错的信号
                response.getCoyoteResponse().action(ActionCode.CLOSE_NOW, null);
            }
            return;
        }

        Throwable throwable = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        // 如果异步请求正在进行，并且一旦容器线程结束，就不会结束, 不要在这里处理任何错误页面.
        if (request.isAsync() && !request.isAsyncCompleting()) {
            return;
        }

        if (throwable != null && !response.isError()) {
            // 确保响应时调用了必要的方法. (一个组件可能已经设置了 Throwable. Tomcat不会这样做，但其他组件可能会.)
            // 在这一点上，这些都是安全的，因为知道响应没有被提交.
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        // 不管怎样, response.sendError()已经被调用, 在执行之前到达此点并暂停响应. 需要反转，所以这个阀门可以写入响应.
        response.setSuspended(false);

        try {
            report(request, response, throwable);
        } catch (Throwable tt) {
            ExceptionUtils.handleThrowable(tt);
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 打印出错误报告.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    protected void report(Request request, Response response, Throwable throwable) {

        int statusCode = response.getStatus();

        // Do nothing on a 1xx, 2xx and 3xx status
        // Do nothing if anything has been written already
        // Do nothing 如果响应未显式地标记为错误，则未报告错误.
        if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }

        // 如果发生错误，阻止进一步的I/O, 不要浪费时间产生一个永远不会被读取的错误报告
        AtomicBoolean result = new AtomicBoolean(false);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, result);
        if (!result.get()) {
            return;
        }

        String message = Escape.htmlElementContent(response.getMessage());
        if (message == null) {
            if (throwable != null) {
                String exceptionMessage = throwable.getMessage();
                if (exceptionMessage != null && exceptionMessage.length() > 0) {
                    message = Escape.htmlElementContent((new Scanner(exceptionMessage)).nextLine());
                }
            }
            if (message == null) {
                message = "";
            }
        }

        // 如果没有指定的状态码的原因，并且没有提供错误消息，则不做任何事
        String reason = null;
        String description = null;
        StringManager smClient = StringManager.getManager(
                Constants.Package, request.getLocales());
        response.setLocale(smClient.getLocale());
        try {
            reason = smClient.getString("http." + statusCode + ".reason");
            description = smClient.getString("http." + statusCode + ".desc");
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }
        if (reason == null || description == null) {
            if (message.isEmpty()) {
                return;
            } else {
                reason = smClient.getString("errorReportValve.unknownReason");
                description = smClient.getString("errorReportValve.noDescription");
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append("<!doctype html><html lang=\"");
        sb.append(smClient.getLocale().getLanguage()).append("\">");
        sb.append("<head>");
        sb.append("<title>");
        sb.append(smClient.getString("errorReportValve.statusHeader",
                String.valueOf(statusCode), reason));
        sb.append("</title>");
        sb.append("<style type=\"text/css\">");
        sb.append(TomcatCSS.TOMCAT_CSS);
        sb.append("</style>");
        sb.append("</head><body>");
        sb.append("<h1>");
        sb.append(smClient.getString("errorReportValve.statusHeader",
                String.valueOf(statusCode), reason)).append("</h1>");
        if (isShowReport()) {
            sb.append("<hr class=\"line\" />");
            sb.append("<p><b>");
            sb.append(smClient.getString("errorReportValve.type"));
            sb.append("</b> ");
            if (throwable != null) {
                sb.append(smClient.getString("errorReportValve.exceptionReport"));
            } else {
                sb.append(smClient.getString("errorReportValve.statusReport"));
            }
            sb.append("</p>");
            if (!message.isEmpty()) {
                sb.append("<p><b>");
                sb.append(smClient.getString("errorReportValve.message"));
                sb.append("</b> ");
                sb.append(message).append("</p>");
            }
            sb.append("<p><b>");
            sb.append(smClient.getString("errorReportValve.description"));
            sb.append("</b> ");
            sb.append(description);
            sb.append("</p>");
            if (throwable != null) {
                String stackTrace = getPartialServletStackTrace(throwable);
                sb.append("<p><b>");
                sb.append(smClient.getString("errorReportValve.exception"));
                sb.append("</b></p><pre>");
                sb.append(Escape.htmlElementContent(stackTrace));
                sb.append("</pre>");

                int loops = 0;
                Throwable rootCause = throwable.getCause();
                while (rootCause != null && (loops < 10)) {
                    stackTrace = getPartialServletStackTrace(rootCause);
                    sb.append("<p><b>");
                    sb.append(smClient.getString("errorReportValve.rootCause"));
                    sb.append("</b></p><pre>");
                    sb.append(Escape.htmlElementContent(stackTrace));
                    sb.append("</pre>");
                    // In case root cause is somehow heavily nested
                    rootCause = rootCause.getCause();
                    loops++;
                }

                sb.append("<p><b>");
                sb.append(smClient.getString("errorReportValve.note"));
                sb.append("</b> ");
                sb.append(smClient.getString("errorReportValve.rootCauseInLogs"));
                sb.append("</p>");

            }
            sb.append("<hr class=\"line\" />");
        }
        if (isShowServerInfo()) {
            sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        }
        sb.append("</body></html>");

        try {
            try {
                response.setContentType("text/html");
                response.setCharacterEncoding("utf-8");
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (container.getLogger().isDebugEnabled()) {
                    container.getLogger().debug("status.setContentType", t);
                }
            }
            Writer writer = response.getReporter();
            if (writer != null) {
                // If writer is null, it's an indication that the response has
                // been hard committed already, which should never happen
                writer.write(sb.toString());
                response.finishResponse();
            }
        } catch (IOException e) {
            // Ignore
        } catch (IllegalStateException e) {
            // Ignore
        }

    }


    /**
     * 打印出部分servlet堆栈跟踪(在最后一次javax.servlet.出现时截断).
     * 
     * @param t 堆栈跟踪
     * @return 与应用层相关的堆栈跟踪
     */
    protected String getPartialServletStackTrace(Throwable t) {
        StringBuilder trace = new StringBuilder();
        trace.append(t.toString()).append(System.lineSeparator());
        StackTraceElement[] elements = t.getStackTrace();
        int pos = elements.length;
        for (int i = elements.length - 1; i >= 0; i--) {
            if ((elements[i].getClassName().startsWith
                 ("org.apache.catalina.core.ApplicationFilterChain"))
                && (elements[i].getMethodName().equals("internalDoFilter"))) {
                pos = i;
                break;
            }
        }
        for (int i = 0; i < pos; i++) {
            if (!(elements[i].getClassName().startsWith
                  ("org.apache.catalina.core."))) {
                trace.append('\t').append(elements[i].toString()).append(System.lineSeparator());
            }
        }
        return trace.toString();
    }

    /**
     * 启用/禁用完全错误报告
     *
     * @param showReport <code>true</code>显示完全错误数据
     */
    public void setShowReport(boolean showReport) {
        this.showReport = showReport;
    }

    public boolean isShowReport() {
        return showReport;
    }

    /**
     * 启用/禁用错误页面上的服务器信息
     *
     * @param showServerInfo <code>true</code>显示服务器信息
     */
    public void setShowServerInfo(boolean showServerInfo) {
        this.showServerInfo = showServerInfo;
    }

    public boolean isShowServerInfo() {
        return showServerInfo;
    }
}
