package javax.security.auth.message.config;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.message.AuthException;

public interface ServerAuthConfig extends AuthConfig {

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject, Map properties)
            throws AuthException;
}
