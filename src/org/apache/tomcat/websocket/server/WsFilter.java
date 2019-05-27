package org.apache.tomcat.websocket.server;

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
 * 处理WebSocket 连接的初始HTTP连接.
 */
public class WsFilter implements Filter {

    private WsServerContainer sc;


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        sc = (WsServerContainer) filterConfig.getServletContext().getAttribute(
                Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        // 此过滤器只需要处理WebSocket 升级请求
        if (!sc.areEndpointsRegistered() ||
                !UpgradeUtil.isWebSocketUpgradeRequest(request, response)) {
            chain.doFilter(request, response);
            return;
        }

        // 带有WebSocket 的升级header的HTTP请求
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // 检查此WebSocket 实现是否具有匹配映射
        String path;
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            path = req.getServletPath();
        } else {
            path = req.getServletPath() + pathInfo;
        }
        WsMappingResult mappingResult = sc.findMapping(path);

        if (mappingResult == null) {
            // 请求的路径没有端点注册. 让应用程序处理它 (它可能重定向或转发)
            chain.doFilter(request, response);
            return;
        }

        UpgradeUtil.doUpgrade(sc, req, resp, mappingResult.getConfig(),
                mappingResult.getPathParams());
    }


    @Override
    public void destroy() {
        // NO-OP
    }
}
