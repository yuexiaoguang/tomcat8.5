package javax.security.auth.message;

import javax.security.auth.Subject;

public interface ClientAuth {

    AuthStatus secureRequest(MessageInfo messageInfo, Subject clientSubject) throws AuthException;

    AuthStatus validateResponse(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException;

    void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException;
}

