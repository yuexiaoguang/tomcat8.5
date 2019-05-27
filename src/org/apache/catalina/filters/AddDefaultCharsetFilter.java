package org.apache.catalina.filters;

import java.io.IOException;
import java.nio.charset.Charset;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * 为"text"类型的子类型显式设置默认字符集为ISO-8859-1, 或另一个用户定义的字符集.
 * RFC2616 要求浏览器必须使用 ISO-8859-1, 如果没有定义"text"类型的字符集. 但是, 浏览器可能尝试自动检测字符集.
 * 这可能被攻击者利用来执行XSS攻击. 默认情况下，Internet Explorer具有此行为. 其他浏览器有一个选项来启用它.<br>
 *
 * 此过滤器通过显式设置字符集来防止攻击. 除非所提供的字符集被用户明确地重写 - 浏览器将显式设置字符集, 从而防止XSS攻击.
 */
public class AddDefaultCharsetFilter extends FilterBase {

    private static final Log log =
        LogFactory.getLog(AddDefaultCharsetFilter.class);

    private static final String DEFAULT_ENCODING = "ISO-8859-1";

    private String encoding;

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    protected Log getLogger() {
        return log;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        if (encoding == null || encoding.length() == 0 ||
                encoding.equalsIgnoreCase("default")) {
            encoding = DEFAULT_ENCODING;
        } else if (encoding.equalsIgnoreCase("system")) {
            encoding = Charset.defaultCharset().name();
        } else if (!Charset.isSupported(encoding)) {
            throw new IllegalArgumentException(sm.getString(
                    "addDefaultCharset.unsupportedCharset", encoding));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        // Wrap the response
        if (response instanceof HttpServletResponse) {
            ResponseWrapper wrapped =
                new ResponseWrapper((HttpServletResponse)response, encoding);
            chain.doFilter(request, wrapped);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * 为text类型添加字符集的Wrapper, 如果未指定字符集.
     */
    public static class ResponseWrapper extends HttpServletResponseWrapper {

        private String encoding;

        public ResponseWrapper(HttpServletResponse response, String encoding) {
            super(response);
            this.encoding = encoding;
        }

        @Override
        public void setContentType(String ct) {

            if (ct != null && ct.startsWith("text/")) {
                if (ct.indexOf("charset=") < 0) {
                    super.setContentType(ct + ";charset=" + encoding);
                } else {
                    super.setContentType(ct);
                    encoding = getCharacterEncoding();
                }
            } else {
                super.setContentType(ct);
            }

        }

        @Override
        public void setCharacterEncoding(String charset) {
            super.setCharacterEncoding(charset);
            encoding = charset;
        }
    }
}
