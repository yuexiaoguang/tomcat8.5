package org.apache.catalina.filters;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.Parameters.FailReason;

/**
 * 过滤器, 如果在参数解析过程中出现故障，将拒绝请求. 此过滤器可用于确保客户端提交的所有参数值丢失.
 *
 * <p>
 * 请注意，它具有副作用，它触发参数解析，从而消耗POST请求的正文.
 * 参数解析检查请求的内容类型, 所以使用<code>request.getInputStream()</code> 和 <code>request.getReader()</code>的地址不应该有问题,
 * 如果它们解析的请求不使用内容MIME类型的标准值.
 */
public class FailedRequestFilter extends FilterBase {

    private static final Log log = LogFactory.getLog(FailedRequestFilter.class);

    @Override
    protected Log getLogger() {
        return log;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!isGoodRequest(request)) {
            FailReason reason = (FailReason) request.getAttribute(
                    Globals.PARAMETER_PARSE_FAILED_REASON_ATTR);

            int status;

            switch (reason) {
                case IO_ERROR:
                    // 不是客户端的错
                    status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                    break;
                case POST_TOO_LARGE:
                    status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
                    break;
                case TOO_MANY_PARAMETERS:
                    // 413/414 不是很正确，由于请求主体或URI可以远远低于任何限制集.
                    // 使用默认的.
                case UNKNOWN: // 假设客户端处于故障状态
                // 客户端没有特定的状态码会出错, 所以使用默认的.
                case INVALID_CONTENT_TYPE:
                case MULTIPART_CONFIG_INVALID:
                case NO_NAME:
                case REQUEST_BODY_INCOMPLETE:
                case URL_DECODING:
                case CLIENT_DISCONNECT:
                    // 客户端永远不会看到这一点，所以这只是访问日志. The default is fine.
                default:
                    // 400
                    status = HttpServletResponse.SC_BAD_REQUEST;
                    break;
            }

            ((HttpServletResponse) response).sendError(status);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isGoodRequest(ServletRequest request) {
        // Trigger parsing of parameters
        request.getParameter("none");
        // Detect failure
        if (request.getAttribute(Globals.PARAMETER_PARSE_FAILED_ATTR) != null) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean isConfigProblemFatal() {
        return true;
    }

}
