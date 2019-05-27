package org.apache.catalina.realm;

import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.CredentialHandler;

public class NestedCredentialHandler implements CredentialHandler {

    private final List<CredentialHandler> credentialHandlers = new ArrayList<>();


    @Override
    public boolean matches(String inputCredentials, String storedCredentials) {
        for (CredentialHandler handler : credentialHandlers) {
            if (handler.matches(inputCredentials, storedCredentials)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 输入的凭据将被解析为第一个嵌套的{@link CredentialHandler}.
     * 如果没有配置嵌套的{@link CredentialHandler}，将返回<code>null</code>.
     */
    @Override
    public String mutate(String inputCredentials) {
        if (credentialHandlers.isEmpty()) {
            return null;
        }

        return credentialHandlers.get(0).mutate(inputCredentials);
    }


    public void addCredentialHandler(CredentialHandler handler) {
        credentialHandlers.add(handler);
    }

    public CredentialHandler[] getCredentialHandlers() {
        return credentialHandlers.toArray(new CredentialHandler[0]);
    }

}
