package javax.security.auth.message.module;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.ServerAuth;

public interface ServerAuthModule extends ServerAuth {

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map options) throws AuthException;

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    Class[] getSupportedMessageTypes();
}
