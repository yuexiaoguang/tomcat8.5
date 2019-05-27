package org.apache.coyote;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.tomcat.ContextBind;
import org.apache.tomcat.InstanceManager;

/**
 * 在升级过程中使用的Token.
 */
public final class UpgradeToken {

    private final ContextBind contextBind;
    private final HttpUpgradeHandler httpUpgradeHandler;
    private final InstanceManager instanceManager;

    public UpgradeToken(HttpUpgradeHandler httpUpgradeHandler,
            ContextBind contextBind, InstanceManager instanceManager) {
        this.contextBind = contextBind;
        this.httpUpgradeHandler = httpUpgradeHandler;
        this.instanceManager = instanceManager;
    }

    public final ContextBind getContextBind() {
        return contextBind;
    }

    public final HttpUpgradeHandler getHttpUpgradeHandler() {
        return httpUpgradeHandler;
    }

    public final InstanceManager getInstanceManager() {
        return instanceManager;
    }

}
