package javax.security.auth.message.module;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.ClientAuth;
import javax.security.auth.message.MessagePolicy;

public interface ClientAuthModule extends ClientAuth {

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map options) throws AuthException;

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    Class[] getSupportedMessageTypes();
}
