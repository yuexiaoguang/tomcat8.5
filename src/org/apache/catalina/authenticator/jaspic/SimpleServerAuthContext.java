package org.apache.catalina.authenticator.jaspic;

import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.ServerAuthContext;
import javax.security.auth.message.module.ServerAuthModule;

/**
 * 基本实现主要用于使用只提供模块的第三方{@link ServerAuthModule}实现.
 * 此实现支持多个模块，并且如果任何一个模块能够对用户进行认证，则将用户视为已验证的.
 */
public class SimpleServerAuthContext implements ServerAuthContext {

    private final List<ServerAuthModule> modules;


    public SimpleServerAuthContext(List<ServerAuthModule> modules) {
        this.modules = modules;
    }


    @SuppressWarnings("unchecked") // JASPIC API uses raw types
    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {
        for (int moduleIndex = 0; moduleIndex < modules.size(); moduleIndex++) {
            ServerAuthModule module = modules.get(moduleIndex);
            AuthStatus result = module.validateRequest(messageInfo, clientSubject, serviceSubject);
            if (result != AuthStatus.SEND_FAILURE) {
                messageInfo.getMap().put("moduleIndex", Integer.valueOf(moduleIndex));
                return result;
            }
        }
        return AuthStatus.SEND_FAILURE;
    }


    @Override
    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject)
            throws AuthException {
        ServerAuthModule module = modules.get(((Integer) messageInfo.getMap().get("moduleIndex")).intValue());
        return module.secureResponse(messageInfo, serviceSubject);
    }


    @Override
    public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
        for (ServerAuthModule module : modules) {
            module.cleanSubject(messageInfo, subject);
        }
    }
}
