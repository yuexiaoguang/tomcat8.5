package org.apache.catalina.filters;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <code>RequestFilter</code>的基于远程客户端的IP地址的实现类.
 */
public final class RemoteAddrFilter extends RequestFilter {

    private static final Log log = LogFactory.getLog(RemoteAddrFilter.class);


    /**
     * 提取请求属性, 并传递(与指定的请求和响应对象以及关联的过滤器链一起)到<code>process()</code>方法来执行真正的过滤.
     *
     * @param request  要处理的servlet请求
     * @param response 要创建的servlet响应
     * @param chain    此请求的过滤器链
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        process(request.getRemoteAddr(), request, response, chain);

    }

    @Override
    protected Log getLogger() {
        return log;
    }
}
