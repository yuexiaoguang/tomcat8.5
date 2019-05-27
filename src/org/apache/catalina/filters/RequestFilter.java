package org.apache.catalina.filters;


import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Filter实现类基于比较适当的请求属性执行过滤(selected based on which subclass you choose
 * to configure into your Container's pipeline)针对该Filter配置的正则表达式.
 * <p>
 * 这个过滤器通过设置<code>allow</code>和<code>deny</code>属性配置, 正则表达式(在{@link Pattern}语法支持下)将比较适当的请求属性.
 * 评估进行如下:
 * <ul>
 * <li>子类提取要过滤的请求属性, 并调用普通的<code>process()</code>方法.
 * <li>如果存在配置的拒绝表达式, 属性将与表达式进行比较. 如果找到一个匹配, 此请求将被使用"Forbidden" HTTP 响应拒绝.</li>
 * <li>如果存在配置的允许表达式, 属性将与表达式进行比较. 如果找到一个匹配, 此请求将允许传递到当前管道中的下一个过滤器.</li>
 * <li>如果指定了拒绝表达式，但不允许表达式, 允许传递这个请求(因为没有一个拒绝表达式匹配它).
 * <li>请求将使用"Forbidden" HTTP 响应拒绝.</li>
 * </ul>
 */
public abstract class RequestFilter extends FilterBase {


    // ----------------------------------------------------- Instance Variables

    /**
     * 用于允许请求测试的正则表达式.
     */
    protected Pattern allow = null;

    /**
     * 用于拒绝请求测试的正则表达式.
     */
    protected Pattern deny = null;

    /**
     * HTTP响应状态码，拒绝请求时使用. 默认是 403, 但可以修改为 404.
     */
    protected int denyStatus = HttpServletResponse.SC_FORBIDDEN;

    /**
     * mime type -- "text/plain"
     */
    private static final String PLAIN_TEXT_MIME_TYPE = "text/plain";


    // ------------------------------------------------------------- Properties


    /**
     * @return 用于对该过滤器的允许请求进行测试的正则表达式; 否则返回<code>null</code>.
     */
    public String getAllow() {
        if (allow == null) {
            return null;
        }
        return allow.toString();
    }


    /**
     * 设置用于测试该过滤器的允许请求的正则表达式.
     *
     * @param allow 允许的表达式
     */
    public void setAllow(String allow) {
        if (allow == null || allow.length() == 0) {
            this.allow = null;
        } else {
            this.allow = Pattern.compile(allow);
        }
    }


    /**
     * @return 用于对该过滤器的拒绝请求进行测试的正则表达式; 否则返回<code>null</code>.
     */
    public String getDeny() {
        if (deny == null) {
            return null;
        }
        return deny.toString();
    }


    /**
     * 设置用于筛选此过滤器的拒绝请求的正则表达式.
     *
     * @param deny 拒绝表达式
     */
    public void setDeny(String deny) {
        if (deny == null || deny.length() == 0) {
            this.deny = null;
        } else {
            this.deny = Pattern.compile(deny);
        }
    }


    /**
     * @return 拒绝请求的响应状态码.
     */
    public int getDenyStatus() {
        return denyStatus;
    }


    /**
     * 设置拒绝请求的响应状态码.
     *
     * @param denyStatus 拒绝的状态码
     */
    public void setDenyStatus(int denyStatus) {
        this.denyStatus = denyStatus;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 提取请求属性, 并传递它(与指定的请求和响应对象一起)到<code>process()</code>方法执行实际过滤.
     * 这个方法必须由一个具体的子类来实现.
     *
     * @param request 要处理的servlet请求
     * @param response 要创建的servlet响应
     * @param chain 过滤器链
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public abstract void doFilter(ServletRequest request,
            ServletResponse response, FilterChain chain) throws IOException,
            ServletException;


    // ------------------------------------------------------ Protected Methods


    @Override
    protected boolean isConfigProblemFatal() {
        return true;
    }


    /**
     * 执行已为该过滤器配置的过滤, 与指定请求的属性匹配.
     *
     * @param property 要过滤的请求属性
     * @param request 要处理的servlet请求
     * @param response 要处理的servlet响应
     * @param chain 过滤器链
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    protected void process(String property, ServletRequest request,
            ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (isAllowed(property)) {
            chain.doFilter(request, response);
        } else {
            if (response instanceof HttpServletResponse) {
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug(sm.getString("requestFilter.deny",
                            ((HttpServletRequest) request).getRequestURI(), property));
                }
                ((HttpServletResponse) response).sendError(denyStatus);
            } else {
                sendErrorWhenNotHttp(response);
            }
        }
    }


    /**
     * 处理所提供的属性的允许和拒绝规则.
     *
     * @param property  对允许列表和拒绝列表进行测试的属性
     * 
     * @return          <code>true</code>如果应该允许这个请求,
     *                  否则<code>false</code>
     */
    private boolean isAllowed(String property) {
        if (deny != null && deny.matcher(property).matches()) {
            return false;
        }

        // 检查允许的模式
        if (allow != null && allow.matcher(property).matches()) {
            return true;
        }

        // Allow if denies specified but not allows
        if (deny != null && allow == null) {
            return true;
        }

        // 拒绝这个请求
        return false;
    }

    private void sendErrorWhenNotHttp(ServletResponse response)
            throws IOException {
        response.setContentType(PLAIN_TEXT_MIME_TYPE);
        response.getWriter().write(sm.getString("http.403"));
        response.getWriter().flush();
    }
}
