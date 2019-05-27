package org.apache.catalina.ssi;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.http.RequestUtil;

public class SSIServletRequestUtil {
    /**
     * 返回与此servlet关联的相对路径.
     * 从DefaultServlet.java中拿. 也许这应该放进  org.apache.catalina.util somewhere?  似乎会被广泛使用.
     *
     * @param request
     *            The servlet request we are processing
     * @return the relative path
     */
    public static String getRelativePath(HttpServletRequest request) {
        // Are we being processed by a RequestDispatcher.include()?
        if (request.getAttribute(
                RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            String result = (String)request.getAttribute(
                    RequestDispatcher.INCLUDE_PATH_INFO);
            if (result == null)
                result = (String)request.getAttribute(
                        RequestDispatcher.INCLUDE_SERVLET_PATH);
            if ((result == null) || (result.equals(""))) result = "/";
            return (result);
        }
        // No, 直接从请求中提取期望的路径
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return RequestUtil.normalize(result);
    }

}