package org.apache.tomcat.websocket;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * 根据服务器使用的方案返回适当的验证器.
 */
public class AuthenticatorFactory {

    /**
     * 返回新的身份验证器实例.
     * 
     * @param authScheme 使用的方案
     * @return 验证器
     */
    public static Authenticator getAuthenticator(String authScheme) {

        Authenticator auth = null;
        switch (authScheme.toLowerCase()) {

        case BasicAuthenticator.schemeName:
            auth = new BasicAuthenticator();
            break;

        case DigestAuthenticator.schemeName:
            auth = new DigestAuthenticator();
            break;

        default:
            auth = loadAuthenticators(authScheme);
            break;
        }

        return auth;

    }

    private static Authenticator loadAuthenticators(String authScheme) {
        ServiceLoader<Authenticator> serviceLoader = ServiceLoader.load(Authenticator.class);
        Iterator<Authenticator> auths = serviceLoader.iterator();

        while (auths.hasNext()) {
            Authenticator auth = auths.next();
            if (auth.getSchemeName().equalsIgnoreCase(authScheme))
                return auth;
        }

        return null;
    }
}
