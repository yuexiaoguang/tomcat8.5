package org.apache.coyote.http11.upgrade;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.http.WebConnection;

import org.apache.coyote.AbstractProcessorLight;
import org.apache.coyote.Request;
import org.apache.coyote.UpgradeToken;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketWrapperBase;

public abstract class UpgradeProcessorBase extends AbstractProcessorLight implements WebConnection {

    protected static final int INFINITE_TIMEOUT = -1;

    private final UpgradeToken upgradeToken;

    public UpgradeProcessorBase(UpgradeToken upgradeToken) {
        this.upgradeToken = upgradeToken;
    }


    // ------------------------------------------- Implemented Processor methods

    @Override
    public final boolean isUpgrade() {
        return true;
    }


    @Override
    public UpgradeToken getUpgradeToken() {
        return upgradeToken;
    }


    @Override
    public final void recycle() {
        // 目前NO-OP作为升级处理器不再循环使用.
    }


    // ---------------------------- Processor methods that are NO-OP for upgrade

    @Override
    public final SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException {
        return null;
    }


    @Override
    public final SocketState asyncPostProcess() {
        return null;
    }


    @Override
    public final boolean isAsync() {
        return false;
    }


    @Override
    public final Request getRequest() {
        return null;
    }


    @Override
    public ByteBuffer getLeftoverInput() {
        return null;
    }


    @Override
    public void timeoutAsync(long now) {
        // NO-OP
    }
}
