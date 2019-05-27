package javax.security.auth.message;

import javax.security.auth.Subject;

public interface ServerAuth {

    AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException;

    AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject) throws AuthException;

    void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException;
}
