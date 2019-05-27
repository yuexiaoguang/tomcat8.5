package org.apache.catalina.valves;


import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;

/**
 * Valve实现类, 在比较适当的请求属性的基础上执行筛选(基于您选择配置到Container的管道中的子类来选择)针对该Valve配置的一组正则表达式.
 * <p>
 * 这个valve通过设置<code>allow</code>和<code>deny</code>属性配置以逗号分隔的正则表达式列表，其中将比较适当的请求属性.
 * 评估结果如下:
 * <ul>
 * <li>子类提取要过滤的请求属性, 并调用普通的<code>process()</code>方法.
 * <li>如果任何表达式没有配置, 属性将与每个表达式进行比较. 
 * 		如果找到匹配项, 这个请求将被拒绝，使用 "Forbidden" HTTP响应.</li>
 * <li>如果有允许表达式配置, 属性将与每个表达式进行比较. 如果找到匹配项, 此请求将被允许传递到当前pipeline中的下一个Valve.</li>
 * <li>如果指定了一个或多个否定表达式，但不允许表达式, 允许这个请求通过(因为没有一个否定表达式匹配它).
 * <li>这个请求将被拒绝，使用"Forbidden" HTTP响应.</li>
 * </ul>
 * <p>
 * 作为阀门的选择, 可以生成一个无效的<code>authenticate</code> header, 而不是拒绝请求.
 * 这可以和上下文属性<code>preemptiveAuthentication="true"</code>和一个验证器组合来强制认证而不是拒绝.
 * <p>
 * 这个Valve可以连接到任何Container, 取决于希望执行的筛选的粒度.
 */
public abstract class RequestFilterValve extends ValveBase {

