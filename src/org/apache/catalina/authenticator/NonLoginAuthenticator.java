package org.apache.catalina.authenticator;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;

/**
 * <b>Authenticator</b>和<b>Valve</b>实现类， 只检查不涉及用户身份验证的安全约束
 */
public final class NonLoginAuthenticator extends AuthenticatorBase {


    // --------------------------------------------------------- Public Methods


    /**
     * <p>验证此请求的用户, 基于容器中未定义<code>login-config</code>.</p>
     *
     * <p>这个实现意味着"即使没有为该用户建立安全Principal，也可以登录".</p>
     *
     * <p>这个方法由AuthenticatorBase父类调用为用户建立一个 Principal, 在检查容器安全约束之前,
     * 也就是说，还不知道用户最终是否被允许访问所请求的资源.
     * 因此, 有必要一直返回<code>true</code>指示用户没有验证失败.</p>
     *
     * <p>有两种情况:</p>
     * <ul>
     * <li>SingleSignon: 会话实例还不存在，而且没有<code>auth-method</code>来验证用户, 因此让Request的Principal 是null.
     *     Note: AuthenticatorBase稍后将检查安全约束，以确定没有安全Principal和Role的用户是否可以访问该资源.
     * </li>
     * <li>SingleSignon: 如果用户已经通过另一个容器进行了身份验证(使用自己的登录配置), 随后将Session和SSOEntry关联起来，
     * 		因此，它继承了已经建立的安全Principal和相关Role.
     *     Note: 这个特别的会话将变成SingleSignOnEntry Session集合的一员，而且将潜在的保持SSOE "alive", 即使所有其他正确认证的会话第一次过期…直到它到期.
     * </li>
     * </ul>
     *
     * @param request  Request we are processing
     * @param response Response we are creating
     * @return boolean 用户是否已认证
     * @exception IOException if an input/output error occurs
     */
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response)
        throws IOException {

        // 不要尝试使用SSO进行身份验证，因为没有为此Web应用程序配置的Auth
        if (checkForCachedAuthentication(request, response, true)) {
            // 在这个会话中保存继承的Principal，这样它就可以保持身份验证，直到它到期
            if (cache) {
                request.getSessionInternal(true).setPrincipal(request.getUserPrincipal());
            }
            return true;
        }

        // 没有Principal， 意味着用户尚未被认证，因此不会被分配任何角色. 可以说，用户现在已经被认证了，因为只允许具有匹配的角色，才能访问受保护的资源.
        // 即 SC_FORBIDDEN (403 status)将在稍后生成.
        if (containerLog.isDebugEnabled())
            containerLog.debug("User authenticated without any roles");
        return true;
    }


    /**
     * 返回认证方法, 它是特定于供应商的，而不是由HttpServletRequest请求定义的.
     */
    @Override
    protected String getAuthMethod() {
        return "NONE";
    }
}
