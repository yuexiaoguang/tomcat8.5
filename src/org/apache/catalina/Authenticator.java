package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;


/**
 * 提供某种身份验证服务.
 */
public interface Authenticator {

    /**
     * 对作出此请求的用户进行身份验证, 基于关联的{@link Context}的登录配置.
     *
     * @param request
     * @param response
     *
     * @return <code>true</code>如果满足指定的约束, 或者<code>false</code> 如果不满足约束条件(在这种情况下，身份验证将被写入响应).
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public boolean authenticate(Request request, HttpServletResponse response)
            throws IOException;

    public void login(String userName, String password, Request request)
            throws ServletException;

    public void logout(Request request);
}