    //------------------------------------------------------ Constructor
    public RequestFilterValve() {
        super(true);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 用于允许请求测试的正则表达式.
     */
    protected volatile Pattern allow = null;


    /**
     * 当前允许配置值, 编译成一个有效的{@link Pattern}.
     */
    protected volatile String allowValue = null;


    /**
     * 是否捕获配置错误.
     * 默认是<code>true</code>, <code>false</code>如果试图分配一个无效值为<code>allow</code>模式.
     */
    protected volatile boolean allowValid = true;


    /**
     * 用于拒绝请求测试的正则表达式.
     */
    protected volatile Pattern deny = null;


    /**
     * 当前不允许配置值, 编译成一个有效的{@link Pattern}.
     */
    protected volatile String denyValue = null;


    /**
     * 是否捕获配置错误.
     * 默认是<code>true</code>, <code>false</code>如果试图分配一个无效值为<code>deny</code>模式.
     */
    protected volatile boolean denyValid = true;


    /**
     * 拒绝请求时使用的HTTP响应状态码. 默认是403, 但可能会改变为404.
     */
    protected int denyStatus = HttpServletResponse.SC_FORBIDDEN;

    /**
     * <p>如果<code>invalidAuthenticationWhenDeny</code>是 true,
     * 而且上下文有<code>preemptiveAuthentication</code>, 设置无效的认证header来触发基本的AUTH，而不是拒绝请求.
     */
    private boolean invalidAuthenticationWhenDeny = false;

    /**
     * 在过滤方法中决定是否将服务器连接器端口添加到属性中. 将被追加的端口, 使用 ";"作为分隔符.
     */
    private volatile boolean addConnectorPort = false;

    // ------------------------------------------------------------- Properties


    /**
     * 返回用于测试此Valve允许请求的正则表达式; 否则返回<code>null</code>.
     * 
     * @return 正则表达式
     */
    public String getAllow() {
        return allowValue;
    }


    /**
     * 设置用于测试此Valve允许请求的正则表达式.
     *
     * @param allow 正则表达式
     */
    public void setAllow(String allow) {
        if (allow == null || allow.length() == 0) {
            this.allow = null;
            allowValue = null;
            allowValid = true;
        } else {
            boolean success = false;
            try {
                allowValue = allow;
                this.allow = Pattern.compile(allow);
                success = true;
            } finally {
                allowValid = success;
            }
        }
    }


    /**
     * 返回用于测试此Valve拒绝请求的正则表达式; 否则返回<code>null</code>.
     * 
     * @return 正则表达式
     */
    public String getDeny() {
        return denyValue;
    }


    /**
     * 返回用于测试此Valve拒绝请求的正则表达式.
     *
     * @param deny The new deny expression
     */
    public void setDeny(String deny) {
        if (deny == null || deny.length() == 0) {
            this.deny = null;
            denyValue = null;
            denyValid = true;
        } else {
            boolean success = false;
            try {
                denyValue = deny;
                this.deny = Pattern.compile(deny);
                success = true;
            } finally {
                denyValid = success;
            }
        }
    }


    /**
     * 返回{@code false}, 如果最后修改的 {@code allow} 模式应用不成功. 即如果模式在语法上是无效的.
     * 
     * @return <code>false</code>如果当前模式无效
     */
    public final boolean isAllowValid() {
        return allowValid;
    }


    /**
     * 返回{@code false}, 如果最后修改的 {@code deny} 模式应用不成功. 即如果模式在语法上是无效的.
     * 
     * @return <code>false</code>如果当前模式无效
     */
    public final boolean isDenyValid() {
        return denyValid;
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
     * @param denyStatus The status code
     */
    public void setDenyStatus(int denyStatus) {
        this.denyStatus = denyStatus;
    }


    /**
     * @return <code>true</code>如果通过设置无效的auth header来处理拒绝.
     */
    public boolean getInvalidAuthenticationWhenDeny() {
        return invalidAuthenticationWhenDeny;
    }


    /**
     * @param value <code>true</code>如果通过设置无效的auth header来处理拒绝.
     */
    public void setInvalidAuthenticationWhenDeny(boolean value) {
        invalidAuthenticationWhenDeny = value;
    }


    /**
     * 在过滤方法中是否将服务器连接器端口添加到属性中. 将被追加的端口, 使用 ";"作为分隔符.
     * 
     * @return <code>true</code>添加连接器端口
     */
    public boolean getAddConnectorPort() {
        return addConnectorPort;
    }


    /**
     * 在过滤方法中是否将服务器连接器端口添加到属性中. 将被追加的端口, 使用 ";"作为分隔符.
     *
     * @param addConnectorPort The new flag
     */
    public void setAddConnectorPort(boolean addConnectorPort) {
        this.addConnectorPort = addConnectorPort;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 提取期望的请求属性, 并将其传递到(与指定的请求和响应对象一起)<code>process()</code>方法执行实际过滤.
     * 这个方法必须由一个具体的子类来实现.
     *
     * @param request 要处理的servlet请求
     * @param response 要创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    @Override
    public abstract void invoke(Request request, Response response)
        throws IOException, ServletException;


    // ------------------------------------------------------ Protected Methods


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        if (!allowValid || !denyValid) {
            throw new LifecycleException(
                    sm.getString("requestFilterValve.configInvalid"));
        }
    }


    @Override
    protected synchronized void startInternal() throws LifecycleException {
        if (!allowValid || !denyValid) {
            throw new LifecycleException(
                    sm.getString("requestFilterValve.configInvalid"));
        }
        super.startInternal();
    }


    /**
     * 执行为这个Valve配置的过滤, 与指定的请求属性匹配.
     *
     * @param property 要过滤的请求属性
     * @param request The servlet request to be processed
     * @param response The servlet response to be processed
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    protected void process(String property, Request request, Response response)
            throws IOException, ServletException {

        if (isAllowed(property)) {
            getNext().invoke(request, response);
            return;
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("requestFilterValve.deny",
                    request.getRequestURI(), property));
        }

        // Deny this request
        denyRequest(request, response);
    }


    protected abstract Log getLog();


    /**
     * 拒绝被该阀门拒绝的请求.
     * <p>如果<code>invalidAuthenticationWhenDeny</code>是 true,
     * 而且上下文已经设置<code>preemptiveAuthentication</code>, 设置无效的授权标头来触发基本AUTH.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be processed
     * 
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    protected void denyRequest(Request request, Response response)
            throws IOException, ServletException {
        if (invalidAuthenticationWhenDeny) {
            Context context = request.getContext();
            if (context != null && context.getPreemptiveAuthentication()) {
                if (request.getCoyoteRequest().getMimeHeaders().getValue("authorization") == null) {
                    request.getCoyoteRequest().getMimeHeaders().addValue("authorization").setString("invalid");
                }
                getNext().invoke(request, response);
                return;
            }
        }
        response.sendError(denyStatus);
    }


    /**
     * 执行这个Valve实现的测试, 与指定请求属性值匹配. 测试阀门配置是否允许或拒绝某些IP地址.
     *
     * @param property 要过滤的请求属性值
     * @return <code>true</code>如果允许请求
     */
    public boolean isAllowed(String property) {
        // 使用本地副本进行线程安全
        Pattern deny = this.deny;
        Pattern allow = this.allow;

        // 检查拒绝模式
        if (deny != null && deny.matcher(property).matches()) {
            return false;
        }

        // 检查允许模式
        if (allow != null && allow.matcher(property).matches()) {
            return true;
        }

        // Allow if denies specified but not allows
        if (deny != null && allow == null) {
            return true;
        }

        // 拒绝此请求
        return false;
    }
}
