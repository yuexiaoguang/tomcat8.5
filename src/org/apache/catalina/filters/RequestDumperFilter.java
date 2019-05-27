package org.apache.catalina.filters;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <p>Filter实现类, 从指定的Request和对应的Response记录相关内容. 它特别适用于调试与header和cookie有关的问题.</p>
 *
 * <p>当使用这个Filter的时候, 强烈建议<code>org.apache.catalina.filter.RequestDumperFilter</code> logger 指向专用文件,
 * 并使用<code>org.apache.juli.VerbatimFormatter</code>.</p>
 */
public class RequestDumperFilter implements Filter {

    private static final String NON_HTTP_REQ_MSG =
        "Not available. Non-http request.";
    private static final String NON_HTTP_RES_MSG =
        "Not available. Non-http response.";

    private static final ThreadLocal<Timestamp> timestamp =
            new ThreadLocal<Timestamp>() {
        @Override
        protected Timestamp initialValue() {
            return new Timestamp();
        }
    };

    /**
     * The logger for this class.
     */
    private static final Log log = LogFactory.getLog(RequestDumperFilter.class);


    /**
     * 记录请求参数, 调用序列中的下一个筛选器, 并记录响应参数.
     *
     * @param request  要处理的servlet请求
     * @param response 要创建的servlet响应
     * @param chain    正在处理的过滤器链
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest hRequest = null;
        HttpServletResponse hResponse = null;

        if (request instanceof HttpServletRequest) {
            hRequest = (HttpServletRequest) request;
        }
        if (response instanceof HttpServletResponse) {
            hResponse = (HttpServletResponse) response;
        }

        // Log pre-service information
        doLog("START TIME        ", getTimestamp());

        if (hRequest == null) {
            doLog("        requestURI", NON_HTTP_REQ_MSG);
            doLog("          authType", NON_HTTP_REQ_MSG);
        } else {
            doLog("        requestURI", hRequest.getRequestURI());
            doLog("          authType", hRequest.getAuthType());
        }

        doLog(" characterEncoding", request.getCharacterEncoding());
        doLog("     contentLength",
                Long.toString(request.getContentLengthLong()));
        doLog("       contentType", request.getContentType());

        if (hRequest == null) {
            doLog("       contextPath", NON_HTTP_REQ_MSG);
            doLog("            cookie", NON_HTTP_REQ_MSG);
            doLog("            header", NON_HTTP_REQ_MSG);
        } else {
            doLog("       contextPath", hRequest.getContextPath());
            Cookie cookies[] = hRequest.getCookies();
            if (cookies != null) {
                for (int i = 0; i < cookies.length; i++) {
                    doLog("            cookie", cookies[i].getName() +
                            "=" + cookies[i].getValue());
                }
            }
            Enumeration<String> hnames = hRequest.getHeaderNames();
            while (hnames.hasMoreElements()) {
                String hname = hnames.nextElement();
                Enumeration<String> hvalues = hRequest.getHeaders(hname);
                while (hvalues.hasMoreElements()) {
                    String hvalue = hvalues.nextElement();
                    doLog("            header", hname + "=" + hvalue);
                }
            }
        }

        doLog("            locale", request.getLocale().toString());

        if (hRequest == null) {
            doLog("            method", NON_HTTP_REQ_MSG);
        } else {
            doLog("            method", hRequest.getMethod());
        }

        Enumeration<String> pnames = request.getParameterNames();
        while (pnames.hasMoreElements()) {
            String pname = pnames.nextElement();
            String pvalues[] = request.getParameterValues(pname);
            StringBuilder result = new StringBuilder(pname);
            result.append('=');
            for (int i = 0; i < pvalues.length; i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(pvalues[i]);
            }
            doLog("         parameter", result.toString());
        }

        if (hRequest == null) {
            doLog("          pathInfo", NON_HTTP_REQ_MSG);
        } else {
            doLog("          pathInfo", hRequest.getPathInfo());
        }

        doLog("          protocol", request.getProtocol());

        if (hRequest == null) {
            doLog("       queryString", NON_HTTP_REQ_MSG);
        } else {
            doLog("       queryString", hRequest.getQueryString());
        }

        doLog("        remoteAddr", request.getRemoteAddr());
        doLog("        remoteHost", request.getRemoteHost());

        if (hRequest == null) {
            doLog("        remoteUser", NON_HTTP_REQ_MSG);
            doLog("requestedSessionId", NON_HTTP_REQ_MSG);
        } else {
            doLog("        remoteUser", hRequest.getRemoteUser());
            doLog("requestedSessionId", hRequest.getRequestedSessionId());
        }

        doLog("            scheme", request.getScheme());
        doLog("        serverName", request.getServerName());
        doLog("        serverPort",
                Integer.toString(request.getServerPort()));

        if (hRequest == null) {
            doLog("       servletPath", NON_HTTP_REQ_MSG);
        } else {
            doLog("       servletPath", hRequest.getServletPath());
        }

        doLog("          isSecure",
                Boolean.valueOf(request.isSecure()).toString());
        doLog("------------------",
                "--------------------------------------------");

        // 执行请求
        chain.doFilter(request, response);

        // Log post-service information
        doLog("------------------",
                "--------------------------------------------");
        if (hRequest == null) {
            doLog("          authType", NON_HTTP_REQ_MSG);
        } else {
            doLog("          authType", hRequest.getAuthType());
        }

        doLog("       contentType", response.getContentType());

        if (hResponse == null) {
            doLog("            header", NON_HTTP_RES_MSG);
        } else {
            Iterable<String> rhnames = hResponse.getHeaderNames();
            for (String rhname : rhnames) {
                Iterable<String> rhvalues = hResponse.getHeaders(rhname);
                for (String rhvalue : rhvalues) {
                    doLog("            header", rhname + "=" + rhvalue);
                }
            }
        }

        if (hRequest == null) {
            doLog("        remoteUser", NON_HTTP_REQ_MSG);
        } else {
            doLog("        remoteUser", hRequest.getRemoteUser());
        }

        if (hResponse == null) {
            doLog("        remoteUser", NON_HTTP_RES_MSG);
        } else {
            doLog("            status",
                    Integer.toString(hResponse.getStatus()));
        }

        doLog("END TIME          ", getTimestamp());
        doLog("==================",
                "============================================");
    }

    private void doLog(String attribute, String value) {
        StringBuilder sb = new StringBuilder(80);
        sb.append(Thread.currentThread().getName());
        sb.append(' ');
        sb.append(attribute);
        sb.append('=');
        sb.append(value);
        log.info(sb.toString());
    }

    private String getTimestamp() {
        Timestamp ts = timestamp.get();
        long currentTime = System.currentTimeMillis();

        if ((ts.date.getTime() + 999) < currentTime) {
            ts.date.setTime(currentTime - (currentTime % 1000));
            ts.update();
        }
        return ts.dateString;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // NOOP
    }

    @Override
    public void destroy() {
        // NOOP
    }

    private static final class Timestamp {
        private final Date date = new Date(0);
        private final SimpleDateFormat format =
            new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
        private String dateString = format.format(date);
        private void update() {
            dateString = format.format(date);
        }
    }
}
