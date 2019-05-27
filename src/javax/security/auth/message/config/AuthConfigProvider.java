package javax.security.auth.message.config;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;

public interface AuthConfigProvider {

    ClientAuthConfig getClientAuthConfig(String layer, String appContext, CallbackHandler handler)
            throws AuthException;

    ServerAuthConfig getServerAuthConfig(String layer, String appContext, CallbackHandler handler)
            throws AuthException;

    void refresh();
}
