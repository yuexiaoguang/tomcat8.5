package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.res.StringManager;

/**
 * 实现<code>StandardContext</code>容器实现类的默认基本行为.
 * <p>
 * <b>使用约束</b>: 只有在处理HTTP请求时，这种实现才可能有用.
 */
final class StandardContextValve extends ValveBase {

    private static final StringManager sm = StringManager.getManager(StandardContextValve.class);

    public StandardContextValve() {
        super(true);
    }


    /**
     * 选择合适的子级Wrapper处理这个请求, 基于指定的请求URI. 
     * 如果没有匹配的Wrapper, 返回一个适当的HTTP错误.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet错误
     */
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 禁止任何直接访问WEB-INF或META-INF文件夹下的资源
        MessageBytes requestPathMB = request.getRequestPathMB();
        if ((requestPathMB.startsWithIgnoreCase("/META-INF/", 0))
                || (requestPathMB.equalsIgnoreCase("/META-INF"))
                || (requestPathMB.startsWithIgnoreCase("/WEB-INF/", 0))
                || (requestPathMB.equalsIgnoreCase("/WEB-INF"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Select the Wrapper to be used for this Request
        Wrapper wrapper = request.getWrapper();
        if (wrapper == null || wrapper.isUnavailable()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Acknowledge the request
        try {
            response.sendAcknowledgement();
        } catch (IOException ioe) {
            container.getLogger().error(sm.getString(
                    "standardContextValve.acknowledgeException"), ioe);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        if (request.isAsyncSupported()) {
            request.setAsyncSupported(wrapper.getPipeline().isAsyncSupported());
        }
        wrapper.getPipeline().getFirst().invoke(request, response);
    }
}
