package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;

/**
 * <b>Authenticator</b>和<b>Valve</b>身份验证的实现，使用SSL证书识别客户端用户.
 */
public class SSLAuthenticator extends AuthenticatorBase {

    // --------------------------------------------------------- Public Methods

    /**
     * 通过检查证书链的存在来验证用户(应该由一个<code>CertificatesValve</code>实例显示), 
     * 还可以请求信任管理器验证我们信任这个用户.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     *
     * @exception IOException 如果发生输入/输出错误
     */
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response)
            throws IOException {

        // NOTE: 不尝试使用任何现有的SSO会话来重新认证,
        // 因为只有原始的认证是BASIC 或 FORM才有效, 不如CLIENT-CERT auth-type安全
        //
        // 更改为true，以允许先前的FORM或BASIC身份验证为该WebApp验证用户
        // TODO 使此成为可配置属性(in SingleSignOn??)
        if (checkForCachedAuthentication(request, response, false)) {
            return true;
        }

        // 检索此客户端的证书链
        if (containerLog.isDebugEnabled()) {
            containerLog.debug(" Looking up certificates");
        }

        X509Certificate certs[] = getRequestCertificates(request);

        if ((certs == null) || (certs.length < 1)) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug("  No certificates included with this request");
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    sm.getString("authenticator.certificates"));
            return false;
        }

        // 验证指定的证书链
        Principal principal = context.getRealm().authenticate(certs);
        if (principal == null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug("  Realm.authenticate() returned false");
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                               sm.getString("authenticator.unauthorized"));
            return false;
        }

        // 缓存主体并记录此身份验证
        register(request, response, principal,
                HttpServletRequest.CLIENT_CERT_AUTH, null, null);
        return true;

    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.CLIENT_CERT_AUTH;
    }
}
