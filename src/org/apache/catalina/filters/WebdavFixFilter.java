package org.apache.catalina.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 过滤器, 尝试强制MS WebDAV客户端连接端口 80 使用实际工作的WebDAV客户端.
 * 其他可能有帮助的解决方案包括:
 * <ul>
 *   <li>指定端口, 即使是端口80, 试图连接时.</li>
 *   <li>取消第一个身份验证对话框，然后尝试重新连接.</li>
 * </ul>
 *
 * 通常，每个不同版本的MS客户端都有不同的问题.
 * <p>
 * TODO: 更新此过滤器以识别特定的MS客户端，并为该特定客户端应用适当的解决方案
 * <p>
 * 作为过滤器，这与其他过滤器一样配置在Web.xml中. 通常希望将此过滤器映射到WebDava Servlet映射到的任何内容.
 * <p>
 * 除了这个过滤器固定的问题之外, 也观察到以下问题，不能通过该过滤器固定. 在可能的情况下，过滤器将向日志添加消息.
 * <p>
 * XP x64 SP2 (MiniRedir Version 3790)
 * <ul>
 *   <li>只连接到端口80</li>
 *   <li>未知的问题意味着它不起作用</li>
 * </ul>
 */
public class WebdavFixFilter implements Filter {

    private static final String LOG_MESSAGE_PREAMBLE =
        "WebdavFixFilter: Detected client problem: ";

    /* 所有版本的启动字符串 */
    private static final String UA_MINIDIR_START =
        "Microsoft-WebDAV-MiniRedir";
    /* XP 32-bit SP3 */
    private static final String UA_MINIDIR_5_1_2600 =
        "Microsoft-WebDAV-MiniRedir/5.1.2600";

    /* XP 64-bit SP2 */
    private static final String UA_MINIDIR_5_2_3790 =
        "Microsoft-WebDAV-MiniRedir/5.2.3790";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // NOOP
    }

    @Override
    public void destroy() {
        // NOOP
    }

    /**
     * 检查损坏的MS WebDAV客户端，如果检测到重定向，希望能够使用未中断的客户端.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) ||
                !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = ((HttpServletRequest) request);
        HttpServletResponse httpResponse = ((HttpServletResponse) response);
        String ua = httpRequest.getHeader("User-Agent");

        if (ua == null || ua.length() == 0 ||
                !ua.startsWith(UA_MINIDIR_START)) {
            // 没有UA或从非MS值开始
            // Hope everything just works...
            chain.doFilter(request, response);
        } else if (ua.startsWith(UA_MINIDIR_5_1_2600)) {
            // XP 32-bit SP3 - 需要用显式端口重定向
            httpResponse.sendRedirect(buildRedirect(httpRequest));
        } else if (ua.startsWith(UA_MINIDIR_5_2_3790)) {
            // XP 64-bit SP2
            if (!"".equals(httpRequest.getContextPath())) {
                log(httpRequest, "XP-x64-SP2 clients only work with the root context");
            }
            // Namespace issue maybe
            // see http://greenbytes.de/tech/webdav/webdav-redirector-list.html
            log(httpRequest, "XP-x64-SP2 is known not to work with WebDAV Servlet");

            chain.doFilter(request, response);
        } else {
            // 不知道是哪个 MS客户端 - 尝试用显式端口重定向，希望它将客户端移动到一个不同的WebDAV实现
            httpResponse.sendRedirect(buildRedirect(httpRequest));
        }
    }

    private String buildRedirect(HttpServletRequest request) {
        StringBuilder location =
            new StringBuilder(request.getRequestURL().length());
        location.append(request.getScheme());
        location.append("://");
        location.append(request.getServerName());
        location.append(':');
        // 如果包括端口, 即使是 80, 那么MS客户端将使用 WebDAV客户端，而不是和BASIC认证一起使用有问题的MiniRedir
        location.append(request.getServerPort());
        location.append(request.getRequestURI());
        return location.toString();
    }

    private void log(ServletRequest request, String msg) {
        StringBuilder builder = new StringBuilder(LOG_MESSAGE_PREAMBLE);
        builder.append(msg);
        request.getServletContext().log(builder.toString());
    }
}
