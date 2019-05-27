package javax.security.auth.message.config;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.message.AuthException;

public interface ClientAuthConfig extends AuthConfig {

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    ClientAuthContext getAuthContext(String authContextID, Subject clientSubject, Map properties)
            throws AuthException;
}
