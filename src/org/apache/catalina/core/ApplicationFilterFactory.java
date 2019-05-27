package org.apache.catalina.core;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;

import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.tomcat.util.descriptor.web.FilterMap;

/**
 * 过滤器创建和缓存以及过滤器链创建的工厂.
 */
public final class ApplicationFilterFactory {

    private ApplicationFilterFactory() {
        // Prevent instance creation. This is a utility class.
    }


    /**
     * 创建并返回一个FilterChain 实现类, 它将包装指定servlet实例的执行.
     *
     * @param request 正在处理的servlet请求
     * @param wrapper 包装servlet实例的包装器
     * @param servlet 要包装的servlet实例
     *
     * @return 配置的FilterChain实例, 或 null.
     */
    public static ApplicationFilterChain createFilterChain(ServletRequest request,
            Wrapper wrapper, Servlet servlet) {

        // 如果没有servlet来执行, 返回 null
        if (servlet == null)
            return null;

        // 创建和初始化过滤器链对象
        ApplicationFilterChain filterChain = null;
        if (request instanceof Request) {
            Request req = (Request) request;
            if (Globals.IS_SECURITY_ENABLED) {
                // Security: Do not recycle
                filterChain = new ApplicationFilterChain();
            } else {
                filterChain = (ApplicationFilterChain) req.getFilterChain();
                if (filterChain == null) {
                    filterChain = new ApplicationFilterChain();
                    req.setFilterChain(filterChain);
                }
            }
        } else {
            // Request dispatcher in use
            filterChain = new ApplicationFilterChain();
        }

        filterChain.setServlet(servlet);
        filterChain.setServletSupportsAsync(wrapper.isAsyncSupported());

        // 获取此上下文的过滤器映射
        StandardContext context = (StandardContext) wrapper.getParent();
        FilterMap filterMaps[] = context.findFilterMaps();

        // 如果没有过滤器映射
        if ((filterMaps == null) || (filterMaps.length == 0))
            return (filterChain);

        // 获取匹配过滤器映射所需的信息
        DispatcherType dispatcher =
                (DispatcherType) request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);

        String requestPath = null;
        Object attribute = request.getAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR);
        if (attribute != null){
            requestPath = attribute.toString();
        }

        String servletName = wrapper.getName();

        // 将相关的路径映射过滤器添加到此过滤器链中
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersURL(filterMaps[i], requestPath))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
        }

        // 添加与servlet名称匹配的过滤器
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersServlet(filterMaps[i], servletName))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
        }

        // 返回已完成的过滤器链
        return filterChain;
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 返回<code>true</code>如果上下文相对请求路径与指定过滤器映射的要求相匹配;
     * 否则返回<code>null</code>.
     *
     * @param filterMap 正在检查的过滤器映射
     * @param requestPath 此请求的上下文相对请求路径
     */
    private static boolean matchFiltersURL(FilterMap filterMap, String requestPath) {

        // 检查特殊的 "*" URL模式, 也与命名的调度相匹配
        if (filterMap.getMatchAllUrlPatterns())
            return true;

        if (requestPath == null)
            return false;

        // 上下文相对请求路径匹配
        String[] testPaths = filterMap.getURLPatterns();

        for (int i = 0; i < testPaths.length; i++) {
            if (matchFiltersURL(testPaths[i], requestPath)) {
                return true;
            }
        }

        // No match
        return false;
    }


    /**
     * 返回<code>true</code>如果上下文相对请求路径与指定过滤器映射的要求相匹配;
     * 否则返回<code>false</code>.
     *
     * @param testPath 正在检查的URL映射
     * @param requestPath 上下文相对请求路径
     */
    private static boolean matchFiltersURL(String testPath, String requestPath) {

        if (testPath == null)
            return false;

        // Case 1 - 精确匹配
        if (testPath.equals(requestPath))
            return true;

        // Case 2 - 路径匹配 ("/.../*")
        if (testPath.equals("/*"))
            return true;
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0,
                                       testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return true;
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return true;
                }
            }
            return false;
        }

        // Case 3 - 扩展匹配
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash)
                && (period != requestPath.length() - 1)
                && ((requestPath.length() - period)
                    == (testPath.length() - 1))) {
                return (testPath.regionMatches(2, requestPath, period + 1,
                                               testPath.length() - 2));
            }
        }

        // Case 4 - "Default" Match
        return false; // NOTE - 不相关的选择过滤器
    }


    /**
     * 返回<code>true</code> 如果指定的servlet名称与指定过滤器映射的要求相匹配;否则返回<code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param servletName Servlet name being checked
     */
    private static boolean matchFiltersServlet(FilterMap filterMap,
                                        String servletName) {

        if (servletName == null) {
            return false;
        }
        // Check the specific "*" special servlet name
        else if (filterMap.getMatchAllServletNames()) {
            return true;
        } else {
            String[] servletNames = filterMap.getServletNames();
            for (int i = 0; i < servletNames.length; i++) {
                if (servletName.equals(servletNames[i])) {
                    return true;
                }
            }
            return false;
        }

    }


    /**
     * 返回true, 如果调度器类型匹配FilterMap中指定的调度器类型
     */
    private static boolean matchDispatcher(FilterMap filterMap, DispatcherType type) {
        switch (type) {
            case FORWARD :
                if ((filterMap.getDispatcherMapping() & FilterMap.FORWARD) != 0) {
                    return true;
                }
                break;
            case INCLUDE :
                if ((filterMap.getDispatcherMapping() & FilterMap.INCLUDE) != 0) {
                    return true;
                }
                break;
            case REQUEST :
                if ((filterMap.getDispatcherMapping() & FilterMap.REQUEST) != 0) {
                    return true;
                }
                break;
            case ERROR :
                if ((filterMap.getDispatcherMapping() & FilterMap.ERROR) != 0) {
                    return true;
                }
                break;
            case ASYNC :
                if ((filterMap.getDispatcherMapping() & FilterMap.ASYNC) != 0) {
                    return true;
                }
                break;
        }
        return false;
    }
}
