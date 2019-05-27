package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Host;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Valve，实现了默认基本行为，为<code>StandardEngine</code>容器实现类.
 * <p>
 * <b>使用约束</b>: 只有在处理HTTP请求时，这种实现才可能有用.
 */
final class StandardEngineValve extends ValveBase {

    //------------------------------------------------------ Constructor
    public StandardEngineValve() {
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
     * 选择合适的子级Host处理这个请求,基于请求的服务器名. 
     * 如果找不到匹配的主机, 返回一个合适的HTTP错误.
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

        // 选择要用于此请求的Host
        Host host = request.getHost();
        if (host == null) {
            response.sendError
                (HttpServletResponse.SC_BAD_REQUEST,
                 sm.getString("standardEngine.noHost",
                              request.getServerName()));
            return;
        }
        if (request.isAsyncSupported()) {
            request.setAsyncSupported(host.getPipeline().isAsyncSupported());
        }

        // Ask this Host to process this request
        host.getPipeline().getFirst().invoke(request, response);
    }
}
